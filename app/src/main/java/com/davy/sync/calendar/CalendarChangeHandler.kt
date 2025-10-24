package com.davy.sync.calendar

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.data.repository.CalendarRepository
import com.davy.sync.SyncLock
import com.davy.sync.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles calendar changes detected by CalendarContentObserver.
 * 
 * Processes changes to determine which events need to be pushed to the server
 * and triggers appropriate sync operations.
 */
@ServiceScoped
class CalendarChangeHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: CalendarEventRepository,
    private val syncManager: SyncManager,
    private val reverseSync: CalendarProviderToDAVySync,
    private val syncLock: SyncLock
) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    @Volatile
    private var hasPendingChanges = false
    private var pendingChangeType: CalendarChangeType? = null
    private var pendingChangeUri: Uri? = null
    
    companion object {
        private const val ACCOUNT_TYPE = "com.davy"
    }
    
    /**
     * Handle a calendar change event.
     */
    fun handleChange(changeType: CalendarChangeType, uri: Uri?) {
        scope.launch {
            // Don't process changes during active sync operations, but remember them
            if (syncLock.isSyncing()) {
                Timber.d("⏸️ Sync in progress - queuing Calendar Provider change for later processing")
                hasPendingChanges = true
                pendingChangeType = changeType
                pendingChangeUri = uri
                
                // Wait for sync to finish, then process
                scope.launch {
                    var attempts = 0
                    while (syncLock.isSyncing() && attempts < 30) { // Max 30 seconds wait
                        delay(1000)
                        attempts++
                    }
                    
                    if (hasPendingChanges && !syncLock.isSyncing()) {
                        Timber.d("✅ Sync finished - processing queued changes")
                        hasPendingChanges = false
                        val type = pendingChangeType ?: CalendarChangeType.UNKNOWN
                        val u = pendingChangeUri
                        pendingChangeType = null
                        pendingChangeUri = null
                        
                        handleChangeInternal(type, u)
                    }
                }
                return@launch
            }
            
            handleChangeInternal(changeType, uri)
        }
    }
    
    /**
     * Internal change handler - actually processes the change.
     */
    private suspend fun handleChangeInternal(changeType: CalendarChangeType, uri: Uri?) {
        try {
            when (changeType) {
                CalendarChangeType.EVENT -> handleEventChange(uri)
                CalendarChangeType.CALENDAR -> handleCalendarChange(uri)
                CalendarChangeType.UNKNOWN -> handleUnknownChange()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling calendar change")
        }
    }
    
    /**
     * Handle event changes (creation, modification, deletion).
     */
    private suspend fun handleEventChange(uri: Uri?) {
        Timber.d("Handling event change: $uri")
        
        // If we have a specific event URI, extract the event ID
        val androidEventId = uri?.lastPathSegment?.toLongOrNull()
        
        if (androidEventId != null) {
            // Specific event changed
            handleSpecificEventChange(androidEventId)
        } else {
            // Bulk change or unknown - sync all DAVy calendars
            handleBulkEventChange()
        }
    }
    
    /**
     * Handle a specific event change.
     */
    private suspend fun handleSpecificEventChange(androidEventId: Long) {
        Timber.d("========================================")
        Timber.d("SPECIFIC EVENT CHANGED: Android Event ID = $androidEventId")
        Timber.d("========================================")
        
        // Query the event to get its details
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DELETED
        )
        
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events._ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                val deleted = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.DELETED))
                
                Timber.d("Event details: Title='$title', Calendar ID=$calendarId, Deleted=$deleted")
                
                // Check if this calendar belongs to a DAVy account
                val isDAVy = isDAVyCalendar(calendarId)
                Timber.d("Is DAVy calendar? $isDAVy")
                
                if (isDAVy) {
                    Timber.d("✓ Event belongs to DAVy calendar: $calendarId")
                    
                    // Get the DAVy account for this calendar
                    val account = getAccountForAndroidCalendar(calendarId)
                    
                    if (account != null) {
                        Timber.d("✓ Found DAVy account: ${account.accountName} (ID: ${account.id})")
                        
                        // Sync this event from Android back to DAVy's database
                        Timber.d("→ Starting reverse sync (Android → DAVy DB)...")
                        reverseSync.syncEventFromProvider(androidEventId)
                        Timber.d("✓ Reverse sync completed")
                        
                        // Trigger push sync to server
                        Timber.d("→ Triggering push sync to server for account: ${account.accountName}")
                        syncManager.syncNow(account.id, SyncManager.SYNC_TYPE_CALENDAR)
                        Timber.d("✓ Push sync triggered")
                    } else {
                        Timber.w("✗ Could not find DAVy account for calendar: $calendarId")
                    }
                } else {
                    Timber.d("✗ Event does not belong to DAVy calendar (skipping)")
                }
            } else {
                Timber.w("✗ Event not found in cursor")
            }
        } ?: Timber.w("✗ Cursor is null when querying event $androidEventId")
        
        Timber.d("========================================")
        Timber.d("END handleSpecificEventChange")
        Timber.d("========================================")
    }
    
    /**
     * Handle bulk event changes.
     */
    private suspend fun handleBulkEventChange() {
        Timber.d("Handling bulk event change")
        
        // Get all DAVy accounts and trigger sync for each
        val accounts = accountRepository.getAll()
        for (account in accounts) {
            if (account.calendarEnabled) {
                Timber.d("Triggering sync for account: ${account.accountName}")
                syncManager.syncNow(account.id, SyncManager.SYNC_TYPE_CALENDAR)
            }
        }
    }
    
    /**
     * Handle calendar metadata changes.
     */
    private suspend fun handleCalendarChange(uri: Uri?) {
        Timber.d("Handling calendar change: $uri")
        
        // Calendar settings may have changed (visibility, sync enabled, etc.)
        // Trigger a lightweight metadata sync
        val accounts = accountRepository.getAll()
        for (account in accounts) {
            if (account.calendarEnabled) {
                // Sync calendar settings
                Timber.d("Calendar metadata changed for account: ${account.accountName}")
            }
        }
    }
    
    /**
     * Handle unknown change type.
     */
    private suspend fun handleUnknownChange() {
        Timber.d("========================================")
        Timber.d("HANDLING UNKNOWN CHANGE")
        Timber.d("========================================")
        
    // Step 1: Sync FROM Calendar Provider TO DAVy database (reverse sync)
    Timber.d("→ Starting reverse sync for dirty DAVy calendar events...")
    reverseSync.syncDirtyEventsOnly()
        Timber.d("✓ Reverse sync completed")
        
        // Step 2: Upload dirty events to server
        Timber.d("→ Triggering push sync for all accounts...")
        syncManager.syncAllNow()
        Timber.d("✓ Push sync triggered")
        
        Timber.d("========================================")
        Timber.d("END handleUnknownChange")
        Timber.d("========================================")
    }
    
    /**
     * Check if an Android calendar belongs to a DAVy account.
     */
    private fun isDAVyCalendar(androidCalendarId: Long): Boolean {
        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(androidCalendarId.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val accountType = it.getString(0)
                return accountType == ACCOUNT_TYPE
            }
        }
        
        return false
    }
    
    /**
     * Get the DAVy account associated with an Android calendar.
     */
    private suspend fun getAccountForAndroidCalendar(androidCalendarId: Long): com.davy.domain.model.Account? {
        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(androidCalendarId.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val accountName = it.getString(0)
                return accountRepository.getByAccountName(accountName)
            }
        }
        
        return null
    }
}
