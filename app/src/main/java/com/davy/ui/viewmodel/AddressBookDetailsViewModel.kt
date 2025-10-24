package com.davy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.AccountRepository
import com.davy.data.local.CredentialStore
import com.davy.domain.model.AddressBook
import com.davy.domain.usecase.UpdateAddressBookUseCase
import com.davy.sync.SyncManager
import com.davy.sync.account.AndroidAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddressBookDetailsViewModel @Inject constructor(
    private val addressBookRepository: AddressBookRepository,
    private val accountRepository: AccountRepository,
    private val updateAddressBookUseCase: UpdateAddressBookUseCase,
    private val syncManager: SyncManager,
    private val credentialStore: CredentialStore,
    private val androidAccountManager: AndroidAccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddressBookDetailsUiState>(AddressBookDetailsUiState.Loading)
    val uiState: StateFlow<AddressBookDetailsUiState> = _uiState.asStateFlow()

    fun loadAddressBook(addressBookId: Long) {
        viewModelScope.launch {
            try {
                val addressBook = addressBookRepository.getById(addressBookId)
                if (addressBook != null) {
                    _uiState.value = AddressBookDetailsUiState.Success(addressBook)
                } else {
                    _uiState.value = AddressBookDetailsUiState.Error("Address book not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load address book")
                _uiState.value = AddressBookDetailsUiState.Error(
                    e.message ?: "Failed to load address book"
                )
            }
        }
    }

    fun toggleSync(addressBook: AddressBook, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = addressBook.copy(
                    syncEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                updateAddressBookUseCase(updated)
                _uiState.value = AddressBookDetailsUiState.Success(updated)
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle sync")
            }
        }
    }

    fun syncNow(addressBook: AddressBook) {
        viewModelScope.launch {
            try {
                Timber.d("Manual sync triggered for address book: ${addressBook.displayName}")
                syncManager.syncNow(addressBook.accountId, SyncManager.SYNC_TYPE_CONTACTS)
            } catch (e: Exception) {
                Timber.e(e, "Failed to trigger sync")
            }
        }
    }

    fun updateSettings(
        addressBook: AddressBook,
        wifiOnlySync: Boolean,
        syncIntervalMinutes: Int?
    ) {
        viewModelScope.launch {
            try {
                val updated = addressBook.copy(
                    wifiOnlySync = wifiOnlySync,
                    syncIntervalMinutes = syncIntervalMinutes,
                    updatedAt = System.currentTimeMillis()
                )
                updateAddressBookUseCase(updated)
                _uiState.value = AddressBookDetailsUiState.Success(updated)
                Timber.d("Address book settings updated")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update settings")
            }
        }
    }

    fun updateReadOnly(addressBook: AddressBook, forceReadOnly: Boolean) {
        viewModelScope.launch {
            try {
                val updated = addressBook.copy(
                    forceReadOnly = forceReadOnly,
                    updatedAt = System.currentTimeMillis()
                )
                // Persist via use case to keep consistency
                updateAddressBookUseCase(updated)
                _uiState.value = AddressBookDetailsUiState.Success(updated)
                Timber.d("Address book read-only updated: $forceReadOnly")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update read-only")
            }
        }
    }

    fun deleteAddressBook(addressBook: AddressBook) {
        viewModelScope.launch {
            try {
                // Guard: prevent deleting read-only address books
                if (addressBook.isReadOnly()) {
                    Timber.w("Attempted to delete read-only address book: ${addressBook.displayName}")
                    return@launch
                }

                val account = accountRepository.getById(addressBook.accountId)
                val password = account?.id?.let { credentialStore.getPassword(it) }

                // Try server deletion first (best-effort)
                if (account != null && addressBook.url.isNotBlank() && password != null) {
                    try {
                        val request = okhttp3.Request.Builder()
                            .url(addressBook.url)
                            .delete()
                            .header("Authorization", okhttp3.Credentials.basic(account.username, password))
                            .build()
                        val client = okhttp3.OkHttpClient()
                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful && response.code !in listOf(204, 404, 410)) {
                            Timber.w("Failed to delete address book on server: ${response.code} - ${response.message}")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Exception deleting address book on server (continuing with local removal)")
                    }
                }

                // Remove the Android address book account (provider cleanup)
                if (account != null) {
                    try {
                        androidAccountManager.removeAddressBookAccountById(account.accountName, addressBook.id)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to remove Android address book account")
                    }
                }

                // Delete from local database
                addressBookRepository.delete(addressBook)

                Timber.d("Deleted address book: ${addressBook.displayName}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete address book")
            }
        }
    }
}

sealed class AddressBookDetailsUiState {
    object Loading : AddressBookDetailsUiState()
    data class Success(val addressBook: AddressBook) : AddressBookDetailsUiState()
    data class Error(val message: String) : AddressBookDetailsUiState()
}
