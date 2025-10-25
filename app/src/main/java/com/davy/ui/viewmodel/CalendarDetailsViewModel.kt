package com.davy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.local.CredentialStore
import com.davy.data.remote.caldav.CalDAVClient
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.domain.model.Calendar
import com.davy.domain.usecase.UpdateCalendarUseCase
import com.davy.sync.SyncManager
import com.davy.sync.calendar.CalendarContractSync
import com.davy.ui.screens.CalendarDetailsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for calendar details screen.
 * Manages calendar settings and sync operations.
 */
@HiltViewModel
class CalendarDetailsViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val accountRepository: AccountRepository,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val syncManager: SyncManager,
    private val caldavClient: CalDAVClient,
    private val credentialStore: CredentialStore,
    private val calendarContractSync: CalendarContractSync
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarDetailsUiState>(CalendarDetailsUiState.Loading)
    val uiState: StateFlow<CalendarDetailsUiState> = _uiState.asStateFlow()

    /**
     * Load calendar by ID.
     */
    fun loadCalendar(calendarId: Long) {
        viewModelScope.launch {
            try {
                val calendar = calendarRepository.getById(calendarId)
                if (calendar != null) {
                    _uiState.value = CalendarDetailsUiState.Success(calendar)
                } else {
                    _uiState.value = CalendarDetailsUiState.Error("Calendar not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load calendar")
                _uiState.value = CalendarDetailsUiState.Error(
                    e.message ?: "Failed to load calendar"
                )
            }
        }
    }

    /**
     * Toggle sync enabled for calendar.
     */
    fun toggleSync(calendar: Calendar, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = calendar.copy(
                    syncEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                _uiState.value = CalendarDetailsUiState.Success(updated)
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle sync")
            }
        }
    }

    /**
     * Toggle visibility in Android Calendar app.
     */
    fun toggleVisibility(calendar: Calendar, visible: Boolean) {
        viewModelScope.launch {
            try {
                val updated = calendar.copy(
                    visible = visible,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                _uiState.value = CalendarDetailsUiState.Success(updated)
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle visibility")
            }
        }
    }

    /**
     * Trigger manual sync for this calendar.
     */
    fun syncNow(calendar: Calendar) {
        viewModelScope.launch {
            try {
                Timber.d("Manual sync triggered for calendar: ${calendar.displayName}")
                syncManager.syncNow(calendar.accountId, SyncManager.SYNC_TYPE_CALENDAR)
            } catch (e: Exception) {
                Timber.e(e, "Failed to trigger sync")
            }
        }
    }

    /**
     * Reset sync token and trigger full sync.
     */
    fun resetSyncAndSync(calendar: Calendar) {
        viewModelScope.launch {
            try {
                Timber.d("Reset sync token + manual sync for calendar: ${calendar.displayName}")
                
                // Reset sync token to force full PROPFIND sync
                calendarRepository.resetSyncToken(calendar.id)
                Timber.d("✓ Sync token reset - will trigger full sync")
                
                // Reload calendar to show updated state
                val updatedCalendar = calendarRepository.getById(calendar.id)
                if (updatedCalendar != null) {
                    _uiState.value = CalendarDetailsUiState.Success(updatedCalendar)
                }
                
                // Trigger immediate calendar sync
                syncManager.syncNow(calendar.accountId, SyncManager.SYNC_TYPE_CALENDAR)
                Timber.d("✓ Manual sync request enqueued")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset sync and trigger sync")
            }
        }
    }

    /**
     * Change calendar color.
     */
    fun changeColor(calendar: Calendar, color: Int) {
        viewModelScope.launch {
            try {
                val updated = calendar.copy(
                    color = color,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                _uiState.value = CalendarDetailsUiState.Success(updated)
            } catch (e: Exception) {
                Timber.e(e, "Failed to change color")
            }
        }
    }

    /**
     * Update calendar settings (WiFi-only, force read-only).
     */
    fun updateSettings(
        calendar: Calendar,
        wifiOnlySync: Boolean,
        forceReadOnly: Boolean
    ) {
        viewModelScope.launch {
            try {
                val updated = calendar.copy(
                    wifiOnlySync = wifiOnlySync,
                    forceReadOnly = forceReadOnly,
                    updatedAt = System.currentTimeMillis()
                )
                updateCalendarUseCase(updated)
                _uiState.value = CalendarDetailsUiState.Success(updated)
                Timber.d("Calendar settings updated")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update settings")
            }
        }
    }

    /**
     * Delete a single calendar from both server and device.
     * - Sends HTTP DELETE to CalDAV collection URL (if present and not demo account)
     * - Removes the calendar from Android Calendar Provider
     * - Deletes the calendar from local database
     */
    fun deleteCalendar(calendar: Calendar) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Guard: prevent deleting calendars that cannot be deleted
                    if (!calendar.canDelete()) {
                        Timber.w("Attempted to delete non-deletable calendar: ${calendar.displayName}")
                        return@withContext
                    }

                    // Load account and credentials
                    val account = accountRepository.getById(calendar.accountId) ?: run {
                        Timber.e("Account not found for calendar: ${calendar.displayName}")
                        return@withContext
                    }
                    
                    val isDemoAccount = account.serverUrl.equals("https://demo.local", ignoreCase = true)
                    val password = if (!isDemoAccount) {
                        credentialStore.getPassword(account.id) ?: run {
                            Timber.e("No password stored for account: ${account.accountName}")
                            return@withContext
                        }
                    } else null

                    // Delete from server (CalDAV) for non-demo accounts
                    if (!isDemoAccount && calendar.calendarUrl.isNotBlank() && password != null) {
                        Timber.d("Deleting calendar on server: ${calendar.calendarUrl}")
                        val response = caldavClient.deleteCalendar(
                            calendarUrl = calendar.calendarUrl,
                            username = account.username,
                            password = password
                        )
                        if (!response.isSuccessful) {
                            Timber.e("Failed to delete calendar on server: ${response.statusCode} - ${response.error}")
                            // Continue with local deletion even if server deletion failed (404/410 acceptable)
                        } else {
                            Timber.d("Calendar deleted on server successfully")
                        }
                    } else if (isDemoAccount) {
                        Timber.d("Demo account: skipping server deletion for calendar: ${calendar.displayName}")
                    }

                    // Delete from Android Calendar Provider
                    if (calendar.androidCalendarId != null) {
                        Timber.d("Deleting calendar from Calendar Provider: ${calendar.androidCalendarId}")
                        val providerDeleted = calendarContractSync.deleteCalendarFromProvider(
                            androidCalendarId = calendar.androidCalendarId,
                            accountName = account.accountName
                        )
                        if (providerDeleted) {
                            Timber.d("Calendar deleted from Calendar Provider successfully")
                        } else {
                            Timber.e("Failed to delete calendar from Calendar Provider")
                        }
                    }

                    // Delete from local database
                    calendarRepository.delete(calendar)
                    Timber.d("Calendar deleted from local database")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete calendar: ${calendar.displayName}")
                }
            }
        }
    }
}

