package com.davy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davy.data.local.entity.SyncConfigurationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) for SyncConfiguration operations.
 * 
 * Provides database operations for managing sync preferences.
 */
@Dao
interface SyncConfigurationDao {
    
    /**
     * Insert a new sync configuration.
     * 
     * @param config The configuration to insert
     * @return The ID of the inserted configuration
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SyncConfigurationEntity): Long
    
    /**
     * Update an existing sync configuration.
     * 
     * @param config The configuration to update
     */
    @Update
    suspend fun update(config: SyncConfigurationEntity)
    
    /**
     * Delete a sync configuration.
     * 
     * @param config The configuration to delete
     */
    @Delete
    suspend fun delete(config: SyncConfigurationEntity)
    
    /**
     * Get sync configuration by ID.
     * 
     * @param configId The configuration ID
     * @return The configuration, or null if not found
     */
    @Query("SELECT * FROM sync_configurations WHERE id = :configId")
    suspend fun getById(configId: Long): SyncConfigurationEntity?
    
    /**
     * Get sync configuration for an account.
     * 
     * @param accountId The account ID
     * @return The configuration, or null if not found
     */
    @Query("SELECT * FROM sync_configurations WHERE account_id = :accountId")
    suspend fun getByAccountId(accountId: Long): SyncConfigurationEntity?
    
    /**
     * Get sync configuration for an account as Flow.
     * 
     * @param accountId The account ID
     * @return Flow emitting the configuration
     */
    @Query("SELECT * FROM sync_configurations WHERE account_id = :accountId")
    fun getByAccountIdFlow(accountId: Long): Flow<SyncConfigurationEntity?>
    
    /**
     * Get all sync configurations.
     * 
     * @return Flow emitting list of all configurations
     */
    @Query("SELECT * FROM sync_configurations")
    fun getAllFlow(): Flow<List<SyncConfigurationEntity>>
    
    /**
     * Get configurations with background sync enabled.
     * 
     * @return List of configurations with background sync enabled
     */
    @Query("SELECT * FROM sync_configurations WHERE sync_in_background = 1")
    suspend fun getBackgroundSyncEnabled(): List<SyncConfigurationEntity>
    
    /**
     * Delete all sync configurations (for testing).
     */
    @Query("DELETE FROM sync_configurations")
    suspend fun deleteAll()
}
