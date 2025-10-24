package com.davy.data.repository

import com.davy.data.local.dao.CalendarDao
import com.davy.data.local.entity.CalendarEntity
import com.davy.domain.model.Calendar
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CalendarRepository.
 * 
 * Tests the repository layer's interaction with the CalendarDao
 * and proper domain/entity mapping.
 */
class CalendarRepositoryTest {

    private lateinit var calendarDao: CalendarDao
    private lateinit var repository: CalendarRepository

    private val testAccountId = 1L
    private val testCalendarId = 100L
    private val testCalendarUrl = "https://example.com/calendars/personal"
    
    private val testCalendar = Calendar(
        id = testCalendarId,
        accountId = testAccountId,
        calendarUrl = testCalendarUrl,
        displayName = "Personal Calendar",
        color = -12543,
        description = "My personal calendar",
        timezone = "America/New_York",
        visible = true,
        syncEnabled = true,
        androidCalendarId = null,
        syncToken = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        lastSyncedAt = null
    )
    
    private val testCalendarEntity = CalendarEntity(
        id = testCalendarId,
        accountId = testAccountId,
        calendarUrl = testCalendarUrl,
        displayName = "Personal Calendar",
        color = -12543,
        description = "My personal calendar",
        timezone = "America/New_York",
        visible = true,
        syncEnabled = true,
        androidCalendarId = null,
        syncToken = null,
        createdAt = testCalendar.createdAt,
        updatedAt = testCalendar.updatedAt,
        lastSyncedAt = null
    )

    @Before
    fun setUp() {
        calendarDao = mockk()
        repository = CalendarRepository(calendarDao)
    }

    @Test
    fun `insert calendar calls dao insert and returns id`() = runTest {
        // Given
        val expectedId = testCalendarId
        coEvery { calendarDao.insert(any()) } returns expectedId

        // When
        val result = repository.insert(testCalendar)

        // Then
        assertEquals(expectedId, result)
        coVerify { calendarDao.insert(any()) }
    }

    @Test
    fun `update calendar calls dao update`() = runTest {
        // Given
        coEvery { calendarDao.update(any()) } returns Unit

        // When
        repository.update(testCalendar)

        // Then
        coVerify { calendarDao.update(any()) }
    }

    @Test
    fun `delete calendar calls dao delete`() = runTest {
        // Given
        coEvery { calendarDao.delete(any()) } returns Unit

        // When
        repository.delete(testCalendar)

        // Then
        coVerify { calendarDao.delete(any()) }
    }

    @Test
    fun `getById returns calendar when found`() = runTest {
        // Given
        coEvery { calendarDao.getById(testCalendarId) } returns testCalendarEntity

        // When
        val result = repository.getById(testCalendarId)

        // Then
        assertEquals(testCalendar.id, result?.id)
        assertEquals(testCalendar.displayName, result?.displayName)
        assertEquals(testCalendar.calendarUrl, result?.calendarUrl)
        coVerify { calendarDao.getById(testCalendarId) }
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        // Given
        coEvery { calendarDao.getById(testCalendarId) } returns null

        // When
        val result = repository.getById(testCalendarId)

        // Then
        assertNull(result)
        coVerify { calendarDao.getById(testCalendarId) }
    }

    @Test
    fun `getByUrl returns calendar when found`() = runTest {
        // Given
        coEvery { calendarDao.getByUrl(testCalendarUrl) } returns testCalendarEntity

        // When
        val result = repository.getByUrl(testCalendarUrl)

        // Then
        assertEquals(testCalendar.id, result?.id)
        assertEquals(testCalendar.calendarUrl, result?.calendarUrl)
        coVerify { calendarDao.getByUrl(testCalendarUrl) }
    }

    @Test
    fun `getByUrl returns null when not found`() = runTest {
        // Given
        coEvery { calendarDao.getByUrl(testCalendarUrl) } returns null

        // When
        val result = repository.getByUrl(testCalendarUrl)

        // Then
        assertNull(result)
        coVerify { calendarDao.getByUrl(testCalendarUrl) }
    }

    @Test
    fun `getByAccountIdFlow returns flow of calendars`() = runTest {
        // Given
        val entities = listOf(testCalendarEntity)
        every { calendarDao.getByAccountIdFlow(testAccountId) } returns flowOf(entities)

        // When
        val result = repository.getByAccountIdFlow(testAccountId).first()

        // Then
        assertEquals(1, result.size)
        assertEquals(testCalendar.id, result[0].id)
        assertEquals(testCalendar.displayName, result[0].displayName)
    }

