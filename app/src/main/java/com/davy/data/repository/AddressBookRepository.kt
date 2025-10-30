package com.davy.data.repository

import com.davy.data.local.dao.AddressBookDao
import com.davy.data.local.entity.AddressBookEntity
import com.davy.domain.model.AddressBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository for address book operations.
 * 
 * Handles CRUD operations and provides reactive data streams
 * for address books.
 */
class AddressBookRepository @Inject constructor(
    private val addressBookDao: AddressBookDao
) {
    
    /**
     * Insert a new address book.
     * 
     * @return The ID of the inserted address book
     */
    suspend fun insert(addressBook: AddressBook): Long {
        return addressBookDao.insert(addressBook.toEntity())
    }
    
    /**
     * Insert multiple address books.
     * 
     * @return List of IDs of inserted address books
     */
    suspend fun insertAll(addressBooks: List<AddressBook>): List<Long> {
        return addressBookDao.insertAll(addressBooks.map { it.toEntity() })
    }
    
    /**
     * Update an existing address book.
     */
    suspend fun update(addressBook: AddressBook) {
        addressBookDao.update(addressBook.toEntity())
    }
    
    /**
     * Delete an address book.
     */
    suspend fun delete(addressBook: AddressBook) {
        addressBookDao.delete(addressBook.id)
    }
    
    /**
     * Get address book by ID.
     */
    suspend fun getById(id: Long): AddressBook? {
        return addressBookDao.getById(id)?.toDomain()
    }
    
    /**
     * Get address book by URL.
     */
    suspend fun getByUrl(url: String): AddressBook? {
        return addressBookDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get all address books as Flow.
     */
    fun getAllFlow(): Flow<List<AddressBook>> {
        return addressBookDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all address books.
     */
    suspend fun getAll(): List<AddressBook> {
        return addressBookDao.getAll().map { it.toDomain() }
    }
    
    /**
     * Get all address books for an account as Flow.
     */
    fun getByAccountIdFlow(accountId: Long): Flow<List<AddressBook>> {
        return addressBookDao.getByAccountIdFlow(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all address books for an account.
     */
    suspend fun getByAccountId(accountId: Long): List<AddressBook> {
        return addressBookDao.getByAccountId(accountId).map { it.toDomain() }
    }
    
    /**
     * Get all visible address books.
     */
    suspend fun getVisible(): List<AddressBook> {
        return addressBookDao.getVisible().map { it.toDomain() }
    }
    
    /**
     * Get all sync-enabled address books.
     */
    suspend fun getSyncEnabled(): List<AddressBook> {
        return addressBookDao.getSyncEnabled().map { it.toDomain() }
    }
    
    /**
     * Update CTag and lastSynced for an address book after successful sync.
     */
    suspend fun updateCTag(id: Long, ctag: String) {
        val timestamp = System.currentTimeMillis()
        Timber.d("📊 AddressBookRepository.updateCTag: id=$id, ctag=$ctag, timestamp=$timestamp")
        Timber.d("📊 About to update address_books SET ctag='$ctag', updated_at=$timestamp, last_synced=$timestamp WHERE id=$id")
        addressBookDao.updateCTag(id, ctag, updatedAt = timestamp, lastSynced = timestamp)
        Timber.d("📊 ✓ Updated address book $id with lastSynced=$timestamp")
        
        // Verify the update actually happened
        val updated = addressBookDao.getById(id)
        Timber.d("📊 Verification: address book $id now has lastSynced=${updated?.lastSynced}")
    }
    
    /**
     * Update only lastSynced timestamp for an address book.
     * Called when sync completes but no data changes were detected.
     */
    suspend fun updateLastSynced(id: Long) {
        val timestamp = System.currentTimeMillis()
        Timber.d("📊 AddressBookRepository.updateLastSynced: id=$id, timestamp=$timestamp")
        addressBookDao.updateLastSynced(id, lastSynced = timestamp)
        Timber.d("📊 ✓ Updated address book $id with lastSynced=$timestamp")
        
        // Verify the update actually happened
        val updated = addressBookDao.getById(id)
        Timber.d("📊 Verification: address book $id now has lastSynced=${updated?.lastSynced}")
    }
    
    /**
     * Get count of address books for an account.
     */
    suspend fun countByAccountId(accountId: Long): Int {
        return addressBookDao.countByAccountId(accountId)
    }
    
    /**
     * Delete all address books for an account.
     */
    suspend fun deleteByAccountId(accountId: Long) {
        addressBookDao.deleteByAccountId(accountId)
    }
    
    /**
     * Delete all address books.
     */
    suspend fun deleteAll() {
        addressBookDao.deleteAll()
    }
    
    /**
     * Update addressbook displayname.
     */
    suspend fun updateDisplayName(id: Long, displayName: String) {
        val addressBook = addressBookDao.getById(id) ?: return
        addressBookDao.update(addressBook.copy(displayName = displayName))
    }
    
    /**
     * Convert AddressBook domain model to AddressBookEntity.
     */
    private fun AddressBook.toEntity(): AddressBookEntity {
        return AddressBookEntity(
            id = id,
            accountId = accountId,
            url = url,
            displayName = displayName,
            description = description,
            color = color,
            ctag = ctag,
            syncEnabled = syncEnabled,
            visible = visible,
            androidAccountName = androidAccountName,
            owner = owner,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastSynced = lastSynced,
            privWriteContent = privWriteContent,
            forceReadOnly = forceReadOnly,
            wifiOnlySync = wifiOnlySync,
            syncIntervalMinutes = syncIntervalMinutes
        )
    }
    
    /**
     * Convert AddressBookEntity to AddressBook domain model.
     */
    private fun AddressBookEntity.toDomain(): AddressBook {
        return AddressBook(
            id = id,
            accountId = accountId,
            url = url,
            displayName = displayName,
            description = description,
            color = color,
            ctag = ctag,
            syncEnabled = syncEnabled,
            visible = visible,
            androidAccountName = androidAccountName,
            owner = owner,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastSynced = lastSynced,
            privWriteContent = privWriteContent,
            forceReadOnly = forceReadOnly,
            wifiOnlySync = wifiOnlySync,
            syncIntervalMinutes = syncIntervalMinutes
        )
    }
}
