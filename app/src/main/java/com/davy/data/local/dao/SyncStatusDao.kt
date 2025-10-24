package com.davy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davy.data.local.entity.SyncStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) for SyncStatus operations.
 * 
 * Provides database operations for tracking sync status and history.
 */
@Dao
interface SyncStatusDao {
    
    /**
     * Insert a new sync status.
     * 
     * @param status The status to insert
     * @return The ID of the inserted status
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(status: SyncStatusEntity): Long
    
    /**
     * Update an existing sync status.
     * 
     * @param status The status to update
     */
    @Update
    suspend fun update(status: SyncStatusEntity)
    
    /**
     * Delete a sync status.
     * 
     * @param status The status to delete
     */
    @Delete
    suspend fun delete(status: SyncStatusEntity)
    
    /**
     * Get sync status by ID.
     * 
     * @param statusId The status ID
     * @return The status, or null if not found
     */
    @Query("SELECT * FROM sync_status WHERE id = :statusId")
    suspend fun getById(statusId: Long): SyncStatusEntity?
    
    /**
     * Get sync status for an account and resource type.
     * 
     * @param accountId The account ID
     * @param resourceType The resource type
     * @return The status, or null if not found
     */
    @Query("SELECT * FROM sync_status WHERE account_id = :accountId AND resource_type = :resourceType")
    suspend fun getByAccountAndResource(accountId: Long, resourceType: String): SyncStatusEntity?
    
    /**
     * Get sync status for an account and resource type as Flow.
     * 
     * @param accountId The account ID
     * @param resourceType The resource type
     * @return Flow emitting the status
     */
    @Query("SELECT * FROM sync_status WHERE account_id = :accountId AND resource_type = :resourceType")
    fun getByAccountAndResourceFlow(accountId: Long, resourceType: String): Flow<SyncStatusEntity?>
    
    /**
     * Get all sync statuses for an account.
     * 
     * @param accountId The account ID
     * @return Flow emitting list of statuses
     */
    @Query("SELECT * FROM sync_status WHERE account_id = :accountId")
    fun getByAccountFlow(accountId: Long): Flow<List<SyncStatusEntity>>
    
    /**
     * Get all sync statuses.
     * 
     * @return Flow emitting list of all statuses
     */
    @Query("SELECT * FROM sync_status")
    fun getAllFlow(): Flow<List<SyncStatusEntity>>
    
    /**
     * Get currently syncing statuses.
     * 
     * @return List of statuses where sync is in progress
     */
    @Query("SELECT * FROM sync_status WHERE currently_syncing = 1")
    suspend fun getCurrentlySyncing(): List<SyncStatusEntity>
    
    /**
     * Get failed sync statuses.
     * 
     * @return List of statuses where last sync failed
     */
    @Query("SELECT * FROM sync_status WHERE last_sync_result = 'FAILED'")
    suspend fun getFailed(): List<SyncStatusEntity>
    
    /**
     * Delete all sync statuses for an account.
     * 
     * @param accountId The account ID
     */
    @Query("DELETE FROM sync_status WHERE account_id = :accountId")
    suspend fun deleteByAccount(accountId: Long)
    
    /**
     * Delete all sync statuses (for testing).
     */
    @Query("DELETE FROM sync_status")
    suspend fun deleteAll()
}
