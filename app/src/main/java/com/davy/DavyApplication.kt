package com.davy

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davy.ui.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * DAVy Application class.
 * 
 * Entry point for the application. Initializes Hilt dependency injection
 * and sets up Timber logging with Android best practices.
 * 
 * LOGGING ARCHITECTURE:
 * ====================
 * Multi-layered logging approach for security, performance, and debuggability:
 * 
 * 1. Compile-Time Stripping (ProGuard/R8):
 *    - Timber.v() and Timber.d() calls are completely removed from release APK
 *    - Zero overhead, reduces APK size
 * 
 * 2. Runtime Filtering (DebugLogger):
 *    - User-controllable debug logging toggle in Settings
 *    - Production mode: Only WARN and ERROR logs
 *    - Debug mode: All logs with sensitive data filtering
 * 
 * 3. Sensitive Data Protection:
 *    - Automatic redaction of passwords, tokens, credentials
 *    - Pattern-based detection and filtering
 * 
 * See: docs/logging-best-practices.md for complete documentation
 * 
 * PERFORMANCE OPTIMIZATION:
 * - Removed WorkManager database deletion (was causing ~500ms delay on EVERY startup)
 * - Moved initialization to App Startup library for better control
 * - Removed preloading operations that blocked startup
 * - Monitoring services (CalendarMonitorService, ContactsContentObserver) now initialized via MonitoringServicesInitializer
 */
@HiltAndroidApp
class DavyApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        
        // CRITICAL: Initialize debug logger FIRST before any other Timber logging
        // This sets up the Timber tree based on user preferences and applies sensitive data filtering
        // See: util/DebugLogger.kt for implementation details
        com.davy.util.DebugLogger.init(this)

        // Enable StrictMode in debug builds to catch performance issues early
        // Detects: disk I/O on main thread, network on main thread, memory leaks, etc.
        if (com.davy.BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // Application startup logs
        // Note: These debug logs will only appear if debug logging is enabled
        // In release builds, Timber.d() calls are stripped by ProGuard
        Timber.d("=========================================")
        Timber.d("🚀 APPLICATION ONCREATE START")
        Timber.d("=========================================")
        Timber.d("DAVy Application initialized")
        Timber.i("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        
        // Create notification channels for error notifications
        NotificationHelper.createNotificationChannels(this)
        
        // All initialization moved to AppInitializer (App Startup library)
        // See: MonitoringServicesInitializer, BackgroundSyncInitializer, AccountMigrationInitializer
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
