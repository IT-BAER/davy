package com.davy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the sync status for a resource type.
 * 
 * Tracks the state and history of sync operations per account and resource type.
 * 
 * @property id Unique status identifier (auto-generated)
 * @property accountId Foreign key to accounts table
 * @property resourceType Type of resource (CALENDAR, CONTACT, TASK)
 * @property lastSyncTimestamp Timestamp of last successful sync
 * @property lastSyncResult Result of last sync operation
 * @property lastSyncToken Sync token from server for incremental sync
 * @property currentlySyncing Whether a sync is currently in progress
 * @property syncStartedAt Timestamp when current sync started (null if not syncing)
 * @property itemsSynced Count of items synced in last operation
 * @property errorMessage Error message if last sync failed
 * @property retryCount Number of retry attempts for current sync
 * @property createdAt Timestamp when status was first created
 * @property updatedAt Timestamp when status was last updated
 */
@Entity(
    tableName = "sync_status",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("account_id"),
        Index(value = ["account_id", "resource_type"], unique = true)
    ]
)
data class SyncStatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    
    @ColumnInfo(name = "resource_type")
    val resourceType: String, // ResourceType enum name
    
    @ColumnInfo(name = "last_sync_timestamp")
    val lastSyncTimestamp: Long? = null,
    
    @ColumnInfo(name = "last_sync_result")
    val lastSyncResult: String = SyncResult.PENDING.name,
    
    @ColumnInfo(name = "last_sync_token")
    val lastSyncToken: String? = null,
    
    @ColumnInfo(name = "currently_syncing")
    val currentlySyncing: Boolean = false,
    
    @ColumnInfo(name = "sync_started_at")
    val syncStartedAt: Long? = null,
    
    @ColumnInfo(name = "items_synced")
    val itemsSynced: Int = 0,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Resource type enumeration
 */
enum class ResourceType {
    /** Calendar events */
    CALENDAR,
    
    /** Contacts */
    CONTACT,
    
    /** Tasks/todos */
    TASK
}

/**
 * Sync result enumeration
 */
enum class SyncResult {
    /** Sync not yet attempted */
    PENDING,
    
    /** Sync completed successfully */
    SUCCESS,
    
    /** Sync failed with error */
    FAILED,
    
    /** Sync cancelled by user */
    CANCELLED,
    
    /** Sync in progress */
    IN_PROGRESS
}
