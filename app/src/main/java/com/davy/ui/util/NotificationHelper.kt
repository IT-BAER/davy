package com.davy.ui.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.davy.R
import timber.log.Timber

/**
 * Helper for showing notifications for background errors.
 * Integrates with AppError to display user-friendly error notifications.
 */
object NotificationHelper {
    
    private const val CHANNEL_ID_SYNC_ERRORS = "sync_errors"
    private const val CHANNEL_NAME_SYNC_ERRORS = "Sync Errors"
    
    // Track recent notifications to prevent duplicates
    private val recentNotifications = mutableMapOf<String, Long>()
    private const val NOTIFICATION_COOLDOWN_MS = 30000L // 30 seconds
    
    /**
     * Ensure notification channel is created (required for Android 8.0+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SYNC_ERRORS,
                CHANNEL_NAME_SYNC_ERRORS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for sync errors and authentication issues"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show notification for an AppError during background sync.
     * 
     * @param context Application context
     * @param error The AppError to display
     * @param accountName Optional account name to include in notification
     * @param accountId Optional account ID to open settings when notification is tapped
     */
    fun showErrorNotification(
        context: Context,
        error: AppError,
        accountName: String? = null,
        accountId: Long? = null
    ) {
        // Ensure channel exists
        createNotificationChannels(context)
        
        // Log the error
        error.log("NotificationHelper")
        
        // Check for duplicate notifications (deduplication)
        // Use accountId if available, otherwise fallback to accountName
        val notificationKey = if (accountId != null) {
            "${error.javaClass.simpleName}:account_$accountId"
        } else {
            "${error.javaClass.simpleName}:$accountName"
        }
        val now = System.currentTimeMillis()
        recentNotifications[notificationKey]?.let { lastTime ->
            if (now - lastTime < NOTIFICATION_COOLDOWN_MS) {
                Timber.d("Skipping duplicate notification: $notificationKey")
                return
            }
        }
        recentNotifications[notificationKey] = now
        
        // Clean up old entries (older than 5 minutes)
        recentNotifications.entries.removeIf { now - it.value > 300000 }
        
        // Build notification title and text
        val title = when (error) {
            is AppError.AuthenticationFailed -> "Authentication Failed"
            is AppError.NetworkError -> "Network Error"
            is AppError.ServerUnreachable -> "Server Unreachable"
            is AppError.SyncFailed -> "Sync Failed"
            else -> "Error"
        }
        
        val text = buildString {
            if (accountName != null) {
                append("Account: $accountName\n")
            }
            append(error.message)
        }
        
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.w("Notification permission not granted, cannot show error notification")
                return
            }
        }
        
        // Create pending intent to open account settings when tapped
        val intent = if (accountId != null) {
            Intent(context, Class.forName("com.davy.ui.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "account/$accountId/settings")
            }
        } else {
            Intent(context, Class.forName("com.davy.ui.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            accountId?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build and show notification
        val notificationId = accountId?.toInt() ?: error.javaClass.simpleName.hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC_ERRORS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                notificationId,
                notification
            )
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to show notification due to missing permission")
        }
    }
    
    /**
     * Show notification for HTTP status code errors (like 401, 404, 500).
     */
    fun showHttpErrorNotification(
        context: Context,
        statusCode: Int,
        accountName: String? = null,
        accountId: Long? = null
    ) {
        val error = when (statusCode) {
            401, 403 -> AppError.AuthenticationFailed(
                "Authentication failed for ${accountName ?: "account"}. Please check your credentials."
            )
            404 -> AppError.ServerUnreachable(
                "Server not found (HTTP 404). Please check the server URL."
            )
            500, 502, 503 -> AppError.ServerUnreachable(
                "Server error (HTTP $statusCode). The server may be temporarily unavailable."
            )
            else -> AppError.SyncFailed(
                "Sync operation failed with HTTP $statusCode"
            )
        }
        
        showErrorNotification(context, error, accountName, accountId)
    }
}