    @Test
    fun `getByAccountIdFlow returns empty flow when no calendars`() = runTest {
        // Given
        every { calendarDao.getByAccountIdFlow(testAccountId) } returns flowOf(emptyList())

        // When
        val result = repository.getByAccountIdFlow(testAccountId).first()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `getByAccountId returns list of calendars`() = runTest {
        // Given
        val entities = listOf(testCalendarEntity)
        coEvery { calendarDao.getByAccountId(testAccountId) } returns entities

        // When
        val result = repository.getByAccountId(testAccountId)

        // Then
        assertEquals(1, result.size)
        assertEquals(testCalendar.id, result[0].id)
        coVerify { calendarDao.getByAccountId(testAccountId) }
    }

    @Test
    fun `getSyncEnabledByAccountId returns only sync-enabled calendars`() = runTest {
        // Given
        val entities = listOf(testCalendarEntity)
        coEvery { calendarDao.getSyncEnabledByAccountId(testAccountId) } returns entities

        // When
        val result = repository.getSyncEnabledByAccountId(testAccountId)

        // Then
        assertEquals(1, result.size)
        assertEquals(true, result[0].syncEnabled)
        coVerify { calendarDao.getSyncEnabledByAccountId(testAccountId) }
    }

    @Test
    fun `getVisibleByAccountIdFlow returns flow of visible calendars`() = runTest {
        // Given
        val entities = listOf(testCalendarEntity)
        every { calendarDao.getVisibleByAccountIdFlow(testAccountId) } returns flowOf(entities)

        // When
        val result = repository.getVisibleByAccountIdFlow(testAccountId).first()

        // Then
        assertEquals(1, result.size)
        assertEquals(true, result[0].visible)
    }

    @Test
    fun `getAllFlow returns flow of all calendars`() = runTest {
        // Given
        val entities = listOf(testCalendarEntity)
        every { calendarDao.getAllFlow() } returns flowOf(entities)

        // When
        val result = repository.getAllFlow().first()

        // Then
        assertEquals(1, result.size)
        assertEquals(testCalendar.id, result[0].id)
    }

    @Test
    fun `countByAccountId returns count`() = runTest {
        // Given
        val expectedCount = 3
        coEvery { calendarDao.countByAccountId(testAccountId) } returns expectedCount

        // When
        val result = repository.countByAccountId(testAccountId)

        // Then
        assertEquals(expectedCount, result)
        coVerify { calendarDao.countByAccountId(testAccountId) }
    }

    @Test
    fun `updateSyncToken calls dao with correct parameters`() = runTest {
        // Given
        val syncToken = "sync-token-123"
        coEvery { calendarDao.updateSyncToken(any(), any(), any()) } returns Unit

        // When
        repository.updateSyncToken(testCalendarId, syncToken)

        // Then
        coVerify { 
            calendarDao.updateSyncToken(
                id = testCalendarId,
                syncToken = syncToken,
                lastSyncedAt = any()
            )
        }
    }

    @Test
    fun `updateAndroidCalendarId calls dao with correct parameters`() = runTest {
        // Given
        val androidCalendarId = 999L
        coEvery { calendarDao.updateAndroidCalendarId(any(), any()) } returns Unit

        // When
        repository.updateAndroidCalendarId(testCalendarId, androidCalendarId)

        // Then
        coVerify { 
            calendarDao.updateAndroidCalendarId(testCalendarId, androidCalendarId)
        }
    }

    @Test
    fun `deleteByAccountId calls dao delete`() = runTest {
        // Given
        coEvery { calendarDao.deleteByAccountId(testAccountId) } returns Unit

        // When
        repository.deleteByAccountId(testAccountId)

        // Then
        coVerify { calendarDao.deleteByAccountId(testAccountId) }
    }

    @Test
    fun `deleteAll calls dao deleteAll`() = runTest {
        // Given
        coEvery { calendarDao.deleteAll() } returns Unit

        // When
        repository.deleteAll()

        // Then
        coVerify { calendarDao.deleteAll() }
    }

    @Test
    fun `multiple calendars are mapped correctly`() = runTest {
        // Given
        val calendar2 = testCalendar.copy(
            id = 101L,
            displayName = "Work Calendar",
            color = -16711936
        )
        val entity2 = testCalendarEntity.copy(
            id = 101L,
            displayName = "Work Calendar",
            color = -16711936
        )
        val entities = listOf(testCalendarEntity, entity2)
        coEvery { calendarDao.getByAccountId(testAccountId) } returns entities

        // When
        val result = repository.getByAccountId(testAccountId)

        // Then
        assertEquals(2, result.size)
        assertEquals("Personal Calendar", result[0].displayName)
        assertEquals("Work Calendar", result[1].displayName)
    }
}
