package com.davy.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davy.domain.usecase.CreateBackupUseCase
import com.davy.domain.usecase.RestoreBackupUseCase
import com.davy.domain.usecase.ListBackupsUseCase
import com.davy.domain.manager.BackupRestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for managing app-wide backup and restore operations.
 */
@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    application: Application,
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase
) : AndroidViewModel(application) {
    
    /**
     * Create a full backup of all accounts and app settings.
     * Returns the backup JSON string to be saved by the caller.
     */
    suspend fun createFullBackup(): BackupRestoreManager.BackupResult {
        return withContext(Dispatchers.IO) {
            createBackupUseCase(accountId = null) // null = full backup
        }
    }
    
    /**
     * Create a backup of a specific account.
     * Returns the backup JSON string to be saved by the caller.
     */
    suspend fun createAccountBackup(accountId: Long): BackupRestoreManager.BackupResult {
        return withContext(Dispatchers.IO) {
            createBackupUseCase(accountId)
        }
    }
    
    /**
     * Restore settings from a backup JSON string.
     */
    suspend fun restoreBackup(backupJson: String, overwriteExisting: Boolean): BackupRestoreManager.RestoreResult {
        return withContext(Dispatchers.IO) {
            restoreBackupUseCase(backupJson, overwriteExisting)
        }
    }
}
