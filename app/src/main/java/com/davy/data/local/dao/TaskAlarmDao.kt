package com.davy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davy.data.local.entity.TaskAlarmEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for task alarms.
 */
@Dao
interface TaskAlarmDao {
    
    /**
     * Get all alarms for a specific task.
     */
    @Query("SELECT * FROM task_alarms WHERE task_id = :taskId ORDER BY trigger_minutes_before ASC, trigger_absolute ASC")
    fun getAlarmsForTask(taskId: Long): Flow<List<TaskAlarmEntity>>
    
    /**
     * Get all alarms for a task (non-flow, for sync operations).
     */
    @Query("SELECT * FROM task_alarms WHERE task_id = :taskId")
    suspend fun getAlarmsForTaskSync(taskId: Long): List<TaskAlarmEntity>
    
    /**
     * Get a single alarm by ID.
     */
    @Query("SELECT * FROM task_alarms WHERE id = :alarmId")
    suspend fun getAlarmById(alarmId: Long): TaskAlarmEntity?
    
    /**
     * Insert a new alarm.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: TaskAlarmEntity): Long
    
    /**
     * Insert multiple alarms.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alarms: List<TaskAlarmEntity>): List<Long>
    
    /**
     * Update an existing alarm.
     */
    @Update
    suspend fun update(alarm: TaskAlarmEntity)
    
    /**
     * Delete an alarm.
     */
    @Delete
    suspend fun delete(alarm: TaskAlarmEntity)
    
    /**
     * Delete all alarms for a task.
     */
    @Query("DELETE FROM task_alarms WHERE task_id = :taskId")
    suspend fun deleteAlarmsForTask(taskId: Long)
    
    /**
     * Delete all alarms.
     */
    @Query("DELETE FROM task_alarms")
    suspend fun deleteAll()
    
    /**
     * Get all alarms that should trigger soon (for scheduling notifications).
     * Returns alarms where the absolute trigger time is within the specified window.
     */
    @Query("""
        SELECT ta.* FROM task_alarms ta
        INNER JOIN tasks t ON ta.task_id = t.id
        WHERE ta.trigger_absolute IS NOT NULL 
          AND ta.trigger_absolute >= :startTime 
          AND ta.trigger_absolute <= :endTime
          AND t.deleted = 0
        ORDER BY ta.trigger_absolute ASC
    """)
    suspend fun getUpcomingAlarms(startTime: Long, endTime: Long): List<TaskAlarmEntity>
    
    /**
     * Get all relative alarms for tasks with a due date.
     * Useful for calculating trigger times.
     */
    @Query("""
        SELECT ta.* FROM task_alarms ta
        INNER JOIN tasks t ON ta.task_id = t.id
        WHERE ta.trigger_minutes_before IS NOT NULL 
          AND t.due IS NOT NULL
          AND t.deleted = 0
    """)
    suspend fun getRelativeAlarmsWithDue(): List<TaskAlarmEntity>
}
