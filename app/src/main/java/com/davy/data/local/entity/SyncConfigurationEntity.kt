package com.davy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing sync configuration for an account.
 * 
 * Stores user preferences for how sync should behave (frequency, WiFi-only, etc.)
 * 
 * @property id Unique configuration identifier (auto-generated)
 * @property accountId Foreign key to accounts table
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
@Entity(
    tableName = "sync_configurations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("account_id", unique = true)]
)
data class SyncConfigurationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    
    @ColumnInfo(name = "sync_interval_minutes")
    val syncIntervalMinutes: Int = 60,  // Default: 1 hour
    
    @ColumnInfo(name = "wifi_only_sync")
    val wifiOnlySync: Boolean = false,
    
    @ColumnInfo(name = "sync_calendars")
    val syncCalendars: Boolean = true,
    
    @ColumnInfo(name = "sync_contacts")
    val syncContacts: Boolean = true,
    
    @ColumnInfo(name = "sync_tasks")
    val syncTasks: Boolean = true,
    
    @ColumnInfo(name = "sync_in_background")
    val syncInBackground: Boolean = true,
    
    @ColumnInfo(name = "conflict_resolution_strategy")
    val conflictResolutionStrategy: String = ConflictResolutionStrategy.SERVER_WINS.name,
    
    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

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
