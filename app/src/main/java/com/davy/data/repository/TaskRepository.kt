package com.davy.data.repository

import com.davy.data.local.dao.TaskDao
import com.davy.data.local.entity.TaskEntity
import com.davy.domain.model.Task
import com.davy.domain.model.TaskPriority
import com.davy.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Repository for task operations.
 * 
 * Handles CRUD operations and provides reactive data streams for tasks.
 */
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    
    /**
     * Insert a new task.
     * 
     * @return The ID of the inserted task
     */
    suspend fun insert(task: Task): Long {
        return taskDao.insert(task.toEntity())
    }
    
    /**
     * Insert multiple tasks.
     * 
     * @return List of IDs of inserted tasks
     */
    suspend fun insertAll(tasks: List<Task>): List<Long> {
        return taskDao.insertAll(tasks.map { it.toEntity() })
    }
    
    /**
     * Update an existing task.
     */
    suspend fun update(task: Task) {
        taskDao.update(task.toEntity())
    }
    
    /**
     * Delete a task.
     */
    suspend fun delete(task: Task) {
        taskDao.delete(task.toEntity())
    }
    
    /**
     * Delete a task by ID.
     */
    suspend fun deleteById(id: Long) {
        taskDao.deleteById(id)
    }
    
    /**
     * Get all tasks for a task list as Flow.
     */
    fun getByTaskListId(taskListId: Long): Flow<List<Task>> {
        return taskDao.getByTaskListId(taskListId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get a specific task by ID.
     */
    suspend fun getById(id: Long): Task? {
        return taskDao.getById(id)?.toDomain()
    }
    
    /**
     * Get a task by URL.
     */
    suspend fun getByUrl(url: String): Task? {
        return taskDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get a task by UID.
     */
    suspend fun getByUid(uid: String): Task? {
        return taskDao.getByUid(uid)?.toDomain()
    }
    
    /**
     * Search tasks by summary.
     */
    fun search(taskListId: Long, query: String): Flow<List<Task>> {
        return taskDao.search(taskListId, query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all dirty tasks (pending upload).
     */
    suspend fun getDirtyTasks(): List<Task> {
        return taskDao.getDirtyTasks().map { it.toDomain() }
    }
    
    /**
     * Get all deleted tasks (pending deletion on server).
     */
    suspend fun getDeletedTasks(): List<Task> {
        return taskDao.getDeletedTasks().map { it.toDomain() }
    }
    
    /**
     * Get tasks by status.
     */
    fun getByStatus(taskListId: Long, status: TaskStatus): Flow<List<Task>> {
        return taskDao.getByStatus(taskListId, status.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get completed tasks.
     */
    fun getCompletedTasks(taskListId: Long): Flow<List<Task>> {
        return taskDao.getCompletedTasks(taskListId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get tasks due before a specific date.
     */
    fun getTasksDueBefore(taskListId: Long, dueDate: Long): Flow<List<Task>> {
        return taskDao.getTasksDueBefore(taskListId, dueDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Convert Task domain model to TaskEntity.
     */
    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            taskListId = taskListId,
            url = url,
            uid = uid,
            etag = etag,
            summary = summary,
            description = description,
            status = status.name,
            priority = priority.value,
            percentComplete = percentComplete,
            due = due,
            dtStart = dtStart,
            completed = completed,
            created = created,
            lastModified = lastModified,
            rrule = rrule,
            parentTaskId = parentTaskId,
            location = location,
            categories = categories.joinToString(","),
            dirty = dirty,
            deleted = deleted
        )
    }
    
    /**
     * Convert TaskEntity to Task domain model.
     */
    private fun TaskEntity.toDomain(): Task {
        return Task(
            id = id,
            taskListId = taskListId,
            url = url,
            uid = uid,
            etag = etag,
            summary = summary,
            description = description,
            status = status?.let { TaskStatus.valueOf(it) } ?: TaskStatus.NEEDS_ACTION,
            priority = priority?.let { TaskPriority.fromValue(it) } ?: TaskPriority.UNDEFINED,
            percentComplete = percentComplete,
            due = due,
            dtStart = dtStart,
            completed = completed,
            created = created,
            lastModified = lastModified,
            rrule = rrule,
            parentTaskId = parentTaskId,
            location = location,
            categories = categories?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            dirty = dirty,
            deleted = deleted
        )
    }
}
