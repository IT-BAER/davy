package com.davy.sync

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling and executing sync operations.
 * 
 * Handles:
 * - Periodic background sync scheduling (per account settings)
 * - Manual sync triggering
 * - Sync constraints (network)
 * - Retry policies
 */
@Singleton
class SyncManager @Inject constructor(
    private val context: Context,
    private val networkUtils: com.davy.util.NetworkUtils,
    private val calendarRepository: com.davy.data.repository.CalendarRepository,
    private val addressBookRepository: com.davy.data.repository.AddressBookRepository
) {
    
    private val workManager = WorkManager.getInstance(context)
    
    companion object {
        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 60L
        private const val TAG_PERIODIC_SYNC = "periodic_sync"
        private const val TAG_MANUAL_SYNC = "manual_sync"
        const val SYNC_TYPE_ALL = "all"
        const val SYNC_TYPE_CALENDAR = "calendar"
        const val SYNC_TYPE_CONTACTS = "contacts"
        const val SYNC_TYPE_TASKS = "tasks"
    }
    
    /**
     * Schedule periodic background sync for a specific account.
     * Uses WorkManager with appropriate constraints.
     * 
     * @param accountId Account to sync
     * @param intervalMinutes Sync interval in minutes
     * @param wifiOnly Whether to sync only on WiFi
     */
    fun schedulePeriodicSync(
        accountId: Long,
        intervalMinutes: Int = DEFAULT_SYNC_INTERVAL_MINUTES.toInt(),
        wifiOnly: Boolean = false
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_ALL,
            // Do NOT force WebCal here; calendar and webcal must be scheduled separately
            SyncWorker.INPUT_FORCE_WEB_CAL to false
        )
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .addTag("account_$accountId")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "periodic_sync_account_$accountId",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncRequest
        )
    }
    
    /**
     * Schedule periodic background sync for all accounts.
     * Uses default settings.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            DEFAULT_SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }
    
    /**
     * Schedule periodic sync for a specific account without triggering immediate execution.
     * Uses KEEP policy - only schedules if not already scheduled.
     * To update an existing schedule, cancel first then call this.
     */
    fun schedulePeriodicSyncSilent(
        accountId: Long,
        intervalMinutes: Int = DEFAULT_SYNC_INTERVAL_MINUTES.toInt(),
        wifiOnly: Boolean = false
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_ALL,
            // Do NOT force WebCal here; calendar and webcal must be scheduled separately
            SyncWorker.INPUT_FORCE_WEB_CAL to false
        )
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .addTag("account_$accountId")
            .build()
        
        // Use UPDATE policy: updates existing work with new interval/constraints
        // This avoids cancelling and re-enqueueing which would trigger immediate execution
        Timber.d("schedulePeriodicSyncSilent: Updating work with interval ${intervalMinutes}min, policy UPDATE")
        val operation = workManager.enqueueUniquePeriodicWork(
            "periodic_sync_account_$accountId",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncRequest
        )
        
        operation.result.addListener({
            try {
                operation.result.get()
                Timber.d("✓ Periodic sync work updated successfully for account $accountId (interval: ${intervalMinutes}min)")
            } catch (e: Exception) {
                Timber.e(e, "✗ Failed to update periodic sync work for account $accountId")
            }
        }, { it.run() }) // Execute on calling thread
    }
    
    /**
     * Schedule periodic sync preserving existing schedule if already running.
     * Uses KEEP policy - does not reschedule if work is already enqueued.
     * This prevents resetting the sync timer on app restart.
     */
    fun schedulePeriodicSyncPreserving(
        accountId: Long,
        intervalMinutes: Int = DEFAULT_SYNC_INTERVAL_MINUTES.toInt(),
        wifiOnly: Boolean = false
    ) {
        Timber.d("schedulePeriodicSyncPreserving: accountId=$accountId, intervalMinutes=$intervalMinutes, wifiOnly=$wifiOnly")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_ALL,
            // Do NOT force WebCal here; calendar and webcal must be scheduled separately
            SyncWorker.INPUT_FORCE_WEB_CAL to false
        )
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .addTag("account_$accountId")
            .build()
        
        Timber.d("→ Enqueueing periodic work with unique name: periodic_sync_account_$accountId, policy: KEEP (preserve timer)")
        val operation = workManager.enqueueUniquePeriodicWork(
            "periodic_sync_account_$accountId",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
        
        operation.result.addListener({
            try {
                operation.result.get()
                Timber.d("✓ Periodic sync work enqueued successfully for account $accountId")
            } catch (e: Exception) {
                Timber.e(e, "✗ Failed to enqueue periodic sync work for account $accountId")
            }
        }, { it.run() }) // Execute on calling thread
    }
    
    /**
     * Cancel periodic sync for a specific account.
     */
    fun cancelPeriodicSync(accountId: Long) {
        workManager.cancelUniqueWork("periodic_sync_account_$accountId")
        // Also cancel all service-specific syncs for this account
        cancelServiceSync(accountId, SYNC_TYPE_CALENDAR)
        cancelServiceSync(accountId, SYNC_TYPE_CONTACTS)
        cancelServiceSync(accountId, "webcal")
    }
    
    /**
     * Cancel periodic sync for all accounts.
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        // Also cancel all account-specific syncs
        workManager.cancelAllWorkByTag(TAG_PERIODIC_SYNC)
    }
    
    /**
     * Schedule periodic sync for a specific service type (CalDAV, CardDAV, or WebCAL).
     * Each service gets its own WorkManager job with its own interval.
     * 
     * @param accountId Account to sync
     * @param serviceType Service type (calendar, contacts, or webcal)
     * @param intervalMinutes Sync interval in minutes for this specific service
     * @param wifiOnly Whether to sync only on WiFi
     */
    fun scheduleServiceSync(
        accountId: Long,
        serviceType: String,
        intervalMinutes: Int,
        wifiOnly: Boolean = false,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
    ) {
        Timber.d("Scheduling service-specific sync: account=$accountId, service=$serviceType, interval=${intervalMinutes}min")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        
    // Map webcal to calendar sync type but only force WebCal when the serviceType is actually webcal
    val actualSyncType = if (serviceType == "webcal") SYNC_TYPE_CALENDAR else serviceType
    val forceWebCal = serviceType == "webcal"
        
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to actualSyncType,
            SyncWorker.INPUT_FORCE_WEB_CAL to forceWebCal
        )
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .addTag("account_$accountId")
            .addTag("service_$serviceType")
            .build()
        
        val workName = "periodic_sync_${accountId}_$serviceType"
        Timber.d("Enqueueing unique periodic work: $workName with policy=$policy")
        
        workManager.enqueueUniquePeriodicWork(
            workName,
            policy,
            periodicSyncRequest
        )
    }

    /**
     * Schedule service-specific periodic sync but preserve the existing timer if already scheduled.
     * This avoids resetting the next-run window on app startup.
     */
    fun scheduleServiceSyncPreserving(
        accountId: Long,
        serviceType: String,
        intervalMinutes: Int,
        wifiOnly: Boolean = false
    ) {
        scheduleServiceSync(
            accountId = accountId,
            serviceType = serviceType,
            intervalMinutes = intervalMinutes,
            wifiOnly = wifiOnly,
            policy = ExistingPeriodicWorkPolicy.KEEP
        )
    }
    
    /**
     * Cancel periodic sync for a specific service type.
     * 
     * @param accountId Account ID
     * @param serviceType Service type (calendar, contacts, or webcal)
     */
    fun cancelServiceSync(accountId: Long, serviceType: String) {
        val workName = "periodic_sync_${accountId}_$serviceType"
        Timber.d("Canceling service-specific sync: $workName")
        workManager.cancelUniqueWork(workName)
    }
    
    /**
     * Trigger immediate sync for specific account.
     * 
     * @param accountId Account to sync
     * @param syncType Type of sync (all, calendar, contacts, tasks)
     */
    fun syncNow(accountId: Long, syncType: String = SyncWorker.SYNC_TYPE_ALL) {
        Timber.d("========================================")
        Timber.d("SyncManager.syncNow() called")
        Timber.d("Account ID: $accountId")
        Timber.d("Sync Type: $syncType")
        Timber.d("========================================")
    // Only include WebCal when doing ALL-sync. Calendar-only must not trigger WebCal.
    val forceWebCal = syncType == SYNC_TYPE_ALL
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to syncType,
            SyncWorker.INPUT_FORCE_WEB_CAL to forceWebCal
        )
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_MANUAL_SYNC)
            .addTag("account_$accountId")
            .build()
        
        Timber.d("→ Enqueueing sync work with unique name: manual_sync_$accountId")
        workManager.enqueueUniqueWork(
            "manual_sync_$accountId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Timber.d("✓ Sync work enqueued successfully")
        Timber.d("========================================")
    }
    
    /**
     * Trigger immediate sync for all accounts.
     */
    fun syncAllNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_ALL,
                    // Manual all-sync can include WebCal
                    SyncWorker.INPUT_FORCE_WEB_CAL to true
                )
            )
            .addTag(TAG_MANUAL_SYNC)
            .build()
        
        workManager.enqueueUniqueWork(
            "manual_sync_all",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
    
    /**
     * Get sync status as LiveData.
     */
    fun getSyncStatus() = workManager.getWorkInfosByTagLiveData(TAG_MANUAL_SYNC)
    
    /**
     * Observe sync state for a specific account as a Flow.
     * Returns true when syncing, false otherwise.
     * 
     * Note: Uses SampledFlow to ensure immediate state updates and avoid delays from WorkManager's
     * internal state observation. This ensures UI animations stop as soon as sync completes.
     */
    fun observeSyncState(accountId: Long): Flow<Boolean> {
        return workManager.getWorkInfosByTagFlow("account_$accountId")
            .map { workInfos ->
                val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
                Timber.v("Sync state for account $accountId: running=$isRunning (work items: ${workInfos.size})")
                isRunning
            }
    }
    
    /**
     * Cancel ongoing sync for specific account.
     */
    fun cancelSync(accountId: Long) {
        workManager.cancelAllWorkByTag("account_$accountId")
    }
    
    /**
     * Cancel all ongoing syncs.
     */
    fun cancelAllSync() {
        workManager.cancelAllWorkByTag(TAG_MANUAL_SYNC)
    }
    
    /**
     * Sync multiple collections in batch.
     * Syncs selected calendars, address books, and webcal subscriptions in parallel.
     * 
     * @param accountId Account ID
     * @param calendarIds List of calendar IDs to sync
     * @param addressBookIds List of address book IDs to sync
     * @param webCalIds List of webcal subscription IDs to sync
     */
    suspend fun syncBatch(
        accountId: Long,
        calendarIds: List<Long> = emptyList(),
        addressBookIds: List<Long> = emptyList(),
        webCalIds: List<Long> = emptyList()
    ) {
        Timber.d("========================================")
        Timber.d("SyncManager.syncBatch() called")
        Timber.d("Account ID: $accountId")
        Timber.d("Calendars: ${calendarIds.size}, AddressBooks: ${addressBookIds.size}, WebCals: ${webCalIds.size}")
        Timber.d("========================================")
        
        // Create batch sync requests for each collection type
        val syncRequests = mutableListOf<OneTimeWorkRequest>()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
    // Create work requests for each calendar
        calendarIds.forEach { calendarId ->
            // Check WiFi-only restriction
            val calendar = calendarRepository.getById(calendarId)
            if (calendar?.wifiOnlySync == true && !networkUtils.isOnWiFi()) {
                Timber.w("Calendar $calendarId requires WiFi but device is not on WiFi. Skipping.")
                return@forEach
            }
            
            val inputData = workDataOf(
                SyncWorker.INPUT_ACCOUNT_ID to accountId,
                SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_CALENDAR,
                "calendar_id" to calendarId,
                // Batch calendar syncs must NOT trigger WebCal updates
                SyncWorker.INPUT_FORCE_WEB_CAL to false
            )
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(TAG_MANUAL_SYNC)
                .addTag("account_$accountId")
                .addTag("batch_sync")
                .build()
            
            syncRequests.add(syncRequest)
        }
        
        // Create work requests for each address book
        addressBookIds.forEach { addressBookId ->
            // Check WiFi-only restriction
            val addressBook = addressBookRepository.getById(addressBookId)
            if (addressBook?.wifiOnlySync == true && !networkUtils.isOnWiFi()) {
                Timber.w("AddressBook $addressBookId requires WiFi but device is not on WiFi. Skipping.")
                return@forEach
            }
            
            val inputData = workDataOf(
                SyncWorker.INPUT_ACCOUNT_ID to accountId,
                SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_CONTACTS,
                "addressbook_id" to addressBookId
            )
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(TAG_MANUAL_SYNC)
                .addTag("account_$accountId")
                .addTag("batch_sync")
                .build()
            
            syncRequests.add(syncRequest)
        }
        
        // Create work requests for each webcal subscription
        // WebCals are synced as part of calendar sync, so we'll trigger them individually
        webCalIds.forEach { webCalId ->
            val inputData = workDataOf(
                SyncWorker.INPUT_ACCOUNT_ID to accountId,
                SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_CALENDAR,
                "webcal_id" to webCalId,
                SyncWorker.INPUT_FORCE_WEB_CAL to true
            )
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(TAG_MANUAL_SYNC)
                .addTag("account_$accountId")
                .addTag("batch_sync")
                .build()
            
            syncRequests.add(syncRequest)
        }
        
        // Enqueue all requests in parallel using WorkContinuation
        if (syncRequests.isNotEmpty()) {
            Timber.d("Enqueueing ${syncRequests.size} batch sync work requests")
            workManager.enqueue(syncRequests)
        } else {
            Timber.w("No collections to sync in batch")
        }
    }
    
    /**
     * Sync a specific calendar by its ID.
     * 
     * @param accountId Account ID owning the calendar
     * @param calendarId Calendar ID to sync
     */
    suspend fun syncCalendar(accountId: Long, calendarId: Long) {
        Timber.d("========================================")
        Timber.d("SyncManager.syncCalendar() called")
        Timber.d("Account ID: $accountId")
        Timber.d("Calendar ID: $calendarId")
        Timber.d("========================================")
        
        // Check WiFi-only restriction
        val calendar = calendarRepository.getById(calendarId)
        if (calendar?.wifiOnlySync == true && !networkUtils.isOnWiFi()) {
            Timber.w("Calendar $calendarId requires WiFi but device is not on WiFi. Skipping sync.")
            return
        }
        
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_CALENDAR,
            "calendar_id" to calendarId,
            // Calendar-specific sync must NOT trigger WebCal updates
            SyncWorker.INPUT_FORCE_WEB_CAL to false
        )
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(TAG_MANUAL_SYNC)
            .addTag("account_$accountId")
            .addTag("calendar_$calendarId")
            .build()
        
        Timber.d("→ Enqueueing calendar sync work with unique name: manual_sync_calendar_$calendarId")
        workManager.enqueueUniqueWork(
            "manual_sync_calendar_$calendarId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Timber.d("✓ Calendar sync work enqueued successfully")
        Timber.d("========================================")
    }
    
    /**
     * Sync a specific address book by its ID.
     * 
     * @param accountId Account ID owning the address book
     * @param addressBookId Address book ID to sync
     */
    suspend fun syncAddressBook(accountId: Long, addressBookId: Long) {
        Timber.d("========================================")
        Timber.d("SyncManager.syncAddressBook() called")
        Timber.d("Account ID: $accountId")
        Timber.d("Address Book ID: $addressBookId")
        Timber.d("========================================")
        
        // Check WiFi-only restriction
        val addressBook = addressBookRepository.getById(addressBookId)
        if (addressBook?.wifiOnlySync == true && !networkUtils.isOnWiFi()) {
            Timber.w("Address book $addressBookId requires WiFi but device is not on WiFi. Skipping sync.")
            return
        }
        
        val inputData = workDataOf(
            SyncWorker.INPUT_ACCOUNT_ID to accountId,
            SyncWorker.INPUT_SYNC_TYPE to SYNC_TYPE_CONTACTS,
            "addressbook_id" to addressBookId,
            SyncWorker.INPUT_FORCE_WEB_CAL to false
        )
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(TAG_MANUAL_SYNC)
            .addTag("account_$accountId")
            .addTag("addressbook_$addressBookId")
            .build()
        
        Timber.d("→ Enqueueing addressbook sync work with unique name: manual_sync_addressbook_$addressBookId")
        workManager.enqueueUniqueWork(
            "manual_sync_addressbook_$addressBookId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Timber.d("✓ Address book sync work enqueued successfully")
        Timber.d("========================================")
    }
}
