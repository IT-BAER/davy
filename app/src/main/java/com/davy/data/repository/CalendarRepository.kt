package com.davy.data.repository

import com.davy.data.local.dao.CalendarDao
import com.davy.data.local.entity.CalendarEntity
import com.davy.domain.model.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Repository for calendar operations.
 * 
 * Handles CRUD operations and provides reactive data streams
 * for calendars.
 */
class CalendarRepository @Inject constructor(
    private val calendarDao: CalendarDao
) {
    
    /**
     * Insert a new calendar.
     * 
     * @return The ID of the inserted calendar
     */
    suspend fun insert(calendar: Calendar): Long {
        return calendarDao.insert(calendar.toEntity())
    }
    
    /**
     * Update an existing calendar.
     */
    suspend fun update(calendar: Calendar) {
        calendarDao.update(calendar.toEntity())
    }
    
    /**
     * Delete a calendar.
     */
    suspend fun delete(calendar: Calendar) {
        calendarDao.delete(calendar.toEntity())
    }
    
    /**
     * Get calendar by ID.
     */
    suspend fun getById(id: Long): Calendar? {
        return calendarDao.getById(id)?.toDomain()
    }
    
    /**
     * Get calendar by URL.
     */
    suspend fun getByUrl(url: String): Calendar? {
        return calendarDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get calendar by Android calendar ID.
     * Useful for mapping Android Calendar Provider events back to DAVy calendars.
     */
    suspend fun getByAndroidCalendarId(androidCalendarId: Long): Calendar? {
        return calendarDao.getByAndroidCalendarId(androidCalendarId)?.toDomain()
    }
    
    /**
     * Get all calendars for an account as Flow.
     */
    fun getByAccountIdFlow(accountId: Long): Flow<List<Calendar>> {
        return calendarDao.getByAccountIdFlow(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all calendars for an account.
     */
    suspend fun getByAccountId(accountId: Long): List<Calendar> {
        return calendarDao.getByAccountId(accountId).map { it.toDomain() }
    }
    
    /**
     * Get all sync-enabled calendars for an account.
     */
    suspend fun getSyncEnabledByAccountId(accountId: Long): List<Calendar> {
        return calendarDao.getSyncEnabledByAccountId(accountId).map { it.toDomain() }
    }
    
    /**
     * Get all visible calendars for an account as Flow.
     */
    fun getVisibleByAccountIdFlow(accountId: Long): Flow<List<Calendar>> {
        return calendarDao.getVisibleByAccountIdFlow(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all calendars as Flow.
     */
    fun getAllFlow(): Flow<List<Calendar>> {
        return calendarDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get count of calendars for an account.
     */
    suspend fun countByAccountId(accountId: Long): Int {
        return calendarDao.countByAccountId(accountId)
    }
    
    /**
     * Update sync token for a calendar.
     */
    suspend fun updateSyncToken(id: Long, syncToken: String) {
        calendarDao.updateSyncToken(id, syncToken, System.currentTimeMillis())
    }
    
    /**
     * Reset sync token for a calendar (forces full sync on next sync operation).
     * This clears the sync token and sets lastSyncedAt to 0, which will trigger
     * a full PROPFIND sync instead of incremental Collection Sync.
     * Use this to force re-download all events for a calendar.
     */
    suspend fun resetSyncToken(id: Long) {
        calendarDao.resetSyncToken(id)
    }
    
    /**
     * Update Android calendar ID.
     */
    suspend fun updateAndroidCalendarId(id: Long, androidCalendarId: Long) {
        calendarDao.updateAndroidCalendarId(id, androidCalendarId)
    }
    
    /**
     * Delete all calendars for an account.
     */
    suspend fun deleteByAccountId(accountId: Long) {
        calendarDao.deleteByAccountId(accountId)
    }
    
    /**
     * Delete all calendars.
     */
    suspend fun deleteAll() {
        calendarDao.deleteAll()
    }
    
    /**
     * Update calendar displayname.
     */
    suspend fun updateDisplayName(id: Long, displayName: String) {
        val calendar = calendarDao.getById(id) ?: return
        calendarDao.update(calendar.copy(displayName = displayName))
    }
    
    /**
     * Convert Calendar domain model to CalendarEntity.
     */
    private fun Calendar.toEntity(): CalendarEntity {
        return CalendarEntity(
            id = id,
            accountId = accountId,
            calendarUrl = calendarUrl,
            displayName = displayName,
            color = color,
            description = description,
            timezone = timezone,
            visible = visible,
            syncEnabled = syncEnabled,
            androidCalendarId = androidCalendarId,
            syncToken = syncToken,
            owner = owner,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastSyncedAt = lastSyncedAt,
            privWriteContent = privWriteContent,
            privUnbind = privUnbind,
            forceReadOnly = forceReadOnly,
            supportsVTODO = supportsVTODO,
            supportsVJOURNAL = supportsVJOURNAL,
            source = source,
            wifiOnlySync = wifiOnlySync,
            syncIntervalMinutes = syncIntervalMinutes
        )
    }
    
    /**
     * Convert CalendarEntity to Calendar domain model.
     */
    private fun CalendarEntity.toDomain(): Calendar {
        return Calendar(
            id = id,
            accountId = accountId,
            calendarUrl = calendarUrl,
            displayName = displayName,
            color = color,
            description = description,
            timezone = timezone,
            visible = visible,
            syncEnabled = syncEnabled,
            androidCalendarId = androidCalendarId,
            syncToken = syncToken,
            owner = owner,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastSyncedAt = lastSyncedAt,
            privWriteContent = privWriteContent,
            privUnbind = privUnbind,
            forceReadOnly = forceReadOnly,
            supportsVTODO = supportsVTODO,
            supportsVJOURNAL = supportsVJOURNAL,
            source = source,
            wifiOnlySync = wifiOnlySync,
            syncIntervalMinutes = syncIntervalMinutes
        )
    }
}
