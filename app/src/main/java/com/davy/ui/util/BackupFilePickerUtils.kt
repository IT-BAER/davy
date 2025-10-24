package com.davy.ui.util

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates an ActivityResultLauncher for saving backup files using Storage Access Framework.
 * @param onFileSelected Callback when user selects a save location
 */
@Composable
fun rememberBackupFileSaver(
    onFileSelected: (Uri?) -> Unit
): ManagedActivityResultLauncher<String, Uri?> {
    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) }
    val defaultFilename = remember { "davy_backup_$timestamp.json" }
    
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        onFileSelected(uri)
    }
}

/**
 * Creates an ActivityResultLauncher for selecting backup files to restore using Storage Access Framework.
 * @param onFileSelected Callback when user selects a file
 */
@Composable
fun rememberBackupFileOpener(
    onFileSelected: (Uri?) -> Unit
): ManagedActivityResultLauncher<Array<String>, Uri?> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onFileSelected(uri)
    }
}

/**
 * Writes backup content to the selected URI using Storage Access Framework.
 */
fun writeBackupToUri(context: Context, uri: Uri, content: String): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        }
        Timber.d("Backup written successfully to: $uri")
        true
    } catch (e: Exception) {
        Timber.e(e, "Failed to write backup to URI: $uri")
        false
    }
}

/**
 * Reads backup content from the selected URI using Storage Access Framework.
 */
fun readBackupFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to read backup from URI: $uri")
        null
    }
}

/**
 * Gets a displayable filename from a content URI.
 */
fun getFileNameFromUri(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "Unknown"
    } catch (e: Exception) {
        Timber.e(e, "Failed to get filename from URI")
        uri.lastPathSegment ?: "Unknown"
    }
}

/**
 * Gets file size from a content URI.
 */
fun getFileSizeFromUri(context: Context, uri: Uri): Long {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            cursor.moveToFirst()
            cursor.getLong(sizeIndex)
        } ?: 0L
    } catch (e: Exception) {
        Timber.e(e, "Failed to get file size from URI")
        0L
    }
}
