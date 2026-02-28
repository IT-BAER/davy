package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model for a task (VTODO component).
 *
 * Represents a task from a CalDAV task list.
 * Immutable data class for clean architecture.
 * Supports all major VTODO properties including recurrence and subtasks.
 *
 * @property id Unique identifier (local database ID)
 * @property taskListId Task list ID this task belongs to
 * @property url CalDAV URL for this task
 * @property uid Unique identifier (from VTODO UID)
 * @property etag Entity tag for change detection
 * @property summary Task summary/title (required)
 * @property description Detailed task description
 * @property status Task status
 * @property priority Task priority
 * @property percentComplete Completion percentage (0-100)
 * @property due Due date (milliseconds since epoch)
 * @property dtStart Start date (milliseconds since epoch)
 * @property completed Completion date (milliseconds since epoch)
 * @property created Creation date (milliseconds since epoch)
 * @property lastModified Last modified date (milliseconds since epoch)
 * @property rrule Recurrence rule for recurring tasks
 * @property parentTaskId Parent task ID (for subtasks/related tasks)
 * @property parentTaskUid Parent task UID from RELATED-TO (for resolution after sync)
 * @property location Task location
 * @property geoLat Latitude from GEO property
 * @property geoLng Longitude from GEO property
 * @property taskColor Task color from COLOR property (ARGB int)
 * @property todoUrl URL property from VTODO (informational, not CalDAV resource URL)
 * @property organizer Organizer URI or email from ORGANIZER property
 * @property sequence SEQUENCE revision number for conflict detection
 * @property duration DURATION string (ISO 8601, e.g. "PT1H30M")
 * @property comment COMMENT property text
 * @property isAllDay Whether this is an all-day task (DATE vs DATE-TIME)
 * @property timezone IANA timezone ID for date properties
 * @property categories Task categories/tags
 * @property classification Access classification (PUBLIC, PRIVATE, CONFIDENTIAL)
 * @property exdates Exception dates for recurrence (list of epoch milliseconds)
 * @property rdates Additional recurrence dates (list of epoch milliseconds)
 * @property alarms Task alarms/reminders
 * @property unknownProperties Unknown iCalendar properties to preserve during round-trip
 * @property dirty Whether task has local changes pending upload
 * @property deleted Whether task is marked for deletion
 */
