package com.davy.domain.usecase

import com.davy.domain.manager.BackupRestoreManager
import javax.inject.Inject

/**
 * Use case for creating a backup of app settings and account configurations.
 */
class CreateBackupUseCase @Inject constructor(
    private val backupRestoreManager: BackupRestoreManager
) {
    /**
     * Create a backup file.
     * @param accountId If provided, backup only this account. If null, backup all accounts and app settings.
     */
    suspend operator fun invoke(accountId: Long? = null): BackupRestoreManager.BackupResult {
        return backupRestoreManager.createBackup(accountId)
    }
}
