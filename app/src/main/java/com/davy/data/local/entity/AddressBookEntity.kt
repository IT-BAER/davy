package com.davy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Address book entity for Room database.
 * 
 * Represents a CardDAV address book from Nextcloud.
 * 
 * @property id Local unique identifier (auto-generated)
 * @property accountId Foreign key to Account entity
 * @property url CardDAV address book URL
 * @property displayName Human-readable name
 * @property description Optional description
 * @property color Display color (ARGB integer)
 * @property ctag Change tag for incremental sync
 * @property syncEnabled Whether sync is enabled
 * @property visible Whether visible in UI
 * @property androidAccountName Android account name for ContactsContract
 * @property createdAt Creation timestamp (epoch millis)
 * @property updatedAt Last update timestamp (epoch millis)
 * @property privWriteContent Server write permission from DAV:current-user-privilege-set
 */
@Entity(
    tableName = "address_books",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["url"], unique = true),
        Index(value = ["android_account_name"])
    ]
)
data class AddressBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @androidx.room.ColumnInfo(name = "account_id")
    val accountId: Long,
    
    @androidx.room.ColumnInfo(name = "url")
    val url: String,
    
    @androidx.room.ColumnInfo(name = "display_name")
    val displayName: String,
    
    @androidx.room.ColumnInfo(name = "description")
    val description: String? = null,
    
    @androidx.room.ColumnInfo(name = "color")
    val color: Int = 0xFF2196F3.toInt(), // Material Blue
    
    @androidx.room.ColumnInfo(name = "ctag")
    val ctag: String? = null,
    
    @androidx.room.ColumnInfo(name = "sync_enabled")
    val syncEnabled: Boolean = true,
    
    @androidx.room.ColumnInfo(name = "visible")
    val visible: Boolean = true,
    
    @androidx.room.ColumnInfo(name = "android_account_name")
    val androidAccountName: String? = null,
    
    @androidx.room.ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Owner principal URL (null if owned by user, non-null if shared addressbook)
    @androidx.room.ColumnInfo(name = "owner")
    val owner: String? = null,
    
    // Server permissions (CardDAV)
    @androidx.room.ColumnInfo(name = "priv_write_content", defaultValue = "1")
    val privWriteContent: Boolean = true,
    
    // User-controlled read-only override (parity with Calendar.forceReadOnly)
    @androidx.room.ColumnInfo(name = "force_read_only", defaultValue = "0")
    val forceReadOnly: Boolean = false,
    
    // Sync restrictions
    @androidx.room.ColumnInfo(name = "wifi_only_sync", defaultValue = "0")
    val wifiOnlySync: Boolean = false,
    
    // Custom sync interval (null = use account default)
    @androidx.room.ColumnInfo(name = "sync_interval_minutes")
    val syncIntervalMinutes: Int? = null
)
