package com.davy.data.local.dao

import androidx.room.*
import com.davy.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for contact entities.
 * 
 * Provides CRUD operations and queries for contacts.
 */
@Dao
interface ContactDao {
    
    /**
     * Inserts contact.
     * 
     * @param contact Contact to insert
     * @return ID of inserted contact
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long
    
    /**
     * Inserts multiple contacts.
     * 
     * @param contacts Contacts to insert
     * @return IDs of inserted contacts
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>): List<Long>
    
    /**
     * Updates contact.
     * 
     * @param contact Contact to update
     */
    @Update
    suspend fun update(contact: ContactEntity)
    
    /**
     * Deletes contact.
     * 
     * @param id Contact ID
     */
    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: Long)
    
    /**
     * Gets contact by ID.
     * 
     * @param id Contact ID
     * @return Contact or null
     */
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?
    
    /**
     * Gets contact by URL.
     * 
     * @param url Contact URL
     * @return Contact or null
     */
    @Query("SELECT * FROM contacts WHERE contact_url = :url")
    suspend fun getByUrl(url: String): ContactEntity?
    
    /**
     * Gets contact by UID.
     * 
     * @param uid Contact UID
     * @return Contact or null
     */
    @Query("SELECT * FROM contacts WHERE uid = :uid")
    suspend fun getByUid(uid: String): ContactEntity?
    
    /**
     * Gets contact by Android raw contact ID.
     * 
     * @param rawContactId Android raw contact ID
     * @return Contact or null
     */
    @Query("SELECT * FROM contacts WHERE android_raw_contact_id = :rawContactId")
    suspend fun getByAndroidRawContactId(rawContactId: Long): ContactEntity?
    
    /**
     * Gets contacts for address book as Flow.
     * 
     * @param addressBookId Address book ID
     * @return Flow of contact list
     */
    @Query("SELECT * FROM contacts WHERE address_book_id = :addressBookId AND is_deleted = 0 ORDER BY display_name ASC")
    fun getByAddressBookIdFlow(addressBookId: Long): Flow<List<ContactEntity>>
    
    /**
     * Gets contacts for address book.
     * 
     * @param addressBookId Address book ID
     * @return List of contacts
     */
    @Query("SELECT * FROM contacts WHERE address_book_id = :addressBookId AND is_deleted = 0 ORDER BY display_name ASC")
    suspend fun getByAddressBookId(addressBookId: Long): List<ContactEntity>
    
    /**
     * Searches contacts by name or email.
     * 
     * @param query Search query
     * @return List of matching contacts
     */
    @Query("""
        SELECT * FROM contacts 
        WHERE is_deleted = 0 
        AND (
            display_name LIKE '%' || :query || '%' 
            OR given_name LIKE '%' || :query || '%'
            OR family_name LIKE '%' || :query || '%'
            OR emails LIKE '%' || :query || '%'
            OR organization LIKE '%' || :query || '%'
        )
        ORDER BY display_name ASC
    """)
    suspend fun search(query: String): List<ContactEntity>
    
    /**
     * Gets dirty contacts (have local changes).
     * 
     * @return List of dirty contacts
     */
    @Query("SELECT * FROM contacts WHERE is_dirty = 1 AND is_deleted = 0 ORDER BY updated_at DESC")
    suspend fun getDirtyContacts(): List<ContactEntity>
    
    /**
     * Gets deleted contacts (marked for deletion).
     * 
     * @return List of deleted contacts
     */
    @Query("SELECT * FROM contacts WHERE is_deleted = 1 ORDER BY updated_at DESC")
    suspend fun getDeletedContacts(): List<ContactEntity>
    
    /**
     * Marks contact as dirty (has local changes).
     * 
     * @param id Contact ID
     */
    @Query("UPDATE contacts SET is_dirty = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun markDirty(id: Long, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * Marks contact as clean (synced).
     * 
     * @param id Contact ID
     */
    @Query("UPDATE contacts SET is_dirty = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun markClean(id: Long, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * Soft deletes contact (marks for deletion).
     * 
     * @param id Contact ID
     */
    @Query("UPDATE contacts SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * Updates contact ETag.
     * 
     * @param id Contact ID
     * @param etag New ETag
     */
    @Query("UPDATE contacts SET etag = :etag, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateETag(id: Long, etag: String, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * Updates Android contact IDs.
     * 
     * @param id Contact ID
     * @param androidContactId Android contact ID
     * @param androidRawContactId Android raw contact ID
     */
    @Query("UPDATE contacts SET android_contact_id = :androidContactId, android_raw_contact_id = :androidRawContactId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateAndroidContactIds(
        id: Long,
        androidContactId: Long,
        androidRawContactId: Long,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Counts contacts for address book.
     * 
     * @param addressBookId Address book ID
     * @return Count of contacts
     */
    @Query("SELECT COUNT(*) FROM contacts WHERE address_book_id = :addressBookId AND is_deleted = 0")
    suspend fun countByAddressBookId(addressBookId: Long): Int
    
    /**
     * Deletes all contacts for address book.
     * 
     * @param addressBookId Address book ID
     */
    @Query("DELETE FROM contacts WHERE address_book_id = :addressBookId")
    suspend fun deleteByAddressBookId(addressBookId: Long)
    
    /**
     * Purges soft-deleted contacts (permanently deletes).
     * 
     * @param olderThan Delete contacts marked as deleted before this timestamp
     */
    @Query("DELETE FROM contacts WHERE is_deleted = 1 AND updated_at < :olderThan")
    suspend fun purgeSoftDeleted(olderThan: Long = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) // 7 days
    
    /**
     * Deletes all contacts.
     */
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}
