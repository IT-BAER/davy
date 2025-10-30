package com.davy.data.repository

import com.davy.data.local.dao.ContactDao
import com.davy.data.local.entity.ContactEntity
import com.davy.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

/**
 * Repository for contact operations.
 * 
 * Handles CRUD operations and provides reactive data streams for contacts.
 */
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {
    
    private val gson = Gson()
    
    /**
     * Insert a new contact.
     */
    suspend fun insert(contact: Contact): Long {
        return contactDao.insert(contact.toEntity())
    }
    
    /**
     * Insert multiple contacts.
     */
    suspend fun insertAll(contacts: List<Contact>): List<Long> {
        return contactDao.insertAll(contacts.map { it.toEntity() })
    }
    
    /**
     * Update an existing contact.
     */
    suspend fun update(contact: Contact) {
        contactDao.update(contact.toEntity())
    }
    
    /**
     * Delete a contact.
     */
    suspend fun delete(id: Long) {
        contactDao.delete(id)
    }
    
    /**
     * Get contact by ID.
     */
    suspend fun getById(id: Long): Contact? {
        return contactDao.getById(id)?.toDomain()
    }
    
    /**
     * Get contact by URL.
     */
    suspend fun getByUrl(url: String): Contact? {
        return contactDao.getByUrl(url)?.toDomain()
    }
    
    /**
     * Get contact by UID.
     */
    suspend fun getByUid(uid: String): Contact? {
        return contactDao.getByUid(uid)?.toDomain()
    }
    
    /**
     * Get all contacts for an address book as Flow.
     */
    fun getByAddressBookIdFlow(addressBookId: Long): Flow<List<Contact>> {
        return contactDao.getByAddressBookIdFlow(addressBookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all contacts for an address book.
     */
    suspend fun getByAddressBookId(addressBookId: Long): List<Contact> {
        return contactDao.getByAddressBookId(addressBookId).map { it.toDomain() }
    }
    
    /**
     * Get contact by Android raw contact ID.
     */
    suspend fun getByAndroidRawContactId(rawContactId: Long): Contact? {
        return contactDao.getByAndroidRawContactId(rawContactId)?.toDomain()
    }
    
    /**
     * Get contact by SOURCE_ID (filename) and address book ID.
     */
    suspend fun getBySourceId(sourceId: String, addressBookId: Long): Contact? {
        // SOURCE_ID is the filename (e.g., "UUID.vcf")
        // We need to find the contact with this filename in the contact_url
        val contacts = contactDao.getByAddressBookId(addressBookId)
        return contacts.firstOrNull { contact ->
            contact.contactUrl?.substringAfterLast("/") == sourceId
        }?.toDomain()
    }
    
    /**
     * Get all dirty contacts.
     */
    suspend fun getDirtyContacts(): List<Contact> {
        return contactDao.getDirtyContacts().map { it.toDomain() }
    }
    
    /**
     * Get all deleted contacts.
     */
    suspend fun getDeletedContacts(): List<Contact> {
        return contactDao.getDeletedContacts().map { it.toDomain() }
    }
    
    /**
     * Search contacts by name or email.
     */
    suspend fun searchByName(query: String): List<Contact> {
        return contactDao.search(query).map { it.toDomain() }
    }
    
    /**
     * Mark contact as dirty.
     */
    suspend fun markDirty(id: Long) {
        contactDao.markDirty(id)
    }
    
    /**
     * Mark contact as clean.
     */
    suspend fun markClean(id: Long) {
        contactDao.markClean(id)
    }
    
    /**
     * Soft delete a contact.
     */
    suspend fun softDelete(id: Long) {
        contactDao.softDelete(id)
    }
    
    /**
     * Update etag.
     */
    suspend fun updateEtag(id: Long, etag: String) {
        contactDao.updateETag(id, etag)
    }
    
    /**
     * Delete all contacts for address book.
     */
    suspend fun deleteByAddressBookId(addressBookId: Long) {
        contactDao.deleteByAddressBookId(addressBookId)
    }
    
    /**
     * Delete a contact (hard delete).
     */
    suspend fun delete(contact: Contact) {
        contactDao.delete(contact.id)
    }
    
    /**
     * Update Android contact IDs.
     */
    suspend fun updateAndroidContactIds(id: Long, contactId: Long, rawContactId: Long) {
        contactDao.updateAndroidContactIds(id, contactId, rawContactId)
    }
    
    /**
     * Purge soft-deleted contacts older than retention period.
     */
    suspend fun purgeSoftDeleted(retentionDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        contactDao.purgeSoftDeleted(cutoffTime)
    }
    
    /**
     * Convert Contact domain model to ContactEntity.
     */
    private fun Contact.toEntity(): ContactEntity {
        return ContactEntity(
            id = id,
            addressBookId = addressBookId,
            uid = uid,
            contactUrl = contactUrl,
            etag = etag,
            displayName = displayName,
            givenName = givenName,
            familyName = familyName,
            middleName = middleName,
            namePrefix = namePrefix,
            nameSuffix = nameSuffix,
            nickname = nickname,
            organization = organization,
            organizationUnit = organizationUnit,
            jobTitle = jobTitle,
            phoneNumbers = if (phoneNumbers.isNotEmpty()) gson.toJson(phoneNumbers) else null,
            emails = if (emails.isNotEmpty()) gson.toJson(emails) else null,
            postalAddresses = if (postalAddresses.isNotEmpty()) gson.toJson(postalAddresses) else null,
            websites = if (websites.isNotEmpty()) gson.toJson(websites) else null,
            note = note,
            birthday = birthday,
            anniversary = anniversary,
            photoBase64 = photoBase64,
            categories = if (categories.isNotEmpty()) gson.toJson(categories) else null,
            isGroup = isGroup,
            groupMembers = if (groupMembers.isNotEmpty()) gson.toJson(groupMembers) else null,
            isDirty = isDirty,
            isDeleted = isDeleted,
            androidContactId = androidContactId,
            androidRawContactId = androidRawContactId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * Convert ContactEntity to Contact domain model.
     */
    private fun ContactEntity.toDomain(): Contact {
        val phoneNumbersList = phoneNumbers?.let {
            val type = object : TypeToken<List<PhoneNumber>>() {}.type
            gson.fromJson<List<PhoneNumber>>(it, type)
        } ?: emptyList()
        
        val emailsList = emails?.let {
            val type = object : TypeToken<List<Email>>() {}.type
            gson.fromJson<List<Email>>(it, type)
        } ?: emptyList()
        
        val postalAddressesList = postalAddresses?.let {
            val type = object : TypeToken<List<PostalAddress>>() {}.type
            gson.fromJson<List<PostalAddress>>(it, type)
        } ?: emptyList()
        
        val websitesList = websites?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(it, type)
        } ?: emptyList()
        
        val categoriesList = categories?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(it, type)
        } ?: emptyList()
        
        val groupMembersList = groupMembers?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(it, type)
        } ?: emptyList()
        
        return Contact(
            id = id,
            addressBookId = addressBookId,
            uid = uid,
            contactUrl = contactUrl,
            etag = etag,
            displayName = displayName,
            givenName = givenName,
            familyName = familyName,
            middleName = middleName,
            namePrefix = namePrefix,
            nameSuffix = nameSuffix,
            nickname = nickname,
            organization = organization,
            organizationUnit = organizationUnit,
            jobTitle = jobTitle,
            phoneNumbers = phoneNumbersList,
            emails = emailsList,
            postalAddresses = postalAddressesList,
            websites = websitesList,
            note = note,
            birthday = birthday,
            anniversary = anniversary,
            photoBase64 = photoBase64,
            categories = categoriesList,
            isGroup = isGroup,
            groupMembers = groupMembersList,
            isDirty = isDirty,
            isDeleted = isDeleted,
            androidContactId = androidContactId,
            androidRawContactId = androidRawContactId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
