package com.davy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import com.davy.domain.model.WebCalSubscription

/**
 * WebCal subscription database entity.
 * 
 * Stores webcal:// subscription information for periodic HTTP .ics refresh.
 */
@Entity(
    tableName = "webcal_subscription",
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
        Index("subscription_url", unique = true)
    ]
)
data class WebCalSubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    
    @ColumnInfo(name = "subscription_url")
    val subscriptionUrl: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "color")
    val color: Int,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "sync_enabled")
    val syncEnabled: Boolean = true,
    
    @ColumnInfo(name = "visible")
    val visible: Boolean = true,
    
    @ColumnInfo(name = "android_calendar_id")
    val androidCalendarId: Long? = null,
    
    @ColumnInfo(name = "etag")
    val etag: String? = null,
    
    @ColumnInfo(name = "refresh_interval_minutes")
    val refreshIntervalMinutes: Int = 60,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,
    
    @ColumnInfo(name = "last_sync_error")
    val lastSyncError: String? = null
)

/**
 * Convert entity to domain model.
 */
fun WebCalSubscriptionEntity.toDomain(): WebCalSubscription {
    return WebCalSubscription(
        id = id,
        accountId = accountId,
        subscriptionUrl = subscriptionUrl,
        displayName = displayName,
        color = color,
        description = description,
        syncEnabled = syncEnabled,
        visible = visible,
        androidCalendarId = androidCalendarId,
        etag = etag,
        refreshIntervalMinutes = refreshIntervalMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastSyncedAt = lastSyncedAt,
        lastSyncError = lastSyncError
    )
}

/**
 * Convert domain model to entity.
 */
fun WebCalSubscription.toEntity(): WebCalSubscriptionEntity {
    return WebCalSubscriptionEntity(
        id = id,
        accountId = accountId,
        subscriptionUrl = subscriptionUrl,
        displayName = displayName,
        color = color,
        description = description,
        syncEnabled = syncEnabled,
        visible = visible,
        androidCalendarId = androidCalendarId,
        etag = etag,
        refreshIntervalMinutes = refreshIntervalMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastSyncedAt = lastSyncedAt,
        lastSyncError = lastSyncError
    )
}
