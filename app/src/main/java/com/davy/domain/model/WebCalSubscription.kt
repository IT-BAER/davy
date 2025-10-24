package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * WebCal subscription domain model.
 * 
 * Represents a webcal:// subscription (read-only calendar feed via HTTP).
 * WebCal subscriptions are periodically refreshed via HTTP GET of .ics files.
 */
@Immutable
data class WebCalSubscription(
    val id: Long = 0,
    val accountId: Long,
    
    /** 
     * Subscription URL (http:// or https://, converted from webcal://)
     */
    val subscriptionUrl: String,
    
    /**
     * Display name for the subscription
     */
    val displayName: String,
    
    /**
     * Calendar color
     */
    val color: Int,
    
    /**
     * Description/notes
     */
    val description: String? = null,
    
    /**
     * Whether subscription is enabled for sync
     */
    val syncEnabled: Boolean = true,
    
    /**
     * Whether calendar is visible in calendar apps
     */
    val visible: Boolean = true,
    
    /**
     * Android Calendar ID (null if not synced to Calendar Provider yet)
     */
    val androidCalendarId: Long? = null,
    
    /**
     * Last ETag from server (for conditional requests)
     */
    val etag: String? = null,
    
    /**
     * Refresh interval in minutes (default: 60 = 1 hour)
     */
    val refreshIntervalMinutes: Int = 60,
    
    /**
     * Timestamp when subscription was created
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when subscription was last updated
     */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when last sync/refresh occurred
     */
    val lastSyncedAt: Long? = null,
    
    /**
     * Last sync error message (null if no error)
     */
    val lastSyncError: String? = null
) {
    
    /**
     * Check if subscription has been synced to Android Calendar Provider.
     */
    fun isSyncedWithAndroid(): Boolean = androidCalendarId != null
    
    /**
     * Check if subscription needs refresh based on interval.
     */
    fun needsRefresh(): Boolean {
        if (!syncEnabled) return false
        if (lastSyncedAt == null) return true
        
        val intervalMillis = refreshIntervalMinutes * 60 * 1000L
        val timeSinceLastSync = System.currentTimeMillis() - lastSyncedAt
        return timeSinceLastSync >= intervalMillis
    }
    
    /**
     * Get color as hex string.
     */
    fun getColorHex(): String = String.format("#%08X", color)
    
    /**
     * Convert webcal:// or webcals:// URL to http:// or https://
     */
    fun getHttpUrl(): String = when {
        subscriptionUrl.startsWith("webcal://") -> 
            subscriptionUrl.replace("webcal://", "http://")
        subscriptionUrl.startsWith("webcals://") -> 
            subscriptionUrl.replace("webcals://", "https://")
        else -> subscriptionUrl
    }
    
    /**
     * Check if subscription has sync error.
     */
    fun hasError(): Boolean = lastSyncError != null
    
    /**
     * WebCal subscriptions are always read-only.
     */
    fun isReadOnly(): Boolean = true
}
