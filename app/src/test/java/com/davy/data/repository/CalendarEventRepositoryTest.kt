package com.davy.data.repository

import com.davy.data.local.dao.CalendarEventDao
import com.davy.data.local.entity.CalendarEventEntity
import com.davy.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CalendarEventRepository.
 * 
 * Tests the repository layer's interaction with the CalendarEventDao
 * and proper domain/entity mapping, including JSON serialization.
 */
class CalendarEventRepositoryTest {

    private lateinit var calendarEventDao: CalendarEventDao
    private lateinit var repository: CalendarEventRepository

    private val testCalendarId = 100L
    private val testEventId = 1000L
    private val testEventUrl = "https://example.com/calendars/personal/events/meeting.ics"
    private val testUid = "meeting-2024-001@example.com"
    
    private val testAttendee = Attendee(
        email = "john@example.com",
        name = "John Doe",
        role = AttendeeRole.REQUIRED_PARTICIPANT,
        status = AttendeeStatus.ACCEPTED
    )
    
    private val testReminder = Reminder(
        minutes = 15,
        method = ReminderMethod.ALERT
    )
    
    private val testEvent = CalendarEvent(
        id = testEventId,
        calendarId = testCalendarId,
        eventUrl = testEventUrl,
        uid = testUid,
        title = "Team Meeting",
        description = "Weekly team sync",
        location = "Conference Room A",
        dtStart = System.currentTimeMillis(),
        dtEnd = System.currentTimeMillis() + 3600000, // 1 hour later
        allDay = false,
        timezone = "America/New_York",
        status = EventStatus.CONFIRMED,
        availability = Availability.BUSY,
        rrule = null,
        rdate = null,
        exdate = null,
        recurrenceId = null,
        originalEventId = null,
        organizer = "organizer@example.com",
        attendees = listOf(testAttendee),
        reminders = listOf(testReminder),
        etag = "etag-123",
        androidEventId = null,
        dirty = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        deletedAt = null
    )
    
    private val testEventEntity = CalendarEventEntity(
        id = testEventId,
        calendarId = testCalendarId,
        eventUrl = testEventUrl,
        uid = testUid,
        title = "Team Meeting",
        description = "Weekly team sync",
        location = "Conference Room A",
        dtStart = testEvent.dtStart,
        dtEnd = testEvent.dtEnd,
        allDay = false,
        timezone = "America/New_York",
        status = "CONFIRMED",
        availability = "BUSY",
        rrule = null,
        rdate = null,
        exdate = null,
        recurrenceId = null,
        originalEventId = null,
        organizer = "organizer@example.com",
        attendeesJson = """[{"email":"john@example.com","name":"John Doe","role":"REQUIRED_PARTICIPANT","status":"ACCEPTED"}]""",
        remindersJson = """[{"minutes":15,"method":"ALERT"}]""",
        etag = "etag-123",
        androidEventId = null,
        dirty = false,
        createdAt = testEvent.createdAt,
        updatedAt = testEvent.updatedAt,
        deletedAt = null
    )

    @Before
    fun setUp() {
        calendarEventDao = mockk()
        repository = CalendarEventRepository(calendarEventDao)
    }

    @Test
    fun `insert event calls dao insert and returns id`() = runTest {
        // Given
        val expectedId = testEventId
        coEvery { calendarEventDao.insert(any()) } returns expectedId

        // When
        val result = repository.insert(testEvent)

        // Then
        assertEquals(expectedId, result)
        coVerify { calendarEventDao.insert(any()) }
    }

    @Test
    fun `insertAll events calls dao insertAll and returns ids`() = runTest {
        // Given
        val events = listOf(testEvent, testEvent.copy(id = 1001L))
        val expectedIds = listOf(testEventId, 1001L)
        coEvery { calendarEventDao.insertAll(any()) } returns expectedIds

        // When
        val result = repository.insertAll(events)

        // Then
        assertEquals(expectedIds, result)
        coVerify { calendarEventDao.insertAll(any()) }
    }

    @Test
    fun `update event calls dao update`() = runTest {
        // Given
        coEvery { calendarEventDao.update(any()) } returns Unit

        // When
        repository.update(testEvent)

        // Then
        coVerify { calendarEventDao.update(any()) }
    }

    @Test
    fun `delete event calls dao delete`() = runTest {
        // Given
        coEvery { calendarEventDao.delete(any()) } returns Unit

        // When
        repository.delete(testEvent)

        // Then
        coVerify { calendarEventDao.delete(any()) }
    }

