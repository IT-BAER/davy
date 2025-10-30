package com.davy.sync

import android.content.ContentResolver
import android.content.Context
import androidx.work.*
import androidx.work.Data
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.Locale
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
    private val addressBookRepository: com.davy.data.repository.AddressBookRepository,
    private val accountRepository: com.davy.data.repository.AccountRepository,
    private val androidAccountManager: com.davy.sync.account.AndroidAccountManager,
    private val syncFrameworkIntegration: SyncFrameworkIntegration
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

    private data class PeriodicRequestResult(
        val request: PeriodicWorkRequest,
        val effectiveIntervalMinutes: Int
    )

    private fun createAccountPeriodicRequest(
        accountId: Long,
        intervalMinutes: Int,
        wifiOnly: Boolean,
        includePushOnlyFlag: Boolean
    ): PeriodicRequestResult {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val minIntervalMinutes = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        val effectiveInterval = maxOf(intervalMinutes, minIntervalMinutes)

        val inputDataBuilder = Data.Builder()
            .putLong(SyncWorker.INPUT_ACCOUNT_ID, accountId)
            .putString(SyncWorker.INPUT_SYNC_TYPE, SYNC_TYPE_ALL)
            .putBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, false)

        if (includePushOnlyFlag) {
            inputDataBuilder.putBoolean("push_only", true)
        }

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            effectiveInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(inputDataBuilder.build())
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .addTag(accountTag(accountId))
            .addTag(serviceTag(SYNC_TYPE_ALL))
            .addTag(periodicCommonTag(accountId, SYNC_TYPE_ALL))
            .build()

        return PeriodicRequestResult(request, effectiveInterval)
    }

    private fun createServicePeriodicRequest(
        accountId: Long,
        serviceType: String,
        intervalMinutes: Int,
        wifiOnly: Boolean,
        includePushOnlyFlag: Boolean
    ): PeriodicRequestResult {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val minIntervalMinutes = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        val effectiveInterval = maxOf(intervalMinutes, minIntervalMinutes)

        val actualSyncType = if (serviceType == "webcal") SYNC_TYPE_CALENDAR else serviceType
        val forceWebCal = serviceType == "webcal"

        val inputDataBuilder = Data.Builder()
            .putLong(SyncWorker.INPUT_ACCOUNT_ID, accountId)
            .putString(SyncWorker.INPUT_SYNC_TYPE, actualSyncType)
            .putBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, forceWebCal)

        if (includePushOnlyFlag) {
            inputDataBuilder.putBoolean("push_only", true)
        }

        val builder = PeriodicWorkRequestBuilder<SyncWorker>(
            effectiveInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(inputDataBuilder.build())
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .addTag(accountTag(accountId))
            .addTag(serviceTag(serviceType))
            .addTag(periodicCommonTag(accountId, serviceType))

        if (serviceType != actualSyncType) {
            builder.addTag(serviceTag(actualSyncType))
            builder.addTag(periodicCommonTag(accountId, actualSyncType))
        }

        return PeriodicRequestResult(builder.build(), effectiveInterval)
    }

    private fun accountTag(accountId: Long) = "account_${accountId}"

    private fun serviceTag(serviceType: String) = "service_${serviceType.lowercase(Locale.ROOT)}"

    private fun periodicCommonTag(accountId: Long, serviceType: String) =
        "periodic-sync/common/${serviceType.lowercase(Locale.ROOT)}/${accountTag(accountId)}"

    private fun periodicWorkerName(accountId: Long, serviceType: String) =
        "periodic-sync ${serviceType.lowercase(Locale.ROOT)} ${accountTag(accountId)}"

    private fun legacyPeriodicWorkName(accountId: Long) = "periodic_sync_account_${accountId}"

    private fun legacyServiceWorkName(accountId: Long, serviceType: String) =
        "periodic_sync_${accountId}_${serviceType}"
    
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
        val result = createAccountPeriodicRequest(
            accountId = accountId,
            intervalMinutes = intervalMinutes,
            wifiOnly = wifiOnly,
            includePushOnlyFlag = true
        )
        Timber.d("schedulePeriodicSync: requested=${intervalMinutes}min, effective=${result.effectiveIntervalMinutes}min (min=${PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)}min)")

        workManager.cancelUniqueWork(legacyPeriodicWorkName(accountId))

        workManager.enqueueUniquePeriodicWork(
            periodicWorkerName(accountId, SYNC_TYPE_ALL),
            ExistingPeriodicWorkPolicy.UPDATE,
            result.request
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
            maxOf(DEFAULT_SYNC_INTERVAL_MINUTES, (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1))),
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
        val result = createAccountPeriodicRequest(
            accountId = accountId,
            intervalMinutes = intervalMinutes,
            wifiOnly = wifiOnly,
            includePushOnlyFlag = true
        )
        val minIntervalMinutes = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        Timber.d("schedulePeriodicSyncSilent: requested=${intervalMinutes}min, effective=${result.effectiveIntervalMinutes}min (min=${minIntervalMinutes}min)")

        workManager.cancelUniqueWork(legacyPeriodicWorkName(accountId))

        Timber.d("schedulePeriodicSyncSilent: Updating work with interval ${intervalMinutes}min, policy UPDATE")
        val operation = workManager.enqueueUniquePeriodicWork(
            periodicWorkerName(accountId, SYNC_TYPE_ALL),
            ExistingPeriodicWorkPolicy.UPDATE,
            result.request
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

        val result = createAccountPeriodicRequest(
            accountId = accountId,
            intervalMinutes = intervalMinutes,
            wifiOnly = wifiOnly,
            includePushOnlyFlag = false
        )
        val minIntervalMinutes = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        Timber.d("schedulePeriodicSyncPreserving: requested=${intervalMinutes}min, effective=${result.effectiveIntervalMinutes}min (min=${minIntervalMinutes}min)")

    workManager.cancelUniqueWork(legacyPeriodicWorkName(accountId))

    Timber.d("→ Enqueueing periodic work with unique name: ${periodicWorkerName(accountId, SYNC_TYPE_ALL)}, policy: KEEP (preserve timer)")
        val operation = workManager.enqueueUniquePeriodicWork(
            periodicWorkerName(accountId, SYNC_TYPE_ALL),
            ExistingPeriodicWorkPolicy.KEEP,
            result.request
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
        workManager.cancelUniqueWork(periodicWorkerName(accountId, SYNC_TYPE_ALL))
        workManager.cancelUniqueWork(legacyPeriodicWorkName(accountId))
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
        val result = createServicePeriodicRequest(
            accountId = accountId,
            serviceType = serviceType,
            intervalMinutes = intervalMinutes,
            wifiOnly = wifiOnly,
            includePushOnlyFlag = true
        )
        val minIntervalMinutes = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        Timber.d("scheduleServiceSync: service=${serviceType}, requested=${intervalMinutes}min, effective=${result.effectiveIntervalMinutes}min (min=${minIntervalMinutes}min)")

        val workName = periodicWorkerName(accountId, serviceType)
        Timber.d("Enqueueing unique periodic work: $workName with policy=$policy")

        workManager.cancelUniqueWork(legacyServiceWorkName(accountId, serviceType))

        workManager.enqueueUniquePeriodicWork(
            workName,
            policy,
            result.request
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
        val workName = periodicWorkerName(accountId, serviceType)
        Timber.d("Canceling service-specific sync: $workName")
        workManager.cancelUniqueWork(workName)
        workManager.cancelUniqueWork(legacyServiceWorkName(accountId, serviceType))
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
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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
     * Sync a specific calendar by its ID in PUSH-ONLY mode (no download from server).
     * This is optimized for local changes where we only need to upload modified events
     * without downloading the entire calendar from the server.
     * 
     * @param accountId Account ID owning the calendar
     * @param calendarId Calendar ID to sync
     */
    suspend fun syncCalendarPushOnly(accountId: Long, calendarId: Long) {
        Timber.d("========================================")
        Timber.d("SyncManager.syncCalendarPushOnly() called")
        Timber.d("Account ID: $accountId")
        Timber.d("Calendar ID: $calendarId")
        Timber.d("Push-only mode: Only uploading dirty events")
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
            "push_only" to true, // NEW: Enable push-only mode
            SyncWorker.INPUT_FORCE_WEB_CAL to false
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
            .addTag("calendar_$calendarId")
            .addTag("push_only")
            .build()
        
        Timber.d("→ Enqueueing push-only calendar sync work with unique name: manual_sync_calendar_push_$calendarId")
        workManager.enqueueUniqueWork(
            "manual_sync_calendar_push_$calendarId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Timber.d("✓ Push-only calendar sync work enqueued successfully")
        Timber.d("========================================")
    }
    
    /**
     * Sync a specific address book by its ID in PUSH-ONLY mode (no download from server).
     * This is optimized for local changes where we only need to upload modified contacts
     * without downloading the entire address book from the server.
     * 
     * @param accountId Account ID owning the address book
     * @param addressBookId Address book ID to sync
     */
    suspend fun syncAddressBookPushOnly(accountId: Long, addressBookId: Long) {
        Timber.d("========================================")
        Timber.d("SyncManager.syncAddressBookPushOnly() called")
        Timber.d("Account ID: $accountId")
        Timber.d("Address Book ID: $addressBookId")
        Timber.d("Push-only mode: Only uploading dirty contacts")
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
            "push_only" to true, // NEW: Enable push-only mode
            SyncWorker.INPUT_FORCE_WEB_CAL to false
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
            .addTag("addressbook_$addressBookId")
            .addTag("push_only")
            .build()
        
        Timber.d("→ Enqueueing push-only address book sync work with unique name: manual_sync_addressbook_push_$addressBookId")
        workManager.enqueueUniqueWork(
            "manual_sync_addressbook_push_$addressBookId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Timber.d("✓ Push-only address book sync work enqueued successfully")
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
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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
    
    /**
     * Request manual contacts sync through Android SyncAdapter framework.
     * This ensures the sync goes through ContactsSyncAdapter.onPerformSync() with MANUAL flag set,
     * bypassing the auto-sync gate logic that blocks non-manual syncs.
     * 
     * Uses SyncFrameworkIntegration wrapper for cleaner, more maintainable code.
     * 
     * @param accountId The DAVy account ID to sync contacts for
     */
    suspend fun requestContactsSyncThroughAdapter(accountId: Long) {
        Timber.d("========================================")
        Timber.d("SyncManager.requestContactsSyncThroughAdapter() called")
        Timber.d("Account ID: $accountId")
        Timber.d("========================================")
        
        try {
            // Get account from repository
            val account = accountRepository.getById(accountId)
            if (account == null) {
                Timber.e("❌ Account not found: $accountId")
                Timber.d("========================================")
                return
            }
            
            Timber.d("✓ Account found: ${account.accountName}")
            
            // Get all address book accounts for this DAVy account
            val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
            
            if (addressBookAccounts.isEmpty()) {
                Timber.w("⚠️ No address book accounts found for: ${account.accountName}")
                Timber.d("This might mean address books haven't been synced yet")
                Timber.d("========================================")
                return
            }
            
            Timber.d("✓ Found ${addressBookAccounts.size} address book account(s)")
            
            // Log each account for debugging
            addressBookAccounts.forEachIndexed { index, addressBookAccount ->
                Timber.d("  [$index] ${addressBookAccount.name}")
                
                // Check sync status
                val syncable = ContentResolver.getIsSyncable(addressBookAccount, "com.android.contacts")
                val autoSync = ContentResolver.getSyncAutomatically(addressBookAccount, "com.android.contacts")
                Timber.d("      - Syncable: $syncable, AutoSync: $autoSync")
            }
            
            Timber.d("Requesting manual sync for each address book account...")
            
            // Request manual sync for each address book account using the wrapper
            for (addressBookAccount in addressBookAccounts) {
                syncFrameworkIntegration.requestManualContactSync(addressBookAccount)
            }
            
            Timber.d("========================================")
            Timber.d("✅ Manual contacts sync requested for ${addressBookAccounts.size} address book(s)")
            Timber.d("Watch for ContactsSyncAdapter.onPerformSync logs to confirm sync execution")
            Timber.d("========================================")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to request contacts sync through adapter")
            Timber.e(e, "Exception: ${e.message}")
            Timber.d("========================================")
        }
    }
}
