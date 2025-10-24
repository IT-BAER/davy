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
    
    @ColumnInfo(name = "categories")
    val categories: String? = null, // JSON array
    
    @ColumnInfo(name = "dirty")
    val dirty: Boolean = false,
    
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false
)
