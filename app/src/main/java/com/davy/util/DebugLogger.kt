package com.davy.util

import android.content.Context
import timber.log.Timber

/**
 * Debug logger utility that can be enabled/disabled at runtime.
 * Provides detailed logging for troubleshooting without exposing sensitive data.
 */
object DebugLogger {
    
    private var isDebugLoggingEnabled = false
    private var debugTree: Timber.Tree? = null
    
    /**
     * Initialize debug logging based on user preferences.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // If user explicitly set the preference, honor it. Otherwise default to enabled in debug builds.
        val enabled = if (prefs.contains("debug_logging")) {
            prefs.getBoolean("debug_logging", false)
        } else {
            // Enable verbose logging by default in debug builds to aid troubleshooting
            com.davy.BuildConfig.DEBUG
        }
        setDebugLoggingEnabled(enabled)
    }
    
    /**
     * Enable or disable debug logging.
     * When enabled, plants a custom debug tree with sensitive data filtering.
     * When disabled, removes the debug tree to reduce logging overhead.
     */
    fun setDebugLoggingEnabled(enabled: Boolean) {
        isDebugLoggingEnabled = enabled
        
        if (enabled) {
            // Remove existing debug tree if any
            debugTree?.let { Timber.uproot(it) }
            
            // Plant a new filtered debug tree
            debugTree = FilteredDebugTree()
            Timber.plant(debugTree!!)
            Timber.d("Debug logging ENABLED")
        } else {
            debugTree?.let {
                Timber.d("Debug logging DISABLED")
                Timber.uproot(it)
                debugTree = null
            }
        }
    }
    
    /**
     * Custom debug tree that filters out sensitive data like passwords.
     */
    private class FilteredDebugTree : Timber.DebugTree() {
        
        private val sensitivePatterns = listOf(
            Regex("password[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("passwd[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("pwd[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("token[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("secret[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("authorization[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("auth[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE)
        )
        
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Filter sensitive data from message
            val filteredMessage = filterSensitiveData(message)
            super.log(priority, tag, filteredMessage, t)
        }
        
        /**
         * Filter out sensitive data from log messages.
         * Replaces passwords, tokens, and other secrets with [FILTERED].
         */
        private fun filterSensitiveData(message: String): String {
            var filtered = message
            
            sensitivePatterns.forEach { pattern ->
                filtered = filtered.replace(pattern) { matchResult ->
                    val key = matchResult.value.substringBefore("=").substringBefore(":").trim()
                    "$key=[FILTERED]"
                }
            }
            
            return filtered
        }
    }
}
