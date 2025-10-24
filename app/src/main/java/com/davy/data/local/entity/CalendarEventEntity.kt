package com.davy.data.local.entity

import androidx.room.*

/**
 * Entity representing a calendar event in the local database.
 * 
 * Maps to CalDAV VEVENT components with support for recurrence,
 * reminders, and attendees.
 */
@Entity(
    tableName = "calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["calendarId"]),
        Index(value = ["eventUrl"], unique = true),
        Index(value = ["androidEventId"]),
        Index(value = ["dtStart"])
    ]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Calendar this event belongs to.
     */
    val calendarId: Long,
    
    /**
     * Full CalDAV URL for this event.
     */
    val eventUrl: String,
    
    /**
     * Event UID (unique identifier in iCalendar).
     */
    val uid: String,
    
    /**
     * Event title/summary.
     */
    val title: String,
    
    /**
     * Event description/notes.
     */
    val description: String? = null,
    
    /**
     * Event location.
     */
    val location: String? = null,
    
    /**
     * Start time in milliseconds (epoch).
     */
    val dtStart: Long,
    
    /**
     * End time in milliseconds (epoch).
     */
    val dtEnd: Long,
    
    /**
     * Whether this is an all-day event.
     */
    val allDay: Boolean = false,
    
    /**
     * Event timezone ID.
     */
    val timezone: String? = null,
    
    /**
     * Event status (TENTATIVE, CONFIRMED, CANCELLED).
     */
    val status: String? = null,
    
    /**
     * Availability during event (FREE, BUSY, TENTATIVE, UNAVAILABLE).
     */
    val availability: String? = null,
    
    /**
     * Recurrence rule (RRULE) in iCalendar format.
     */
    val rrule: String? = null,
    
    /**
     * Recurrence dates (RDATE) in iCalendar format.
     */
    val rdate: String? = null,
    
    /**
     * Exception dates (EXDATE) in iCalendar format.
     */
    val exdate: String? = null,
    
    /**
     * Recurrence ID for exception instances.
     */
    val recurrenceId: String? = null,
    
    /**
     * Original event ID for exception instances.
     */
    val originalEventId: Long? = null,
    
    /**
     * Organizer email address.
     */
    val organizer: String? = null,
    
    /**
     * Attendees as JSON array (will be in separate table).
     */
    val attendeesJson: String? = null,
    
    /**
     * Reminders as JSON array (will be in separate table).
     */
    val remindersJson: String? = null,
    
    /**
     * ETag from server for conflict detection.
     */
    val etag: String? = null,
    
    /**
     * Android Calendar Event ID (from CalendarContract.Events._ID).
     */
    val androidEventId: Long? = null,
    
    /**
     * Whether this event has been modified locally.
     */
    val dirty: Boolean = false,
    
    /**
     * Timestamp when event was created.
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when event was last updated.
     */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when event was deleted (soft delete).
     */
    val deletedAt: Long? = null
)

/**
 * Event status enum values.
 */
enum class EventStatus {
    TENTATIVE,
    CONFIRMED,
    CANCELLED
}

/**
 * Event availability enum values.
 */
enum class Availability {
    FREE,
    BUSY,
    TENTATIVE,
    UNAVAILABLE
}
