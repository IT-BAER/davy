package com.davy.domain.usecase

import com.davy.domain.manager.BackupRestoreManager
import javax.inject.Inject

/**
 * Use case for restoring settings from a backup JSON string.
 */
class RestoreBackupUseCase @Inject constructor(
    private val backupRestoreManager: BackupRestoreManager
) {
    /**
     * Restore settings from a backup JSON string.
     * @param backupJson The backup JSON string content
     * @param overwriteExisting If true, existing settings will be overwritten
     */
    suspend operator fun invoke(
        backupJson: String,
        overwriteExisting: Boolean = false
    ): BackupRestoreManager.RestoreResult {
        return backupRestoreManager.restoreBackup(backupJson, overwriteExisting)
    }
}
