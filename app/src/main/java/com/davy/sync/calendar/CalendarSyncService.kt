package com.davy.sync.calendar

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

/**
 * Service for the Calendar SyncAdapter.
 * 
 * This service is required by Android's sync framework and provides
 * the SyncAdapter to the system.
 * 
 * Note: ContentObserver for monitoring calendar changes is now in
 * CalendarMonitorService (foreground service) to ensure continuous monitoring.
 */
class CalendarSyncService : Service() {
    
    private var syncAdapter: CalendarSyncAdapter? = null
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("CalendarSyncService created")
        synchronized(this) {
            if (syncAdapter == null) {
                syncAdapter = CalendarSyncAdapter(applicationContext, true)
                Timber.d("CalendarSyncAdapter initialized")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("CalendarSyncService destroyed")
    }
    
    override fun onBind(intent: Intent): IBinder {
        return syncAdapter!!.syncAdapterBinder
    }
}
