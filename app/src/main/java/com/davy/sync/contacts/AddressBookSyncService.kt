package com.davy.sync.contacts

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Sync service for address book accounts (com.davy.addressbook).
 * 
 * Uses the same ContactsSyncAdapter implementation but for the separate
 * address book account type, enabling per-address-book Android accounts.
 */
class AddressBookSyncService : Service() {

    private val syncAdapter by lazy {
        ContactsSyncAdapter(applicationContext, true)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return syncAdapter.syncAdapterBinder
    }
}