@Immutable
data class Task(
    val id: Long,
    val taskListId: Long,
    val url: String,
    val uid: String,
    val etag: String? = null,
    val summary: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.NEEDS_ACTION,
    val priority: TaskPriority = TaskPriority.UNDEFINED,
    val percentComplete: Int? = null,
    val due: Long? = null,
    val dtStart: Long? = null,
    val completed: Long? = null,
    val created: Long? = null,
    val lastModified: Long? = null,
    val rrule: String? = null,
    val parentTaskId: Long? = null,
    val parentTaskUid: String? = null,
    val location: String? = null,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val taskColor: Int? = null,
    val todoUrl: String? = null,
    val organizer: String? = null,
    val sequence: Int? = null,
    val duration: String? = null,
    val comment: String? = null,
    val isAllDay: Boolean = false,
    val timezone: String? = null,
    val categories: List<String> = emptyList(),
    val classification: TaskClassification = TaskClassification.PUBLIC,
    val exdates: List<Long> = emptyList(),
    val rdates: List<Long> = emptyList(),
    val alarms: List<TaskAlarm> = emptyList(),
    val unknownProperties: Map<String, String> = emptyMap(),
    val dirty: Boolean = false,
    val deleted: Boolean = false
) {
    /**
     * Checks if task is completed.
     *
     * @return True if status is COMPLETED
     */
    fun isCompleted(): Boolean = status == TaskStatus.COMPLETED

    /**
     * Checks if task is overdue.
     *
     * @return True if due date is in the past and not completed
     */
    fun isOverdue(): Boolean {
        val dueDate = due ?: return false
        return dueDate < System.currentTimeMillis() && !isCompleted()
    }

    /**
     * Checks if task is due today.
     *
     * @return True if due date is today
     */
    fun isDueToday(): Boolean {
        val dueDate = due ?: return false
        val now = System.currentTimeMillis()
        val dayStart = now - (now % (24 * 60 * 60 * 1000))
        val dayEnd = dayStart + (24 * 60 * 60 * 1000)
        return dueDate in dayStart..dayEnd
    }

    /**
     * Checks if task is a subtask.
     *
     * @return True if has parent task
     */
    fun isSubtask(): Boolean = parentTaskId != null

    /**
     * Checks if task is recurring.
     *
     * @return True if has recurrence rule
     */
    fun isRecurring(): Boolean = !rrule.isNullOrBlank()

    /**
     * Gets completion percentage with validation.
     *
     * @return Percentage (0-100) or null
     */
    fun getValidatedPercentComplete(): Int? {
        val percent = percentComplete ?: return null
        return percent.coerceIn(0, 100)
    }

    /**
     * Checks if task has a due date.
     *
     * @return True if due date is set
     */
    fun hasDueDate(): Boolean = due != null

    /**
     * Checks if task has been started.
     *
     * @return True if status is IN_PROCESS or COMPLETED
     */
    fun isStarted(): Boolean = status == TaskStatus.IN_PROCESS || status == TaskStatus.COMPLETED

    /**
     * Checks if task is cancelled.
     *
     * @return True if status is CANCELLED
     */
    fun isCancelled(): Boolean = status == TaskStatus.CANCELLED

    /**
     * Gets formatted completion info.
     *
     * @return String like "50%" or "Completed" or null
     */
    fun getCompletionInfo(): String? {
        return when {
            isCompleted() -> "Completed"
            percentComplete != null -> "$percentComplete%"
            else -> null
        }
    }

    /**
     * Checks if task has high priority.
     *
     * @return True if priority is HIGHEST or HIGH
     */
    fun isHighPriority(): Boolean = priority == TaskPriority.HIGHEST || priority == TaskPriority.HIGH

    /**
     * Checks if task has description.
     *
     * @return True if description is not blank
     */
    fun hasDescription(): Boolean = !description.isNullOrBlank()

    /**
     * Checks if task has location.
     *
     * @return True if location is not blank
     */
    fun hasLocation(): Boolean = !location.isNullOrBlank()

    /**
     * Checks if task has geo coordinates.
     *
     * @return True if both latitude and longitude are set
     */
    fun hasGeo(): Boolean = geoLat != null && geoLng != null

    /**
     * Checks if task has a custom color.
     *
     * @return True if task color is set
     */
    fun hasColor(): Boolean = taskColor != null

    /**
     * Checks if task has an associated URL.
     *
     * @return True if todo URL is not blank
     */
    fun hasTodoUrl(): Boolean = !todoUrl.isNullOrBlank()

    /**
     * Checks if task has a comment.
     *
     * @return True if comment is not blank
     */
    fun hasComment(): Boolean = !comment.isNullOrBlank()

    /**
     * Checks if task has categories.
     *
     * @return True if has at least one category
     */
    fun hasCategories(): Boolean = categories.isNotEmpty()

    /**
     * Gets days until due.
     *
     * @return Number of days (negative if overdue, null if no due date)
     */
    fun getDaysUntilDue(): Int? {
        val dueDate = due ?: return null
        val now = System.currentTimeMillis()
        val diff = dueDate - now
        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }
    
    /**
     * Checks if task has alarms.
     *
     * @return True if has at least one alarm
     */
    fun hasAlarms(): Boolean = alarms.isNotEmpty()
    
    /**
     * Checks if task is private.
     *
     * @return True if classification is PRIVATE or CONFIDENTIAL
     */
    fun isPrivate(): Boolean = classification == TaskClassification.PRIVATE || 
                               classification == TaskClassification.CONFIDENTIAL
    
    /**
     * Gets the first/soonest alarm trigger time in milliseconds.
     * Returns null if no alarms or no due/start date for relative alarms.
     */
    fun getNextAlarmTrigger(): Long? {
        if (alarms.isEmpty()) return null
        
        val referenceTime = due ?: dtStart ?: return null
        
        return alarms.mapNotNull { alarm ->
            when {
                alarm.triggerAbsolute != null -> alarm.triggerAbsolute
                alarm.triggerMinutesBefore != null -> referenceTime - (alarm.triggerMinutesBefore * 60 * 1000L)
                else -> null
            }
        }.minOrNull()
    }
}

/**
 * Task access classification (CLASS property in iCalendar).
 */
enum class TaskClassification {
    PUBLIC,
    PRIVATE,
    CONFIDENTIAL;
    
    companion object {
        fun fromValue(value: String?): TaskClassification {
            return when (value?.uppercase()) {
                "PUBLIC" -> PUBLIC
                "PRIVATE" -> PRIVATE
                "CONFIDENTIAL" -> CONFIDENTIAL
                else -> PUBLIC
            }
        }
    }
    
    fun toICalValue(): String = name
}
