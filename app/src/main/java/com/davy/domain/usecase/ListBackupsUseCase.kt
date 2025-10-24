package com.davy.domain.usecase

import com.davy.domain.manager.BackupRestoreManager
import javax.inject.Inject

/**
 * Use case for listing all available backup files.
 */
class ListBackupsUseCase @Inject constructor(
    private val backupRestoreManager: BackupRestoreManager
) {
    operator fun invoke(): List<BackupRestoreManager.BackupInfo> {
        return backupRestoreManager.listBackups()
    }
}
