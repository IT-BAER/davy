package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for CalendarEvent entity operations.
 * 
 * Provides database access for calendar events with support for
 * recurrence, conflict detection, and sync management.
 */
@Dao
interface CalendarEventDao {
    
    /**
     * Insert a new event.
     * 
     * @return The ID of the inserted event
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity): Long
    
    /**
     * Insert multiple events.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEventEntity>): List<Long>
    
    /**
     * Update an existing event.
     */
    @Update
    suspend fun update(event: CalendarEventEntity)
    
    /**
     * Delete an event.
     */
    @Delete
    suspend fun delete(event: CalendarEventEntity)
    
    /**
     * Get event by ID.
     */
    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: Long): CalendarEventEntity?
    
    /**
     * Get event by URL.
     */
    @Query("SELECT * FROM calendar_events WHERE eventUrl = :url")
    suspend fun getByUrl(url: String): CalendarEventEntity?
    
    /**
     * Get event by UID.
     */
    @Query("SELECT * FROM calendar_events WHERE uid = :uid")
    suspend fun getByUid(uid: String): CalendarEventEntity?
    
    /**
     * Get all events for a calendar as Flow.
     */
    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId AND deletedAt IS NULL ORDER BY dtStart ASC")
    fun getByCalendarIdFlow(calendarId: Long): Flow<List<CalendarEventEntity>>
    
    /**
     * Get all events for a calendar.
     */
    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId AND deletedAt IS NULL ORDER BY dtStart ASC")
    suspend fun getByCalendarId(calendarId: Long): List<CalendarEventEntity>
    
    /**
     * Get events in date range.
     */
    @Query(
        """SELECT * FROM calendar_events 
        WHERE calendarId = :calendarId 
        AND deletedAt IS NULL 
        AND dtStart >= :startTime 
        AND dtStart < :endTime 
        ORDER BY dtStart ASC"""
    )
    suspend fun getByDateRange(
        calendarId: Long,
        startTime: Long,
        endTime: Long
    ): List<CalendarEventEntity>
    
    /**
     * Get all dirty (locally modified) events.
     */
    @Query("SELECT * FROM calendar_events WHERE dirty = 1 AND deletedAt IS NULL")
    suspend fun getDirtyEvents(): List<CalendarEventEntity>
    
    /**
     * Get dirty events for a specific calendar (includes deleted events marked as dirty).
     */
    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId AND dirty = 1")
    suspend fun getDirtyEventsByCalendarId(calendarId: Long): List<CalendarEventEntity>
    
    /**
     * Get event by Android event ID.
     */
    @Query("SELECT * FROM calendar_events WHERE androidEventId = :androidEventId")
    suspend fun getByAndroidEventId(androidEventId: Long): CalendarEventEntity?
    
    /**
     * Get all deleted events (soft delete).
     */
    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NOT NULL")
    suspend fun getDeletedEvents(): List<CalendarEventEntity>
    
    /**
     * Mark event as dirty (modified locally).
     */
    @Query("UPDATE calendar_events SET dirty = 1 WHERE id = :id")
    suspend fun markDirty(id: Long)
    
    /**
     * Mark event as clean (synced).
     */
    @Query("UPDATE calendar_events SET dirty = 0 WHERE id = :id")
    suspend fun markClean(id: Long)
    
    /**
     * Soft delete an event.
     */
    @Query("UPDATE calendar_events SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Update ETag for an event.
     */
    @Query("UPDATE calendar_events SET etag = :etag WHERE id = :id")
    suspend fun updateETag(id: Long, etag: String)
    
    /**
     * Update Android event ID.
     */
    @Query("UPDATE calendar_events SET androidEventId = :androidEventId WHERE id = :id")
    suspend fun updateAndroidEventId(id: Long, androidEventId: Long)
    
    /**
     * Get count of events for a calendar.
     */
    @Query("SELECT COUNT(*) FROM calendar_events WHERE calendarId = :calendarId AND deletedAt IS NULL")
    suspend fun countByCalendarId(calendarId: Long): Int
    
    /**
     * Delete all events for a calendar.
     */
    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteByCalendarId(calendarId: Long)
    
    /**
     * Delete all soft-deleted events.
     */
    @Query("DELETE FROM calendar_events WHERE deletedAt IS NOT NULL")
    suspend fun purgeSoftDeleted()
    
    /**
     * Delete all events.
     */
    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}
