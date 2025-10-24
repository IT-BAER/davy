package com.davy.sync.contacts

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

/**
 * Service for the Contacts SyncAdapter.
 * 
 * This service is required by Android's sync framework and provides
 * the SyncAdapter to the system.
 */
class ContactsSyncService : Service() {
    
    private var syncAdapter: ContactsSyncAdapter? = null
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("ContactsSyncService created")
        synchronized(this) {
            if (syncAdapter == null) {
                syncAdapter = ContactsSyncAdapter(applicationContext, true)
                Timber.d("ContactsSyncAdapter initialized")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("ContactsSyncService destroyed")
    }
    
    override fun onBind(intent: Intent): IBinder {
        return syncAdapter!!.syncAdapterBinder
    }
}
