package com.davy.data.remote.caldav

import android.graphics.Color
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarParserFactory
import net.fortuna.ical4j.data.ContentHandlerContext
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.*
import timber.log.Timber
import java.io.StringReader
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

/**
 * Parser for iCalendar (.ics) data using ical4j library.
 * Converts between iCalendar format and domain models.
 * 
 * Implementation based on robust, compatibility-focused approaches:
 * - Pre-processes iCalendar data to fix common issues (empty ATTENDEE emails, etc.)
 * - Suppresses invalid properties to continue parsing
 * - Handles individual event parsing failures gracefully
 * - Logs errors for debugging without stopping sync
 */
object ICalendarParser {
    
    /**
     * Pre-process iCalendar data to fix common issues before parsing.
    * Based on a pre-processing stream approach.
     * 
     * Fixes:
     * 1. Line unfolding issues (parameters split across lines)
     * 2. Empty or invalid email addresses in ATTENDEE/ORGANIZER parameters
     * 3. Malformed LOCATION properties with encoding issues
     */
    private fun preprocessICalendar(icalendarData: String): String {
        var fixed = icalendarData
        
        // Fix 0: Properly unfold lines first (RFC 5545 section 3.1)
        // Lines are folded by inserting CRLF + SPACE/TAB
        // We need to unfold them before processing parameters
        // Example: "EMAIL=lagerh\r\n aus.at" → "EMAIL=lagerhaus.at"
        fixed = fixed.replace(Regex("\\r\\n[ \\t]"), "")
        fixed = fixed.replace(Regex("\\n[ \\t]"), "")
        
        // Fix 1: Remove ATTENDEE/ORGANIZER parameters with empty EMAIL values
        // Example: "ATTENDEE;CN=Name;PARTSTAT=NEEDS-ACTION;EMAIL=:mailto:user@example.com"
        // → "ATTENDEE;CN=Name;PARTSTAT=NEEDS-ACTION:mailto:user@example.com"
        fixed = fixed.replace(Regex(";EMAIL=:", RegexOption.IGNORE_CASE), ":")
        
        // Fix 2: Remove standalone EMAIL parameters without value
        // Example: "ATTENDEE;EMAIL=;CN=Name:mailto:user@example.com"
        // → "ATTENDEE;CN=Name:mailto:user@example.com"
        fixed = fixed.replace(Regex(";EMAIL=;", RegexOption.IGNORE_CASE), ";")
        
        // Fix 3: Remove EMAIL parameter if it's at the start before other params
        // Example: "ATTENDEE;EMAIL=:mailto:user@example.com"
        fixed = fixed.replace(Regex("ATTENDEE;EMAIL=:", RegexOption.IGNORE_CASE), "ATTENDEE:")
        fixed = fixed.replace(Regex("ORGANIZER;EMAIL=:", RegexOption.IGNORE_CASE), "ORGANIZER:")
        
        // Fix 4: Remove EMAIL= with whitespace or at line end
        // Example: "ATTENDEE;EMAIL= :mailto:..." → "ATTENDEE:mailto:..."
        fixed = fixed.replace(Regex(";EMAIL=\\s*(?=[;:]|$)", RegexOption.IGNORE_CASE), "")
        
        // Log if we made any fixes
        if (fixed != icalendarData) {
            Timber.d("ICalendarParser: Pre-processed iCalendar (unfolded lines and fixed EMAIL parameters)")
        }
        
        return fixed
    }
    
