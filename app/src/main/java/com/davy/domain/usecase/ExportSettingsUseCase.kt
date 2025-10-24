package com.davy.domain.usecase

import android.content.Context
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.AddressBookRepository
import com.davy.domain.model.Account
import com.davy.domain.model.Calendar
import com.davy.domain.model.AddressBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Export account configurations to JSON file.
 * Backs up account settings, sync config, and collection metadata.
 * 
 * See reference implementation: ExportSettingsController
 */
class ExportSettingsUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val addressBookRepository: AddressBookRepository,
    private val context: Context
) {
    
    sealed class Result {
        data class Success(val filePath: String) : Result()
        data class Error(val message: String) : Result()
    }
    
    suspend operator fun invoke(accountId: Long): Result = withContext(Dispatchers.IO) {
        try {
            val account = accountRepository.getById(accountId)
                ?: return@withContext Result.Error("Account not found")
            
            val calendars = calendarRepository.getByAccountId(accountId)
            val addressBooks = addressBookRepository.getByAccountId(accountId)
            
            val exportData = JSONObject().apply {
                put("version", 1)
                put("exportTimestamp", System.currentTimeMillis())
                
                put("account", JSONObject().apply {
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
                })
                
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
            
            val jsonString = exportData.toString(2)  // 2 spaces indentation
            
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val filename = "davy_settings_${account.accountName}_$timestamp.json"
            val exportFile = File(exportDir, filename)
            
            exportFile.writeText(jsonString)
            
            Timber.d("Settings exported successfully to: ${exportFile.absolutePath}")
            Result.Success(exportFile.absolutePath)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings")
            Result.Error("Failed to export settings: ${e.message}")
        }
    }
}