    @Test
    fun `getById returns event when found`() = runTest {
        // Given
        coEvery { calendarEventDao.getById(testEventId) } returns testEventEntity

        // When
        val result = repository.getById(testEventId)

        // Then
        assertEquals(testEvent.id, result?.id)
        assertEquals(testEvent.title, result?.title)
        assertEquals(testEvent.eventUrl, result?.eventUrl)
        assertEquals(1, result?.attendees?.size)
        assertEquals("john@example.com", result?.attendees?.get(0)?.email)
        coVerify { calendarEventDao.getById(testEventId) }
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        // Given
        coEvery { calendarEventDao.getById(testEventId) } returns null

        // When
        val result = repository.getById(testEventId)

        // Then
        assertNull(result)
        coVerify { calendarEventDao.getById(testEventId) }
    }

    @Test
    fun `getByUrl returns event when found`() = runTest {
        // Given
        coEvery { calendarEventDao.getByUrl(testEventUrl) } returns testEventEntity

        // When
        val result = repository.getByUrl(testEventUrl)

        // Then
        assertEquals(testEvent.id, result?.id)
        assertEquals(testEvent.eventUrl, result?.eventUrl)
        coVerify { calendarEventDao.getByUrl(testEventUrl) }
    }

    @Test
    fun `getByUid returns event when found`() = runTest {
        // Given
        coEvery { calendarEventDao.getByUid(testUid) } returns testEventEntity

        // When
        val result = repository.getByUid(testUid)

        // Then
        assertEquals(testEvent.id, result?.id)
        assertEquals(testEvent.uid, result?.uid)
        coVerify { calendarEventDao.getByUid(testUid) }
    }

    @Test
    fun `getByCalendarIdFlow returns flow of events`() = runTest {
        // Given
        val entities = listOf(testEventEntity)
        every { calendarEventDao.getByCalendarIdFlow(testCalendarId) } returns flowOf(entities)

        // When
        val result = repository.getByCalendarIdFlow(testCalendarId).first()

        // Then
        assertEquals(1, result.size)
        assertEquals(testEvent.id, result[0].id)
        assertEquals(testEvent.title, result[0].title)
    }

    @Test
    fun `getByCalendarId returns list of events`() = runTest {
        // Given
        val entities = listOf(testEventEntity)
        coEvery { calendarEventDao.getByCalendarId(testCalendarId) } returns entities

        // When
        val result = repository.getByCalendarId(testCalendarId)

        // Then
        assertEquals(1, result.size)
        assertEquals(testEvent.id, result[0].id)
        coVerify { calendarEventDao.getByCalendarId(testCalendarId) }
    }

