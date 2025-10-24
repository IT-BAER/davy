package com.davy.data.repository

import com.davy.data.local.dao.TaskListDao
import com.davy.data.local.entity.TaskListEntity
import com.davy.domain.model.TaskList
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
 * Unit tests for TaskListRepository.
 * 
 * Tests CRUD operations, sync state management, and Flow emissions
 * for task list data access.
 */
class TaskListRepositoryTest {

    private lateinit var taskListDao: TaskListDao
    private lateinit var repository: TaskListRepository

    // Test fixtures
    private val testTaskListEntity = TaskListEntity(
        id = 1L,
        accountId = 100L,
        url = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/",
        displayName = "Personal Tasks",
        color = "#FF5722",
        syncEnabled = true,
        visible = true,
        ctag = "ctag-123",
        lastSynced = 1705315800000L // 2024-01-15T10:30:00Z in milliseconds
    )

    private val testTaskList = TaskList(
        id = 1L,
        accountId = 100L,
        url = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/",
        displayName = "Personal Tasks",
        color = "#FF5722",
        syncEnabled = true,
        visible = true,
        ctag = "ctag-123",
        lastSynced = 1705315800000L
    )

    @Before
    fun setup() {
        taskListDao = mockk(relaxed = true)
        repository = TaskListRepository(taskListDao)
    }

