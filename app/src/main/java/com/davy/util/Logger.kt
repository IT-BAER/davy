package com.davy.util

import timber.log.Timber

/**
 * Centralized logging utility for DAVy app.
 * Wraps Timber to provide structured logging.
 */
object Logger {
    
    /**
     * Initializes logging for the app.
     * Should be called in Application.onCreate()
     *
     * @param isDebug Whether app is in debug mode
     */
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In release, plant a tree that logs to crash reporting
            Timber.plant(ReleaseTree())
        }
    }
    
    /**
     * Logs verbose message (debug only).
     */
    fun v(message: String, vararg args: Any?) {
        Timber.v(message, *args)
    }
    
    /**
     * Logs debug message.
     */
    fun d(message: String, vararg args: Any?) {
        Timber.d(message, *args)
    }
    
    /**
     * Logs info message.
     */
    fun i(message: String, vararg args: Any?) {
        Timber.i(message, *args)
    }
    
    /**
     * Logs warning message.
     */
    fun w(message: String, vararg args: Any?) {
        Timber.w(message, *args)
    }
    
    /**
     * Logs warning with throwable.
     */
    fun w(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.w(throwable, message, *args)
    }
    
    /**
     * Logs error message.
     */
    fun e(message: String, vararg args: Any?) {
        Timber.e(message, *args)
    }
    
    /**
     * Logs error with throwable.
     */
    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.e(throwable, message, *args)
    }
    
    /**
     * Logs sync-specific messages.
     */
    fun sync(message: String, vararg args: Any?) {
        Timber.tag("SYNC").d(message, *args)
    }
    
    /**
     * Logs network-specific messages.
     */
    fun network(message: String, vararg args: Any?) {
        Timber.tag("NETWORK").d(message, *args)
    }
    
    /**
     * Logs database-specific messages.
     */
    fun db(message: String, vararg args: Any?) {
        Timber.tag("DB").d(message, *args)
    }
}

/**
 * Release tree that logs errors to crash reporting.
 */
private class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == android.util.Log.ERROR || priority == android.util.Log.WARN) {
            // In production, send to crash reporting service
            // TODO: Integrate with Firebase Crashlytics or similar
            t?.let {
                // crashlytics.log(message)
                // crashlytics.recordException(it)
            }
        }
    }
}
