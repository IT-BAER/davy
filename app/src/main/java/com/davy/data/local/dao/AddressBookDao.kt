package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.AddressBookEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for address book entities.
 * 
 * Provides CRUD operations and queries for address books.
 */
@Dao
interface AddressBookDao {
    
    /**
     * Inserts address book.
     * 
     * @param addressBook Address book to insert
     * @return ID of inserted address book
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addressBook: AddressBookEntity): Long
    
    /**
     * Inserts multiple address books.
     * 
     * @param addressBooks Address books to insert
     * @return IDs of inserted address books
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(addressBooks: List<AddressBookEntity>): List<Long>
    
    /**
     * Updates address book.
     * 
     * @param addressBook Address book to update
     */
    @Update
    suspend fun update(addressBook: AddressBookEntity)
    
    /**
     * Deletes address book.
     * 
     * @param id Address book ID
     */
    @Query("DELETE FROM address_books WHERE id = :id")
    suspend fun delete(id: Long)
    
    /**
     * Gets address book by ID.
     * 
     * @param id Address book ID
     * @return Address book or null
     */
    @Query("SELECT * FROM address_books WHERE id = :id")
    suspend fun getById(id: Long): AddressBookEntity?
    
    /**
     * Gets address book by URL.
     * 
     * @param url Address book URL
     * @return Address book or null
     */
    @Query("SELECT * FROM address_books WHERE url = :url")
    suspend fun getByUrl(url: String): AddressBookEntity?
    
    /**
     * Gets all address books as Flow.
     * 
     * @return Flow of address book list
     */
    @Query("SELECT * FROM address_books ORDER BY display_name ASC")
    fun getAllFlow(): Flow<List<AddressBookEntity>>
    
    /**
     * Gets all address books.
     * 
     * @return List of address books
     */
    @Query("SELECT * FROM address_books ORDER BY display_name ASC")
    suspend fun getAll(): List<AddressBookEntity>
    
    /**
     * Gets address books for account as Flow.
     * 
     * @param accountId Account ID
     * @return Flow of address book list
     */
    @Query("SELECT * FROM address_books WHERE account_id = :accountId ORDER BY display_name ASC")
    fun getByAccountIdFlow(accountId: Long): Flow<List<AddressBookEntity>>
    
    /**
     * Gets address books for account.
     * 
     * @param accountId Account ID
     * @return List of address books
     */
    @Query("SELECT * FROM address_books WHERE account_id = :accountId ORDER BY display_name ASC")
    suspend fun getByAccountId(accountId: Long): List<AddressBookEntity>
    
    /**
     * Gets visible address books.
     * 
     * @return List of visible address books
     */
    @Query("SELECT * FROM address_books WHERE visible = 1 ORDER BY display_name ASC")
    suspend fun getVisible(): List<AddressBookEntity>
    
    /**
     * Gets sync-enabled address books.
     * 
     * @return List of sync-enabled address books
     */
    @Query("SELECT * FROM address_books WHERE sync_enabled = 1 ORDER BY display_name ASC")
    suspend fun getSyncEnabled(): List<AddressBookEntity>
    
    /**
     * Updates CTag and lastSynced for address book.
     * 
     * @param id Address book ID
     * @param ctag New CTag
     * @param updatedAt Updated timestamp
     * @param lastSynced Last sync timestamp
     */
    @Query("UPDATE address_books SET ctag = :ctag, updated_at = :updatedAt, last_synced = :lastSynced WHERE id = :id")
    suspend fun updateCTag(
        id: Long, 
        ctag: String, 
        updatedAt: Long = System.currentTimeMillis(),
        lastSynced: Long = System.currentTimeMillis()
    )
    
    /**
     * Updates only lastSynced timestamp for address book.
     * Called when sync completes but no data changes were detected.
     * 
     * @param id Address book ID
     * @param lastSynced Last sync timestamp
     */
    @Query("UPDATE address_books SET last_synced = :lastSynced WHERE id = :id")
    suspend fun updateLastSynced(
        id: Long, 
        lastSynced: Long = System.currentTimeMillis()
    )
    
    /**
     * Counts address books for account.
     * 
     * @param accountId Account ID
     * @return Count of address books
     */
    @Query("SELECT COUNT(*) FROM address_books WHERE account_id = :accountId")
    suspend fun countByAccountId(accountId: Long): Int
    
    /**
     * Deletes all address books for account.
     * 
     * @param accountId Account ID
     */
    @Query("DELETE FROM address_books WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
    
    /**
     * Deletes all address books.
     */
    @Query("DELETE FROM address_books")
    suspend fun deleteAll()
}
