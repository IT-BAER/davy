package com.davy.sync.calendar

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.davy.sync.SyncManager
import dagger.hilt.android.scopes.ServiceScoped
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ContentObserver that monitors changes to Android's Calendar Provider.
 * 
 * When events are created/modified in the phone's Calendar app for DAVy accounts,
 * this observer triggers automatic push sync to the CalDAV server.
 */
@ServiceScoped
class CalendarContentObserver @Inject constructor(
    private val syncManager: SyncManager,
    private val calendarChangeHandler: CalendarChangeHandler
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
    companion object {
        private const val DEBOUNCE_DELAY_MS = 2000L
        
        // Flag to prevent observing our own changes
        @Volatile
        var isSyncInProgress = false
    }
    
    private var lastChangeTimestamp = 0L
    private val changeHandler = Handler(Looper.getMainLooper())
    private var pendingChangeRunnable: Runnable? = null
    private val ignoredInitialNullChange = AtomicBoolean(false)
    
    /**
     * Called when calendar data changes in the Calendar Provider.
     */
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        Timber.d("========================================")
        Timber.d("Calendar data changed - selfChange: $selfChange, uri: $uri")
        Timber.d("========================================")
        if (uri == null && !ignoredInitialNullChange.getAndSet(true)) {
            Timber.d("Ignoring initial observer callback with null URI")
            return
        }
        
        // Ignore changes during sync to prevent loops
        if (isSyncInProgress) {
            Timber.d("⏸️ Ignoring change - sync in progress (our own change)")
            return
        }
        
        // Debounce rapid changes (user may be editing multiple fields)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastChangeTimestamp < DEBOUNCE_DELAY_MS) {
            Timber.d("Debouncing change - too rapid (last: $lastChangeTimestamp, now: $currentTime)")
            pendingChangeRunnable?.let { changeHandler.removeCallbacks(it) }
        }
        
        lastChangeTimestamp = currentTime
        
        // Schedule processing after debounce delay
        pendingChangeRunnable = Runnable {
            Timber.d("Executing debounced change handler for URI: $uri")
            handleChange(uri)
        }
        changeHandler.postDelayed(pendingChangeRunnable!!, DEBOUNCE_DELAY_MS)
        Timber.d("Change handler scheduled with ${DEBOUNCE_DELAY_MS}ms delay")
    }
    
    /**
     * Handle the calendar change after debounce period.
     */
    private fun handleChange(uri: Uri?) {
        Timber.d("Processing calendar change: $uri")
        
        // Determine what changed based on URI
        val changeType = when {
            uri == null -> CalendarChangeType.UNKNOWN
            uri.toString().contains("events") -> CalendarChangeType.EVENT
            uri.toString().contains("calendars") -> CalendarChangeType.CALENDAR
            else -> CalendarChangeType.UNKNOWN
        }
        
        // Process the change asynchronously
        calendarChangeHandler.handleChange(changeType, uri)
    }
    
    /**
     * Start observing calendar changes.
     */
    fun startObserving(contentResolver: ContentResolver) {
        Timber.d("========================================")
        Timber.d("Starting calendar content observation")
        Timber.d("========================================")
        
        // Observe calendar events
        contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true, // observe descendants
            this
        )
        Timber.d("Registered observer for Events URI: ${CalendarContract.Events.CONTENT_URI}")
        
        // Observe calendar metadata
        contentResolver.registerContentObserver(
            CalendarContract.Calendars.CONTENT_URI,
            true,
            this
        )
        Timber.d("Registered observer for Calendars URI: ${CalendarContract.Calendars.CONTENT_URI}")
        
        Timber.d("========================================")
        Timber.d("Calendar content observer registration COMPLETE")
        Timber.d("========================================")
    }
    
    /**
     * Stop observing calendar changes.
     */
    fun stopObserving(contentResolver: ContentResolver) {
        Timber.d("Stopping calendar content observation")
        contentResolver.unregisterContentObserver(this)
        
        // Cancel any pending changes
        pendingChangeRunnable?.let { changeHandler.removeCallbacks(it) }
    }
}

/**
 * Type of calendar change detected.
 */
enum class CalendarChangeType {
    EVENT,
    CALENDAR,
    UNKNOWN
}
