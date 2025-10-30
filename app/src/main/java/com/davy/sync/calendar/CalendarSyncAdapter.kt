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
        fun calendarProviderToDAVySync(): CalendarProviderToDAVySync
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

                // CRITICAL STEP 1: Reverse sync - Import dirty/deleted events from Calendar Provider to DAVy DB
                // This MUST happen before CalDAV sync so dirty events are available for upload
                Timber.d("========================================")
                Timber.d("STEP 1: REVERSE SYNC - Calendar Provider → DAVy DB")
                Timber.d("Importing dirty/deleted events from Calendar Provider into DAVy database...")
                Timber.d("========================================")
                
                try {
                    entryPoint.calendarProviderToDAVySync().syncDirtyEventsOnly()
                    Timber.d("✓ Reverse sync complete - dirty/deleted events now in DAVy DB")
                } catch (e: Exception) {
                    Timber.e(e, "Reverse sync failed - continuing with CalDAV sync anyway")
                }

                // Determine which specific calendars need syncing (only those with dirty events)
                val calendarRepository = entryPoint.calendarRepository()
                val calendars = calendarRepository.getByAccountId(davyAccount.id)
                val calendarsToSync = mutableListOf<com.davy.domain.model.Calendar>()
                
                for (calendar in calendars) {
                    if (!calendar.syncEnabled) {
                        Timber.d("Skipping disabled calendar: ${calendar.displayName}")
                        continue
                    }
                    
                    // Check if calendar has dirty events in DAVy DB (after reverse sync)
                    val dirtyCount = entryPoint.calDAVSyncService().getDirtyEventCount(calendar.id)
                    if (dirtyCount > 0) {
                        Timber.d("✓ Calendar '${calendar.displayName}' has $dirtyCount dirty events - will sync")
                        calendarsToSync.add(calendar)
                    } else {
                        Timber.d("○ Calendar '${calendar.displayName}' has 0 dirty events - skipping")
                    }
                }
                
                if (calendarsToSync.isEmpty()) {
                    Timber.w("No calendars have dirty events to sync - this shouldn't happen for content-triggered sync")
                    Timber.w("Android likely triggered sync due to DIRTY flag being cleared")
                    return@launch
                }
                
                Timber.d("Will sync ${calendarsToSync.size} calendar(s) with dirty events")

                // STEP 2: Perform CalDAV sync ONLY for calendars with dirty events
                Timber.d("========================================")
                Timber.d("STEP 2: CALDAV SYNC - DAVy DB ↔ Server (selective)")
                Timber.d("Syncing only calendars with dirty events...")
                Timber.d("========================================")
                
                var totalDownloaded = 0
                var totalUploaded = 0
                
                for (calendar in calendarsToSync) {
                    try {
                        Timber.d("→ Syncing calendar: ${calendar.displayName}")
                        val result = entryPoint.calDAVSyncService().syncCalendar(davyAccount, calendar, pushOnly = true)
                        
                        when (result) {
                            is com.davy.data.sync.SyncResult.Success -> {
                                Timber.d("  ✓ Synced: ↓${result.eventsDownloaded} ↑${result.eventsUploaded}")
                                totalDownloaded += result.eventsDownloaded
                                totalUploaded += result.eventsUploaded
                            }
                            is com.davy.data.sync.SyncResult.Error -> {
                                Timber.e("  ✗ Sync failed: ${result.message}")
                                syncResult.stats.numIoExceptions++
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Exception syncing calendar: ${calendar.displayName}")
                        syncResult.stats.numIoExceptions++
                    }
                }
                
                Timber.d("✓ CalDAV sync complete for ${calendarsToSync.size} calendars: ↓$totalDownloaded ↑$totalUploaded")
                
                Timber.d("✓ CalDAV sync complete for ${calendarsToSync.size} calendars: ↓$totalDownloaded ↑$totalUploaded")
                
                // STEP 3: Sync from internal database to Android CalendarContract
                Timber.d("========================================")
                Timber.d("STEP 3: FORWARD SYNC - DAVy DB → Calendar Provider")
                Timber.d("Syncing internal database to Calendar Provider...")
                Timber.d("========================================")
                val contractResult = entryPoint.calendarContractSync()
                    .syncToCalendarProvider(davyAccount.id)
                
                when (contractResult) {
                    is CalendarContractResult.Success -> {
                        Timber.d("✓ Calendar Provider sync successful: ${contractResult.calendarsCreated} calendars, ${contractResult.eventsInserted} events inserted, ${contractResult.eventsUpdated} events updated")
                        Timber.d("========================================")
                        Timber.d("SYNC COMPLETE - ALL 3 STEPS SUCCESSFUL (SELECTIVE)")
                        Timber.d("  Step 1: Reverse sync (Provider → DAVy)")
                        Timber.d("  Step 2: CalDAV sync (${calendarsToSync.size} calendars): ↓$totalDownloaded ↑$totalUploaded")
                        Timber.d("  Step 3: Forward sync (DAVy → Provider): +${contractResult.eventsInserted} ~${contractResult.eventsUpdated}")
                        Timber.d("========================================")
                        syncResult.stats.numInserts = (contractResult.eventsInserted + totalDownloaded).toLong()
                        syncResult.stats.numUpdates = (contractResult.eventsUpdated + totalUploaded).toLong()
                        syncResult.stats.numDeletes = contractResult.eventsDeleted.toLong()
                    }
                    is CalendarContractResult.Error -> {
                        Timber.e("Calendar Provider sync failed: ${contractResult.message}")
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
