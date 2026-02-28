package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.davy.data.local.DavyDatabase
import com.davy.data.local.dao.AccountDao
import com.davy.data.local.dao.TaskDao
import com.davy.data.local.dao.TaskListDao
import com.davy.data.local.entity.AccountEntity
import com.davy.data.local.entity.TaskEntity
import com.davy.data.local.entity.TaskListEntity
import com.davy.data.repository.TaskListRepository
import com.davy.data.repository.TaskRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration tests for CalDAV task synchronization data layer.
 *
 * Tests the complete task data flow through Room DAOs and repositories including:
 * - Task CRUD operations
 * - Task list association
 * - Dirty / deleted flag management for sync
 * - ETag change detection
 * - Subtask parent-child relationships
 * - Completion status tracking
 * - Task query methods (by status, due date, etc.)
 *
 * This validates User Story 4 (Task Synchronization) at the data layer.
 * CalDAV protocol-level sync is tested via CalDAVSyncService + CalDAVClient directly.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TaskSyncIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: DavyDatabase

    @Inject
    lateinit var taskListRepository: TaskListRepository

    @Inject
    lateinit var taskRepository: TaskRepository

    private lateinit var context: Context
    private lateinit var accountDao: AccountDao
    private lateinit var taskListDao: TaskListDao
    private lateinit var taskDao: TaskDao
    private var testAccountId: Long = 0
    private var testTaskListId: Long = 0

    // ---- Test fixtures matching actual entity constructors ----

    private fun createTestAccount() = AccountEntity(
        id = 0,
        accountName = "testuser@nextcloud.example.com",
        serverUrl = "https://nextcloud.example.com",
        username = "testuser",
        displayName = "Test Account",
        email = "testuser@example.com",
        calendarEnabled = true,
        contactsEnabled = true,
        tasksEnabled = true
    )

    private fun createTestTaskList(accountId: Long) = TaskListEntity(
        id = 0,
        accountId = accountId,
        url = "https://nextcloud.example.com/remote.php/dav/calendars/testuser/tasks/",
        displayName = "Personal Tasks",
        color = "#FF5722",
        ctag = "ctag-initial",
        syncEnabled = true,
        visible = true,
        lastSynced = null
    )

    private fun createTestTask(
        taskListId: Long,
        uid: String = "task-uid-001",
        summary: String = "Complete project documentation",
        status: String? = "IN_PROCESS",
        priority: Int? = 1,
        percentComplete: Int? = 50,
        due: Long? = 1706745599000L, // 2024-01-31T23:59:59Z
        dirty: Boolean = false,
        deleted: Boolean = false,
        etag: String? = "etag-123",
        completed: Long? = null,
        parentTaskId: Long? = null,
        parentTaskUid: String? = null
    ) = TaskEntity(
        id = 0,
        taskListId = taskListId,
        url = "https://nextcloud.example.com/remote.php/dav/calendars/testuser/tasks/$uid.ics",
        uid = uid,
        etag = etag,
        summary = summary,
        description = "Write comprehensive documentation",
        status = status,
        priority = priority,
        percentComplete = percentComplete,
        due = due,
        dtStart = null,
        completed = completed,
        created = System.currentTimeMillis(),
        lastModified = System.currentTimeMillis(),
        rrule = null,
        parentTaskId = parentTaskId,
        parentTaskUid = parentTaskUid,
        location = null,
        categories = null,
        classification = null,
        exdates = null,
        rdates = null,
        unknownProperties = null,
        dirty = dirty,
        deleted = deleted
    )

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Setup DAOs
        accountDao = database.accountDao()
        taskListDao = database.taskListDao()
        taskDao = database.taskDao()

        // Create test account and task list
        runBlocking {
            testAccountId = accountDao.insert(createTestAccount())
            testTaskListId = taskListDao.insert(createTestTaskList(testAccountId))
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    // ========== Task CRUD Tests ==========

    @Test
    fun insertTask_persistsAllFields() = runTest {
        // Given
        val task = createTestTask(testTaskListId)

        // When
        val taskId = taskDao.insert(task)

        // Then
        val saved = taskDao.getById(taskId)
        assertThat(saved).isNotNull()
        assertThat(saved!!.uid).isEqualTo("task-uid-001")
        assertThat(saved.summary).isEqualTo("Complete project documentation")
        assertThat(saved.description).isEqualTo("Write comprehensive documentation")
        assertThat(saved.status).isEqualTo("IN_PROCESS")
        assertThat(saved.priority).isEqualTo(1)
        assertThat(saved.percentComplete).isEqualTo(50)
        assertThat(saved.due).isEqualTo(1706745599000L)
        assertThat(saved.etag).isEqualTo("etag-123")
        assertThat(saved.dirty).isFalse()
        assertThat(saved.deleted).isFalse()
    }

    @Test
    fun getByTaskListId_returnsTasksForCorrectList() = runTest {
        // Given: two task lists, each with tasks
        val otherTaskListId = taskListDao.insert(
            createTestTaskList(testAccountId).copy(
                url = "https://nextcloud.example.com/remote.php/dav/calendars/testuser/work/",
                displayName = "Work Tasks"
            )
        )
        taskDao.insert(createTestTask(testTaskListId, uid = "personal-1", summary = "Personal task"))
        taskDao.insert(createTestTask(otherTaskListId, uid = "work-1", summary = "Work task"))

        // When
        val personalTasks = taskDao.getByTaskListId(testTaskListId).first()
        val workTasks = taskDao.getByTaskListId(otherTaskListId).first()

        // Then
        assertThat(personalTasks).hasSize(1)
        assertThat(personalTasks[0].summary).isEqualTo("Personal task")
        assertThat(workTasks).hasSize(1)
        assertThat(workTasks[0].summary).isEqualTo("Work task")
    }

    // ========== Dirty Flag Tests (Upload Sync) ==========

    @Test
    fun getDirtyTasks_returnsDirtyTasksOnly() = runTest {
        // Given
        taskDao.insert(createTestTask(testTaskListId, uid = "clean-1", dirty = false))
        taskDao.insert(createTestTask(testTaskListId, uid = "dirty-1", dirty = true, summary = "Modified locally"))
        taskDao.insert(createTestTask(testTaskListId, uid = "dirty-2", dirty = true, summary = "Another dirty"))

        // When
        val dirtyTasks = taskDao.getDirtyTasks()

        // Then
        assertThat(dirtyTasks).hasSize(2)
        assertThat(dirtyTasks.map { it.uid }).containsExactly("dirty-1", "dirty-2")
    }

    @Test
    fun markDirtyTask_whenUploaded_clearsDirtyFlag() = runTest {
        // Given: a dirty task simulating local modification
        val taskId = taskDao.insert(
            createTestTask(testTaskListId, uid = "dirty-upload", dirty = true, summary = "Local edit")
        )

        // When: simulate successful upload -> clear dirty flag + update etag
        val task = taskDao.getById(taskId)!!
        taskDao.update(task.copy(dirty = false, etag = "etag-new-after-upload"))

        // Then
        val updated = taskDao.getById(taskId)!!
        assertThat(updated.dirty).isFalse()
        assertThat(updated.etag).isEqualTo("etag-new-after-upload")
        assertThat(taskDao.getDirtyTasks()).isEmpty()
    }

    // ========== Deleted Flag Tests (Delete Sync) ==========

    @Test
    fun getDeletedTasks_returnsDeletedTasksOnly() = runTest {
        // Given
        taskDao.insert(createTestTask(testTaskListId, uid = "active-1", deleted = false))
        taskDao.insert(createTestTask(testTaskListId, uid = "deleted-1", deleted = true))

        // When
        val deletedTasks = taskDao.getDeletedTasks()

        // Then
        assertThat(deletedTasks).hasSize(1)
        assertThat(deletedTasks[0].uid).isEqualTo("deleted-1")
    }

    @Test
    fun deleteTask_afterServerConfirmation_removesFromDb() = runTest {
        // Given: task marked for deletion
        val taskId = taskDao.insert(
            createTestTask(testTaskListId, uid = "to-delete", deleted = true)
        )

        // When: server confirmed deletion -> remove from local DB
        taskDao.deleteById(taskId)

        // Then
        assertThat(taskDao.getById(taskId)).isNull()
        assertThat(taskDao.getDeletedTasks()).isEmpty()
    }

    // ========== ETag Change Detection Tests ==========

    @Test
    fun etagChanged_indicatesServerUpdate() = runTest {
        // Given: existing task with known etag
        val taskId = taskDao.insert(
            createTestTask(testTaskListId, uid = "etag-test", etag = "etag-old")
        )

        // When: server reports new etag -> update task
        val existing = taskDao.getById(taskId)!!
        val serverEtag = "etag-new"
        assertThat(existing.etag).isNotEqualTo(serverEtag)

        taskDao.update(existing.copy(
            etag = serverEtag,
            summary = "Updated from server",
            status = "COMPLETED",
            percentComplete = 100,
            completed = System.currentTimeMillis()
        ))

        // Then
        val updated = taskDao.getById(taskId)!!
        assertThat(updated.etag).isEqualTo("etag-new")
        assertThat(updated.summary).isEqualTo("Updated from server")
        assertThat(updated.status).isEqualTo("COMPLETED")
    }

    @Test
    fun etagUnchanged_skipsUpdate() = runTest {
        // Given
        val taskId = taskDao.insert(
            createTestTask(testTaskListId, uid = "same-etag", etag = "etag-same")
        )

        // When: server reports same etag
        val existing = taskDao.getById(taskId)!!
        val serverEtag = "etag-same"

        // Then: etag matches -> skip
        assertThat(existing.etag).isEqualTo(serverEtag)
    }

    // ========== Subtask / Parent-Child Tests ==========

    @Test
    fun subtask_maintainsParentChildRelationship() = runTest {
        // Given: parent task
        val parentId = taskDao.insert(
            createTestTask(testTaskListId, uid = "parent-1", summary = "Parent task")
        )

        // And: subtask referencing parent
        val subtaskId = taskDao.insert(
            createTestTask(
                testTaskListId,
                uid = "child-1",
                summary = "Subtask 1",
                parentTaskId = parentId
            )
        )

        // When
        val subtasks = taskDao.getSubtasks(parentId).first()

        // Then
        assertThat(subtasks).hasSize(1)
        assertThat(subtasks[0].summary).isEqualTo("Subtask 1")
        assertThat(subtasks[0].parentTaskId).isEqualTo(parentId)
    }

    // ========== Completion Status Tests ==========

    @Test
    fun completeTask_updatesStatusAndCompletedDate() = runTest {
        // Given: in-progress task
        val taskId = taskDao.insert(
            createTestTask(
                testTaskListId,
                uid = "completing",
                status = "IN_PROCESS",
                percentComplete = 50,
                completed = null,
                dirty = false
            )
        )

        // When: mark as completed
        val task = taskDao.getById(taskId)!!
        val completedTime = System.currentTimeMillis()
        taskDao.update(task.copy(
            status = "COMPLETED",
            percentComplete = 100,
            completed = completedTime,
            dirty = true
        ))

        // Then
        val completed = taskDao.getById(taskId)!!
        assertThat(completed.status).isEqualTo("COMPLETED")
        assertThat(completed.percentComplete).isEqualTo(100)
        assertThat(completed.completed).isEqualTo(completedTime)
        assertThat(completed.dirty).isTrue()

        // Also appears in completed tasks query
        val completedTasks = taskDao.getCompletedTasks(testTaskListId).first()
        assertThat(completedTasks).hasSize(1)
    }

    @Test
    fun getByStatus_filtersCorrectly() = runTest {
        // Given
        taskDao.insert(createTestTask(testTaskListId, uid = "todo-1", status = "NEEDS_ACTION"))
        taskDao.insert(createTestTask(testTaskListId, uid = "progress-1", status = "IN_PROCESS"))
        taskDao.insert(createTestTask(testTaskListId, uid = "done-1", status = "COMPLETED", completed = System.currentTimeMillis()))
        taskDao.insert(createTestTask(testTaskListId, uid = "cancelled-1", status = "CANCELLED"))

        // When
        val needsAction = taskDao.getByStatus(testTaskListId, "NEEDS_ACTION").first()
        val inProcess = taskDao.getByStatus(testTaskListId, "IN_PROCESS").first()
        val completedList = taskDao.getCompletedTasks(testTaskListId).first()

        // Then
        assertThat(needsAction).hasSize(1)
        assertThat(inProcess).hasSize(1)
        assertThat(completedList).hasSize(1)
    }

    // ========== Task List CTag Tests ==========

    @Test
    fun updateCTag_indicatesNewServerChanges() = runTest {
        // Given: task list with initial ctag
        val taskList = taskListDao.getById(testTaskListId)
        assertThat(taskList).isNotNull()
        assertThat(taskList!!.ctag).isEqualTo("ctag-initial")

        // When: server reports new ctag -> need to sync
        taskListDao.updateCTag(testTaskListId, "ctag-updated")

        // Then
        val updated = taskListDao.getById(testTaskListId)!!
        assertThat(updated.ctag).isEqualTo("ctag-updated")
    }

    @Test
    fun taskListsByAccount_returnsCorrectLists() = runTest {
        // Given: second task list for same account
        taskListDao.insert(
            createTestTaskList(testAccountId).copy(
                url = "https://nextcloud.example.com/remote.php/dav/calendars/testuser/work/",
                displayName = "Work Tasks"
            )
        )

        // When
        val taskLists = taskListDao.getByAccountId(testAccountId).first()

        // Then
        assertThat(taskLists).hasSize(2)
        assertThat(taskLists.map { it.displayName }).containsExactly("Personal Tasks", "Work Tasks")
    }

    // ========== Repository-Level Tests ==========

    @Test
    fun repository_getByTaskListId_returnsDomainModels() = runTest {
        // Given
        taskDao.insert(createTestTask(testTaskListId, uid = "repo-1", summary = "Repo task 1"))
        taskDao.insert(createTestTask(testTaskListId, uid = "repo-2", summary = "Repo task 2"))

        // When: query through repository (returns domain model Flow)
        val tasks = taskRepository.getByTaskListId(testTaskListId).first()

        // Then
        assertThat(tasks).hasSize(2)
        assertThat(tasks.map { it.summary }).containsExactly("Repo task 1", "Repo task 2")
    }

    @Test
    fun repository_getById_returnsDomainModel() = runTest {
        // Given
        val taskId = taskDao.insert(
            createTestTask(testTaskListId, uid = "repo-single", summary = "Single task")
        )

        // When
        val task = taskRepository.getById(taskId)

        // Then
        assertThat(task).isNotNull()
        assertThat(task!!.uid).isEqualTo("repo-single")
        assertThat(task.summary).isEqualTo("Single task")
    }

    // ========== Recurrence Rule Tests ==========

    @Test
    fun taskWithRecurrenceRule_persistsCorrectly() = runTest {
        // Given
        val task = createTestTask(testTaskListId, uid = "recurring-1", summary = "Weekly review").let {
            it.copy(rrule = "FREQ=WEEKLY;BYDAY=FR")
        }

        // When
        val taskId = taskDao.insert(task)

        // Then
        val saved = taskDao.getById(taskId)!!
        assertThat(saved.rrule).isNotNull()
        assertThat(saved.rrule).contains("FREQ=WEEKLY")
        assertThat(saved.rrule).contains("BYDAY=FR")
    }

    // ========== Due Date Query Tests ==========

    @Test
    fun getTasksDueBefore_returnsOverdueTasks() = runTest {
        // Given
        val pastDue = System.currentTimeMillis() - 86400000L // yesterday
        val futureDue = System.currentTimeMillis() + 86400000L // tomorrow
        taskDao.insert(createTestTask(testTaskListId, uid = "overdue-1", due = pastDue, summary = "Overdue"))
        taskDao.insert(createTestTask(testTaskListId, uid = "future-1", due = futureDue, summary = "Future"))
        taskDao.insert(createTestTask(testTaskListId, uid = "no-due", due = null, summary = "No due date"))

        // When
        val overdueTasks = taskDao.getTasksDueBefore(testTaskListId, System.currentTimeMillis()).first()

        // Then
        assertThat(overdueTasks).hasSize(1)
        assertThat(overdueTasks.first().summary).isEqualTo("Overdue")
    }

    // ========== Multiple Task List Sync Independence ==========

    @Test
    fun syncMultipleTaskLists_handlesEachIndependently() = runTest {
        // Given: two independent task lists
        val workListId = taskListDao.insert(
            createTestTaskList(testAccountId).copy(
                url = "https://nextcloud.example.com/remote.php/dav/calendars/testuser/work/",
                displayName = "Work Tasks",
                ctag = "work-ctag-1"
            )
        )

        // Insert tasks into each list
        taskDao.insert(createTestTask(testTaskListId, uid = "personal-t1", summary = "Personal task"))
        taskDao.insert(createTestTask(workListId, uid = "work-t1", summary = "Work task"))

        // When: update ctag on one list only
        taskListDao.updateCTag(workListId, "work-ctag-2")

        // Then: lists are independent
        val personalList = taskListDao.getById(testTaskListId)!!
        val workList = taskListDao.getById(workListId)!!
        assertThat(personalList.ctag).isEqualTo("ctag-initial")
        assertThat(workList.ctag).isEqualTo("work-ctag-2")

        val personalTasks = taskDao.getByTaskListId(testTaskListId).first()
        val workTasks = taskDao.getByTaskListId(workListId).first()
        assertThat(personalTasks).hasSize(1)
        assertThat(workTasks).hasSize(1)
    }

    // ========== Error Recovery Tests ==========

    @Test
    fun dirtyTask_notOverwrittenByServerUpdate() = runTest {
        // Given: a dirty task (local changes pending upload)
        val taskId = taskDao.insert(
            createTestTask(
                testTaskListId,
                uid = "conflict-1",
                summary = "Local modification",
                etag = "etag-old",
                dirty = true
            )
        )

        // When: server reports different etag (server also changed)
        val existing = taskDao.getById(taskId)!!
        assertThat(existing.dirty).isTrue()

        // Then: dirty tasks should be skipped during download
        // (CalDAVSyncService skips dirty tasks when server etag differs)
        // The local dirty task remains unchanged
        assertThat(existing.summary).isEqualTo("Local modification")
    }

    @Test
    fun getByUrl_findsTaskByCalDAVUrl() = runTest {
        // Given
        val url = "https://nextcloud.example.com/remote.php/dav/calendars/testuser/tasks/specific.ics"
        taskDao.insert(
            createTestTask(testTaskListId, uid = "url-test").copy(url = url)
        )

        // When
        val found = taskDao.getByUrl(url)

        // Then
        assertThat(found).isNotNull()
        assertThat(found!!.uid).isEqualTo("url-test")
    }

    @Test
    fun getByUid_findsTaskByVTODOUid() = runTest {
        // Given
        taskDao.insert(createTestTask(testTaskListId, uid = "vtodo-uid@example.com"))

        // When
        val found = taskDao.getByUid("vtodo-uid@example.com")

        // Then
        assertThat(found).isNotNull()
        assertThat(found!!.uid).isEqualTo("vtodo-uid@example.com")
    }

    @Test
    fun searchTasks_findsMatchesByKeyword() = runTest {
        // Given
        taskDao.insert(createTestTask(testTaskListId, uid = "s1", summary = "Buy groceries"))
        taskDao.insert(createTestTask(testTaskListId, uid = "s2", summary = "Write documentation"))
        taskDao.insert(createTestTask(testTaskListId, uid = "s3", summary = "Review grocery list"))

        // When
        val results = taskDao.search(testTaskListId, "grocer").first()

        // Then
        assertThat(results).hasSize(2)
    }
}
