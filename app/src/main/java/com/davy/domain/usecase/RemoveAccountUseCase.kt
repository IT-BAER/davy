package com.davy.domain.usecase

import com.davy.data.repository.AccountRepository
import com.davy.domain.model.Account
import com.davy.sync.account.AndroidAccountManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case to remove an account.
 * 
 * This removes the account from both DAVy's database and Android's AccountManager.
 * When the Android account is removed, all associated synced data (contacts, calendars) 
 * are automatically cleaned up by the system.
 */
class RemoveAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val androidAccountManager: AndroidAccountManager
) {
    suspend operator fun invoke(account: Account) {
        try {
            // First remove all address book accounts (reference implementation architecture)
            // Each address book has its own Android account that must be removed
            val addressBookAccountsRemoved = androidAccountManager.removeAddressBookAccounts(account.accountName)
            
            if (addressBookAccountsRemoved) {
                Timber.d("Successfully removed address book accounts for: ${account.accountName}")
            } else {
                Timber.w("Failed to remove some address book accounts for: ${account.accountName}")
            }
            
            // Then remove main Android account
            // This will clean up any remaining synced data (calendars, etc.)
            val androidRemoved = androidAccountManager.removeAccount(account.accountName)
            
            if (androidRemoved) {
                Timber.d("Successfully removed Android account: ${account.accountName}")
            } else {
                Timber.w("Failed to remove Android account: ${account.accountName}")
            }
            
            // Finally remove from DAVy's database
            accountRepository.delete(account)
            Timber.d("Successfully removed DAVy account: ${account.accountName}")
            
        } catch (e: Exception) {
            Timber.e(e, "Error removing account: ${account.accountName}")
            throw e
        }
    }
}
