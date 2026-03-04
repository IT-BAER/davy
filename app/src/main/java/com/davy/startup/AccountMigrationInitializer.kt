package com.davy.startup

import android.content.Context
import androidx.startup.Initializer
import com.davy.data.local.CredentialStore
import com.davy.data.repository.AccountRepository
import com.davy.sync.account.AndroidAccountManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Initializes account migration in the background using App Startup library.
 * This defers expensive migration work away from Application.onCreate().
 */
class AccountMigrationInitializer : Initializer<Unit> {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccountMigrationDependencies {
        fun accountRepository(): AccountRepository
        fun androidAccountManager(): AndroidAccountManager
        fun credentialStore(): CredentialStore
    }

    override fun create(context: Context) {
        val appContext = context.applicationContext
        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                appContext,
                AccountMigrationDependencies::class.java
            )
        } catch (e: IllegalStateException) {
            Timber.w(e, "AccountMigrationInitializer: Skipping — Hilt component not ready (test environment)")
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val accountRepository = entryPoint.accountRepository()
                val androidAccountManager = entryPoint.androidAccountManager()
                val credentialStore = entryPoint.credentialStore()

                val accounts = accountRepository.getAll()
                val existingAndroidAccounts = androidAccountManager.getAllAccounts()
                    .map { it.name }
                    .toSet()

                Timber.d("AccountMigrationInitializer: Migrating ${accounts.size} existing accounts")

                for (account in accounts) {
                    if (existingAndroidAccounts.contains(account.accountName)) {
                        continue
                    }

                    try {
                        val password = credentialStore.getPassword(account.id)
                        if (password.isNullOrEmpty()) {
                            Timber.w("No stored password for account ${account.accountName}")
                            continue
                        }

                        androidAccountManager.createOrUpdateAccount(
                            account.accountName,
                            password
                        )

                        Timber.d("Successfully migrated Android account: ${account.accountName}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to migrate account: ${account.accountName}")
                    }
                }

                Timber.d("AccountMigrationInitializer: Migration completed")
                
                // Clean up orphaned Android accounts (e.g., after clearing app data)
                // Android AccountManager accounts persist in the system even when app data is cleared,
                // so we need to remove any that no longer have a corresponding entry in the Room database.
                val dbAccountNames = accounts.map { it.accountName }.toSet()
                val orphanedAccounts = existingAndroidAccounts.filter { it !in dbAccountNames }
                
                if (orphanedAccounts.isNotEmpty()) {
                    Timber.d("AccountMigrationInitializer: Removing ${orphanedAccounts.size} orphaned Android account(s)")
                    for (orphanedName in orphanedAccounts) {
                        try {
                            // Remove address book accounts first
                            androidAccountManager.removeAddressBookAccounts(orphanedName)
                            // Remove main account
                            val removed = androidAccountManager.removeAccount(orphanedName)
                            if (removed) {
                                Timber.d("Removed orphaned Android account: $orphanedName")
                            } else {
                                Timber.w("Failed to remove orphaned Android account: $orphanedName")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to remove orphaned account: $orphanedName")
                        }
                    }
                }
                
                // Enable content-triggered sync for all existing accounts
                // This allows immediate sync when users edit contacts/calendars in native Android apps
                Timber.d("========================================")
                Timber.d("Enabling content-triggered sync for all accounts...")
                Timber.d("========================================")
                androidAccountManager.enableContentTriggeredSyncForAllAccounts()
                androidAccountManager.logSyncStatusForAllAccounts()
                
            } catch (e: Exception) {
                Timber.e(e, "AccountMigrationInitializer: Failed")
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
