package com.davy.domain.model

/**
 * Calendar event domain model.
 * 
 * Business logic representation of a calendar event with support for
 * recurrence, reminders, and attendees.
 */
data class CalendarEvent(
    val id: Long = 0,
    val calendarId: Long,
    val eventUrl: String,
    val uid: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val dtStart: Long,
    val dtEnd: Long,
    val allDay: Boolean = false,
    val timezone: String? = null,
    val status: EventStatus? = null,
    val availability: Availability? = null,
    val rrule: String? = null,
    val rdate: String? = null,
    val exdate: String? = null,
    val recurrenceId: String? = null,
    val originalEventId: Long? = null,
    val organizer: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val etag: String? = null,
    val androidEventId: Long? = null,
    val dirty: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    
    /**
     * Check if event is recurring.
     */
    fun isRecurring(): Boolean {
        return !rrule.isNullOrBlank()
    }
    
    /**
     * Check if event is a recurrence exception.
     */
    fun isException(): Boolean {
        return recurrenceId != null && originalEventId != null
    }
    
    /**
     * Check if event has been modified locally.
     */
    fun isModified(): Boolean {
        return dirty
    }
    
    /**
     * Check if event is deleted.
     */
    fun isDeleted(): Boolean {
        return deletedAt != null
    }
    
    /**
     * Check if event is synced with Android.
     */
    fun isSyncedWithAndroid(): Boolean {
        return androidEventId != null
    }
    
    /**
     * Get event duration in milliseconds.
     */
    fun getDurationMillis(): Long {
        return dtEnd - dtStart
    }
    
    /**
     * Check if event is currently happening.
     */
    fun isNow(): Boolean {
        val now = System.currentTimeMillis()
        return now >= dtStart && now < dtEnd
    }
    
    /**
     * Check if event is in the past.
     */
    fun isPast(): Boolean {
        return System.currentTimeMillis() >= dtEnd
    }
    
    /**
     * Check if event is in the future.
     */
    fun isFuture(): Boolean {
        return System.currentTimeMillis() < dtStart
    }
}

/**
 * Event status enum.
 */
enum class EventStatus {
    TENTATIVE,
    CONFIRMED,
    CANCELLED
}

/**
 * Event availability enum.
 */
enum class Availability {
    FREE,
    BUSY,
    TENTATIVE,
    UNAVAILABLE
}

/**
 * Event attendee.
 */
data class Attendee(
    val email: String,
    val name: String? = null,
    val role: AttendeeRole = AttendeeRole.REQUIRED_PARTICIPANT,
    val status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION
)

/**
 * Attendee role enum.
 */
enum class AttendeeRole {
    CHAIR,
    REQUIRED_PARTICIPANT,
    OPTIONAL_PARTICIPANT,
    NON_PARTICIPANT
}

/**
 * Attendee status enum.
 */
enum class AttendeeStatus {
    NEEDS_ACTION,
    ACCEPTED,
    DECLINED,
    TENTATIVE,
    DELEGATED
}

/**
 * Event reminder.
 */
data class Reminder(
    val minutes: Int,
    val method: ReminderMethod = ReminderMethod.ALERT
)

/**
 * Reminder method enum.
 */
enum class ReminderMethod {
    ALERT,
    EMAIL,
    SMS
}

/**
 * Extension function to convert CalendarEvent to iCalendar format.
 * Uses ICalendarParser for conversion.
 */
fun CalendarEvent.toICalendar(): String {
    return com.davy.data.remote.caldav.ICalendarParser.eventToICalendar(this)
}
