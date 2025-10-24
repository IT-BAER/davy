package com.davy.sync

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync lock to prevent ContentObserver from triggering re-sync during active sync operations.
 * 
 * When CalDAVSyncService is actively syncing (downloading/deleting events), we don't want
 * CalendarChangeHandler to trigger reverse sync, which would create a loop.
 */
@Singleton
class SyncLock @Inject constructor() {
    
    @Volatile
    private var isSyncing = false
    
    /**
     * Check if a sync operation is currently in progress.
     */
    fun isSyncing(): Boolean = isSyncing
    
    /**
     * Acquire the sync lock before starting a sync operation.
     */
    fun acquire() {
        Timber.d("🔒 Sync lock ACQUIRED - ContentObserver will be suppressed")
        isSyncing = true
    }
    
    /**
     * Release the sync lock after sync operation completes.
     */
    fun release() {
        Timber.d("🔓 Sync lock RELEASED - ContentObserver active again")
        isSyncing = false
    }
    
    /**
     * Execute a block with the sync lock held.
     */
    suspend fun <T> withLock(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }
}
