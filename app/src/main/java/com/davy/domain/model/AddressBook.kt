package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * Address book domain model.
 * 
 * Represents a CardDAV address book.
 */
@Immutable
data class AddressBook(
    val id: Long = 0,
    val accountId: Long,
    val url: String,
    val displayName: String,
    val description: String? = null,
    val color: Int = 0xFF2196F3.toInt(),
    val ctag: String? = null,
    val syncEnabled: Boolean = true,
    val visible: Boolean = true,
    val androidAccountName: String? = null,
    val owner: String? = null,  // Owner principal URL (null = owned by user, non-null = shared addressbook)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSynced: Long? = null,  // Last successful sync timestamp (null = never synced)
    
    // Server permissions (from CardDAV current-user-privilege-set)
    // See reference implementation: Collection.privWriteContent
    val privWriteContent: Boolean = true,  // Default to true for compatibility
    // User-controlled read-only override (parity with Calendar.forceReadOnly)
    val forceReadOnly: Boolean = false,
    
    // Sync restrictions
    val wifiOnlySync: Boolean = false,     // Restrict sync to WiFi networks only
    val syncIntervalMinutes: Int? = null   // Custom sync interval (null = use account default)
) {
    /**
     * Checks if address book is synced (has CTag).
     */
    fun isSynced(): Boolean = ctag != null
    
    /**
     * Checks if address book needs sync.
     */
    fun needsSync(): Boolean = syncEnabled && !isSynced()
    
    /**
     * Checks if address book is read-only.
     * 
     * Following reference implementation pattern: readOnly = forceReadOnly || !privWriteContent
     */
    fun isReadOnly(): Boolean = forceReadOnly || !privWriteContent

    /**
     * Whether deletion is allowed. For address books, treat read-only as non-deletable.
     */
    fun canDelete(): Boolean = !isReadOnly()
}