    /**
     * Parse iCalendar string to extract events.
     * Returns list of CalendarEvent domain objects.
     * 
    * Uses lenient parsing to handle Google Calendar and other non-standard formats.
     */
    fun parseEvents(icalendarData: String): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        try {
            // Guard: empty or whitespace-only payloads are common when a feed is disabled,
            // a server returns 204/empty, or calendar-data is omitted. Treat as no events.
            if (icalendarData.isBlank()) {
                Timber.i("ICalendarParser: Empty iCalendar payload, returning 0 events")
                return emptyList()
            }
            Timber.d("ICalendarParser: Building calendar from data (${icalendarData.length} chars)")
            
            // Pre-process to fix known issues (like Google Calendar's invalid ATTENDEE parameters)
            val preprocessed = preprocessICalendar(icalendarData)
            if (preprocessed.isBlank()) {
                Timber.i("ICalendarParser: Empty iCalendar payload after preprocessing, returning 0 events")
                return emptyList()
            }
            
            // Use CalendarBuilder with suppressInvalidProperties
            // This continues parsing even when individual properties are malformed
            val builder = CalendarBuilder(
                CalendarParserFactory.getInstance().get(),
                // KEY: Suppress invalid properties instead of throwing exceptions
                ContentHandlerContext().withSupressInvalidProperties(true),
                TimeZoneRegistryFactory.getInstance().createRegistry()
            )
            
            val calendar = builder.build(StringReader(preprocessed))
            
            Timber.d("ICalendarParser: Calendar built, getting VEVENT components")
            val vEvents = calendar.getComponents<VEvent>("VEVENT")
            
            Timber.d("ICalendarParser: Found ${vEvents.size} VEVENT components")
            vEvents.forEach { vEvent ->
                try {
                    Timber.d("ICalendarParser: Parsing VEVENT with UID=${vEvent.uid?.value}")
                    val event = parseVEvent(vEvent)
                    events.add(event)
                    Timber.d("ICalendarParser: Successfully parsed event: ${event.title}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse individual VEVENT, skipping (will continue with others)")
                    // Log the error but continue with other events
                }
            }
        } catch (e: Exception) {
            // Downgrade severity for common EOF cases (often caused by empty/truncated inputs)
            val msg = e.message ?: ""
            val isEof = msg.contains("Unexpected end of file", ignoreCase = true)
            if (isEof) {
                Timber.w(e, "ICalendar parse aborted due to unexpected EOF. Treating as 0 events")
            } else {
                Timber.e(e, "Failed to parse iCalendar data - event is malformed and incompatible with RFC 5545")
            }
            
            // Try to extract event summary/UID from raw data for debugging
            val summaryMatch = Regex("SUMMARY:(.+?)\\r?\\n").find(icalendarData)
            val uidMatch = Regex("UID:(.+?)\\r?\\n").find(icalendarData)
            val eventInfo = buildString {
                uidMatch?.groupValues?.get(1)?.let { append("UID=$it ") }
                summaryMatch?.groupValues?.get(1)?.let { append("SUMMARY='$it'") }
            }
            if (eventInfo.isNotEmpty()) {
                Timber.w("Skipping incompatible event: $eventInfo")
            }
            
            // Log error type for analysis
            when {
                e.message?.contains("Expected [VEVENT], read [VCALENDAR]") == true ->
                    Timber.w("Issue: Nested VCALENDAR objects (non-standard format)")
                e.message?.contains("Expected [-3], read [10]") == true ->
                    Timber.w("Issue: Malformed line folding in property parameters")
                e.message?.contains("Invalid address") == true ->
                    Timber.w("Issue: Invalid EMAIL parameter (should have been pre-processed)")
                else ->
                    Timber.w("Issue: ${e.message}")
            }
        }
        
