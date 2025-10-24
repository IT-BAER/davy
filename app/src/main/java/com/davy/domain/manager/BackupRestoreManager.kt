package com.davy.domain.manager

import android.content.Context
import android.content.SharedPreferences
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.AddressBookRepository
import com.davy.domain.model.Account
import com.davy.domain.model.AuthType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages backup and restore operations for app settings and account configurations.
 * Backs up ALL settings except passwords and sensitive authentication data.
 * 
 * Backup structure:
 * - App-wide settings (theme, sync preferences, debug mode)
 * - Account configurations (server URL, username, enabled services)
 * - Per-account sync settings (intervals, WiFi-only, etc.)
 * - Calendar metadata (display names, colors, visibility)
 * - Address book metadata (display names, sync settings)
 * 
 * Excluded from backup (security):
 * - Passwords
 * - OAuth tokens
 * - Client certificate private keys
 * - Any authentication credentials
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val addressBookRepository: AddressBookRepository
) {
    
    companion object {
        private const val BACKUP_VERSION = 2
        private const val BACKUP_DIR = "backups"
        
        // Keys for sensitive data that should NOT be backed up
        private val SENSITIVE_KEYS = setOf(
            "password",
            "token",
            "oauth",
            "secret",
            "key",
            "credential"
        )
    }
    
    sealed class BackupResult {
        data class Success(val backupJson: String, val size: Long) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }
    
    sealed class RestoreResult {
        data class Success(val accountsRestored: Int, val settingsRestored: Boolean) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }
    
    /**
     * Export all settings to a JSON backup file.
     * @param accountId If provided, backup only this account. If null, backup all accounts.
     * @return The backup JSON as a string (to be saved by caller)
     */
    suspend fun createBackup(accountId: Long? = null): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupJson = createBackupJson(accountId)
            val backupString = backupJson.toString(2) // Pretty print with 2-space indent
            
            Timber.i("Backup JSON created successfully (${backupString.length} bytes)")
            BackupResult.Success(backupString, backupString.length.toLong())
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup")
            BackupResult.Error("Failed to create backup: ${e.message}")
        }
    }
    
    /**
     * Create backup JSON for given account or all accounts.
     * Returns the JSON object that can be saved by the caller.
     */
    private suspend fun createBackupJson(accountId: Long?): JSONObject {
        return JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("exportTimestamp", System.currentTimeMillis())
                put("appVersion", getAppVersion())
                
                // App-wide settings
                put("appSettings", exportAppSettings())
                
                // Account(s) configuration
                if (accountId != null) {
                    // Single account backup
                    val account = accountRepository.getById(accountId)
                        ?: throw IllegalArgumentException("Account not found")
                    put("accounts", JSONArray().apply {
                        put(exportAccount(account))
                    })
                } else {
                    // Full backup - all accounts
                    val accounts = accountRepository.getAll()
                    put("accounts", JSONArray().apply {
                        accounts.forEach { account ->
                            put(exportAccount(account))
                        }
                    })
                }
            }
        }
    
    /**
     * Restore settings from a backup JSON string.
     * @param backupJson The backup JSON string content
     * @param overwriteExisting If true, existing settings will be overwritten
     */
    suspend fun restoreBackup(backupJson: String, overwriteExisting: Boolean = false): RestoreResult = withContext(Dispatchers.IO) {
        try {
            // First, validate the backup
            val validationResult = validateBackup(backupJson)
            if (!validationResult.isValid) {
                return@withContext RestoreResult.Error("Backup validation failed: ${validationResult.errors.joinToString(", ")}")
            }
            
            val backup = JSONObject(backupJson)
            
            // Validate backup version
            val version = backup.optInt("version", 1)
            if (version > BACKUP_VERSION) {
                return@withContext RestoreResult.Error("Backup version $version is not supported by this app version")
            }
            
            Timber.i("Restoring backup (version $version)")
            
            var accountsRestored = 0
            var settingsRestored = false
            
            // Restore app settings
            if (backup.has("appSettings")) {
                importAppSettings(backup.getJSONObject("appSettings"), overwriteExisting)
                settingsRestored = true
                Timber.d("App settings restored")
            }
            
            // Restore accounts
            if (backup.has("accounts")) {
                val accounts = backup.getJSONArray("accounts")
                for (i in 0 until accounts.length()) {
                    val accountJson = accounts.getJSONObject(i)
                    val restored = importAccount(accountJson, overwriteExisting)
                    if (restored) {
                        accountsRestored++
                    }
                }
                Timber.d("Restored $accountsRestored accounts")
            }
            
            Timber.i("Backup restored successfully: $accountsRestored accounts, app settings: $settingsRestored")
            RestoreResult.Success(accountsRestored, settingsRestored)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore backup")
            RestoreResult.Error("Failed to restore backup: ${e.message}")
        }
    }
    
    /**
     * Validate backup JSON structure and content.
     * @return ValidationResult with success status and any errors found
     */
    fun validateBackup(backupJson: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            val backup = JSONObject(backupJson)
            
            // Check required fields
            if (!backup.has("version")) {
                errors.add("Missing 'version' field")
            } else {
                val version = backup.optInt("version", -1)
                if (version < 1 || version > BACKUP_VERSION) {
                    errors.add("Invalid backup version: $version (supported: 1-$BACKUP_VERSION)")
                }
            }
            
            if (!backup.has("exportTimestamp")) {
                errors.add("Missing 'exportTimestamp' field")
            } else {
                val timestamp = backup.optLong("exportTimestamp", -1)
                if (timestamp <= 0) {
                    errors.add("Invalid export timestamp")
                }
            }
            
            if (!backup.has("appVersion")) {
                errors.add("Missing 'appVersion' field")
            }
            
            // Validate accounts structure if present
            if (backup.has("accounts")) {
                val accounts = backup.optJSONArray("accounts")
                if (accounts == null) {
                    errors.add("'accounts' field must be a JSON array")
                } else {
                    for (i in 0 until accounts.length()) {
                        val account = accounts.optJSONObject(i)
                        if (account == null) {
                            errors.add("Invalid account at index $i")
                            continue
                        }
                        
                        // Validate required account fields
                        val requiredAccountFields = listOf("id", "accountName", "username", "serverUrl")
                        requiredAccountFields.forEach { field ->
                            if (!account.has(field)) {
                                errors.add("Account $i missing required field: '$field'")
                            }
                        }
                        
                        // Validate server URL format
                        if (account.has("serverUrl")) {
                            val serverUrl = account.optString("serverUrl", "")
                            if (serverUrl.isBlank()) {
                                errors.add("Account $i has empty server URL")
                            } else if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                                errors.add("Account $i has invalid server URL format")
                            }
                        }
                    }
                }
            }
            
            // Validate appSettings structure if present
            if (backup.has("appSettings")) {
                val appSettings = backup.optJSONObject("appSettings")
                if (appSettings == null) {
                    errors.add("'appSettings' field must be a JSON object")
                }
            }
            
            // Check if backup has meaningful content
            if (!backup.has("accounts") && !backup.has("appSettings")) {
                errors.add("Backup contains no accounts or app settings")
            }
            
        } catch (e: org.json.JSONException) {
            errors.add("Invalid JSON format: ${e.message}")
        } catch (e: Exception) {
            errors.add("Validation error: ${e.message}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * List all available backup files.
     */
    fun listBackups(): List<BackupInfo> {
        val backupDir = File(context.getExternalFilesDir(null), BACKUP_DIR)
        if (!backupDir.exists()) {
            return emptyList()
        }
        
        return backupDir.listFiles { file ->
            file.extension == "json" && file.name.startsWith("davy_")
        }?.map { file ->
            BackupInfo(
                filePath = file.absolutePath,
                fileName = file.name,
                size = file.length(),
                timestamp = file.lastModified()
            )
        }?.sortedByDescending { it.timestamp } ?: emptyList()
    }
    
    /**
     * Delete a backup file.
     */
    fun deleteBackup(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete backup: $filePath")
            false
        }
    }
    
    // Private helper methods
    
    private fun exportAppSettings(): JSONObject {
        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return JSONObject().apply {
            // Export non-sensitive settings only
            appPrefs.all.forEach { (key, value) ->
                if (!isSensitiveKey(key)) {
                    when (value) {
                        is Boolean -> put(key, value)
                        is Int -> put(key, value)
                        is Long -> put(key, value)
                        is Float -> put(key, value)
                        is String -> put(key, value)
                        else -> Timber.w("Skipping unknown setting type: $key")
                    }
                }
            }
        }
    }
    
    private suspend fun exportAccount(account: Account): JSONObject {
        return JSONObject().apply {
            put("id", account.id)  // Include account ID
            put("accountName", account.accountName)
            put("serverUrl", account.serverUrl)
            put("username", account.username)
            put("displayName", account.displayName ?: JSONObject.NULL)
            put("email", account.email ?: JSONObject.NULL)
            put("calendarEnabled", account.calendarEnabled)
            put("contactsEnabled", account.contactsEnabled)
            put("tasksEnabled", account.tasksEnabled)
            put("authType", account.authType.name)
            put("notes", account.notes ?: JSONObject.NULL)
            put("certificateFingerprint", account.certificateFingerprint ?: JSONObject.NULL)
            
            // Export per-account sync settings
            put("syncSettings", exportAccountSyncSettings(account.id))
            
            // Export calendars metadata
            val calendars = calendarRepository.getByAccountId(account.id)
            put("calendars", JSONArray().apply {
                calendars.forEach { calendar ->
                    put(JSONObject().apply {
                        put("displayName", calendar.displayName)
                        put("color", calendar.color)
                        put("description", calendar.description ?: JSONObject.NULL)
                        put("timezone", calendar.timezone ?: JSONObject.NULL)
                        put("visible", calendar.visible)
                        put("syncEnabled", calendar.syncEnabled)
                        put("forceReadOnly", calendar.forceReadOnly)
                        put("supportsVTODO", calendar.supportsVTODO)
                        put("supportsVJOURNAL", calendar.supportsVJOURNAL)
                        put("wifiOnlySync", calendar.wifiOnlySync)
                        put("syncIntervalMinutes", calendar.syncIntervalMinutes ?: JSONObject.NULL)
                    })
                }
            })
            
            // Export address books metadata
            val addressBooks = addressBookRepository.getByAccountId(account.id)
            put("addressBooks", JSONArray().apply {
                addressBooks.forEach { addressBook ->
                    put(JSONObject().apply {
                        put("displayName", addressBook.displayName)
                        put("description", addressBook.description ?: JSONObject.NULL)
                        put("syncEnabled", addressBook.syncEnabled)
                        put("visible", addressBook.visible)
                        put("wifiOnlySync", addressBook.wifiOnlySync)
                        put("syncIntervalMinutes", addressBook.syncIntervalMinutes ?: JSONObject.NULL)
                    })
                }
            })
        }
    }
    
    private fun exportAccountSyncSettings(accountId: Long): JSONObject {
        val syncPrefs = context.getSharedPreferences("sync_config_$accountId", Context.MODE_PRIVATE)
        return JSONObject().apply {
            syncPrefs.all.forEach { (key, value) ->
                if (!isSensitiveKey(key)) {
                    when (value) {
                        is Boolean -> put(key, value)
                        is Int -> put(key, value)
                        is Long -> put(key, value)
                        is Float -> put(key, value)
                        is String -> put(key, value)
                    }
                }
            }
        }
    }
    
    private fun importAppSettings(settings: JSONObject, overwrite: Boolean) {
        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val editor = appPrefs.edit()
        
        settings.keys().forEach { key ->
            if (!isSensitiveKey(key)) {
                // Only import if overwrite is enabled or key doesn't exist
                if (overwrite || !appPrefs.contains(key)) {
                    when (val value = settings.get(key)) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                    }
                }
            }
        }
        
        editor.apply()
    }
    
    private suspend fun importAccount(accountJson: JSONObject, overwrite: Boolean): Boolean {
        return try {
            // Check if account with same server URL and username already exists
            val serverUrl = accountJson.getString("serverUrl")
            val username = accountJson.getString("username")
            val existingAccounts = accountRepository.getAll()
            val existingAccount = existingAccounts.find { 
                it.serverUrl == serverUrl && it.username == username 
            }
            
            if (existingAccount != null && !overwrite) {
                Timber.d("Account already exists, skipping: $username@$serverUrl")
                return false
            }
            
            // Note: We do NOT restore passwords - user must re-enter them
            // This is a security measure to prevent password leakage through backups
            
            val account = Account(
                id = existingAccount?.id ?: 0L, // 0 = new account
                accountName = accountJson.getString("accountName"),
                serverUrl = serverUrl,
                username = username,
                displayName = accountJson.optString("displayName").takeIf { it != "null" },
                email = accountJson.optString("email").takeIf { it != "null" },
                calendarEnabled = accountJson.optBoolean("calendarEnabled", true),
                contactsEnabled = accountJson.optBoolean("contactsEnabled", true),
                tasksEnabled = accountJson.optBoolean("tasksEnabled", false),
                authType = AuthType.valueOf(accountJson.optString("authType", "BASIC_AUTH")),
                notes = accountJson.optString("notes").takeIf { it != "null" },
                certificateFingerprint = accountJson.optString("certificateFingerprint").takeIf { it != "null" },
                createdAt = System.currentTimeMillis(),
                lastAuthenticatedAt = null
            )
            
            // Save or update account (without password)
            val accountId = if (existingAccount != null) {
                accountRepository.update(account)
                existingAccount.id
            } else {
                accountRepository.insert(account)
            }
            
            // Import sync settings
            if (accountJson.has("syncSettings")) {
                importAccountSyncSettings(accountId, accountJson.getJSONObject("syncSettings"), overwrite)
            }
            
            Timber.d("Account imported successfully: ${account.accountName}")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to import account")
            false
        }
    }
    
    private fun importAccountSyncSettings(accountId: Long, settings: JSONObject, overwrite: Boolean) {
        val syncPrefs = context.getSharedPreferences("sync_config_$accountId", Context.MODE_PRIVATE)
        val editor = syncPrefs.edit()
        
        settings.keys().forEach { key ->
            if (!isSensitiveKey(key)) {
                if (overwrite || !syncPrefs.contains(key)) {
                    when (val value = settings.get(key)) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                    }
                }
            }
        }
        
        editor.apply()
    }
    
    private fun isSensitiveKey(key: String): Boolean {
        val lowerKey = key.lowercase()
        return SENSITIVE_KEYS.any { lowerKey.contains(it) }
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    data class BackupInfo(
        val filePath: String,
        val fileName: String,
        val size: Long,
        val timestamp: Long
    )
    
    /**
     * Result of backup validation.
     * @param isValid Whether the backup is valid and can be safely restored
     * @param errors List of validation errors found (empty if isValid is true)
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
}
