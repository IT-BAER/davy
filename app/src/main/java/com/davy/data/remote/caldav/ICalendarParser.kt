package com.davy.data.remote.caldav

import android.graphics.Color
import com.davy.domain.model.AlarmAction
import com.davy.domain.model.AlarmRelativeTo
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import com.davy.domain.model.Task
import com.davy.domain.model.TaskAlarm
import com.davy.domain.model.TaskClassification
import com.davy.domain.model.TaskPriority
import com.davy.domain.model.TaskStatus
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarParserFactory
import net.fortuna.ical4j.data.ContentHandlerContext
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VToDo
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
     * Parse iCalendar string to extract tasks (VTODO components).
     * Returns list of Task domain objects.
     * 
     * Uses lenient parsing to handle various server implementations.
     */
    fun parseTasks(icalendarData: String): List<Task> {
        val tasks = mutableListOf<Task>()
        
        try {
            if (icalendarData.isBlank()) {
                Timber.i("ICalendarParser: Empty iCalendar payload, returning 0 tasks")
                return emptyList()
            }
            Timber.d("ICalendarParser: Building calendar from data for tasks (${icalendarData.length} chars)")
            
            val preprocessed = preprocessICalendar(icalendarData)
            if (preprocessed.isBlank()) {
                Timber.i("ICalendarParser: Empty iCalendar payload after preprocessing, returning 0 tasks")
                return emptyList()
            }
            
            val builder = CalendarBuilder(
                CalendarParserFactory.getInstance().get(),
                ContentHandlerContext().withSupressInvalidProperties(true),
                TimeZoneRegistryFactory.getInstance().createRegistry()
            )
            
            val calendar = builder.build(StringReader(preprocessed))
            
            Timber.d("ICalendarParser: Calendar built, getting VTODO components")
            val vTodos = calendar.getComponents<VToDo>("VTODO")
            
            Timber.d("ICalendarParser: Found ${vTodos.size} VTODO components")
            vTodos.forEach { vTodo ->
                try {
                    Timber.d("ICalendarParser: Parsing VTODO with UID=${vTodo.uid?.value}")
                    val task = parseVTodo(vTodo)
                    tasks.add(task)
                    Timber.d("ICalendarParser: Successfully parsed task: ${task.summary}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse individual VTODO, skipping (will continue with others)")
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isEof = msg.contains("Unexpected end of file", ignoreCase = true)
            if (isEof) {
                Timber.w(e, "ICalendar parse aborted due to unexpected EOF. Treating as 0 tasks")
            } else {
                Timber.e(e, "Failed to parse iCalendar data for tasks - malformed VTODO")
            }
            
            val summaryMatch = Regex("SUMMARY:(.+?)\\r?\\n").find(icalendarData)
            val uidMatch = Regex("UID:(.+?)\\r?\\n").find(icalendarData)
            val taskInfo = buildString {
                uidMatch?.groupValues?.get(1)?.let { append("UID=$it ") }
                summaryMatch?.groupValues?.get(1)?.let { append("SUMMARY='$it'") }
            }
            if (taskInfo.isNotEmpty()) {
                Timber.w("Skipping incompatible task: $taskInfo")
            }
        }
        
        Timber.d("ICalendarParser: Returning ${tasks.size} tasks")
        return tasks
    }
    
    /**
     * Parse single VTODO component to Task domain model.
     * Maps all RFC 5545 VTODO properties to Task fields.
     * Enhanced with VALARM, CLASS, EXDATE, RDATE, GEO, URL, ORGANIZER, SEQUENCE,
     * COLOR, DURATION, COMMENT, all-day detection, timezone extraction,
     * and unknown property support.
     */
    private fun parseVTodo(vTodo: VToDo): Task {
        val uid = vTodo.uid?.value ?: UUID.randomUUID().toString()
        val summary = vTodo.summary?.value ?: ""
        val description = vTodo.description?.value
        val location = vTodo.location?.value
        
        // Parse status
        val statusValue = vTodo.status?.value
        val status = TaskStatus.fromValue(statusValue)
        
        // Parse priority (0-9, where 0=undefined, 1=highest, 9=lowest)
        val priorityValue = vTodo.priority?.level
        val priority = TaskPriority.fromValue(priorityValue)
        
        // Parse percent complete
        val percentComplete = vTodo.percentComplete?.percentage
        
        // Parse dates with all-day and timezone detection
        val dtStartProp = vTodo.startDate
        val dueProp = vTodo.due
        val dtStart = dtStartProp?.date?.time
        val due = dueProp?.date?.time
        val completed = vTodo.dateCompleted?.date?.time
        val created = vTodo.dateStamp?.date?.time ?: vTodo.created?.date?.time
        val lastModified = vTodo.lastModified?.date?.time
        
        // Detect all-day tasks: DATE value type (no time component) indicates all-day
        // ical4j uses net.fortuna.ical4j.model.Date for DATE and DateTime for DATE-TIME
        val isAllDay = run {
            val startIsDate = dtStartProp?.date != null &&
                dtStartProp.date !is net.fortuna.ical4j.model.DateTime
            val dueIsDate = dueProp?.date != null &&
                dueProp.date !is net.fortuna.ical4j.model.DateTime
            // All-day if any present date property uses DATE (not DATE-TIME)
            (dtStartProp != null && startIsDate) || (dueProp != null && dueIsDate)
        }
        
        // Extract timezone from DTSTART or DUE (TZID parameter)
        val timezone = run {
            val tzid = dtStartProp?.getParameter<net.fortuna.ical4j.model.parameter.TzId>("TZID")
                ?: dueProp?.getParameter<net.fortuna.ical4j.model.parameter.TzId>("TZID")
            tzid?.value
        }
        
        // DTSTART/DUE validation (based on ical4android best practices):
        // 1. If both DTSTART and DUE exist and DUE <= DTSTART, drop DTSTART
        val validatedDtStart = if (dtStart != null && due != null && due <= dtStart) {
            Timber.w("VTODO $uid: DUE ($due) <= DTSTART ($dtStart), dropping DTSTART")
            null
        } else {
            dtStart
        }
        
        // Parse recurrence rule
        val rrule = vTodo.getProperty<RRule>("RRULE")?.value
        
        // Parse categories
        val categoriesProperty = vTodo.getProperty<Categories>("CATEGORIES")
        val categories = categoriesProperty?.categories?.toList() ?: emptyList()
        
        // Parse related-to for parent task (subtask support)
        // RELATED-TO;RELTYPE=PARENT:parent-uid
        val relatedTo = vTodo.getProperty<RelatedTo>("RELATED-TO")
        val parentTaskUid = relatedTo?.value // Store the UID for later resolution
        
        // Parse CLASS (access classification)
        val classValue = vTodo.getProperty<Clazz>("CLASS")?.value
        val classification = TaskClassification.fromValue(classValue)
        
        // Parse GEO (geographic position: latitude;longitude)
        val geo = vTodo.getProperty<Geo>("GEO")
        val geoLat = geo?.latitude?.toDouble()
        val geoLng = geo?.longitude?.toDouble()
        
        // Parse URL
        val todoUrl = vTodo.getProperty<Url>("URL")?.value
        
        // Parse ORGANIZER (e.g., mailto:user@example.com)
        val organizer = vTodo.getProperty<Organizer>("ORGANIZER")?.value
        
        // Parse SEQUENCE (version/conflict detection counter)
        val sequence = vTodo.getProperty<Sequence>("SEQUENCE")?.sequenceNo
        
        // Parse COLOR (CSS3 color name per RFC 7986)
        val taskColor = run {
            val colorProp = vTodo.getProperty<net.fortuna.ical4j.model.property.Color>("COLOR")
            if (colorProp?.value != null) {
                try {
                    Color.parseColor(colorProp.value)
                } catch (e: Exception) {
                    Timber.w("Failed to parse COLOR value '${colorProp.value}' for task $uid")
                    null
                }
            } else {
                null
            }
        }
        
        // Parse DURATION (ISO 8601 duration, e.g., PT1H30M)
        val duration = vTodo.getProperty<Duration>("DURATION")?.value
        
        // Parse COMMENT
        val comment = vTodo.getProperty<Comment>("COMMENT")?.value
        
        // Parse EXDATE (exception dates for recurrence)
        val exdates = mutableListOf<Long>()
        val exdateProperties = vTodo.getProperties<ExDate>("EXDATE")
        exdateProperties?.forEach { exDate ->
            exDate.dates?.forEach { date ->
                exdates.add(date.time)
            }
        }
        
        // Parse RDATE (additional recurrence dates)
        val rdates = mutableListOf<Long>()
        val rdateProperties = vTodo.getProperties<RDate>("RDATE")
        rdateProperties?.forEach { rDate ->
            rDate.dates?.forEach { date ->
                rdates.add(date.time)
            }
        }
        
        // Parse VALARM components (reminders)
        // Use AlarmsAccessor.getAlarms() method for proper ical4j 3.x API access
        val alarms = mutableListOf<TaskAlarm>()
        val vAlarms = try {
            // In ical4j 3.x, VTodo implements AlarmsAccessor which provides getAlarms()
            vTodo.alarms ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to get alarms via accessor, trying component filter")
            // Fallback to component filtering
            vTodo.components?.filterIsInstance<VAlarm>() ?: emptyList()
        }
        Timber.d("Found ${vAlarms.size} VALARM components for task UID: $uid")
        vAlarms.forEach { vAlarm ->
            try {
                val alarm = parseVAlarm(vAlarm)
                alarms.add(alarm)
                Timber.d("Parsed VALARM: action=${alarm.action}, triggerMinutes=${alarm.triggerMinutesBefore}, triggerAbsolute=${alarm.triggerAbsolute}")
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse VALARM, skipping")
            }
        }
        Timber.d("Total parsed alarms for task UID $uid: ${alarms.size}")
        
        // Collect unknown properties for preservation during round-trip
        val unknownProperties = mutableMapOf<String, String>()
        val knownPropertyNames = setOf(
            "UID", "SUMMARY", "DESCRIPTION", "LOCATION", "STATUS", "PRIORITY",
            "PERCENT-COMPLETE", "DTSTART", "DUE", "COMPLETED", "CREATED",
            "LAST-MODIFIED", "DTSTAMP", "RRULE", "CATEGORIES", "RELATED-TO",
            "CLASS", "EXDATE", "RDATE", "ORGANIZER", "URL", "GEO", "SEQUENCE",
            "COLOR", "DURATION", "COMMENT"
        )
        vTodo.properties?.forEach { property ->
            val name = property.name?.uppercase()
            if (name != null && !knownPropertyNames.contains(name) && property.value != null) {
                // Store unknown property for round-trip preservation
                unknownProperties[name] = property.value
                Timber.d("Preserving unknown property: $name = ${property.value}")
            }
        }
        
        return Task(
            id = 0, // Will be set by database
            taskListId = 0, // Will be set when assigning to task list
            url = "", // Will be set from task URL
            uid = uid,
            etag = null, // Will be set from HTTP response
            summary = summary,
            description = description,
            status = status,
            priority = priority,
            percentComplete = percentComplete,
            due = due,
            dtStart = validatedDtStart,
            completed = completed,
            created = created,
            lastModified = lastModified ?: System.currentTimeMillis(),
            rrule = rrule,
            parentTaskId = null, // Will be resolved later using parentTaskUid
            parentTaskUid = parentTaskUid,
            location = location,
            geoLat = geoLat,
            geoLng = geoLng,
            taskColor = taskColor,
            todoUrl = todoUrl,
            organizer = organizer,
            sequence = sequence,
            duration = duration,
            comment = comment,
            isAllDay = isAllDay,
            timezone = timezone,
            categories = categories,
            classification = classification,
            exdates = exdates,
            rdates = rdates,
            alarms = alarms,
            unknownProperties = unknownProperties,
            dirty = false,
            deleted = false
        )
    }
    
    /**
     * Parse VALARM component to TaskAlarm domain model.
     */
    private fun parseVAlarm(vAlarm: VAlarm): TaskAlarm {
        // Parse action (DISPLAY, AUDIO, EMAIL)
        val actionValue = vAlarm.action?.value
        val action = AlarmAction.fromValue(actionValue)
        
        // Parse trigger
        val trigger = vAlarm.trigger
        var triggerMinutesBefore: Int? = null
        var triggerAbsolute: Long? = null
        var triggerRelativeTo = AlarmRelativeTo.START
        
        if (trigger != null) {
            // Check for RELATED parameter (START or END)
            val relatedParam = trigger.getParameter<net.fortuna.ical4j.model.parameter.Related>("RELATED")
            if (relatedParam?.value == "END") {
                triggerRelativeTo = AlarmRelativeTo.END
            }
            
            // Check if absolute or relative trigger
            val triggerDate = trigger.dateTime
            if (triggerDate != null) {
                // Absolute trigger - specific date/time
                triggerAbsolute = triggerDate.time
            } else {
                // Relative trigger - duration before/after
                // In ical4j 3.x, trigger.duration returns TemporalAmount (java.time)
                val duration = trigger.duration
                if (duration != null) {
                    // Convert TemporalAmount to total minutes
                    val totalSeconds = when (duration) {
                        is java.time.Duration -> duration.seconds
                        is java.time.Period -> {
                            // Period is day-based, convert to seconds
                            (duration.days.toLong() * 24 * 60 * 60) +
                            (duration.months.toLong() * 30 * 24 * 60 * 60) +
                            (duration.years.toLong() * 365 * 24 * 60 * 60)
                        }
                        else -> {
                            // Fallback: try to get seconds from TemporalAmount
                            try {
                                duration.get(java.time.temporal.ChronoUnit.SECONDS)
                            } catch (e: Exception) {
                                0L
                            }
                        }
                    }
                    
                    val totalMinutes = (kotlin.math.abs(totalSeconds) / 60).toInt()
                    
                    // If negative, it means "before" the event
                    triggerMinutesBefore = totalMinutes
                }
            }
        }
        
        // Parse description
        val description = vAlarm.description?.value
        
        // Parse summary (for EMAIL action)
        val summary = vAlarm.summary?.value
        
        return TaskAlarm(
            id = 0, // Will be set by database
            taskId = 0, // Will be set when associating with task
            action = action,
            triggerMinutesBefore = triggerMinutesBefore,
            triggerAbsolute = triggerAbsolute,
            triggerRelativeTo = triggerRelativeTo,
            description = description,
            summary = summary
        )
    }
    
    /**
     * Convert Task domain model to iCalendar VTODO component.
     * Returns iCalendar string representation.
     * Enhanced with VALARM, CLASS, EXDATE, RDATE, RELATED-TO, GEO, URL, ORGANIZER,
     * SEQUENCE, COLOR, DURATION, COMMENT, VTIMEZONE, all-day, and unknown property support.
     */
    fun taskToICalendar(task: Task): String {
        try {
            val calendar = net.fortuna.ical4j.model.Calendar()
            calendar.properties.add(ProdId("-//DAVy//CalDAV Client 1.0//EN"))
            calendar.properties.add(Version.VERSION_2_0)
            
            // Track timezones for VTIMEZONE generation
            val usedTimezones = mutableSetOf<String>()
            
            val vTodo = VToDo()
            
            // UID
            vTodo.properties.add(Uid(task.uid))
            
            // Summary (title)
            if (task.summary.isNotEmpty()) {
                vTodo.properties.add(Summary(task.summary))
            }
            
            // Description
            if (!task.description.isNullOrEmpty()) {
                vTodo.properties.add(Description(task.description))
            }
            
            // Location
            if (!task.location.isNullOrEmpty()) {
                vTodo.properties.add(Location(task.location))
            }
            
            // Status
            val statusValue = when (task.status) {
                TaskStatus.NEEDS_ACTION -> "NEEDS-ACTION"
                TaskStatus.IN_PROCESS -> "IN-PROCESS"
                TaskStatus.COMPLETED -> "COMPLETED"
                TaskStatus.CANCELLED -> "CANCELLED"
            }
            vTodo.properties.add(Status(statusValue))
            
            // Priority
            if (task.priority != TaskPriority.UNDEFINED) {
                vTodo.properties.add(Priority(task.priority.value))
            }
            
            // Percent complete
            task.percentComplete?.let {
                vTodo.properties.add(PercentComplete(it))
            }
            
            // Due date — use DATE for all-day tasks, DATE-TIME otherwise
            task.due?.let { dueMillis ->
                if (task.isAllDay) {
                    val dateOnly = net.fortuna.ical4j.model.Date(Date(dueMillis))
                    vTodo.properties.add(Due(dateOnly))
                } else {
                    val dateTime = net.fortuna.ical4j.model.DateTime(Date(dueMillis))
                    if (!task.timezone.isNullOrEmpty()) {
                        try {
                            val registry = TimeZoneRegistryFactory.getInstance().createRegistry()
                            val tz = registry.getTimeZone(task.timezone)
                            if (tz != null) {
                                dateTime.timeZone = tz
                                usedTimezones.add(task.timezone)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to set timezone ${task.timezone} for DUE")
                        }
                    }
                    vTodo.properties.add(Due(dateTime))
                }
            }
            
            // Start date — use DATE for all-day tasks, DATE-TIME otherwise
            task.dtStart?.let { startMillis ->
                if (task.isAllDay) {
                    val dateOnly = net.fortuna.ical4j.model.Date(Date(startMillis))
                    vTodo.properties.add(DtStart(dateOnly))
                } else {
                    val dateTime = net.fortuna.ical4j.model.DateTime(Date(startMillis))
                    if (!task.timezone.isNullOrEmpty()) {
                        try {
                            val registry = TimeZoneRegistryFactory.getInstance().createRegistry()
                            val tz = registry.getTimeZone(task.timezone)
                            if (tz != null) {
                                dateTime.timeZone = tz
                                usedTimezones.add(task.timezone)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to set timezone ${task.timezone} for DTSTART")
                        }
                    }
                    vTodo.properties.add(DtStart(dateTime))
                }
            }
            
            // Completed date
            task.completed?.let { completedMillis ->
                vTodo.properties.add(Completed(net.fortuna.ical4j.model.DateTime(Date(completedMillis))))
            }
            
            // Recurrence rule
            if (!task.rrule.isNullOrEmpty()) {
                vTodo.properties.add(RRule(task.rrule))
            }
            
            // Categories
            if (task.categories.isNotEmpty()) {
                val categoriesList = net.fortuna.ical4j.model.TextList()
                task.categories.forEach { categoriesList.add(it) }
                vTodo.properties.add(Categories(categoriesList))
            }
            
            // CLASS (access classification)
            if (task.classification != TaskClassification.PUBLIC) {
                vTodo.properties.add(Clazz(task.classification.toICalValue()))
            }
            
            // GEO (geographic position)
            if (task.hasGeo()) {
                vTodo.properties.add(Geo(
                    java.math.BigDecimal(task.geoLat!!.toString()),
                    java.math.BigDecimal(task.geoLng!!.toString())
                ))
            }
            
            // URL
            if (task.hasTodoUrl()) {
                try {
                    vTodo.properties.add(Url(java.net.URI(task.todoUrl!!)))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write URL property: ${task.todoUrl}")
                }
            }
            
            // ORGANIZER
            if (!task.organizer.isNullOrEmpty()) {
                try {
                    vTodo.properties.add(Organizer(task.organizer))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write ORGANIZER property: ${task.organizer}")
                }
            }
            
            // SEQUENCE (conflict detection counter)
            task.sequence?.let {
                vTodo.properties.add(Sequence(it))
            }
            
            // COLOR (RFC 7986)
            if (task.hasColor()) {
                try {
                    // Convert int color back to hex string for iCalendar
                    val hexColor = String.format("#%06X", 0xFFFFFF and task.taskColor!!)
                    vTodo.properties.add(net.fortuna.ical4j.model.property.Color(null, hexColor))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write COLOR property")
                }
            }
            
            // DURATION
            if (!task.duration.isNullOrEmpty()) {
                try {
                    vTodo.properties.add(Duration(java.time.Duration.parse(task.duration)))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write DURATION property: ${task.duration}")
                }
            }
            
            // COMMENT
            if (task.hasComment()) {
                vTodo.properties.add(Comment(task.comment))
            }
            
            // EXDATE (exception dates)
            if (task.exdates.isNotEmpty()) {
                val exDateList = net.fortuna.ical4j.model.DateList(net.fortuna.ical4j.model.parameter.Value.DATE_TIME)
                task.exdates.forEach { exDateList.add(net.fortuna.ical4j.model.DateTime(Date(it))) }
                vTodo.properties.add(ExDate(exDateList))
            }
            
            // RDATE (additional recurrence dates)
            if (task.rdates.isNotEmpty()) {
                val rDateList = net.fortuna.ical4j.model.DateList(net.fortuna.ical4j.model.parameter.Value.DATE_TIME)
                task.rdates.forEach { rDateList.add(net.fortuna.ical4j.model.DateTime(Date(it))) }
                vTodo.properties.add(RDate(rDateList))
            }
            
            // RELATED-TO (parent task for subtasks)
            if (!task.parentTaskUid.isNullOrEmpty()) {
                val relatedTo = RelatedTo(task.parentTaskUid)
                relatedTo.parameters.add(net.fortuna.ical4j.model.parameter.RelType.PARENT)
                vTodo.properties.add(relatedTo)
            }
            
            // DTSTAMP is required
            vTodo.properties.add(DtStamp(net.fortuna.ical4j.model.DateTime(Date(System.currentTimeMillis()))))
            
            // Last Modified
            task.lastModified?.let {
                vTodo.properties.add(LastModified(net.fortuna.ical4j.model.DateTime(Date(it))))
            }
            
            // Add unknown properties for round-trip preservation
            task.unknownProperties.forEach { (name, value) ->
                try {
                    // Create generic X- property for unknown properties
                    // Note: Only X- properties can be safely added without validation
                    if (name.startsWith("X-")) {
                        vTodo.properties.add(XProperty(name, value))
                    }
                    Timber.d("Preserved unknown property: $name = $value")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to add unknown property $name")
                }
            }
            
            // Add VALARM components for reminders
            task.alarms.forEach { alarm ->
                try {
                    val vAlarm = createVAlarm(alarm)
                    vTodo.components.add(vAlarm)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to create VALARM for alarm: ${alarm.description}")
                }
            }
            
            // Add VTIMEZONE components for all referenced timezones
            val registry = TimeZoneRegistryFactory.getInstance().createRegistry()
            usedTimezones.forEach { tzId ->
                try {
                    val tz = registry.getTimeZone(tzId)
                    if (tz?.vTimeZone != null) {
                        calendar.components.add(tz.vTimeZone)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to add VTIMEZONE for $tzId")
                }
            }
            
            calendar.components.add(vTodo)
            
            return calendar.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert task to iCalendar")
            throw e
        }
    }
    
    /**
     * Create a VALARM component from TaskAlarm domain model.
     */
    private fun createVAlarm(alarm: TaskAlarm): VAlarm {
        val vAlarm: VAlarm
        
        // Create trigger
        if (alarm.triggerAbsolute != null) {
            // Absolute trigger
            val triggerDate = net.fortuna.ical4j.model.DateTime(Date(alarm.triggerAbsolute))
            vAlarm = VAlarm(triggerDate)
        } else if (alarm.triggerMinutesBefore != null) {
            // Relative trigger - use java.time.Duration (TemporalAmount) for ical4j 3.x
            // Negative values represent "before" the event
            val totalMinutes = alarm.triggerMinutesBefore
            
            // Create negative duration for "before" trigger
            val duration = java.time.Duration.ofMinutes(-totalMinutes.toLong())
            vAlarm = VAlarm(duration)
            
            // Add RELATED parameter if relative to END (due date)
            if (alarm.triggerRelativeTo == AlarmRelativeTo.END) {
                vAlarm.trigger?.parameters?.add(net.fortuna.ical4j.model.parameter.Related.END)
            }
        } else {
            // Default: 15 minutes before
            val duration = java.time.Duration.ofMinutes(-15)
            vAlarm = VAlarm(duration)
        }
        
        // Set action
        val action = when (alarm.action) {
            AlarmAction.DISPLAY -> Action.DISPLAY
            AlarmAction.AUDIO -> Action.AUDIO
            AlarmAction.EMAIL -> Action.EMAIL
        }
        vAlarm.properties.add(action)
        
        // Description (required for DISPLAY)
        if (!alarm.description.isNullOrEmpty()) {
            vAlarm.properties.add(Description(alarm.description))
        } else if (alarm.action == AlarmAction.DISPLAY) {
            // DISPLAY requires description
            vAlarm.properties.add(Description("Task reminder"))
        }
        
        // Summary (for EMAIL action)
        if (!alarm.summary.isNullOrEmpty() && alarm.action == AlarmAction.EMAIL) {
            vAlarm.properties.add(Summary(alarm.summary))
        }
        
        return vAlarm
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
