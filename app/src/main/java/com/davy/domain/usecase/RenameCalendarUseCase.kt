package com.davy.domain.usecase

import com.davy.data.local.CredentialStore
import com.davy.data.remote.caldav.CalDAVClient
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import javax.inject.Inject

/**
 * Use case for renaming a calendar.
 * Updates displayname on server and in local database.
 */
class RenameCalendarUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val accountRepository: AccountRepository,
    private val caldavClient: CalDAVClient,
    private val credentialStore: CredentialStore
) {
    
    sealed class Result {
        data class Success(val newDisplayName: String) : Result()
        data class Error(val message: String) : Result()
    }
    
    suspend operator fun invoke(calendarId: Long, newDisplayName: String): Result {
        val calendar = calendarRepository.getById(calendarId)
            ?: return Result.Error("Calendar not found")
        
        val account = accountRepository.getById(calendar.accountId)
            ?: return Result.Error("Account not found")
        
        val password = credentialStore.getPassword(account.id)
            ?: return Result.Error("Password not found in credential store")
        
        // Update on server via PROPPATCH
        val response = caldavClient.proppatchCollection(
            collectionUrl = calendar.calendarUrl,
            username = account.username,
            password = password,
            propertyName = "displayname",
            propertyValue = newDisplayName
        )
        
        if (!response.isSuccessful) {
            return Result.Error("Failed to rename calendar on server: ${response.error ?: "HTTP ${response.statusCode}"}")
        }
        
        // Update in local database
        calendarRepository.updateDisplayName(calendarId, newDisplayName)
        
        return Result.Success(newDisplayName)
    }
}
