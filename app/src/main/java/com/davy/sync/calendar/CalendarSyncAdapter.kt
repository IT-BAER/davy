package com.davy.sync.calendar

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import com.davy.data.repository.AccountRepository
import com.davy.data.sync.CalDAVSyncService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SyncAdapter for Android Calendar integration.
 * 
 * This adapter integrates with Android's sync framework to allow
 * DAVy calendars to appear in the system Calendar app.
 */
class CalendarSyncAdapter(
    context: Context,
    autoInitialize: Boolean
) : AbstractThreadedSyncAdapter(context, autoInitialize) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CalendarSyncAdapterEntryPoint {
        fun accountRepository(): AccountRepository
        fun calDAVSyncService(): CalDAVSyncService
        fun calendarContractSync(): CalendarContractSync
        fun calendarRepository(): com.davy.data.repository.CalendarRepository
    }

    private val entryPoint: CalendarSyncAdapterEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CalendarSyncAdapterEntryPoint::class.java
        )
    }

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        Timber.d("========================================")
        Timber.d("CalendarSyncAdapter: onPerformSync for account ${account.name}")
        Timber.d("⚠️  WARNING: Android Auto-Sync triggered!")
        Timber.d("⚠️  This bypasses DAVy's interval settings!")
        Timber.d("========================================")
        
        // Check sync request type
        val isManual = extras.getBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, false)
        val isExpedited = extras.getBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, false)
        val isUploadOnly = extras.getBoolean(android.content.ContentResolver.SYNC_EXTRAS_UPLOAD, false)
        
        Timber.d("Sync flags: manual=$isManual, expedited=$isExpedited, uploadOnly=$isUploadOnly")
        
        // Determine sync trigger source
        val syncTrigger = when {
            isManual && isExpedited -> "MANUAL (user-initiated)"
            isManual -> "MANUAL"
            isExpedited -> "EXPEDITED"
            isUploadOnly -> "CONTENT_TRIGGERED (push-only)"
            else -> "AUTO_SYNC (periodic framework)"
        }
        Timber.d("🔄 Sync trigger: $syncTrigger")
        
        // Policy: Allow content-triggered syncs (UPLOAD_ONLY) for immediate push
        // Block ONLY periodic framework syncs (WorkManager handles intervals)
        if (!isManual && !isExpedited && !isUploadOnly) {
            Timber.w("🚫 BLOCKING periodic framework sync (WorkManager handles intervals)")
            Timber.w("   Content-triggered syncs (UPLOAD_ONLY) are allowed")
            Timber.w("   Manual syncs are allowed")
            return
        }

        Timber.d("✓ Allowing sync: $syncTrigger")

        // Use coroutine scope for async operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find the DAVy account matching this Android account
                val davyAccount = entryPoint.accountRepository()
                    .getByAccountName(account.name)
                
                if (davyAccount == null) {
                    // This can happen transiently right after an Android account rename,
                    // before the app database commit is visible. Treat as soft/retry case.
                    Timber.w("DAVy account not found for Android account: ${account.name} (maybe rename in progress) - will let system retry")
                    syncResult.stats.numIoExceptions++
                    return@launch
                }

                // Enhanced gate for content-triggered syncs: Check if ANY calendar has dirty events
                if (isUploadOnly && !isManual && !isExpedited) {
                    Timber.d("========================================")
                    Timber.d("CONTENT-TRIGGERED SYNC GATE CHECK")
                    Timber.d("Checking if any calendar has dirty events...")
                    Timber.d("========================================")
                    
                    val calendarRepository = entryPoint.calendarRepository()
                    val calendars = calendarRepository.getByAccountId(davyAccount.id)
                    
                    var totalDirtyEvents = 0
                    for (calendar in calendars) {
                        val dirtyCount = entryPoint.calDAVSyncService().getDirtyEventCount(calendar.id)
                        Timber.d("Calendar '${calendar.displayName}' has $dirtyCount dirty events")
                        totalDirtyEvents += dirtyCount
                    }
                    
                    if (totalDirtyEvents == 0) {
                        Timber.w("🚫 BLOCKING content-triggered sync - NO dirty events in ANY calendar")
                        Timber.w("   This sync was triggered by Android after clearing DIRTY flags")
                        Timber.w("   All calendars are already in sync with server")
                        Timber.d("========================================")
                        return@launch
                    }
                    
                    Timber.d("✅ Allowing sync - Found $totalDirtyEvents dirty events across ${calendars.size} calendars")
                    Timber.d("========================================")
                }

                // Perform CalDAV sync to internal database
                Timber.d("Starting CalDAV sync for account: ${davyAccount.accountName}")
                val calDAVResult = entryPoint.calDAVSyncService().syncAccount(davyAccount)
                
                when (calDAVResult) {
                    is com.davy.data.sync.SyncResult.Success -> {
                        Timber.d("CalDAV sync successful: ${calDAVResult.eventsDownloaded} events downloaded, ${calDAVResult.eventsUploaded} events uploaded")
                        
                        // Sync from internal database to Android CalendarContract
                        val contractResult = entryPoint.calendarContractSync()
                            .syncToCalendarProvider(davyAccount.id)
                        
                        when (contractResult) {
                            is CalendarContractResult.Success -> {
                                Timber.d("Calendar Provider sync successful: ${contractResult.calendarsCreated} calendars, ${contractResult.eventsInserted} events")
                                syncResult.stats.numInserts = (contractResult.eventsInserted + calDAVResult.eventsDownloaded).toLong()
                                syncResult.stats.numUpdates = (contractResult.eventsUpdated + calDAVResult.eventsUploaded).toLong()
                                syncResult.stats.numDeletes = contractResult.eventsDeleted.toLong()
                            }
                            is CalendarContractResult.Error -> {
                                Timber.e("Calendar Provider sync failed: ${contractResult.message}")
                                syncResult.stats.numIoExceptions++
                            }
                        }
                    }
                    is com.davy.data.sync.SyncResult.Error -> {
                        Timber.e("CalDAV sync failed: ${calDAVResult.message}")
                        syncResult.stats.numIoExceptions++
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Exception during sync")
                syncResult.stats.numIoExceptions++
            }
        }
    }
}
