package com.davy.domain.usecase

import com.davy.data.local.CredentialStore
import com.davy.data.repository.AccountRepository
import com.davy.domain.model.Account
import com.davy.sync.account.AndroidAccountManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case to add a new account.
 */
class AddAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val androidAccountManager: AndroidAccountManager,
    private val credentialStore: CredentialStore
) {
    suspend operator fun invoke(account: Account): Long {
        // Insert account into DAVy's database
        val accountId = accountRepository.insert(account)
        
        // Get password from credential store
        val password = credentialStore.getPassword(accountId)
        
        // Create Android account for sync framework
        val androidAccountCreated = androidAccountManager.createOrUpdateAccount(
            account.accountName,
            password
        )
        
        if (androidAccountCreated) {
            Timber.d("Created Android account for: ${account.accountName}")
            
            // Do NOT trigger sync automatically on account creation
            // Sync will happen based on:
            // 1. Sync interval settings (periodic background sync)
            // 2. User manual sync trigger
        } else {
            Timber.e("Failed to create Android account for: ${account.accountName}")
        }
        
        return accountId
    }
}

