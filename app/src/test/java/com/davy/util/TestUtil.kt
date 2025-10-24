package com.davy.util

import com.davy.data.local.entity.AccountEntity
import com.davy.data.local.entity.AuthType
import com.davy.data.local.entity.ConflictResolutionStrategy
import com.davy.data.local.entity.ResourceType
import com.davy.data.local.entity.SyncConfigurationEntity
import com.davy.data.local.entity.SyncResult
import com.davy.data.local.entity.SyncStatusEntity

/**
 * Test utilities and fixtures for unit testing.
 * 
 * Provides factory methods for creating test data with sensible defaults.
 */
object TestUtil {
    
    /**
     * Create a test account entity with optional customization.
     */
    fun createTestAccount(
        id: Long = 1,
        accountName: String = "test@nextcloud.example.com",
        serverUrl: String = "https://nextcloud.example.com",
        username: String = "testuser",
        displayName: String? = "Test User",
        email: String? = "test@example.com",
        calendarEnabled: Boolean = true,
        contactsEnabled: Boolean = true,
        tasksEnabled: Boolean = true,
        createdAt: Long = System.currentTimeMillis(),
        lastAuthenticatedAt: Long? = System.currentTimeMillis(),
        authType: AuthType = AuthType.BASIC,
        certificateFingerprint: String? = null
    ): AccountEntity {
        return AccountEntity(
            id = id,
            accountName = accountName,
            serverUrl = serverUrl,
            username = username,
            displayName = displayName,
            email = email,
            calendarEnabled = calendarEnabled,
            contactsEnabled = contactsEnabled,
            tasksEnabled = tasksEnabled,
            createdAt = createdAt,
            lastAuthenticatedAt = lastAuthenticatedAt,
            authType = authType.name,
            certificateFingerprint = certificateFingerprint
        )
    }
    
    /**
     * Create a test sync configuration entity with optional customization.
     */
    fun createTestSyncConfiguration(
        id: Long = 1,
        accountId: Long = 1,
        syncIntervalMinutes: Int = 60,
        wifiOnlySync: Boolean = false,
        syncCalendars: Boolean = true,
        syncContacts: Boolean = true,
        syncTasks: Boolean = true,
        syncInBackground: Boolean = true,
        conflictResolutionStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.SERVER_WINS,
        maxRetries: Int = 3,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): SyncConfigurationEntity {
        return SyncConfigurationEntity(
            id = id,
            accountId = accountId,
            syncIntervalMinutes = syncIntervalMinutes,
            wifiOnlySync = wifiOnlySync,
            syncCalendars = syncCalendars,
            syncContacts = syncContacts,
            syncTasks = syncTasks,
            syncInBackground = syncInBackground,
            conflictResolutionStrategy = conflictResolutionStrategy.name,
            maxRetries = maxRetries,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * Create a test sync status entity with optional customization.
     */
    fun createTestSyncStatus(
        id: Long = 1,
        accountId: Long = 1,
        resourceType: ResourceType = ResourceType.CALENDAR,
        lastSyncTimestamp: Long? = System.currentTimeMillis(),
        lastSyncResult: SyncResult = SyncResult.SUCCESS,
        lastSyncToken: String? = "test-sync-token",
        currentlySyncing: Boolean = false,
        syncStartedAt: Long? = null,
        itemsSynced: Int = 0,
        errorMessage: String? = null,
        retryCount: Int = 0,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): SyncStatusEntity {
        return SyncStatusEntity(
            id = id,
            accountId = accountId,
            resourceType = resourceType.name,
            lastSyncTimestamp = lastSyncTimestamp,
            lastSyncResult = lastSyncResult.name,
            lastSyncToken = lastSyncToken,
            currentlySyncing = currentlySyncing,
            syncStartedAt = syncStartedAt,
            itemsSynced = itemsSynced,
            errorMessage = errorMessage,
            retryCount = retryCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * Create multiple test accounts.
     */
    fun createTestAccounts(count: Int): List<AccountEntity> {
        return (1..count).map { i ->
            createTestAccount(
                id = i.toLong(),
                accountName = "test$i@nextcloud.example.com",
                username = "testuser$i"
            )
        }
    }
}
