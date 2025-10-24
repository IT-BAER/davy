package com.davy.data.repository

import com.davy.data.local.dao.TaskDao
import com.davy.data.local.entity.TaskEntity
import com.davy.domain.model.Task
import com.davy.domain.model.TaskPriority
import com.davy.domain.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TaskRepository.
 * 
 * Tests CRUD operations, query functionality, sync state management,
 * and Flow emissions for task data access.
 */
class TaskRepositoryTest {

    private lateinit var taskDao: TaskDao
    private lateinit var repository: TaskRepository

    // Test fixtures
    private val testTaskEntity = TaskEntity(
        id = 1L,
        taskListId = 100L,
        url = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics",
        uid = "task-uid-001",
        etag = "etag-123",
        summary = "Complete project documentation",
        description = "Write comprehensive documentation for the DAVy project",
        status = TaskStatus.IN_PROGRESS.name,
        priority = TaskPriority.HIGH.value,
        percentComplete = 50,
        due = 1706745599000L, // 2024-01-31T23:59:59Z in milliseconds
        dtStart = null,
        completed = null,
        created = 1704067200000L, // 2024-01-01T00:00:00Z in milliseconds
        lastModified = 1705315800000L, // 2024-01-15T10:30:00Z in milliseconds
        rrule = null,
        parentTaskId = null,
        location = null,
        categories = null,
        dirty = false,
        deleted = false
    )

    private val testTask = Task(
        id = 1L,
        taskListId = 100L,
        url = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics",
        uid = "task-uid-001",
        etag = "etag-123",
        summary = "Complete project documentation",
        description = "Write comprehensive documentation for the DAVy project",
        status = TaskStatus.IN_PROGRESS,
        priority = TaskPriority.HIGH,
        percentComplete = 50,
        due = 1706745599000L,
        dtStart = null,
        completed = null,
        created = 1704067200000L,
        lastModified = 1705315800000L,
        rrule = null,
        parentTaskId = null,
        location = null,
        categories = emptyList(),
        dirty = false,
        deleted = false
    )

    @Before
    fun setup() {
        taskDao = mockk(relaxed = true)
        repository = TaskRepository(taskDao)
    }

