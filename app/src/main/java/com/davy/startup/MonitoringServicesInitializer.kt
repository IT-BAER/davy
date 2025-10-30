package com.davy.startup

import android.content.Context
import androidx.startup.Initializer
import com.davy.sync.calendar.CalendarContentObserver
import com.davy.sync.contacts.ContactsContentObserver
import com.davy.sync.account.AndroidAccountManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * Initializes content observers for automatic push sync.
 * 
 * This initializer:
 * 1. Registers CalendarContentObserver to watch calendar changes
 * 2. Registers ContactsContentObserver to watch contact changes
 * 
 * Both observers run at application level without requiring foreground services.
 * They detect local changes and immediately trigger push sync to the DAV server.
 * Notifications are only shown during active sync operations, not for continuous monitoring.
 */
class MonitoringServicesInitializer : Initializer<Unit> {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MonitoringDependencies {
        fun calendarContentObserver(): dagger.Lazy<CalendarContentObserver>
        fun contactsContentObserver(): dagger.Lazy<ContactsContentObserver>
        fun androidAccountManager(): AndroidAccountManager
    }

    override fun create(context: Context) {
        val appContext = context.applicationContext
        
        try {
            Timber.d("========================================")
            Timber.d("MonitoringServicesInitializer: Starting content observers")
            Timber.d("========================================")
            
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                MonitoringDependencies::class.java
            )
            
            // Proactively ensure Contacts auto-sync is enabled for address-book accounts
            // so the system can trigger upload-only syncs on local edits even when our app isn't running.
            try {
                entryPoint.androidAccountManager().enableContactsAutoSyncForAddressBooks()
            } catch (e: Exception) {
                Timber.w(e, "Unable to enforce Contacts auto-sync for address book accounts at startup")
            }

            // 1. Start CalendarContentObserver (no foreground service needed)
            startCalendarMonitoring(appContext, entryPoint)
            
            // 2. Start ContactsContentObserver (no foreground service needed)
            startContactsMonitoring(appContext, entryPoint)
            
            Timber.d("========================================")
            Timber.d("MonitoringServicesInitializer: Completed successfully")
            Timber.d("No persistent notifications - observers run at app level")
            Timber.d("========================================")
        } catch (e: Exception) {
            Timber.e(e, "MonitoringServicesInitializer: Failed to start content observers")
        }
    }

    /**
     * Start the calendar content observer.
     * This observer watches for changes in the Calendar Provider and triggers push sync.
     * No foreground service needed - runs at application level.
     */
    private fun startCalendarMonitoring(context: Context, entryPoint: MonitoringDependencies) {
        try {
            val calendarObserver = entryPoint.calendarContentObserver().get()
            calendarObserver.startObserving(context.contentResolver)
            
            Timber.d("✓ CalendarContentObserver started (no foreground service)")
        } catch (e: Exception) {
            Timber.e(e, "✗ Failed to start CalendarContentObserver")
        }
    }

    /**
     * Start the contacts content observer.
     * This observer watches for changes in the Contacts Provider and triggers push sync.
     * No foreground service needed - runs at application level.
     */
    private fun startContactsMonitoring(context: Context, entryPoint: MonitoringDependencies) {
        try {
            val contactsObserver = entryPoint.contactsContentObserver().get()
            contactsObserver.startObserving(context.contentResolver)
            
            Timber.d("✓ ContactsContentObserver started (no foreground service)")
        } catch (e: Exception) {
            Timber.e(e, "✗ Failed to start ContactsContentObserver")
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = 
        listOf(WorkManagerSetupInitializer::class.java)
}
