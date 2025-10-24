package com.davy.data.local.entity

import androidx.room.*
import java.time.Instant

/**
 * Entity representing a calendar in the local database.
 * 
 * Maps to CalDAV calendar resources with color, display name,
 * sync configuration, and metadata.
 */
@Entity(
    tableName = "calendars",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["calendarUrl"], unique = true)
    ]
)
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Account this calendar belongs to.
     */
    val accountId: Long,
    
    /**
     * Full CalDAV URL for this calendar.
     */
    val calendarUrl: String,
    
    /**
     * Display name of the calendar.
     */
    val displayName: String,
    
    /**
     * Calendar color as ARGB hex (e.g., 0xFFFF0000 for red).
     */
    val color: Int,
    
    /**
     * Calendar description.
     */
    val description: String? = null,
    
    /**
     * Calendar timezone ID (e.g., "America/New_York").
     */
    val timezone: String? = null,
    
    /**
     * Whether this calendar is visible in Android Calendar app.
     */
    val visible: Boolean = true,
    
    /**
     * Whether this calendar is synced.
     */
    val syncEnabled: Boolean = true,
    
    /**
     * Android Calendar ID (from CalendarContract.Calendars._ID).
     */
    val androidCalendarId: Long? = null,
    
    /**
     * Current sync token (ctag) for incremental sync.
     */
    val syncToken: String? = null,
    
    /**
     * Owner principal URL (null if owned by user, non-null if shared calendar).
     */
    val owner: String? = null,
    
    /**
    * Server write permission from DAV:current-user-privilege-set (CalDAV ACL).
    * Indicates if user can create/modify events in this calendar.
     */
    val privWriteContent: Boolean = true,
    
    /**
    * Server delete/unbind permission from DAV:current-user-privilege-set (CalDAV ACL).
    * Indicates if user can delete events from this calendar.
     */
    val privUnbind: Boolean = true,
    
    /**
    * User-controlled read-only override.
    * When true, calendar is treated as read-only even if server grants write permission.
     */
    val forceReadOnly: Boolean = false,
    
    /**
    * Whether calendar supports VTODO (tasks/todos).
    * Parsed from CalDAV supported-calendar-component-set.
     */
    val supportsVTODO: Boolean = false,
    
    /**
    * Whether calendar supports VJOURNAL (journal entries).
    * Parsed from CalDAV supported-calendar-component-set.
     */
    val supportsVJOURNAL: Boolean = false,
    
    /**
    * Source URL for webcal subscriptions.
    * If this calendar is subscribed to an external webcal feed, this field contains
    * the original subscription URL (e.g., webcal://example.com/calendar.ics).
    * Parsed from CalDAV <source> property.
     */
    val source: String? = null,
    
    /**
    * WiFi-only sync restriction.
    * When true, calendar sync is restricted to WiFi networks only (no mobile data).
     */
    val wifiOnlySync: Boolean = false,
    
    /**
    * Custom sync interval in minutes.
    * Null means use account-level default sync interval.
     */
    val syncIntervalMinutes: Int? = null,
    
    /**
     * Timestamp when calendar was created.
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when calendar was last updated.
     */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when calendar was last synced.
     */
    val lastSyncedAt: Long? = null
)
