package com.davy.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.davy.R
import com.davy.sync.calendar.CalendarContentObserver
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * DEPRECATED: Foreground service that monitors calendar changes.
 * 
 * This service is no longer used. Calendar monitoring is now handled by
 * CalendarContentObserver registered at application level without requiring
 * a foreground service.
 * 
 * This eliminates the persistent "monitoring calendar changes" notification.
 * 
 * @see com.davy.sync.calendar.CalendarContentObserver
 * @see com.davy.startup.MonitoringServicesInitializer
 */
@Deprecated(
    message = "No longer needed - CalendarContentObserver is registered at app level without service",
    level = DeprecationLevel.WARNING
)
@AndroidEntryPoint
class CalendarMonitorService : Service() {
    
    @Inject
    lateinit var contentObserver: CalendarContentObserver
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "calendar_monitor"
        private const val CHANNEL_NAME = "Calendar Monitoring"
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("CalendarMonitorService created")
        
        // Create notification channel for Android O+
        createNotificationChannel()
        
        // Start as foreground service with proper type for Android 14+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
            stopSelf() // Stop service if we can't go foreground
            return
        }
        
        // Start observing calendar changes
        try {
            contentObserver.startObserving(contentResolver)
            Timber.d("Calendar monitoring started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start calendar monitoring")
            stopSelf() // Stop service if monitoring fails
        }
    }
    
    override fun onDestroy() {
        Timber.d("CalendarMonitorService destroyed")
        
        // Stop observing calendar changes
        try {
            contentObserver.stopObserving(contentResolver)
            Timber.d("Calendar monitoring stopped")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop calendar monitoring")
        }
        
        // Ensure we stop foreground state before destroying
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop foreground")
        }
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("CalendarMonitorService onStartCommand")
        return START_STICKY // Restart service if killed
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DAVy Calendar Sync")
            .setContentText("Monitoring calendar changes")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar) // Using system calendar icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors calendar changes for automatic sync"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
