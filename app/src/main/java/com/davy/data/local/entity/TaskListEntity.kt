package com.davy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for task lists (CalDAV calendars with VTODO support).
 *
 * Represents a task list from a CalDAV server that supports VTODO components.
 * Similar to CalendarEntity but specifically for tasks.
 *
 * @property id Primary key (auto-generated)
 * @property accountId Foreign key to Account
 * @property url CalDAV task list URL
 * @property displayName Display name of the task list
 * @property color Task list color (hex format: #RRGGBB or #AARRGGBB)
 * @property ctag Collection tag for change tracking
 * @property syncEnabled Whether sync is enabled for this task list
 * @property visible Whether task list is visible in UI
 * @property lastSynced Last sync timestamp
 */
@Entity(
    tableName = "task_lists",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["url"], unique = true)
    ]
)
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    
    @ColumnInfo(name = "url")
    val url: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "color")
    val color: String? = null,
    
    @ColumnInfo(name = "ctag")
    val ctag: String? = null,
    
    @ColumnInfo(name = "sync_enabled")
    val syncEnabled: Boolean = true,
    
    @ColumnInfo(name = "visible")
    val visible: Boolean = true,
    
    @ColumnInfo(name = "last_synced")
    val lastSynced: Long? = null
)
