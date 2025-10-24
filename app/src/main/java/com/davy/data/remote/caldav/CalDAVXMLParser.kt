package com.davy.data.remote.caldav

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.StringReader

/**
 * Parser for CalDAV XML responses (PROPFIND, REPORT).
 * Handles WebDAV multistatus responses and extracts calendar properties.
 */
object CalDAVXMLParser {
    
    private const val TAG_MULTISTATUS = "multistatus"
    private const val TAG_RESPONSE = "response"
    private const val TAG_HREF = "href"
    private const val TAG_PROPSTAT = "propstat"
    private const val TAG_PROP = "prop"
    private const val TAG_STATUS = "status"
    private const val TAG_DISPLAYNAME = "displayname"
    private const val TAG_RESOURCETYPE = "resourcetype"
    private const val TAG_CALENDAR = "calendar"
    private const val TAG_COLLECTION = "collection"
    private const val TAG_GETETAG = "getetag"
    private const val TAG_GETCONTENTTYPE = "getcontenttype"
    private const val TAG_CALENDAR_DATA = "calendar-data"
    private const val TAG_CALENDAR_COLOR = "calendar-color"
    private const val TAG_CALENDAR_DESCRIPTION = "calendar-description"
    private const val TAG_SYNC_TOKEN = "sync-token"
    private const val TAG_GETCTAG = "getctag"
    private const val TAG_SUPPORTED_CALENDAR_COMPONENT_SET = "supported-calendar-component-set"
    private const val TAG_COMP = "comp"
    
