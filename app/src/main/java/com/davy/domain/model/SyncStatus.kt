package com.davy.domain.model

/**
 * Domain model representing the sync status for a resource type.
 * 
 * @property id Unique status identifier
 * @property accountId Foreign key to account
 * @property resourceType Type of resource (CALENDAR, CONTACT, TASK)
 * @property lastSyncTimestamp Timestamp of last successful sync
 * @property lastSyncResult Result of last sync operation
 * @property lastSyncToken Sync token from server for incremental sync
 * @property currentlySyncing Whether a sync is currently in progress
 * @property syncStartedAt Timestamp when current sync started
 * @property itemsSynced Count of items synced in last operation
 * @property errorMessage Error message if last sync failed
 * @property retryCount Number of retry attempts for current sync
 * @property createdAt Timestamp when status was first created
 * @property updatedAt Timestamp when status was last updated
 */
data class SyncStatus(
    val id: Long,
    val accountId: Long,
    val resourceType: ResourceType,
    val lastSyncTimestamp: Long?,
    val lastSyncResult: SyncResult,
    val lastSyncToken: String?,
    val currentlySyncing: Boolean,
    val syncStartedAt: Long?,
    val itemsSynced: Int,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * Check if sync has never been attempted
     */
    fun isNeverSynced(): Boolean = lastSyncTimestamp == null
    
    /**
     * Check if last sync was successful
     */
    fun wasLastSyncSuccessful(): Boolean = lastSyncResult == SyncResult.SUCCESS
    
    /**
     * Check if sync is in error state
     */
    fun isInErrorState(): Boolean = lastSyncResult == SyncResult.FAILED
}

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