    @Test
    fun `getAllTaskLists returns all task lists as Flow`() = runTest {
        // Given
        val taskLists = listOf(
            testTaskListEntity,
            testTaskListEntity.copy(
                id = 2L,
                displayName = "Work Tasks",
                url = "https://nextcloud.example.com/remote.php/dav/calendars/user/work/"
            )
        )
        coEvery { taskListDao.getAllTaskLists() } returns flowOf(taskLists)

        // When
        val result = repository.getAllTaskLists().first()

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].displayName).isEqualTo("Personal Tasks")
        assertThat(result[1].displayName).isEqualTo("Work Tasks")
    }

    @Test
    fun `getAllTaskLists maps entities to domain models correctly`() = runTest {
        // Given
        coEvery { taskListDao.getAllTaskLists() } returns flowOf(listOf(testTaskListEntity))

        // When
        val result = repository.getAllTaskLists().first()

        // Then
        val taskList = result.first()
        assertThat(taskList.id).isEqualTo(testTaskListEntity.id)
        assertThat(taskList.accountId).isEqualTo(testTaskListEntity.accountId)
        assertThat(taskList.url).isEqualTo(testTaskListEntity.url)
        assertThat(taskList.displayName).isEqualTo(testTaskListEntity.displayName)
        assertThat(taskList.color).isEqualTo(testTaskListEntity.color)
        assertThat(taskList.syncEnabled).isEqualTo(testTaskListEntity.syncEnabled)
        assertThat(taskList.visible).isEqualTo(testTaskListEntity.visible)
        assertThat(taskList.ctag).isEqualTo(testTaskListEntity.ctag)
        assertThat(taskList.lastSynced).isEqualTo(testTaskListEntity.lastSynced)
    }

    @Test
    fun `getByAccountId returns task lists for specific account`() = runTest {
        // Given
        val accountId = 100L
        val taskLists = listOf(testTaskListEntity)
        coEvery { taskListDao.getByAccountId(accountId) } returns flowOf(taskLists)

        // When
        val result = repository.getByAccountId(accountId).first()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first().accountId).isEqualTo(accountId)
        coVerify { taskListDao.getByAccountId(accountId) }
    }

    @Test
    fun `getById returns single task list`() = runTest {
        // Given
        val taskListId = 1L
        coEvery { taskListDao.getById(taskListId) } returns testTaskListEntity

        // When
        val result = repository.getById(taskListId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(taskListId)
        assertThat(result?.displayName).isEqualTo("Personal Tasks")
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        // Given
        val taskListId = 999L
        coEvery { taskListDao.getById(taskListId) } returns null

        // When
        val result = repository.getById(taskListId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getByUrl returns task list matching URL`() = runTest {
        // Given
        val url = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/"
        coEvery { taskListDao.getByUrl(url) } returns testTaskListEntity

        // When
        val result = repository.getByUrl(url)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.url).isEqualTo(url)
    }

    @Test
    fun `insert inserts entity and returns ID`() = runTest {
        // Given
        val newId = 42L
        coEvery { taskListDao.insert(any()) } returns newId

        // When
        val result = repository.insert(testTaskList)

        // Then
        assertThat(result).isEqualTo(newId)
        coVerify {
            taskListDao.insert(match {
                it.accountId == testTaskList.accountId &&
                it.displayName == testTaskList.displayName &&
                it.url == testTaskList.url
            })
        }
    }

    @Test
    fun `insertAll inserts multiple task lists and returns IDs`() = runTest {
        // Given
        val taskLists = listOf(
            testTaskList,
            testTaskList.copy(id = 2L, displayName = "Work Tasks")
        )
        val expectedIds = listOf(1L, 2L)
        coEvery { taskListDao.insertAll(any()) } returns expectedIds

        // When
        val result = repository.insertAll(taskLists)

        // Then
        assertThat(result).isEqualTo(expectedIds)
        coVerify { taskListDao.insertAll(match { it.size == 2 }) }
    }

    @Test
    fun `update updates existing task list`() = runTest {
        // Given
        val updatedTaskList = testTaskList.copy(
            displayName = "Updated Personal Tasks",
            color = "#2196F3"
        )

        // When
        repository.update(updatedTaskList)

        // Then
        coVerify {
            taskListDao.update(match {
                it.id == updatedTaskList.id &&
                it.displayName == "Updated Personal Tasks" &&
                it.color == "#2196F3"
            })
        }
    }

    @Test
    fun `delete removes task list from database`() = runTest {
        // Given
        val taskList = testTaskList

        // When
        repository.delete(taskList)

        // Then
        coVerify { taskListDao.delete(match { it.id == taskList.id }) }
    }

    @Test
    fun `deleteById removes task list by ID`() = runTest {
        // Given
        val taskListId = 1L

        // When
        repository.deleteById(taskListId)

        // Then
        coVerify { taskListDao.deleteById(taskListId) }
    }

    @Test
    fun `getSyncEnabled returns only sync-enabled task lists`() = runTest {
        // Given
        val enabledTaskLists = listOf(
            testTaskListEntity,
            testTaskListEntity.copy(id = 2L, displayName = "Work Tasks")
        )
        coEvery { taskListDao.getSyncEnabled() } returns enabledTaskLists

        // When
        val result = repository.getSyncEnabled()

        // Then
        assertThat(result).hasSize(2)
        result.forEach { taskList ->
            assertThat(taskList.syncEnabled).isTrue()
        }
    }

    @Test
    fun `domain model isSynced returns true when lastSynced is not null`() = runTest {
        // Given
        val syncedTaskList = testTaskList.copy(lastSynced = 1705315800000L)
        val unsyncedTaskList = testTaskList.copy(lastSynced = null)

        // When/Then
        assertThat(syncedTaskList.isSynced()).isTrue()
        assertThat(unsyncedTaskList.isSynced()).isFalse()
    }

    @Test
    fun `domain model needsSync returns true when ctag or lastSynced is null`() = runTest {
        // Given
        val fullySyncedTaskList = testTaskList.copy(
            ctag = "ctag-123",
            lastSynced = 1705315800000L
        )
        val noCtagTaskList = testTaskList.copy(ctag = null, lastSynced = 1705315800000L)
        val notSyncedTaskList = testTaskList.copy(ctag = "ctag-123", lastSynced = null)

        // When/Then
        assertThat(fullySyncedTaskList.needsSync()).isFalse()
        assertThat(noCtagTaskList.needsSync()).isTrue()
        assertThat(notSyncedTaskList.needsSync()).isTrue()
    }
}
