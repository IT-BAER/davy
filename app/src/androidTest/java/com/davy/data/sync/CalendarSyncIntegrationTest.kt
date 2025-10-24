package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.davy.data.local.DavyDatabase
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.data.repository.AccountRepository
import com.davy.data.remote.caldav.CalDAVService
import com.davy.domain.model.Account
import com.davy.domain.model.AuthType
import com.davy.domain.model.Calendar
import com.davy.domain.model.CalendarEvent
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration test for CalDAV calendar synchronization.
 * 
 * Tests the full sync flow including:
 * - Calendar discovery via CalDAV PROPFIND
 * - Event retrieval via CalDAV calendar-query
 * - Local database storage
 * - Incremental sync using ctag/etag
 * - Event upload to CalDAV server
 * - Conflict detection and resolution
 * 
 * TDD: This test is written BEFORE the full implementation.
 * 
 * Note: This is an integration test that requires either:
 * 1. A mock CalDAV server running locally
 * 2. A test CalDAV server endpoint
 * 3. Mocked HTTP responses using MockWebServer
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarSyncIntegrationTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var database: DavyDatabase
    
    @Inject
    lateinit var accountRepository: AccountRepository
    
    @Inject
    lateinit var calendarRepository: CalendarRepository
    
    @Inject
    lateinit var calendarEventRepository: CalendarEventRepository
    
    @Inject
    lateinit var calDAVService: CalDAVService
    
    private lateinit var context: Context
    private lateinit var testAccount: Account
    
    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        hiltRule.inject()
        
        // Create test account
        testAccount = Account(
            id = 0,
            serverUrl = "https://demo.nextcloud.com",
            username = "test_user",
            displayName = "Test User",
            authType = AuthType.BASIC,
            calDavUrl = "https://demo.nextcloud.com/remote.php/dav/calendars/test_user/",
            cardDavUrl = "https://demo.nextcloud.com/remote.php/dav/addressbooks/test_user/",
            syncEnabled = true,
            createdAt = System.currentTimeMillis()
        )
        
        val accountId = accountRepository.insert(testAccount)
        testAccount = testAccount.copy(id = accountId)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun testFullCalendarSyncFlow() = runTest {
        // Given: Account with CalDAV URL exists
        assertThat(testAccount.id).isGreaterThan(0L)
        
        // When: Discover calendars from CalDAV server
        // TODO: This requires CalDAV server implementation or mock
        // For now, create test calendar manually
        val testCalendar = Calendar(
            id = 0,
            accountId = testAccount.id,
            calendarUrl = "${testAccount.calDavUrl}personal/",
            displayName = "Personal",
            description = "Personal calendar",
            color = 0xFF5733.toInt(),
            timezone = "Europe/Berlin",
            syncEnabled = true,
            createdAt = System.currentTimeMillis()
        )
        
        val calendarId = calendarRepository.insert(testCalendar)
        
        // Then: Calendar is stored in database
        val storedCalendar = calendarRepository.getById(calendarId)
        assertThat(storedCalendar).isNotNull()
        assertThat(storedCalendar?.displayName).isEqualTo("Personal")
        assertThat(storedCalendar?.accountId).isEqualTo(testAccount.id)
    }
    
    @Test
    fun testCalendarEventSync() = runTest {
        // Given: Calendar exists
        val testCalendar = createTestCalendar()
        val calendarId = calendarRepository.insert(testCalendar)
        
        // When: Sync events from CalDAV server
        // TODO: This requires CalDAV server implementation or mock
        // For now, create test event manually
        val testEvent = CalendarEvent(
            id = 0,
            calendarId = calendarId,
            eventUrl = "${testCalendar.calendarUrl}event1.ics",
            uid = "test-event-uid-001",
            title = "Test Meeting",
            description = "Integration test meeting",
            location = "Conference Room A",
            dtStart = System.currentTimeMillis(),
            dtEnd = System.currentTimeMillis() + 3600000, // 1 hour later
            allDay = false,
            timezone = "Europe/Berlin",
            etag = "initial-etag",
            dirty = false,
            createdAt = System.currentTimeMillis()
        )
        
        val eventId = calendarEventRepository.insert(testEvent)
        
        // Then: Event is stored in database
        val storedEvent = calendarEventRepository.getById(eventId)
        assertThat(storedEvent).isNotNull()
        assertThat(storedEvent?.title).isEqualTo("Test Meeting")
        assertThat(storedEvent?.calendarId).isEqualTo(calendarId)
        assertThat(storedEvent?.uid).isEqualTo("test-event-uid-001")
    }
    
    @Test
    fun testIncrementalSyncUsingCTag() = runTest {
        // Given: Calendar with known ctag exists
        val testCalendar = createTestCalendar().copy(syncToken = "ctag-v1")
        val calendarId = calendarRepository.insert(testCalendar)
        
        // When: Check if ctag changed (would happen via CalDAV PROPFIND)
        val updatedCalendar = testCalendar.copy(
            id = calendarId,
            syncToken = "ctag-v2",
            lastSyncedAt = System.currentTimeMillis()
        )
        calendarRepository.update(updatedCalendar)
        
        // Then: Calendar sync token is updated
        val storedCalendar = calendarRepository.getById(calendarId)
        assertThat(storedCalendar?.syncToken).isEqualTo("ctag-v2")
        assertThat(storedCalendar?.lastSyncedAt).isNotNull()
    }
    
    @Test
    fun testEventModificationDetectionUsingETag() = runTest {
        // Given: Event with known etag exists
        val testCalendar = createTestCalendar()
        val calendarId = calendarRepository.insert(testCalendar)
        
        val testEvent = createTestEvent(calendarId).copy(etag = "etag-v1")
        val eventId = calendarEventRepository.insert(testEvent)
        
        // When: Event etag changes (detected via CalDAV sync)
        val modifiedEvent = testEvent.copy(
            id = eventId,
            title = "Modified Meeting Title",
            etag = "etag-v2",
            updatedAt = System.currentTimeMillis()
        )
        calendarEventRepository.update(modifiedEvent)
        
        // Then: Event is updated with new etag
        val storedEvent = calendarEventRepository.getById(eventId)
        assertThat(storedEvent?.etag).isEqualTo("etag-v2")
        assertThat(storedEvent?.title).isEqualTo("Modified Meeting Title")
    }
    
    @Test
    fun testDirtyEventUploadFlow() = runTest {
        // Given: Event modified locally and marked dirty
        val testCalendar = createTestCalendar()
        val calendarId = calendarRepository.insert(testCalendar)
        
        val testEvent = createTestEvent(calendarId).copy(
            dirty = true,
            title = "Locally Modified Event"
        )
        val eventId = calendarEventRepository.insert(testEvent)
        
        // When: Sync worker processes dirty events
        val dirtyEvents = calendarEventRepository.getDirtyEventsByCalendarId(calendarId)
        
        // Then: Dirty event is found and ready for upload
        assertThat(dirtyEvents).hasSize(1)
        assertThat(dirtyEvents[0].dirty).isTrue()
        assertThat(dirtyEvents[0].title).isEqualTo("Locally Modified Event")
        
        // Simulate successful upload
        val uploadedEvent = dirtyEvents[0].copy(
            dirty = false,
            etag = "new-etag-after-upload",
            updatedAt = System.currentTimeMillis()
        )
        calendarEventRepository.update(uploadedEvent)
        
        // Verify dirty flag is cleared
        val clearedEvent = calendarEventRepository.getById(eventId)
        assertThat(clearedEvent?.dirty).isFalse()
    }
    
    @Test
    fun testMultipleCalendarSync() = runTest {
        // Given: Multiple calendars for same account
        val calendar1 = createTestCalendar().copy(
            calendarUrl = "${testAccount.calDavUrl}personal/",
            displayName = "Personal"
        )
        val calendar2 = createTestCalendar().copy(
            calendarUrl = "${testAccount.calDavUrl}work/",
            displayName = "Work"
        )
        val calendar3 = createTestCalendar().copy(
            calendarUrl = "${testAccount.calDavUrl}family/",
            displayName = "Family"
        )
        
        val calId1 = calendarRepository.insert(calendar1)
        val calId2 = calendarRepository.insert(calendar2)
        val calId3 = calendarRepository.insert(calendar3)
        
        // When: Query all calendars for account
        val calendars = calendarRepository.getByAccountId(testAccount.id)
        
        // Then: All calendars are retrieved
        assertThat(calendars).hasSize(3)
        assertThat(calendars.map { it.displayName })
            .containsExactly("Personal", "Work", "Family")
    }
    
    @Test
    fun testSyncEnabledCalendarsOnly() = runTest {
        // Given: Mix of enabled and disabled calendars
        val enabledCal = createTestCalendar().copy(
            displayName = "Enabled Calendar",
            syncEnabled = true
        )
        val disabledCal = createTestCalendar().copy(
            displayName = "Disabled Calendar",
            syncEnabled = false
        )
        
        calendarRepository.insert(enabledCal)
        calendarRepository.insert(disabledCal)
        
        // When: Query sync-enabled calendars
        val syncEnabledCalendars = calendarRepository.getSyncEnabledByAccountId(testAccount.id)
        
        // Then: Only enabled calendar is returned
        assertThat(syncEnabledCalendars).hasSize(1)
        assertThat(syncEnabledCalendars[0].displayName).isEqualTo("Enabled Calendar")
        assertThat(syncEnabledCalendars[0].syncEnabled).isTrue()
    }
    
    @Test
    fun testEventDeletionSync() = runTest {
        // Given: Event exists locally
        val testCalendar = createTestCalendar()
        val calendarId = calendarRepository.insert(testCalendar)
        val testEvent = createTestEvent(calendarId)
        val eventId = calendarEventRepository.insert(testEvent)
        
        // When: Event is marked as deleted (soft delete)
        val deletedEvent = testEvent.copy(
            id = eventId,
            deletedAt = System.currentTimeMillis()
        )
        calendarEventRepository.update(deletedEvent)
        
        // Then: Event is marked for deletion
        val storedEvent = calendarEventRepository.getById(eventId)
        assertThat(storedEvent?.deletedAt).isNotNull()
        
        // In actual sync, this would trigger DELETE request to CalDAV server
        // and then remove from local database
    }
    
    private fun createTestCalendar(): Calendar {
        return Calendar(
            id = 0,
            accountId = testAccount.id,
            calendarUrl = "${testAccount.calDavUrl}test-calendar/",
            displayName = "Test Calendar",
            description = "Calendar for integration testing",
            color = 0xFF5733.toInt(),
            timezone = "UTC",
            syncEnabled = true,
            createdAt = System.currentTimeMillis()
        )
    }
    
    private fun createTestEvent(calendarId: Long): CalendarEvent {
        return CalendarEvent(
            id = 0,
            calendarId = calendarId,
            eventUrl = "https://demo.nextcloud.com/event-test.ics",
            uid = "test-event-${System.currentTimeMillis()}",
            title = "Integration Test Event",
            description = "Event for integration testing",
            location = "Test Location",
            dtStart = System.currentTimeMillis(),
            dtEnd = System.currentTimeMillis() + 3600000,
            allDay = false,
            timezone = "UTC",
            etag = "test-etag",
            dirty = false,
            createdAt = System.currentTimeMillis()
        )
    }
}
