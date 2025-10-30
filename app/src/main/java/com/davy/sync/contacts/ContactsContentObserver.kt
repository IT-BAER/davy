package com.davy.sync.contacts

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.davy.sync.account.AndroidAccountManager
import com.davy.sync.SyncManager
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.AddressBookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ContentObserver that monitors changes to Android's Contacts Provider.
 * 
 * When contacts are created/modified in the phone's Contacts app for DAVy accounts,
 * this observer triggers automatic push sync to the CardDAV server.
 * 
 * Similar to reference implementation's ContactsSyncManager and CalendarContentObserver.
 */
@Singleton
class ContactsContentObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidAccountManager: AndroidAccountManager,
    private val syncManager: SyncManager,
    private val accountRepository: AccountRepository,
    private val addressBookRepository: AddressBookRepository
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val DEBOUNCE_DELAY_MS = 2000L
        
        // Flag to prevent observing our own changes
        @Volatile
        var isSyncInProgress = false
    }
    
    private var lastChangeTimestamp = 0L
    private val changeHandler = Handler(Looper.getMainLooper())
    private var pendingChangeRunnable: Runnable? = null
    
    /**
     * Called when contact data changes in the Contacts Provider.
     */
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        Timber.d("========================================")
        Timber.d("Contact data changed - selfChange: $selfChange, uri: $uri")
        Timber.d("========================================")
        
        // Ignore changes during sync to prevent loops
        if (isSyncInProgress) {
            Timber.d("⏸️ Ignoring change - sync in progress (our own change)")
            return
        }
        
        // Debounce rapid changes (user may be editing multiple fields)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastChangeTimestamp < DEBOUNCE_DELAY_MS) {
            Timber.d("Debouncing change - too rapid (last: $lastChangeTimestamp, now: $currentTime)")
            pendingChangeRunnable?.let { changeHandler.removeCallbacks(it) }
        }
        
        lastChangeTimestamp = currentTime
        
        // Schedule processing after debounce delay
        pendingChangeRunnable = Runnable {
            Timber.d("Executing debounced change handler for URI: $uri")
            handleChange(uri)
        }
        changeHandler.postDelayed(pendingChangeRunnable!!, DEBOUNCE_DELAY_MS)
        Timber.d("Change handler scheduled with ${DEBOUNCE_DELAY_MS}ms delay")
    }
    
    /**
     * Handle the contact change after debounce period.
     */
    private fun handleChange(uri: Uri?) {
        Timber.d("Processing contact change: $uri")
        
        // Try to extract contact information from URI for diagnostic logging
        uri?.let { 
            extractAndLogContactAccount(it)
        }
        
        triggerContactsSync()
    }
    
    /**
     * Extract and log which account the changed contact belongs to.
     * This helps diagnose issues where contacts are created under wrong accounts.
     */
    private fun extractAndLogContactAccount(uri: Uri) {
        try {
            // Try to extract raw contact ID from URI
            val uriString = uri.toString()
            
            // URIs can be:
            // - content://com.android.contacts/raw_contacts/123 (specific raw contact)
            // - content://com.android.contacts/data/456 (specific data row)
            // - content://com.android.contacts/contacts/789 (aggregated contact)
            
            when {
                uriString.contains("/raw_contacts/") -> {
                    val rawContactId = uri.lastPathSegment?.toLongOrNull()
                    if (rawContactId != null) {
                        logRawContactAccount(rawContactId)
                    }
                }
                uriString.contains("/data/") -> {
                    // For data URIs, query the data row to get raw_contact_id
                    val dataId = uri.lastPathSegment?.toLongOrNull()
                    if (dataId != null) {
                        val projection = arrayOf(ContactsContract.Data.RAW_CONTACT_ID)
                        val cursor = context.contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            projection,
                            "${ContactsContract.Data._ID} = ?",
                            arrayOf(dataId.toString()),
                            null
                        )
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val rawContactId = it.getLong(0)
                                logRawContactAccount(rawContactId)
                            }
                        }
                    }
                }
                uriString.contains("/contacts/") -> {
                    // For contact URIs, find all raw contacts for this contact
                    val contactId = uri.lastPathSegment?.toLongOrNull()
                    if (contactId != null) {
                        val projection = arrayOf(ContactsContract.RawContacts._ID)
                        val cursor = context.contentResolver.query(
                            ContactsContract.RawContacts.CONTENT_URI,
                            projection,
                            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                            arrayOf(contactId.toString()),
                            null
                        )
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val rawContactId = it.getLong(0)
                                logRawContactAccount(rawContactId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not extract contact account info from URI: $uri")
        }
    }
    
    /**
     * Log detailed information about which account a raw contact belongs to.
     */
    private fun logRawContactAccount(rawContactId: Long) {
        val projection = arrayOf(
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.DIRTY,
            ContactsContract.RawContacts.DELETED
        )
        
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val accountName = it.getString(0) ?: "null"
                val accountType = it.getString(1) ?: "null"
                val dirty = it.getInt(2)
                val deleted = it.getInt(3)
                
                Timber.d("========================================")
                Timber.d("🔍 CHANGED CONTACT DETAILS:")
                Timber.d("   Raw Contact ID: $rawContactId")
                Timber.d("   Account Name: $accountName")
                Timber.d("   Account Type: $accountType")
                Timber.d("   Dirty Flag: $dirty")
                Timber.d("   Deleted Flag: $deleted")
                
                // Check if this is a DAVy account
                val isDAVyAccount = accountType == AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                
                if (isDAVyAccount) {
                    Timber.d("   ✅ This IS a DAVy address book account")
                    if (dirty == 0 && deleted == 0) {
                        Timber.w("   ⚠️ WARNING: Contact belongs to DAVy account but DIRTY=0 and DELETED=0")
                        Timber.w("   ⚠️ This might indicate the contact was just synced from server")
                    }
                } else {
                    Timber.w("   ❌ WARNING: Contact does NOT belong to a DAVy account!")
                    Timber.w("   ❌ To sync this contact with DAVy:")
                    Timber.w("   ❌ 1. Open Contacts app")
                    Timber.w("   ❌ 2. Create new contact")
                    Timber.w("   ❌ 3. Select 'Save to: [DAVy Address Book]'")
                    Timber.w("   ❌ 4. Example: 'Kontakte@Rog@cloud.it-baer.net'")
                }
                Timber.d("========================================")
            }
        }
    }
    
    /**
     * Start observing contact changes.
     */
    fun startObserving(contentResolver: ContentResolver) {
        Timber.d("========================================")
        Timber.d("Starting contacts content observation")
        Timber.d("========================================")
        
        // Observe raw contacts (structural changes like insert/delete)
        contentResolver.registerContentObserver(
            ContactsContract.RawContacts.CONTENT_URI,
            true,
            this
        )

        // Observe Data changes (field-level edits within a contact)
        contentResolver.registerContentObserver(
            ContactsContract.Data.CONTENT_URI,
            true,
            this
        )

        // Observe top-level Contacts (aggregation/state changes)
        contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            this
        )

        Timber.d("Registered observer for RawContacts URI: ${ContactsContract.RawContacts.CONTENT_URI}")
        Timber.d("Registered observer for Data URI: ${ContactsContract.Data.CONTENT_URI}")
        Timber.d("Registered observer for Contacts URI: ${ContactsContract.Contacts.CONTENT_URI}")
        Timber.d("========================================")
        Timber.d("Contacts content observer registration COMPLETE")
        Timber.d("========================================")
    }
    
    /**
     * Stop observing contact changes.
     */
    fun stopObserving(contentResolver: ContentResolver) {
        Timber.d("Stopping contacts content observation")
        contentResolver.unregisterContentObserver(this)
        
        // Cancel pending change handler
        pendingChangeRunnable?.let { changeHandler.removeCallbacks(it) }
    }
    
    /**
     * Triggers an immediate PUSH-ONLY sync for affected DAVy address books.
     * This uploads local contact changes without downloading from the server,
     * providing immediate sync for quick responsiveness.
     * 
     * OPTIMIZATION: Only syncs address books that have dirty contacts,
     * not all address books for all accounts.
     */
    private fun triggerContactsSync() {
        scope.launch {
            try {
                Timber.d("========================================")
                Timber.d("Triggering immediate PUSH-ONLY contact sync (optimized)")
                Timber.d("========================================")
                
                // Track which address books have dirty contacts
                val addressBooksWithChanges = mutableSetOf<Pair<Long, Long>>() // Pair<accountId, addressBookId>
                
                // Get all DAVy accounts
                val accounts = accountRepository.getAll()
                
                Timber.d("Scanning ${accounts.size} DAVy account(s) for dirty contacts...")
                
                for (account in accounts) {
                    // Get all address books for this account
                    val addressBooks = addressBookRepository.getByAccountId(account.id)
                    
                    Timber.d("Account: ${account.accountName} has ${addressBooks.size} address book(s)")
                    
                    for (addressBook in addressBooks) {
                        // Get the correct Android account for this address book
                        // Account name format: "AddressBookName (mainAccount) #id"
                        val androidAccount = androidAccountManager.findAddressBookAccount(
                            account.accountName,
                            addressBook.id
                        )
                        
                        if (androidAccount == null) {
                            Timber.w("   ⚠️ Android account not found for address book: ${addressBook.displayName} (ID: ${addressBook.id})")
                            continue
                        }
                        
                        val accountName = androidAccount.name
                        val accountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                        
                        Timber.d("   Checking address book: ${addressBook.displayName}")
                        Timber.d("      Query ACCOUNT_NAME: $accountName")
                        Timber.d("      Query ACCOUNT_TYPE: $accountType")
                        
                        // First, query ALL contacts (not just dirty) for diagnostic purposes
                        val allProjection = arrayOf(
                            ContactsContract.RawContacts._ID,
                            ContactsContract.RawContacts.DIRTY,
                            ContactsContract.RawContacts.DELETED,
                            ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY
                        )
                        
                        val allCursor = context.contentResolver.query(
                            ContactsContract.RawContacts.CONTENT_URI,
                            allProjection,
                            "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
                            arrayOf(accountName, accountType),
                            null
                        )
                        
                        var totalCount = 0
                        var dirtyCount = 0
                        var deletedCount = 0
                        
                        allCursor?.use {
                            totalCount = it.count
                            val idIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
                            val dirtyIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DIRTY)
                            val deletedIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DELETED)
                            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY)
                            
                            while (it.moveToNext()) {
                                val id = it.getLong(idIndex)
                                val dirty = it.getInt(dirtyIndex)
                                val deleted = it.getInt(deletedIndex)
                                val name = it.getString(nameIndex) ?: "Unnamed"
                                
                                if (dirty == 1) dirtyCount++
                                if (deleted == 1) deletedCount++
                                
                                // Log first 5 contacts for debugging
                                if (it.position < 5) {
                                    Timber.d("         Contact #${it.position + 1}: id=$id, name='$name', DIRTY=$dirty, DELETED=$deleted")
                                }
                            }
                        }
                        
                        Timber.d("      Total contacts: $totalCount")
                        Timber.d("      Dirty contacts: $dirtyCount")
                        Timber.d("      Deleted contacts: $deletedCount")
                        
                        // Now query for dirty/deleted contacts
                        val projection = arrayOf(
                            ContactsContract.RawContacts._ID,
                            ContactsContract.RawContacts.DIRTY,
                            ContactsContract.RawContacts.DELETED
                        )
                        
                        val cursor = context.contentResolver.query(
                            ContactsContract.RawContacts.CONTENT_URI,
                            projection,
                            "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND (${ContactsContract.RawContacts.DIRTY} = 1 OR ${ContactsContract.RawContacts.DELETED} = 1)",
                            arrayOf(accountName, accountType),
                            null
                        )
                        
                        cursor?.use {
                            dirtyCount = it.count
                        }
                        
                        if (dirtyCount > 0) {
                            Timber.d("   ✓ Address book '${addressBook.displayName}' has $dirtyCount dirty contact(s)")
                            addressBooksWithChanges.add(Pair(account.id, addressBook.id))
                        } else {
                            Timber.d("   ○ Address book '${addressBook.displayName}' has no dirty contacts (skipping)")
                        }
                    }
                }
                
                if (addressBooksWithChanges.isEmpty()) {
                    Timber.d("========================================")
                    Timber.d("✓ No dirty contacts found - nothing to sync")
                    Timber.d("========================================")
                    return@launch
                }
                
                Timber.d("========================================")
                Timber.d("Found dirty contacts in ${addressBooksWithChanges.size} address book(s)")
                Timber.d("Triggering push-only sync for affected address books only...")
                Timber.d("========================================")
                
                // Trigger push-only sync for address books with changes
                for ((accountId, addressBookId) in addressBooksWithChanges) {
                    val addressBook = addressBookRepository.getById(addressBookId)
                    Timber.d("🚀 Triggering push-only sync for address book: ${addressBook?.displayName} (ID: $addressBookId)")
                    syncManager.syncAddressBookPushOnly(accountId, addressBookId)
                }
                
                Timber.d("========================================")
                Timber.d("✅ Push-only sync triggered for ${addressBooksWithChanges.size} address book(s)")
                Timber.d("========================================")
            } catch (e: Exception) {
                Timber.e(e, "Failed to trigger push-only contacts sync")
            }
        }
    }
}
