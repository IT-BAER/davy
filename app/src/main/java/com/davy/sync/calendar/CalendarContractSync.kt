package com.davy.sync.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.domain.model.EventStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of syncing to Calendar Provider.
 */
sealed class CalendarContractResult {
    data class Success(
        val calendarsCreated: Int,
        val eventsInserted: Int,
        val eventsUpdated: Int,
        val eventsDeleted: Int
    ) : CalendarContractResult()
    
    data class Error(val message: String) : CalendarContractResult()
}

/**
 * Syncs calendar data from DAVy's database to Android's CalendarContract.
 * 
 * This service reads calendars and events from the local Room database
 * and writes them to Android's central calendar database, making them
 * visible in the system Calendar app.
 */
@Singleton
class CalendarContractSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: CalendarEventRepository
) {
    
    companion object {
        private const val ACCOUNT_TYPE = "com.davy"
        private const val CALENDAR_COLOR = 0xFF42A5F5.toInt() // Blue
        
        // Mutex to prevent concurrent CalendarContractSync operations
        private val syncMutex = Mutex()
    }

    private enum class UpdateEventResult {
        UPDATED,
        UNCHANGED,
        FAILED
    }

    private data class ProviderEventSnapshot(
        val calendarId: Long,
        val title: String?,
        val description: String?,
        val location: String?,
        val dtStart: Long?,
        val dtEnd: Long?,
        val allDay: Int?,
        val timezone: String?,
        val uid: String?,
        val status: Int?,
        val rrule: String?,
        val organizer: String?
    )
    
    /**
     * Enable all calendars (VISIBLE=1, SYNC_EVENTS=1) for the given account name in the provider.
     * Useful after account rename to ensure the Calendar app shows the calendars as enabled by default.
     *
     * @return number of rows updated
     */
    suspend fun enableAllCalendarsForAccount(accountName: String): Int {
        if (!hasCalendarPermissions()) {
            Timber.e("Calendar permissions not granted")
            return 0
        }

        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                }

                // Use sync-adapter URI
                val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()

                // Scope is already the given account via sync-adapter URI; update all rows under that account.
                val updated = context.contentResolver.update(syncAdapterUri, values, null, null)
                Timber.d("Enabled all calendars for account '%s' (rows=%d)", accountName, updated)
                updated
            } catch (e: Exception) {
                Timber.e(e, "Failed to enable calendars for account '%s'", accountName)
                0
            }
        }
    }

    /**
     * Sync all calendars and events for an account to Android's Calendar Provider.
     */
    suspend fun syncToCalendarProvider(accountId: Long): CalendarContractResult {
        // Check for calendar permissions
        if (!hasCalendarPermissions()) {
            return CalendarContractResult.Error("Calendar permissions not granted")
        }
        
        // Use mutex to prevent concurrent CalendarContractSync operations
        return syncMutex.withLock {
            Timber.d("🔒 CalendarContractSync: Acquired lock, starting sync for account $accountId")
            
            // Set flag to prevent ContentObserver from triggering during our sync
            CalendarContentObserver.isSyncInProgress = true
            
            try {
            var calendarsCreated = 0
            var eventsInserted = 0
            var eventsUpdated = 0
            var eventsDeleted = 0
            
            // Get account name
            val davyAccount = accountRepository.getById(accountId)
            if (davyAccount == null) {
                return CalendarContractResult.Error("Account not found: $accountId")
            }
            val accountName = davyAccount.accountName
            
            // Get only sync-enabled calendars for this account from DAVy's database
            val calendars = calendarRepository.getSyncEnabledByAccountId(accountId)
            Timber.d("Found ${calendars.size} sync-enabled calendars in DAVy database for account $accountId")
            
            for (calendar in calendars) {
                // Create or update calendar in Android's Calendar Provider
                val androidCalendarId = ensureCalendarExists(calendar.id, calendar.accountId)
                
                if (androidCalendarId == null) {
                    Timber.e("Failed to create/find calendar in Calendar Provider: ${calendar.displayName}")
                    continue
                }
                
                // Update DAVy calendar with Android calendar ID if not already set
                if (calendar.androidCalendarId != androidCalendarId) {
                    calendarRepository.updateAndroidCalendarId(calendar.id, androidCalendarId)
                    Timber.d("Updated DAVy calendar ${calendar.id} with Android calendar ID: $androidCalendarId")
                }
                
                if (androidCalendarId > 0) {
                    calendarsCreated++
                }
                
                // Get all events for this calendar from DAVy's database
                val events = eventRepository.getByCalendarId(calendar.id)
                Timber.d("Syncing ${events.size} events for calendar ${calendar.displayName}")
                
                for (event in events) {
                    // Resolve existing provider event id with multiple strategies
                    var existingEventId = event.androidEventId

                    // If we have an ID from our DB, verify it still exists in the provider
                    if (existingEventId != null && !eventExists(existingEventId)) {
                        Timber.d("Provider event $existingEventId for '${event.title}' no longer exists; will re-resolve")
                        existingEventId = null
                    }

                    // If no androidEventId, try to find by UID
                    if (existingEventId == null) {
                        existingEventId = findEventByUid(event.uid, androidCalendarId)
                    }

                    // If still not found, try to find by title + time (prevents duplicates when event created on phone)
                    if (existingEventId == null) {
                        existingEventId = findEventByTitleAndTime(event.title, event.dtStart, event.dtEnd, androidCalendarId)
                        if (existingEventId != null) {
                            Timber.d("✓ Found existing event by title+time: ${event.title} (ID: $existingEventId)")
                        }
                    }

                    if (existingEventId != null) {
                        // Update existing event; on failure, try to insert anew
                        when (updateEvent(existingEventId, event, androidCalendarId, accountName)) {
                            UpdateEventResult.UPDATED -> {
                                eventsUpdated++
                                if (event.androidEventId != existingEventId) {
                                    eventRepository.updateAndroidEventId(event.id, existingEventId)
                                }
                            }
                            UpdateEventResult.UNCHANGED -> Timber.d("Skipping provider update for '${event.title}' – no changes detected")
                            UpdateEventResult.FAILED -> {
                                Timber.w("Update failed for event '${event.title}' (id=$existingEventId). Attempting insert fallback…")
                                val newEventId = insertEvent(event, androidCalendarId, accountName)
                                if (newEventId != null) {
                                    eventsInserted++
                                    eventRepository.updateAndroidEventId(event.id, newEventId)
                                    Timber.d("Fallback insert succeeded for '${event.title}' → newId=$newEventId")
                                }
                            }
                        }
                    } else {
                        // Insert new event
                        val newEventId = insertEvent(event, androidCalendarId, accountName)
                        if (newEventId != null) {
                            eventsInserted++
                            // Store androidEventId
                            eventRepository.updateAndroidEventId(event.id, newEventId)
                        }
                    }
                }
            }
            
                CalendarContractResult.Success(
                    calendarsCreated = calendarsCreated,
                    eventsInserted = eventsInserted,
                    eventsUpdated = eventsUpdated,
                    eventsDeleted = eventsDeleted
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Exception syncing to Calendar Provider")
                CalendarContractResult.Error(e.message ?: "Unknown error")
            } finally {
                // Always clear the flag, even if there was an error
                CalendarContentObserver.isSyncInProgress = false
                Timber.d("🔓 CalendarContractSync: Released lock")
            }
        }
    }

    /**
     * Sync a single calendar (and its events) to Android's Calendar Provider.
     * This avoids mirroring the entire account when only one calendar changed.
     */
    suspend fun syncSingleCalendarToProvider(accountId: Long, davyCalendarId: Long): CalendarContractResult {
        // Check for calendar permissions
        if (!hasCalendarPermissions()) {
            return CalendarContractResult.Error("Calendar permissions not granted")
        }

        return syncMutex.withLock {
            Timber.d("🔒 CalendarContractSync: Acquired lock, starting single-calendar sync for account $accountId, calendar $davyCalendarId")
            CalendarContentObserver.isSyncInProgress = true
            try {
                var calendarsCreated = 0
                var eventsInserted = 0
                var eventsUpdated = 0
                var eventsDeleted = 0

                // Resolve account
                val davyAccount = accountRepository.getById(accountId)
                    ?: return@withLock CalendarContractResult.Error("Account not found: $accountId")
                val accountName = davyAccount.accountName

                // Resolve the target calendar
                val calendar = calendarRepository.getById(davyCalendarId)
                    ?: return@withLock CalendarContractResult.Error("Calendar not found: $davyCalendarId")

                // Respect user sync toggle: if calendar sync is disabled, just ensure it is hidden/disabled in provider
                // but do not push events. We still ensure the calendar row exists with correct flags.
                val androidCalendarId = ensureCalendarExists(calendar.id, calendar.accountId)
                    ?: return@withLock CalendarContractResult.Error("Failed to ensure calendar exists in provider: ${calendar.displayName}")

                if (calendar.androidCalendarId != androidCalendarId) {
                    calendarRepository.updateAndroidCalendarId(calendar.id, androidCalendarId)
                    Timber.d("Updated DAVy calendar ${calendar.id} with Android calendar ID: $androidCalendarId")
                }
                if (androidCalendarId > 0) calendarsCreated++

                // Only upsert events when sync is enabled; otherwise just update visibility flags
                if (calendar.syncEnabled) {
                    val events = eventRepository.getByCalendarId(calendar.id)
                    Timber.d("Single-calendar provider sync: processing ${events.size} events for '${calendar.displayName}'")
                    for (event in events) {
                        var existingEventId = event.androidEventId
                        if (existingEventId != null && !eventExists(existingEventId)) {
                            Timber.d("Provider event $existingEventId for '${event.title}' no longer exists; will re-resolve")
                            existingEventId = null
                        }
                        if (existingEventId == null) {
                            existingEventId = findEventByUid(event.uid, androidCalendarId)
                        }
                        if (existingEventId == null) {
                            existingEventId = findEventByTitleAndTime(event.title, event.dtStart, event.dtEnd, androidCalendarId)
                            if (existingEventId != null) {
                                Timber.d("✓ Found existing event by title+time: ${event.title} (ID: $existingEventId)")
                            }
                        }

                        if (existingEventId != null) {
                            when (updateEvent(existingEventId, event, androidCalendarId, accountName)) {
                                UpdateEventResult.UPDATED -> {
                                    eventsUpdated++
                                    if (event.androidEventId != existingEventId) {
                                        eventRepository.updateAndroidEventId(event.id, existingEventId)
                                    }
                                }
                                UpdateEventResult.UNCHANGED -> Timber.d("Skipping provider update for '${event.title}' – no changes detected")
                                UpdateEventResult.FAILED -> {
                                    Timber.w("Update failed for event '${event.title}' (id=$existingEventId). Attempting insert fallback…")
                                    val newEventId = insertEvent(event, androidCalendarId, accountName)
                                    if (newEventId != null) {
                                        eventsInserted++
                                        eventRepository.updateAndroidEventId(event.id, newEventId)
                                        Timber.d("Fallback insert succeeded for '${event.title}' → newId=$newEventId")
                                    }
                                }
                            }
                        } else {
                            val newEventId = insertEvent(event, androidCalendarId, accountName)
                            if (newEventId != null) {
                                eventsInserted++
                                eventRepository.updateAndroidEventId(event.id, newEventId)
                            }
                        }
                    }
                } else {
                    // sync disabled: update provider visibility/sync flags only
                    updateCalendarVisibility(androidCalendarId, accountName, calendar.visible, calendar.syncEnabled)
                }

                CalendarContractResult.Success(
                    calendarsCreated = calendarsCreated,
                    eventsInserted = eventsInserted,
                    eventsUpdated = eventsUpdated,
                    eventsDeleted = eventsDeleted
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception syncing single calendar to Calendar Provider")
                CalendarContractResult.Error(e.message ?: "Unknown error")
            } finally {
                CalendarContentObserver.isSyncInProgress = false
                Timber.d("🔓 CalendarContractSync: Released lock (single-calendar)")
            }
        }
    }
    
    /**
     * Ensure a calendar exists in the Calendar Provider.
     * Returns the Android calendar ID if successful, null otherwise.
     */
    private suspend fun ensureCalendarExists(davyCalendarId: Long, accountId: Long): Long? {
        try {
            // Get account from repository to get account name
            val davyAccount = accountRepository.getById(accountId)
            if (davyAccount == null) {
                Timber.e("Account not found: $accountId")
                return null
            }
            
            val accountName = davyAccount.accountName
            
            // Get calendar from DAVy database
            val calendar = calendarRepository.getById(davyCalendarId) ?: return null
            
            // Query for existing calendar by URL (NAME field)
            // This is the unique identifier for a calendar in Calendar Provider
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
            val selectionArgs = arrayOf(accountName, ACCOUNT_TYPE, calendar.calendarUrl)
            
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            // Check if calendar already exists in Calendar Provider
            var existingCalendarId: Long? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    existingCalendarId = it.getLong(0)
                    Timber.d("Calendar already exists with ID: $existingCalendarId")
                }
            }
            
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                put(CalendarContract.Calendars.NAME, calendar.calendarUrl)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendar.displayName)
                put(CalendarContract.Calendars.CALENDAR_COLOR, calendar.color)
                
                // Set access level based on calendar read-only status
                // CAL_ACCESS_READ = read-only (user cannot modify events in Calendar app)
                // CAL_ACCESS_OWNER = read-write (user can modify events)
                val accessLevel = if (calendar.isReadOnly()) {
                    CalendarContract.Calendars.CAL_ACCESS_READ
                } else {
                    CalendarContract.Calendars.CAL_ACCESS_OWNER
                }
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, accessLevel)
                
                put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
                put(CalendarContract.Calendars.VISIBLE, if (calendar.visible) 1 else 0)
                put(CalendarContract.Calendars.SYNC_EVENTS, if (calendar.syncEnabled) 1 else 0)
                
                // Set calendar display property for newly created calendars
                // CALENDAR_DISPLAY_NAME already set, VISIBLE controls display in app
                
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, calendar.timezone ?: "UTC")
            }
            
            // Use sync adapter URI to bypass restrictions
            val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build()
            
            val calendarId: Long
            val existingId = existingCalendarId
            if (existingId != null) {
                // Update existing calendar
                val updateUri = ContentUris.withAppendedId(syncAdapterUri, existingId)
                context.contentResolver.update(updateUri, values, null, null)
                Timber.d("Updated calendar with ID: $existingId")
                calendarId = existingId
            } else {
                // Insert new calendar
                val uri = context.contentResolver.insert(syncAdapterUri, values)
                val newCalendarId = uri?.lastPathSegment?.toLongOrNull()
                
                if (newCalendarId != null) {
                    Timber.d("Created calendar with ID: $newCalendarId")
                    calendarId = newCalendarId
                } else {
                    Timber.e("Failed to create calendar in Calendar Provider")
                    return null
                }
            }
            
            return calendarId
            
        } catch (e: Exception) {
            Timber.e(e, "Exception ensuring calendar exists")
            return null
        } finally {
            // Notify Calendar Provider that calendars have changed
            context.contentResolver.notifyChange(CalendarContract.Calendars.CONTENT_URI, null, false)
        }
    }
    
    /**
     * Find an event in Calendar Provider by UID.
     */
    private fun findEventByUid(uid: String, calendarId: Long): Long? {
        try {
            val projection = arrayOf(CalendarContract.Events._ID)
            val selection = "${CalendarContract.Events.UID_2445} = ? AND ${CalendarContract.Events.CALENDAR_ID} = ?"
            val selectionArgs = arrayOf(uid, calendarId.toString())
            
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(0)
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception finding event by UID")
        }
        
        return null
    }
    
    /**
     * Find event in Calendar Provider by title, start time, and end time.
     * Used to prevent duplicates when event was created on phone and then synced from server.
     */
    private fun findEventByTitleAndTime(title: String, dtStart: Long, dtEnd: Long?, calendarId: Long): Long? {
        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
            )
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
            val selectionArgs = arrayOf(calendarId.toString())
            
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val eventId = it.getLong(0)
                    val eventTitle = it.getString(1)
                    val eventDtStart = it.getLong(2)
                    val eventDtEnd = it.getLong(3)
                    
                    // Match by title (case-insensitive) and start/end times
                    if (eventTitle?.equals(title, ignoreCase = true) == true &&
                        eventDtStart == dtStart &&
                        (dtEnd == null || eventDtEnd == dtEnd)) {
                        Timber.d("findEventByTitleAndTime: Match found - ID: $eventId, Title: '$title', Start: $dtStart")
                        return eventId
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception finding event by title and time")
        }
        
        return null
    }

    /**
     * Check if a provider event with the given ID currently exists.
     */
    private fun eventExists(eventId: Long): Boolean {
        return try {
            val projection = arrayOf(CalendarContract.Events._ID)
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.query(uri, projection, null, null, null).use { cursor ->
                cursor != null && cursor.moveToFirst()
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception checking event existence for id=$eventId")
            false
        }
    }
    
    /**
     * Insert a new event into Calendar Provider.
     * Returns the Android event ID if successful, null otherwise.
     */
    private fun insertEvent(
        event: com.davy.domain.model.CalendarEvent,
        androidCalendarId: Long,
        accountName: String
    ): Long? {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, androidCalendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.DTSTART, event.dtStart)
                put(CalendarContract.Events.DTEND, event.dtEnd)
                put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone ?: "UTC")
                put(CalendarContract.Events.UID_2445, event.uid)
                
                // Add status if present
                event.status?.let {
                    put(CalendarContract.Events.STATUS, mapEventStatus(it))
                }
                
                // Add recurrence rule if present
                event.rrule?.let {
                    put(CalendarContract.Events.RRULE, it)
                }
                
                // Add organizer
                event.organizer?.let {
                    put(CalendarContract.Events.ORGANIZER, it)
                }
            }
            
            // Use sync adapter URI to prevent triggering ContentObserver
            val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, accountName)
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build()
            
            val uri = context.contentResolver.insert(syncAdapterUri, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()
            
            if (eventId != null) {
                Timber.d("Inserted event with ID: $eventId (${event.title})")
                return eventId
            } else {
                Timber.e("Failed to insert event: ${event.title}")
                return null
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception inserting event: ${event.title}")
            return null
        }
    }
    
    /**
     * Update an existing event in Calendar Provider.
     */
    private fun updateEvent(
        eventId: Long,
        event: com.davy.domain.model.CalendarEvent,
        androidCalendarId: Long,
        accountName: String
    ): UpdateEventResult {
        try {
            val existingSnapshot = loadProviderEventSnapshot(accountName, eventId)
            if (existingSnapshot == null) {
                Timber.d("Provider event $eventId not found before update; will signal failure")
                return UpdateEventResult.FAILED
            }

            val desiredSnapshot = ProviderEventSnapshot(
                calendarId = androidCalendarId,
                title = event.title,
                description = event.description,
                location = event.location,
                dtStart = event.dtStart,
                dtEnd = event.dtEnd,
                allDay = if (event.allDay) 1 else 0,
                timezone = event.timezone ?: "UTC",
                uid = event.uid,
                status = event.status?.let { mapEventStatus(it) },
                rrule = event.rrule,
                organizer = event.organizer
            )

            if (existingSnapshot == desiredSnapshot) {
                return UpdateEventResult.UNCHANGED
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, androidCalendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.DTSTART, event.dtStart)
                put(CalendarContract.Events.DTEND, event.dtEnd)
                put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone ?: "UTC")
                put(CalendarContract.Events.UID_2445, event.uid)
                
                // Add status if present
                event.status?.let {
                    put(CalendarContract.Events.STATUS, mapEventStatus(it))
                }
                
                // Add recurrence rule if present
                event.rrule?.let {
                    put(CalendarContract.Events.RRULE, it)
                }
                
                // Add organizer
                event.organizer?.let {
                    put(CalendarContract.Events.ORGANIZER, it)
                }
            }
            
            // Use sync adapter URI to prevent triggering ContentObserver
            val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, accountName)
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build()
            val uri = ContentUris.withAppendedId(syncAdapterUri, eventId)
            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            
            if (rowsUpdated > 0) {
                Timber.d("Updated event with ID: $eventId (${event.title})")
                return UpdateEventResult.UPDATED
            }

            Timber.e("Failed to update event id=$eventId title='${event.title}' (0 rows)")
            return UpdateEventResult.FAILED
            
        } catch (e: Exception) {
            Timber.e(e, "Exception updating event: ${event.title}")
            return UpdateEventResult.FAILED
        }
    }

    private fun loadProviderEventSnapshot(accountName: String, eventId: Long): ProviderEventSnapshot? {
        val projection = arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.ORGANIZER
        )

        return try {
            val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, accountName)
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build()
            val uri = ContentUris.withAppendedId(syncAdapterUri, eventId)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    Timber.d("Provider event $eventId query returned no rows")
                    return null
                }
                ProviderEventSnapshot(
                    calendarId = cursor.getLongOrNull(0) ?: return null,
                    title = cursor.getStringOrNull(1),
                    description = cursor.getStringOrNull(2),
                    location = cursor.getStringOrNull(3),
                    dtStart = cursor.getLongOrNull(4),
                    dtEnd = cursor.getLongOrNull(5),
                    allDay = cursor.getIntOrNull(6),
                    timezone = cursor.getStringOrNull(7),
                    uid = cursor.getStringOrNull(8),
                    status = cursor.getIntOrNull(9),
                    rrule = cursor.getStringOrNull(10),
                    organizer = cursor.getStringOrNull(11)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load provider snapshot for eventId=$eventId")
            null
        }
    }

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private fun Cursor.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
    
    /**
     * Map EventStatus enum to CalendarContract status integer.
     */
    private fun mapEventStatus(status: EventStatus): Int {
        return when (status) {
            EventStatus.TENTATIVE -> CalendarContract.Events.STATUS_TENTATIVE
            EventStatus.CONFIRMED -> CalendarContract.Events.STATUS_CONFIRMED
            EventStatus.CANCELLED -> CalendarContract.Events.STATUS_CANCELED
        }
    }
    
    /**
     * Check if the app has calendar permissions.
     */
    private fun hasCalendarPermissions(): Boolean {
        val readPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        )
        val writePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        )
        
        return readPermission == PackageManager.PERMISSION_GRANTED &&
                writePermission == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Update calendar visibility and sync status in Calendar Provider.
     * This hides/shows the calendar without deleting it.
     */
    suspend fun updateCalendarVisibility(androidCalendarId: Long?, accountName: String, visible: Boolean, syncEnabled: Boolean): Boolean {
        if (androidCalendarId == null) {
            Timber.d("No Android calendar ID, skipping Calendar Provider update")
            return true
        }
        
        if (!hasCalendarPermissions()) {
            Timber.e("Calendar permissions not granted")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Calendars.VISIBLE, if (visible) 1 else 0)
                    put(CalendarContract.Calendars.SYNC_EVENTS, if (syncEnabled) 1 else 0)
                }
                
                val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()
                
                val updateUri = ContentUris.withAppendedId(syncAdapterUri, androidCalendarId)
                val updatedRows = context.contentResolver.update(updateUri, values, null, null)
                
                Timber.d("Updated calendar $androidCalendarId visibility=$visible, syncEnabled=$syncEnabled ($updatedRows rows)")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to update calendar visibility")
                false
            }
        }
    }
    
    /**
     * Delete a calendar from Android's Calendar Provider.
     * This should be called when deleting a calendar from DAVy to ensure
     * it's also removed from the system Calendar app.
     * 
     * @param androidCalendarId The Android Calendar Provider ID
     * @param accountName The account name for sync adapter URI
     * @return True if deletion was successful, false otherwise
     */
    suspend fun deleteCalendarFromProvider(androidCalendarId: Long?, accountName: String): Boolean {
        if (androidCalendarId == null) {
            Timber.d("No Android calendar ID, skipping Calendar Provider deletion")
            return true // Not an error - calendar was never synced to provider
        }
        
        if (!hasCalendarPermissions()) {
            Timber.e("Calendar permissions not granted")
            return false
        }
        
        return syncMutex.withLock {
            Timber.d("🔒 CalendarContractSync: Deleting calendar $androidCalendarId from Calendar Provider")
            Timber.d("Account: $accountName")
            
            // Set flag to prevent ContentObserver from triggering
            CalendarContentObserver.isSyncInProgress = true
            
            var result: Boolean
            try {
                // FAST PATH: Try deleting the calendar row first and rely on provider cascade to remove events.
                // This avoids O(n) per-event deletions on providers that support cascading deletes (most do).
                Timber.d("→ Fast path: Attempting direct calendar delete (sync-adapter)")
                val syncAdapterCalendarsUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()

                val fastDeleteUri = ContentUris.withAppendedId(syncAdapterCalendarsUri, androidCalendarId)
                var deletedRows = context.contentResolver.delete(fastDeleteUri, null, null)
                if (deletedRows > 0) {
                    Timber.d("✓ Fast delete succeeded for calendar $androidCalendarId (sync-adapter, $deletedRows rows)")
                    // Optional: final cleanup in case cascade didn't remove orphans
                    try {
                        val finalDeleted = context.contentResolver.delete(
                            CalendarContract.Events.CONTENT_URI,
                            "${CalendarContract.Events.CALENDAR_ID} = ?",
                            arrayOf(androidCalendarId.toString())
                        )
                        if (finalDeleted > 0) {
                            Timber.d("✓ Final cleanup after fast delete removed $finalDeleted orphaned events for calendar $androidCalendarId")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Final event cleanup after fast delete failed")
                    }
                    result = true
                    return@withLock result
                }

                // Fallback: try plain calendar delete directly before per-event work
                Timber.d("→ Fast path fallback: Attempting direct calendar delete (plain)")
                val plainFastDeleteUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, androidCalendarId)
                deletedRows = context.contentResolver.delete(plainFastDeleteUri, null, null)
                if (deletedRows > 0) {
                    Timber.d("✓ Fast delete (plain) succeeded for calendar $androidCalendarId ($deletedRows rows)")
                    // Optional: final cleanup in case cascade didn't remove orphans
                    try {
                        val finalDeleted = context.contentResolver.delete(
                            CalendarContract.Events.CONTENT_URI,
                            "${CalendarContract.Events.CALENDAR_ID} = ?",
                            arrayOf(androidCalendarId.toString())
                        )
                        if (finalDeleted > 0) {
                            Timber.d("✓ Final cleanup after fast delete (plain) removed $finalDeleted orphaned events for calendar $androidCalendarId")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Final event cleanup after fast plain delete failed")
                    }
                    result = true
                    return@withLock result
                }

                // STEP 1: Query and log events before deletion
                Timber.d("→ Step 1: Querying events for calendar $androidCalendarId")
                val eventsQuery = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE),
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(androidCalendarId.toString()),
                    null
                )
                
                val eventIds = mutableListOf<Long>()
                eventsQuery?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val eventId = cursor.getLong(0)
                        val title = cursor.getString(1) ?: "No title"
                        eventIds.add(eventId)
                        Timber.d("  Found event: ID=$eventId, Title=$title")
                    }
                }
                Timber.d("✓ Found ${eventIds.size} events to delete")
                
                // STEP 2: Delete events individually with sync adapter URI
                Timber.d("→ Step 2: Deleting ${eventIds.size} events individually")
                var deletedEventCount = 0
                for (eventId in eventIds) {
                    try {
                        // First try with sync-adapter URI
                        val syncAdapterEventUri = ContentUris.withAppendedId(
                            CalendarContract.Events.CONTENT_URI,
                            eventId
                        ).buildUpon()
                            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                            .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, accountName)
                            .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, ACCOUNT_TYPE)
                            .build()

                        var deleted = context.contentResolver.delete(syncAdapterEventUri, null, null)
                        if (deleted > 0) {
                            deletedEventCount++
                            Timber.d("  ✓ Deleted event $eventId (sync-adapter)")
                        } else {
                            // Fallback: try plain URI (some providers ignore account params for Events)
                            val plainUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                            deleted = context.contentResolver.delete(plainUri, null, null)
                            if (deleted > 0) {
                                deletedEventCount++
                                Timber.d("  ✓ Deleted event $eventId (plain)")
                            } else {
                                Timber.w("  ✗ Failed to delete event $eventId (both sync-adapter and plain)")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "  ✗ Exception deleting event $eventId")
                    }
                }
                Timber.d("✓ Deleted $deletedEventCount/${eventIds.size} events")
                
                // STEP 3: Bulk delete any remaining events
                Timber.d("→ Step 3: Bulk deleting any remaining events")
                val eventsUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()
                
                var bulkDeleted = context.contentResolver.delete(
                    eventsUri,
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(androidCalendarId.toString())
                )
                Timber.d("✓ Bulk delete removed $bulkDeleted additional events (sync-adapter)")

                if (bulkDeleted <= 0) {
                    // Fallback: try plain URI bulk delete
                    bulkDeleted = context.contentResolver.delete(
                        CalendarContract.Events.CONTENT_URI,
                        "${CalendarContract.Events.CALENDAR_ID} = ?",
                        arrayOf(androidCalendarId.toString())
                    )
                    Timber.d("✓ Bulk delete removed $bulkDeleted additional events (plain)")
                }
                
                // STEP 4: Delete the calendar itself (after events cleared)
                Timber.d("→ Step 4: Deleting calendar $androidCalendarId")
                val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()
                
                val deleteUri = ContentUris.withAppendedId(syncAdapterUri, androidCalendarId)
                deletedRows = context.contentResolver.delete(deleteUri, null, null)
                
                if (deletedRows > 0) {
                    Timber.d("✓ Deleted calendar $androidCalendarId from Calendar Provider ($deletedRows rows, sync-adapter)")
                    result = true
                } else {
                    // Fallback: try plain calendar delete
                    val plainDeleteUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, androidCalendarId)
                    deletedRows = context.contentResolver.delete(plainDeleteUri, null, null)
                    if (deletedRows > 0) {
                        Timber.d("✓ Deleted calendar $androidCalendarId from Calendar Provider ($deletedRows rows, plain)")
                        result = true
                    } else {
                        Timber.w("Calendar $androidCalendarId not found in Calendar Provider (may have been already deleted)")
                        result = true // Not an error - calendar is gone
                    }
                }

                // STEP 5: Final cleanup in case cascade didn't remove orphans
                try {
                    val finalDeleted = context.contentResolver.delete(
                        CalendarContract.Events.CONTENT_URI,
                        "${CalendarContract.Events.CALENDAR_ID} = ?",
                        arrayOf(androidCalendarId.toString())
                    )
                    if (finalDeleted > 0) {
                        Timber.d("✓ Final cleanup removed $finalDeleted orphaned events for calendar $androidCalendarId")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Final event cleanup after calendar deletion failed")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Exception deleting calendar from Calendar Provider")
                result = false
            } finally {
                CalendarContentObserver.isSyncInProgress = false
                Timber.d("🔓 CalendarContractSync: Released lock")
            }
            result
        }
    }
    
    /**
     * Delete events older than the specified number of days.
     * Removes events from both Android Calendar Provider and DAVy database.
     * 
     * @param accountId The account ID to filter calendars
     * @param daysThreshold Events older than this many days will be deleted (-1 means keep all)
     * @return Number of events deleted
     */
    suspend fun deleteOldEvents(accountId: Long, daysThreshold: Int): Int {
        if (daysThreshold == -1) {
            Timber.d("Skip events days set to -1 (all events), no deletion needed")
            return 0
        }
        
        if (!hasCalendarPermissions()) {
            Timber.e("Calendar permissions not granted")
            return 0
        }
        
        return syncMutex.withLock {
            Timber.d("🔒 CalendarContractSync: Deleting events older than $daysThreshold days")
            
            // Set flag to prevent ContentObserver from triggering
            CalendarContentObserver.isSyncInProgress = true
            
            var deletedCount = 0
            try {
                // Calculate cutoff timestamp (current time - daysThreshold days)
                val cutoffTimestamp = System.currentTimeMillis() - (daysThreshold * 24 * 60 * 60 * 1000L)
                Timber.d("Cutoff timestamp: $cutoffTimestamp (${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(cutoffTimestamp))})")
                
                // Get account name
                val davyAccount = accountRepository.getById(accountId)
                if (davyAccount == null) {
                    Timber.e("Account not found: $accountId")
                    deletedCount = 0
                } else {
                val accountName = davyAccount.accountName
                
                // Get all calendars for this account
                val calendars = calendarRepository.getByAccountId(accountId)
                Timber.d("Found ${calendars.size} calendars for account $accountId")
                
                for (calendar in calendars) {
                    @Suppress("UNUSED_VARIABLE")  // Used for logging context
                    val androidCalendarId = calendar.androidCalendarId ?: continue
                    
                    // Get old events from DAVy database
                    val oldEvents = eventRepository.getByCalendarId(calendar.id)
                        .filter { it.dtStart < cutoffTimestamp }
                    
                    Timber.d("Found ${oldEvents.size} old events in calendar ${calendar.displayName}")
                    
                    for (event in oldEvents) {
                        // Delete from Android Calendar Provider
                        event.androidEventId?.let { androidEventId ->
                            try {
                                val syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                                    .build()
                                
                                val deleteUri = ContentUris.withAppendedId(syncAdapterUri, androidEventId)
                                val deletedRows = context.contentResolver.delete(deleteUri, null, null)
                                
                                if (deletedRows > 0) {
                                    Timber.d("✓ Deleted old event from Calendar Provider: ${event.title} (Android ID: $androidEventId)")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to delete event from Calendar Provider: ${event.title}")
                            }
                        }
                        
                        // Delete from DAVy database
                        try {
                            eventRepository.delete(event)
                            deletedCount++
                            Timber.d("✓ Deleted old event from DAVy database: ${event.title}")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to delete event from DAVy database: ${event.title}")
                        }
                    }
                }
                
                Timber.d("✓ Deleted $deletedCount old events (older than $daysThreshold days)")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Exception deleting old events")
                deletedCount = 0
            } finally {
                CalendarContentObserver.isSyncInProgress = false
                Timber.d("🔓 CalendarContractSync: Released lock")
            }
            deletedCount
        }
    }
}