    @Test
    fun `getByTaskListId returns tasks for specific task list as Flow`() = runTest {
        // Given
        val taskListId = 100L
        val tasks = listOf(
            testTaskEntity,
            testTaskEntity.copy(
                id = 2L,
                uid = "task-uid-002",
                summary = "Review code changes"
            )
        )
        coEvery { taskDao.getByTaskListId(taskListId) } returns flowOf(tasks)

        // When
        val result = repository.getByTaskListId(taskListId).first()

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].summary).isEqualTo("Complete project documentation")
        assertThat(result[1].summary).isEqualTo("Review code changes")
        assertThat(result.first().taskListId).isEqualTo(taskListId)
        coVerify { taskDao.getByTaskListId(taskListId) }
    }

    @Test
    fun `getByTaskListId maps entities to domain models correctly`() = runTest {
        // Given
        val taskListId = 100L
        coEvery { taskDao.getByTaskListId(taskListId) } returns flowOf(listOf(testTaskEntity))

        // When
        val result = repository.getByTaskListId(taskListId).first()

        // Then
        val task = result.first()
        assertThat(task.id).isEqualTo(testTaskEntity.id)
        assertThat(task.taskListId).isEqualTo(testTaskEntity.taskListId)
        assertThat(task.url).isEqualTo(testTaskEntity.url)
        assertThat(task.uid).isEqualTo(testTaskEntity.uid)
        assertThat(task.summary).isEqualTo(testTaskEntity.summary)
        assertThat(task.description).isEqualTo(testTaskEntity.description)
        assertThat(task.status).isEqualTo(TaskStatus.IN_PROGRESS)
        assertThat(task.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(task.percentComplete).isEqualTo(testTaskEntity.percentComplete)
        assertThat(task.etag).isEqualTo(testTaskEntity.etag)
    }

    @Test
    fun `getById returns single task`() = runTest {
        // Given
        val taskId = 1L
        coEvery { taskDao.getById(taskId) } returns testTaskEntity

        // When
        val result = repository.getById(taskId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(taskId)
        assertThat(result?.summary).isEqualTo("Complete project documentation")
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        // Given
        val taskId = 999L
        coEvery { taskDao.getById(taskId) } returns null

        // When
        val result = repository.getById(taskId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getByUrl returns task matching URL`() = runTest {
        // Given
        val url = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics"
        coEvery { taskDao.getByUrl(url) } returns testTaskEntity

        // When
        val result = repository.getByUrl(url)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.url).isEqualTo(url)
    }

    @Test
    fun `getByUid returns task matching UID`() = runTest {
        // Given
        val uid = "task-uid-001"
        coEvery { taskDao.getByUid(uid) } returns testTaskEntity

        // When
        val result = repository.getByUid(uid)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.uid).isEqualTo(uid)
    }

    @Test
    fun `insert inserts entity and returns ID`() = runTest {
        // Given
        val newId = 42L
        coEvery { taskDao.insert(any()) } returns newId

        // When
        val result = repository.insert(testTask)

        // Then
        assertThat(result).isEqualTo(newId)
        coVerify {
            taskDao.insert(match {
                it.taskListId == testTask.taskListId &&
                it.uid == testTask.uid &&
                it.summary == testTask.summary
            })
        }
    }

    @Test
    fun `insertAll batch inserts multiple tasks and returns IDs`() = runTest {
        // Given
        val tasks = listOf(
            testTask,
            testTask.copy(id = 0L, uid = "task-uid-002", summary = "Second task")
        )
        val expectedIds = listOf(1L, 2L)
        coEvery { taskDao.insertAll(any()) } returns expectedIds

        // When
        val result = repository.insertAll(tasks)

        // Then
        assertThat(result).isEqualTo(expectedIds)
        coVerify { taskDao.insertAll(match { it.size == 2 }) }
    }

    @Test
    fun `update updates existing task`() = runTest {
        // Given
        val updatedTask = testTask.copy(
            summary = "Updated documentation task",
            percentComplete = 75,
            status = TaskStatus.IN_PROGRESS
        )

        // When
        repository.update(updatedTask)

        // Then
        coVerify {
            taskDao.update(match {
                it.id == updatedTask.id &&
                it.summary == "Updated documentation task" &&
                it.percentComplete == 75
            })
        }
    }

    @Test
    fun `delete removes task from database`() = runTest {
        // Given
        val task = testTask

        // When
        repository.delete(task)

        // Then
        coVerify { taskDao.delete(match { it.id == task.id }) }
    }

    @Test
    fun `deleteById removes task by ID`() = runTest {
        // Given
        val taskId = 1L

        // When
        repository.deleteById(taskId)

        // Then
        coVerify { taskDao.deleteById(taskId) }
    }

    @Test
    fun `search returns tasks matching query`() = runTest {
        // Given
        val taskListId = 100L
        val query = "documentation"
        val matchingTasks = listOf(testTaskEntity)
        coEvery { taskDao.search(taskListId, "%$query%") } returns flowOf(matchingTasks)

        // When
        val result = repository.search(taskListId, query).first()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first().summary).contains(query)
        coVerify { taskDao.search(taskListId, "%$query%") }
    }

    @Test
    fun `getDirtyTasks returns only tasks with dirty flag true`() = runTest {
        // Given
        val dirtyTasks = listOf(
            testTaskEntity.copy(dirty = true),
            testTaskEntity.copy(id = 2L, dirty = true)
        )
        coEvery { taskDao.getDirtyTasks() } returns dirtyTasks

        // When
        val result = repository.getDirtyTasks()

        // Then
        assertThat(result).hasSize(2)
        result.forEach { task ->
            assertThat(task.dirty).isTrue()
        }
    }

    @Test
    fun `getDeletedTasks returns only tasks with deleted flag true`() = runTest {
        // Given
        val deletedTasks = listOf(
            testTaskEntity.copy(deleted = true),
            testTaskEntity.copy(id = 2L, deleted = true)
        )
        coEvery { taskDao.getDeletedTasks() } returns deletedTasks

        // When
        val result = repository.getDeletedTasks()

        // Then
        assertThat(result).hasSize(2)
        result.forEach { task ->
            assertThat(task.deleted).isTrue()
        }
    }

    @Test
    fun `getByStatus returns tasks with matching status`() = runTest {
        // Given
        val taskListId = 100L
        val status = TaskStatus.IN_PROGRESS
        val tasks = listOf(testTaskEntity)
        coEvery { taskDao.getByStatus(taskListId, status.name) } returns flowOf(tasks)

        // When
        val result = repository.getByStatus(taskListId, status).first()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first().status).isEqualTo(status)
        coVerify { taskDao.getByStatus(taskListId, status.name) }
    }

    @Test
    fun `getCompletedTasks returns tasks with COMPLETED status`() = runTest {
        // Given
        val taskListId = 100L
        val completedTask = testTaskEntity.copy(
            status = TaskStatus.COMPLETED.name,
            completed = 1705748400000L, // 2024-01-20T10:00:00Z
            percentComplete = 100
        )
        coEvery { taskDao.getCompletedTasks(taskListId) } returns flowOf(listOf(completedTask))

        // When
        val result = repository.getCompletedTasks(taskListId).first()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first().status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(result.first().completed).isNotNull()
        assertThat(result.first().percentComplete).isEqualTo(100)
    }

    @Test
    fun `getTasksDueBefore returns tasks due before specified date`() = runTest {
        // Given
        val taskListId = 100L
        val dueDate = 1706745599000L // 2024-01-31T23:59:59Z
        val tasks = listOf(testTaskEntity)
        coEvery { taskDao.getTasksDueBefore(taskListId, dueDate) } returns flowOf(tasks)

        // When
        val result = repository.getTasksDueBefore(taskListId, dueDate).first()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first().due).isLessThan(dueDate + 1) // due date or earlier
        coVerify { taskDao.getTasksDueBefore(taskListId, dueDate) }
    }

    @Test
    fun `domain model isCompleted returns true when status is COMPLETED`() = runTest {
        // Given
        val completedTask = testTask.copy(status = TaskStatus.COMPLETED)
        val incompleteTask = testTask.copy(status = TaskStatus.IN_PROGRESS)

        // When/Then
        assertThat(completedTask.isCompleted()).isTrue()
        assertThat(incompleteTask.isCompleted()).isFalse()
    }

    @Test
    fun `domain model isOverdue returns true when due date is in past and not completed`() = runTest {
        // Given
        val pastDue = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days ago
        val overdueTask = testTask.copy(
            due = pastDue,
            status = TaskStatus.IN_PROGRESS
        )
        val overdueButCompletedTask = testTask.copy(
            due = pastDue,
            status = TaskStatus.COMPLETED
        )

        // When/Then
        assertThat(overdueTask.isOverdue()).isTrue()
        assertThat(overdueButCompletedTask.isOverdue()).isFalse()
    }

    @Test
    fun `categories mapping handles empty and populated lists`() = runTest {
        // Given
        val taskWithCategories = testTaskEntity.copy(categories = "work,urgent,documentation")
        val taskWithoutCategories = testTaskEntity.copy(categories = null)
        val taskWithEmptyCategories = testTaskEntity.copy(categories = "")

        coEvery { taskDao.getById(1L) } returns taskWithCategories
        coEvery { taskDao.getById(2L) } returns taskWithoutCategories
        coEvery { taskDao.getById(3L) } returns taskWithEmptyCategories

        // When
        val result1 = repository.getById(1L)
        val result2 = repository.getById(2L)
        val result3 = repository.getById(3L)

        // Then
        assertThat(result1?.categories).containsExactly("work", "urgent", "documentation")
        assertThat(result2?.categories).isEmpty()
        assertThat(result3?.categories).isEmpty()
    }
}
