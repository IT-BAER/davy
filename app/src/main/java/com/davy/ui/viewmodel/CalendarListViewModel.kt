package com.davy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.repository.CalendarRepository
import com.davy.domain.model.Calendar
import com.davy.domain.usecase.GetAllAccountsUseCase
import com.davy.domain.usecase.GetCalendarsUseCase
import com.davy.domain.usecase.UpdateCalendarUseCase
import com.davy.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for calendar list screen.
 * Manages calendar list for selected account.
 */
@HiltViewModel
class CalendarListViewModel @Inject constructor(
    private val getCalendarsUseCase: GetCalendarsUseCase,
    private val getAllAccountsUseCase: GetAllAccountsUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val syncManager: SyncManager,
    private val calendarRepository: CalendarRepository,
    private val calendarContractSync: com.davy.sync.calendar.CalendarContractSync,
    private val accountRepository: com.davy.data.repository.AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarListUiState>(CalendarListUiState.Loading)
    val uiState: StateFlow<CalendarListUiState> = _uiState.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadFirstAccount()
        observeSelectedAccountSync()
    }

    /**
     * Load the first available account.
     */
    private fun loadFirstAccount() {
        viewModelScope.launch {
            getAllAccountsUseCase()
                .catch { error ->
                    _uiState.value = CalendarListUiState.Error(
                        error.message ?: "Failed to load accounts"
                    )
                }
                .collect { accounts ->
                    if (accounts.isNotEmpty()) {
                        _selectedAccountId.value = accounts.first().id
                        loadCalendars(accounts.first().id)
                    } else {
                        _uiState.value = CalendarListUiState.NoAccount
                    }
                }
        }
    }

    /**
     * Load calendars for selected account.
     */
    fun loadCalendars(accountId: Long) {
        viewModelScope.launch {
            _selectedAccountId.value = accountId
            _uiState.value = CalendarListUiState.Loading

            getCalendarsUseCase(accountId)
                .catch { error ->
                    _uiState.value = CalendarListUiState.Error(
                        error.message ?: "Failed to load calendars"
                    )
                }
                .collect { calendars ->
                    if (calendars.isEmpty()) {
                        _uiState.value = CalendarListUiState.Empty
                    } else {
                        _uiState.value = CalendarListUiState.Success(calendars)
                    }
                }
        }
    }

    /**
     * Refresh calendar list.
     */
    fun refresh() {
        _selectedAccountId.value?.let { accountId ->
            loadCalendars(accountId)
        } ?: loadFirstAccount()
    }

    private fun observeSelectedAccountSync() {
        viewModelScope.launch {
            selectedAccountId
                .flatMapLatest { id ->
                    if (id != null) syncManager.observeSyncState(id) else flowOf(false)
                }
                .collect { syncing -> _isSyncing.value = syncing }
        }
    }
    
    /**
     * Sync calendars only (CalDAV service) for the current account.
     */
    fun syncCalendarsOnly() {
        viewModelScope.launch {
            _selectedAccountId.value?.let { accountId ->
                try {
                    syncManager.syncNow(accountId, com.davy.sync.SyncWorker.SYNC_TYPE_CALENDAR)
                    Timber.d("Sync requested for CalDAV service only (account: $accountId)")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync calendars")
                }
            }
        }
    }
    
    /**
     * Toggle sync enabled/disabled for a calendar.
     * 
     * Clean approach:
     * - When disabled: Removes calendar and events from Calendar Provider
     * - When enabled: Re-adds calendar and syncs from server
     * - Server is the source of truth, no data loss
     */
    fun toggleSync(calendar: Calendar, enabled: Boolean) {
        viewModelScope.launch {
            Timber.tag("CalendarListViewModel").d("===== toggleSync called for ${calendar.displayName}: enabled=$enabled =====")
            try {
                // Update database - UpdateCalendarUseCase handles provider removal/re-add
                val updated = calendar.copy(
                    syncEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                
                Timber.tag("CalendarListViewModel").d("Toggled sync for ${calendar.displayName}: $enabled")
            } catch (e: Exception) {
                Timber.tag("CalendarListViewModel").e(e, "Failed to toggle sync")
            }
        }
    }
    
    /**
     * Toggle visibility for a calendar.
     * 
     * When visible, the calendar appears in the calendar list and can be displayed.
     * When hidden, the calendar is hidden from views but data remains.
     * 
     * Note: Visibility is separate from sync.
     */
    fun toggleVisibility(calendar: Calendar, visible: Boolean) {
        viewModelScope.launch {
            try {
                // Update database
                val updated = calendar.copy(
                    visible = visible,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                
                // Update Calendar Provider visibility (if calendar exists in provider)
                if (calendar.syncEnabled && calendar.androidCalendarId != null) {
                    val account = accountRepository.getById(calendar.accountId)
                    if (account != null) {
                        calendarContractSync.updateCalendarVisibility(
                            androidCalendarId = calendar.androidCalendarId,
                            accountName = account.accountName,
                            visible = visible,
                            syncEnabled = calendar.syncEnabled
                        )
                        Timber.d("Calendar visibility updated in provider: $visible")
                    }
                }
                
                Timber.d("Toggled visibility for ${calendar.displayName}: $visible")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle visibility")
            }
        }
    }
    
    /**
     * Trigger manual sync for a specific calendar.
     */
    fun syncNow(calendar: Calendar) {
        viewModelScope.launch {
            try {
                Timber.d("========================================")
                Timber.d("MANUAL SYNC TRIGGERED")
                Timber.d("Calendar: ${calendar.displayName} (ID: ${calendar.id})")
                Timber.d("Account ID: ${calendar.accountId}")
                Timber.d("========================================")
                
                // Trigger item-scoped calendar sync for this specific calendar
                syncManager.syncCalendar(calendar.accountId, calendar.id)
                
                Timber.d("✓ Manual sync request enqueued")
            } catch (e: Exception) {
                Timber.e(e, "✗ Failed to trigger manual sync for ${calendar.displayName}")
            }
        }
    }
    
    /**
     * Reset sync token for a calendar and trigger full sync.
     * This forces a complete re-download of all events from the server.
     * Useful for debugging sync issues or when calendar appears empty despite server having events.
     */
    fun resetSyncAndSync(calendar: Calendar) {
        viewModelScope.launch {
            try {
                Timber.d("========================================")
                Timber.d("RESET SYNC TOKEN + MANUAL SYNC TRIGGERED")
                Timber.d("Calendar: ${calendar.displayName} (ID: ${calendar.id})")
                Timber.d("Account ID: ${calendar.accountId}")
                Timber.d("========================================")
                
                // Reset sync token to force full PROPFIND sync
                calendarRepository.resetSyncToken(calendar.id)
                Timber.d("✓ Sync token reset - will trigger full sync")
                
                // Trigger immediate calendar sync for the account
                syncManager.syncNow(calendar.accountId, SyncManager.SYNC_TYPE_CALENDAR)
                
                Timber.d("✓ Manual sync request enqueued")
            } catch (e: Exception) {
                Timber.e(e, "✗ Failed to reset sync token and trigger sync for ${calendar.displayName}")
            }
        }
    }
    
    /**
     * Change calendar color.
     */
    fun changeColor(calendar: Calendar, color: Int) {
        viewModelScope.launch {
            try {
                Timber.d("Changing color for %s", calendar.displayName)
                val updated = calendar.copy(
                    color = color,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                Timber.d("Successfully changed color for %s", calendar.displayName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to change color")
            }
        }
    }
    
    /**
     * Sync all calendars for an account.
     */
    fun syncAccount(accountId: Long) {
        viewModelScope.launch {
            try {
                Timber.d("Syncing all calendars for account $accountId")
                syncManager.syncNow(accountId, SyncManager.SYNC_TYPE_CALENDAR)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync calendars for account $accountId")
            }
        }
    }
}

/**
 * UI state for calendar list screen.
 */
sealed class CalendarListUiState {
    object Loading : CalendarListUiState()
    object Empty : CalendarListUiState()
    object NoAccount : CalendarListUiState()
    data class Success(val calendars: List<Calendar>) : CalendarListUiState()
    data class Error(val message: String) : CalendarListUiState()
}

