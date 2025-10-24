package com.davy.data.remote.caldav

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalDAV HTTP client for calendar synchronization.
 * Implements CalDAV protocol methods:
 * - PROPFIND: Discover calendars and events
 * - REPORT: Query calendar data
 * - GET: Fetch individual calendar events
 * - PUT: Create/update events
 * - DELETE: Remove events
 */
@Singleton
class CalDAVClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    
    companion object {
        private const val CALDAV_DEPTH_0 = "0"
        private const val CALDAV_DEPTH_1 = "1"
        private const val CALDAV_DEPTH_INFINITY = "infinity"
        
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val ICALENDAR_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
    }
    
    /**
     * PROPFIND request to discover calendars at given URL.
     * Returns XML response containing calendar properties.
     */
    fun propfindCalendars(
        url: String,
        username: String,
        password: String,
        depth: String = CALDAV_DEPTH_1
    ): CalDAVResponse {
        val requestBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <d:resourcetype/>
                    <d:displayname/>
                    <d:current-user-privilege-set/>
                    <c:calendar-description/>
                    <c:calendar-color/>
                    <c:calendar-timezone/>
                    <c:supported-calendar-component-set/>
                    <c:calendar-home-set/>
                    <d:sync-token/>
                    <c:getctag/>
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return executePropfind(url, username, password, depth, requestBody)
    }
    
    /**
     * PROPFIND request to discover calendar events.
     * Returns XML response containing event ETags and URLs.
     */
    fun propfindEvents(
        calendarUrl: String,
        username: String,
        password: String
    ): CalDAVResponse {
        val requestBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <d:getetag/>
                    <d:getcontenttype/>
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return executePropfind(calendarUrl, username, password, CALDAV_DEPTH_1, requestBody)
    }
    
    /**
     * REPORT calendar-query request for efficient event fetching.
     * Can filter by time range and event types.
     */
    fun reportCalendarQuery(
        calendarUrl: String,
        username: String,
        password: String,
        timeRangeStart: String? = null,
        timeRangeEnd: String? = null
    ): CalDAVResponse {
        val timeRangeFilter = if (timeRangeStart != null && timeRangeEnd != null) {
            """
            <c:time-range start="$timeRangeStart" end="$timeRangeEnd"/>
            """.trimIndent()
        } else {
            ""
        }
        
        val requestBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <d:getetag/>
                    <c:calendar-data/>
                </d:prop>
                <c:filter>
                    <c:comp-filter name="VCALENDAR">
                        <c:comp-filter name="VEVENT">
                            $timeRangeFilter
                        </c:comp-filter>
                    </c:comp-filter>
                </c:filter>
            </c:calendar-query>
        """.trimIndent()
        
        return executeReport(calendarUrl, username, password, requestBody)
    }
    
    /**
    * REPORT calendar-multiget request to fetch multiple events at once.
    * This is significantly more efficient than individual GET requests.
    * Batch up to MAX_MULTIGET_RESOURCES events per request.
     * 
     * @param calendarUrl Base calendar collection URL
     * @param eventHrefs List of event resource paths (relative or absolute)
     * @param username CalDAV username
     * @param password CalDAV password
     * @return CalDAVResponse containing calendar-data for all requested events
     */
    fun multigetEvents(
        calendarUrl: String,
        eventHrefs: List<String>,
        username: String,
        password: String
    ): CalDAVResponse {
        val hrefElements = eventHrefs.joinToString("\n") { href ->
            "    <d:href>$href</d:href>"
        }
        
        val requestBody = """<?xml version="1.0" encoding="UTF-8"?>
<c:calendar-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
    <d:prop>
        <d:getetag/>
        <c:calendar-data/>
    </d:prop>
$hrefElements
</c:calendar-multiget>"""
        
        return executeReport(calendarUrl, username, password, requestBody)
    }
    
    /**
     * REPORT request with custom body (for sync-collection, etc).
     */
    fun reportRequest(
        url: String,
        username: String,
        password: String,
        requestBody: String
    ): CalDAVResponse {
        return executeReport(url, username, password, requestBody)
    }
    
    /**
     * GET request to fetch individual calendar event (.ics file).
     */
    fun getEvent(
        eventUrl: String,
        username: String,
        password: String,
        etag: String? = null
    ): CalDAVResponse {
        val requestBuilder = Request.Builder()
            .url(eventUrl)
            .get()
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
        
        // Add If-None-Match header for conditional GET
        if (etag != null) {
            requestBuilder.addHeader("If-None-Match", etag)
        }
        
        val request = requestBuilder.build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                isSuccessful = response.isSuccessful
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to GET event: $eventUrl")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
    
    /**
     * PUT request to create or update calendar event.
     */
    fun putEvent(
        eventUrl: String,
        username: String,
        password: String,
        icalendarData: String,
        etag: String? = null
    ): CalDAVResponse {
        val requestBody = icalendarData.toRequestBody(ICALENDAR_MEDIA_TYPE)
        
        val requestBuilder = Request.Builder()
            .url(eventUrl)
            .put(requestBody)
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .addHeader("Content-Type", "text/calendar; charset=utf-8")
        
        // Add If-Match header for conditional PUT (optimistic locking)
        if (etag != null) {
            requestBuilder.addHeader("If-Match", etag)
        }
        
        val request = requestBuilder.build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                isSuccessful = response.isSuccessful || response.code == 201 || response.code == 204
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to PUT event: $eventUrl")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
    
    /**
     * DELETE request to remove calendar event.
     */
    fun deleteEvent(
        eventUrl: String,
        username: String,
        password: String,
        etag: String? = null
    ): CalDAVResponse {
        val requestBuilder = Request.Builder()
            .url(eventUrl)
            .delete()
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
        
        // Add If-Match header for conditional DELETE
        if (etag != null) {
            requestBuilder.addHeader("If-Match", etag)
        }
        
        val request = requestBuilder.build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                isSuccessful = response.isSuccessful || response.code == 204
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to DELETE event: $eventUrl")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
    
    /**
     * MKCALENDAR request to create a new calendar collection on the server.
     * @param calendarUrl The URL where the calendar should be created (must end with /)
     * @param username Username for authentication
     * @param password Password for authentication
     * @param displayName Calendar display name
     * @param description Optional calendar description
     * @param color Optional calendar color in ARGB format (0xAARRGGBB)
     * @return CalDAVResponse with success/failure status
     */
    fun mkCalendar(
        calendarUrl: String,
        username: String,
        password: String,
        displayName: String,
        description: String? = null,
        color: Int? = null
    ): CalDAVResponse {
        val colorHex = color?.let {
            // Convert ARGB to CalDAV color format (#RRGGBBAA)
            val alpha = (it shr 24) and 0xFF
            val red = (it shr 16) and 0xFF
            val green = (it shr 8) and 0xFF
            val blue = it and 0xFF
            "#%02X%02X%02X%02X".format(red, green, blue, alpha)
        }
        
        val descriptionXml = description?.let {
            """<c:calendar-description>$it</c:calendar-description>"""
        } ?: ""
        
        val colorXml = colorHex?.let {
            """<x1:calendar-color xmlns:x1="http://apple.com/ns/ical/">$it</x1:calendar-color>"""
        } ?: ""
        
        val requestBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:mkcalendar xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:set>
                    <d:prop>
                        <d:resourcetype>
                            <d:collection/>
                            <c:calendar/>
                        </d:resourcetype>
                        <d:displayname>$displayName</d:displayname>
                        $descriptionXml
                        $colorXml
                        <c:supported-calendar-component-set>
                            <c:comp name="VEVENT"/>
                            <c:comp name="VTODO"/>
                        </c:supported-calendar-component-set>
                    </d:prop>
                </d:set>
            </c:mkcalendar>
        """.trimIndent()
        
        val requestBodyObj = requestBody.toRequestBody(XML_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url(calendarUrl)
            .method("MKCALENDAR", requestBodyObj)
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .addHeader("Content-Type", "application/xml; charset=utf-8")
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                isSuccessful = response.isSuccessful || response.code == 201
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to MKCALENDAR: $calendarUrl")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
    
    /**
     * DELETE request to remove an entire calendar collection from the server.
     * @param calendarUrl The URL of the calendar to delete
     * @param username Username for authentication
     * @param password Password for authentication
     * @return CalDAVResponse with success/failure status
     */
    fun deleteCalendar(
        calendarUrl: String,
        username: String,
        password: String
    ): CalDAVResponse {
        val request = Request.Builder()
            .url(calendarUrl)
            .delete()
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                isSuccessful = response.isSuccessful || response.code == 204 || response.code == 404 || response.code == 410
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to DELETE calendar: $calendarUrl")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
    
    /**
     * PROPPATCH request to update properties like displayname.
     * Used for renaming calendars and addressbooks.
     */
    fun proppatchCollection(
        collectionUrl: String,
        username: String,
        password: String,
        propertyName: String,
        propertyValue: String,
        namespace: String = "DAV:"
    ): CalDAVResponse {
        val requestBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propertyupdate xmlns:d="$namespace">
                <d:set>
                    <d:prop>
                        <d:$propertyName>$propertyValue</d:$propertyName>
                    </d:prop>
                </d:set>
            </d:propertyupdate>
        """.trimIndent()
        
        val request = Request.Builder()
            .url(collectionUrl)
            .method("PROPPATCH", requestBody.toRequestBody(XML_MEDIA_TYPE))
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                isSuccessful = response.isSuccessful
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to PROPPATCH collection: $collectionUrl")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
    
    /**
     * Execute PROPFIND request with given XML body.
     */
    private fun executePropfind(
        url: String,
        username: String,
        password: String,
        depth: String,
        xmlBody: String
    ): CalDAVResponse {
        val requestBody = xmlBody.toRequestBody(XML_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", requestBody)
            .addHeader("Depth", depth)
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .addHeader("Content-Type", "application/xml; charset=utf-8")
            .build()
        
        return executeRequest(request)
    }
    
    /**
     * Execute REPORT request with given XML body.
     */
    private fun executeReport(
        url: String,
        username: String,
        password: String,
        xmlBody: String
    ): CalDAVResponse {
        val requestBody = xmlBody.toRequestBody(XML_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url(url)
            .method("REPORT", requestBody)
            .addHeader("Depth", CALDAV_DEPTH_1)
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .addHeader("Content-Type", "application/xml; charset=utf-8")
            .build()
        
        return executeRequest(request)
    }
    
    /**
     * Execute HTTP request and return CalDAVResponse.
     */
    private fun executeRequest(request: Request): CalDAVResponse {
        return try {
            val response = okHttpClient.newCall(request).execute()
            CalDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                // WebDAV often uses 207 Multi-Status for PROPFIND/REPORT; treat it as success
                isSuccessful = response.isSuccessful || response.code == 207
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to execute request: ${request.method} ${request.url}")
            CalDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
}

/**
 * CalDAV HTTP response.
 */
data class CalDAVResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, List<String>>,
    val isSuccessful: Boolean,
    val error: String? = null
)
