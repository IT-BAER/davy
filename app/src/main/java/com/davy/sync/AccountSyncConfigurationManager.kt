package com.davy.sync

import android.content.Context
import com.davy.data.repository.AccountRepository
import com.davy.domain.model.SyncConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sync configuration for accounts.
 * 
 * Handles:
 * - Enabling/disabling background sync per account
 * - Setting sync intervals based on account preferences
 * - Applying WiFi-only constraints
 * - Respecting global auto-sync setting
 */
@Singleton
class AccountSyncConfigurationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val syncManager: SyncManager
) {
    
    private val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    /**
     * Check if global auto-sync is enabled.
     */
    private fun isGlobalAutoSyncEnabled(): Boolean {
        return appPrefs.getBoolean("auto_sync", true)
    }
    
    /**
     * Check if global WiFi-only sync is enabled.
     */
    private fun isGlobalWifiOnlyEnabled(): Boolean {
        return appPrefs.getBoolean("sync_wifi_only", false)
    }
    
    /**
     * Apply sync configuration for an account.
     * Schedules or cancels periodic sync based on configuration AND global auto-sync setting.
     * Applies global WiFi-only setting if enabled.
     */
    suspend fun applySyncConfiguration(accountId: Long, config: SyncConfiguration) {
        Timber.d("Applying sync configuration for account $accountId")
        val globalAutoSyncEnabled = isGlobalAutoSyncEnabled()
        val globalWifiOnly = isGlobalWifiOnlyEnabled()
        Timber.d("Global auto-sync enabled: $globalAutoSyncEnabled")
        Timber.d("Global WiFi-only: $globalWifiOnly")
        
        if (globalAutoSyncEnabled && config.isAutoSyncEnabled()) {
            // Use global WiFi-only if enabled, otherwise use account preference
            val effectiveWifiOnly = globalWifiOnly || config.wifiOnlySync
            Timber.d("Auto-sync enabled: interval=${config.syncIntervalMinutes}min, wifiOnly=$effectiveWifiOnly (global=$globalWifiOnly, account=${config.wifiOnlySync})")
            syncManager.schedulePeriodicSyncSilent(
                accountId = accountId,
                intervalMinutes = config.syncIntervalMinutes,
                wifiOnly = effectiveWifiOnly
            )
        } else {
            if (!globalAutoSyncEnabled) {
                Timber.d("Global auto-sync disabled, canceling periodic sync")
            } else {
                Timber.d("Auto-sync disabled for this account, canceling periodic sync")
            }
            syncManager.cancelPeriodicSync(accountId)
        }
    }
    
    /**
     * Apply sync configuration for all accounts.
     * Useful on app startup or after global settings change.
     */
    suspend fun applyAllSyncConfigurations() {
        Timber.d("Applying sync configurations for all accounts - using service-specific scheduling")
        
        val accounts = accountRepository.getAll()
        for (account in accounts) {
            // Use service-specific scheduling instead of old single-job approach
            scheduleServiceSpecificSync(account.id)
        }
    }
    
    /**
     * Apply sync configuration for all accounts, preserving existing schedules.
     * Used on app startup to avoid resetting sync timers.
     */
    suspend fun applyAllSyncConfigurationsPreserving() {
        Timber.d("Applying sync configurations for all accounts (preserving existing) - using service-specific scheduling")
        
        val accounts = accountRepository.getAll()
        for (account in accounts) {
            // Use service-specific scheduling instead of old single-job approach
            scheduleServiceSpecificSync(account.id)
        }
    }
    
    /**
     * Apply sync configuration for an account, preserving existing schedule if possible.
     */
    private suspend fun applySyncConfigurationPreserving(accountId: Long, config: SyncConfiguration) {
        Timber.d("Applying sync configuration for account $accountId (preserving)")
        val globalAutoSyncEnabled = isGlobalAutoSyncEnabled()
        val globalWifiOnly = isGlobalWifiOnlyEnabled()
        Timber.d("Global auto-sync enabled: $globalAutoSyncEnabled")
        Timber.d("Global WiFi-only: $globalWifiOnly")
        
        if (globalAutoSyncEnabled && config.isAutoSyncEnabled()) {
            // Use global WiFi-only if enabled, otherwise use account preference
            val effectiveWifiOnly = globalWifiOnly || config.wifiOnlySync
            Timber.d("Auto-sync enabled: interval=${config.syncIntervalMinutes}min, wifiOnly=$effectiveWifiOnly (global=$globalWifiOnly, account=${config.wifiOnlySync})")
            syncManager.schedulePeriodicSyncPreserving(
                accountId = accountId,
                intervalMinutes = config.syncIntervalMinutes,
                wifiOnly = effectiveWifiOnly
            )
        } else {
            if (!globalAutoSyncEnabled) {
                Timber.d("Global auto-sync disabled, canceling periodic sync")
            } else {
                Timber.d("Auto-sync disabled for this account, canceling periodic sync")
            }
            syncManager.cancelPeriodicSync(accountId)
        }
    }
    
    /**
     * Load sync configuration from SharedPreferences.
     */
    fun loadSyncConfigFromPreferences(accountId: Long): SyncConfiguration {
        val prefs = context.getSharedPreferences("sync_config_$accountId", Context.MODE_PRIVATE)
        
        val calendarInterval = prefs.getInt("calendar_sync_interval", 60)
        val contactInterval = prefs.getInt("contact_sync_interval", 60)
        val webCalInterval = prefs.getInt("webcal_sync_interval", 60)
        
        // NOTE: Individual service intervals are stored in SharedPreferences
        // and should be scheduled separately via scheduleServiceSpecificSync()
        // For backward compatibility, we still need a general sync interval
        val syncInterval = minOf(calendarInterval, contactInterval, webCalInterval)
        
        return SyncConfiguration(
            id = 0,
            accountId = accountId,
            syncIntervalMinutes = if (syncInterval > 0) syncInterval else 60,
            wifiOnlySync = prefs.getBoolean("wifi_only", false),
            syncCalendars = true,
            syncContacts = true,
            syncTasks = true,
            syncInBackground = true,
            conflictResolutionStrategy = com.davy.domain.model.ConflictResolutionStrategy.SERVER_WINS,
            maxRetries = 3,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Schedule separate sync jobs for each service type (CalDAV, CardDAV, WebCAL).
     * Each service respects its own sync interval setting.
     */
    suspend fun scheduleServiceSpecificSync(accountId: Long) {
        Timber.d("Scheduling service-specific sync for account $accountId")
        
        val prefs = context.getSharedPreferences("sync_config_$accountId", Context.MODE_PRIVATE)
        val globalAutoSyncEnabled = isGlobalAutoSyncEnabled()
        val globalWifiOnly = isGlobalWifiOnlyEnabled()
        val wifiOnlySync = prefs.getBoolean("wifi_only", false)
        val effectiveWifiOnly = globalWifiOnly || wifiOnlySync
        
        if (!globalAutoSyncEnabled) {
            Timber.d("Global auto-sync disabled, canceling all service-specific syncs")
            syncManager.cancelServiceSync(accountId, SyncManager.SYNC_TYPE_CALENDAR)
            syncManager.cancelServiceSync(accountId, SyncManager.SYNC_TYPE_CONTACTS)
            syncManager.cancelServiceSync(accountId, "webcal")
            return
        }
        
        // Schedule CalDAV sync
        val calendarInterval = prefs.getInt("calendar_sync_interval", 60)
        if (calendarInterval > 0) {
            Timber.d("Scheduling CalDAV sync: interval=${calendarInterval}min, wifiOnly=$effectiveWifiOnly")
            syncManager.scheduleServiceSync(
                accountId = accountId,
                serviceType = SyncManager.SYNC_TYPE_CALENDAR,
                intervalMinutes = calendarInterval,
                wifiOnly = effectiveWifiOnly
            )
        } else {
            Timber.d("CalDAV sync disabled (manual only)")
            syncManager.cancelServiceSync(accountId, SyncManager.SYNC_TYPE_CALENDAR)
        }
        
        // Schedule CardDAV sync
        val contactInterval = prefs.getInt("contact_sync_interval", 60)
        if (contactInterval > 0) {
            Timber.d("Scheduling CardDAV sync: interval=${contactInterval}min, wifiOnly=$effectiveWifiOnly")
            syncManager.scheduleServiceSync(
                accountId = accountId,
                serviceType = SyncManager.SYNC_TYPE_CONTACTS,
                intervalMinutes = contactInterval,
                wifiOnly = effectiveWifiOnly
            )
        } else {
            Timber.d("CardDAV sync disabled (manual only)")
            syncManager.cancelServiceSync(accountId, SyncManager.SYNC_TYPE_CONTACTS)
        }
        
        // Schedule WebCAL sync
        val webCalInterval = prefs.getInt("webcal_sync_interval", 60)
        if (webCalInterval > 0) {
            Timber.d("Scheduling WebCAL sync: interval=${webCalInterval}min, wifiOnly=$effectiveWifiOnly")
            syncManager.scheduleServiceSync(
                accountId = accountId,
                serviceType = "webcal",
                intervalMinutes = webCalInterval,
                wifiOnly = effectiveWifiOnly
            )
        } else {
            Timber.d("WebCAL sync disabled (manual only)")
            syncManager.cancelServiceSync(accountId, "webcal")
        }
    }
    
    /**
     * Disable sync for an account.
     */
    suspend fun disableSync(accountId: Long) {
        Timber.d("Disabling sync for account $accountId")
        syncManager.cancelPeriodicSync(accountId)
    }
    
    /**
     * Create default sync configuration.
     */
    private fun createDefaultSyncConfig(accountId: Long): SyncConfiguration {
        return SyncConfiguration(
            id = 0,
            accountId = accountId,
            syncIntervalMinutes = 60, // 1 hour
            wifiOnlySync = false,
            syncCalendars = true,
            syncContacts = true,
            syncTasks = true,
            syncInBackground = true,
            conflictResolutionStrategy = com.davy.domain.model.ConflictResolutionStrategy.SERVER_WINS,
            maxRetries = 3,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
