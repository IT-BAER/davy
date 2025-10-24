package com.davy.domain.usecase

import android.content.Context
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.domain.model.Calendar
import com.davy.sync.SyncManager
import com.davy.sync.calendar.CalendarContractSync
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for updating calendar settings.
 * 
 * Clean approach for sync toggle:
 * - When sync is disabled: Remove calendar and events from Calendar Provider
 * - When sync is enabled: Re-add calendar to Calendar Provider and sync from server
 * - No data loss: Server always has the source data
 */
class UpdateCalendarUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val accountRepository: AccountRepository,
    @ApplicationContext private val context: Context,
    private val syncManager: SyncManager,
    private val calendarContractSync: CalendarContractSync
) {
    
    /**
     * Update a calendar.
     * 
     * Clean sync toggle approach:
     * - Sync disabled: Remove calendar and events from Calendar Provider
     * - Sync enabled: Re-add calendar and sync events from server
     * - Server is the source of truth, no data loss
     * 
     * @param calendar The calendar with updated properties
     */
    suspend operator fun invoke(calendar: Calendar) {
        Timber.tag("UpdateCalendarUseCase").d("========================================")
        Timber.tag("UpdateCalendarUseCase").d("INVOKE CALLED FOR: %s", calendar.displayName)
        Timber.tag("UpdateCalendarUseCase").d("   syncEnabled: %s", calendar.syncEnabled)
        Timber.tag("UpdateCalendarUseCase").d("   androidCalendarId: %s", calendar.androidCalendarId)
        Timber.tag("UpdateCalendarUseCase").d("========================================")
        
        // Get the old calendar state to compare
        val oldCalendar = calendarRepository.getById(calendar.id)
        
    Timber.tag("UpdateCalendarUseCase").d("Old calendar retrieved:")
    Timber.tag("UpdateCalendarUseCase").d("   old.syncEnabled: %s", oldCalendar?.syncEnabled)
    Timber.tag("UpdateCalendarUseCase").d("   old.androidCalendarId: %s", oldCalendar?.androidCalendarId)
        
        // Update the calendar in repository
        calendarRepository.update(calendar)
        
        // Handle sync toggle: clean approach
        if (oldCalendar != null) {
            // Get account name for Calendar Provider operations
            val account = accountRepository.getById(calendar.accountId)
            val accountName = account?.accountName ?: "DAVy Account"
            
            Timber.tag("UpdateCalendarUseCase").d("Account name: %s", accountName)
            
            // Sync was disabled: Remove calendar from Calendar Provider
            if (oldCalendar.syncEnabled && !calendar.syncEnabled) {
                Timber.tag("UpdateCalendarUseCase").d("SYNC DISABLED - oldEnabled=true, newEnabled=false")
                Timber.d("[SYNC_DISABLE] Removing calendar from Calendar Provider: ${calendar.displayName}")
                
                if (calendar.androidCalendarId != null) {
                    Timber.tag("UpdateCalendarUseCase").d("androidCalendarId exists: %s", calendar.androidCalendarId)
                    Timber.tag("UpdateCalendarUseCase").d("Calling deleteCalendarFromProvider...")
                    
                    calendarContractSync.deleteCalendarFromProvider(
                        androidCalendarId = calendar.androidCalendarId,
                        accountName = accountName
                    )
                    
                    Timber.tag("UpdateCalendarUseCase").d("deleteCalendarFromProvider completed")
                    
                    // Clear the androidCalendarId since it's been removed
                    val updated = calendar.copy(androidCalendarId = null)
                    calendarRepository.update(updated)
                    
                    Timber.d("[SYNC_DISABLE] Calendar and events removed from Calendar Provider")
                } else {
                    Timber.tag("UpdateCalendarUseCase").d("androidCalendarId is NULL - skipping deletion")
                }
            } else {
                Timber.tag("UpdateCalendarUseCase").d("NOT a disable toggle: oldEnabled=%s, newEnabled=%s", oldCalendar.syncEnabled, calendar.syncEnabled)
            }
            
            // Sync was enabled: Re-add calendar to Calendar Provider and sync
            if (!oldCalendar.syncEnabled && calendar.syncEnabled) {
                Timber.d("[SYNC_ENABLE] Re-adding calendar to Calendar Provider: ${calendar.displayName}")
                
                // Calendar will be re-created in Calendar Provider during sync
                // Trigger immediate sync for THIS calendar only to download events from server
                syncManager.syncCalendar(calendar.accountId, calendar.id)
                
                Timber.d("[SYNC_ENABLE] Calendar sync triggered, will re-add to Calendar Provider")
            }
        }
    }
}
