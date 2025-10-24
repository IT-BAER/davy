package com.davy.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for managing Android system accounts for DAVy.
 * 
 * This creates accounts in Android's AccountManager so that they can
 * be used with the sync framework and appear in system settings.
 */
@Singleton
class AndroidAccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val ACCOUNT_TYPE = "com.davy"
        const val ACCOUNT_TYPE_ADDRESS_BOOK = "com.davy.addressbook"
        private const val CALENDAR_AUTHORITY = "com.android.calendar"
        private const val CONTACTS_AUTHORITY = "com.android.contacts"
        
        // UserData keys for address book accounts
        private const val USER_DATA_MAIN_ACCOUNT_NAME = "main_account_name"
        private const val USER_DATA_ADDRESS_BOOK_ID = "address_book_id"
        private const val USER_DATA_ADDRESS_BOOK_URL = "address_book_url"
    }
    
    private val accountManager: AccountManager = AccountManager.get(context)
    
    /**
     * Create or update an Android account for a DAVy account.
     * 
     * @param accountName The account name (e.g., email or username)
     * @param password The account password
     * @return true if account was created/updated successfully
     */
    fun createOrUpdateAccount(accountName: String, password: String?): Boolean {
        try {
            val account = Account(accountName, ACCOUNT_TYPE)
            
            // Check if account already exists
            val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            val accountExists = existingAccounts.any { it.name == accountName }
            
            if (!accountExists) {
                // Add new account
                val added = accountManager.addAccountExplicitly(account, password, null)
                
                if (!added) {
                    Timber.e("Failed to create Android account: $accountName")
                    return false
                }
                
                Timber.d("Created Android account: $accountName")
            } else {
                // Account exists, update password if provided
                if (password != null) {
                    accountManager.setPassword(account, password)
                    Timber.d("Updated password for Android account: $accountName")
                }
            }
            
            // Configure calendar sync (syncable and automatic sync enabled by default)
            // Enabling here ensures the Calendar app shows the account as syncing without extra user steps
            ContentResolver.setIsSyncable(account, CALENDAR_AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CALENDAR_AUTHORITY, true)

            // Configure contacts sync (keep automatic sync disabled unless explicitly enabled later)
            ContentResolver.setIsSyncable(account, CONTACTS_AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CONTACTS_AUTHORITY, false)
            
            Timber.d("Configured calendar (auto-sync ON) and contacts (auto-sync OFF) for Android account: $accountName")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "Exception creating/updating Android account: $accountName")
            return false
        }
    }
    
    /**
     * Remove an Android account.
     */
    fun removeAccount(accountName: String): Boolean {
        try {
            val account = Account(accountName, ACCOUNT_TYPE)
            val result = accountManager.removeAccountExplicitly(account)
            
            if (result) {
                Timber.d("Removed Android account: $accountName")
            } else {
                Timber.e("Failed to remove Android account: $accountName")
            }
            
            return result
            
        } catch (e: Exception) {
            Timber.e(e, "Exception removing Android account: $accountName")
            return false
        }
    }
    
    /**
     * Rename an Android account.
     * Uses Android's renameAccount API which properly migrates all data.
     * 
     * @param oldName The current account name
     * @param newName The new account name
     * @return true if rename was successful
     */
    fun renameAccount(oldName: String, newName: String): Boolean {
        try {
            val oldAccount = Account(oldName, ACCOUNT_TYPE)
            
            // Check if old account exists
            val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            val accountExists = existingAccounts.any { it.name == oldName }
            
            if (!accountExists) {
                Timber.e("Cannot rename: Android account does not exist: $oldName")
                return false
            }
            
            // Check if new name already exists
            if (existingAccounts.any { it.name == newName }) {
                Timber.e("Cannot rename: Account with new name already exists: $newName")
                return false
            }
            
            // Android's renameAccount properly migrates all calendar and contact data
            val future = accountManager.renameAccount(oldAccount, newName, null, null)
            val renamedAccount = future.result
            
            if (renamedAccount != null && renamedAccount.name == newName) {
                Timber.d("Successfully renamed Android account from '$oldName' to '$newName'")
                
                // Verify sync settings are maintained
                ContentResolver.setIsSyncable(renamedAccount, CALENDAR_AUTHORITY, 1)
                ContentResolver.setSyncAutomatically(renamedAccount, CALENDAR_AUTHORITY, true)
                ContentResolver.setIsSyncable(renamedAccount, CONTACTS_AUTHORITY, 1)
                ContentResolver.setSyncAutomatically(renamedAccount, CONTACTS_AUTHORITY, true)
                
                return true
            } else {
                Timber.e("Failed to rename Android account from '$oldName' to '$newName'")
                return false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception renaming Android account from '$oldName' to '$newName'")
            return false
        }
    }
    
    /**
     * Update address book accounts to reference the new main account name after rename.
     * 
     * When the main account is renamed, all associated address book accounts need their
     * USER_DATA_MAIN_ACCOUNT_NAME updated to the new name AND the account name itself
     * needs to be updated to reflect the new main account name.
     * 
    * This matches the behavior where address book account names include the main
    * account name, e.g., "Contacts (MainAccount) #1"
     * 
     * @param oldAccountName The old main account name
     * @param newAccountName The new main account name
     */
    fun updateAddressBookAccountReferences(oldAccountName: String, newAccountName: String) {
        try {
            val addressBookAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE_ADDRESS_BOOK)
            var updatedCount = 0
            
            addressBookAccounts.forEach { addressBookAccount ->
                val mainAccountName = accountManager.getUserData(addressBookAccount, USER_DATA_MAIN_ACCOUNT_NAME)
                
                if (mainAccountName == oldAccountName) {
                    // Update USER_DATA to new main account name
                    accountManager.setUserData(addressBookAccount, USER_DATA_MAIN_ACCOUNT_NAME, newAccountName)
                    
                    // Also rename the address book account itself to reflect the new main account name
                    // Old name format: "AddressBookName (OldMainAccount) #id"
                    // New name format: "AddressBookName (NewMainAccount) #id"
                    val oldAddressBookName = addressBookAccount.name
                    val newAddressBookName = oldAddressBookName.replace("($oldAccountName)", "($newAccountName)")
                    
                    if (newAddressBookName != oldAddressBookName) {
                        try {
                            val future = accountManager.renameAccount(addressBookAccount, newAddressBookName, null, null)
                            val renamedAccount = future.result
                            
                            if (renamedAccount != null && renamedAccount.name == newAddressBookName) {
                                Timber.d("Renamed address book account from '$oldAddressBookName' to '$newAddressBookName'")
                            } else {
                                Timber.w("Failed to rename address book account '$oldAddressBookName'")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Exception renaming address book account '$oldAddressBookName'")
                        }
                    }
                    
                    updatedCount++
                    Timber.d("Updated address book '${addressBookAccount.name}' to reference new account '$newAccountName'")
                }
            }
            
            Timber.d("Updated $updatedCount address book accounts to reference '$newAccountName'")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update address book account references")
        }
    }
    
    /**
     * Update calendar provider to reference the new account name after rename.
     * 
     * When the main account is renamed, all calendars need their ACCOUNT_NAME field
     * updated in the calendar provider so they continue to appear in the Calendar app.
     * 
    * This aligns with LocalCalendarStore.updateAccount() behavior.
     * 
     * @param oldAccountName The old main account name
     * @param newAccountName The new main account name
     */
    fun updateCalendarProviderAccount(oldAccountName: String, newAccountName: String) {
        try {
            val oldAccount = Account(oldAccountName, ACCOUNT_TYPE)
            @Suppress("UNUSED_VARIABLE")
            val newAccount = Account(newAccountName, ACCOUNT_TYPE)
            
            // Update calendar provider
            val values = android.content.ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, newAccountName)
                // Keep OWNER_ACCOUNT consistent with the new account to avoid provider inconsistencies
                put(CalendarContract.Calendars.OWNER_ACCOUNT, newAccountName)
            }
            
            // Use sync adapter URI to bypass permission checks
            val uri = CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(oldAccount)
            
            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { client ->
                val updated = client.update(
                    uri,
                    values,
                    "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
                    arrayOf(oldAccountName, ACCOUNT_TYPE)
                )
                
                Timber.d("Updated $updated calendars in Calendar provider from '$oldAccountName' to '$newAccountName'")
            } ?: Timber.e("Failed to acquire Calendar content provider client")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update calendar provider account")
        }
    }
    
    /**
     * Extension function to create sync adapter URI.
     * This adds the caller_is_syncadapter parameter which bypasses some permission checks.
     */
    private fun android.net.Uri.asSyncAdapter(account: Account): android.net.Uri {
        return this.buildUpon()
            .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
            .build()
    }
    
    /**
     * Trigger a manual sync for an account.
     */
    fun requestSync(accountName: String) {
        try {
            val account = Account(accountName, ACCOUNT_TYPE)
            val bundle = android.os.Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            
            ContentResolver.requestSync(account, CALENDAR_AUTHORITY, bundle)
            Timber.d("Requested sync for Android account: $accountName")
            
        } catch (e: Exception) {
            Timber.e(e, "Exception requesting sync: $accountName")
        }
    }
    
    /**
     * Check if an Android account exists.
     */
    fun accountExists(accountName: String): Boolean {
        val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        return existingAccounts.any { it.name == accountName }
    }
    
    /**
     * Get all DAVy Android accounts.
     */
    fun getAllAccounts(): List<Account> {
        return accountManager.getAccountsByType(ACCOUNT_TYPE).toList()
    }
    
    // ========== ADDRESS BOOK ACCOUNT METHODS ==========
    
    /**
     * Create an Android account for a specific address book.
     * 
     * Following reference implementation architecture: each CardDAV address book gets its own Android account
     * with account type ACCOUNT_TYPE_ADDRESS_BOOK. This enables contacts to be editable.
     * 
     * @param mainAccountName The name of the main DAVy account
     * @param addressBookName The display name for this address book
     * @param addressBookId The database ID of the address book
     * @param addressBookUrl The CardDAV URL of the address book
     * @return The created Account, or null if creation failed
     */
    fun createAddressBookAccount(
        mainAccountName: String,
        addressBookName: String,
        addressBookId: Long,
        addressBookUrl: String
    ): Account? {
        try {
            // Create unique account name: "AddressBookName (mainAccount) #id"
            val accountName = "$addressBookName ($mainAccountName) #$addressBookId"
            val account = Account(accountName, ACCOUNT_TYPE_ADDRESS_BOOK)
            
            // Check if account already exists
            val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE_ADDRESS_BOOK)
            if (existingAccounts.any { it.name == accountName }) {
                Timber.d("Address book account already exists: $accountName")
                return account
            }
            
            // Create account with user data
            val userData = android.os.Bundle().apply {
                putString(USER_DATA_MAIN_ACCOUNT_NAME, mainAccountName)
                putString(USER_DATA_ADDRESS_BOOK_ID, addressBookId.toString())
                putString(USER_DATA_ADDRESS_BOOK_URL, addressBookUrl)
                // Mark account as writable (attempt to signal to Contacts app)
                putString("writable", "1")
            }
            
            val added = accountManager.addAccountExplicitly(account, null, userData)
            
            if (!added) {
                Timber.e("Failed to create address book account: $accountName")
                return null
            }
            
            // Configure contacts sync for address book account (syncable, but NO automatic periodic sync)
            ContentResolver.setIsSyncable(account, CONTACTS_AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CONTACTS_AUTHORITY, false)
            
            // Create default group for the address book account
            // This is CRITICAL for contacts to be visible in Contacts app filter
            createDefaultGroup(account, addressBookName)
            
            // Set contacts provider settings for the address book account
            // UNGROUPED_VISIBLE=1 is required for account to appear in Contacts app filter
            setContactsProviderSettings(account)
            
            Timber.d("Created address book account: $accountName")
            return account
            
        } catch (e: Exception) {
            Timber.e(e, "Exception creating address book account")
            return null
        }
    }
    
    /**
     * Get all address book accounts for a main account.
     */
    fun getAddressBookAccounts(mainAccountName: String): List<Account> {
        return try {
            accountManager.getAccountsByType(ACCOUNT_TYPE_ADDRESS_BOOK)
                .filter { account ->
                    val mainAccount = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME)
                    mainAccount == mainAccountName
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception getting address book accounts")
            emptyList()
        }
    }

    /**
     * Find a specific address book account by its associated addressBookId and main account.
     */
    fun findAddressBookAccount(mainAccountName: String, addressBookId: Long): Account? {
        return try {
            getAddressBookAccounts(mainAccountName).firstOrNull { acct ->
                val id = getAddressBookId(acct)
                id == addressBookId
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception finding address book account for id=$addressBookId")
            null
        }
    }
    
    /**
     * Remove all address book accounts associated with a main account.
     */
    fun removeAddressBookAccounts(mainAccountName: String): Boolean {
        return try {
            val addressBookAccounts = getAddressBookAccounts(mainAccountName)
            var allRemoved = true
            
            for (addressBookAccount in addressBookAccounts) {
                val removed = accountManager.removeAccountExplicitly(addressBookAccount)
                if (!removed) {
                    Timber.e("Failed to remove address book account: ${addressBookAccount.name}")
                    allRemoved = false
                } else {
                    Timber.d("Removed address book account: ${addressBookAccount.name}")
                }
            }
            
            allRemoved
        } catch (e: Exception) {
            Timber.e(e, "Exception removing address book accounts")
            false
        }
    }

    /**
     * Remove a single address book account by ID for the given main account.
     */
    fun removeAddressBookAccountById(mainAccountName: String, addressBookId: Long): Boolean {
        return try {
            val acct = findAddressBookAccount(mainAccountName, addressBookId)
            if (acct == null) {
                Timber.w("Address book account not found for main='$mainAccountName', id=$addressBookId")
                return false
            }
            val removed = accountManager.removeAccountExplicitly(acct)
            if (removed) Timber.d("Removed address book account: ${acct.name}") else Timber.e("Failed to remove address book account: ${acct.name}")
            removed
        } catch (e: Exception) {
            Timber.e(e, "Exception removing address book account id=$addressBookId")
            false
        }
    }
    
    /**
     * Get the address book ID from an address book account.
     */
    fun getAddressBookId(addressBookAccount: Account): Long? {
        return try {
            accountManager.getUserData(addressBookAccount, USER_DATA_ADDRESS_BOOK_ID)?.toLongOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Exception getting address book ID")
            null
        }
    }
    
    /**
     * Get the main account name from an address book account.
     */
    fun getMainAccountName(addressBookAccount: Account): String? {
        return try {
            accountManager.getUserData(addressBookAccount, USER_DATA_MAIN_ACCOUNT_NAME)
        } catch (e: Exception) {
            Timber.e(e, "Exception getting main account name")
            null
        }
    }
    
    /**
     * Create default group for address book account.
     * This is CRITICAL for contacts to show in Contacts app filter.
     */
    private fun createDefaultGroup(account: Account, groupName: String) {
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.ContactsContract.Groups.ACCOUNT_NAME, account.name)
                put(android.provider.ContactsContract.Groups.ACCOUNT_TYPE, account.type)
                put(android.provider.ContactsContract.Groups.TITLE, groupName)
                put(android.provider.ContactsContract.Groups.GROUP_VISIBLE, 1)  // VISIBLE!
                put(android.provider.ContactsContract.Groups.SHOULD_SYNC, 1)
            }
            
            context.contentResolver.insert(
                android.provider.ContactsContract.Groups.CONTENT_URI,
                values
            )
            
            Timber.d("Created default group for account: ${account.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create default group for account: ${account.name}")
        }
    }
    
    /**
     * Set ContactsContract.Settings for address book account.
     * UNGROUPED_VISIBLE=1 is CRITICAL for account to appear in Contacts app filter.
     */
    private fun setContactsProviderSettings(account: Account) {
        try {
            val uri = android.provider.ContactsContract.Settings.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(android.provider.ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                .appendQueryParameter(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                .build()
            
            val values = android.content.ContentValues().apply {
                put(android.provider.ContactsContract.Settings.ACCOUNT_NAME, account.name)
                put(android.provider.ContactsContract.Settings.ACCOUNT_TYPE, account.type)
                put(android.provider.ContactsContract.Settings.SHOULD_SYNC, 1)
                put(android.provider.ContactsContract.Settings.UNGROUPED_VISIBLE, 1)  // CRITICAL!
            }
            
            context.contentResolver.insert(uri, values)
            
            Timber.d("Set contacts provider settings for account: ${account.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set contacts provider settings for account: ${account.name}")
        }
    }
}
