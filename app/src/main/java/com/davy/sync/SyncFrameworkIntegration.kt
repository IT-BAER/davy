package com.davy.sync

import android.accounts.Account
import android.content.ContentResolver
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around Android's SyncAdapter framework APIs.
 * Centralizes all SyncAdapter-related operations for easier maintenance
 * and to apply Android version-specific workarounds in one place.
 * 
 * Benefits:
 * - Single point of truth for framework interactions
 * - Easier to apply Android version-specific workarounds
 * - Better testability through abstraction
 * - Consistent error handling and logging
 */
@Singleton
class SyncFrameworkIntegration @Inject constructor() {
    
    companion object {
        private const val TAG = "SyncFrameworkIntegration"
    }
    
    /**
     * Enable content-triggered sync for contacts.
     * When enabled, Android will automatically trigger sync when contacts are edited
     * in native Android Contacts app.
     * 
     * This enables immediate push of local changes to the server without waiting
     * for periodic sync or manual user action.
     * 
     * @param account The account to enable sync for
     */
    fun enableContactSyncOnContentChange(account: Account) {
        try {
            // Make the account syncable for contacts
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
            
            // Enable automatic sync when content changes
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
            
            // Note: We do NOT add periodic syncs here - those are managed by WorkManager
            // This is purely for content-triggered (reactive) sync
            // Remove any periodic syncs that might have been added by the framework
            val periodicSyncs = ContentResolver.getPeriodicSyncs(account, ContactsContract.AUTHORITY)
            periodicSyncs.forEach { periodicSync ->
                ContentResolver.removePeriodicSync(account, ContactsContract.AUTHORITY, periodicSync.extras)
                Timber.tag(TAG).d("Removed framework-added periodic sync for contacts")
            }
            
            Timber.tag(TAG).d("✓ Enabled content-triggered sync for contacts: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to enable content-triggered sync for: ${account.name}")
        }
    }
    
    /**
     * Enable content-triggered sync for calendars.
     * When enabled, Android will automatically trigger sync when calendar events are edited
     * in native Android Calendar app.
     * 
     * @param account The account to enable sync for
     */
    fun enableCalendarSyncOnContentChange(account: Account) {
        try {
            // Make the account syncable for calendars
            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
            
            // Enable automatic sync when content changes
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
            
            // Remove any periodic syncs added by framework (we use WorkManager instead)
            val periodicSyncs = ContentResolver.getPeriodicSyncs(account, CalendarContract.AUTHORITY)
            periodicSyncs.forEach { periodicSync ->
                ContentResolver.removePeriodicSync(account, CalendarContract.AUTHORITY, periodicSync.extras)
                Timber.tag(TAG).d("Removed framework-added periodic sync for calendars")
            }
            
            Timber.tag(TAG).d("✓ Enabled content-triggered sync for calendars: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to enable content-triggered sync for calendars: ${account.name}")
        }
    }
    
