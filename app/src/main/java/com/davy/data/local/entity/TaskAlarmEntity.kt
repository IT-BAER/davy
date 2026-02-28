package com.davy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for task alarms (VALARM components from iCalendar).
 *
 * This entity stores alarms/reminders associated with tasks.
 * References TaskEntity with a foreign key for cascade deletion.
 */
@Entity(
    tableName = "task_alarms",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("task_id")
    ]
)
data class TaskAlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "task_id")
    val taskId: Long,
    
    /**
     * Alarm action: DISPLAY, AUDIO, or EMAIL
     */
    @ColumnInfo(name = "action")
    val action: String = "DISPLAY",
    
    /**
     * Trigger offset in minutes before the task's start/due date.
     * Negative values mean "before", positive means "after".
     * Null if this is an absolute trigger.
     */
    @ColumnInfo(name = "trigger_minutes_before")
    val triggerMinutesBefore: Int? = null,
    
    /**
     * Absolute trigger time in epoch milliseconds.
     * Null if this is a relative trigger.
     */
    @ColumnInfo(name = "trigger_absolute")
    val triggerAbsolute: Long? = null,
    
    /**
     * What the relative trigger is relative to: START or END (due).
     * Only relevant for relative triggers.
     */
    @ColumnInfo(name = "trigger_relative_to")
    val triggerRelativeTo: String = "START",
    
    /**
     * Alarm description/message text.
     */
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    /**
     * Alarm summary (mainly for EMAIL action).
     */
    @ColumnInfo(name = "summary")
    val summary: String? = null
)
