package com.davy.data.repository

import com.davy.data.local.dao.CalendarEventDao
import com.davy.data.local.entity.CalendarEventEntity
import com.davy.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

/**
 * Repository for calendar event operations.
 * 
 * Handles CRUD operations and provides reactive data streams
 * for calendar events.
 */
class CalendarEventRepository @Inject constructor(
    private val calendarEventDao: CalendarEventDao
) {
    
    private val gson = Gson()
    
    /**
     * Insert a new event.
     * 
     * @return The ID of the inserted event
     */
    suspend fun insert(event: CalendarEvent): Long {
        return calendarEventDao.insert(event.toEntity())
    }
    
    /**
     * Insert multiple events.
     */
    suspend fun insertAll(events: List<CalendarEvent>): List<Long> {
        return calendarEventDao.insertAll(events.map { it.toEntity() })
    }
    
    /**
     * Update an existing event.
     */
    suspend fun update(event: CalendarEvent) {
        calendarEventDao.update(event.toEntity())
    }
    
    /**
     * Delete an event.
     */
    suspend fun delete(event: CalendarEvent) {
        calendarEventDao.delete(event.toEntity())
    }
    
    /**
     * Get event by ID.
     */
    suspend fun getById(id: Long): CalendarEvent? {
        return calendarEventDao.getById(id)?.toDomain()
    }
    
    /**
     * Get event by URL.
     */
    suspend fun getByUrl(url: String): CalendarEvent? {
        return calendarEventDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get event by UID.
     */
    suspend fun getByUid(uid: String): CalendarEvent? {
        return calendarEventDao.getByUid(uid)?.toDomain()
    }
    
    /**
     * Get all events for a calendar as Flow.
     */
    fun getByCalendarIdFlow(calendarId: Long): Flow<List<CalendarEvent>> {
        return calendarEventDao.getByCalendarIdFlow(calendarId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all events for a calendar.
     */
    suspend fun getByCalendarId(calendarId: Long): List<CalendarEvent> {
        return calendarEventDao.getByCalendarId(calendarId).map { it.toDomain() }
    }
    
    /**
     * Get events in date range.
     */
    suspend fun getByDateRange(
        calendarId: Long,
        startTime: Long,
        endTime: Long
    ): List<CalendarEvent> {
        return calendarEventDao.getByDateRange(calendarId, startTime, endTime)
            .map { it.toDomain() }
    }
    
    /**
     * Get all dirty (locally modified) events.
     */
    suspend fun getDirtyEvents(): List<CalendarEvent> {
        return calendarEventDao.getDirtyEvents().map { it.toDomain() }
    }
    
    /**
     * Get dirty events for a specific calendar.
     */
    suspend fun getDirtyEventsByCalendarId(calendarId: Long): List<CalendarEvent> {
        return calendarEventDao.getDirtyEventsByCalendarId(calendarId).map { it.toDomain() }
    }
    
    /**
     * Get event by Android event ID.
     */
    suspend fun getByAndroidEventId(androidEventId: Long): CalendarEvent? {
        return calendarEventDao.getByAndroidEventId(androidEventId)?.toDomain()
    }
    
    /**
     * Get all deleted events.
     */
    suspend fun getDeletedEvents(): List<CalendarEvent> {
        return calendarEventDao.getDeletedEvents().map { it.toDomain() }
    }
    
    /**
     * Mark event as dirty (modified locally).
     */
    suspend fun markDirty(id: Long) {
        calendarEventDao.markDirty(id)
    }
    
    /**
     * Mark event as clean (synced).
     */
    suspend fun markClean(id: Long) {
        calendarEventDao.markClean(id)
    }
    
    /**
     * Soft delete an event.
     */
    suspend fun softDelete(id: Long) {
        calendarEventDao.softDelete(id)
    }
    
    /**
     * Update ETag for an event.
     */
    suspend fun updateETag(id: Long, etag: String) {
        calendarEventDao.updateETag(id, etag)
    }
    
    /**
     * Update Android event ID.
     */
    suspend fun updateAndroidEventId(id: Long, androidEventId: Long) {
        calendarEventDao.updateAndroidEventId(id, androidEventId)
    }
    
    /**
     * Get count of events for a calendar.
     */
    suspend fun countByCalendarId(calendarId: Long): Int {
        return calendarEventDao.countByCalendarId(calendarId)
    }
    
    /**
     * Delete all events for a calendar.
     */
    suspend fun deleteByCalendarId(calendarId: Long) {
        calendarEventDao.deleteByCalendarId(calendarId)
    }
    
    /**
     * Delete all soft-deleted events.
     */
    suspend fun purgeSoftDeleted() {
        calendarEventDao.purgeSoftDeleted()
    }
    
    /**
     * Delete all events.
     */
    suspend fun deleteAll() {
        calendarEventDao.deleteAll()
    }
    
    /**
     * Convert CalendarEvent domain model to CalendarEventEntity.
     */
    private fun CalendarEvent.toEntity(): CalendarEventEntity {
        return CalendarEventEntity(
            id = id,
            calendarId = calendarId,
            eventUrl = eventUrl,
            uid = uid,
            title = title,
            description = description,
            location = location,
            dtStart = dtStart,
            dtEnd = dtEnd,
            allDay = allDay,
            timezone = timezone,
            status = status?.name,
            availability = availability?.name,
            rrule = rrule,
            rdate = rdate,
            exdate = exdate,
            recurrenceId = recurrenceId,
            originalEventId = originalEventId,
            organizer = organizer,
            attendeesJson = if (attendees.isNotEmpty()) gson.toJson(attendees) else null,
            remindersJson = if (reminders.isNotEmpty()) gson.toJson(reminders) else null,
            etag = etag,
            androidEventId = androidEventId,
            dirty = dirty,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt
        )
    }
    
    /**
     * Convert CalendarEventEntity to CalendarEvent domain model.
     */
    private fun CalendarEventEntity.toDomain(): CalendarEvent {
        val attendeesList = attendeesJson?.let {
            val type = object : TypeToken<List<Attendee>>() {}.type
            gson.fromJson<List<Attendee>>(it, type)
        } ?: emptyList()
        
        val remindersList = remindersJson?.let {
            val type = object : TypeToken<List<Reminder>>() {}.type
            gson.fromJson<List<Reminder>>(it, type)
        } ?: emptyList()
        
        return CalendarEvent(
            id = id,
            calendarId = calendarId,
            eventUrl = eventUrl,
            uid = uid,
            title = title,
            description = description,
            location = location,
            dtStart = dtStart,
            dtEnd = dtEnd,
            allDay = allDay,
            timezone = timezone,
            status = status?.let { EventStatus.valueOf(it) },
            availability = availability?.let { com.davy.domain.model.Availability.valueOf(it) },
            rrule = rrule,
            rdate = rdate,
            exdate = exdate,
            recurrenceId = recurrenceId,
            originalEventId = originalEventId,
            organizer = organizer,
            attendees = attendeesList,
            reminders = remindersList,
            etag = etag,
            androidEventId = androidEventId,
            dirty = dirty,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt
        )
    }
}
