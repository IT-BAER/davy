package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.WebCalSubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for WebCal subscription operations.
 * 
 * Provides database access for webcal:// subscriptions.
 */
@Dao
interface WebCalSubscriptionDao {
    
    /**
     * Insert a new WebCal subscription.
     * 
     * @return ID of inserted subscription
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subscription: WebCalSubscriptionEntity): Long
    
    /**
     * Update an existing WebCal subscription.
     */
    @Update
    suspend fun update(subscription: WebCalSubscriptionEntity)
    
    /**
     * Delete a WebCal subscription.
     */
    @Delete
    suspend fun delete(subscription: WebCalSubscriptionEntity)
    
    /**
     * Delete subscription by ID.
     */
    @Query("DELETE FROM webcal_subscription WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * Get subscription by ID.
     */
    @Query("SELECT * FROM webcal_subscription WHERE id = :id")
    suspend fun getById(id: Long): WebCalSubscriptionEntity?
    
    /**
     * Get subscription by URL.
     */
    @Query("SELECT * FROM webcal_subscription WHERE subscription_url = :url")
    suspend fun getByUrl(url: String): WebCalSubscriptionEntity?
    
    /**
     * Get all subscriptions for an account as Flow.
     */
    @Query("SELECT * FROM webcal_subscription WHERE account_id = :accountId ORDER BY display_name ASC")
    fun getByAccountIdFlow(accountId: Long): Flow<List<WebCalSubscriptionEntity>>
    
    /**
     * Get all subscriptions for an account.
     */
    @Query("SELECT * FROM webcal_subscription WHERE account_id = :accountId ORDER BY display_name ASC")
    suspend fun getByAccountId(accountId: Long): List<WebCalSubscriptionEntity>
    
    /**
     * Get all sync-enabled subscriptions for an account.
     */
    @Query("SELECT * FROM webcal_subscription WHERE account_id = :accountId AND sync_enabled = 1 ORDER BY display_name ASC")
    suspend fun getSyncEnabledByAccountId(accountId: Long): List<WebCalSubscriptionEntity>
    
    /**
     * Get all subscriptions that need refresh (enabled and past refresh interval).
     */
    @Query("""
        SELECT * FROM webcal_subscription 
        WHERE sync_enabled = 1 
        AND (last_synced_at IS NULL 
             OR ((:currentTime - last_synced_at) >= (refresh_interval_minutes * 60 * 1000)))
        ORDER BY last_synced_at ASC
    """)
    suspend fun getSubscriptionsNeedingRefresh(currentTime: Long = System.currentTimeMillis()): List<WebCalSubscriptionEntity>
    
    /**
     * Get all subscriptions for an account that need refresh.
     */
    @Query("""
        SELECT * FROM webcal_subscription 
        WHERE account_id = :accountId
        AND sync_enabled = 1 
        AND (last_synced_at IS NULL 
             OR ((:currentTime - last_synced_at) >= (refresh_interval_minutes * 60 * 1000)))
        ORDER BY last_synced_at ASC
    """)
    suspend fun getAccountSubscriptionsNeedingRefresh(
        accountId: Long,
        currentTime: Long = System.currentTimeMillis()
    ): List<WebCalSubscriptionEntity>
    
    /**
     * Update sync status after refresh.
     */
    @Query("""
        UPDATE webcal_subscription 
        SET last_synced_at = :syncTime,
            last_sync_error = :error,
            etag = :etag,
            updated_at = :syncTime
        WHERE id = :id
    """)
    suspend fun updateSyncStatus(
        id: Long,
        syncTime: Long,
        etag: String?,
        error: String?
    )
    
    /**
     * Update Android Calendar ID after syncing to Calendar Provider.
     */
    @Query("""
        UPDATE webcal_subscription 
        SET android_calendar_id = :androidCalendarId,
            updated_at = :updateTime
        WHERE id = :id
    """)
    suspend fun updateAndroidCalendarId(
        id: Long,
        androidCalendarId: Long?,
        updateTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Toggle sync enabled status.
     */
    @Query("""
        UPDATE webcal_subscription 
        SET sync_enabled = :enabled,
            updated_at = :updateTime
        WHERE id = :id
    """)
    suspend fun updateSyncEnabled(
        id: Long,
        enabled: Boolean,
        updateTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Toggle visibility status.
     */
    @Query("""
        UPDATE webcal_subscription 
        SET visible = :visible,
            updated_at = :updateTime
        WHERE id = :id
    """)
    suspend fun updateVisible(
        id: Long,
        visible: Boolean,
        updateTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Update subscription color.
     */
    @Query("""
        UPDATE webcal_subscription 
        SET color = :color,
            updated_at = :updateTime
        WHERE id = :id
    """)
    suspend fun updateColor(
        id: Long,
        color: Int,
        updateTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Update refresh interval.
     */
    @Query("""
        UPDATE webcal_subscription 
        SET refresh_interval_minutes = :intervalMinutes,
            updated_at = :updateTime
        WHERE id = :id
    """)
    suspend fun updateRefreshInterval(
        id: Long,
        intervalMinutes: Int,
        updateTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Get count of all subscriptions.
     */
    @Query("SELECT COUNT(*) FROM webcal_subscription")
    suspend fun getCount(): Int
    
    /**
     * Get count of subscriptions for an account.
     */
    @Query("SELECT COUNT(*) FROM webcal_subscription WHERE account_id = :accountId")
    suspend fun getCountByAccount(accountId: Long): Int
    
    /**
     * Delete all subscriptions for an account.
     * (Cascade delete handles this automatically via foreign key)
     */
    @Query("DELETE FROM webcal_subscription WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
}
