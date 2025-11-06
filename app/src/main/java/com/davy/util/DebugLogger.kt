package com.davy.util

import android.content.Context
import android.util.Log
import timber.log.Timber

/**
 * Debug logger utility that can be enabled/disabled at runtime.
 * Implements Android logging best practices with runtime configuration.
 * 
 * ANDROID LOGGING BEST PRACTICES:
 * ================================
 * 1. Runtime Control: User can toggle debug logging in app settings
 * 2. Sensitive Data Filtering: Passwords, tokens, etc. are automatically filtered
 * 3. Level-based Filtering: Different log levels for development vs production
 * 4. ProGuard Integration: VERBOSE and DEBUG calls are stripped at compile time in release
 * 5. Performance: Minimal overhead when debug logging is disabled
 * 
 * LOG LEVELS:
 * -----------
 * - VERBOSE: Development only, most detailed (stripped in release builds)
 * - DEBUG: Development only, debugging information (stripped in release builds)
 * - INFO: Production-safe, general information
 * - WARN: Production, potential issues that should be monitored
 * - ERROR: Production, errors that need attention
 * 
 * When debug logging is disabled (production mode):
 * - Only WARNING and ERROR level logs are shown
 * - VERBOSE, DEBUG, and INFO logs are suppressed
 * - Reduces logcat noise and performance overhead
 * 
 * When debug logging is enabled (debug mode):
 * - All log levels are shown (with sensitive data filtering)
 * - Full diagnostics for troubleshooting
 * - Automatically filters passwords, tokens, credentials
 * 
 * USAGE:
 * ------
 * // In Application.onCreate()
 * DebugLogger.init(context)
 * 
 * // In Settings
 * DebugLogger.setDebugLoggingEnabled(enabled)
 * 
 * // In code - use Timber as normal
 * Timber.v("Verbose message") // Stripped in release builds
 * Timber.d("Debug message")   // Stripped in release builds
 * Timber.i("Info message")    // Kept, but filtered by runtime setting
 * Timber.w("Warning")         // Always logged
 * Timber.e("Error")           // Always logged
 */
object DebugLogger {
    
    private var isDebugLoggingEnabled = false
    private var debugTree: Timber.Tree? = null
    
    /**
     * Initialize debug logging based on user preferences.
     * In release builds with debug logging disabled, only errors and warnings are logged.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // If user explicitly set the preference, honor it. Otherwise default to disabled in release builds.
        val enabled = if (prefs.contains("debug_logging")) {
            prefs.getBoolean("debug_logging", false)
        } else {
            // Only enable verbose logging by default in debug builds
            com.davy.BuildConfig.DEBUG
        }
        setDebugLoggingEnabled(enabled)
    }
    
    /**
     * Enable or disable debug logging.
     * When enabled, plants a custom debug tree with sensitive data filtering for all log levels.
     * When disabled, plants a tree that only logs warnings and errors to reduce overhead.
     */
    fun setDebugLoggingEnabled(enabled: Boolean) {
        isDebugLoggingEnabled = enabled
        
        // Remove existing debug tree if any
        debugTree?.let { 
            Timber.uproot(it)
            debugTree = null
        }
        
        if (enabled) {
            // Plant a new filtered debug tree that logs everything
            debugTree = FilteredDebugTree()
            Timber.plant(debugTree!!)
            Timber.d("Debug logging ENABLED - All log levels active with sensitive data filtering")
        } else {
            // Plant a tree that only logs warnings and errors (suppresses verbose, debug, info)
            debugTree = ProductionTree()
            Timber.plant(debugTree!!)
            // Use warning level since debug is now disabled
            Timber.w("Debug logging DISABLED - Only warnings and errors will be logged")
        }
    }
    
    /**
     * Custom debug tree that filters out sensitive data like passwords.
     * Logs all levels when debug logging is enabled.
     * 
     * SECURITY: Automatically redacts sensitive information to prevent credential leaks.
     */
    private class FilteredDebugTree : Timber.DebugTree() {
        
        // Patterns to detect and filter sensitive data in log messages
        private val sensitivePatterns = listOf(
            Regex("password[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("passwd[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("pwd[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("token[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("secret[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("authorization[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("auth[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("api[_-]?key[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("client[_-]?secret[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("access[_-]?token[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("refresh[_-]?token[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE),
            Regex("bearer[\\s=:]+[\\S]+", RegexOption.IGNORE_CASE)
        )
        
        override fun createStackElementTag(element: StackTraceElement): String {
            // Add line number to tag for easier debugging
            return super.createStackElementTag(element) + ":" + element.lineNumber
        }
        
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
    
    /**
     * Production tree that only logs warnings and errors.
     * Suppresses VERBOSE, DEBUG, and INFO logs to reduce overhead when debug logging is disabled.
     * 
     * PERFORMANCE: Minimizes logging overhead in production by filtering at runtime.
     * SECURITY: Combined with ProGuard rules that strip v() and d() calls at compile time.
     */
    private class ProductionTree : Timber.Tree() {
        
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Only log warnings and errors in production mode
            if (priority >= Log.WARN) {
                // Send to Android log
                if (t != null) {
                    if (priority == Log.WARN) {
                        Log.w(tag, message, t)
                    } else {
                        Log.e(tag, message, t)
                    }
                } else {
                    if (priority == Log.WARN) {
                        Log.w(tag, message)
                    } else {
                        Log.e(tag, message)
                    }
                }
                
                // TODO: Send ERROR level to crash reporting (Firebase Crashlytics, Sentry, etc.)
                if (priority == Log.ERROR && t != null) {
                    // Example: FirebaseCrashlytics.getInstance().recordException(t)
                    // Example: Sentry.captureException(t)
                }
            }
            // VERBOSE, DEBUG, and INFO are silently discarded
        }
    }
    
    /**
     * Check if debug logging is currently enabled.
     * Useful for conditional expensive log operations.
     * 
     * Example:
     * ```
     * if (DebugLogger.isEnabled()) {
     *     val expensiveData = computeExpensiveDebugInfo()
     *     Timber.d("Debug info: $expensiveData")
     * }
     * ```
     */
    fun isEnabled(): Boolean = isDebugLoggingEnabled
}
