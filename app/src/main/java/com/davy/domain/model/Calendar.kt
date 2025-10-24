package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * Calendar domain model.
 * 
 * Business logic representation of a calendar with color, display name,
 * and sync configuration.
 */
@Immutable
data class Calendar(
    val id: Long = 0,
    val accountId: Long,
    val calendarUrl: String,
    val displayName: String,
    val color: Int,
    val description: String? = null,
    val timezone: String? = null,
    val visible: Boolean = true,
    val syncEnabled: Boolean = true,
    val androidCalendarId: Long? = null,
    val syncToken: String? = null,
    val owner: String? = null,  // Owner principal URL (null = owned by user, non-null = shared calendar)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    
    // Server permissions (from CalDAV current-user-privilege-set)
    // See reference implementation: Collection.privWriteContent, Collection.privUnbind, Collection.forceReadOnly
    val privWriteContent: Boolean = true,  // Default to true for compatibility
    val privUnbind: Boolean = true,        // Default to true for compatibility
    val forceReadOnly: Boolean = false,    // User-controlled read-only override
    
    // Calendar component support (from CalDAV supported-calendar-component-set)
    // See reference implementation: Collection.supportsVTODO, Collection.supportsVJOURNAL, Collection.source
    val supportsVTODO: Boolean = false,    // Tasks/Todos support
    val supportsVJOURNAL: Boolean = false, // Journal entries support
    
    // Webcal subscription source URL
    val source: String? = null,            // External webcal feed URL (for subscribed calendars)
    
    // Sync restrictions
    val wifiOnlySync: Boolean = false,     // Restrict sync to WiFi networks only
    val syncIntervalMinutes: Int? = null   // Custom sync interval (null = use account default)
) {
    
    /**
     * Check if calendar has been synced with Android.
     */
    fun isSyncedWithAndroid(): Boolean {
        return androidCalendarId != null
    }
    
    /**
     * Check if calendar needs synchronization.
     */
    fun needsSync(): Boolean {
        return syncEnabled && (lastSyncedAt == null || syncToken == null)
    }
    
    /**
     * Get color as hex string.
     */
    fun getColorHex(): String {
        return String.format("#%08X", color)
    }
    
    /**
     * Check if calendar is read-only (cannot write events).
     * Following reference implementation pattern: readOnly = forceReadOnly || !privWriteContent
     * A calendar is read-only if:
     * - User explicitly set forceReadOnly flag, OR
     * - Server doesn't grant write permission (privWriteContent = false)
     */
    fun isReadOnly(): Boolean = forceReadOnly || !privWriteContent
    
    /**
     * Check if calendar allows event deletion.
     * Respects forceReadOnly flag - even if server allows deletion, user can force read-only.
     */
    fun canDelete(): Boolean = !forceReadOnly && privUnbind
}
