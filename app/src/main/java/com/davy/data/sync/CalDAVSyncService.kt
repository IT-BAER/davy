package com.davy.data.sync

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.davy.data.local.CredentialStore
import com.davy.data.remote.caldav.CalDAVClient
import com.davy.data.remote.caldav.CalDAVXMLParser
import com.davy.data.remote.caldav.ICalendarParser
import com.davy.data.repository.CalendarEventRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.TaskRepository
import com.davy.domain.model.Account
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import com.davy.domain.model.Task
import com.davy.domain.model.toICalendar
import com.davy.ui.util.AppError
import com.davy.ui.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.LinkedHashSet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalDAV synchronization service.
 * Implements full bidirectional sync: download changes from server + upload local changes.
 */
@Singleton
class CalDAVSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calDAVClient: CalDAVClient,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: CalendarEventRepository,
    private val taskRepository: TaskRepository,
    private val credentialStore: CredentialStore,
    private val syncLock: com.davy.sync.SyncLock
) {
    
    companion object {
    /**
     * Maximum number of events to fetch in a single calendar-multiget request.
     * Batch requests to balance efficiency and memory usage.
     */
        const val MAX_MULTIGET_RESOURCES = 10
    }
    
    /**
     * Sync all calendars for given account.
     * Returns SyncResult with statistics.
     */
    suspend fun syncAccount(account: Account): SyncResult {
        return syncLock.withLock {
            syncAccountInternal(account)
        }
    }
    
    /**
     * Sync a specific calendar.
     * Returns SyncResult with statistics for this calendar only.
     * 
     * @param pushOnly If true, only uploads dirty events without downloading from server (optimized for local changes)
     */
    suspend fun syncCalendar(account: Account, calendar: Calendar, pushOnly: Boolean = false): SyncResult {
        return syncLock.withLock {
            Timber.d("========================================")
            if (pushOnly) {
                Timber.d("⚡ STARTING PUSH-ONLY SYNC")
                Timber.d("   Calendar: ${calendar.displayName}")
                Timber.d("   Mode: Upload dirty events only (NO DOWNLOAD)")
                Timber.d("   WILL update lastSyncedAt timestamp")
            } else {
                Timber.d("🔄 STARTING FULL BIDIRECTIONAL SYNC")
                Timber.d("   Calendar: ${calendar.displayName}")
                Timber.d("   Mode: Download from server + Upload dirty events")
                Timber.d("   WILL update lastSyncedAt timestamp")
            }
            Timber.d("========================================")
            
            // Demo account handling - just update timestamp without actual sync
            if (account.serverUrl.equals("https://demo.local", ignoreCase = true)) {
                Timber.d("Demo account detected - updating lastSyncedAt for calendar: ${calendar.displayName}")
                val currentTime = System.currentTimeMillis()
                calendarRepository.update(calendar.copy(lastSyncedAt = currentTime))
                return@withLock SyncResult.Success(0, 0, 0)
            }
            
            val password = credentialStore.getPassword(account.id)
            if (password == null) {
                Timber.e("No credentials found for account: ${account.id}")
                return@withLock SyncResult.Error("No credentials found for account ${account.accountName}")
            }
            
            if (!calendar.syncEnabled) {
                Timber.d("Calendar sync is disabled: ${calendar.displayName}")
                return@withLock SyncResult.Success(0, 0, 0)
            }
            
            try {
                val result = if (pushOnly) {
                    // Push-only mode: Skip download phase, only upload dirty events
                    Timber.d("⚡ Push-only mode: Uploading dirty events only")
                    val uploaded = uploadDirtyEvents(calendar, account, password)
                    
                    // Update last sync time to show in UI
                    Timber.d("📊 ⏰ UPDATING lastSyncedAt timestamp (push-only sync completed)")
                    calendarRepository.update(calendar.copy(
                        lastSyncedAt = System.currentTimeMillis()
                    ))
                    
                    EventSyncResult(eventsDownloaded = 0, eventsUploaded = uploaded)
                } else {
                    // Full bidirectional sync (download + upload)
                    Timber.d("🔄 Full sync mode: Syncing calendar events (download + upload)")
                    syncCalendarEvents(calendar, account, password)
                }
                
                Timber.d("========================================")
                if (pushOnly) {
                    Timber.d("✓ PUSH-ONLY SYNC COMPLETED: ↑${result.eventsUploaded} uploaded")
                } else {
                    Timber.d("✓ FULL SYNC COMPLETED: ↓${result.eventsDownloaded} downloaded, ↑${result.eventsUploaded} uploaded")
                }
                Timber.d("========================================")
                return@withLock SyncResult.Success(0, result.eventsDownloaded, result.eventsUploaded)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync calendar: ${calendar.displayName}")
                return@withLock SyncResult.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Get the count of dirty events for a specific calendar.
     * Used by SyncAdapter gate to determine if a sync is needed.
     */
    suspend fun getDirtyEventCount(calendarId: Long): Int {
        return eventRepository.getDirtyEventsByCalendarId(calendarId).size
    }
    
    private suspend fun syncAccountInternal(account: Account): SyncResult {
        Timber.d("Starting CalDAV sync for account: ${account.accountName}")
        
        // Demo account handling - just update timestamps without actual sync
        if (account.serverUrl.equals("https://demo.local", ignoreCase = true)) {
            Timber.d("Demo account detected - updating lastSyncedAt for all calendars")
            val calendars = calendarRepository.getByAccountId(account.id)
            val currentTime = System.currentTimeMillis()
            calendars.forEach { calendar ->
                calendarRepository.update(calendar.copy(lastSyncedAt = currentTime))
            }
            return SyncResult.Success(0, 0, 0)
        }
        
        val password = credentialStore.getPassword(account.id)
        if (password == null) {
            Timber.e("No credentials found for account: ${account.id}")
            return SyncResult.Error("No credentials found for account ${account.accountName}")
        }
        
        var calendarsAdded = 0
        var eventsDownloaded = 0
        var eventsUploaded = 0
        var errors = 0
        
        try {
            // Get all calendars for this account from local database
            val localCalendars = calendarRepository.getByAccountId(account.id)
            Timber.d("Found ${localCalendars.size} calendars in local database for account: ${account.accountName}")
            
            if (localCalendars.isEmpty()) {
                Timber.w("No calendars found for account ${account.accountName}. Calendar discovery should happen during account creation.")
                return SyncResult.Success(0, 0, 0)
            }
            
            // Sync events for each calendar IN PARALLEL
            // Use coroutineScope to launch parallel async operations
            coroutineScope {
                val syncJobs = localCalendars.map { calendar ->
                    async {
                        if (!calendar.syncEnabled) {
                            Timber.d("Skipping disabled calendar: ${calendar.displayName}")
                            return@async EventSyncResult(0, 0)
                        }
                        
                        try {
                            Timber.d("Syncing calendar: ${calendar.displayName} (${calendar.calendarUrl})")
                            syncCalendarEvents(calendar, account, password)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync calendar: ${calendar.displayName}")
                            errors++
                            EventSyncResult(0, 0)
                        }
                    }
                }
                
                // Collect all results
                val results = syncJobs.map { it.await() }
                eventsDownloaded = results.sumOf { it.eventsDownloaded }
                eventsUploaded = results.sumOf { it.eventsUploaded }
            }
            
            Timber.d("CalDAV sync completed: +$calendarsAdded calendars, ↓$eventsDownloaded ↑$eventsUploaded events, $errors errors")
            return SyncResult.Success(calendarsAdded, eventsDownloaded, eventsUploaded)
            
        } catch (e: Exception) {
            Timber.e(e, "CalDAV sync failed for account: ${account.accountName}")
            return SyncResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Sync events for a single calendar using Collection Sync (REPORT sync-collection) if available,
     * falling back to PROPFIND if this is the first sync or server doesn't support it.
     */
    private suspend fun syncCalendarEvents(
        calendar: Calendar,
        account: Account,
        password: String
    ): EventSyncResult {
        // Skip sync for calendars without a URL (newly created, waiting for server URL)
        if (calendar.calendarUrl.isBlank()) {
            Timber.w("Skipping sync for calendar '${calendar.displayName}' - no calendar URL yet")
            return EventSyncResult(0, 0)
        }
        
        // Force full sync if last sync was more than 7 days ago (to catch missed deletions)
        val daysSinceLastSync = if (calendar.lastSyncedAt != null) {
            (System.currentTimeMillis() - calendar.lastSyncedAt) / (1000 * 60 * 60 * 24)
        } else {
            Long.MAX_VALUE
        }
        val forceFullSync = daysSinceLastSync > 7
        
        // Use Collection Sync if we have a sync-token (incremental sync)
        if (calendar.syncToken != null && !forceFullSync) {
            Timber.d("📊 Using Collection Sync (incremental) with token: ${calendar.syncToken}")
            val result = syncWithCollectionSync(calendar, account, password)
            if (result != null) {
                return result
            }
            Timber.w("Collection Sync failed, falling back to PROPFIND")
        } else {
            if (forceFullSync) {
                Timber.d("📊 Forcing full PROPFIND sync (last sync was $daysSinceLastSync days ago)")
            } else {
                Timber.d("📊 No sync-token, using PROPFIND (full sync)")
            }
        }
        
        // Fall back to traditional PROPFIND sync
        return syncWithPropfind(calendar, account, password)
    }
    
    /**
     * Sync events using WebDAV REPORT sync-collection (efficient incremental sync).
     * Returns null if sync-collection is not supported or fails.
     */
    private suspend fun syncWithCollectionSync(
        calendar: Calendar,
        account: Account,
        password: String
    ): EventSyncResult? {
        try {
            val syncCollectionXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:sync-collection xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/">
                    <d:sync-token>${calendar.syncToken}</d:sync-token>
                    <d:sync-level>1</d:sync-level>
                    <d:prop>
                        <d:getetag/>
                        <cs:getctag/>
                    </d:prop>
                </d:sync-collection>
            """.trimIndent()
            
            val response = calDAVClient.reportRequest(
                url = calendar.calendarUrl,
                username = account.username,
                password = password,
                requestBody = syncCollectionXml
            )
            
            if (!response.isSuccessful) {
                Timber.e("Collection Sync REPORT failed: ${response.statusCode}")
                
                // Show notification for auth errors
                if (response.statusCode == 401 || response.statusCode == 403) {
                    NotificationHelper.showHttpErrorNotification(
                        context,
                        response.statusCode,
                        account.accountName,
                        account.id
                    )
                }
                
                return null
            }
            
            val syncResponse = CalDAVXMLParser.parseSyncCollection(response.body ?: "")
            Timber.d("✓ Collection Sync found ${syncResponse.changes.size} changes, new token: ${syncResponse.newSyncToken}")
            
            var downloaded = 0
            var uploaded: Int
            var deleted = 0
            
            // Process changes (added/modified/deleted events)
            syncResponse.changes.forEach { change ->
                val absoluteUrl = if (change.href.startsWith("http")) {
                    change.href
                } else {
                    calendar.calendarUrl.removeSuffix("/") + "/" + change.href.removePrefix("/")
                }

                when (change.status) {
                    200, 201 -> {
                        val localEvent = eventRepository.getByUrl(change.href)
                        if (localEvent == null || localEvent.etag != change.etag) {
                            val eventData = calDAVClient.getEvent(absoluteUrl, account.username, password)
                            val statusCode = eventData.statusCode
                            if (statusCode == 404 || statusCode == 410) {
                                Timber.w("Remote returned HTTP $statusCode for ${change.href}, removing local event copy")
                                handleMissingRemoteEvent(
                                    account = account,
                                    calendar = calendar,
                                    href = change.href,
                                    absoluteUrl = absoluteUrl,
                                    statusCode = statusCode
                                )
                                return@forEach
                            }

                            val body = eventData.body
                            if (eventData.isSuccessful && !body.isNullOrBlank()) {
                                val events = ICalendarParser.parseEvents(body)
                                events.forEach eventsInChange@{ event ->
                                    val existingEventByUid = eventRepository.getByUid(event.uid)
                                    
                                    // Skip downloading if event is marked for deletion locally
                                    if (existingEventByUid != null && existingEventByUid.isDeleted()) {
                                        Timber.d("Skipping download - event marked for deletion: ${event.title} (UID: ${event.uid})")
                                        return@eventsInChange
                                    }
                                    
                                    val eventToSave = event.copy(
                                        calendarId = calendar.id,
                                        eventUrl = change.href,
                                        etag = change.etag
                                    )

                                    if (existingEventByUid == null) {
                                        eventRepository.insert(eventToSave)
                                        Timber.d("📥 Inserted new event: ${event.title}")
                                    } else {
                                        eventRepository.update(eventToSave.copy(id = existingEventByUid.id))
                                        Timber.d("📥 Updated event: ${event.title}")
                                    }
                                    downloaded++
                                }
                            } else {
                                if (!eventData.isSuccessful) {
                                    Timber.e("Failed to download event during Collection Sync: $statusCode")
                                } else {
                                    Timber.w("Downloaded event had empty calendar-data: ${change.href}")
                                }
                                return@forEach
                            }
                        }
                    }

                    404, 410 -> {
                        Timber.d("Server reports event deleted: ${change.href} (status ${change.status})")
                        deleted++
                        handleMissingRemoteEvent(
                            account = account,
                            calendar = calendar,
                            href = change.href,
                            absoluteUrl = absoluteUrl,
                            statusCode = change.status
                        )
                    }
                    else -> {
                        Timber.w("Unexpected status in sync-collection: ${change.status} for ${change.href}")
                    }
                }
            }
            
            // Update sync token and last sync time
            syncResponse.newSyncToken?.let { newToken ->
                Timber.d("📊 Updating calendar with new sync-token: $newToken")
                Timber.d("📊 ⏰ UPDATING lastSyncedAt timestamp (full sync completed)")
                calendarRepository.update(calendar.copy(
                    syncToken = newToken,
                    lastSyncedAt = System.currentTimeMillis()
                ))
            } ?: run {
                // Even if no new token, update last sync time
                Timber.d("📊 No new sync-token, but updating lastSyncedAt anyway")
                Timber.d("📊 ⏰ UPDATING lastSyncedAt timestamp (full sync completed)")
                calendarRepository.update(calendar.copy(
                    lastSyncedAt = System.currentTimeMillis()
                ))
            }
            
            // Upload dirty local events
            uploaded = uploadDirtyEvents(calendar, account, password)
            
            Timber.d("✓ Collection Sync complete: ↓$downloaded events, ↑$uploaded events, 🗑️$deleted events")
            return EventSyncResult(downloaded, uploaded)
            
        } catch (e: Exception) {
            Timber.e(e, "Collection Sync failed")
            return null
        }
    }
    
    /**
     * Sync events using traditional PROPFIND (full sync).
     */
    private suspend fun syncWithPropfind(
        calendar: Calendar,
        account: Account,
        password: String
    ): EventSyncResult {
        var downloaded = 0
        var uploaded = 0
        
        try {
            // Get list of events on server
            val eventsPropfind = calDAVClient.propfindEvents(
                calendarUrl = calendar.calendarUrl,
                username = account.username,
                password = password
            )
            
            if (!eventsPropfind.isSuccessful) {
                Timber.e("Events PROPFIND failed for ${calendar.displayName}")
                
                // Show notification for auth errors
                if (eventsPropfind.statusCode == 401 || eventsPropfind.statusCode == 403) {
                    NotificationHelper.showHttpErrorNotification(
                        context,
                        eventsPropfind.statusCode,
                        account.accountName,
                        account.id
                    )
                }
                
                return EventSyncResult(0, 0)
            }
            
            val serverEvents = CalDAVXMLParser.parseEventPropfind(eventsPropfind.body ?: "")
            Timber.d("Found ${serverEvents.size} events on server for calendar: ${calendar.displayName}")
            
            // FIRST: Delete events that exist locally but not on server
            // Do this BEFORE downloading to ensure deletions happen even if download fails
            val localEvents = eventRepository.getByCalendarId(calendar.id)
            val serverHrefs = serverEvents.map { it.href }.toSet()
            localEvents.forEach { localEvent ->
                if (localEvent.eventUrl.isNotBlank() && !serverHrefs.contains(localEvent.eventUrl)) {
                    removeLocalEvent(
                        calendar = calendar,
                        event = localEvent,
                        reason = "Event missing from server listing"
                    )
                }
            }
            
            // SECOND: Download new/updated events using batched multiget
            // Collect events needing download, then fetch in batches
            val eventsToDownload = mutableListOf<String>()
            val localEventMap = eventRepository.getByCalendarId(calendar.id).associateBy { it.eventUrl }
            
            Timber.d("DEBUG: localEventMap contains ${localEventMap.size} events with keys:")
            localEventMap.keys.take(3).forEach { Timber.d("  Local key: '$it'") }
            
            serverEvents.forEach { eventResource ->
                val localEvent = localEventMap[eventResource.href]
                if (localEvent == null || localEvent.etag != eventResource.etag) {
                    if (localEvent == null) {
                        Timber.d("DEBUG: No local match for server href '${eventResource.href}'")
                    } else {
                        Timber.d("DEBUG: ETag mismatch - server: '${eventResource.etag}', local: '${localEvent.etag}' for '${eventResource.href}'")
                    }
                    eventsToDownload.add(eventResource.href)
                }
            }
            
            Timber.d("Need to download ${eventsToDownload.size} events (out of ${serverEvents.size} total)")
            
            // Download in batches of MAX_MULTIGET_RESOURCES
            eventsToDownload.chunked(MAX_MULTIGET_RESOURCES).forEach { batch ->
                try {
                    Timber.d("Downloading batch of ${batch.size} events")
                    Timber.d("Event hrefs: ${batch.joinToString()}")
                    
                    val multigetResponse = calDAVClient.multigetEvents(
                        calendarUrl = calendar.calendarUrl,
                        eventHrefs = batch,
                        username = account.username,
                        password = password
                    )
                    
                    if (!multigetResponse.isSuccessful) {
                        Timber.e("Multiget failed for batch, status: ${multigetResponse.statusCode}")
                        Timber.e("Response body: ${multigetResponse.body?.take(500)}")
                        return@forEach
                    }
                    
                    val fetchedEvents = CalDAVXMLParser.parseEventPropfind(multigetResponse.body ?: "")
                    Timber.d("Multiget returned ${fetchedEvents.size} events with calendar-data")
                    
                    fetchedEvents.forEach fetchedEventsLoop@{ eventResource ->
                        try {
                            // Check for null calendar-data
                            val calendarData = eventResource.calendarData
                            if (calendarData == null || calendarData.isBlank()) {
                                Timber.w("Event ${eventResource.href} has no calendar-data or it's empty, skipping")
                                return@fetchedEventsLoop
                            }
                            
                            Timber.d("Processing downloaded event: ${eventResource.href}")
                            val events = ICalendarParser.parseEvents(calendarData)
                            Timber.d("Parsed ${events.size} events from iCalendar")
                            
                            events.forEach eventsLoop@{ event ->
                                // Check for duplicate by UID (unique identifier)
                                val existingEventByUid = eventRepository.getByUid(event.uid)
                                
                                // Skip downloading if event is marked for deletion locally
                                if (existingEventByUid != null && existingEventByUid.isDeleted()) {
                                    Timber.d("Skipping download - event marked for deletion: ${event.title} (UID: ${event.uid})")
                                    return@eventsLoop
                                }
                                
                                val eventToSave = event.copy(
                                    calendarId = calendar.id,
                                    eventUrl = eventResource.href,
                                    etag = eventResource.etag
                                )
                                
                                if (existingEventByUid == null) {
                                    eventRepository.insert(eventToSave)
                                    Timber.d("Inserted new event: ${event.title} (UID: ${event.uid})")
                                    downloaded++
                                } else {
                                    eventRepository.update(eventToSave.copy(id = existingEventByUid.id))
                                    Timber.d("Updated existing event: ${event.title} (UID: ${event.uid})")
                                    // Don't increment downloaded counter for updates - only new events count
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process event from multiget: ${eventResource.href}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download batch of ${batch.size} events")
                }
            }
            
            // Upload local changes (pending events marked as dirty)
            uploaded = uploadDirtyEvents(calendar, account, password)
            
            // Get sync-token from calendar for next Collection Sync
            var lastSyncUpdated = false
            try {
                val calendarPropfind = calDAVClient.propfindCalendars(
                    url = calendar.calendarUrl,
                    username = account.username,
                    password = password
                )
                if (calendarPropfind.isSuccessful) {
                    val calendarResources = CalDAVXMLParser.parseCalendarPropfind(calendarPropfind.body ?: "")
                    val token = calendarResources.firstOrNull()?.syncToken 
                        ?: calendarResources.firstOrNull()?.ctag  // Fallback to ctag if no sync-token
                    
                    token?.let {
                        Timber.d("📊 Storing sync-token for next Collection Sync: $it")
                        Timber.d("📊 ⏰ UPDATING lastSyncedAt timestamp (full sync completed)")
                        calendarRepository.update(calendar.copy(
                            syncToken = it,
                            lastSyncedAt = System.currentTimeMillis()
                        ))
                        lastSyncUpdated = true
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch/store sync-token")
            }
            
            // Always update last sync time even if token fetch failed
            if (!lastSyncUpdated) {
                Timber.d("📊 ⏰ UPDATING lastSyncedAt timestamp (full sync completed, no token update)")
                calendarRepository.update(calendar.copy(
                    lastSyncedAt = System.currentTimeMillis()
                ))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync events for calendar: ${calendar.displayName}")
        }
        
        return EventSyncResult(downloaded, uploaded)
    }
    
    /**
     * Upload all dirty events for a calendar.
     */
    private suspend fun uploadDirtyEvents(
        calendar: Calendar,
        account: Account,
        password: String
    ): Int {
        // Check if calendar is force read-only
        if (calendar.forceReadOnly) {
            val dirtyCount = eventRepository.getDirtyEventsByCalendarId(calendar.id).size
            if (dirtyCount > 0) {
                Timber.w("Calendar '${calendar.displayName}' is set to force read-only. Skipping upload of $dirtyCount dirty event(s).")
            } else {
                Timber.d("Calendar '${calendar.displayName}' is read-only. No dirty events to skip.")
            }
            return 0
        }
        
        var uploaded = 0
        
        Timber.d("→ Checking for dirty events to upload...")
        Timber.d("   Calendar ID (DAVy DB): ${calendar.id}, Name: ${calendar.displayName}")
        val dirtyEvents = eventRepository.getDirtyEventsByCalendarId(calendar.id)
        Timber.d("   Found ${dirtyEvents.size} dirty events for this calendar")
        
        if (dirtyEvents.isNotEmpty()) {
            Timber.d("========================================")
            Timber.d("✓ Found ${dirtyEvents.size} DIRTY events to upload for calendar: ${calendar.displayName}")
            Timber.d("========================================")
            
            dirtyEvents.forEach { event ->
                try {
                    // Check if event is marked for deletion
                    if (event.isDeleted()) {
                        Timber.d("→ Deleting event from server: '${event.title}' (ID: ${event.id}, UID: ${event.uid})")
                        val deleteResult = deleteEvent(event, account, password)
                        if (deleteResult) {
                            uploaded++
                            // Actually delete the event from local DB after successful server deletion
                            eventRepository.delete(event)
                            Timber.d("✓ Successfully deleted from server and local DB: ${event.title}")
                        } else {
                            Timber.w("✗ Delete failed for event: ${event.title}")
                        }
                    } else {
                        Timber.d("→ Uploading event: '${event.title}' (ID: ${event.id}, UID: ${event.uid})")
                        val uploadResult = uploadEvent(event, calendar, account, password)
                        if (uploadResult) {
                            uploaded++
                            eventRepository.markClean(event.id)
                            Timber.d("✓ Successfully uploaded and marked clean: ${event.title}")
                        } else {
                            Timber.w("✗ Upload failed for event: ${event.title}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "✗ Exception processing event: ${event.title}")
                }
            }
        } else {
            Timber.d("✓ No dirty events to upload for calendar: ${calendar.displayName}")
        }
        
        return uploaded
    }
    
    /**
     * Upload a dirty event to the CalDAV server.
     */
    private suspend fun uploadEvent(
        event: CalendarEvent,
        calendar: Calendar,
        account: Account,
        password: String
    ): Boolean {
        Timber.d("Uploading event to server: ${event.title}")
        
        try {
            // Convert event to iCalendar format
            val icalData = event.toICalendar()
            
            // Determine if this is a new event or an update
            val isNewEvent = event.eventUrl.isBlank()
            
            val eventUrl = if (isNewEvent) {
                // Generate URL for new event
                "${calendar.calendarUrl}${event.uid}.ics"
            } else {
                // Use existing URL for update
                if (event.eventUrl.startsWith("http")) {
                    event.eventUrl
                } else {
                    "${account.serverUrl}${event.eventUrl}"
                }
            }
            
            Timber.d("Event URL: $eventUrl")
            Timber.d("iCalendar data: $icalData")
            
            // PUT request to upload/update event
            val response = calDAVClient.putEvent(
                eventUrl = eventUrl,
                username = account.username,
                password = password,
                icalendarData = icalData,
                etag = if (isNewEvent) null else event.etag
            )
            
            if (response.isSuccessful) {
                // Update event with server response (new etag, URL)
                val newEtag = response.headers["ETag"]?.firstOrNull() ?: response.headers["etag"]?.firstOrNull()
                if (newEtag != null) {
                    eventRepository.updateETag(event.id, newEtag.trim('"'))
                }
                
                // Update event URL if it was a new event
                if (isNewEvent) {
                    val updatedEvent = event.copy(eventUrl = eventUrl)
                    eventRepository.update(updatedEvent)
                }
                
                Timber.d("Event uploaded successfully: ${response.statusCode}")
                return true
            } else {
                Timber.e("Failed to upload event: ${response.statusCode} - ${response.error ?: "Unknown error"}")
                return false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception uploading event")
            return false
        }
    }
    
    /**
     * Delete an event from the CalDAV server.
     */
    private suspend fun deleteEvent(
        event: CalendarEvent,
        account: Account,
        password: String
    ): Boolean {
        Timber.d("Deleting event from server: ${event.title}")
        
        try {
            // Build event URL
            val eventUrl = if (event.eventUrl.startsWith("http")) {
                event.eventUrl
            } else {
                "${account.serverUrl}${event.eventUrl}"
            }
            
            Timber.d("DELETE request to: $eventUrl")
            
            // DELETE request to remove event from server
            val response = calDAVClient.deleteEvent(
                eventUrl = eventUrl,
                username = account.username,
                password = password,
                etag = event.etag
            )
            
            if (response.isSuccessful) {
                Timber.d("Event deleted successfully: ${response.statusCode}")
                return true
            } else {
                Timber.e("Failed to delete event: ${response.statusCode} - ${response.error ?: "Unknown error"}")
                return false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception deleting event")
            return false
        }
    }
    
    private suspend fun handleMissingRemoteEvent(
        account: Account,
        calendar: Calendar,
        href: String,
        absoluteUrl: String?,
        statusCode: Int
    ) {
        Timber.d("handleMissingRemoteEvent: href=$href, absoluteUrl=$absoluteUrl, status=$statusCode")
        val candidates = buildEventUrlCandidates(account, calendar, href, absoluteUrl)
        Timber.d("Built ${candidates.size} URL candidates: $candidates")
        val localEvent = findLocalEventByCandidates(candidates)
        if (localEvent == null) {
            Timber.w("Remote returned HTTP $statusCode for $href but no matching local event was found")
            return
        }
        Timber.d("Found local event to delete: '${localEvent.title}' (id=${localEvent.id}, uid=${localEvent.uid})")
        removeLocalEvent(
            calendar = calendar,
            event = localEvent,
            reason = "Remote returned HTTP $statusCode"
        )
    }

    private suspend fun findLocalEventByCandidates(candidates: List<String>): CalendarEvent? {
        for (candidate in candidates) {
            val event = eventRepository.getByUrl(candidate)
            if (event != null) {
                return event
            }
        }
        return null
    }

    private fun buildEventUrlCandidates(
        account: Account,
        calendar: Calendar,
        href: String?,
        absoluteUrl: String?
    ): List<String> {
        val candidates = LinkedHashSet<String>()

        fun addCandidate(raw: String?) {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (candidates.add(value)) {
                val isAbsolute = value.startsWith("http://") || value.startsWith("https://")
                if (!isAbsolute) {
                    if (value.startsWith("/")) {
                        candidates.add(value.removePrefix("/"))
                    } else {
                        candidates.add("/$value")
                    }
                }
            }
        }

        href?.let { addCandidate(it) }

        absoluteUrl?.let { url ->
            addCandidate(url)
            val serverBase = account.serverUrl.trimEnd('/')
            if (url.startsWith(serverBase)) {
                addCandidate(url.removePrefix(serverBase))
            }
            val calendarBase = calendar.calendarUrl.trimEnd('/')
            if (url.startsWith(calendarBase)) {
                addCandidate(url.removePrefix(calendarBase))
            }
        }

        return candidates.toList()
    }

    private suspend fun removeLocalEvent(
        calendar: Calendar,
        event: CalendarEvent,
        reason: String
    ) {
        Timber.d("🗑️ Removing local event '${event.title}' (uid=${event.uid}) - $reason")
        Timber.d("📱 Event details: androidEventId=${event.androidEventId}, calendarAndroidId=${calendar.androidCalendarId}")
        try {
            var removedFromProvider = false

            event.androidEventId?.let { androidEventId ->
                Timber.d("📱 Attempting deletion by androidEventId: $androidEventId")
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, androidEventId)
                val deletedRows = context.contentResolver.delete(uri, null, null)
                Timber.d("📱 Deleted from Calendar Provider by ID: $deletedRows row(s)")
                removedFromProvider = deletedRows > 0
            }

            if (!removedFromProvider && calendar.androidCalendarId != null && event.uid.isNotBlank()) {
                Timber.d("📱 Attempting deletion by UID: ${event.uid}, calendar: ${calendar.androidCalendarId}")
                val deletedRows = context.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.UID_2445} = ?",
                    arrayOf(calendar.androidCalendarId.toString(), event.uid)
                )
                Timber.d("📱 Deleted from Calendar Provider by UID: $deletedRows row(s)")
                removedFromProvider = deletedRows > 0
            }

            if (!removedFromProvider) {
                Timber.w("📱 Unable to remove event from Calendar Provider (androidEventId=${event.androidEventId}, calendarAndroidId=${calendar.androidCalendarId})")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete event from Calendar Provider for UID ${event.uid}")
        }

        try {
            eventRepository.delete(event)
            Timber.d("✓ Local event removed from repository: ${event.title}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove event ${event.uid} from repository")
        }
    }
    
    /**
     * Parse calendar color from CalDAV format (#RRGGBBAA) to Android Int.
     */
    private fun parseCalendarColor(colorString: String?): Int {
        if (colorString == null) return 0xFF2196F3.toInt() // Default blue
        
        return try {
            android.graphics.Color.parseColor(colorString)
        } catch (e: Exception) {
            0xFF2196F3.toInt()
        }
    }
    
    /**
     * Sync tasks for a specific task list.
     * Returns a Pair of (tasksDownloaded, tasksUploaded).
     * 
     * Implements full bidirectional VTODO sync following the same pattern as calendar sync:
     * 1. Upload dirty tasks (local changes) first
     * 2. Delete tasks marked for deletion from server
     * 3. Query server for all VTODOs in the task list
     * 4. Download and parse new/modified VTODOs
     * 5. Store tasks in local database
     */
    suspend fun syncTaskList(account: Account, taskList: com.davy.domain.model.TaskList): Pair<Int, Int> {
        Timber.d("========================================")
        Timber.d("🔄 STARTING TASK LIST SYNC")
        Timber.d("   Task List: ${taskList.displayName}")
        Timber.d("   URL: ${taskList.url}")
        Timber.d("========================================")
        
        val password = credentialStore.getPassword(account.id)
        if (password == null) {
            Timber.e("No password found for account ${account.id}")
            return 0 to 0
        }
        
        var tasksDownloaded = 0
        var tasksUploaded = 0
        
        try {
            // Step 1: Upload dirty tasks first (local changes take precedence)
            tasksUploaded = uploadDirtyTasks(account, taskList, password)
            Timber.d("Uploaded $tasksUploaded dirty tasks")
            
            // Step 2: Delete tasks marked for deletion
            val deletedTasks = deleteMarkedTasks(account, taskList, password)
            Timber.d("Deleted $deletedTasks tasks from server")
            
            // Step 3: Query server for all VTODOs
            Timber.d("Querying server for VTODO components...")
            val response = calDAVClient.reportTaskQuery(
                taskListUrl = taskList.url,
                username = account.username,
                password = password
            )
            
            if (!response.isSuccessful) {
                Timber.e("Failed to query tasks: ${response.statusCode} - ${response.error}")
                return tasksDownloaded to tasksUploaded
            }
            
            val responseBody = response.body
            if (responseBody.isNullOrBlank()) {
                Timber.d("Empty response from task list query - no tasks on server")
                return tasksDownloaded to tasksUploaded
            }
            
            // Step 4: Parse the multistatus response to get task URLs and ETags
            // Reuse parseEventPropfind since VTODO uses same calendar-multiget XML structure
            val serverTasks = CalDAVXMLParser.parseEventPropfind(responseBody)
            Timber.d("Found ${serverTasks.size} tasks on server")
            
            // Track server URLs for orphan detection
            val serverUrls = mutableSetOf<String>()
            
            // Step 5: Process each task from server
            for (taskResource in serverTasks) {
                val href = taskResource.href
                val etag = taskResource.etag.trim('"')
                val calendarData = taskResource.calendarData
                
                // Build absolute URL
                val taskUrl = if (href.startsWith("http")) {
                    href
                } else {
                    "${account.serverUrl.trimEnd('/')}${if (href.startsWith("/")) href else "/$href"}"
                }
                serverUrls.add(taskUrl)
                
                // Check if we already have this task locally
                val existingTask = taskRepository.getByUrl(taskUrl)
                
                if (existingTask != null) {
                    // Task exists - check if ETag changed
                    if (existingTask.etag == etag) {
                        Timber.d("Task unchanged (same ETag): ${existingTask.summary}")
                        continue
                    }
                    
                    // Skip if local task is dirty (will be uploaded on next sync)
                    if (existingTask.dirty) {
                        Timber.d("Skipping server update for dirty task: ${existingTask.summary}")
                        continue
                    }
                    
                    Timber.d("Task ETag changed, updating: ${existingTask.summary}")
                }
                
                // Parse and store the task
                if (calendarData != null && calendarData.isNotBlank()) {
                    val parsedTasks = ICalendarParser.parseTasks(calendarData)
                    if (parsedTasks.isNotEmpty()) {
                        val task = parsedTasks.first()
                        
                        val taskToSave = if (existingTask != null) {
                            // Update existing task
                            task.copy(
                                id = existingTask.id,
                                taskListId = taskList.id,
                                url = taskUrl,
                                etag = etag,
                                dirty = false,
                                deleted = false
                            )
                        } else {
                            // New task
                            task.copy(
                                taskListId = taskList.id,
                                url = taskUrl,
                                etag = etag,
                                dirty = false,
                                deleted = false
                            )
                        }
                        
                        if (existingTask != null) {
                            taskRepository.updateWithAlarms(taskToSave)
                            Timber.d("✓ Updated task: ${taskToSave.summary} (${taskToSave.alarms.size} alarms)")
                        } else {
                            taskRepository.insertWithAlarms(taskToSave)
                            Timber.d("✓ Inserted new task: ${taskToSave.summary} (${taskToSave.alarms.size} alarms)")
                        }
                        tasksDownloaded++
                    }
                } else {
                    Timber.w("No calendar-data for task $href")
                }
            }
            
            // Step 6: Remove local tasks that no longer exist on server
            // (Only tasks that are not dirty and not marked for deletion)
            val localTasks = taskRepository.getByTaskListId(taskList.id)
                .first()
            for (localTask in localTasks) {
                if (!serverUrls.contains(localTask.url) && !localTask.dirty && !localTask.deleted) {
                    Timber.d("🗑️ Removing orphaned local task: ${localTask.summary}")
                    taskRepository.delete(localTask)
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error syncing task list: ${taskList.displayName}")
        }
        
        Timber.d("========================================")
        Timber.d("📊 TASK SYNC COMPLETE")
        Timber.d("   Downloaded: $tasksDownloaded tasks")
        Timber.d("   Uploaded: $tasksUploaded tasks")
        Timber.d("========================================")
        
        return tasksDownloaded to tasksUploaded
    }
    
    /**
     * Upload dirty tasks (local changes) to the server.
     * Returns the number of tasks successfully uploaded.
     */
    private suspend fun uploadDirtyTasks(
        account: Account,
        taskList: com.davy.domain.model.TaskList,
        password: String
    ): Int {
        val dirtyTasks = taskRepository.getDirtyTasks().filter { it.taskListId == taskList.id }
        Timber.d("Found ${dirtyTasks.size} dirty tasks to upload")
        
        var uploaded = 0
        
        for (task in dirtyTasks) {
            try {
                val success = uploadTask(task, account, taskList, password)
                if (success) {
                    uploaded++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload task: ${task.summary}")
            }
        }
        
        return uploaded
    }
    
    /**
     * Upload a single task to the server.
     */
    private suspend fun uploadTask(
        task: Task,
        account: Account,
        taskList: com.davy.domain.model.TaskList,
        password: String
    ): Boolean {
        Timber.d("Uploading task: ${task.summary}")
        
        try {
            // Convert task to iCalendar format
            val icalData = ICalendarParser.taskToICalendar(task)
            
            // Build task URL
            val isNewTask = task.url.isBlank()
            val taskUrl = if (isNewTask) {
                // New task - generate URL using UID
                val uid = if (task.uid.isNotBlank()) task.uid else UUID.randomUUID().toString()
                "${taskList.url.trimEnd('/')}/$uid.ics"
            } else {
                // Existing task - use stored URL
                if (task.url.startsWith("http")) {
                    task.url
                } else {
                    "${account.serverUrl}${task.url}"
                }
            }
            
            Timber.d("Task URL: $taskUrl")
            Timber.d("Task ETag: ${task.etag}")
            Timber.d("Is new task: $isNewTask")
            
            // PUT request to upload/update task
            var response = calDAVClient.putEvent(
                eventUrl = taskUrl,
                username = account.username,
                password = password,
                icalendarData = icalData,
                etag = if (isNewTask) null else task.etag
            )
            
            // If we get 412 Precondition Failed (ETag mismatch), retry without ETag
            // This will overwrite the server version with local changes
            if (response.statusCode == 412 && !isNewTask) {
                Timber.w("ETag mismatch (412), retrying without ETag to force update...")
                response = calDAVClient.putEvent(
                    eventUrl = taskUrl,
                    username = account.username,
                    password = password,
                    icalendarData = icalData,
                    etag = null // No If-Match header - will overwrite
                )
            }
            
            if (response.isSuccessful) {
                // Update task with server response (new etag, URL)
                val newEtag = response.headers["ETag"]?.firstOrNull() 
                    ?: response.headers["etag"]?.firstOrNull()
                
                val updatedTask = task.copy(
                    url = taskUrl,
                    etag = newEtag?.trim('"'),
                    dirty = false
                )
                taskRepository.update(updatedTask)
                
                Timber.d("✓ Task uploaded successfully: ${task.summary}")
                return true
            } else {
                Timber.e("Failed to upload task: ${response.statusCode} - ${response.error}")
                return false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception uploading task: ${task.summary}")
            return false
        }
    }
    
    /**
     * Delete tasks marked for deletion from the server.
     * Returns the number of tasks successfully deleted.
     */
    private suspend fun deleteMarkedTasks(
        account: Account,
        taskList: com.davy.domain.model.TaskList,
        password: String
    ): Int {
        val deletedTasks = taskRepository.getDeletedTasks().filter { it.taskListId == taskList.id }
        Timber.d("Found ${deletedTasks.size} tasks marked for deletion")
        
        var deleted = 0
        
        for (task in deletedTasks) {
            try {
                if (task.url.isBlank()) {
                    // Task was never uploaded - just remove locally
                    taskRepository.delete(task)
                    deleted++
                    continue
                }
                
                val taskUrl = if (task.url.startsWith("http")) {
                    task.url
                } else {
                    "${account.serverUrl}${task.url}"
                }
                
                val response = calDAVClient.deleteEvent(
                    eventUrl = taskUrl,
                    username = account.username,
                    password = password,
                    etag = task.etag
                )
                
                if (response.isSuccessful || response.statusCode == 404 || response.statusCode == 410) {
                    // Delete from local database
                    taskRepository.delete(task)
                    deleted++
                    Timber.d("✓ Deleted task: ${task.summary}")
                } else {
                    Timber.e("Failed to delete task from server: ${response.statusCode}")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete task: ${task.summary}")
            }
        }
        
        return deleted
    }
}

/**
 * Result of calendar sync operation.
 */
sealed class SyncResult {
    data class Success(
        val calendarsAdded: Int,
        val eventsDownloaded: Int,
        val eventsUploaded: Int
    ) : SyncResult()
    
    data class Error(val message: String) : SyncResult()
    
    companion object {
        fun success(calendarsAdded: Int, eventsDownloaded: Int, eventsUploaded: Int) =
            Success(calendarsAdded, eventsDownloaded, eventsUploaded)
        
        fun error(message: String) = Error(message)
    }
}

/**
 * Result of event sync for single calendar.
 */
data class EventSyncResult(
    val eventsDownloaded: Int,
    val eventsUploaded: Int
)
