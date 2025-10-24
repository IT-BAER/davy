package com.davy.sync.calendar

import android.content.Context
import android.provider.CalendarContract
import com.davy.data.repository.CalendarEventRepository
import com.davy.data.repository.CalendarRepository
import com.davy.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverse sync: Android Calendar Provider -> DAVy Database
 * 
 * When events are created/modified in the phone's Calendar app,
 * this service updates DAVy's internal database and marks events as dirty
 * so they will be pushed to the CalDAV server on the next sync.
 */
@Singleton
class CalendarProviderToDAVySync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: CalendarEventRepository,
    private val accountRepository: com.davy.data.repository.AccountRepository
) {
    
    /**
     * Scan all DAVy calendars for changes in Calendar Provider.
     * This is used when we can't determine which specific event changed.
     */
    suspend fun syncAllDAVyCalendars() {
        Timber.d("========================================")
        Timber.d("REVERSE SYNC ALL: Scanning all DAVy calendars for changes")
        Timber.d("========================================")
        
        try {
            // Get all DAVy accounts and their calendars
            val accounts = accountRepository.getAll()
            Timber.d("Found ${accounts.size} DAVy accounts")
            
            for (account in accounts) {
                val calendars = calendarRepository.getByAccountId(account.id)
                Timber.d("Account ${account.accountName}: ${calendars.size} calendars")
                
                for (calendar in calendars) {
                    if (calendar.androidCalendarId != null) {
                        Timber.d("→ Scanning calendar: ${calendar.displayName} (Android ID: ${calendar.androidCalendarId})")
                        syncCalendarEvents(calendar.androidCalendarId)
                    }
                }
            }
            
            Timber.d("========================================")
            Timber.d("END REVERSE SYNC ALL")
            Timber.d("========================================")
        } catch (e: Exception) {
            Timber.e(e, "Error syncing all DAVy calendars")
        }
    }
    
    /**
     * Sync ONLY dirty events (reference implementation pattern).
     * More efficient than scanning all events when we know changes occurred.
     */
    suspend fun syncDirtyEventsOnly() {
        Timber.d("========================================")
        Timber.d("REVERSE SYNC DIRTY ONLY: Scanning for locally modified/deleted events")
        Timber.d("========================================")
        
        try {
            val accounts = accountRepository.getAll()
            var totalDirty = 0
            var totalDeleted = 0
            
            for (account in accounts) {
                val calendars = calendarRepository.getByAccountId(account.id)
                
                for (calendar in calendars) {
                    if (calendar.androidCalendarId != null) {
                        val dirtyCount = syncDirtyCalendarEvents(calendar.androidCalendarId)
                        totalDirty += dirtyCount
                        
                        val deletedCount = syncDeletedCalendarEvents(calendar.androidCalendarId)
                        totalDeleted += deletedCount
                    }
                }
            }
            
            Timber.d("✓ Total dirty events synced: $totalDirty")
            Timber.d("✓ Total deleted events synced: $totalDeleted")
            Timber.d("========================================")
            Timber.d("END REVERSE SYNC DIRTY ONLY")
            Timber.d("========================================")
        } catch (e: Exception) {
            Timber.e(e, "Error syncing dirty events")
        }
    }
    
    /**
     * Sync only dirty events from a specific calendar (reference implementation pattern).
     */
    private suspend fun syncDirtyCalendarEvents(androidCalendarId: Long): Int {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE
        )
        
        // Query ONLY dirty events (following reference implementation pattern)
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DIRTY} = 1 AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(androidCalendarId.toString()),
            null
        )
        
        var count = 0
        cursor?.use {
            val titleIndex = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            
            while (it.moveToNext()) {
                val eventId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                val title = it.getString(titleIndex)
                
                Timber.d("📝 Syncing DIRTY event: $title (ID: $eventId)")
                syncEventFromProvider(eventId)
                count++
            }
        }
        
        if (count > 0) {
            Timber.d("✓ Synced $count dirty events from calendar $androidCalendarId")
        }
        
        return count
    }
    
    /**
     * Sync deleted events from a specific calendar.
     * Since Android immediately purges deleted events (doesn't keep DELETED=1),
     * we detect deletions by finding events in DAVy DB that no longer exist in Calendar Provider.
     */
    private suspend fun syncDeletedCalendarEvents(androidCalendarId: Long): Int {
        // Find the DAVy calendar that corresponds to this Android calendar
        val accounts = accountRepository.getAll()
        var davyCalendar: com.davy.domain.model.Calendar? = null
        
        for (account in accounts) {
            val calendars = calendarRepository.getByAccountId(account.id)
            davyCalendar = calendars.firstOrNull { it.androidCalendarId == androidCalendarId }
            if (davyCalendar != null) break
        }
        
        if (davyCalendar == null) {
            Timber.w("Could not find DAVy calendar for Android calendar ID: $androidCalendarId")
            return 0
        }
        
        // Get all events for this calendar from DAVy database
        val davyEvents = eventRepository.getByCalendarId(davyCalendar.id)
        
        var count = 0
        
        // Check each event to see if it still exists in Calendar Provider
        for (davyEvent in davyEvents) {
            val androidEventId = davyEvent.androidEventId ?: continue
            
            // Query Calendar Provider to see if event still exists
            val projection = arrayOf(CalendarContract.Events._ID)
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(androidEventId.toString()),
                null
            )
            
            val existsInProvider = cursor?.use { it.count > 0 } ?: false
            
            if (!existsInProvider) {
                // Event was deleted from Calendar Provider - mark it for deletion
                Timber.d("🗑️ Event deleted from provider: ${davyEvent.title} (DAVy ID: ${davyEvent.id}, Android ID: $androidEventId)")
                Timber.d("   Event calendar ID in DAVy DB: ${davyEvent.calendarId}")
                Timber.d("   Marking as deleted and dirty in DAVy database for server deletion")
                
                // Mark as deleted and dirty in DAVy database (will be removed from server on next sync)
                val deletedEvent = davyEvent.copy(
                    deletedAt = System.currentTimeMillis(),
                    dirty = true
                )
                eventRepository.update(deletedEvent)
                
                // Verify the update worked
                val verifyEvent = eventRepository.getById(davyEvent.id)
                Timber.d("   ✅ VERIFIED: Event after update - dirty=${verifyEvent?.dirty}, deletedAt=${verifyEvent?.deletedAt}")
                
                count++
            }
        }
        
        if (count > 0) {
            Timber.d("✓ Detected $count deleted events from calendar $androidCalendarId")
        }
        
        return count
    }
    
    /**
     * Sync all events from a specific Android calendar.
     * Enhanced with reference implementation pattern: Query native DIRTY flag first for efficiency.
     */
    private suspend fun syncCalendarEvents(androidCalendarId: Long) {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DIRTY
        )
        
        // Query ALL events (we'll check DIRTY flag in the loop)
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(androidCalendarId.toString()),
            null
        )
        
        var totalCount = 0
        var dirtyCount = 0
        cursor?.use {
            val dirtyIndex = it.getColumnIndexOrThrow(CalendarContract.Events.DIRTY)
            
            while (it.moveToNext()) {
                val eventId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                val isDirty = it.getInt(dirtyIndex) == 1
                
                totalCount++
                
                // Following reference implementation pattern: Prioritize DIRTY events
                if (isDirty) {
                    dirtyCount++
                    Timber.d("📝 Event $eventId has DIRTY flag - syncing with priority")
                    syncEventFromProvider(eventId)
                } else {
                    // Still sync non-dirty events to catch any that were missed
                    syncEventFromProvider(eventId)
                }
            }
        }
        
        Timber.d("✓ Scanned $totalCount events ($dirtyCount with DIRTY flag) from calendar $androidCalendarId")
    }
    
    /**
     * Sync a single event from Calendar Provider to DAVy's database.
     * Marks the event as dirty so it will be uploaded to server.
     */
    suspend fun syncEventFromProvider(androidEventId: Long) {
        Timber.d("========================================")
        Timber.d("REVERSE SYNC: Android Event ID $androidEventId → DAVy DB")
        Timber.d("========================================")
        
        try {
            // Query event details from Calendar Provider
            Timber.d("→ Querying event from Calendar Provider...")
            val eventDetails = queryEventFromProvider(androidEventId)
            
            if (eventDetails == null) {
                Timber.w("✗ Event not found in Calendar Provider: $androidEventId")
                return
            }
            
            Timber.d("✓ Event found: '${eventDetails.title}'")
            Timber.d("  - Android Calendar ID: ${eventDetails.androidCalendarId}")
            Timber.d("  - Start: ${eventDetails.dtStart}")
            Timber.d("  - End: ${eventDetails.dtEnd}")
            Timber.d("  - All Day: ${eventDetails.allDay}")
            
            // Check if we already have this event in DAVy's database
            Timber.d("→ Checking if event already exists in DAVy DB...")
            var existingEvent = eventRepository.getById(eventDetails.davyEventId ?: 0)
                ?: findEventByAndroidId(androidEventId)
            
            // If not found by androidEventId, check by title + time in the same calendar
            if (existingEvent == null) {
                val calendarId = findDAVyCalendarId(eventDetails.androidCalendarId)
                if (calendarId != null) {
                    existingEvent = findEventByTitleAndTime(
                        calendarId,
                        eventDetails.title,
                        eventDetails.dtStart,
                        eventDetails.dtEnd
                    )
                    if (existingEvent != null) {
                        Timber.d("✓ Found existing event by title+time match (ID: ${existingEvent.id})")
                    }
                }
            }
            
            if (existingEvent != null) {
                // Update existing event
                Timber.d("✓ Found existing event in DAVy DB (ID: ${existingEvent.id})")
                Timber.d("→ Updating event and marking as dirty...")
                val updatedEvent = existingEvent.copy(
                    title = eventDetails.title,
                    description = eventDetails.description,
                    location = eventDetails.location,
                    dtStart = eventDetails.dtStart,
                    dtEnd = eventDetails.dtEnd,
                    allDay = eventDetails.allDay,
                    timezone = eventDetails.timezone,
                    androidEventId = androidEventId,
                    dirty = true, // Mark as dirty for upload
                    updatedAt = System.currentTimeMillis()
                )
                eventRepository.update(updatedEvent)
                Timber.d("✓ Event updated in DAVy DB and marked DIRTY for push sync")
            } else {
                // New event created in Calendar app
                Timber.d("✗ Event not found in DAVy DB - this is a NEW event")
                Timber.d("→ Finding DAVy calendar for Android calendar ${eventDetails.androidCalendarId}...")
                
                // Find the calendar this event belongs to
                val calendarId = findDAVyCalendarId(eventDetails.androidCalendarId)
                
                if (calendarId != null) {
                    Timber.d("✓ Found DAVy calendar ID: $calendarId")
                    Timber.d("→ Creating new event in DAVy DB...")
                    
                    val newEvent = CalendarEvent(
                        calendarId = calendarId,
                        eventUrl = "", // Will be assigned by server on upload
                        uid = generateUID(),
                        title = eventDetails.title,
                        description = eventDetails.description,
                        location = eventDetails.location,
                        dtStart = eventDetails.dtStart,
                        dtEnd = eventDetails.dtEnd,
                        allDay = eventDetails.allDay,
                        timezone = eventDetails.timezone,
                        androidEventId = androidEventId,
                        dirty = true, // Mark as dirty for upload
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    val eventId = eventRepository.insert(newEvent)
                    Timber.d("✓ NEW event inserted in DAVy DB with ID: $eventId")
                    Timber.d("✓ Event marked DIRTY for push sync")
                } else {
                    Timber.w("✗ Could not find DAVy calendar for Android calendar: ${eventDetails.androidCalendarId}")
                    Timber.w("✗ CANNOT SYNC - event will remain only on device")
                }
            }
            
            Timber.d("========================================")
            Timber.d("END REVERSE SYNC")
            Timber.d("========================================")
            
        } catch (e: Exception) {
            Timber.e(e, "Error syncing event from Calendar Provider")
        }
    }
    
    /**
     * Query event details from Calendar Provider.
     */
    private fun queryEventFromProvider(androidEventId: Long): EventDetails? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
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
                val deleted = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.DELETED))
                
                if (deleted == 1) {
                    // Event was deleted - handle separately
                    Timber.d("Event $androidEventId was deleted")
                    return null
                }
                
                return EventDetails(
                    androidEventId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID)),
                    androidCalendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)),
                    title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "",
                    description = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
                    location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
                    dtStart = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
                    dtEnd = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)),
                    allDay = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1,
                    timezone = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE))
                )
            }
        }
        
        return null
    }
    
    /**
     * Find event by Android event ID.
     */
    private suspend fun findEventByAndroidId(androidEventId: Long): CalendarEvent? {
        return eventRepository.getByAndroidEventId(androidEventId)
    }
    
    /**
     * Find event by title and time in a specific calendar.
     * Used to detect duplicates when androidEventId is not set.
     */
    private suspend fun findEventByTitleAndTime(
        calendarId: Long,
        title: String,
        dtStart: Long,
        dtEnd: Long
    ): CalendarEvent? {
        val events = eventRepository.getByCalendarId(calendarId)
        return events.find { 
            it.title == title && 
            it.dtStart == dtStart && 
            it.dtEnd == dtEnd
        }
    }
    
    /**
     * Find DAVy calendar ID from Android calendar ID.
     */
    private suspend fun findDAVyCalendarId(androidCalendarId: Long): Long? {
        // Query to find DAVy calendar that corresponds to this Android calendar
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.NAME // This should be the calendar URL
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
                val calendarUrl = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.NAME))
                
                // Look up DAVy calendar by URL
                val davyCalendar = calendarRepository.getByUrl(calendarUrl)
                return davyCalendar?.id
            }
        }
        
        return null
    }
    
    /**
     * Generate a UID for a new event.
     * Uses UUID with timestamp to ensure uniqueness.
     */
    private fun generateUID(): String {
        return "${java.util.UUID.randomUUID()}@davy-${System.currentTimeMillis()}"
    }
    
    /**
     * Data class to hold event details from Calendar Provider.
     */
    private data class EventDetails(
        val androidEventId: Long,
        val androidCalendarId: Long,
        val title: String,
        val description: String?,
        val location: String?,
        val dtStart: Long,
        val dtEnd: Long,
        val allDay: Boolean,
        val timezone: String?,
        val davyEventId: Long? = null
    )
}
