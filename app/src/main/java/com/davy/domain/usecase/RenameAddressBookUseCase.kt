package com.davy.domain.usecase

import com.davy.data.local.CredentialStore
import com.davy.data.remote.caldav.CalDAVClient
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.AddressBookRepository
import javax.inject.Inject

/**
 * Use case for renaming an address book.
 * Updates displayname on server and in local database.
 */
class RenameAddressBookUseCase @Inject constructor(
    private val addressBookRepository: AddressBookRepository,
    private val accountRepository: AccountRepository,
    private val caldavClient: CalDAVClient,
    private val credentialStore: CredentialStore
) {
    
    sealed class Result {
        data class Success(val newDisplayName: String) : Result()
        data class Error(val message: String) : Result()
    }
    
    suspend operator fun invoke(addressBookId: Long, newDisplayName: String): Result {
        val addressBook = addressBookRepository.getById(addressBookId)
            ?: return Result.Error("Address book not found")
        
        val account = accountRepository.getById(addressBook.accountId)
            ?: return Result.Error("Account not found")
        
        val password = credentialStore.getPassword(account.id)
            ?: return Result.Error("Password not found in credential store")
        
        // Update on server via PROPPATCH
        val response = caldavClient.proppatchCollection(
            collectionUrl = addressBook.url,
            username = account.username,
            password = password,
            propertyName = "displayname",
            propertyValue = newDisplayName
        )
        
        if (!response.isSuccessful) {
            return Result.Error("Failed to rename address book on server: ${response.error ?: "HTTP ${response.statusCode}"}")
        }
        
        // Update in local database
        addressBookRepository.updateDisplayName(addressBookId, newDisplayName)
        
        return Result.Success(newDisplayName)
    }
}
