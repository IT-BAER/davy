package com.davy.data.repository

import com.davy.data.local.dao.TaskListDao
import com.davy.data.local.entity.TaskListEntity
import com.davy.domain.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Repository for task list operations.
 * 
 * Handles CRUD operations and provides reactive data streams for task lists.
 */
class TaskListRepository @Inject constructor(
    private val taskListDao: TaskListDao
) {
    
    /**
     * Insert a new task list.
     * 
     * @return The ID of the inserted task list
     */
    suspend fun insert(taskList: TaskList): Long {
        return taskListDao.insert(taskList.toEntity())
    }
    
    /**
     * Insert multiple task lists.
     * 
     * @return List of IDs of inserted task lists
     */
    suspend fun insertAll(taskLists: List<TaskList>): List<Long> {
        return taskListDao.insertAll(taskLists.map { it.toEntity() })
    }
    
    /**
     * Update an existing task list.
     */
    suspend fun update(taskList: TaskList) {
        taskListDao.update(taskList.toEntity())
    }
    
    /**
     * Delete a task list.
     */
    suspend fun delete(taskList: TaskList) {
        taskListDao.delete(taskList.toEntity())
    }
    
    /**
     * Delete a task list by ID.
     */
    suspend fun deleteById(id: Long) {
        taskListDao.deleteById(id)
    }
    
    /**
     * Get all task lists as Flow.
     */
    fun getAllTaskLists(): Flow<List<TaskList>> {
        return taskListDao.getAllTaskLists().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get task lists for a specific account as Flow.
     */
    fun getByAccountId(accountId: Long): Flow<List<TaskList>> {
        return taskListDao.getByAccountId(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get a specific task list by ID.
     */
    suspend fun getById(id: Long): TaskList? {
        return taskListDao.getById(id)?.toDomain()
    }
    
    /**
     * Get a task list by URL.
     */
    suspend fun getByUrl(url: String): TaskList? {
        return taskListDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get all sync-enabled task lists.
     */
    suspend fun getSyncEnabled(): List<TaskList> {
        return taskListDao.getSyncEnabled().map { it.toDomain() }
    }
    
    /**
     * Convert TaskList domain model to TaskListEntity.
     */
    private fun TaskList.toEntity(): TaskListEntity {
        return TaskListEntity(
            id = id,
            accountId = accountId,
            url = url,
            displayName = displayName,
            color = color,
            ctag = ctag,
            syncEnabled = syncEnabled,
            visible = visible,
            lastSynced = lastSynced
        )
    }
    
    /**
     * Convert TaskListEntity to TaskList domain model.
     */
    private fun TaskListEntity.toDomain(): TaskList {
        return TaskList(
            id = id,
            accountId = accountId,
            url = url,
            displayName = displayName,
            color = color,
            ctag = ctag,
            syncEnabled = syncEnabled,
            visible = visible,
            lastSynced = lastSynced
        )
    }
}
