package com.davy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.domain.model.Account
import com.davy.domain.usecase.AddAccountUseCase
import com.davy.domain.usecase.GetAllAccountsUseCase
import com.davy.domain.usecase.RemoveAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for account list screen.
 * Manages account list, adding, and removing accounts.
 */
@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val getAllAccountsUseCase: GetAllAccountsUseCase,
    private val addAccountUseCase: AddAccountUseCase,
    private val removeAccountUseCase: RemoveAccountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountListUiState>(AccountListUiState.Loading)
    val uiState: StateFlow<AccountListUiState> = _uiState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadAccounts()
    }

    /**
     * Loads all accounts.
     */
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.value = AccountListUiState.Loading

            getAllAccountsUseCase()
                .catch { error ->
                    _uiState.value = AccountListUiState.Error(
                        error.message ?: "Failed to load accounts"
                    )
                }
                .collect { accounts ->
                    if (accounts.isEmpty()) {
                        _uiState.value = AccountListUiState.Empty
                    } else {
                        _uiState.value = AccountListUiState.Success(accounts)
                    }
                }
        }
    }

    /**
     * Removes an account.
     *
     * @param account Account to remove
     */
    fun removeAccount(account: Account) {
        viewModelScope.launch {
            try {
                removeAccountUseCase(account)
                // Reload accounts happens automatically via Flow
            } catch (e: Exception) {
                _uiState.value = AccountListUiState.Error(
                    e.message ?: "Failed to remove account"
                )
            }
        }
    }

    /**
     * Refreshes account list.
     */
    fun refresh() {
        loadAccounts()
    }

    /**
     * Triggers sync for all accounts.
     */
    fun syncAll() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // Sync logic will be handled by SyncManager
                // This just updates the UI state
                kotlinx.coroutines.delay(2000) // Minimum animation duration
            } finally {
                _isSyncing.value = false
            }
        }
    }
}

/**
 * UI state for account list screen.
 */
sealed class AccountListUiState {
    object Loading : AccountListUiState()
    object Empty : AccountListUiState()
    data class Success(val accounts: List<Account>) : AccountListUiState()
    data class Error(val message: String) : AccountListUiState()
}
