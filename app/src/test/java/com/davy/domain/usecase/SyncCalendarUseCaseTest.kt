package com.davy.domain.usecase

import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import com.davy.domain.model.SyncStatus
import com.davy.domain.model.SyncResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.time.Instant

/**
 * Unit tests for SyncCalendarUseCase.
 * 
 * Tests the calendar synchronization logic with mocked dependencies.
 * 
 * TDD: This test is written BEFORE the implementation.
 */
@DisplayName("SyncCalendarUseCase Tests")
class SyncCalendarUseCaseTest {
    
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var calendarEventRepository: CalendarEventRepository
    private lateinit var useCase: SyncCalendarUseCase
    
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
        calendarRepository = mockk()
        calendarEventRepository = mockk()
        
        useCase = SyncCalendarUseCase(
            calendarRepository = calendarRepository,
            calendarEventRepository = calendarEventRepository
        )
    }
    
    @Test
    @DisplayName("invoke with valid calendar ID performs sync successfully")
    fun `invoke with valid calendar ID performs sync successfully`() = runTest {
        // Given
        coEvery { calendarRepository.getById(testCalendarId) } returns testCalendar
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns emptyList()
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result).isInstanceOf(SyncCalendarUseCase.SyncResult::class.java)
        assertThat(result.success).isTrue()
        coVerify { calendarRepository.getById(testCalendarId) }
        coVerify { calendarRepository.update(any()) }
    }
    
    @Test
    @DisplayName("invoke with non-existent calendar returns failure")
    fun `invoke with non-existent calendar returns failure`() = runTest {
        // Given
        val nonExistentCalendarId = 999L
        coEvery { calendarRepository.getById(nonExistentCalendarId) } returns null
        
        // When
        val result = useCase(nonExistentCalendarId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Calendar not found")
    }
    
    @Test
    @DisplayName("invoke with disabled sync calendar returns failure")
    fun `invoke with disabled sync calendar returns failure`() = runTest {
        // Given
        val disabledCalendar = testCalendar.copy(syncEnabled = false)
        coEvery { calendarRepository.getById(testCalendarId) } returns disabledCalendar
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Sync is disabled")
    }
    
    @Test
    @DisplayName("successful sync updates lastSyncedAt timestamp")
    fun `successful sync updates lastSyncedAt timestamp`() = runTest {
        // Given
        coEvery { calendarRepository.getById(testCalendarId) } returns testCalendar
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns emptyList()
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result.success).isTrue()
        coVerify {
            calendarRepository.update(match { calendar ->
                calendar.lastSyncedAt != null && calendar.id == testCalendarId
            })
        }
    }
    
    @Test
    @DisplayName("sync failure does not update lastSyncedAt timestamp")
    fun `sync failure does not update lastSyncedAt timestamp`() = runTest {
        // Given
        coEvery { calendarRepository.getById(testCalendarId) } returns testCalendar
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } throws RuntimeException("Network error")
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Network error")
        coVerify(exactly = 0) { calendarRepository.update(any()) }
    }
    
    @Test
    @DisplayName("sync counts events correctly")
    fun `sync counts events correctly`() = runTest {
        // Given
        val testEvents = listOf(
            createTestEvent(1L, "Event 1"),
            createTestEvent(2L, "Event 2"),
            createTestEvent(3L, "Event 3")
        )
        
        coEvery { calendarRepository.getById(testCalendarId) } returns testCalendar
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns testEvents
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result.success).isTrue()
        assertThat(result.eventsSynced).isEqualTo(3)
    }
    
    @Test
    @DisplayName("sync with empty calendar succeeds")
    fun `sync with empty calendar succeeds`() = runTest {
        // Given
        coEvery { calendarRepository.getById(testCalendarId) } returns testCalendar
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns emptyList()
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result.success).isTrue()
        assertThat(result.eventsSynced).isEqualTo(0)
    }
    
    @Test
    @DisplayName("sync updates syncToken when server provides new token")
    fun `sync updates syncToken when server provides new token`() = runTest {
        // Given
        val newSyncToken = "updated-ctag-value"
        coEvery { calendarRepository.getById(testCalendarId) } returns testCalendar
        coEvery { calendarRepository.update(any()) } returns Unit
        coEvery { calendarEventRepository.getByCalendarId(testCalendarId) } returns emptyList()
        
        // When
        val result = useCase(testCalendarId, newSyncToken = newSyncToken)
        
        // Then
        assertThat(result.success).isTrue()
        coVerify {
            calendarRepository.update(match { calendar ->
                calendar.syncToken == newSyncToken
            })
        }
    }
    
    @Test
    @DisplayName("sync with repository exception returns failure with error message")
    fun `sync with repository exception returns failure with error message`() = runTest {
        // Given
        val errorMessage = "Database connection failed"
        coEvery { calendarRepository.getById(testCalendarId) } throws Exception(errorMessage)
        
        // When
        val result = useCase(testCalendarId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).isNotNull()
        assertThat(result.errorMessage).contains(errorMessage)
    }
    
    private fun createTestEvent(id: Long, summary: String): CalendarEvent {
        return CalendarEvent(
            id = id,
            calendarId = testCalendarId,
            eventUrl = "https://nextcloud.example.com/event$id.ics",
            uid = "event-uid-$id",
            title = summary,
            description = null,
            location = null,
            dtStart = System.currentTimeMillis(),
            dtEnd = System.currentTimeMillis() + 3600000,
            allDay = false,
            rrule = null,
            timezone = "UTC",
            etag = "event-etag-$id",
            dirty = false,
            deletedAt = null
        )
    }
}
