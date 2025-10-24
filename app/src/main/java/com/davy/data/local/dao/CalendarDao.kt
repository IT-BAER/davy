package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Calendar entity operations.
 * 
 * Provides database access for calendars with Flow-based queries
 * for reactive UI updates.
 */
@Dao
interface CalendarDao {
    
    /**
     * Insert a new calendar.
     * 
     * @return The ID of the inserted calendar
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendar: CalendarEntity): Long
    
    /**
     * Update an existing calendar.
     */
    @Update
    suspend fun update(calendar: CalendarEntity)
    
    /**
     * Delete a calendar.
     */
    @Delete
    suspend fun delete(calendar: CalendarEntity)
    
    /**
     * Get calendar by ID.
     */
    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getById(id: Long): CalendarEntity?
    
    /**
     * Get calendar by URL.
     */
    @Query("SELECT * FROM calendars WHERE calendarUrl = :url")
    suspend fun getByUrl(url: String): CalendarEntity?
    
    /**
     * Get all calendars for an account as Flow.
     */
    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY displayName ASC")
    fun getByAccountIdFlow(accountId: Long): Flow<List<CalendarEntity>>
    
    /**
     * Get all calendars for an account.
     */
    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY displayName ASC")
    suspend fun getByAccountId(accountId: Long): List<CalendarEntity>
    
    /**
     * Get all sync-enabled calendars for an account.
     */
    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND syncEnabled = 1 ORDER BY displayName ASC")
    suspend fun getSyncEnabledByAccountId(accountId: Long): List<CalendarEntity>
    
    /**
     * Get all visible calendars for an account.
     */
    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND visible = 1 ORDER BY displayName ASC")
    fun getVisibleByAccountIdFlow(accountId: Long): Flow<List<CalendarEntity>>
    
    /**
     * Get all calendars as Flow.
     */
    @Query("SELECT * FROM calendars ORDER BY displayName ASC")
    fun getAllFlow(): Flow<List<CalendarEntity>>
    
    /**
     * Get count of calendars for an account.
     */
    @Query("SELECT COUNT(*) FROM calendars WHERE accountId = :accountId")
    suspend fun countByAccountId(accountId: Long): Int
    
    /**
     * Update sync token for a calendar.
     */
    @Query("UPDATE calendars SET syncToken = :syncToken, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateSyncToken(id: Long, syncToken: String, timestamp: Long)
    
    /**
     * Update Android calendar ID.
     */
    @Query("UPDATE calendars SET androidCalendarId = :androidCalendarId WHERE id = :id")
    suspend fun updateAndroidCalendarId(id: Long, androidCalendarId: Long)
    
    /**
     * Delete all calendars for an account.
     */
    @Query("DELETE FROM calendars WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
    
    /**
     * Delete all calendars.
     */
    @Query("DELETE FROM calendars")
    suspend fun deleteAll()
    
    /**
     * Reset sync token for a calendar (forces full sync on next sync operation).
     * Sets syncToken to NULL and lastSyncedAt to 0 to trigger PROPFIND full sync.
     */
    @Query("UPDATE calendars SET syncToken = NULL, lastSyncedAt = 0 WHERE id = :id")
    suspend fun resetSyncToken(id: Long)
}
