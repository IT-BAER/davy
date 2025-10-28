package com.davy.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.ContactRepository
import com.davy.data.repository.TaskListRepository
import com.davy.data.sync.CalDAVSyncService
import com.davy.data.sync.CardDAVSyncService
import com.davy.sync.webcal.WebCalSyncService
import com.davy.sync.calendar.CalendarContractSync
import com.davy.ui.util.AppError
import com.davy.ui.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Collections

/**
 * WorkManager worker for synchronizing CalDAV/CardDAV data.
 * 
 * This worker handles background synchronization of:
 * - Calendars and events (CalDAV)
 * - Contacts (CardDAV)
 * - Tasks (CalDAV with VTODO support)
 * 
 * Scheduling: Runs periodically based on sync configuration
 * or can be triggered manually.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val contactRepository: ContactRepository,
    private val addressBookRepository: com.davy.data.repository.AddressBookRepository,
    private val taskListRepository: TaskListRepository,
    private val calDAVSyncService: CalDAVSyncService,
    private val cardDAVSyncService: CardDAVSyncService,
    private val webCalSyncService: WebCalSyncService,
    private val calendarContractSync: CalendarContractSync,
    private val principalDiscovery: com.davy.data.remote.caldav.PrincipalDiscovery,
    private val credentialStore: com.davy.data.local.CredentialStore,
    private val authenticationManager: com.davy.data.remote.AuthenticationManager
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        const val WORK_NAME = "davy_sync_work"
        const val INPUT_ACCOUNT_ID = "account_id"
        const val INPUT_SYNC_TYPE = "sync_type"
        const val INPUT_FORCE_WEB_CAL = "force_webcal"
        
        const val SYNC_TYPE_ALL = "all"
        const val SYNC_TYPE_CALENDAR = "calendar"
        const val SYNC_TYPE_CONTACTS = "contacts"
        const val SYNC_TYPE_TASKS = "tasks"

        private val runningSyncs = Collections.synchronizedSet(mutableSetOf<String>())
    }
    
    override suspend fun doWork(): Result {
        val accountId = inputData.getLong(INPUT_ACCOUNT_ID, -1L)
        val syncType = inputData.getString(INPUT_SYNC_TYPE) ?: SYNC_TYPE_ALL
        val calendarId = inputData.getLong("calendar_id", -1L)
        val addressBookId = inputData.getLong("addressbook_id", -1L)
        val forceWebCal = inputData.getBoolean(INPUT_FORCE_WEB_CAL, false)

        val syncSignature = buildSyncSignature(accountId, syncType, calendarId, addressBookId, forceWebCal)
        if (!runningSyncs.add(syncSignature)) {
            Timber.i("Sync already running for signature=$syncSignature – skipping duplicate invocation")
            return Result.success()
        }

        Timber.d("========================================")
        Timber.d("SYNCWORKER STARTED")
        Timber.d("Run attempt: $runAttemptCount")
        Timber.d("========================================")

        return try {
            Timber.d("Input data:")
            Timber.d("  - Account ID: $accountId")
            Timber.d("  - Sync Type: $syncType")
            Timber.d("  - Calendar ID: $calendarId")
            Timber.d("  - AddressBook ID: $addressBookId")
            Timber.d("  - Force WebCal: $forceWebCal")

            if (accountId == -1L) {
                Timber.w("No account ID specified, syncing all accounts")
                syncAllAccounts(syncType, forceWebCal)
            } else {
                Timber.d("→ Syncing account ID: $accountId, type: $syncType")
                syncAccount(accountId, syncType, calendarId, addressBookId, forceWebCal)
            }

            Timber.d("========================================")
            Timber.d("✓ SYNCWORKER COMPLETED SUCCESSFULLY")
            Timber.d("========================================")
            Result.success()
        } catch (e: CancellationException) {
            // Treat user/OS-triggered cancellation as a normal outcome, not an error
            Timber.i("Sync cancelled: ${e.message ?: "job cancelled"}")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "✗ SyncWorker failed")
            
            // Show user-friendly error notification
            val error = AppError.fromThrowable(e)
            NotificationHelper.showErrorNotification(applicationContext, error)
            
            if (runAttemptCount < 3) {
                Timber.d("→ Retry attempt $runAttemptCount")
                Result.retry()
            } else {
                Timber.e("✗ Max retries reached")
                Result.failure()
            }
        } finally {
            runningSyncs.remove(syncSignature)
        }
    }

    private fun buildSyncSignature(
        accountId: Long,
        syncType: String,
        calendarId: Long,
        addressBookId: Long,
        forceWebCal: Boolean
    ): String {
        val accountKey = if (accountId == -1L) "ALL" else accountId.toString()
        val calendarKey = if (calendarId == -1L) "*" else calendarId.toString()
        val addressKey = if (addressBookId == -1L) "*" else addressBookId.toString()
        val webCalKey = if (forceWebCal) "1" else "0"
        return listOf(accountKey, syncType, calendarKey, addressKey, webCalKey).joinToString(separator = "/")
    }
    
    private suspend fun syncAllAccounts(syncType: String, forceWebCal: Boolean) {
        Timber.d("Syncing all accounts with type: $syncType")
        
        val accounts = accountRepository.getAll()
        Timber.d("Found ${accounts.size} accounts to sync")
        
        for (account in accounts) {
            syncAccount(account.id, syncType, -1L, -1L, forceWebCal)
        }
    }
    
    private suspend fun syncAccount(
        accountId: Long, 
        syncType: String,
        calendarId: Long = -1L,
        addressBookId: Long = -1L,
        forceWebCal: Boolean
    ) {
        Timber.d("Syncing account $accountId")
        
        // Get account from repository
        val account = accountRepository.getById(accountId)
        if (account == null) {
            Timber.e("Account not found: $accountId")
            return
        }
        
        when (syncType) {
            SYNC_TYPE_CALENDAR -> syncCalendars(account, calendarId, forceWebCal)
            SYNC_TYPE_CONTACTS -> syncContacts(account, addressBookId)
            SYNC_TYPE_TASKS -> syncTasks(account)
            SYNC_TYPE_ALL -> {
                // Parallel sync for all resource types
                coroutineScope {
                    val calendarJob = async { syncCalendars(account, calendarId, forceWebCal) }
                    val contactsJob = async { syncContacts(account, addressBookId) }
                    val tasksJob = async { syncTasks(account) }
                    
                    // Wait for all syncs to complete
                    calendarJob.await()
                    contactsJob.await()
                    tasksJob.await()
                }
            }
        }
    }
    
    private suspend fun syncCalendars(
        account: com.davy.domain.model.Account,
        calendarId: Long = -1L,
        forceWebCal: Boolean
    ) {
        Timber.d("========================================")
        Timber.d("SYNCING CALENDARS")
        Timber.d("Account: ${account.accountName} (ID: ${account.id})")
        if (calendarId != -1L) {
            Timber.d("Specific Calendar ID: $calendarId")
        }
        Timber.d("========================================")
        
        try {
            if (calendarId != -1L) {
                // Sync specific calendar only
                Timber.d("→ Calling CalDAVSyncService.syncCalendar($calendarId)...")
                val calendar = calendarRepository.getById(calendarId)
                if (calendar == null) {
                    Timber.e("Calendar not found: $calendarId")
                    return
                }
                val result = calDAVSyncService.syncCalendar(account, calendar)
                
                when (result) {
                    is com.davy.data.sync.SyncResult.Success -> {
                        Timber.d("========================================")
                        Timber.d("✓ CALENDAR SYNC SUCCESSFUL")
                        Timber.d("  Events Downloaded: ${result.eventsDownloaded}")
                        Timber.d("  Events Uploaded: ${result.eventsUploaded}")
                        Timber.d("========================================")
                        // Mirror only this calendar to Android Calendar Provider so events appear in Calendar app
                        Timber.d("→ Syncing DAVy data to Android Calendar Provider (single calendar path, scoped)…")
                        val providerResult = calendarContractSync.syncSingleCalendarToProvider(account.id, calendar.id)
                        when (providerResult) {
                            is com.davy.sync.calendar.CalendarContractResult.Success -> {
                                Timber.d("✓ Calendar Provider sync: +${providerResult.calendarsCreated} cal, ↓${providerResult.eventsInserted} ins, ↺${providerResult.eventsUpdated} upd, 🗑️${providerResult.eventsDeleted} del")
                            }
                            is com.davy.sync.calendar.CalendarContractResult.Error -> {
                                Timber.e("✗ Calendar Provider sync failed: ${providerResult.message}")
                            }
                        }
                    }
                    is com.davy.data.sync.SyncResult.Error -> {
                        Timber.e("========================================")
                        Timber.e("✗ CALENDAR SYNC FAILED")
                        Timber.e("Error: ${result.message}")
                        Timber.e("========================================")
                    }
                }
            } else {
                // Sync all calendars for the account
                Timber.d("→ Calling CalDAVSyncService.syncAccount()...")
                val result = calDAVSyncService.syncAccount(account)
                
                when (result) {
                    is com.davy.data.sync.SyncResult.Success -> {
                        Timber.d("========================================")
                        Timber.d("✓ CALENDAR SYNC SUCCESSFUL")
                        Timber.d("  Calendars Added: ${result.calendarsAdded}")
                        Timber.d("  Events Downloaded: ${result.eventsDownloaded}")
                        Timber.d("  Events Uploaded: ${result.eventsUploaded}")
                        Timber.d("========================================")
                        // Mirror DAVy DB to Android Calendar Provider so events appear in Calendar app
                        Timber.d("→ Syncing DAVy data to Android Calendar Provider (account path)...")
                        val providerResult = calendarContractSync.syncToCalendarProvider(account.id)
                        when (providerResult) {
                            is com.davy.sync.calendar.CalendarContractResult.Success -> {
                                Timber.d("✓ Calendar Provider sync: +${providerResult.calendarsCreated} cal, ↓${providerResult.eventsInserted} ins, ↺${providerResult.eventsUpdated} upd, 🗑️${providerResult.eventsDeleted} del")
                            }
                            is com.davy.sync.calendar.CalendarContractResult.Error -> {
                                Timber.e("✗ Calendar Provider sync failed: ${providerResult.message}")
                            }
                        }

                        // After successful account calendar sync, prune locally stored calendars
                        // that no longer exist on the server. This keeps the phone in sync when
                        // calendars were removed on the server side and user runs "Sync all".
                        try {
                            val password = credentialStore.getPassword(account.id)
                            if (password == null) {
                                Timber.w("Cannot prune calendars: missing credentials for account ${account.id}")
                            } else {
                                // Discover principal using full service discovery with fallback
                                val principalInfo = try {
                                    Timber.d("Attempting full service discovery for pruning")
                                    val authResult = authenticationManager.authenticate(
                                        serverUrl = account.serverUrl,
                                        username = account.username,
                                        password = password
                                    )
                                    
                                    if (!authResult.hasCalDAV() || authResult.calDavPrincipal == null) {
                                        throw com.davy.data.remote.caldav.PrincipalDiscoveryException("CalDAV service not available from authentication")
                                    }
                                    
                                    Timber.d("Full discovery succeeded for pruning, using calendarHomeSet: ${authResult.calDavPrincipal.calendarHomeSet}")
                                    authResult.calDavPrincipal
                                } catch (e: Exception) {
                                    // Fallback to direct principal discovery
                                    Timber.w(e, "Full discovery failed for pruning, falling back to direct principal discovery")
                                    principalDiscovery.discoverPrincipal(
                                        baseUrl = account.serverUrl.trimEnd('/'),
                                        username = account.username,
                                        password = password
                                    )
                                }
                                
                                val discovered = principalDiscovery.discoverCalendars(
                                    calendarHomeSetUrl = principalInfo.calendarHomeSet,
                                    username = account.username,
                                    password = password
                                )
                                val discoveredCalendarUrls = discovered
                                    .filter { it.source == null }
                                    .map { it.url }
                                    .toSet()

                                val localCalendars = calendarRepository.getByAccountId(account.id)
                                val toRemove = localCalendars.filter { local ->
                                    local.source == null && !discoveredCalendarUrls.contains(local.calendarUrl)
                                }
                                if (toRemove.isNotEmpty()) {
                                    Timber.d("Pruning ${toRemove.size} calendars no longer present on server (sync all path)")
                                }
                                toRemove.forEach { localCal ->
                                    try {
                                        if (localCal.androidCalendarId != null) {
                                            calendarContractSync.deleteCalendarFromProvider(
                                                androidCalendarId = localCal.androidCalendarId,
                                                accountName = account.accountName
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Timber.w(e, "Provider cleanup failed for pruned calendar ${localCal.displayName}")
                                    }
                                    try {
                                        calendarRepository.delete(localCal)
                                        Timber.d("Pruned calendar '${localCal.displayName}' from local DB")
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to delete pruned calendar ${localCal.displayName}")
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            // Propagate cancellation without error noise
                            Timber.i("Calendar prune cancelled")
                            return
                        } catch (e: Exception) {
                            Timber.e(e, "Error during pruning after account sync")
                        }
                    }
                    is com.davy.data.sync.SyncResult.Error -> {
                        Timber.e("========================================")
                        Timber.e("✗ CALENDAR SYNC FAILED")
                        Timber.e("Error: ${result.message}")
                        Timber.e("========================================")
                    }
                }
            }

            // Only sync WebCal when explicitly requested (forceWebCal)
            if (forceWebCal) {
                try {
                    val webCalResults = webCalSyncService.syncAccountSubscriptions(account.id, force = true)
                    Timber.d("WebCal sync processed ${webCalResults.size} subscriptions for ${account.accountName}")
                    webCalResults.forEach { (subscriptionId, result) ->
                        when (result) {
                            is com.davy.sync.webcal.WebCalSyncResult.Success -> Timber.d("WebCal subscription $subscriptionId synced (${result.eventsUpdated} events)")
                            is com.davy.sync.webcal.WebCalSyncResult.Error -> Timber.e("WebCal subscription $subscriptionId failed: ${result.errorMessage}")
                            is com.davy.sync.webcal.WebCalSyncResult.NotModified -> Timber.d("WebCal subscription $subscriptionId not modified")
                        }
                    }
                } catch (e: CancellationException) {
                    // Propagate cancellation without showing error notification
                    Timber.i("WebCal sync cancelled")
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync WebCal subscriptions for account ${account.accountName}")
                    NotificationHelper.showErrorNotification(
                        applicationContext,
                        AppError.SyncFailed("Failed to sync calendar subscriptions"),
                        account.accountName,
                        account.id
                    )
                }
            }
        } catch (e: CancellationException) {
            Timber.i("Calendar sync cancelled")
            return
        } catch (e: Exception) {
            Timber.e(e, "========================================")
            Timber.e("✗ EXCEPTION DURING CALENDAR SYNC")
            Timber.e("========================================")
            
            // Show error notification with account context
            val error = AppError.fromThrowable(e)
            NotificationHelper.showErrorNotification(
                applicationContext,
                error,
                account.accountName,
                account.id
            )
        }
    }
    
    private suspend fun syncContacts(
        account: com.davy.domain.model.Account,
        addressBookId: Long = -1L
    ) {
        Timber.d("========================================")
        Timber.d("SYNCING CONTACTS")
        Timber.d("Account: ${account.accountName} (ID: ${account.id})")
        if (addressBookId != -1L) {
            Timber.d("Specific AddressBook ID: $addressBookId")
        }
        Timber.d("========================================")
        
        try {
            if (addressBookId != -1L) {
                // Sync specific address book only
                Timber.d("→ Calling CardDAVSyncService.syncAddressBook($addressBookId)...")
                val addressBook = addressBookRepository.getById(addressBookId)
                if (addressBook == null) {
                    Timber.e("AddressBook not found: $addressBookId")
                    return
                }
                val result = cardDAVSyncService.syncAddressBook(account, addressBook)
                
                Timber.d("========================================")
                Timber.d("✓ ADDRESSBOOK SYNC SUCCESSFUL")
                Timber.d("  Contacts Downloaded: ${result.contactsDownloaded}")
                Timber.d("  Contacts Uploaded: ${result.contactsUploaded}")
                Timber.d("  Contacts Deleted: ${result.contactsDeleted}")
                Timber.d("========================================")
            } else {
                // Sync all address books for the account
                Timber.d("→ Calling CardDAVSyncService.syncAccount()...")
                val result = cardDAVSyncService.syncAccount(account)
                
                Timber.d("========================================")
                Timber.d("✓ CONTACT SYNC SUCCESSFUL")
                Timber.d("  Address Books Added: ${result.addressBooksAdded}")
                Timber.d("  Contacts Downloaded: ${result.contactsDownloaded}")
                Timber.d("  Contacts Uploaded: ${result.contactsUploaded}")
                Timber.d("  Contacts Deleted: ${result.contactsDeleted}")
                Timber.d("========================================")
            }
        } catch (e: CancellationException) {
            Timber.i("Contacts sync cancelled")
            return
        } catch (e: Exception) {
            Timber.e(e, "========================================")
            Timber.e("✗ EXCEPTION DURING CONTACT SYNC")
            Timber.e("========================================")
        }
    }
    
    private suspend fun syncTasks(account: com.davy.domain.model.Account) {
        Timber.d("========================================")
        Timber.d("SYNCING TASKS")
        Timber.d("Account: ${account.accountName} (ID: ${account.id})")
        Timber.d("========================================")
        
        try {
            // Get all task lists for this account
            val taskLists = taskListRepository.getByAccountId(account.id).first()
            Timber.d("Found ${taskLists.size} task lists for account: ${account.accountName}")
            
            if (taskLists.isEmpty()) {
                Timber.w("No task lists found for account ${account.accountName}. Task list discovery should happen during account creation.")
                return
            }
            
            var tasksDownloaded = 0
            var tasksUploaded = 0
            
            // Sync tasks for each task list
            coroutineScope {
                val syncJobs = taskLists.map { taskList ->
                    async {
                        if (!taskList.syncEnabled) {
                            Timber.d("Skipping disabled task list: ${taskList.displayName}")
                            return@async 0 to 0
                        }
                        
                        try {
                            Timber.d("Syncing task list: ${taskList.displayName} (${taskList.url})")
                            calDAVSyncService.syncTaskList(account, taskList)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync task list: ${taskList.displayName}")
                            0 to 0
                        }
                    }
                }
                
                // Collect all results
                val results = syncJobs.map { it.await() }
                tasksDownloaded = results.sumOf { it.first }
                tasksUploaded = results.sumOf { it.second }
            }
            
            Timber.d("========================================")
            Timber.d("✓ TASK SYNC SUCCESSFUL")
            Timber.d("  Tasks Downloaded: $tasksDownloaded")
            Timber.d("  Tasks Uploaded: $tasksUploaded")
            Timber.d("========================================")
        } catch (e: CancellationException) {
            Timber.i("Tasks sync cancelled")
            return
        } catch (e: Exception) {
            Timber.e(e, "========================================")
            Timber.e("✗ EXCEPTION DURING TASK SYNC")
            Timber.e("========================================")
            
            // Show error notification with account context
            val error = AppError.fromThrowable(e)
            NotificationHelper.showErrorNotification(
                applicationContext,
                error,
                account.accountName,
                account.id
            )
        }
    }
}