    @Test
    fun `getByDateRange returns events in range`() = runTest {
        // Given
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 86400000 // 24 hours
        val entities = listOf(testEventEntity)
        coEvery { 
            calendarEventDao.getByDateRange(testCalendarId, startTime, endTime) 
        } returns entities

        // When
        val result = repository.getByDateRange(testCalendarId, startTime, endTime)

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].dtStart >= startTime && result[0].dtStart < endTime)
        coVerify { calendarEventDao.getByDateRange(testCalendarId, startTime, endTime) }
    }

    @Test
    fun `getDirtyEvents returns modified events`() = runTest {
        // Given
        val dirtyEntity = testEventEntity.copy(dirty = true)
        coEvery { calendarEventDao.getDirtyEvents() } returns listOf(dirtyEntity)

        // When
        val result = repository.getDirtyEvents()

        // Then
        assertEquals(1, result.size)
        assertEquals(true, result[0].dirty)
        coVerify { calendarEventDao.getDirtyEvents() }
    }

    @Test
    fun `getDeletedEvents returns soft-deleted events`() = runTest {
        // Given
        val deletedEntity = testEventEntity.copy(deletedAt = System.currentTimeMillis())
        coEvery { calendarEventDao.getDeletedEvents() } returns listOf(deletedEntity)

        // When
        val result = repository.getDeletedEvents()

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].deletedAt != null)
        coVerify { calendarEventDao.getDeletedEvents() }
    }

    @Test
    fun `markDirty calls dao markDirty`() = runTest {
        // Given
        coEvery { calendarEventDao.markDirty(testEventId) } returns Unit

        // When
        repository.markDirty(testEventId)

        // Then
        coVerify { calendarEventDao.markDirty(testEventId) }
    }

    @Test
    fun `markClean calls dao markClean`() = runTest {
        // Given
        coEvery { calendarEventDao.markClean(testEventId) } returns Unit

        // When
        repository.markClean(testEventId)

        // Then
        coVerify { calendarEventDao.markClean(testEventId) }
    }

    @Test
    fun `softDelete calls dao softDelete`() = runTest {
        // Given
        coEvery { calendarEventDao.softDelete(testEventId, any()) } returns Unit

        // When
        repository.softDelete(testEventId)

        // Then
        coVerify { calendarEventDao.softDelete(testEventId, any()) }
    }

    @Test
    fun `updateETag calls dao with correct parameters`() = runTest {
        // Given
        val newEtag = "etag-456"
        coEvery { calendarEventDao.updateETag(testEventId, newEtag) } returns Unit

        // When
        repository.updateETag(testEventId, newEtag)

        // Then
        coVerify { calendarEventDao.updateETag(testEventId, newEtag) }
    }

    @Test
    fun `updateAndroidEventId calls dao with correct parameters`() = runTest {
        // Given
        val androidEventId = 999L
        coEvery { calendarEventDao.updateAndroidEventId(testEventId, androidEventId) } returns Unit

        // When
        repository.updateAndroidEventId(testEventId, androidEventId)

        // Then
        coVerify { calendarEventDao.updateAndroidEventId(testEventId, androidEventId) }
    }

    @Test
    fun `countByCalendarId returns count`() = runTest {
        // Given
        val expectedCount = 5
        coEvery { calendarEventDao.countByCalendarId(testCalendarId) } returns expectedCount

        // When
        val result = repository.countByCalendarId(testCalendarId)

        // Then
        assertEquals(expectedCount, result)
        coVerify { calendarEventDao.countByCalendarId(testCalendarId) }
    }

    @Test
    fun `deleteByCalendarId calls dao delete`() = runTest {
        // Given
        coEvery { calendarEventDao.deleteByCalendarId(testCalendarId) } returns Unit

        // When
        repository.deleteByCalendarId(testCalendarId)

        // Then
        coVerify { calendarEventDao.deleteByCalendarId(testCalendarId) }
    }

    @Test
    fun `purgeSoftDeleted calls dao purgeSoftDeleted`() = runTest {
        // Given
        coEvery { calendarEventDao.purgeSoftDeleted() } returns Unit

        // When
        repository.purgeSoftDeleted()

        // Then
        coVerify { calendarEventDao.purgeSoftDeleted() }
    }

    @Test
    fun `deleteAll calls dao deleteAll`() = runTest {
        // Given
        coEvery { calendarEventDao.deleteAll() } returns Unit

        // When
        repository.deleteAll()

        // Then
        coVerify { calendarEventDao.deleteAll() }
    }

    @Test
    fun `event with no attendees or reminders maps correctly`() = runTest {
        // Given
        val simpleEvent = testEvent.copy(attendees = emptyList(), reminders = emptyList())
        val simpleEntity = testEventEntity.copy(attendeesJson = null, remindersJson = null)
        coEvery { calendarEventDao.getById(testEventId) } returns simpleEntity

        // When
        val result = repository.getById(testEventId)

        // Then
        assertEquals(0, result?.attendees?.size)
        assertEquals(0, result?.reminders?.size)
    }

    @Test
    fun `event with multiple attendees and reminders maps correctly`() = runTest {
        // Given
        val attendee2 = Attendee("jane@example.com", "Jane Smith", AttendeeRole.OPTIONAL_PARTICIPANT, AttendeeStatus.TENTATIVE)
        val reminder2 = Reminder(30, ReminderMethod.EMAIL)
        
        val multiEvent = testEvent.copy(
            attendees = listOf(testAttendee, attendee2),
            reminders = listOf(testReminder, reminder2)
        )
        
        val multiEntity = testEventEntity.copy(
            attendeesJson = """[{"email":"john@example.com","name":"John Doe","role":"REQUIRED_PARTICIPANT","status":"ACCEPTED"},{"email":"jane@example.com","name":"Jane Smith","role":"OPTIONAL_PARTICIPANT","status":"TENTATIVE"}]""",
            remindersJson = """[{"minutes":15,"method":"ALERT"},{"minutes":30,"method":"EMAIL"}]"""
        )
        
        coEvery { calendarEventDao.getById(testEventId) } returns multiEntity

        // When
        val result = repository.getById(testEventId)

        // Then
        assertEquals(2, result?.attendees?.size)
        assertEquals("jane@example.com", result?.attendees?.get(1)?.email)
        assertEquals(2, result?.reminders?.size)
        assertEquals(30, result?.reminders?.get(1)?.minutes)
    }
}
