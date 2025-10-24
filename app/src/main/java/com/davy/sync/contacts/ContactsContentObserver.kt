package com.davy.sync.contacts

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.davy.sync.account.AndroidAccountManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
    private val androidAccountManager: AndroidAccountManager
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
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
        triggerContactsSync()
    }
    
    /**
     * Start observing contact changes.
     */
    fun startObserving(contentResolver: ContentResolver) {
        Timber.d("========================================")
        Timber.d("Starting contacts content observation")
        Timber.d("========================================")
        
        // Observe raw contacts (this includes all data changes)
        contentResolver.registerContentObserver(
            ContactsContract.RawContacts.CONTENT_URI,
            true,
            this
        )
        
        Timber.d("Registered observer for RawContacts URI: ${ContactsContract.RawContacts.CONTENT_URI}")
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
     * Triggers an immediate sync for all DAVy address book accounts.
     */
    private fun triggerContactsSync() {
        try {
            Timber.d("Triggering immediate contacts sync for all DAVy accounts")
            
            // Get all DAVy main accounts (Android accounts)
            val mainAccounts = androidAccountManager.getAllAccounts()
            
            for (mainAccount in mainAccounts) {
                // Get address book accounts for this main account
                val addressBookAccounts = androidAccountManager.getAddressBookAccounts(mainAccount.name)
                
                for (addressBookAccount in addressBookAccounts) {
                    Timber.d("🔄 Requesting sync for address book account: ${addressBookAccount.name}")
                    
                    // Request immediate sync with expedited flag
                    val extras = Bundle().apply {
                        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true) // Upload changes only
                    }
                    
                    ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, extras)
                }
            }
            
            Timber.d("✅ Sync requests sent for all address book accounts")
        } catch (e: Exception) {
            Timber.e(e, "Failed to trigger contacts sync")
        }
    }
}
