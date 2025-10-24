package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.TaskListEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TaskList entities.
 *
 * Provides CRUD operations and queries for task lists.
 */
@Dao
interface TaskListDao {
    
    /**
     * Inserts a new task list.
     *
     * @param taskList The task list to insert
     * @return The ID of the inserted task list
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(taskList: TaskListEntity): Long
    
    /**
     * Inserts multiple task lists.
     *
     * @param taskLists The task lists to insert
     * @return List of inserted task list IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(taskLists: List<TaskListEntity>): List<Long>
    
    /**
     * Updates an existing task list.
     *
     * @param taskList The task list to update
     */
    @Update
    suspend fun update(taskList: TaskListEntity)
    
    /**
     * Deletes a task list.
     *
     * @param taskList The task list to delete
     */
    @Delete
    suspend fun delete(taskList: TaskListEntity)
    
    /**
     * Deletes a task list by ID.
     *
     * @param id The task list ID
     */
    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * Gets all task lists as Flow.
     *
     * @return Flow of all task lists
     */
    @Query("SELECT * FROM task_lists ORDER BY display_name ASC")
    fun getAllTaskLists(): Flow<List<TaskListEntity>>
    
    /**
     * Gets task lists for a specific account as Flow.
     *
     * @param accountId The account ID
     * @return Flow of task lists for the account
     */
    @Query("SELECT * FROM task_lists WHERE account_id = :accountId ORDER BY display_name ASC")
    fun getByAccountId(accountId: Long): Flow<List<TaskListEntity>>
    
    /**
     * Gets a specific task list by ID.
     *
     * @param id The task list ID
     * @return The task list or null if not found
     */
    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getById(id: Long): TaskListEntity?
    
    /**
     * Gets a task list by URL.
     *
     * @param url The task list URL
     * @return The task list or null if not found
     */
    @Query("SELECT * FROM task_lists WHERE url = :url")
    suspend fun getByUrl(url: String): TaskListEntity?
    
    /**
     * Gets all sync-enabled task lists.
     *
     * @return List of sync-enabled task lists
     */
    @Query("SELECT * FROM task_lists WHERE sync_enabled = 1 ORDER BY display_name ASC")
    suspend fun getSyncEnabled(): List<TaskListEntity>
    
    /**
     * Gets all visible task lists as Flow.
     *
     * @return Flow of visible task lists
     */
    @Query("SELECT * FROM task_lists WHERE visible = 1 ORDER BY display_name ASC")
    fun getVisible(): Flow<List<TaskListEntity>>
    
    /**
     * Updates the ctag for a task list.
     *
     * @param id The task list ID
     * @param ctag The new ctag value
     */
    @Query("UPDATE task_lists SET ctag = :ctag WHERE id = :id")
    suspend fun updateCTag(id: Long, ctag: String)
    
    /**
     * Updates the last synced timestamp.
     *
     * @param id The task list ID
     * @param timestamp The timestamp
     */
    @Query("UPDATE task_lists SET last_synced = :timestamp WHERE id = :id")
    suspend fun updateLastSynced(id: Long, timestamp: Long)
    
    /**
     * Toggles sync enabled for a task list.
     *
     * @param id The task list ID
     * @param enabled Whether sync should be enabled
     */
    @Query("UPDATE task_lists SET sync_enabled = :enabled WHERE id = :id")
    suspend fun setSyncEnabled(id: Long, enabled: Boolean)
    
    /**
     * Toggles visibility for a task list.
     *
     * @param id The task list ID
     * @param visible Whether task list should be visible
     */
    @Query("UPDATE task_lists SET visible = :visible WHERE id = :id")
    suspend fun setVisible(id: Long, visible: Boolean)
}
