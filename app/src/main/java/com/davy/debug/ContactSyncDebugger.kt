package com.davy.debug

import android.content.Context
import android.provider.ContactsContract
import com.davy.sync.account.AndroidAccountManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug utility to diagnose contact sync issues.
 * 
 * This helps identify why contacts aren't syncing by checking:
 * - Which Android account the contact is assigned to
 * - Whether the contact is marked as dirty
 * - Whether the contact is in the correct address book account
 */
@Singleton
class ContactSyncDebugger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    data class ContactDebugInfo(
        val rawContactId: Long,
        val displayName: String?,
        val accountName: String?,
        val accountType: String?,
        val isDirty: Boolean,
        val isDeleted: Boolean,
        val sourceId: String?,
        val version: Int
    )
    
    /**
     * Find all contacts with a specific name across ALL accounts.
     * This helps determine which account a contact belongs to.
     */
    fun findContactsByName(searchName: String): List<ContactDebugInfo> {
        val results = mutableListOf<ContactDebugInfo>()
        
        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.DIRTY,
            ContactsContract.RawContacts.DELETED,
            ContactsContract.RawContacts.SOURCE_ID,
            ContactsContract.RawContacts.VERSION
        )
        
        val selection = "${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$searchName%")
        
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY)
            val accountNameIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME)
            val accountTypeIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val dirtyIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DIRTY)
            val deletedIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DELETED)
            val sourceIdIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.SOURCE_ID)
            val versionIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.VERSION)
            
            while (it.moveToNext()) {
                results.add(
                    ContactDebugInfo(
                        rawContactId = it.getLong(idIndex),
                        displayName = it.getString(nameIndex),
                        accountName = it.getString(accountNameIndex),
                        accountType = it.getString(accountTypeIndex),
                        isDirty = it.getInt(dirtyIndex) == 1,
                        isDeleted = it.getInt(deletedIndex) == 1,
                        sourceId = it.getString(sourceIdIndex),
                        version = it.getInt(versionIndex)
                    )
                )
            }
        }
        
        return results
    }
    
    /**
     * Get all DAVy contacts and their sync status.
     */
    fun getAllDavyContacts(): List<ContactDebugInfo> {
        val results = mutableListOf<ContactDebugInfo>()
        
        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.DIRTY,
            ContactsContract.RawContacts.DELETED,
            ContactsContract.RawContacts.SOURCE_ID,
            ContactsContract.RawContacts.VERSION
        )
        
        // Query for both main DAVy accounts and address book accounts
        val selection = "${ContactsContract.RawContacts.ACCOUNT_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            AndroidAccountManager.ACCOUNT_TYPE,
            AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
        )
        
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY)
            val accountNameIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME)
            val accountTypeIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val dirtyIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DIRTY)
            val deletedIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.DELETED)
            val sourceIdIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.SOURCE_ID)
            val versionIndex = it.getColumnIndexOrThrow(ContactsContract.RawContacts.VERSION)
            
            while (it.moveToNext()) {
                results.add(
                    ContactDebugInfo(
                        rawContactId = it.getLong(idIndex),
                        displayName = it.getString(nameIndex),
                        accountName = it.getString(accountNameIndex),
                        accountType = it.getString(accountTypeIndex),
                        isDirty = it.getInt(dirtyIndex) == 1,
                        isDeleted = it.getInt(deletedIndex) == 1,
                        sourceId = it.getString(sourceIdIndex),
                        version = it.getInt(versionIndex)
                    )
                )
            }
        }
        
        return results
    }
    
    /**
     * Get detailed diagnostics for specific contacts by name.
     * Prints comprehensive information to help debug sync issues.
     */
    fun diagnoseContactsByName(searchName: String) {
        Timber.d("========================================")
        Timber.d("CONTACT SYNC DIAGNOSTIC: Searching for '$searchName'")
        Timber.d("========================================")
        
        val contacts = findContactsByName(searchName)
        
        if (contacts.isEmpty()) {
            Timber.w("❌ No contacts found with name matching: $searchName")
            Timber.w("   This means:")
            Timber.w("   1. Contact doesn't exist in ContactsContract database")
            Timber.w("   2. Contact may have been deleted")
            Timber.w("   3. Contact name doesn't match search term")
            return
        }
        
        Timber.d("Found ${contacts.size} contact(s) matching '$searchName':")
        Timber.d("========================================")
        
        for (contact in contacts) {
            Timber.d("")
            Timber.d("Contact: ${contact.displayName}")
            Timber.d("  Raw Contact ID: ${contact.rawContactId}")
            Timber.d("  Account Name: ${contact.accountName}")
            Timber.d("  Account Type: ${contact.accountType}")
            Timber.d("  Source ID: ${contact.sourceId ?: "null"}")
            Timber.d("  Version: ${contact.version}")
            Timber.d("  Is Dirty: ${contact.isDirty}")
            Timber.d("  Is Deleted: ${contact.isDeleted}")
            
            // Diagnose issues
            if (contact.accountType == AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK) {
                Timber.d("  ✅ CORRECT: Contact is under DAVy address book account")
                
                if (contact.isDirty) {
                    Timber.d("  ✅ Contact is marked DIRTY - should sync on next push")
                } else {
                    Timber.w("  ⚠️  Contact is NOT dirty - won't sync until modified")
                    Timber.w("      This is normal if contact was already synced")
                }
                
                if (contact.isDeleted) {
                    Timber.w("  ⚠️  Contact is marked DELETED - will be removed from server")
                }
            } else if (contact.accountType == AndroidAccountManager.ACCOUNT_TYPE) {
                Timber.d("  ✅ Contact is under DAVy main account")
                
                if (contact.isDirty) {
                    Timber.d("  ✅ Contact is marked DIRTY - should sync on next push")
                } else {
                    Timber.w("  ⚠️  Contact is NOT dirty - won't sync until modified")
                }
            } else {
                Timber.e("  ❌ PROBLEM: Contact is under WRONG account type!")
                Timber.e("     Expected: '${AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK}'")
                Timber.e("     Or: '${AndroidAccountManager.ACCOUNT_TYPE}'")
                Timber.e("     Actual: '${contact.accountType}'")
                Timber.e("     ")
                Timber.e("  🔧 SOLUTION:")
                Timber.e("     1. Open Contacts app")
                Timber.e("     2. Find contact: ${contact.displayName}")
                Timber.e("     3. Edit contact")
                Timber.e("     4. Tap 'Move to another account'")
                Timber.e("     5. Select the DAVy account")
                Timber.e("     6. Save")
                Timber.e("     ")
                Timber.e("  Or create new contacts under DAVy account:")
                Timber.e("     1. When creating contact, select 'Account' dropdown")
                Timber.e("     2. Choose DAVy account (${contact.accountName})")
                Timber.e("     3. Then fill in contact details")
            }
        }
        
        Timber.d("========================================")
        Timber.d("DIAGNOSTIC COMPLETE")
        Timber.d("========================================")
    }
    
    /**
     * Print a summary of all DAVy contacts and their sync status.
     */
    fun printDavyContactsSummary() {
        Timber.d("========================================")
        Timber.d("ALL DAVY CONTACTS SUMMARY")
        Timber.d("========================================")
        
        val contacts = getAllDavyContacts()
        
        if (contacts.isEmpty()) {
            Timber.w("No DAVy contacts found in ContactsContract")
            Timber.w("  This means:")
            Timber.w("  1. No contacts have been synced yet, OR")
            Timber.w("  2. All contacts are under different account types (Google, Samsung, etc.)")
            return
        }
        
        val dirtyCount = contacts.count { it.isDirty }
        val deletedCount = contacts.count { it.isDeleted }
        val addressBookCount = contacts.count { it.accountType == AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK }
        
        Timber.d("Total DAVy contacts: ${contacts.size}")
        Timber.d("  - Address book accounts: $addressBookCount")
        Timber.d("  - Main account: ${contacts.size - addressBookCount}")
        Timber.d("  - Dirty (needs sync): $dirtyCount")
        Timber.d("  - Deleted (pending removal): $deletedCount")
        Timber.d("")
        Timber.d("Contacts list:")
        
        for (contact in contacts) {
            val statusFlags = mutableListOf<String>()
            if (contact.isDirty) statusFlags.add("DIRTY")
            if (contact.isDeleted) statusFlags.add("DELETED")
            val statusStr = if (statusFlags.isEmpty()) "synced" else statusFlags.joinToString(", ")
            
            Timber.d("  - ${contact.displayName} [ID: ${contact.rawContactId}] ($statusStr)")
            Timber.d("    Account: ${contact.accountName}")
        }
        
        Timber.d("========================================")
    }
}
