package com.davy

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davy.sync.contacts.ContactsContentObserver
import dagger.hilt.android.HiltAndroidApp
import dagger.Lazy
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
 * - Using Lazy injection to defer heavy dependency creation until needed
 * - Moved initialization to App Startup library for better control
 * - Removed preloading operations that blocked startup
 */
@HiltAndroidApp
class DavyApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    // Use Lazy injection to defer expensive dependency creation
    @Inject
    lateinit var contactsContentObserver: Lazy<ContactsContentObserver>
    
    override fun onCreate() {
        super.onCreate()
        // Initialize debug logger based on user preferences (controls whether Timber logs are active)
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

        Timber.d("=========================================")
        Timber.d("🚀 APPLICATION ONCREATE START")
        Timber.d("=========================================")
        Timber.d("DAVy Application initialized")
        
        // Start contacts observer (deferred via lazy injection)
        startContactsObserver()
        
        // All other initialization moved to AppInitializer (App Startup library)
        // See: AccountSyncInitializer, BackgroundSyncInitializer, AccountMigrationInitializer
    }
    
    /**
     * Starts the contacts observer to monitor contact changes.
     * Uses lazy injection to defer creation until needed.
     */
    private fun startContactsObserver() {
        try {
            contactsContentObserver.get().startObserving(contentResolver)
            Timber.d("ContactsContentObserver started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start ContactsContentObserver")
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
