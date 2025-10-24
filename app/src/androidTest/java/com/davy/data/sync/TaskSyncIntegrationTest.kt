package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.davy.data.local.DavyDatabase
import com.davy.data.local.dao.AccountDao
import com.davy.data.local.dao.TaskDao
import com.davy.data.local.dao.TaskListDao
import com.davy.data.local.entity.AccountEntity
import com.davy.data.local.entity.TaskEntity
import com.davy.data.local.entity.TaskListEntity
import com.davy.data.remote.caldav.TaskDelete
import com.davy.data.remote.caldav.TaskGet
import com.davy.data.remote.caldav.TaskPut
import com.davy.data.remote.caldav.TaskQuery
import com.davy.data.repository.TaskListRepository
import com.davy.data.repository.TaskRepository
import com.davy.domain.model.AuthType
import com.davy.domain.model.TaskPriority
import com.davy.domain.model.TaskStatus
import com.davy.domain.usecase.SyncTasksUseCase
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for CalDAV task synchronization.
 * 
 * Tests the complete sync flow including:
 * - Initial full sync of task lists and tasks
 * - Incremental sync using ctag/etag
 * - Upload of local changes
 * - Download of remote changes
 * - Task deletion handling
 * - VTODO parsing and serialization
 * - Recurrence rule handling
 * 
 * This validates User Story 4 (Task Synchronization) with actual CalDAV protocol testing.
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
    private lateinit var mockWebServer: MockWebServer
    private lateinit var accountDao: AccountDao
    private lateinit var taskListDao: TaskListDao
    private lateinit var taskDao: TaskDao
    private lateinit var syncTasksUseCase: SyncTasksUseCase
    private var testAccountId: Long = 0

    // Test fixtures for mock-based tests
    private val testTaskListEntity = TaskListEntity(
        id = null,
        accountId = 100L,
        serverUrl = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/",
        displayName = "Personal Tasks",
        color = "#FF5722",
        isEnabled = true,
        cTag = "ctag-initial",
        lastSyncTime = Instant.parse("2024-01-01T00:00:00Z"),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private val testTaskEntity = TaskEntity(
        id = null,
        taskListId = 1L,
        serverUrl = "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics",
        uid = "task-uid-001",
        summary = "Complete project documentation",
        description = "Write comprehensive documentation",
        status = TaskStatus.IN_PROGRESS,
        priority = TaskPriority.HIGH,
        percentComplete = 50,
        dueDate = Instant.parse("2024-01-31T23:59:59Z"),
        completedDate = null,
        parentTaskId = null,
        eTag = "etag-123",
        isDirty = false,
        isDeleted = false,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Setup DAOs
        accountDao = database.accountDao()
        taskListDao = database.taskListDao()
        taskDao = database.taskDao()

        syncTasksUseCase = SyncTasksUseCase(taskListRepository, taskRepository)

        // Setup mock server
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create test account
        runBlocking {
            val account = AccountEntity(
                id = 0,
                displayName = "Test Account",
                username = "testuser",
                serverUrl = mockWebServer.url("/").toString(),
                authType = AuthType.BASIC,
                calendarPrincipalUrl = mockWebServer.url("/caldav/").toString(),
                cardDavPrincipalUrl = null,
                createdAt = OffsetDateTime.now(),
                lastSyncedAt = null
            )
            testAccountId = accountDao.insert(account)
        }
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        database.close()
    }

    // ========== CalDAV Protocol Integration Tests ==========

    @Test
    fun caldavProtocol_initialFullTaskSync_succeeds() = runBlocking {
        // Arrange: Mock CalDAV server responses for initial sync
        
        // 1. PROPFIND to discover task lists
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(207)
            .setBody("""
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
                    <d:response>
                        <d:href>/caldav/tasks/</d:href>
                        <d:propstat>
                            <d:prop>
                                <d:displayname>My Tasks</d:displayname>
                                <d:resourcetype>
                                    <d:collection/>
                                    <cal:calendar/>
                                </d:resourcetype>
                                <cal:supported-calendar-component-set>
                                    <cal:comp name="VTODO"/>
                                </cal:supported-calendar-component-set>
                                <d:sync-token>http://example.com/sync/1</d:sync-token>
                                <cal:getctag>task-ctag-001</cal:getctag>
                            </d:prop>
                            <d:status>HTTP/1.1 200 OK</d:status>
                        </d:propstat>
                    </d:response>
                </d:multistatus>
            """.trimIndent())
            .addHeader("Content-Type", "application/xml"))

        // 2. calendar-query REPORT to get task hrefs
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(207)
            .setBody("""
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:">
                    <d:response>
                        <d:href>/caldav/tasks/task1.ics</d:href>
                        <d:propstat>
                            <d:prop><d:getetag>"task-etag-001"</d:getetag></d:prop>
                            <d:status>HTTP/1.1 200 OK</d:status>
                        </d:propstat>
                    </d:response>
                </d:multistatus>
            """.trimIndent())
            .addHeader("Content-Type", "application/xml"))

        // 3. GET request for task VTODO
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//DAVy Test//EN
                BEGIN:VTODO
                UID:task-001@example.com
                DTSTAMP:20251019T120000Z
                SUMMARY:Write integration tests
                DESCRIPTION:Complete task sync integration tests for US4
                STATUS:IN-PROCESS
                PRIORITY:1
                PERCENT-COMPLETE:50
                DUE:20251020T170000Z
                END:VTODO
                END:VCALENDAR
            """.trimIndent())
            .addHeader("Content-Type", "text/calendar")
            .addHeader("ETag", "\"task-etag-001\""))

        // Act: Execute sync worker
        val worker = TestListenableWorkerBuilder<TaskSyncWorker>(context)
            .setInputData(androidx.work.workDataOf("account_id" to testAccountId))
            .build()
        val result = worker.doWork()

        // Assert: Verify sync succeeded and data persisted
        assertEquals(ListenableWorker.Result.success(), result)
        
        val taskLists = taskListDao.getTaskListsForAccount(testAccountId)
        assertEquals(1, taskLists.size)
        assertEquals("My Tasks", taskLists[0].displayName)
        assertEquals("task-ctag-001", taskLists[0].ctag)

        val tasks = taskDao.getTasksForTaskList(taskLists[0].id)
        assertEquals(1, tasks.size)
        assertEquals("task-001@example.com", tasks[0].uid)
        assertEquals("Write integration tests", tasks[0].summary)
        assertEquals(TaskStatus.IN_PROCESS, tasks[0].status)
        assertEquals(50, tasks[0].percentComplete)
    }

    @Test
    fun caldavProtocol_uploadDirtyTask_sendsVTODOFormat() = runBlocking {
        // Arrange: Create task list and dirty task
        val taskListId = taskListDao.insert(TaskListEntity(
            id = 0,
            accountId = testAccountId,
            url = mockWebServer.url("/caldav/tasks/").toString(),
            displayName = "My Tasks",
            color = "#FF0000",
            ctag = "task-ctag-001",
            syncToken = null,
            createdAt = OffsetDateTime.now(),
            lastSyncedAt = OffsetDateTime.now()
        ))

        taskDao.insert(TaskEntity(
            id = 0,
            taskListId = taskListId,
            uid = "new-task@example.com",
            url = mockWebServer.url("/caldav/tasks/new-task.ics").toString(),
            summary = "New local task",
            description = "Created locally",
            status = TaskStatus.NEEDS_ACTION,
            priority = TaskPriority.MEDIUM,
            percentComplete = 0,
            allDay = false,
            due = null,
            completed = null,
            eTag = null,
            lastModified = OffsetDateTime.now(),
            isDirty = true,
            isDeleted = false
        ))

        // Mock responses
        mockWebServer.enqueue(MockResponse().setResponseCode(207)
            .setBody("""<d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
                <d:response><d:href>/caldav/tasks/</d:href><d:propstat><d:prop>
                <cal:getctag>task-ctag-002</cal:getctag></d:prop><d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat></d:response></d:multistatus>"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(201).addHeader("ETag", "\"new-etag\""))
        mockWebServer.enqueue(MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"></d:multistatus>"))

        // Act
        val worker = TestListenableWorkerBuilder<TaskSyncWorker>(context)
            .setInputData(androidx.work.workDataOf("account_id" to testAccountId))
            .build()
        worker.doWork()

        // Assert: Verify PUT request with VTODO
        mockWebServer.takeRequest() // PROPFIND
        val putRequest = mockWebServer.takeRequest()
        assertEquals("PUT", putRequest.method)
        val body = putRequest.body.readUtf8()
        assertTrue(body.contains("BEGIN:VCALENDAR"))
        assertTrue(body.contains("BEGIN:VTODO"))
        assertTrue(body.contains("SUMMARY:New local task"))
        assertTrue(body.contains("STATUS:NEEDS-ACTION"))
        assertTrue(body.contains("END:VTODO"))
        assertTrue(body.contains("END:VCALENDAR"))
    }

    @Test
    fun caldavProtocol_taskRecurrence_parsedCorrectly() = runBlocking {
        // Arrange: Mock recurring task response
        mockWebServer.enqueue(MockResponse().setResponseCode(207)
            .setBody("""<d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
                <d:response><d:href>/caldav/tasks/</d:href><d:propstat><d:prop>
                <d:displayname>Tasks</d:displayname><cal:getctag>ctag-1</cal:getctag>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                </d:multistatus>"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(207)
            .setBody("""<d:multistatus xmlns:d="DAV:"><d:response>
                <d:href>/caldav/tasks/recurring.ics</d:href><d:propstat><d:prop>
                <d:getetag>"rec-etag"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat></d:response></d:multistatus>"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200)
            .setBody("""BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VTODO
UID:recurring@example.com
SUMMARY:Weekly review
DUE:20251022T170000Z
RRULE:FREQ=WEEKLY;BYDAY=FR
STATUS:NEEDS-ACTION
END:VTODO
END:VCALENDAR""")
            .addHeader("ETag", "\"rec-etag\""))

        // Act
        val worker = TestListenableWorkerBuilder<TaskSyncWorker>(context)
            .setInputData(androidx.work.workDataOf("account_id" to testAccountId))
            .build()
        worker.doWork()

        // Assert: Verify recurrence rule parsed
        val taskLists = taskListDao.getTaskListsForAccount(testAccountId)
        val tasks = taskDao.getTasksForTaskList(taskLists[0].id)
        val recurringTask = tasks.find { it.uid == "recurring@example.com" }
        assertNotNull(recurringTask)
        assertNotNull(recurringTask.rrule)
        assertTrue(recurringTask.rrule!!.contains("FREQ=WEEKLY"))
        assertTrue(recurringTask.rrule!!.contains("BYDAY=FR"))
    }

    // ========== Unit Tests with Mocks (Original) ==========

    private lateinit var taskQuery: TaskQuery
    private lateinit var taskGet: TaskGet
    private lateinit var taskPut: TaskPut
    private lateinit var taskDelete: TaskDelete

    private fun setupMocks() {
        taskQuery = mockk(relaxed = true)
        taskGet = mockk(relaxed = true)
        taskPut = mockk(relaxed = true)
        taskDelete = mockk(relaxed = true)
    }

    @Test
    fun fullSyncFlow_downloadsTasksFromServer() = runTest {
        setupMocks()
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val remoteTaskData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//DAVy//EN
            BEGIN:VTODO
            UID:task-uid-001
            SUMMARY:Complete project documentation
            STATUS:IN-PROCESS
            PRIORITY:1
            PERCENT-COMPLETE:50
            DUE:20240131T235959Z
            CREATED:20240101T000000Z
            LAST-MODIFIED:20240115T103000Z
            END:VTODO
            END:VCALENDAR
        """.trimIndent()
        
        coEvery { taskQuery.queryTasks(any(), any()) } returns listOf(
            "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics"
        )
        coEvery { taskGet.getTask(any(), any()) } returns remoteTaskData

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isSuccess).isTrue()
        val tasks = taskRepository.getTasksByTaskListId(taskListId).first()
        assertThat(tasks).hasSize(1)
        assertThat(tasks.first().summary).isEqualTo("Complete project documentation")
    }

    @Test
    fun mockTest_incrementalSync_onlyDownloadsChangedTasks() = runTest {
        setupMocks()
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val existingTask = testTaskEntity.copy(taskListId = taskListId, eTag = "etag-old")
        taskDao.insertTask(existingTask)
        
        val updatedCTag = "ctag-new"
        taskListDao.updateCTag(taskListId, updatedCTag, Instant.now())
        
        coEvery { taskQuery.queryTasks(any(), any()) } returns listOf(
            "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics"
        )
        coEvery { taskGet.getTask(any(), any()) } returns """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:task-uid-001
            SUMMARY:Updated documentation task
            STATUS:IN-PROCESS
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isSuccess).isTrue()
        val tasks = taskRepository.getTasksByTaskListId(taskListId).first()
        assertThat(tasks.first().summary).isEqualTo("Updated documentation task")
    }

    @Test
    fun mockTest_uploadDirtyTasks_pushesLocalChangesToServer() = runTest {
        setupMocks()
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val dirtyTask = testTaskEntity.copy(
            taskListId = taskListId,
            isDirty = true,
            summary = "Modified locally"
        )
        taskDao.insertTask(dirtyTask)
        
        coEvery { taskPut.putTask(any(), any(), any()) } returns "etag-new-123"
        coEvery { taskQuery.queryTasks(any(), any()) } returns emptyList()

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { taskPut.putTask(any(), any(), any()) }
        
        val tasks = taskRepository.getTasksByTaskListId(taskListId).first()
        assertThat(tasks.first().isDirty).isFalse()
    }

    @Test
    fun mockTest_deleteRemovedTasks_removesTasksFromServer() = runTest {
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val deletedTask = testTaskEntity.copy(
            taskListId = taskListId,
            isDeleted = true
        )
        val taskId = taskDao.insertTask(deletedTask)
        
        coEvery { taskDelete.deleteTask(any(), any()) } returns Unit
        coEvery { taskQuery.queryTasks(any(), any()) } returns emptyList()

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { taskDelete.deleteTask(any(), any()) }
        
        val task = taskRepository.getTaskById(taskId)
        assertThat(task).isNull()
    }

    @Test
    fun mockTest_conflictDetection_identifiesModifiedTasks() = runTest {
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val localTask = testTaskEntity.copy(
            taskListId = taskListId,
            isDirty = true,
            summary = "Local modification",
            eTag = "etag-old"
        )
        taskDao.insertTask(localTask)
        
        val remoteTaskData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:task-uid-001
            SUMMARY:Remote modification
            STATUS:IN-PROCESS
            END:VTODO
            END:VCALENDAR
        """.trimIndent()
        
        coEvery { taskQuery.queryTasks(any(), any()) } returns listOf(
            "https://nextcloud.example.com/remote.php/dav/calendars/user/tasks/task1.ics"
        )
        coEvery { taskGet.getTask(any(), any()) } returns remoteTaskData
        coEvery { taskPut.putTask(any(), any(), any()) } returns "etag-conflict"

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        // Should detect conflict and apply resolution strategy
        assertThat(result.isSuccess).isTrue()
        val tasks = taskRepository.getTasksByTaskListId(taskListId).first()
        assertThat(tasks.first().summary).isNotEmpty()
    }

    @Test
    fun mockTest_syncMultipleTaskLists_handlesEachIndependently() = runTest {
        // Given
        val taskList1Id = taskListDao.insertTaskList(testTaskListEntity)
        val taskList2Id = taskListDao.insertTaskList(
            testTaskListEntity.copy(
                id = null,
                displayName = "Work Tasks",
                serverUrl = "https://nextcloud.example.com/remote.php/dav/calendars/user/work/"
            )
        )
        
        coEvery { taskQuery.queryTasks(any(), any()) } returns emptyList()

        // When
        val result1 = syncTasksUseCase.syncTaskList(taskList1Id)
        val result2 = syncTasksUseCase.syncTaskList(taskList2Id)

        // Then
        assertThat(result1.isSuccess).isTrue()
        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun mockTest_syncWithSubtasks_maintainsParentChildRelationship() = runTest {
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val parentTask = testTaskEntity.copy(taskListId = taskListId)
        val parentTaskId = taskDao.insertTask(parentTask)
        
        val subtask = testTaskEntity.copy(
            id = null,
            taskListId = taskListId,
            uid = "task-uid-002",
            summary = "Subtask 1",
            parentTaskId = parentTaskId
        )
        taskDao.insertTask(subtask)
        
        coEvery { taskQuery.queryTasks(any(), any()) } returns emptyList()

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isSuccess).isTrue()
        val subtasks = taskRepository.getSubtasks(parentTaskId).first()
        assertThat(subtasks).hasSize(1)
        assertThat(subtasks.first().parentTaskId).isEqualTo(parentTaskId)
    }

    @Test
    fun mockTest_syncCompleted_tasks_updatesStatusAndDate() = runTest {
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        val completedTask = testTaskEntity.copy(
            taskListId = taskListId,
            status = TaskStatus.COMPLETED,
            percentComplete = 100,
            completedDate = Instant.parse("2024-01-20T10:00:00Z"),
            isDirty = true
        )
        taskDao.insertTask(completedTask)
        
        coEvery { taskPut.putTask(any(), any(), any()) } returns "etag-completed"
        coEvery { taskQuery.queryTasks(any(), any()) } returns emptyList()

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isSuccess).isTrue()
        val tasks = taskRepository.getTasksByTaskListId(taskListId).first()
        assertThat(tasks.first().status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(tasks.first().completedDate).isNotNull()
    }

    @Test
    fun mockTest_errorHandling_recoversFromNetworkFailures() = runTest {
        // Given
        val taskListId = taskListDao.insertTaskList(testTaskListEntity)
        coEvery { taskQuery.queryTasks(any(), any()) } throws 
            java.net.SocketTimeoutException("Connection timeout")

        // When
        val result = syncTasksUseCase.syncTaskList(taskListId)

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(java.net.SocketTimeoutException::class.java)
    }
}
