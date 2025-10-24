package com.davy.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.domain.model.Account
import com.davy.domain.model.AddressBook
import com.davy.domain.model.Calendar
import com.davy.domain.model.WebCalSubscription
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.WebCalSubscriptionRepository
import com.davy.data.remote.caldav.PrincipalDiscovery
import com.davy.data.remote.caldav.CalDAVClient
import com.davy.data.local.CredentialStore
import com.davy.sync.SyncManager
import com.davy.sync.SyncWorker
import com.davy.sync.webcal.WebCalSyncService
import com.davy.sync.calendar.CalendarContractSync
import com.davy.sync.account.AndroidAccountManager
import com.davy.domain.usecase.UpdateCalendarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val addressBookRepository: AddressBookRepository,
    private val webCalSubscriptionRepository: WebCalSubscriptionRepository,
    private val caldavPrincipalDiscovery: PrincipalDiscovery,
    private val caldavClient: CalDAVClient,
    private val credentialStore: CredentialStore,
    private val syncManager: SyncManager,
    private val webCalSyncService: WebCalSyncService,
    private val accountSyncConfigurationManager: com.davy.sync.AccountSyncConfigurationManager,
    private val calendarContractSync: CalendarContractSync,
    private val androidAccountManager: AndroidAccountManager,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val updateAddressBookUseCase: com.davy.domain.usecase.UpdateAddressBookUseCase,
    private val renameCalendarUseCase: com.davy.domain.usecase.RenameCalendarUseCase,
    private val renameAddressBookUseCase: com.davy.domain.usecase.RenameAddressBookUseCase,
    private val exportSettingsUseCase: com.davy.domain.usecase.ExportSettingsUseCase,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle
) : ViewModel() {

    // PERFORMANCE: Internal individual StateFlows for flexibility
    private val _account = MutableStateFlow<Account?>(null)
    private val _calendars = MutableStateFlow<List<Calendar>>(emptyList())
    private val _addressBooks = MutableStateFlow<List<AddressBook>>(emptyList())
    private val _webCalSubscriptions = MutableStateFlow<List<WebCalSubscription>>(emptyList())
    private val _isSyncing = MutableStateFlow(false)
    private val _syncingCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _syncingAddressBookIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isRefreshingCollections = MutableStateFlow(false)
    private val _accountDeleted = MutableStateFlow(false)
    
    // Batch selection state for multi-select sync operations
    private val _isBatchSelectionMode = MutableStateFlow(false)
    private val _selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedAddressBookIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedWebCalIds = MutableStateFlow<Set<Long>>(emptySet())
    
    val isBatchSelectionMode = _isBatchSelectionMode.asStateFlow()
    val selectedCalendarIds = _selectedCalendarIds.asStateFlow()
    val selectedAddressBookIds = _selectedAddressBookIds.asStateFlow()
    val selectedWebCalIds = _selectedWebCalIds.asStateFlow()

    // PERFORMANCE CRITICAL: Consolidated UI state - Single collection point
    // This dramatically reduces recompositions by combining all state into one flow
    // Instead of 8 separate collectAsState() calls causing 8 recomposition triggers,
    // we have 1 collectAsState() call = 1 recomposition trigger
    // Reference: https://developer.android.com/develop/ui/compose/performance/bestpractices
    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<AccountDetailUiState> = combine(
        _account,
        _calendars,
        _addressBooks,
        _webCalSubscriptions,
        _isSyncing,
        _syncingCalendarIds,
        _syncingAddressBookIds,
        _isRefreshingCollections,
        _accountDeleted
    ) { flows: Array<Any?> ->
        AccountDetailUiState(
            account = flows[0] as Account?,
            calendars = flows[1] as List<Calendar>,
            addressBooks = flows[2] as List<AddressBook>,
            webCalSubscriptions = flows[3] as List<WebCalSubscription>,
            isSyncing = flows[4] as Boolean,
            syncingCalendarIds = flows[5] as Set<Long>,
            syncingAddressBookIds = flows[6] as Set<Long>,
            isRefreshingCollections = flows[7] as Boolean,
            accountDeleted = flows[8] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,  // PERFORMANCE: Eager loading for instant UI
        initialValue = AccountDetailUiState()
    )

    private var currentAccountId: Long? = null
    
    init {
        // PERFORMANCE: Preload data on ViewModel creation to avoid lazy loading delays
        // This ensures data is ready when the screen is displayed
    }

    fun loadAccount(accountId: Long) {
        if (currentAccountId == accountId) return
        currentAccountId = accountId

        // Observe account by ID so UI updates immediately on changes (e.g., rename from another screen)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                accountRepository.getByIdFlow(accountId).collect { acc ->
                    _account.value = acc
                }
            }
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Load calendars - Flow collection on IO thread
                calendarRepository.getByAccountIdFlow(accountId).collect { cals ->
                    _calendars.value = cals
                }
            }
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Load address books - Flow collection on IO thread
                addressBookRepository.getByAccountIdFlow(accountId).collect { books ->
                    _addressBooks.value = books
                }
            }
        }
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Load WebCal subscriptions - Flow collection on IO thread
                webCalSubscriptionRepository.getByAccountIdFlow(accountId).collect { subscriptions ->
                    _webCalSubscriptions.value = subscriptions
                }
            }
        }
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Observe sync state for this account - Flow collection on IO thread
                syncManager.observeSyncState(accountId).collect { syncing ->
                    _isSyncing.value = syncing
                }
            }
        }
    }

    fun updateAccount(account: Account, newPassword: String? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                accountRepository.update(account)
                
                // Update password in credential store if provided
                if (newPassword != null && newPassword.isNotBlank()) {
                    credentialStore.storePassword(account.id, newPassword)
                }
            }
        }
    }
    
    fun updateSyncConfiguration(accountId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Schedule separate sync jobs for each service type with individual intervals
                accountSyncConfigurationManager.scheduleServiceSpecificSync(accountId)
            }
        }
    }

    fun renameAccount(newName: String) {
        val acc = _account.value ?: return
        val oldName = acc.accountName
        
        timber.log.Timber.d("renameAccount called: oldName='$oldName', newName='$newName'")
        
        if (newName.isBlank()) {
            timber.log.Timber.e("Cannot rename: new name is blank")
            return
        }
        
        if (oldName == newName) {
            timber.log.Timber.d("No rename needed: names are identical")
            return
        }
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                timber.log.Timber.d("Starting Android account rename...")

                // 1. Rename the main Android account (this migrates calendar/contact data)
                val androidRenamed = androidAccountManager.renameAccount(oldName, newName)
                if (!androidRenamed) {
                    timber.log.Timber.e("Failed to rename Android account from '$oldName' to '$newName'")
                    return@withContext
                }
                timber.log.Timber.d("Android account renamed successfully")

                // 2. Update our database EARLY so the UI and lists refresh immediately
                timber.log.Timber.d("Updating database with new account name early for instant UI refresh…")
                accountRepository.update(acc.copy(accountName = newName))
                withContext(Dispatchers.Main) {
                    _account.value = acc.copy(accountName = newName)
                }
                timber.log.Timber.d("Database updated; UI name refreshed")

                // 3. Update address book accounts' user data and names
                timber.log.Timber.d("Updating address book account references…")
                androidAccountManager.updateAddressBookAccountReferences(oldName, newName)

                // 4. Update calendar provider so calendars show up with new account name
                timber.log.Timber.d("Updating calendar provider account names…")
                androidAccountManager.updateCalendarProviderAccount(oldName, newName)

                // 5. Re-provision calendars in Calendar Provider, in case the provider removed rows during rename
                try {
                    timber.log.Timber.d("Re-provisioning calendars in Calendar Provider after rename…")
                    val result = calendarContractSync.syncToCalendarProvider(acc.id)
                    when (result) {
                        is com.davy.sync.calendar.CalendarContractResult.Success ->
                            timber.log.Timber.d("Calendar Provider reprovision complete: created=${result.calendarsCreated} inserted=${result.eventsInserted} updated=${result.eventsUpdated} deleted=${result.eventsDeleted}")
                        is com.davy.sync.calendar.CalendarContractResult.Error ->
                            timber.log.Timber.w("Calendar Provider reprovision skipped/failed: %s", result.message)
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Calendar Provider reprovision threw, continuing")
                }

                // 6. Force-enable all calendars for this account (VISIBLE=1, SYNC_EVENTS=1) to match account creation behavior
                try {
                    val updated = calendarContractSync.enableAllCalendarsForAccount(newName)
                    timber.log.Timber.d("Enabled calendars after rename (rows updated=%d)", updated)
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Failed to enable calendars after rename")
                }
            }

            timber.log.Timber.d("Account rename complete!")
        }
    }

    fun deleteAccount() {
        val acc = _account.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                timber.log.Timber.d("Starting account deletion for: ${acc.accountName}")
                
                // 1. Remove address book accounts first
                val addressBookAccountsRemoved = androidAccountManager.removeAddressBookAccounts(acc.accountName)
                if (addressBookAccountsRemoved) {
                    timber.log.Timber.d("Successfully removed address book accounts")
                } else {
                    timber.log.Timber.w("Failed to remove some address book accounts")
                }
                
                // 2. Remove main Android account (this also removes calendars)
                val androidRemoved = androidAccountManager.removeAccount(acc.accountName)
                if (androidRemoved) {
                    timber.log.Timber.d("Successfully removed Android account")
                } else {
                    timber.log.Timber.w("Failed to remove Android account")
                }
                
                // 3. Finally remove from database
                accountRepository.delete(acc)
                timber.log.Timber.d("Account deletion complete")
                
                // 4. Set deletion complete flag to trigger navigation
                _accountDeleted.value = true
            }
        }
    }

    /**
     * Delete multiple calendars by their IDs from both server and device.
     * Performs server DELETE (if URL exists), removes from provider, and deletes from DB.
     */
    fun deleteCalendars(calendarIds: Collection<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val account = _account.value ?: return@withContext
                val password = credentialStore.getPassword(account.id)
                calendarIds.forEach { id ->
                    val calendar = _calendars.value.find { it.id == id } ?: return@forEach
                    // Guard: do not delete calendars that cannot be deleted (read-only or unbind not permitted)
                    if (!calendar.canDelete()) {
                        Timber.tag("AccountDetailViewModel").w("Skip deleting read-only or protected calendar: %s", calendar.displayName)
                        return@forEach
                    }
                    try {
                        // Server deletion if URL present
                        if (calendar.calendarUrl.isNotBlank() && password != null) {
                            Timber.tag("AccountDetailViewModel").d("Deleting calendar from server: %s", calendar.calendarUrl)
                            val response = caldavClient.deleteCalendar(
                                calendarUrl = calendar.calendarUrl,
                                username = account.username,
                                password = password
                            )
                            if (!response.isSuccessful) {
                                Timber.tag("AccountDetailViewModel").w("Failed to delete calendar from server: %s - %s", response.statusCode, response.error)
                            }
                        }

                        // Provider deletion
                        if (calendar.androidCalendarId != null) {
                            calendarContractSync.deleteCalendarFromProvider(
                                androidCalendarId = calendar.androidCalendarId,
                                accountName = account.accountName
                            )
                        }

                        // DB deletion
                        calendarRepository.delete(calendar)
                    } catch (e: Exception) {
                        Timber.tag("AccountDetailViewModel").e(e, "Failed to delete calendar %s", calendar.displayName)
                    }
                }
            }
        }
    }

    fun createCalendar(name: String, color: Int) {
        val accountId = currentAccountId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val account = _account.value ?: return@withContext
                val password = credentialStore.getPassword(account.id) ?: return@withContext
                
                // Generate UUID for folder name
                val folderName = java.util.UUID.randomUUID().toString()
                
                // Build calendar URL: serverUrl/remote.php/dav/calendars/username/UUID/
                val calendarUrl = "${account.serverUrl.trimEnd('/')}/remote.php/dav/calendars/${account.username}/$folderName/"
                
                Timber.tag("AccountDetailViewModel").d("Creating calendar at: %s", calendarUrl)
                
                // Create calendar on server first via MKCALENDAR
                val response = caldavClient.mkCalendar(
                    calendarUrl = calendarUrl,
                    username = account.username,
                    password = password,
                    displayName = name,
                    description = null,
                    color = color
                )
                
                if (!response.isSuccessful) {
                    Timber.tag("AccountDetailViewModel").e("Failed to create calendar on server: %s - %s", response.statusCode, response.error)
                    return@withContext
                }
                
                Timber.tag("AccountDetailViewModel").d("Calendar created on server successfully")
                
                // Server creation successful -> create local calendar with URL
                val calendar = Calendar(
                    id = 0,
                    accountId = accountId,
                    displayName = name,
                    calendarUrl = calendarUrl,
                    color = color
                )
                val newCalendarId = calendarRepository.insert(calendar)
                
                Timber.tag("AccountDetailViewModel").d("Calendar inserted into local database")
                
                // Sync only the new calendar to Android Calendar Provider so it appears in system Calendar app
                val syncResult = calendarContractSync.syncSingleCalendarToProvider(accountId, newCalendarId)
                when (syncResult) {
                    is com.davy.sync.calendar.CalendarContractResult.Success -> {
                        Timber.tag("AccountDetailViewModel").d("Calendar synced to Calendar Provider successfully (single)")
                    }
                    is com.davy.sync.calendar.CalendarContractResult.Error -> {
                        Timber.tag("AccountDetailViewModel").e("Failed to sync calendar to Calendar Provider: %s", syncResult.message)
                    }
                }
                
                // Trigger sync scoped to this new calendar only (no WebCal)
                syncManager.syncCalendar(accountId, newCalendarId)
            }
        }
    }

    fun deleteCalendar(calendar: Calendar) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val account = _account.value ?: return@withContext
                val password = credentialStore.getPassword(account.id) ?: return@withContext
                // Guard: prevent deleting calendars that cannot be deleted
                if (!calendar.canDelete()) {
                    Timber.tag("AccountDetailViewModel").w("Attempted to delete non-deletable calendar: %s", calendar.displayName)
                    return@withContext
                }
                
                // Skip server deletion if calendar has no URL (was never created on server)
                if (calendar.calendarUrl.isNotBlank()) {
                    Timber.tag("AccountDetailViewModel").d("Deleting calendar from server: %s", calendar.calendarUrl)
                    
                    // Delete from server first
                    val response = caldavClient.deleteCalendar(
                        calendarUrl = calendar.calendarUrl,
                        username = account.username,
                        password = password
                    )
                    
                    if (!response.isSuccessful) {
                        Timber.tag("AccountDetailViewModel").e("Failed to delete calendar from server: %s - %s", response.statusCode, response.error)
                        // Continue with local deletion even if server deletion failed (might be 404/410)
                    } else {
                        Timber.tag("AccountDetailViewModel").d("Calendar deleted from server successfully")
                    }
                }
                
                // Delete from Android Calendar Provider (system Calendar app)
                if (calendar.androidCalendarId != null) {
                    Timber.tag("AccountDetailViewModel").d("Deleting calendar from Calendar Provider: %s", calendar.androidCalendarId)
                    val providerDeleted = calendarContractSync.deleteCalendarFromProvider(
                        androidCalendarId = calendar.androidCalendarId,
                        accountName = account.accountName
                    )
                    if (providerDeleted) {
                        Timber.tag("AccountDetailViewModel").d("Calendar deleted from Calendar Provider successfully")
                    } else {
                        Timber.tag("AccountDetailViewModel").e("Failed to delete calendar from Calendar Provider")
                    }
                }
                
                // Delete from local database
                calendarRepository.delete(calendar)
                Timber.tag("AccountDetailViewModel").d("Calendar deleted from local database")
            }
        }
    }

    fun createAddressBook(name: String) {
        val accountId = currentAccountId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val account = _account.value ?: return@withContext
                val password = credentialStore.getPassword(account.id) ?: return@withContext

                // Build CardDAV address book URL: /remote.php/dav/addressbooks/users/{username}/{UUID}/
                val folderName = java.util.UUID.randomUUID().toString()
                val addressBookUrl = "${account.serverUrl.trimEnd('/')}/remote.php/dav/addressbooks/users/${account.username}/$folderName/"

                // Create address book on server using MKCOL with CardDAV addressbook resource type
                try {
                    val mkcolXml = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <d:mkcol xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                          <d:set>
                            <d:prop>
                              <d:resourcetype>
                                <d:collection/>
                                <card:addressbook/>
                              </d:resourcetype>
                              <d:displayname>${name}</d:displayname>
                            </d:prop>
                          </d:set>
                        </d:mkcol>
                    """.trimIndent()

                    val mediaType = "application/xml; charset=utf-8".toMediaType()
                    val body = mkcolXml.toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(addressBookUrl)
                        .method("MKCOL", body)
                        .header("Authorization", Credentials.basic(account.username, password))
                        .header("Content-Type", "application/xml; charset=utf-8")
                        .build()

                    // Use a short-lived client for this call (CardDAV client not wired here)
                    val client = OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        Timber.tag("AccountDetailViewModel").e(
                            "Failed to create address book on server: %s - %s",
                            response.code,
                            response.message
                        )
                        return@withContext
                    }
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Exception creating address book on server")
                    return@withContext
                }

                // Server creation successful -> insert local address book with URL
                val newAddressBook = AddressBook(
                    id = 0,
                    accountId = accountId,
                    displayName = name,
                    url = addressBookUrl,
                    syncEnabled = true,
                    visible = true
                )
                val newId = addressBookRepository.insert(newAddressBook)

                // Provision an Android account for this address book so contacts appear editable/visible
                try {
                    androidAccountManager.createAddressBookAccount(
                        mainAccountName = account.accountName,
                        addressBookName = name,
                        addressBookId = newId,
                        addressBookUrl = addressBookUrl
                    )
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").w(e, "Failed to create Android address book account")
                }

                // Trigger sync scoped to this new address book
                try {
                    syncManager.syncAddressBook(accountId, newId)
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to trigger sync for new address book")
                }
            }
        }
    }

    /**
     * Delete a single address book from both server and device.
     * - Sends HTTP DELETE to CardDAV collection URL (if present)
     * - Removes the per-address-book Android account (provider cleanup)
     * - Deletes the address book from local database
     */
    fun deleteAddressBook(addressBook: AddressBook) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val account = _account.value ?: return@withContext
                val password = credentialStore.getPassword(account.id)
                try {
                    // Guard: prevent deleting read-only address books
                    if (addressBook.isReadOnly()) {
                        Timber.tag("AccountDetailViewModel").w("Attempted to delete read-only address book: %s", addressBook.displayName)
                        return@withContext
                    }
                    // Delete from server (CardDAV) if URL is present
                    if (addressBook.url.isNotBlank() && password != null) {
                        try {
                            val request = okhttp3.Request.Builder()
                                .url(addressBook.url)
                                .delete()
                                .header("Authorization", okhttp3.Credentials.basic(account.username, password))
                                .build()
                            // short-lived client for this call
                            val client = okhttp3.OkHttpClient()
                            val response = client.newCall(request).execute()
                            if (!response.isSuccessful && response.code !in listOf(204, 404, 410)) {
                                Timber.tag("AccountDetailViewModel").w("Failed to delete address book on server: %s - %s", response.code, response.message)
                            }
                        } catch (e: Exception) {
                            Timber.tag("AccountDetailViewModel").w(e, "Exception deleting address book on server")
                            // Continue with local/provider deletion
                        }
                    }

                    // Remove the Android address book account (also cleans provider data)
                    try {
                        androidAccountManager.removeAddressBookAccountById(account.accountName, addressBook.id)
                    } catch (e: Exception) {
                        Timber.tag("AccountDetailViewModel").w(e, "Failed to remove Android address book account")
                    }

                    // Delete from local database
                    addressBookRepository.delete(addressBook)
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to delete address book %s", addressBook.displayName)
                }
            }
        }
    }

    /**
     * Delete multiple address books by their IDs (server + provider + DB).
     */
    fun deleteAddressBooks(addressBookIds: Collection<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val account = _account.value ?: return@withContext
                val password = credentialStore.getPassword(account.id)
                addressBookIds.forEach { id ->
                    val addressBook = _addressBooks.value.find { it.id == id } ?: return@forEach
                    // Guard: skip read-only address books
                    if (addressBook.isReadOnly()) {
                        Timber.tag("AccountDetailViewModel").w("Skip deleting read-only address book: %s", addressBook.displayName)
                        return@forEach
                    }
                    try {
                        // Server deletion
                        if (addressBook.url.isNotBlank() && password != null) {
                            try {
                                val request = okhttp3.Request.Builder()
                                    .url(addressBook.url)
                                    .delete()
                                    .header("Authorization", okhttp3.Credentials.basic(account.username, password))
                                    .build()
                                val client = okhttp3.OkHttpClient()
                                val response = client.newCall(request).execute()
                                if (!response.isSuccessful && response.code !in listOf(204, 404, 410)) {
                                    Timber.tag("AccountDetailViewModel").w("Failed to delete address book on server: %s - %s", response.code, response.message)
                                }
                            } catch (e: Exception) {
                                Timber.tag("AccountDetailViewModel").w(e, "Exception deleting address book on server")
                            }
                        }

                        // Provider/account deletion
                        try {
                            androidAccountManager.removeAddressBookAccountById(account.accountName, addressBook.id)
                        } catch (e: Exception) {
                            Timber.tag("AccountDetailViewModel").w(e, "Failed to remove Android address book account")
                        }

                        // DB deletion
                        addressBookRepository.delete(addressBook)
                    } catch (e: Exception) {
                        Timber.tag("AccountDetailViewModel").e(e, "Failed to delete address book %s", addressBook.displayName)
                    }
                }
            }
        }
    }
    
    fun addWebCalSubscription(url: String, displayName: String) {
        val accountId = currentAccountId ?: return
        viewModelScope.launch {
            try {
                // Check if subscription with this URL already exists for this account
                val existing = webCalSubscriptionRepository.getByUrl(url)
                if (existing != null) {
                    Timber.tag("AccountDetailViewModel").w("WebCal subscription with URL %s already exists (ID: %s)", url, existing.id)
                    return@launch
                }
                
                val subscription = WebCalSubscription(
                    id = 0,
                    accountId = accountId,
                    subscriptionUrl = url,
                    displayName = displayName,
                    color = 0xFF2196F3.toInt(), // Default blue color
                    syncEnabled = true,
                    visible = true
                )
                webCalSubscriptionRepository.insert(subscription)
            } catch (e: Exception) {
                Timber.tag("AccountDetailViewModel").e(e, "Failed to add WebCal subscription: %s", e.message)
            }
        }
    }
    
    fun syncWebCalSubscriptions() {
        val accountId = currentAccountId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    webCalSyncService.syncAccountSubscriptions(accountId, force = true)
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to sync WebCal subscriptions")
                }
            }
        }
    }
    
    /**
     * Sync all calendars for the current account.
     */
    fun syncAllCalendars() {
        viewModelScope.launch {
            val accountId = currentAccountId ?: return@launch
            syncManager.syncNow(accountId, SyncWorker.SYNC_TYPE_CALENDAR)
        }
    }
    
    /**
     * Sync a specific calendar by its ID.
     * Tracks syncing state for visual feedback.
     */
    fun syncCalendar(calendarId: Long) {
        viewModelScope.launch {
            // Add to syncing set
            _syncingCalendarIds.value = _syncingCalendarIds.value + calendarId
            
            try {
                // Trigger calendar sync through SyncManager
                // Note: This will need SyncManager enhancement to support individual calendar sync
                val accountId = currentAccountId ?: return@launch
                syncManager.syncCalendar(accountId, calendarId)
            } finally {
                // Remove from syncing set after completion
                _syncingCalendarIds.value = _syncingCalendarIds.value - calendarId
            }
        }
    }
    
    /**
     * Sync all address books for the current account.
     */
    fun syncAllAddressBooks() {
        viewModelScope.launch {
            val accountId = currentAccountId ?: run {
                Timber.tag("AccountDetailViewModel").e("syncAllAddressBooks: currentAccountId is null")
                return@launch
            }
            Timber.tag("AccountDetailViewModel").d("syncAllAddressBooks: Triggering sync for account %s", accountId)
            syncManager.syncNow(accountId, SyncWorker.SYNC_TYPE_CONTACTS)
        }
    }
    
    /**
     * Sync a specific address book by its ID.
     * Tracks syncing state for visual feedback.
     */
    fun syncAddressBook(addressBookId: Long) {
        viewModelScope.launch {
            // Add to syncing set
            _syncingAddressBookIds.value = _syncingAddressBookIds.value + addressBookId
            
            try {
                // Trigger address book sync through SyncManager
                // Note: This will need SyncManager enhancement to support individual addressbook sync
                val accountId = currentAccountId ?: return@launch
                syncManager.syncAddressBook(accountId, addressBookId)
            } finally {
                // Remove from syncing set after completion
                _syncingAddressBookIds.value = _syncingAddressBookIds.value - addressBookId
            }
        }
    }
    
    /**
     * Toggle batch selection mode.
     */
    fun toggleBatchSelectionMode() {
        _isBatchSelectionMode.value = !_isBatchSelectionMode.value
        
        // Clear all selections when exiting batch mode
        if (!_isBatchSelectionMode.value) {
            _selectedCalendarIds.value = emptySet()
            _selectedAddressBookIds.value = emptySet()
            _selectedWebCalIds.value = emptySet()
        }
    }
    
    /**
     * Toggle calendar selection for batch operations.
     */
    fun toggleCalendarSelection(calendarId: Long) {
        _selectedCalendarIds.value = if (_selectedCalendarIds.value.contains(calendarId)) {
            _selectedCalendarIds.value - calendarId
        } else {
            _selectedCalendarIds.value + calendarId
        }
    }
    
    /**
     * Toggle address book selection for batch operations.
     */
    fun toggleAddressBookSelection(addressBookId: Long) {
        _selectedAddressBookIds.value = if (_selectedAddressBookIds.value.contains(addressBookId)) {
            _selectedAddressBookIds.value - addressBookId
        } else {
            _selectedAddressBookIds.value + addressBookId
        }
    }
    
    /**
     * Toggle webcal subscription selection for batch operations.
     */
    fun toggleWebCalSelection(webCalId: Long) {
        _selectedWebCalIds.value = if (_selectedWebCalIds.value.contains(webCalId)) {
            _selectedWebCalIds.value - webCalId
        } else {
            _selectedWebCalIds.value + webCalId
        }
    }
    
    /**
     * Sync selected collections in batch.
     * Syncs all selected calendars, address books, and webcal subscriptions in parallel.
     */
    fun syncSelectedCollections() {
        viewModelScope.launch {
            val accountId = currentAccountId ?: return@launch
            
            // Add all selected items to syncing sets
            _syncingCalendarIds.value = _syncingCalendarIds.value + _selectedCalendarIds.value
            _syncingAddressBookIds.value = _syncingAddressBookIds.value + _selectedAddressBookIds.value
            
            try {
                syncManager.syncBatch(
                    accountId = accountId,
                    calendarIds = _selectedCalendarIds.value.toList(),
                    addressBookIds = _selectedAddressBookIds.value.toList(),
                    webCalIds = _selectedWebCalIds.value.toList()
                )
            } finally {
                // Remove from syncing sets after completion
                _syncingCalendarIds.value = _syncingCalendarIds.value - _selectedCalendarIds.value
                _syncingAddressBookIds.value = _syncingAddressBookIds.value - _selectedAddressBookIds.value
                
                // Exit batch selection mode and clear selections
                _isBatchSelectionMode.value = false
                _selectedCalendarIds.value = emptySet()
                _selectedAddressBookIds.value = emptySet()
                _selectedWebCalIds.value = emptySet()
            }
        }
    }
    
    /**
     * Clear all batch selections.
     */
    fun clearBatchSelections() {
        _selectedCalendarIds.value = emptySet()
        _selectedAddressBookIds.value = emptySet()
        _selectedWebCalIds.value = emptySet()
    }
    
    /**
     * Select all calendars for batch operations.
     */
    fun selectAllCalendars() {
        _selectedCalendarIds.value = _calendars.value.map { it.id }.toSet()
    }
    
    /**
     * Select all address books for batch operations.
     */
    fun selectAllAddressBooks() {
        _selectedAddressBookIds.value = _addressBooks.value.map { it.id }.toSet()
    }
    
    /**
     * Select all webcal subscriptions for batch operations.
     */
    fun selectAllWebCals() {
        _selectedWebCalIds.value = _webCalSubscriptions.value.map { it.id }.toSet()
    }
    
    /**
     * Toggle synchronization for a calendar.
     */
    fun toggleCalendarSync(calendarId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val calendar = _calendars.value.find { it.id == calendarId } ?: return@withContext
                val newSyncEnabled = !calendar.syncEnabled
                Timber.tag("AccountDetailViewModel").d(
                    "toggleCalendarSync: %s (id=%s) -> %s",
                    calendar.displayName,
                    calendar.id,
                    newSyncEnabled
                )
                try {
                    // Delegate to use case so provider deletion/re-add and logging are handled centrally
                    val updated = calendar.copy(
                        syncEnabled = newSyncEnabled,
                        updatedAt = System.currentTimeMillis()
                    )
                    updateCalendarUseCase(updated)
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to toggle calendar sync")
                }
            }
        }
    }
    
    /**
     * Toggle read-only mode for a calendar.
     */
    fun toggleCalendarReadOnly(calendarId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val calendar = _calendars.value.find { it.id == calendarId } ?: return@withContext
                val newForceReadOnly = !calendar.forceReadOnly
                Timber.tag("AccountDetailViewModel").d(
                    "toggleCalendarReadOnly: %s (id=%s) -> %s",
                    calendar.displayName,
                    calendar.id,
                    newForceReadOnly
                )
                try {
                    // Update only read-only flag; keep other properties intact
                    calendarRepository.update(
                        calendar.copy(
                            forceReadOnly = newForceReadOnly,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to toggle read-only")
                }
            }
        }
    }
    
    /**
     * Update calendar color.
     */
    fun updateCalendarColor(calendarId: Long, color: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val calendar = _calendars.value.find { it.id == calendarId } ?: return@withContext
                calendarRepository.update(calendar.copy(color = color))
            }
        }
    }
    
    /**
     * Rename a calendar.
     */
    fun renameCalendar(calendarId: Long, newDisplayName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = renameCalendarUseCase(calendarId, newDisplayName)
                when (result) {
                    is com.davy.domain.usecase.RenameCalendarUseCase.Result.Success -> {
                        Timber.tag("AccountDetailViewModel").d("Calendar renamed to %s", result.newDisplayName)
                    }
                    is com.davy.domain.usecase.RenameCalendarUseCase.Result.Error -> {
                        Timber.tag("AccountDetailViewModel").e("Failed to rename calendar: %s", result.message)
                    }
                }
            }
        }
    }
    
    /**
     * Update calendar settings (WiFi-only, force read-only, sync interval).
     */
    fun updateCalendarSettings(calendarId: Long, wifiOnlySync: Boolean, forceReadOnly: Boolean, syncIntervalMinutes: Int?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val calendar = _calendars.value.find { it.id == calendarId } ?: return@withContext
                calendarRepository.update(calendar.copy(
                    wifiOnlySync = wifiOnlySync,
                    forceReadOnly = forceReadOnly,
                    syncIntervalMinutes = syncIntervalMinutes
                ))
            }
        }
    }
    
    /**
     * Rename an address book.
     */
    fun renameAddressBook(addressBookId: Long, newDisplayName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = renameAddressBookUseCase(addressBookId, newDisplayName)
                when (result) {
                    is com.davy.domain.usecase.RenameAddressBookUseCase.Result.Success -> {
                        Timber.tag("AccountDetailViewModel").d("Address book renamed to %s", result.newDisplayName)
                    }
                    is com.davy.domain.usecase.RenameAddressBookUseCase.Result.Error -> {
                        Timber.tag("AccountDetailViewModel").e("Failed to rename address book: %s", result.message)
                    }
                }
            }
        }
    }
    
    /**
     * Update address book settings (WiFi-only, sync interval).
     */
    fun updateAddressBookSettings(addressBookId: Long, wifiOnlySync: Boolean, syncIntervalMinutes: Int?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val addressBook = _addressBooks.value.find { it.id == addressBookId } ?: return@withContext
                addressBookRepository.update(addressBook.copy(
                    wifiOnlySync = wifiOnlySync,
                    syncIntervalMinutes = syncIntervalMinutes
                ))
            }
        }
    }
    
    /**
     * Toggle synchronization for an address book.
     */
    fun toggleAddressBookSync(addressBookId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val addressBook = _addressBooks.value.find { it.id == addressBookId } ?: return@withContext
                try {
                    val updated = addressBook.copy(
                        syncEnabled = !addressBook.syncEnabled,
                        updatedAt = System.currentTimeMillis()
                    )
                    // Delegate to use case to handle item-scoped sync on enable
                    updateAddressBookUseCase(updated)
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to toggle address book sync")
                }
            }
        }
    }
    
    /**
     * Toggle sync for a WebCal subscription.
     */
    fun toggleWebCalSync(subscriptionId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val subscription = _webCalSubscriptions.value.find { it.id == subscriptionId } ?: return@withContext
                val newSyncEnabled = !subscription.syncEnabled
                
                // If disabling sync, perform provider deletion asynchronously to avoid blocking UI
                if (!newSyncEnabled) {
                    // Optimistically update DB toggle state immediately
                    webCalSubscriptionRepository.update(subscription.copy(syncEnabled = false))

                    // If calendar exists in provider, delete it in background and then clear androidCalendarId
                    val androidCalId = subscription.androidCalendarId
                    val account = _account.value
                    if (androidCalId != null && account != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                Timber.tag("AccountDetailViewModel").d("Deleting WebCal calendar %s from provider (async)", androidCalId)
                                // Cancel any in-flight WebCal syncs for this account to avoid contention and delays
                                try {
                                    syncManager.cancelServiceSync(account.id, "webcal")
                                    syncManager.cancelSync(account.id)
                                } catch (e: Exception) {
                                    Timber.tag("AccountDetailViewModel").w(e, "Failed to cancel in-flight WebCal syncs before deletion")
                                }
                                calendarContractSync.deleteCalendarFromProvider(androidCalId, account.accountName)
                            } catch (e: Exception) {
                                Timber.tag("AccountDetailViewModel").e(e, "Failed to delete WebCal calendar from provider")
                            } finally {
                                try {
                                    webCalSubscriptionRepository.update(
                                        subscription.copy(
                                            syncEnabled = false,
                                            androidCalendarId = null
                                        )
                                    )
                                } catch (e: Exception) {
                                    Timber.tag("AccountDetailViewModel").e(e, "Failed to finalize WebCal disable update")
                                }
                            }
                        }
                    }
                    return@withContext
                } else if (newSyncEnabled) {
                    // If enabling sync, update database then trigger sync ONLY for this subscription
                    webCalSubscriptionRepository.update(subscription.copy(syncEnabled = newSyncEnabled))

                    Timber.tag("AccountDetailViewModel").d("WebCal sync enabled, triggering single-subscription sync")
                    try {
                        val accountId = currentAccountId ?: return@withContext
                        // Reuse batch API with one ID to sync only this WebCal
                        syncManager.syncBatch(
                            accountId = accountId,
                            webCalIds = listOf(subscription.id)
                        )
                    } catch (e: Exception) {
                        Timber.tag("AccountDetailViewModel").e(e, "Failed to trigger WebCal single sync")
                    }
                    return@withContext
                }
                
                // Fallback: simple DB update
                webCalSubscriptionRepository.update(subscription.copy(syncEnabled = newSyncEnabled))
            }
        }
    }
    
    /**
     * Refresh collections (calendars and address books) from the server.
     * This discovers new calendars and webcal subscriptions from the CalDAV server
     * and updates the local database.
     */
    fun refreshCollections() {
        viewModelScope.launch {
            val account = _account.value
            if (account == null) {
                return@launch
            }
            
            _isRefreshingCollections.value = true
            
            withContext(Dispatchers.IO) {
            
            try {
                Timber.tag("AccountDetailViewModel").d("Refreshing collections for account: %s", account.accountName)
                
                // Get password from credential store
                val password = credentialStore.getPassword(account.id)
                if (password == null) {
                    Timber.tag("AccountDetailViewModel").e("No password found for account %s", account.id)
                    return@withContext
                }
                
                // Construct proper CalDAV endpoint URL for Nextcloud
                // The account serverUrl is like "https://ncl-test.it-baer.net"
                // but CalDAV endpoint needs "/remote.php/dav"
                val caldavBaseUrl = "${account.serverUrl.trimEnd('/')}/remote.php/dav"
                
                Timber.tag("AccountDetailViewModel").d("Using CalDAV base URL: %s", caldavBaseUrl)
                
                // Discover principal and calendar-home-set
                val principalInfo = caldavPrincipalDiscovery.discoverPrincipal(
                    baseUrl = caldavBaseUrl,
                    username = account.username,
                    password = password
                )
                
                Timber.tag("AccountDetailViewModel").d("Calendar home-set: %s", principalInfo.calendarHomeSet)
                
                // Discover calendars and webcal subscriptions from server
                val discoveredCalendars = caldavPrincipalDiscovery.discoverCalendars(
                    calendarHomeSetUrl = principalInfo.calendarHomeSet,
                    username = account.username,
                    password = password
                )
                
                Timber.tag("AccountDetailViewModel").d("Discovered %s calendars/subscriptions from server", discoveredCalendars.size)
                
                // Track discovered identifiers to prune stale local entries afterwards
                val discoveredCalendarUrls = mutableSetOf<String>()
                val discoveredWebCalUrls = mutableSetOf<String>()

                // Process discovered calendars and webcal subscriptions
                discoveredCalendars.forEach { calendarInfo ->
                    if (calendarInfo.source != null) {
                        // This is a webcal subscription
                        discoveredWebCalUrls += calendarInfo.source
                        val existing = webCalSubscriptionRepository.getByUrl(calendarInfo.source)
                        if (existing == null) {
                            // New subscription - insert it
                            val subscription = WebCalSubscription(
                                id = 0,
                                accountId = account.id,
                                subscriptionUrl = calendarInfo.source,
                                displayName = calendarInfo.displayName,
                                description = calendarInfo.description,
                                color = parseColor(calendarInfo.color) ?: 0xFF2196F3.toInt(), // Default blue
                                syncEnabled = true,
                                visible = true
                            )
                            webCalSubscriptionRepository.insert(subscription)
                            Timber.tag("AccountDetailViewModel").d("Inserted new webcal: %s", calendarInfo.displayName)
                        } else {
                            // Update existing subscription
                            val updated = existing.copy(
                                displayName = calendarInfo.displayName,
                                description = calendarInfo.description,
                                color = parseColor(calendarInfo.color) ?: existing.color
                            )
                            webCalSubscriptionRepository.update(updated)
                            Timber.tag("AccountDetailViewModel").d("Updated webcal: %s", calendarInfo.displayName)
                        }
                    } else {
                        // This is a regular calendar
                        discoveredCalendarUrls += calendarInfo.url
                        val existing = calendarRepository.getByUrl(calendarInfo.url)
                        if (existing == null) {
                            // New calendar - insert it
                            val calendar = Calendar(
                                id = 0,
                                accountId = account.id,
                                calendarUrl = calendarInfo.url,
                                displayName = calendarInfo.displayName,
                                description = calendarInfo.description,
                                color = parseColor(calendarInfo.color) ?: 0xFF2196F3.toInt(), // Default blue
                                syncEnabled = true,
                                visible = true,
                                owner = calendarInfo.owner,
                                privWriteContent = calendarInfo.privWriteContent,
                                privUnbind = calendarInfo.privUnbind,
                                supportsVTODO = calendarInfo.supportsVTODO,
                                supportsVJOURNAL = calendarInfo.supportsVJOURNAL,
                                source = calendarInfo.source
                            )
                            calendarRepository.insert(calendar)
                            Timber.tag("AccountDetailViewModel").d("Inserted new calendar: %s", calendarInfo.displayName)
                        } else {
                            // Update existing calendar
                            val updated = existing.copy(
                                displayName = calendarInfo.displayName,
                                description = calendarInfo.description,
                                color = parseColor(calendarInfo.color) ?: existing.color,
                                owner = calendarInfo.owner ?: existing.owner,
                                privWriteContent = calendarInfo.privWriteContent,
                                privUnbind = calendarInfo.privUnbind,
                                supportsVTODO = calendarInfo.supportsVTODO,
                                supportsVJOURNAL = calendarInfo.supportsVJOURNAL,
                                source = calendarInfo.source
                            )
                            calendarRepository.update(updated)
                            Timber.tag("AccountDetailViewModel").d("Updated calendar: %s", calendarInfo.displayName)
                        }
                    }
                }

                // PRUNING: Remove local calendars that no longer exist on the server for this account
                try {
                    val locals = calendarRepository.getByAccountId(account.id)
                    val toRemove = locals.filter { local ->
                        // Only prune CalDAV calendars (not WebCal mirrored as calendars)
                        local.source == null && !discoveredCalendarUrls.contains(local.calendarUrl)
                    }
                    if (toRemove.isNotEmpty()) {
                        Timber.tag("AccountDetailViewModel").d("Pruning %s calendars no longer present on server", toRemove.size)
                    }
                    toRemove.forEach { localCal ->
                        // Delete from Android Calendar Provider first
                        try {
                            if (localCal.androidCalendarId != null) {
                                calendarContractSync.deleteCalendarFromProvider(
                                    androidCalendarId = localCal.androidCalendarId,
                                    accountName = account.accountName
                                )
                            }
                        } catch (e: Exception) {
                            Timber.tag("AccountDetailViewModel").w(e, "Failed provider cleanup for pruned calendar %s", localCal.displayName)
                        }
                        // Then remove from local database (events will cascade)
                        try {
                            calendarRepository.delete(localCal)
                            Timber.tag("AccountDetailViewModel").d("Pruned calendar '%s' (%s)", localCal.displayName, localCal.calendarUrl)
                        } catch (e: Exception) {
                            Timber.tag("AccountDetailViewModel").e(e, "Failed to delete pruned calendar %s", localCal.displayName)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Error while pruning removed calendars")
                }

                // PRUNING (WebCal): Remove local WebCal subscriptions that no longer exist on the server
                try {
                    val localSubs = webCalSubscriptionRepository.getByAccountId(account.id)
                    val subsToRemove = localSubs.filter { sub ->
                        !discoveredWebCalUrls.contains(sub.subscriptionUrl)
                    }
                    if (subsToRemove.isNotEmpty()) {
                        Timber.tag("AccountDetailViewModel").d("Pruning %s WebCal subscriptions no longer present on server", subsToRemove.size)
                    }
                    subsToRemove.forEach { sub ->
                        // Delete provider mirror if present
                        try {
                            if (sub.androidCalendarId != null) {
                                calendarContractSync.deleteCalendarFromProvider(
                                    androidCalendarId = sub.androidCalendarId,
                                    accountName = account.accountName
                                )
                            }
                        } catch (e: Exception) {
                            Timber.tag("AccountDetailViewModel").w(e, "Failed provider cleanup for pruned WebCal %s", sub.displayName)
                        }
                        // Remove from local database
                        try {
                            webCalSubscriptionRepository.delete(sub)
                            Timber.tag("AccountDetailViewModel").d("Pruned WebCal '%s' (%s)", sub.displayName, sub.subscriptionUrl)
                        } catch (e: Exception) {
                            Timber.tag("AccountDetailViewModel").e(e, "Failed to delete pruned WebCal %s", sub.displayName)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Error while pruning removed WebCal subscriptions")
                }
                
                Timber.tag("AccountDetailViewModel").d("Collection refresh complete")
                try {
                    webCalSyncService.syncAccountSubscriptions(account.id, force = true)
                    Timber.tag("AccountDetailViewModel").d("Triggered WebCal sync after collection refresh")
                } catch (e: Exception) {
                    Timber.tag("AccountDetailViewModel").e(e, "Failed to sync WebCal subscriptions after refresh")
                }
                
            } catch (e: Exception) {
                Timber.tag("AccountDetailViewModel").e(e, "Failed to refresh collections")
                e.printStackTrace()
            } finally {
                _isRefreshingCollections.value = false
            }
            }
        }
    }
    
    /**
     * Parse color from hex string to integer.
     * Handles formats like "#RRGGBB" or "#RRGGBBAA"
     */
    private fun parseColor(colorString: String?): Int? {
        if (colorString == null) return null
        return try {
            val hex = colorString.removePrefix("#")
            when (hex.length) {
                6 -> {
                    // #RRGGBB
                    0xFF000000.toInt() or hex.toInt(16)
                }
                8 -> {
                    // #RRGGBBAA -> #AARRGGBB (Android format)
                    val rgb = hex.substring(0, 6).toInt(16)
                    val alpha = hex.substring(6, 8).toInt(16)
                    (alpha shl 24) or rgb
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.tag("AccountDetailViewModel").w(e, "Failed to parse color: %s", colorString)
            null
        }
    }
    
    /**
     * Delete events older than the specified number of days.
     * Called when skipEventsDays setting changes.
     */
    fun deleteOldEvents(accountId: Long, daysThreshold: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Timber.d("Deleting events older than $daysThreshold days for account $accountId")
                    val deletedCount = calendarContractSync.deleteOldEvents(accountId, daysThreshold)
                    Timber.d("Deleted $deletedCount old events")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete old events")
                }
            }
        }
    }
    
    /**
     * Export account settings and collection metadata to JSON file.
     */
    suspend fun exportSettings(accountId: Long): com.davy.domain.usecase.ExportSettingsUseCase.Result {
        return exportSettingsUseCase(accountId)
    }
}