    /**
     * Parse PROPFIND response for calendar discovery.
     * Returns list of calendar resources with their properties.
     */
    fun parseCalendarPropfind(xmlResponse: String): List<CalendarResource> {
        val calendars = mutableListOf<CalendarResource>()
        
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlResponse))
            
            var eventType = parser.eventType
            var currentCalendar: CalendarResource? = null
            var inProp = false
            var inPropstat = false
            // STATUS tag is currently ignored; we only register calendar collections
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            TAG_RESPONSE -> {
                                currentCalendar = CalendarResource()
                            }
                            TAG_HREF -> {
                                if (currentCalendar != null && !inPropstat) {
                                    currentCalendar.href = readText(parser)
                                }
                            }
                            TAG_PROPSTAT -> {
                                inPropstat = true
                            }
                            TAG_PROP -> {
                                inProp = true
                            }
                            TAG_STATUS -> {
                                if (inPropstat) {
                                    readText(parser)
                                }
                            }
                            TAG_DISPLAYNAME -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.displayName = readText(parser)
                                }
                            }
                            TAG_RESOURCETYPE -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.isCalendar = parseResourceType(parser)
                                }
                            }
                            TAG_GETETAG -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.etag = readText(parser)
                                }
                            }
                            TAG_CALENDAR_COLOR -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.color = readText(parser)
                                }
                            }
                            TAG_CALENDAR_DESCRIPTION -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.description = readText(parser)
                                }
                            }
                            TAG_SYNC_TOKEN -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.syncToken = readText(parser)
                                }
                            }
                            TAG_GETCTAG -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.ctag = readText(parser)
                                }
                            }
                            TAG_SUPPORTED_CALENDAR_COMPONENT_SET -> {
                                if (inProp && currentCalendar != null) {
                                    currentCalendar.supportedComponents = parseSupportedComponents(parser)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.lowercase()) {
                            TAG_RESPONSE -> {
                                if (currentCalendar != null && currentCalendar.isCalendar) {
                                    calendars.add(currentCalendar)
                                }
                                currentCalendar = null
                            }
                            TAG_PROPSTAT -> {
                                inPropstat = false
                            }
                            TAG_PROP -> {
                                inProp = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse calendar PROPFIND response")
        }
        
        return calendars
    }
    
    /**
     * Parse PROPFIND response for event discovery.
     * Returns list of event resources with their ETags.
     */
    fun parseEventPropfind(xmlResponse: String): List<EventResource> {
        val events = mutableListOf<EventResource>()
        
        try {
            Timber.d("parseEventPropfind: Parsing XML response (${xmlResponse.length} chars)")
            
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlResponse))
            
            var eventType = parser.eventType
            var currentEvent: EventResource? = null
            var inProp = false
            
            var tagCount = 0
            var responseCount = 0
            while (eventType != XmlPullParser.END_DOCUMENT) {
                tagCount++
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        // Strip namespace prefix (e.g., "d:response" -> "response")
                        val tagName = parser.name.substringAfter(':').lowercase()
                        if (tagCount < 15) {  // Only log first few tags
                            Timber.d("parseEventPropfind: Tag #$tagCount: ${parser.name} -> $tagName")
                        }
                        when (tagName) {
                            TAG_RESPONSE -> {
                                responseCount++
                                Timber.d("parseEventPropfind: Found TAG_RESPONSE #$responseCount")
                                currentEvent = EventResource()
                            }
                            TAG_HREF -> {
                                if (currentEvent != null) {
                                    currentEvent.href = readText(parser)
                                }
                            }
                            TAG_PROP -> {
                                inProp = true
                            }
                            TAG_GETETAG -> {
                                if (inProp && currentEvent != null) {
                                    currentEvent.etag = readText(parser)
                                }
                            }
                            TAG_GETCONTENTTYPE -> {
                                if (inProp && currentEvent != null) {
                                    currentEvent.contentType = readText(parser)
                                }
                            }
                            TAG_CALENDAR_DATA -> {
                                if (inProp && currentEvent != null) {
                                    currentEvent.calendarData = readText(parser)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        // Strip namespace prefix
                        val tagName = parser.name.substringAfter(':').lowercase()
                        when (tagName) {
                            TAG_RESPONSE -> {
                                if (currentEvent != null) {
                                    Timber.d("Parsed event response: href=${currentEvent.href}, etag=${currentEvent.etag}")
                                    // Don't filter by .ics extension - some servers (like Google Calendar imports)
                                    // use different naming patterns (hex strings without extension)
                                    events.add(currentEvent)
                                }
                                currentEvent = null
                            }
                            TAG_PROP -> {
                                inProp = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            Timber.d("parseEventPropfind: Processed $tagCount tags, found ${events.size} events")
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse event PROPFIND response")
        }
        
        return events
    }
    
    /**
     * Parse REPORT sync-collection response.
     * Returns list of changes and new sync-token.
     */
    fun parseSyncCollection(xmlResponse: String): SyncCollectionResponse {
        val changes = mutableListOf<SyncChange>()
        var newSyncToken: String? = null
        
        try {
            Timber.d("parseSyncCollection: Parsing XML response (${xmlResponse.length} chars)")
            
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlResponse))
            
            var eventType = parser.eventType
            var currentHref: String? = null
            var currentEtag: String? = null
            var currentStatus: Int? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name.substringAfter(':').lowercase()
                        when (tagName) {
                            "response" -> {
                                // Reset for new response
                                currentHref = null
                                currentEtag = null
                                currentStatus = null
                            }
                            "href" -> {
                                currentHref = readText(parser)
                            }
                            "getetag" -> {
                                currentEtag = readText(parser)
                            }
                            "status" -> {
                                val statusText = readText(parser)
                                // Parse "HTTP/1.1 200 OK" -> 200
                                currentStatus = statusText.split(" ").getOrNull(1)?.toIntOrNull()
                            }
                            "sync-token" -> {
                                newSyncToken = readText(parser)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name.substringAfter(':').lowercase()
                        if (tagName == "response" && currentHref != null && currentStatus != null) {
                            changes.add(SyncChange(
                                href = currentHref,
                                etag = currentEtag,
                                status = currentStatus
                            ))
                            Timber.d("📊 Sync change: $currentHref (status=$currentStatus, etag=$currentEtag)")
                        }
                    }
                }
                eventType = parser.next()
            }
            
            Timber.d("✓ parseSyncCollection: Found ${changes.size} changes, new token: $newSyncToken")
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse sync-collection response")
        }
        
        return SyncCollectionResponse(changes, newSyncToken)
    }
    
    /**
     * Parse resourcetype element to check if it's a calendar.
     */
    private fun parseResourceType(parser: XmlPullParser): Boolean {
        var isCalendar = false
        val depth = parser.depth
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    TAG_CALENDAR -> isCalendar = true
                }
            }
            
            eventType = parser.next()
        }
        
        return isCalendar
    }
    
    /**
     * Parse supported-calendar-component-set to get supported component types.
     */
    private fun parseSupportedComponents(parser: XmlPullParser): List<String> {
        val components = mutableListOf<String>()
        val depth = parser.depth
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG && parser.name.lowercase() == TAG_COMP) {
                val name = parser.getAttributeValue(null, "name")
                if (name != null) {
                    components.add(name)
                }
            }
            
            eventType = parser.next()
        }
        
        return components
    }
    
    /**
     * Read text content from current parser position.
     */
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result.trim()
    }
}

/**
 * Represents a calendar resource from PROPFIND response.
 */
data class CalendarResource(
    var href: String = "",
    var displayName: String = "",
    var color: String? = null,
    var description: String? = null,
    var syncToken: String? = null,
    var ctag: String? = null,
    var etag: String? = null,
    var isCalendar: Boolean = false,
    var supportedComponents: List<String> = emptyList()
)

/**
 * Represents an event resource from PROPFIND/REPORT response.
 */
data class EventResource(
    var href: String = "",
    var etag: String = "",
    var contentType: String? = null,
    var calendarData: String? = null
)

/**
 * Represents a sync-collection response.
 */
data class SyncCollectionResponse(
    val changes: List<SyncChange>,
    val newSyncToken: String?
)

/**
 * Represents a single change in sync-collection response.
 */
data class SyncChange(
    val href: String,
    val etag: String?,
    val status: Int  // 200=modified, 201=added, 404=deleted
)
