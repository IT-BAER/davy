package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing a Nextcloud server account.
 * 
 * This is the business logic representation, separate from the database entity.
 * 
 * @property id Unique account identifier
 * @property accountName Unique account name (e.g., "user@nextcloud.example.com")
 * @property serverUrl Base URL of the Nextcloud server
 * @property username Username for authentication
 * @property displayName Human-readable display name
 * @property email Account email address
 * @property calendarEnabled Whether calendar sync is enabled
 * @property contactsEnabled Whether contacts sync is enabled
 * @property tasksEnabled Whether tasks sync is enabled
 * @property createdAt Timestamp when account was created (milliseconds since epoch)
 * @property lastAuthenticatedAt Timestamp of last successful authentication
 * @property authType Authentication method
 * @property certificateFingerprint SHA-256 fingerprint of server certificate
 */
@Immutable
data class Account(
    val id: Long,
    val accountName: String,
    val serverUrl: String,
    val username: String,
    val displayName: String?,
    val email: String?,
    val calendarEnabled: Boolean,
    val contactsEnabled: Boolean,
    val tasksEnabled: Boolean,
    val createdAt: Long,
    val lastAuthenticatedAt: Long?,
    val authType: AuthType,
    val certificateFingerprint: String?,
    val notes: String? = null  // User notes/comments for this account
) {
    /**
     * Get display name or fallback to username
     */
    fun getDisplayNameOrUsername(): String = displayName ?: username
    
    /**
     * Check if any sync is enabled
     */
    fun hasSyncEnabled(): Boolean = calendarEnabled || contactsEnabled || tasksEnabled
}

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
