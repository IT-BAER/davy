package com.davy.sync.webcal

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.davy.data.remote.caldav.ICalendarParser
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.WebCalSubscriptionRepository
import com.davy.domain.model.CalendarEvent
import com.davy.domain.model.EventStatus
import com.davy.domain.model.WebCalSubscription
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.TimeZone
import javax.inject.Inject

/**
 * Syncs WebCal subscriptions by fetching .ics files via HTTP.
 * 
 * WebCal subscriptions are read-only calendar feeds accessed via HTTP/HTTPS.
 * This service:
 * - Fetches .ics files from subscription URLs
 * - Parses iCalendar data
 * - Updates local event database
 * - Handles ETags for conditional requests
 * - Respects refresh intervals
 */
class WebCalSyncService @Inject constructor(
    private val webCalRepository: WebCalSubscriptionRepository,
    private val accountRepository: AccountRepository,
    private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ACCOUNT_TYPE = "com.davy"
        private const val CALENDAR_NAME_PREFIX = "webcal:"
    }
    
    /**
     * Sync a single WebCal subscription.
     * 
     * @param subscription The subscription to sync
     * @return Result with sync status
     */
    suspend fun syncSubscription(subscription: WebCalSubscription): WebCalSyncResult = withContext(Dispatchers.IO) {
        Timber.d("Starting WebCal sync for: ${subscription.displayName} (${subscription.subscriptionUrl})")
        
        try {
            // Build HTTP request
            val httpUrl = subscription.getHttpUrl()
            val requestBuilder = Request.Builder()
                .url(httpUrl)
                .get()
            
            // Add If-None-Match header if we have an ETag
            subscription.etag?.let { etag ->
                requestBuilder.header("If-None-Match", etag)
            }
            
            val request = requestBuilder.build()
            
            // Execute request
            val response = httpClient.newCall(request).execute()
            
            when (response.code) {
                304 -> {
                    // Not Modified - content hasn't changed
                    Timber.d("WebCal ${subscription.displayName}: Not modified (304)")
                    
                    // Check if calendar exists in provider, if not we need to fetch fresh data
                    // even though server says content unchanged (calendar was deleted locally)
                    val account = accountRepository.getById(subscription.accountId)
                    if (account != null) {
                        val permissionsGranted = hasCalendarPermissions()
                        if (permissionsGranted) {
                            // Check if calendar exists
                            val calendarName = "$CALENDAR_NAME_PREFIX${subscription.id}"
                            val calendarExists = context.contentResolver.query(
                                CalendarContract.Calendars.CONTENT_URI,
                                arrayOf(CalendarContract.Calendars._ID),
                                "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?",
                                arrayOf(account.accountName, ACCOUNT_TYPE, calendarName),
                                null
                            )?.use { it.count > 0 } ?: false
                            
                            if (!calendarExists) {
                                // Calendar was deleted, need to re-fetch data to populate events
                                Timber.d("WebCal ${subscription.displayName}: Calendar missing despite 304, forcing fresh fetch")
                                // Clear ETag to force fresh download next time
                                webCalRepository.updateSyncStatus(
                                    id = subscription.id,
                                    syncTime = System.currentTimeMillis(),
                                    etag = null,
                                    error = null
                                )
                                // Retry sync without If-None-Match header by recursing (will hit 200 branch)
                                return@withContext syncSubscription(subscription.copy(etag = null))
                            }
                        }
                    }
                    
                    // Calendar exists and content unchanged, just update sync time
                    webCalRepository.updateSyncStatus(
                        id = subscription.id,
                        syncTime = System.currentTimeMillis(),
                        etag = subscription.etag, // Keep existing ETag
                        error = null
                    )
                    
                    return@withContext WebCalSyncResult.NotModified(subscription.id)
                }
                
                200 -> {
                    // OK - got fresh content
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        val errorMsg = "Empty response body"
                        Timber.e("WebCal ${subscription.displayName}: $errorMsg")
                        
                        webCalRepository.updateSyncStatus(
                            id = subscription.id,
                            syncTime = System.currentTimeMillis(),
                            etag = null,
                            error = errorMsg
                        )
                        
                        return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
                    }
                    
                    val events = try {
                        parseICalendarToEvents(body, subscription)
                    } catch (e: Exception) {
                        val errorMsg = "Parse error: ${e.message}"
                        Timber.e(e, "WebCal ${subscription.displayName}: $errorMsg")
                        
                        webCalRepository.updateSyncStatus(
                            id = subscription.id,
                            syncTime = System.currentTimeMillis(),
                            etag = null,
                            error = errorMsg
                        )
                        
                        return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
                    }
                    
                    // Sync to Android Calendar provider
                    val account = accountRepository.getById(subscription.accountId)
                    if (account == null) {
                        val errorMsg = "Account ${subscription.accountId} not found for WebCal"
                        Timber.e("WebCal ${subscription.displayName}: $errorMsg")
                        webCalRepository.updateSyncStatus(
                            id = subscription.id,
                            syncTime = System.currentTimeMillis(),
                            etag = null,
                            error = errorMsg
                        )
                        return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
                    }

                    val permissionsGranted = hasCalendarPermissions()
                    if (!permissionsGranted) {
                        val errorMsg = "Calendar permissions not granted"
                        Timber.e("WebCal ${subscription.displayName}: $errorMsg")
                        webCalRepository.updateSyncStatus(
                            id = subscription.id,
                            syncTime = System.currentTimeMillis(),
                            etag = null,
                            error = errorMsg
                        )
                        return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
                    }

                    val androidCalendarId = ensureAndroidCalendar(account.accountName, subscription)
                    if (androidCalendarId == null) {
                        val errorMsg = "Failed to ensure Android calendar"
                        Timber.e("WebCal ${subscription.displayName}: $errorMsg")
                        webCalRepository.updateSyncStatus(
                            id = subscription.id,
                            syncTime = System.currentTimeMillis(),
                            etag = null,
                            error = errorMsg
                        )
                        return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
                    }

                    webCalRepository.updateAndroidCalendarId(subscription.id, androidCalendarId)

                    val eventsApplied = syncEventsToProvider(
                        accountName = account.accountName,
                        calendarId = androidCalendarId,
                        events = events
                    )

                    Timber.d("WebCal ${subscription.displayName}: Applied $eventsApplied events to CalendarProvider")
                    
                    // Get new ETag if present
                    val newEtag = response.header("ETag")
                    
                    // Update sync status
                    webCalRepository.updateSyncStatus(
                        id = subscription.id,
                        syncTime = System.currentTimeMillis(),
                        etag = newEtag,
                        error = null
                    )
                    
                    return@withContext WebCalSyncResult.Success(
                        subscriptionId = subscription.id,
                        eventsUpdated = eventsApplied
                    )
                }
                
                else -> {
                    // HTTP error
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    Timber.e("WebCal ${subscription.displayName}: $errorMsg")
                    
                    webCalRepository.updateSyncStatus(
                        id = subscription.id,
                        syncTime = System.currentTimeMillis(),
                        etag = null,
                        error = errorMsg
                    )
                    
                    return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
                }
            }
        } catch (e: IOException) {
            val errorMsg = "Network error: ${e.message}"
            Timber.e(e, "WebCal ${subscription.displayName}: $errorMsg")
            
            webCalRepository.updateSyncStatus(
                id = subscription.id,
                syncTime = System.currentTimeMillis(),
                etag = null,
                error = errorMsg
            )
            
            return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
        } catch (e: Exception) {
            val errorMsg = "Unexpected error: ${e.message}"
            Timber.e(e, "WebCal ${subscription.displayName}: $errorMsg")
            
            webCalRepository.updateSyncStatus(
                id = subscription.id,
                syncTime = System.currentTimeMillis(),
                etag = null,
                error = errorMsg
            )
            
            return@withContext WebCalSyncResult.Error(subscription.id, errorMsg)
        }
    }
    
    /**
     * Sync all subscriptions for an account that need refresh.
     * 
     * @param accountId The account ID
     * @return Map of subscription ID to sync result
     */
    suspend fun syncAccountSubscriptions(accountId: Long, force: Boolean = false): Map<Long, WebCalSyncResult> {
        val subscriptionsToSync = if (force) {
            webCalRepository.getSyncEnabledByAccountId(accountId)
        } else {
            webCalRepository.getAccountSubscriptionsNeedingRefresh(accountId)
        }
        
        Timber.d("Syncing ${subscriptionsToSync.size} WebCal subscriptions for account $accountId")
        
        // Sync all subscriptions in parallel using coroutines
        return coroutineScope {
            val syncJobs = subscriptionsToSync.map { subscription ->
                async {
                    subscription.id to syncSubscription(subscription)
                }
            }
            syncJobs.map { it.await() }.toMap()
        }
    }
    
    /**
     * Parse iCalendar string to list of CalendarEvent objects.
     * 
     * @param icalData The iCalendar data as string
     * @param subscription The WebCal subscription
     * @return List of parsed events
     */
    private fun parseICalendarToEvents(
        icalData: String,
        subscription: WebCalSubscription
    ): List<CalendarEvent> {
        val parsed = ICalendarParser.parseEvents(icalData)
        return parsed.map { event ->
            val uniqueUrl = "${subscription.subscriptionUrl}#${event.uid}"
            event.copy(
                calendarId = subscription.id,
                eventUrl = uniqueUrl
            )
        }
    }

    private fun hasCalendarPermissions(): Boolean {
        val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return writeGranted && readGranted
    }

    private suspend fun ensureAndroidCalendar(
        accountName: String,
        subscription: WebCalSubscription
    ): Long? {
        val resolver = context.contentResolver
        val calendarName = "$CALENDAR_NAME_PREFIX${subscription.id}"

        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?",
            arrayOf(accountName, ACCOUNT_TYPE, calendarName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val calendarId = cursor.getLong(0)
                if (subscription.androidCalendarId != calendarId) {
                    webCalRepository.updateAndroidCalendarId(subscription.id, calendarId)
                }

                val updateValues = ContentValues().apply {
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, subscription.displayName)
                    put(CalendarContract.Calendars.CALENDAR_COLOR, subscription.color)
                    put(CalendarContract.Calendars.SYNC_EVENTS, if (subscription.syncEnabled) 1 else 0)
                    put(CalendarContract.Calendars.VISIBLE, if (subscription.visible) 1 else 0)
                }

                val updateUri = asSyncAdapterUri(
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
                    accountName,
                    ACCOUNT_TYPE
                )
                resolver.update(updateUri, updateValues, null, null)

                return calendarId
            }
        }

        val insertUri = asSyncAdapterUri(
            CalendarContract.Calendars.CONTENT_URI,
            accountName,
            ACCOUNT_TYPE
        )

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, calendarName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, subscription.displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, subscription.color)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            put(CalendarContract.Calendars.SYNC_EVENTS, if (subscription.syncEnabled) 1 else 0)
            put(CalendarContract.Calendars.VISIBLE, if (subscription.visible) 1 else 0)
        }

        val resultUri = resolver.insert(insertUri, values)
        val calendarId = resultUri?.lastPathSegment?.toLongOrNull()
        if (calendarId != null) {
            webCalRepository.updateAndroidCalendarId(subscription.id, calendarId)
        }
        return calendarId
    }

    private fun syncEventsToProvider(
        accountName: String,
        calendarId: Long,
        events: List<CalendarEvent>
    ): Int {
        val resolver = context.contentResolver
        val existingEvents = mutableMapOf<String, Long>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.SYNC_DATA1),
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uidIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.SYNC_DATA1)
            while (cursor.moveToNext()) {
                val uid = cursor.getString(uidIndex)
                val id = cursor.getLong(idIndex)
                if (!uid.isNullOrBlank()) {
                    existingEvents[uid] = id
                }
            }
        }

        val appliedUids = mutableSetOf<String>()
        val eventInsertUri = asSyncAdapterUri(
            CalendarContract.Events.CONTENT_URI,
            accountName,
            ACCOUNT_TYPE
        )

        events.forEach { event ->
            val uid = event.uid.ifBlank { event.eventUrl }
            if (uid.isBlank()) return@forEach
            appliedUids += uid

            val start = event.dtStart
            val end = if (event.dtEnd > event.dtStart) event.dtEnd else event.dtStart + 60 * 60 * 1000
            val timezoneId = event.timezone ?: TimeZone.getDefault().id

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, timezoneId)
                put(CalendarContract.Events.EVENT_END_TIMEZONE, timezoneId)
                put(CalendarContract.Events.SYNC_DATA1, uid)
                put(CalendarContract.Events.SYNC_DATA2, event.eventUrl)
                put(CalendarContract.Events.DIRTY, 0)
                put(CalendarContract.Events.HAS_ALARM, 0)
                put(CalendarContract.Events.HAS_ATTENDEE_DATA, if (event.attendees.isNotEmpty()) 1 else 0)
                put(CalendarContract.Events.ORGANIZER, event.organizer)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                put(CalendarContract.Events.STATUS, when (event.status) {
                    EventStatus.TENTATIVE -> CalendarContract.Events.STATUS_TENTATIVE
                    EventStatus.CANCELLED -> CalendarContract.Events.STATUS_CANCELED
                    else -> CalendarContract.Events.STATUS_CONFIRMED
                })
            }

            val existingId = existingEvents[uid]
            if (existingId != null) {
                val updateUri = asSyncAdapterUri(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId),
                    accountName,
                    ACCOUNT_TYPE
                )
                resolver.update(updateUri, values, null, null)
            } else {
                resolver.insert(eventInsertUri, values)?.let { inserted ->
                    val newId = ContentUris.parseId(inserted)
                    existingEvents[uid] = newId
                }
            }
        }

        // Remove stale events that are no longer present
        existingEvents.filterKeys { it !in appliedUids }.values.forEach { eventId ->
            val deleteUri = asSyncAdapterUri(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                accountName,
                ACCOUNT_TYPE
            )
            resolver.delete(deleteUri, null, null)
        }

        return appliedUids.size
    }

    private fun asSyncAdapterUri(baseUri: Uri, accountName: String, accountType: String): Uri {
        return baseUri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
            .build()
    }
}

/**
 * Result of a WebCal sync operation.
 */
sealed class WebCalSyncResult {
    /**
     * Sync completed successfully with new data.
     */
    data class Success(
        val subscriptionId: Long,
        val eventsUpdated: Int
    ) : WebCalSyncResult()
    
    /**
     * Subscription hasn't changed (HTTP 304).
     */
    data class NotModified(
        val subscriptionId: Long
    ) : WebCalSyncResult()
    
    /**
     * Sync failed with error.
     */
    data class Error(
        val subscriptionId: Long,
        val errorMessage: String
    ) : WebCalSyncResult()
}
