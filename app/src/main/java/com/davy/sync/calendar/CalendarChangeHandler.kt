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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles calendar changes detected by CalendarContentObserver.
 * 
 * Processes changes to determine which events need to be pushed to the server
 * and triggers appropriate sync operations.
 * 
 * Changed from @ServiceScoped to @Singleton to match CalendarContentObserver scope,
 * since calendar monitoring now runs at application level without a foreground service.
 */
@Singleton
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
     * 
     * Strategy: When we can't identify a specific event ID from the URI,
     * we still avoid full sync of all calendars. Instead, we scan all calendars
     * for dirty events and only sync calendars that have actual changes.
     */
    private suspend fun handleEventChange(uri: Uri?) {
        Timber.d("Handling event change: $uri")
        
        // If we have a specific event URI, extract the event ID
        val androidEventId = uri?.lastPathSegment?.toLongOrNull()
        
        if (androidEventId != null) {
            // Specific event changed - handle it directly
            handleSpecificEventChange(androidEventId)
        } else {
            // Generic event URI without specific ID - scan for dirty events by calendar
            handleGenericEventChange()
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
        
        // Check if event was found in Calendar Provider
        val eventFound = cursor?.use {
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
                        
                        // Get the DAVy calendar that this Android calendar maps to
                        val davyCalendar = calendarRepository.getByAndroidCalendarId(calendarId)
                        
                        if (davyCalendar != null) {
                            // Trigger PUSH-ONLY sync for this specific calendar to avoid full download
                            Timber.d("→ Triggering PUSH-ONLY sync to server for calendar: ${davyCalendar.displayName}")
                            syncManager.syncCalendarPushOnly(account.id, davyCalendar.id)
                            Timber.d("✓ Push-only sync triggered")
                        } else {
                            Timber.w("✗ Could not find DAVy calendar for Android calendar: $calendarId")
                            // Fallback to full sync if calendar mapping not found
                            syncManager.syncNow(account.id, SyncManager.SYNC_TYPE_CALENDAR)
                        }
                    } else {
                        Timber.w("✗ Could not find DAVy account for calendar: $calendarId")
                    }
                    
                    true // Event found and processed
                } else {
                    Timber.d("✗ Event does not belong to DAVy calendar (skipping)")
                    true // Event found but not ours
                }
            } else {
                false // Event not found
            }
        } ?: false
        
        // If event wasn't found in Calendar Provider, it might have been deleted
        if (!eventFound) {
            Timber.d("✗ Event not found in Calendar Provider - may have been deleted")
            handleEventDeletion(androidEventId)
        }
        
        Timber.d("========================================")
        Timber.d("END handleSpecificEventChange")
        Timber.d("========================================")
    }
    
    /**
     * Handle event deletion by finding the event in DAVy DB and marking it for server deletion.
     */
    private suspend fun handleEventDeletion(androidEventId: Long) {
        Timber.d("========================================")
        Timber.d("HANDLING EVENT DELETION: Android Event ID = $androidEventId")
        Timber.d("========================================")
        
        // Find the event in DAVy database by androidEventId
        val davyEvent = eventRepository.getByAndroidEventId(androidEventId)
        
        if (davyEvent != null) {
            Timber.d("✓ Found deleted event in DAVy DB: ${davyEvent.title} (DAVy ID: ${davyEvent.id})")
            
            // Get the calendar to find the account
            val calendar = calendarRepository.getById(davyEvent.calendarId)
            
            if (calendar != null) {
                Timber.d("✓ Calendar: ${calendar.displayName} (ID: ${calendar.id})")
                
                val account = accountRepository.getById(calendar.accountId)
                
                if (account != null) {
                    Timber.d("✓ Account: ${account.accountName} (ID: ${account.id})")
                    
                    // Mark event as deleted and dirty in DAVy database
                    Timber.d("→ Marking event as deleted and dirty for server deletion...")
                    val deletedEvent = davyEvent.copy(
                        deletedAt = System.currentTimeMillis(),
                        dirty = true
                    )
                    eventRepository.update(deletedEvent)
                    Timber.d("✓ Event marked for deletion")
                    
                    // Trigger PUSH-ONLY sync to upload the deletion to server
                    Timber.d("→ Triggering PUSH-ONLY sync to delete from server...")
                    syncManager.syncCalendarPushOnly(account.id, calendar.id)
                    Timber.d("✓ Push-only sync triggered")
                } else {
                    Timber.w("✗ Could not find account for calendar")
                }
            } else {
                Timber.w("✗ Could not find calendar for deleted event")
            }
        } else {
            Timber.w("✗ Event not found in DAVy database - may be a non-DAVy event")
        }
        
        Timber.d("========================================")
        Timber.d("END handleEventDeletion")
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
     * Handle generic event change without specific event ID.
     * 
     * This happens when:
     * - New events are created (URI: content://com.android.calendar/events)
     * - Bulk operations are performed
     * - Some Android versions send generic URI for deletions
     * 
     * Strategy: Instead of syncing ALL calendars, we:
     * 1. Query Calendar Provider for dirty events in DAVy calendars
     * 2. Check for deleted events (events in DAVy DB but not in Calendar Provider)
     * 3. Group affected events by calendar
     * 4. Only sync calendars that have changes
     * 
     * This avoids triggering full sync for all calendars when only one calendar has changes.
     */
    private suspend fun handleGenericEventChange() {
        Timber.d("========================================")
        Timber.d("HANDLING GENERIC EVENT CHANGE")
        Timber.d("Strategy: Query DIRTY and DELETED events")
        Timber.d("========================================")
        
        try {
            Timber.d("→ Starting to query Calendar Provider for changes...")
            // Map to track which DAVy calendars have changes
            val calendarsWithChanges = mutableSetOf<Long>()
            
            // STEP 1: Query DIRTY events (modified/created) - exclude DELETED ones
            Timber.d("→ Step 1: Scanning for DIRTY (modified/created) events...")
            val dirtyProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE
            )

            // IMPORTANT: On some providers, seeing special rows (like DELETED) requires
            // querying as a SyncAdapter with ACCOUNT_NAME and ACCOUNT_TYPE.
            // We'll iterate DAVy accounts and their Android calendars and run scoped queries.
            var dirtyCount = 0
            val accounts = accountRepository.getAll().filter { it.calendarEnabled }
            for (account in accounts) {
                val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()

                val davyCalendars = calendarRepository.getSyncEnabledByAccountId(account.id)
                for (cal in davyCalendars) {
                    val androidCalId = cal.androidCalendarId ?: continue

                    val dirtyCursor = context.contentResolver.query(
                        syncAdapterUri,
                        dirtyProjection,
                        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DIRTY} = 1 AND ${CalendarContract.Events.DELETED} = 0",
                        arrayOf(androidCalId.toString()),
                        null
                    )

                    dirtyCursor?.use {
                        val idIndex = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                        val calendarIdIndex = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                        val titleIndex = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)

                        while (it.moveToNext()) {
                            val eventId = it.getLong(idIndex)
                            val calendarId = it.getLong(calendarIdIndex)
                            val title = it.getString(titleIndex) ?: "Untitled"

                            // This is our calendar by construction (filtered by CALENDAR_ID),
                            // but keep the guard for safety on OEM variants
                            if (isDAVyCalendar(calendarId)) {
                                Timber.d("   Found dirty event: eventId=$eventId, calendarId=$calendarId, title='$title'")
                                calendarsWithChanges.add(calendarId)
                                dirtyCount++

                                // Sync this specific event to DAVy database
                                reverseSync.syncEventFromProvider(eventId)
                            }
                        }
                    }
                }
            }
            Timber.d("   ✓ Found $dirtyCount dirty events across DAVy accounts")
            
            // STEP 2: Query DELETED events
            // Android Calendar Provider automatically sets DELETED=1 when event is deleted
            // This is MUCH more reliable than scanning for missing events!
            Timber.d("→ Step 2: Scanning for DELETED events (using Android's DELETED flag)...")
            
            val deletedProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE
            )

            var deletedCount = 0
            // Iterate DAVy accounts and their calendars and run scoped deleted queries
            for (account in accounts) {
                val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()

                val davyCalendars = calendarRepository.getSyncEnabledByAccountId(account.id)
                for (cal in davyCalendars) {
                    val androidCalId = cal.androidCalendarId ?: continue

                    val deletedCursor = context.contentResolver.query(
                        syncAdapterUri,
                        deletedProjection,
                        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 1",
                        arrayOf(androidCalId.toString()),
                        null
                    )

                    deletedCursor?.use {
                        val idIndex = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                        val calendarIdIndex = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                        val titleIndex = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)

                        while (it.moveToNext()) {
                            val eventId = it.getLong(idIndex)
                            val calendarId = it.getLong(calendarIdIndex)
                            val title = it.getString(titleIndex) ?: "Untitled"

                            // This is our calendar by construction
                            Timber.d("   🗑️ Found DELETED event: eventId=$eventId, calendarId=$calendarId, title='$title'")
                            calendarsWithChanges.add(calendarId)
                            deletedCount++

                            // Find the event in DAVy DB and mark it for deletion
                            val davyEvent = eventRepository.getByAndroidEventId(eventId)
                            if (davyEvent != null) {
                                Timber.d("   → Marking DAVy event ${davyEvent.id} as deleted (dirty=true, deletedAt=${System.currentTimeMillis()})")
                                val deletedEvent = davyEvent.copy(
                                    deletedAt = System.currentTimeMillis(),
                                    dirty = true
                                )
                                eventRepository.update(deletedEvent)
                            } else {
                                Timber.w("   ⚠️ Event $eventId was deleted but not found in DAVy DB (might have been synced already)")
                            }
                        }
                    }
                }
            }
            Timber.d("   ✓ Found $deletedCount deleted events across DAVy accounts")
            
            // Fallback always for calendars not already flagged: some providers hide DELETED rows
            Timber.d("Running fallback existence check for calendars not already changed…")

            var inferredDeletedCount = 0
            for (account in accounts) {
                val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()

                val davyCalendars = calendarRepository.getSyncEnabledByAccountId(account.id)
                for (cal in davyCalendars) {
                    val androidCalId = cal.androidCalendarId ?: continue
                    if (calendarsWithChanges.contains(androidCalId)) continue // already handled via DIRTY/DELETED

                    // Build provider set of existing event IDs for this calendar
                    val idProjection = arrayOf(CalendarContract.Events._ID)
                    val existingIds = mutableSetOf<Long>()
                    val existingCursor = context.contentResolver.query(
                        syncAdapterUri,
                        idProjection,
                        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
                        arrayOf(androidCalId.toString()),
                        null
                    )
                    existingCursor?.use {
                        val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                        while (it.moveToNext()) {
                            existingIds.add(it.getLong(idIdx))
                        }
                    }

                    if (existingIds.isEmpty()) {
                        // Avoid accidental mass deletes inference if provider filtered everything out
                        Timber.d("   Fallback: Provider returned 0 existing IDs for calendar ${cal.displayName} — skipping inference for this calendar")
                        continue
                    }

                    // Cross-check DAVy DB events for this calendar
                    val davyEvents = eventRepository.getByCalendarId(cal.id)
                    var missingInProviderForCalendar = 0
                    for (evt in davyEvents) {
                        val aId = evt.androidEventId
                        if (aId != null && evt.deletedAt == null && !existingIds.contains(aId)) {
                            // Event no longer present in provider — infer deletion
                            val deletedEvent = evt.copy(
                                deletedAt = System.currentTimeMillis(),
                                dirty = true
                            )
                            eventRepository.update(deletedEvent)
                            missingInProviderForCalendar++
                            inferredDeletedCount++
                        }
                    }

                    if (missingInProviderForCalendar > 0) {
                        Timber.d("   Fallback: Inferred $missingInProviderForCalendar deleted events for calendar '${cal.displayName}'")
                        calendarsWithChanges.add(androidCalId)
                    }
                }
            }

            Timber.d("   Fallback inference completed — inferred $inferredDeletedCount deletions (this pass)")

            if (calendarsWithChanges.isEmpty()) {
                Timber.d("No changes found in DAVy calendars - nothing to sync")
                Timber.d("========================================")
                return
            }
            
            Timber.d("Found changes in ${calendarsWithChanges.size} calendar(s)")
            
            // For each Android calendar with changes, trigger push-only sync
            for (androidCalendarId in calendarsWithChanges) {
                val davyCalendar = calendarRepository.getByAndroidCalendarId(androidCalendarId)
                
                if (davyCalendar != null && davyCalendar.syncEnabled) {
                    val account = accountRepository.getById(davyCalendar.accountId)
                    
                    if (account != null) {
                        Timber.d("→ Triggering PUSH-ONLY sync for calendar: ${davyCalendar.displayName} (Android ID: $androidCalendarId, DAVy ID: ${davyCalendar.id})")
                        syncManager.syncCalendarPushOnly(account.id, davyCalendar.id)
                    } else {
                        Timber.w("Could not find account for DAVy calendar: ${davyCalendar.id}")
                    }
                } else {
                    if (davyCalendar == null) {
                        Timber.w("Could not find DAVy calendar mapping for Android calendar: $androidCalendarId")
                    } else {
                        Timber.d("Skipping calendar '${davyCalendar.displayName}' (sync disabled)")
                    }
                }
            }
            
            Timber.d("✓ Generic event change handling completed")
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling generic event change")
        }
        
        Timber.d("========================================")
        Timber.d("END handleGenericEventChange")
        Timber.d("========================================")
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
     * This typically happens when:
     * 1. Generic URIs without specific event/calendar IDs
     * 2. Bulk operations affecting multiple items
     * 3. Calendar Provider internal operations
     * 
     * Strategy: Use the same targeted approach as generic event changes.
     * Only sync calendars that actually have dirty events.
     */
    private suspend fun handleUnknownChange() {
        Timber.d("========================================")
        Timber.d("HANDLING UNKNOWN CHANGE")
        Timber.d("Strategy: Query dirty events and sync only affected calendars")
        Timber.d("========================================")
        
        try {
            Timber.d("→ Calling handleGenericEventChange()...")
            // Delegate to the generic event change handler which implements the optimized approach
            handleGenericEventChange()
            Timber.d("→ handleGenericEventChange() completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "✗ ERROR in handleGenericEventChange()")
        }
        
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