        Timber.d("ICalendarParser: Returning ${events.size} events")
        return events
    }
    
    /**
     * Parse single VEVENT component to CalendarEvent.
     */
    private fun parseVEvent(vEvent: VEvent): CalendarEvent {
        val uid = vEvent.uid?.value ?: UUID.randomUUID().toString()
        val summary = vEvent.summary?.value ?: ""
        val description = vEvent.description?.value
        val location = vEvent.location?.value
        
        // Parse start and end times
        val dtStart = vEvent.startDate?.date
        val dtEnd = vEvent.endDate?.date
        
        val startMillis = dtStart?.time ?: 0L
        val endMillis = dtEnd?.time ?: startMillis
        
        // Check if all-day event
        val allDay = vEvent.startDate?.value?.contains('T') == false
        
        // Parse recurrence rule
        val rrule = vEvent.getProperty<RRule>("RRULE")?.value
        
        // Parse status - convert from ical4j Status to EventStatus enum
        val status = when (vEvent.status?.value) {
            Status.VALUE_TENTATIVE -> com.davy.domain.model.EventStatus.TENTATIVE
            Status.VALUE_CONFIRMED -> com.davy.domain.model.EventStatus.CONFIRMED
            Status.VALUE_CANCELLED -> com.davy.domain.model.EventStatus.CANCELLED
            else -> com.davy.domain.model.EventStatus.CONFIRMED
        }
        
        // Parse organizer
        val organizer = vEvent.organizer?.value
        
        // Parse attendees - convert from ical4j Attendee to domain Attendee
        val attendees = vEvent.getProperties<net.fortuna.ical4j.model.property.Attendee>("ATTENDEE")
            ?.mapNotNull { ical4jAttendee ->
                try {
                    // Extract email from attendee (format: "mailto:email@example.com")
                    val email = ical4jAttendee.value.removePrefix("mailto:")
                    val name = ical4jAttendee.getParameter<net.fortuna.ical4j.model.parameter.Cn>("CN")?.value
                    com.davy.domain.model.Attendee(
                        email = email,
                        name = name
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse attendee: ${ical4jAttendee.value}")
                    null
                }
            }
            ?: emptyList()
        
        return CalendarEvent(
            id = 0, // Will be set by database
            calendarId = 0, // Will be set when assigning to calendar
            eventUrl = "", // Will be set from calendar URL
            uid = uid,
            title = summary,
            description = description,
            location = location,
            dtStart = startMillis,
            dtEnd = endMillis,
            allDay = allDay,
            timezone = vEvent.startDate?.timeZone?.id,
            status = status,
            rrule = rrule,
            organizer = organizer,
            attendees = attendees,
            etag = null, // Will be set from HTTP response
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Parse calendar color from various formats.
     * Supports: #RRGGBB, #AARRGGBB, rgb(r,g,b), rgba(r,g,b,a)
     */
    private fun parseColor(colorString: String?): Int? {
        if (colorString == null) return null
        
        return try {
            when {
                colorString.startsWith("#") -> {
                    Color.parseColor(colorString)
                }
                colorString.startsWith("rgb") -> {
                    // Parse rgb(r, g, b) or rgba(r, g, b, a)
                    val values = colorString
                        .substringAfter("(")
                        .substringBefore(")")
                        .split(",")
                        .map { it.trim().toInt() }
                    
                    if (values.size >= 3) {
                        val r = values[0]
                        val g = values[1]
                        val b = values[2]
                        val a = if (values.size >= 4) values[3] else 255
                        Color.argb(a, r, g, b)
                    } else {
                        null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.w("Failed to parse color: $colorString")
            null
        }
    }
    
    /**
     * Convert CalendarEvent to iCalendar VEvent component.
     * Returns iCalendar string representation.
     */
    fun eventToICalendar(event: CalendarEvent): String {
        try {
            val calendar = net.fortuna.ical4j.model.Calendar()
            calendar.properties.add(ProdId("-//DAVy//CalDAV Client 1.0//EN"))
            calendar.properties.add(Version.VERSION_2_0)
            
            val vEvent = VEvent()
            
            // UID
            vEvent.properties.add(Uid(event.uid))
            
            // Summary (title)
            if (event.title.isNotEmpty()) {
                vEvent.properties.add(Summary(event.title))
            }
            
            // Description
            if (!event.description.isNullOrEmpty()) {
                vEvent.properties.add(Description(event.description))
            }
            
            // Location
            if (!event.location.isNullOrEmpty()) {
                vEvent.properties.add(Location(event.location))
            }
            
            // Start time
            val startDate = if (event.allDay) {
                DtStart(net.fortuna.ical4j.model.Date(event.dtStart))
            } else {
                val zonedStart = ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(event.dtStart),
                    ZoneId.of(event.timezone ?: "UTC")
                )
                DtStart(net.fortuna.ical4j.model.DateTime(Date.from(zonedStart.toInstant())))
            }
            vEvent.properties.add(startDate)
            
            // End time
            val endDate = if (event.allDay) {
                DtEnd(net.fortuna.ical4j.model.Date(event.dtEnd))
            } else {
                val zonedEnd = ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(event.dtEnd),
                    ZoneId.of(event.timezone ?: "UTC")
                )
                DtEnd(net.fortuna.ical4j.model.DateTime(Date.from(zonedEnd.toInstant())))
            }
            vEvent.properties.add(endDate)
            
            // Recurrence rule
            if (!event.rrule.isNullOrEmpty()) {
                vEvent.properties.add(RRule(event.rrule))
            }
            
            // Status - convert EventStatus enum to ical4j Status
            val statusValue = when (event.status) {
                com.davy.domain.model.EventStatus.TENTATIVE -> Status.VALUE_TENTATIVE
                com.davy.domain.model.EventStatus.CONFIRMED -> Status.VALUE_CONFIRMED
                com.davy.domain.model.EventStatus.CANCELLED -> Status.VALUE_CANCELLED
                null -> Status.VALUE_CONFIRMED
            }
            vEvent.properties.add(Status(statusValue))
            
            // Organizer
            if (!event.organizer.isNullOrEmpty()) {
                vEvent.properties.add(Organizer(event.organizer))
            }
            
            // Attendees - convert domain Attendee to ical4j Attendee
            event.attendees.forEach { domainAttendee ->
                val attendeeProperty = net.fortuna.ical4j.model.property.Attendee("mailto:${domainAttendee.email}")
                if (domainAttendee.name != null) {
                    attendeeProperty.parameters.add(net.fortuna.ical4j.model.parameter.Cn(domainAttendee.name))
                }
                vEvent.properties.add(attendeeProperty)
            }
            
            // Created and Last Modified
            vEvent.properties.add(Created(net.fortuna.ical4j.model.DateTime(Date(event.createdAt))))
            vEvent.properties.add(LastModified(net.fortuna.ical4j.model.DateTime(Date(event.updatedAt))))
            
            calendar.components.add(vEvent)
            
            return calendar.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert event to iCalendar")
            throw e
        }
    }
}