    /**
     * Disable content-triggered sync for contacts.
     * 
     * @param account The account to disable sync for
     */
    fun disableContactSyncOnContentChange(account: Account) {
        try {
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, false)
            Timber.tag(TAG).d("✓ Disabled content-triggered sync for contacts: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to disable content-triggered sync for: ${account.name}")
        }
    }
    
    /**
     * Disable content-triggered sync for calendars.
     * 
     * @param account The account to disable sync for
     */
    fun disableCalendarSyncOnContentChange(account: Account) {
        try {
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, false)
            Timber.tag(TAG).d("✓ Disabled content-triggered sync for calendars: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to disable content-triggered sync for calendars: ${account.name}")
        }
    }
    
    /**
     * Request a manual sync for contacts.
     * Sets MANUAL and EXPEDITED flags to ensure sync executes immediately.
     * 
     * @param account The account to sync
     */
    fun requestManualContactSync(account: Account) {
        try {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            
            ContentResolver.requestSync(account, ContactsContract.AUTHORITY, extras)
            Timber.tag(TAG).d("✓ Requested manual contact sync for: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to request manual contact sync for: ${account.name}")
        }
    }
    
    /**
     * Request a manual sync for calendars.
     * Sets MANUAL and EXPEDITED flags to ensure sync executes immediately.
     * 
     * @param account The account to sync
     */
    fun requestManualCalendarSync(account: Account) {
        try {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            
            ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
            Timber.tag(TAG).d("✓ Requested manual calendar sync for: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to request manual calendar sync for: ${account.name}")
        }
    }
    
    /**
     * Check if content-triggered sync is enabled for contacts.
     * 
     * @param account The account to check
     * @return true if auto-sync is enabled, false otherwise
     */
    fun isContactSyncEnabled(account: Account): Boolean {
        return try {
            ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to check contact sync status for: ${account.name}")
            false
        }
    }
    
    /**
     * Check if content-triggered sync is enabled for calendars.
     * 
     * @param account The account to check
     * @return true if auto-sync is enabled, false otherwise
     */
    fun isCalendarSyncEnabled(account: Account): Boolean {
        return try {
            ContentResolver.getSyncAutomatically(account, CalendarContract.AUTHORITY)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to check calendar sync status for: ${account.name}")
            false
        }
    }
    
    /**
     * Check if the account is syncable for contacts.
     * 
     * @param account The account to check
     * @return 1 if syncable, 0 if not syncable, -1 if unknown
     */
    fun getContactSyncability(account: Account): Int {
        return try {
            ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to check contact syncability for: ${account.name}")
            -1
        }
    }
    
    /**
     * Cancel any pending sync for contacts.
     * Useful as workaround for Android 14+ pending sync indicator bug.
     * 
     * @param account The account to cancel sync for
     */
    fun cancelPendingContactSync(account: Account) {
        try {
            ContentResolver.cancelSync(account, ContactsContract.AUTHORITY)
            Timber.tag(TAG).d("✓ Cancelled pending contact sync for: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to cancel pending contact sync for: ${account.name}")
        }
    }
    
    /**
     * Cancel any pending sync for calendars.
     * Useful as workaround for Android 14+ pending sync indicator bug.
     * 
     * @param account The account to cancel sync for
     */
    fun cancelPendingCalendarSync(account: Account) {
        try {
            ContentResolver.cancelSync(account, CalendarContract.AUTHORITY)
            Timber.tag(TAG).d("✓ Cancelled pending calendar sync for: ${account.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "✗ Failed to cancel pending calendar sync for: ${account.name}")
        }
    }
    
    /**
     * Apply Android 14+ workaround for pending sync indicator bug.
     * On Android 14+, the pending sync indicator may get stuck.
     * This method cancels the pending sync to clear the indicator.
     * 
     * @param account The account to apply workaround for
     * @param authority The authority (ContactsContract.AUTHORITY or CalendarContract.AUTHORITY)
     */
    fun applyAndroid14PendingSyncWorkaround(account: Account, authority: String) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ContentResolver.cancelSync(account, authority)
                Timber.tag(TAG).d("✓ Applied Android 14+ pending sync workaround for ${account.name} / $authority")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "✗ Failed to apply Android 14+ workaround for: ${account.name}")
            }
        }
    }
    
    /**
     * Enable content-triggered sync for all supported authorities (contacts + calendars).
     * 
     * @param account The account to enable sync for
     */
    fun enableAllContentTriggeredSync(account: Account) {
        enableContactSyncOnContentChange(account)
        enableCalendarSyncOnContentChange(account)
    }
    
    /**
     * Disable content-triggered sync for all supported authorities (contacts + calendars).
     * 
     * @param account The account to disable sync for
     */
    fun disableAllContentTriggeredSync(account: Account) {
        disableContactSyncOnContentChange(account)
        disableCalendarSyncOnContentChange(account)
    }
    
    /**
     * Get sync status summary for debugging.
     * 
     * @param account The account to check
     * @return Map of authority to sync status
     */
    fun getSyncStatusSummary(account: Account): Map<String, SyncStatus> {
        return mapOf(
            "Contacts" to SyncStatus(
                authority = ContactsContract.AUTHORITY,
                isSyncable = getContactSyncability(account),
                autoSyncEnabled = isContactSyncEnabled(account)
            ),
            "Calendars" to SyncStatus(
                authority = CalendarContract.AUTHORITY,
                isSyncable = ContentResolver.getIsSyncable(account, CalendarContract.AUTHORITY),
                autoSyncEnabled = isCalendarSyncEnabled(account)
            )
        )
    }
    
    /**
     * Data class representing sync status for an authority.
     */
    data class SyncStatus(
        val authority: String,
        val isSyncable: Int,  // 1=syncable, 0=not syncable, -1=unknown
        val autoSyncEnabled: Boolean
    ) {
        override fun toString(): String {
            val syncableStr = when (isSyncable) {
                1 -> "syncable"
                0 -> "not syncable"
                else -> "unknown"
            }
            return "$authority: $syncableStr, auto-sync=${if (autoSyncEnabled) "ON" else "OFF"}"
        }
    }
}
