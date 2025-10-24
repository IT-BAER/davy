package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Task entities.
 *
 * Provides CRUD operations and queries for tasks.
 */
@Dao
interface TaskDao {
    
    /**
     * Inserts a new task.
     *
     * @param task The task to insert
     * @return The ID of the inserted task
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long
    
    /**
     * Inserts multiple tasks.
     *
     * @param tasks The tasks to insert
     * @return List of inserted task IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>
    
    /**
     * Updates an existing task.
     *
     * @param task The task to update
     */
    @Update
    suspend fun update(task: TaskEntity)
    
    /**
     * Deletes a task.
     *
     * @param task The task to delete
     */
    @Delete
    suspend fun delete(task: TaskEntity)
    
    /**
     * Deletes a task by ID.
     *
     * @param id The task ID
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * Gets all tasks for a task list as Flow.
     *
     * @param taskListId The task list ID
     * @return Flow of tasks for the task list
     */
    @Query("SELECT * FROM tasks WHERE task_list_id = :taskListId AND deleted = 0 ORDER BY due ASC, summary ASC")
    fun getByTaskListId(taskListId: Long): Flow<List<TaskEntity>>
    
    /**
     * Gets a specific task by ID.
     *
     * @param id The task ID
     * @return The task or null if not found
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?
    
    /**
     * Gets a task by URL.
     *
     * @param url The task URL
     * @return The task or null if not found
     */
    @Query("SELECT * FROM tasks WHERE url = :url")
    suspend fun getByUrl(url: String): TaskEntity?
    
    /**
     * Gets a task by UID.
     *
     * @param uid The task UID
     * @return The task or null if not found
     */
    @Query("SELECT * FROM tasks WHERE uid = :uid")
    suspend fun getByUid(uid: String): TaskEntity?
    
    /**
     * Searches tasks by summary.
     *
     * @param taskListId The task list ID
     * @param query The search query
     * @return Flow of matching tasks
     */
    @Query("SELECT * FROM tasks WHERE task_list_id = :taskListId AND deleted = 0 AND summary LIKE '%' || :query || '%' ORDER BY due ASC, summary ASC")
    fun search(taskListId: Long, query: String): Flow<List<TaskEntity>>
    
    /**
     * Gets all dirty tasks (pending upload).
     *
     * @return List of dirty tasks
     */
    @Query("SELECT * FROM tasks WHERE dirty = 1 AND deleted = 0")
    suspend fun getDirtyTasks(): List<TaskEntity>
    
    /**
     * Gets all deleted tasks (pending deletion on server).
     *
     * @return List of deleted tasks
     */
    @Query("SELECT * FROM tasks WHERE deleted = 1")
    suspend fun getDeletedTasks(): List<TaskEntity>
    
    /**
     * Gets tasks by status.
     *
     * @param taskListId The task list ID
     * @param status The task status
     * @return Flow of tasks with the status
     */
    @Query("SELECT * FROM tasks WHERE task_list_id = :taskListId AND status = :status AND deleted = 0 ORDER BY due ASC, summary ASC")
    fun getByStatus(taskListId: Long, status: String): Flow<List<TaskEntity>>
    
    /**
     * Gets completed tasks.
     *
     * @param taskListId The task list ID
     * @return Flow of completed tasks
     */
    @Query("SELECT * FROM tasks WHERE task_list_id = :taskListId AND status = 'COMPLETED' AND deleted = 0 ORDER BY completed DESC")
    fun getCompletedTasks(taskListId: Long): Flow<List<TaskEntity>>
    
    /**
     * Gets tasks due before a specific date.
     *
     * @param taskListId The task list ID
     * @param dueDate The due date timestamp
     * @return Flow of tasks due before the date
     */
    @Query("SELECT * FROM tasks WHERE task_list_id = :taskListId AND due <= :dueDate AND status != 'COMPLETED' AND deleted = 0 ORDER BY due ASC")
    fun getTasksDueBefore(taskListId: Long, dueDate: Long): Flow<List<TaskEntity>>
    
    /**
     * Gets subtasks for a parent task.
     *
     * @param parentTaskId The parent task ID
     * @return Flow of subtasks
     */
    @Query("SELECT * FROM tasks WHERE parent_task_id = :parentTaskId AND deleted = 0 ORDER BY due ASC, summary ASC")
    fun getSubtasks(parentTaskId: Long): Flow<List<TaskEntity>>
    
    /**
     * Marks a task as dirty (pending upload).
     *
     * @param id The task ID
     */
    @Query("UPDATE tasks SET dirty = 1 WHERE id = :id")
    suspend fun markDirty(id: Long)
    
    /**
     * Marks a task as clean (uploaded).
     *
     * @param id The task ID
     */
    @Query("UPDATE tasks SET dirty = 0 WHERE id = :id")
    suspend fun markClean(id: Long)
    
    /**
     * Soft deletes a task (marks for deletion).
     *
     * @param id The task ID
     */
    @Query("UPDATE tasks SET deleted = 1, dirty = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)
    
    /**
     * Updates task ETag.
     *
     * @param id The task ID
     * @param etag The new ETag value
     */
    @Query("UPDATE tasks SET etag = :etag WHERE id = :id")
    suspend fun updateETag(id: Long, etag: String)
    
    /**
     * Updates task status.
     *
     * @param id The task ID
     * @param status The new status
     */
    @Query("UPDATE tasks SET status = :status, dirty = 1 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
    
    /**
     * Updates task completion.
     *
     * @param id The task ID
     * @param percentComplete The completion percentage
     * @param completed The completion timestamp (null if not completed)
     */
    @Query("UPDATE tasks SET percent_complete = :percentComplete, completed = :completed, dirty = 1 WHERE id = :id")
    suspend fun updateCompletion(id: Long, percentComplete: Int, completed: Long?)
}
