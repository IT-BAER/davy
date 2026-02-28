package com.davy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for tasks (VTODO components).
 *
 * Represents a task from a CalDAV task list.
 * Supports all major VTODO properties including recurrence and subtasks.
 *
 * @property id Primary key (auto-generated)
 * @property taskListId Foreign key to TaskList
 * @property url CalDAV task URL
 * @property uid Unique identifier (from VTODO UID)
 * @property etag Entity tag for change tracking
 * @property summary Task summary/title
 * @property description Task description
 * @property status Task status (NEEDS-ACTION, COMPLETED, IN-PROCESS, CANCELLED)
 * @property priority Task priority (0-9, where 0=undefined, 1=highest, 9=lowest)
 * @property percentComplete Completion percentage (0-100)
 * @property due Due date timestamp (milliseconds since epoch)
 * @property dtStart Start date timestamp
 * @property completed Completion date timestamp
 * @property created Creation date timestamp
 * @property lastModified Last modified timestamp
 * @property rrule Recurrence rule (for recurring tasks)
 * @property parentTaskId Parent task ID (for subtasks)
 * @property location Task location
 * @property categories Task categories (JSON array)
 * @property classification Access classification (PUBLIC, PRIVATE, CONFIDENTIAL)
 * @property exdates Exception dates for recurrence (JSON array of timestamps)
 * @property rdates Additional recurrence dates (JSON array of timestamps)
 * @property geoLat Latitude from GEO property
 * @property geoLng Longitude from GEO property
 * @property taskColor Task color from COLOR property (ARGB int)
 * @property todoUrl URL property from VTODO (not the CalDAV resource URL)
 * @property organizer Organizer (URI or email) from ORGANIZER property
 * @property sequence Revision sequence number from SEQUENCE property
 * @property duration Duration string from DURATION property (ISO 8601)
 * @property comment Comment text from COMMENT property
 * @property isAllDay Whether this is an all-day task (DATE vs DATE-TIME)
 * @property timezone IANA timezone ID for date properties (e.g. "Europe/Berlin")
 * @property parentTaskUid Parent task UID for RELATED-TO resolution
 * @property unknownProperties Unknown iCalendar properties to preserve (JSON)
 * @property dirty Whether task has local changes
 * @property deleted Whether task is marked for deletion
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_list_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["task_list_id"]),
        Index(value = ["url"], unique = true),
        Index(value = ["uid"]),
        Index(value = ["parent_task_id"]),
        Index(value = ["dirty"]),
        Index(value = ["deleted"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "task_list_id")
    val taskListId: Long,
    
    @ColumnInfo(name = "url")
    val url: String,
    
    @ColumnInfo(name = "uid")
    val uid: String,
    
    @ColumnInfo(name = "etag")
    val etag: String? = null,
    
    @ColumnInfo(name = "summary")
    val summary: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "status")
    val status: String? = null,
    
    @ColumnInfo(name = "priority")
    val priority: Int? = null,
    
    @ColumnInfo(name = "percent_complete")
    val percentComplete: Int? = null,
    
    @ColumnInfo(name = "due")
    val due: Long? = null,
    
    @ColumnInfo(name = "dt_start")
    val dtStart: Long? = null,
    
    @ColumnInfo(name = "completed")
    val completed: Long? = null,
    
    @ColumnInfo(name = "created")
    val created: Long? = null,
    
    @ColumnInfo(name = "last_modified")
    val lastModified: Long? = null,
    
    @ColumnInfo(name = "rrule")
    val rrule: String? = null,
    
    @ColumnInfo(name = "parent_task_id")
    val parentTaskId: Long? = null,
    
    @ColumnInfo(name = "location")
    val location: String? = null,
    
    /**
     * Latitude from GEO property (RFC 5545 §3.8.1.6).
     */
    @ColumnInfo(name = "geo_lat")
    val geoLat: Double? = null,
    
    /**
     * Longitude from GEO property (RFC 5545 §3.8.1.6).
     */
    @ColumnInfo(name = "geo_lng")
    val geoLng: Double? = null,
    
    /**
     * Task color from COLOR property (ARGB integer).
     * Not the CalDAV collection color — this is the per-task color.
     */
    @ColumnInfo(name = "task_color")
    val taskColor: Int? = null,
    
    /**
     * URL property from VTODO (RFC 5545 §3.8.4.6).
     * This is the informational URL associated with the task, NOT the CalDAV resource URL.
     */
    @ColumnInfo(name = "todo_url")
    val todoUrl: String? = null,
    
    /**
     * ORGANIZER property (RFC 5545 §3.8.4.3).
     * Typically a mailto: URI or cal-address.
     */
    @ColumnInfo(name = "organizer")
    val organizer: String? = null,
    
    /**
     * SEQUENCE number (RFC 5545 §3.8.7.4).
     * Revision sequence number for conflict detection.
     */
    @ColumnInfo(name = "sequence")
    val sequence: Int? = null,
    
    /**
     * DURATION property (RFC 5545 §3.8.2.5).
     * Stored as ISO 8601 duration string (e.g. "PT1H30M").
     * Mutually exclusive with DUE per RFC 5545.
     */
    @ColumnInfo(name = "duration")
    val duration: String? = null,
    
    /**
     * COMMENT property (RFC 5545 §3.8.1.4).
     * Additional descriptive text or comment for the task.
     */
    @ColumnInfo(name = "comment")
    val comment: String? = null,
    
    /**
     * Whether this is an all-day task.
     * True when DTSTART/DUE use DATE (not DATE-TIME) value type.
     */
    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean = false,
    
    /**
     * IANA timezone ID for date properties (e.g. "Europe/Berlin").
     * Null for floating or UTC times.
     */
    @ColumnInfo(name = "timezone")
    val timezone: String? = null,
    
    @ColumnInfo(name = "categories")
    val categories: String? = null, // JSON array
    
    /**
     * Access classification: PUBLIC, PRIVATE, or CONFIDENTIAL
     */
    @ColumnInfo(name = "classification")
    val classification: String? = null,
    
    /**
     * Exception dates for recurrence rules.
     * Stored as JSON array of epoch milliseconds.
     */
    @ColumnInfo(name = "exdates")
    val exdates: String? = null,
    
    /**
     * Additional recurrence dates.
     * Stored as JSON array of epoch milliseconds.
     */
    @ColumnInfo(name = "rdates")
    val rdates: String? = null,
    
    /**
     * Parent task UID from RELATED-TO property.
     * Used for resolving subtask relationships after sync.
     */
    @ColumnInfo(name = "parent_task_uid")
    val parentTaskUid: String? = null,
    
    /**
     * Unknown iCalendar properties to preserve during round-trip.
     * Stored as JSON object mapping property name to value.
     */
    @ColumnInfo(name = "unknown_properties")
    val unknownProperties: String? = null,
    
    @ColumnInfo(name = "dirty")
    val dirty: Boolean = false,
    
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false
)
