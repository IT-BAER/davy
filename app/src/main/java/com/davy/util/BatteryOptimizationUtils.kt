package com.davy.util

import android.content.Context
import android.os.Build
import android.os.PowerManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for checking battery optimization status.
 * Used to guide users to exempt the app for reliable background sync.
 * 
 * See reference implementation: BatteryOptimizationsPage
 */
@Singleton
class BatteryOptimizationUtils @Inject constructor(
    private val context: Context
) {
    
    /**
     * Check if app is being optimized for battery usage.
     * Returns true if battery optimization is active (bad for sync).
     * Returns false if app is exempted (good for sync).
     */
    fun isOptimized(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Battery optimization introduced in Android 6.0
            return false
        }
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    /**
     * Check if device is running Android 6.0+ where battery optimization exists.
     */
    fun isBatteryOptimizationAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
