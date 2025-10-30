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
 * and sets up Timber logging.
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
        // Initialize debug logger based on user preferences (controls whether Timber logs are active)
        // This MUST be called first before any Timber logging
        com.davy.util.DebugLogger.init(this)

        // Enable StrictMode in debug builds to catch accidental disk/network on main thread and leaks
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

        // Note: These debug logs will only show if debug logging is enabled
        Timber.d("=========================================")
        Timber.d("🚀 APPLICATION ONCREATE START")
        Timber.d("=========================================")
        Timber.d("DAVy Application initialized")
        
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
