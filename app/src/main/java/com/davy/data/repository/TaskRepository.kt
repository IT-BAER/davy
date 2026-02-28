package com.davy.data.repository

import com.davy.data.local.dao.TaskAlarmDao
import com.davy.data.local.dao.TaskDao
import com.davy.data.local.entity.TaskAlarmEntity
import com.davy.data.local.entity.TaskEntity
import com.davy.domain.model.AlarmAction
import com.davy.domain.model.AlarmRelativeTo
import com.davy.domain.model.Task
import com.davy.domain.model.TaskAlarm
import com.davy.domain.model.TaskClassification
import com.davy.domain.model.TaskPriority
import com.davy.domain.model.TaskStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository for task operations.
 * 
 * Handles CRUD operations and provides reactive data streams for tasks.
 */
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val taskAlarmDao: TaskAlarmDao
) {
    
    private val gson = Gson()
    private val longListType = object : TypeToken<List<Long>>() {}.type
    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type
    
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
     * Includes loading alarms for each task.
     */
    fun getByTaskListId(taskListId: Long): Flow<List<Task>> {
        return taskDao.getByTaskListId(taskListId).map { entities ->
            entities.map { entity ->
                val alarms = taskAlarmDao.getAlarmsForTaskSync(entity.id).map { it.toDomain() }
                Timber.d("Loading task '${entity.summary}' (id=${entity.id}): ${alarms.size} alarms from DB")
                entity.toDomain(alarms)
            }
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
            parentTaskUid = parentTaskUid,
            location = location,
            geoLat = geoLat,
            geoLng = geoLng,
            taskColor = taskColor,
            todoUrl = todoUrl,
            organizer = organizer,
            sequence = sequence,
            duration = duration,
            comment = comment,
            isAllDay = isAllDay,
            timezone = timezone,
            categories = categories.joinToString(","),
            classification = classification.toICalValue(),
            exdates = if (exdates.isNotEmpty()) gson.toJson(exdates) else null,
            rdates = if (rdates.isNotEmpty()) gson.toJson(rdates) else null,
            unknownProperties = if (unknownProperties.isNotEmpty()) gson.toJson(unknownProperties) else null,
            dirty = dirty,
            deleted = deleted
        )
    }
    
    /**
     * Convert TaskEntity to Task domain model.
     * Note: Alarms need to be loaded separately via loadAlarmsForTask().
     */
    private fun TaskEntity.toDomain(alarms: List<TaskAlarm> = emptyList()): Task {
        return Task(
            id = id,
            taskListId = taskListId,
            url = url,
            uid = uid,
            etag = etag,
            summary = summary,
            description = description,
            status = status?.let { TaskStatus.fromValue(it) } ?: TaskStatus.NEEDS_ACTION,
            priority = priority?.let { TaskPriority.fromValue(it) } ?: TaskPriority.UNDEFINED,
            percentComplete = percentComplete,
            due = due,
            dtStart = dtStart,
            completed = completed,
            created = created,
            lastModified = lastModified,
            rrule = rrule,
            parentTaskId = parentTaskId,
            parentTaskUid = parentTaskUid,
            location = location,
            geoLat = geoLat,
            geoLng = geoLng,
            taskColor = taskColor,
            todoUrl = todoUrl,
            organizer = organizer,
            sequence = sequence,
            duration = duration,
            comment = comment,
            isAllDay = isAllDay,
            timezone = timezone,
            categories = categories?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            classification = classification?.let { TaskClassification.fromValue(it) } ?: TaskClassification.PUBLIC,
            exdates = exdates?.let { runCatching { gson.fromJson<List<Long>>(it, longListType) }.getOrNull() } ?: emptyList(),
            rdates = rdates?.let { runCatching { gson.fromJson<List<Long>>(it, longListType) }.getOrNull() } ?: emptyList(),
            alarms = alarms,
            unknownProperties = unknownProperties?.let { runCatching { gson.fromJson<Map<String, String>>(it, stringMapType) }.getOrNull() } ?: emptyMap(),
            dirty = dirty,
            deleted = deleted
        )
    }
    
    /**
     * Convert TaskAlarm domain model to TaskAlarmEntity.
     */
    private fun TaskAlarm.toEntity(): TaskAlarmEntity {
        return TaskAlarmEntity(
            id = id,
            taskId = taskId,
            action = action.name,
            triggerMinutesBefore = triggerMinutesBefore,
            triggerAbsolute = triggerAbsolute,
            triggerRelativeTo = triggerRelativeTo.name,
            description = description,
            summary = summary
        )
    }
    
    /**
     * Convert TaskAlarmEntity to TaskAlarm domain model.
     */
    private fun TaskAlarmEntity.toDomain(): TaskAlarm {
        return TaskAlarm(
            id = id,
            taskId = taskId,
            action = AlarmAction.fromValue(action),
            triggerMinutesBefore = triggerMinutesBefore,
            triggerAbsolute = triggerAbsolute,
            triggerRelativeTo = AlarmRelativeTo.fromValue(triggerRelativeTo),
            description = description,
            summary = summary
        )
    }
    
    /**
     * Load alarms for a task.
     */
    suspend fun loadAlarmsForTask(taskId: Long): List<TaskAlarm> {
        return taskAlarmDao.getAlarmsForTaskSync(taskId).map { it.toDomain() }
    }
    
    /**
     * Save alarms for a task.
     * Replaces all existing alarms with the new ones.
     */
    suspend fun saveAlarmsForTask(taskId: Long, alarms: List<TaskAlarm>) {
        taskAlarmDao.deleteAlarmsForTask(taskId)
        if (alarms.isNotEmpty()) {
            val entities = alarms.map { it.copy(taskId = taskId).toEntity() }
            taskAlarmDao.insertAll(entities)
        }
    }
    
    /**
     * Get a task with its alarms loaded.
     */
    suspend fun getByIdWithAlarms(id: Long): Task? {
        val entity = taskDao.getById(id) ?: return null
        val alarms = loadAlarmsForTask(id)
        return entity.toDomain(alarms)
    }
    
    /**
     * Insert a task with its alarms.
     */
    suspend fun insertWithAlarms(task: Task): Long {
        val taskId = taskDao.insert(task.toEntity())
        if (task.alarms.isNotEmpty()) {
            saveAlarmsForTask(taskId, task.alarms)
        }
        return taskId
    }
    
    /**
     * Update a task with its alarms.
     */
    suspend fun updateWithAlarms(task: Task) {
        taskDao.update(task.toEntity())
        saveAlarmsForTask(task.id, task.alarms)
    }
}
