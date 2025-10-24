package com.davy.data.repository

import com.davy.data.local.dao.WebCalSubscriptionDao
import com.davy.data.local.entity.WebCalSubscriptionEntity
import com.davy.data.local.entity.toDomain
import com.davy.data.local.entity.toEntity
import com.davy.domain.model.WebCalSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Repository for WebCal subscription operations.
 * 
 * Handles CRUD operations and provides reactive data streams
 * for webcal:// subscriptions.
 */
class WebCalSubscriptionRepository @Inject constructor(
    private val webCalDao: WebCalSubscriptionDao
) {
    
    /**
     * Insert a new WebCal subscription.
     * 
     * @return The ID of the inserted subscription
     * @throws Exception if subscription with same URL already exists
     */
    suspend fun insert(subscription: WebCalSubscription): Long {
        return webCalDao.insert(subscription.toEntity())
    }
    
    /**
     * Update an existing WebCal subscription.
     */
    suspend fun update(subscription: WebCalSubscription) {
        webCalDao.update(subscription.toEntity())
    }
    
    /**
     * Delete a WebCal subscription.
     */
    suspend fun delete(subscription: WebCalSubscription) {
        webCalDao.delete(subscription.toEntity())
    }
    
    /**
     * Delete subscription by ID.
     */
    suspend fun deleteById(id: Long) {
        webCalDao.deleteById(id)
    }
    
    /**
     * Get subscription by ID.
     */
    suspend fun getById(id: Long): WebCalSubscription? {
        return webCalDao.getById(id)?.toDomain()
    }
    
    /**
     * Get subscription by URL.
     */
    suspend fun getByUrl(url: String): WebCalSubscription? {
        return webCalDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get all subscriptions for an account as Flow.
     */
    fun getByAccountIdFlow(accountId: Long): Flow<List<WebCalSubscription>> {
        return webCalDao.getByAccountIdFlow(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all subscriptions for an account.
     */
    suspend fun getByAccountId(accountId: Long): List<WebCalSubscription> {
        return webCalDao.getByAccountId(accountId).map { it.toDomain() }
    }
    
    /**
     * Get all sync-enabled subscriptions for an account.
     */
    suspend fun getSyncEnabledByAccountId(accountId: Long): List<WebCalSubscription> {
        return webCalDao.getSyncEnabledByAccountId(accountId).map { it.toDomain() }
    }
    
    /**
     * Get all subscriptions that need refresh.
     */
    suspend fun getSubscriptionsNeedingRefresh(): List<WebCalSubscription> {
        return webCalDao.getSubscriptionsNeedingRefresh().map { it.toDomain() }
    }
    
    /**
     * Get all subscriptions for an account that need refresh.
     */
    suspend fun getAccountSubscriptionsNeedingRefresh(accountId: Long): List<WebCalSubscription> {
        return webCalDao.getAccountSubscriptionsNeedingRefresh(accountId).map { it.toDomain() }
    }
    
    /**
     * Update sync status after refresh.
     */
    suspend fun updateSyncStatus(
        id: Long,
        syncTime: Long,
        etag: String?,
        error: String?
    ) {
        webCalDao.updateSyncStatus(id, syncTime, etag, error)
    }
    
    /**
     * Update Android Calendar ID after syncing to Calendar Provider.
     */
    suspend fun updateAndroidCalendarId(id: Long, androidCalendarId: Long?) {
        webCalDao.updateAndroidCalendarId(id, androidCalendarId)
    }
    
    /**
     * Toggle sync enabled status.
     */
    suspend fun updateSyncEnabled(id: Long, enabled: Boolean) {
        webCalDao.updateSyncEnabled(id, enabled)
    }
    
    /**
     * Toggle visibility status.
     */
    suspend fun updateVisible(id: Long, visible: Boolean) {
        webCalDao.updateVisible(id, visible)
    }
    
    /**
     * Update subscription color.
     */
    suspend fun updateColor(id: Long, color: Int) {
        webCalDao.updateColor(id, color)
    }
    
    /**
     * Update refresh interval.
     */
    suspend fun updateRefreshInterval(id: Long, intervalMinutes: Int) {
        webCalDao.updateRefreshInterval(id, intervalMinutes)
    }
    
    /**
     * Get count of all subscriptions.
     */
    suspend fun getCount(): Int {
        return webCalDao.getCount()
    }
    
    /**
     * Get count of subscriptions for an account.
     */
    suspend fun getCountByAccount(accountId: Long): Int {
        return webCalDao.getCountByAccount(accountId)
    }
    
    /**
     * Delete all subscriptions for an account.
     */
    suspend fun deleteByAccountId(accountId: Long) {
        webCalDao.deleteByAccountId(accountId)
    }
    
    /**
     * Check if a subscription with given URL already exists for an account.
     */
    suspend fun urlExistsForAccount(url: String, accountId: Long): Boolean {
        val existing = getByUrl(url)
        return existing != null && existing.accountId == accountId
    }
}
