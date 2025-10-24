package com.davy.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davy.sync.AccountSyncConfigurationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing app settings.
 * Uses SharedPreferences for persistence, compatible with reference implementation patterns.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val accountSyncConfigManager: AccountSyncConfigurationManager
) : AndroidViewModel(application) {
    
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    // Debug logging settings
    private val _debugLoggingEnabled = MutableStateFlow(prefs.getBoolean("debug_logging", false))
    val debugLoggingEnabled: StateFlow<Boolean> = _debugLoggingEnabled.asStateFlow()
    
    // Sync settings
    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean("auto_sync", true))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    
    private val _syncOnWifiOnly = MutableStateFlow(prefs.getBoolean("sync_wifi_only", false))
    val syncOnWifiOnly: StateFlow<Boolean> = _syncOnWifiOnly.asStateFlow()
    
    // Battery optimization
    private val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
    private val _isBatteryOptimizationDisabled = MutableStateFlow(
        powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    )
    val isBatteryOptimizationDisabled: StateFlow<Boolean> = _isBatteryOptimizationDisabled.asStateFlow()
    
    // Permissions
    data class PermissionStatus(
        val name: String,
        val displayName: String,
        val isGranted: Boolean
    )
    
    private val _permissions = MutableStateFlow(getPermissionStatuses())
    val permissions: StateFlow<List<PermissionStatus>> = _permissions.asStateFlow()
    
    private fun getPermissionStatuses(): List<PermissionStatus> {
        val permissions = mutableListOf(
            PermissionStatus(
                name = Manifest.permission.READ_CALENDAR,
                displayName = "Read calendar",
                isGranted = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatus(
                name = Manifest.permission.WRITE_CALENDAR,
                displayName = "Write calendar",
                isGranted = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatus(
                name = Manifest.permission.READ_CONTACTS,
                displayName = "Read contacts",
                isGranted = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatus(
                name = Manifest.permission.WRITE_CONTACTS,
                displayName = "Write contacts",
                isGranted = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
            )
        )
        
        // Add POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                PermissionStatus(
                    name = Manifest.permission.POST_NOTIFICATIONS,
                    displayName = "Post notifications",
                    isGranted = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )
            )
        }
        
        return permissions
    }
    
    // Theme settings - compatible with reference implementation PREFERRED_THEME
    private val _themeMode = MutableStateFlow(
        prefs.getInt("preferred_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    )
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()
    
    // Derived state for dark mode toggle
    val isDarkMode: StateFlow<Boolean> = MutableStateFlow(getCurrentDarkModeState()).also { state ->
        viewModelScope.launch {
            _themeMode.collect {
                state.value = getCurrentDarkModeState()
            }
        }
    }
    
    private fun getCurrentDarkModeState(): Boolean {
        return when (_themeMode.value) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                // MODE_NIGHT_FOLLOW_SYSTEM - check system setting
                val nightModeFlags = getApplication<Application>().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
    
    fun updateAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            Timber.d("updateAutoSync: enabled=$enabled")
            _autoSyncEnabled.value = enabled
            prefs.edit().putBoolean("auto_sync", enabled).apply()
            
            // Reapply all account sync configurations to respect the new global setting
            try {
                accountSyncConfigManager.applyAllSyncConfigurations()
                Timber.d("Successfully reapplied all sync configurations")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reapply sync configurations")
            }
        }
    }
    
    fun updateSyncOnWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            Timber.d("updateSyncOnWifiOnly: enabled=$enabled")
            _syncOnWifiOnly.value = enabled
            prefs.edit().putBoolean("sync_wifi_only", enabled).apply()
            
            // Reapply all account sync configurations to respect the new global WiFi-only setting
            try {
                accountSyncConfigManager.applyAllSyncConfigurations()
                Timber.d("Successfully reapplied all sync configurations")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reapply sync configurations")
            }
        }
    }
    
    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            _themeMode.value = mode
            prefs.edit().putInt("preferred_theme", mode).apply()
            // Apply theme immediately
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
    
    fun toggleDarkMode(enabled: Boolean) {
        val newMode = if (enabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        updateThemeMode(newMode)
    }
    
    fun refreshBatteryOptimizationState() {
        viewModelScope.launch {
            _isBatteryOptimizationDisabled.value = 
                powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        }
    }
    
    fun refreshPermissions() {
        viewModelScope.launch {
            _permissions.value = getPermissionStatuses()
        }
    }
    
    fun updateDebugLogging(enabled: Boolean) {
        viewModelScope.launch {
            Timber.d("updateDebugLogging: enabled=$enabled")
            _debugLoggingEnabled.value = enabled
            prefs.edit().putBoolean("debug_logging", enabled).apply()
            
            // Apply logging configuration immediately
            com.davy.util.DebugLogger.setDebugLoggingEnabled(enabled)
        }
    }
}
