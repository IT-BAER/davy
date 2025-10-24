package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Unit tests for CalendarSyncWorker.
 * 
 * Tests the WorkManager worker for calendar synchronization with mocked dependencies.
 * 
 * TDD: This test is written BEFORE the implementation.
 */
@DisplayName("CalendarSyncWorker Tests")
class CalendarSyncWorkerTest {
    
    private lateinit var context: Context
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var calendarEventRepository: CalendarEventRepository
    
    private val testAccountId = 1L
    private val testCalendarId = 100L
    private val testCalendar = Calendar(
        id = testCalendarId,
        accountId = testAccountId,
        calendarUrl = "https://nextcloud.example.com/remote.php/dav/calendars/user/calendar1/",
        displayName = "Test Calendar",
        description = "Test calendar description",
        color = 0xFF5733.toInt(),
        timezone = "UTC",
        syncEnabled = true,
        syncToken = "initial-ctag",
        lastSyncedAt = null
    )
    
    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        calendarRepository = mockk()
        calendarEventRepository = mockk()
    }
    
    @Test
    @DisplayName("doWork with valid account ID returns success")
    fun `doWork with valid account ID returns success`() = runTest {
        // Given
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(testAccountId) } returns listOf(testCalendar)
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify { calendarRepository.getSyncEnabledByAccountId(testAccountId) }
    }
    
    @Test
    @DisplayName("doWork with no account ID syncs all accounts")
    fun `doWork with no account ID syncs all accounts`() = runTest {
        // Given
        val inputData = workDataOf()
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(any()) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }
    
    @Test
    @DisplayName("doWork with sync-disabled calendars skips them")
    fun `doWork with sync-disabled calendars skips them`() = runTest {
        // Given
        val disabledCalendar = testCalendar.copy(syncEnabled = false)
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(testAccountId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 0) { calendarRepository.update(any()) }
    }
    
    @Test
    @DisplayName("doWork with repository exception returns retry")
    fun `doWork with repository exception returns retry`() = runTest {
        // Given
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(testAccountId) } throws RuntimeException("Network error")
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .setRunAttemptCount(0)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
    }
    
    @Test
    @DisplayName("doWork after max retries returns failure")
    fun `doWork after max retries returns failure`() = runTest {
        // Given
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(testAccountId) } throws RuntimeException("Network error")
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .setRunAttemptCount(3)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }
    
    @Test
    @DisplayName("doWork syncs multiple calendars for account")
    fun `doWork syncs multiple calendars for account`() = runTest {
        // Given
        val calendar1 = testCalendar.copy(id = 1L, displayName = "Calendar 1")
        val calendar2 = testCalendar.copy(id = 2L, displayName = "Calendar 2")
        val calendar3 = testCalendar.copy(id = 3L, displayName = "Calendar 3")
        
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(testAccountId) } returns listOf(calendar1, calendar2, calendar3)
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(any()) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 3) { calendarRepository.update(any()) }
    }
    
    @Test
    @DisplayName("doWork updates lastSyncedAt timestamp for each calendar")
    fun `doWork updates lastSyncedAt timestamp for each calendar`() = runTest {
        // Given
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(testAccountId) } returns listOf(testCalendar)
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify {
            calendarRepository.update(match { calendar ->
                calendar.lastSyncedAt != null && calendar.lastSyncedAt!! > testCalendar.lastSyncedAt ?: 0L
            })
        }
    }
    
    @Test
    @DisplayName("doWork with invalid account ID returns success with no-op")
    fun `doWork with invalid account ID returns success with no-op`() = runTest {
        // Given
        val invalidAccountId = -999L
        val inputData = workDataOf(
            CalendarSyncWorker.INPUT_ACCOUNT_ID to invalidAccountId
        )
        
        coEvery { calendarRepository.getSyncEnabledByAccountId(invalidAccountId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify { calendarRepository.getSyncEnabledByAccountId(invalidAccountId) }
        coVerify(exactly = 0) { calendarRepository.update(any()) }
    }
}
