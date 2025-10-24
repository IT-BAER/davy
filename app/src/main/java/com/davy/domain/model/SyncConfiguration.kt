package com.davy.domain.model

/**
 * Domain model representing sync configuration for an account.
 * 
 * @property id Unique configuration identifier
 * @property accountId Foreign key to account
 * @property syncIntervalMinutes Auto-sync interval in minutes (0 = manual only)
 * @property wifiOnlySync Whether to sync only on WiFi
 * @property syncCalendars Whether to sync calendars
 * @property syncContacts Whether to sync contacts
 * @property syncTasks Whether to sync tasks
 * @property syncInBackground Whether background sync is enabled
 * @property conflictResolutionStrategy How to handle sync conflicts
 * @property maxRetries Maximum number of retry attempts for failed syncs
 * @property createdAt Timestamp when configuration was created
 * @property updatedAt Timestamp when configuration was last updated
 */
data class SyncConfiguration(
    val id: Long,
    val accountId: Long,
    val syncIntervalMinutes: Int,
    val wifiOnlySync: Boolean,
    val syncCalendars: Boolean,
    val syncContacts: Boolean,
    val syncTasks: Boolean,
    val syncInBackground: Boolean,
    val conflictResolutionStrategy: ConflictResolutionStrategy,
    val maxRetries: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * Check if auto-sync is enabled
     */
    fun isAutoSyncEnabled(): Boolean = syncIntervalMinutes > 0 && syncInBackground
    
    /**
     * Get sync interval in milliseconds
     */
    fun getSyncIntervalMillis(): Long = syncIntervalMinutes * 60 * 1000L
}

/**
 * Conflict resolution strategy enumeration
 */
enum class ConflictResolutionStrategy {
    /** Server version takes precedence */
    SERVER_WINS,
    
    /** Local version takes precedence */
    LOCAL_WINS,
    
    /** Most recently modified version wins */
    MOST_RECENT_WINS,
    
    /** Ask user to resolve conflict */
    ASK_USER
}
