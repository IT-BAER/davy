package com.davy.sync.calendar

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.davy.sync.SyncLock
import com.davy.sync.SyncManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContentObserver that monitors changes to Android's Calendar Provider.
 * 
 * When events are created/modified in the phone's Calendar app for DAVy accounts,
 * this observer triggers automatic push sync to the CalDAV server.
 * 
 * This observer is registered at application level and does not require a foreground service.
 * Notifications are only shown during active sync operations.
 */
@Singleton
class CalendarContentObserver @Inject constructor(
    private val syncManager: SyncManager,
    private val calendarChangeHandler: CalendarChangeHandler,
    private val syncLock: SyncLock
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
    companion object {
        private const val DEBOUNCE_DELAY_MS = 2000L
        
        // Flag to prevent observing our own changes (DEPRECATED - use SyncLock instead)
        @Deprecated("Use SyncLock.isSyncing() instead")
        @Volatile
        var isSyncInProgress = false
    }
    
    private var lastChangeTimestamp = 0L
    private val changeHandler = Handler(Looper.getMainLooper())
    private var pendingChangeRunnable: Runnable? = null
    private val ignoredInitialNullChange = AtomicBoolean(false)
    
    // Track batched changes within debounce window
    private val batchedChanges = mutableSetOf<String>()
    
    /**
     * Called when calendar data changes in the Calendar Provider.
     */
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        Timber.d("🔔 CALENDAR CHANGE DETECTED: selfChange=$selfChange, URI=$uri")
        
        if (uri == null && !ignoredInitialNullChange.getAndSet(true)) {
            Timber.d("Ignoring initial observer callback with null URI")
            return
        }
        
        // Ignore changes during sync to prevent loops
        if (syncLock.isSyncing()) {
            Timber.d("⏸️ Ignoring change - sync in progress (our own change)")
            return
        }
        
        // Normalize URI to a stable string for batching
        val uriKey = uri?.toString() ?: "null"
        
        // Debounce rapid changes (user may be editing multiple fields)
        // Cancel any pending handler and collect all changes in a batch
        val currentTime = System.currentTimeMillis()
        val timeSinceLastChange = currentTime - lastChangeTimestamp
        
        if (timeSinceLastChange < DEBOUNCE_DELAY_MS) {
            Timber.d("⏰ Batching change within debounce window ($timeSinceLastChange ms < $DEBOUNCE_DELAY_MS ms)")
            Timber.d("   URI: $uri")
            pendingChangeRunnable?.let { changeHandler.removeCallbacks(it) }
        } else {
            // Debounce window expired, start new batch
            Timber.d("📦 Starting new debounce batch (previous batch expired)")
            Timber.d("   URI: $uri")
            batchedChanges.clear()
        }
        
        // Add this change to the batch
        batchedChanges.add(uriKey)
        lastChangeTimestamp = currentTime
        
        // Schedule processing after debounce delay
        pendingChangeRunnable = Runnable {
            Timber.d("========================================")
            Timber.d("🔄 Processing batched changes (${batchedChanges.size} events in batch)")
            Timber.d("========================================")
            
            // Process the most specific URI from the batch
            // Priority: Event-specific URI > Calendar URI > Generic URI
            val specificEventUri = batchedChanges
                .firstOrNull { it.contains("/events/") && it.substringAfterLast("/").toLongOrNull() != null }
            
            val uriToProcess = specificEventUri?.let { Uri.parse(it) } ?: uri
            
            Timber.d("Selected URI for processing: $uriToProcess")
            batchedChanges.clear()
            
            handleChange(uriToProcess)
        }
        changeHandler.postDelayed(pendingChangeRunnable!!, DEBOUNCE_DELAY_MS)
    }
    
    /**
     * Handle the calendar change after debounce period.
     */
    private fun handleChange(uri: Uri?) {
        Timber.d("========================================")
        Timber.d("📍 PROCESSING CALENDAR CHANGE")
        Timber.d("   URI: $uri")
        Timber.d("   URI string: ${uri?.toString()}")
        Timber.d("   Last path segment: ${uri?.lastPathSegment}")
        Timber.d("========================================")
        
        // Determine what changed based on URI
        val changeType = when {
            uri == null -> {
                Timber.d("→ Change type: UNKNOWN (null URI)")
                CalendarChangeType.UNKNOWN
            }
            uri.toString().contains("events") -> {
                Timber.d("→ Change type: EVENT (URI contains 'events')")
                CalendarChangeType.EVENT
            }
            uri.toString().contains("calendars") -> {
                Timber.d("→ Change type: CALENDAR (URI contains 'calendars')")
                CalendarChangeType.CALENDAR
            }
            else -> {
                Timber.d("→ Change type: UNKNOWN (URI doesn't match known patterns)")
                CalendarChangeType.UNKNOWN
            }
        }
        
        Timber.d("Dispatching to handler: $changeType")
        
        // Process the change asynchronously
        calendarChangeHandler.handleChange(changeType, uri)
    }
    
    /**
     * Start observing calendar changes.
     */
    fun startObserving(contentResolver: ContentResolver) {
        Timber.d("========================================")
        Timber.d("🚀 STARTING CALENDAR CONTENT OBSERVATION")
        Timber.d("========================================")
        
        try {
            // Observe calendar events
            contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true, // observe descendants
                this
            )
            Timber.d("✓ Registered observer for Events URI: ${CalendarContract.Events.CONTENT_URI}")
            
            // Observe calendar metadata
            contentResolver.registerContentObserver(
                CalendarContract.Calendars.CONTENT_URI,
                true,
                this
            )
            Timber.d("✓ Registered observer for Calendars URI: ${CalendarContract.Calendars.CONTENT_URI}")
            
            Timber.d("========================================")
            Timber.d("✅ CALENDAR CONTENT OBSERVER ACTIVE")
            Timber.d("   Waiting for calendar changes...")
            Timber.d("========================================")
        } catch (e: Exception) {
            Timber.e(e, "❌ FAILED TO REGISTER CALENDAR CONTENT OBSERVER")
        }
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
