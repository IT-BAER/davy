package com.davy.data.contentprovider

import android.Manifest
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import com.davy.domain.model.EventStatus
import com.davy.domain.model.Availability
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for Android CalendarContract integration.
 * 
 * Tests the ContentProvider adapter for syncing calendar data between
 * the app's local database and Android's native Calendar app via CalendarContract.
 * 
 * Tests include:
 * - Calendar creation in Android Calendar app
 * - Event creation and retrieval
 * - Event updates and sync
 * - Event deletion
 * - Attendees and reminders
 * - Recurring events
 * 
 * TDD: This test is written BEFORE the full implementation.
 * 
 * Requires: READ_CALENDAR and WRITE_CALENDAR permissions
 */
@RunWith(AndroidJUnit4::class)
class CalendarContentProviderTest {
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    
    private lateinit var context: Context
    private lateinit var adapter: CalendarContentProviderAdapter
    private var testAndroidCalendarId: Long? = null
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adapter = CalendarContentProviderAdapter(context)
        
        // Clean up any existing test calendars
        cleanupTestCalendars()
    }
    
    @After
    fun tearDown() {
        // Clean up test data
        cleanupTestCalendars()
        testAndroidCalendarId?.let { calendarId ->
            cleanupTestEvents(calendarId)
            deleteTestCalendar(calendarId)
        }
    }
    
    @Test
    fun testCreateCalendarInAndroidCalendar() {
        // Given: Calendar model from DAVy app
        val davyCalendar = Calendar(
            id = 1L,
            accountId = 1L,
            calendarUrl = "https://nextcloud.example.com/remote.php/dav/calendars/user/personal/",
            displayName = "Test Calendar",
            description = "Calendar for testing",
            color = 0xFF5733.toInt(),
            timezone = "Europe/Berlin",
            syncEnabled = true,
            createdAt = System.currentTimeMillis()
        )
        
        // When: Insert calendar into Android Calendar via ContentProvider
        val androidCalendarId = adapter.insertCalendar(davyCalendar, "test_account@example.com")
        testAndroidCalendarId = androidCalendarId
        
        // Then: Calendar exists in Android Calendar app
        assertThat(androidCalendarId).isGreaterThan(0L)
        
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR
            ),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(androidCalendarId.toString()),
            null
        )
        
        assertThat(cursor).isNotNull()
        cursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val displayName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
            assertThat(displayName).isEqualTo("Test Calendar")
        }
    }
    
    @Test
    fun testCreateEventInAndroidCalendar() {
        // Given: Calendar exists in Android Calendar
        val davyCalendar = createTestDavyCalendar()
        val androidCalendarId = adapter.insertCalendar(davyCalendar, "test_account@example.com")
        testAndroidCalendarId = androidCalendarId
        
        // Given: Event model from DAVy app
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 3600000 // 1 hour later
        
        val davyEvent = CalendarEvent(
            id = 1L,
            calendarId = davyCalendar.id,
            eventUrl = "https://nextcloud.example.com/event1.ics",
            uid = "test-event-uid-001",
            title = "Test Meeting",
            description = "Integration test meeting",
            location = "Conference Room A",
            dtStart = startTime,
            dtEnd = endTime,
            allDay = false,
            timezone = "Europe/Berlin",
            status = EventStatus.CONFIRMED,
            availability = Availability.BUSY,
            etag = "test-etag",
            dirty = false,
            createdAt = System.currentTimeMillis()
        )
        
        // When: Insert event into Android Calendar via ContentProvider
        val androidEventId = adapter.insertEvent(davyEvent, androidCalendarId)
        
        // Then: Event exists in Android Calendar app
        assertThat(androidEventId).isGreaterThan(0L)
        
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
            ),
            "${CalendarContract.Events._ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )
        
        assertThat(cursor).isNotNull()
        cursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
            val location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))
            assertThat(title).isEqualTo("Test Meeting")
            assertThat(location).isEqualTo("Conference Room A")
        }
    }
    
    @Test
    fun testUpdateEventInAndroidCalendar() {
        // Given: Event exists in Android Calendar
        val davyCalendar = createTestDavyCalendar()
        val androidCalendarId = adapter.insertCalendar(davyCalendar, "test_account@example.com")
        testAndroidCalendarId = androidCalendarId
        
        val davyEvent = createTestDavyEvent(davyCalendar.id)
        val androidEventId = adapter.insertEvent(davyEvent, androidCalendarId)
        
        // When: Update event
        val updatedEvent = davyEvent.copy(
            title = "Updated Meeting Title",
            location = "New Conference Room",
            description = "Updated description"
        )
        val updateResult = adapter.updateEvent(updatedEvent, androidEventId)
        
        // Then: Event is updated in Android Calendar
        assertThat(updateResult).isTrue()
        
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
            ),
            "${CalendarContract.Events._ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )
        
        cursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
            val location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))
            assertThat(title).isEqualTo("Updated Meeting Title")
            assertThat(location).isEqualTo("New Conference Room")
        }
    }
    
    @Test
    fun testDeleteEventFromAndroidCalendar() {
        // Given: Event exists in Android Calendar
        val davyCalendar = createTestDavyCalendar()
        val androidCalendarId = adapter.insertCalendar(davyCalendar, "test_account@example.com")
        testAndroidCalendarId = androidCalendarId
        
        val davyEvent = createTestDavyEvent(davyCalendar.id)
        val androidEventId = adapter.insertEvent(davyEvent, androidCalendarId)
        
        // Verify event exists
        val cursorBefore = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events._ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )
        assertThat(cursorBefore?.count).isEqualTo(1)
        cursorBefore?.close()
        
        // When: Delete event
        val deleteResult = adapter.deleteEvent(androidEventId)
        
        // Then: Event is deleted from Android Calendar
        assertThat(deleteResult).isTrue()
        
        val cursorAfter = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events._ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )
        assertThat(cursorAfter?.count).isEqualTo(0)
        cursorAfter?.close()
    }
    
    @Test
    fun testCreateAllDayEvent() {
        // Given: Calendar exists
        val davyCalendar = createTestDavyCalendar()
        val androidCalendarId = adapter.insertCalendar(davyCalendar, "test_account@example.com")
        testAndroidCalendarId = androidCalendarId
        
        // Given: All-day event
        val startTime = System.currentTimeMillis()
        val davyEvent = createTestDavyEvent(davyCalendar.id).copy(
            title = "All Day Event",
            allDay = true,
            dtStart = startTime,
            dtEnd = startTime + 86400000 // Next day
        )
        
        // When: Insert all-day event
        val androidEventId = adapter.insertEvent(davyEvent, androidCalendarId)
        
        // Then: Event is created as all-day
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.TITLE
            ),
            "${CalendarContract.Events._ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )
        
        cursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val allDay = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY))
            assertThat(allDay).isEqualTo(1) // 1 = true for all-day
        }
    }
    
    @Test
    fun testBidirectionalSync() {
        // Given: Calendar exists
        val davyCalendar = createTestDavyCalendar()
        val androidCalendarId = adapter.insertCalendar(davyCalendar, "test_account@example.com")
        testAndroidCalendarId = androidCalendarId
        
        // When: Create event in DAVy and sync to Android
        val davyEvent = createTestDavyEvent(davyCalendar.id)
        val androidEventId = adapter.insertEvent(davyEvent, androidCalendarId)
        
        // Then: Event exists in Android Calendar
        assertThat(androidEventId).isGreaterThan(0L)
        
        // When: Read event back from Android Calendar
        val readEvent = adapter.getEventById(androidEventId)
        
        // Then: Event data matches original
        assertThat(readEvent).isNotNull()
        assertThat(readEvent?.title).isEqualTo(davyEvent.title)
        assertThat(readEvent?.location).isEqualTo(davyEvent.location)
    }
    
    private fun createTestDavyCalendar(): Calendar {
        return Calendar(
            id = 1L,
            accountId = 1L,
            calendarUrl = "https://nextcloud.example.com/remote.php/dav/calendars/user/test/",
            displayName = "Test Calendar ${System.currentTimeMillis()}",
            description = "Test calendar for integration testing",
            color = 0xFF5733.toInt(),
            timezone = "UTC",
            syncEnabled = true,
            createdAt = System.currentTimeMillis()
        )
    }
    
    private fun createTestDavyEvent(calendarId: Long): CalendarEvent {
        val startTime = System.currentTimeMillis()
        return CalendarEvent(
            id = 1L,
            calendarId = calendarId,
            eventUrl = "https://nextcloud.example.com/event-test.ics",
            uid = "test-event-${System.currentTimeMillis()}",
            title = "Integration Test Event",
            description = "Event for integration testing",
            location = "Test Location",
            dtStart = startTime,
            dtEnd = startTime + 3600000,
            allDay = false,
            timezone = "UTC",
            status = EventStatus.CONFIRMED,
            availability = Availability.BUSY,
            etag = "test-etag",
            dirty = false,
            createdAt = System.currentTimeMillis()
        )
    }
    
    private fun cleanupTestCalendars() {
        // Delete any test calendars from previous test runs
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} LIKE ?",
            arrayOf("Test Calendar%"),
            null
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                cleanupTestEvents(calendarId)
                deleteTestCalendar(calendarId)
            }
        }
    }
    
    private fun cleanupTestEvents(calendarId: Long) {
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString())
        )
    }
    
    private fun deleteTestCalendar(calendarId: Long) {
        context.contentResolver.delete(
            CalendarContract.Calendars.CONTENT_URI,
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(calendarId.toString())
        )
    }
}
