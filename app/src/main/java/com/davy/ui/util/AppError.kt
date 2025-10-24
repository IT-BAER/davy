package com.davy.ui.util

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sealed class representing different types of errors that can occur in the app.
 * This provides type-safe error handling with user-friendly messages.
 */
sealed class AppError(val message: String, val cause: Throwable? = null) {
    // Authentication Errors
    data class AuthenticationFailed(val details: String? = null) : AppError(
        message = "Authentication failed${details?.let { ": $it" } ?: ""}. Please check your credentials."
    )
    
    data class InvalidCredentials(val field: String? = null) : AppError(
        message = field?.let { "Invalid $it" } ?: "Invalid credentials"
    )
    
    object SessionExpired : AppError(
        message = "Your session has expired. Please sign in again."
    )
    
    // Network Errors
    data class NetworkError(val details: String? = null, val error: Throwable? = null) : AppError(
        message = "Network error${details?.let { ": $it" } ?: ""}. Please check your connection.",
        cause = error
    )
    
    data class ServerUnreachable(val serverUrl: String? = null) : AppError(
        message = serverUrl?.let { "Cannot reach server: $it" } ?: "Server is unreachable"
    )
    
    data class TimeoutError(val operation: String? = null) : AppError(
        message = operation?.let { "$it timed out" } ?: "Request timed out"
    )
    
    // Sync Errors
    data class SyncFailed(val accountName: String, val details: String? = null) : AppError(
        message = "Failed to sync $accountName${details?.let { ": $it" } ?: ""}"
    )
    
    data class CalendarSyncError(val calendarName: String, val details: String? = null) : AppError(
        message = "Failed to sync calendar '$calendarName'${details?.let { ": $it" } ?: ""}"
    )
    
    data class ContactSyncError(val addressBookName: String, val details: String? = null) : AppError(
        message = "Failed to sync contacts '$addressBookName'${details?.let { ": $it" } ?: ""}"
    )
    
    // Data Errors
    data class DatabaseError(val operation: String, val error: Throwable? = null) : AppError(
        message = "Database error: $operation failed",
        cause = error
    )
    
    data class BackupRestoreError(val details: String) : AppError(
        message = "Backup/Restore error: $details"
    )
    
    // Permission Errors
    object StoragePermissionDenied : AppError(
        message = "Storage permission required to save/load backups"
    )
    
    object CalendarPermissionDenied : AppError(
        message = "Calendar permission required to sync calendars"
    )
    
    object ContactPermissionDenied : AppError(
        message = "Contacts permission required to sync contacts"
    )
    
    // Validation Errors
    data class ValidationError(val field: String, val details: String) : AppError(
        message = "$field: $details"
    )
    
    data class InvalidConfiguration(val details: String) : AppError(
        message = "Invalid configuration: $details"
    )
    
    // General Errors
    data class UnexpectedError(val details: String? = null, val error: Throwable? = null) : AppError(
        message = details ?: "An unexpected error occurred",
        cause = error
    )
    
    companion object {
        /**
         * Convert a throwable to an appropriate AppError.
         */
        fun fromThrowable(throwable: Throwable): AppError {
            return when {
                throwable is java.net.UnknownHostException -> NetworkError("Host not found", throwable)
                throwable is java.net.SocketTimeoutException -> TimeoutError()
                throwable is java.net.ConnectException -> ServerUnreachable()
                throwable is java.io.IOException -> NetworkError(throwable.message, throwable)
                throwable.message?.contains("401", ignoreCase = true) == true -> AuthenticationFailed()
                throwable.message?.contains("403", ignoreCase = true) == true -> AuthenticationFailed("Access denied")
                throwable.message?.contains("404", ignoreCase = true) == true -> ServerUnreachable("Resource not found")
                throwable.message?.contains("500", ignoreCase = true) == true -> NetworkError("Server error")
                else -> UnexpectedError(throwable.message, throwable)
            }
        }
    }
}

/**
 * Extension function to show error messages in a Snackbar with appropriate styling.
 * 
 * @param snackbarHostState The SnackbarHostState to show the error in
 * @param scope CoroutineScope to launch the snackbar
 * @param actionLabel Optional action label (e.g., "Retry")
 * @param onAction Optional action to perform when action is clicked
 * @return SnackbarResult indicating if action was performed
 */
fun AppError.show(
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Long,
    onAction: (() -> Unit)? = null
) {
    scope.launch {
        val result = snackbarHostState.showSnackbar(
            message = this@show.message,
            actionLabel = actionLabel,
            duration = duration,
            withDismissAction = true
        )
        
        if (result == SnackbarResult.ActionPerformed && onAction != null) {
            onAction()
        }
    }
}

/**
 * Extension function to log the error with appropriate severity.
 */
fun AppError.log(tag: String = "AppError") {
    when (this) {
        is AppError.AuthenticationFailed,
        is AppError.InvalidCredentials,
        is AppError.NetworkError,
        is AppError.SyncFailed -> timber.log.Timber.w(cause, message)
        
        is AppError.DatabaseError,
        is AppError.UnexpectedError -> timber.log.Timber.e(cause, message)
        
        else -> timber.log.Timber.i(message)
    }
}
