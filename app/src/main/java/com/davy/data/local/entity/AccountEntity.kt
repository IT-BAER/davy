package com.davy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a Nextcloud server account.
 * 
 * Stores account configuration and metadata. Credentials are stored separately
 * in EncryptedSharedPreferences for security.
 * 
 * @property id Unique account identifier (auto-generated)
 * @property accountName Unique account name (e.g., "user@nextcloud.example.com")
 * @property serverUrl Base URL of the Nextcloud server (e.g., "https://nextcloud.example.com")
 * @property username Username for authentication
 * @property displayName Human-readable display name
 * @property email Account email address
 * @property calendarEnabled Whether calendar sync is enabled for this account
 * @property contactsEnabled Whether contacts sync is enabled for this account
 * @property tasksEnabled Whether tasks sync is enabled for this account
 * @property createdAt Timestamp when account was created (milliseconds since epoch)
 * @property lastAuthenticatedAt Timestamp of last successful authentication
 * @property authType Authentication method (BASIC, BEARER, APP_PASSWORD)
 * @property certificateFingerprint SHA-256 fingerprint of server certificate (for self-signed)
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "account_name")
    val accountName: String,
    
    @ColumnInfo(name = "server_url")
    val serverUrl: String,
    
    @ColumnInfo(name = "username")
    val username: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,
    
    @ColumnInfo(name = "email")
    val email: String? = null,
    
    @ColumnInfo(name = "calendar_enabled")
    val calendarEnabled: Boolean = true,
    
    @ColumnInfo(name = "contacts_enabled")
    val contactsEnabled: Boolean = true,
    
    @ColumnInfo(name = "tasks_enabled")
    val tasksEnabled: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_authenticated_at")
    val lastAuthenticatedAt: Long? = null,
    
    @ColumnInfo(name = "auth_type")
    val authType: String = AuthType.BASIC.name,
    
    @ColumnInfo(name = "certificate_fingerprint")
    val certificateFingerprint: String? = null,
    
    @ColumnInfo(name = "notes")
    val notes: String? = null  // User notes/comments for this account
)

/**
 * Authentication type enumeration
 */
enum class AuthType {
    /** Basic HTTP authentication with username/password */
    BASIC,
    
    /** OAuth2 bearer token authentication */
    BEARER,
    
    /** Nextcloud app-specific password */
    APP_PASSWORD
}
