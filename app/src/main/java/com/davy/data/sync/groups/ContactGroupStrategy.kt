package com.davy.data.sync.groups

import com.davy.domain.model.Contact

/**
 * Strategy interface for handling contact groups during CardDAV synchronization.
 *
 * Two implementations exist:
 * - VCard4Strategy: Groups as separate vCard files (KIND:group)
 * - CategoriesStrategy: Groups as CATEGORIES property in contact vCards
 */
interface ContactGroupStrategy {
    
    /**
     * Called before uploading dirty contacts/groups.
     * Prepares group-related changes (e.g., marking affected contacts as dirty).
     *
     * @param addressBookId The ID of the address book being synced
     */
    suspend fun beforeUploadDirty(addressBookId: Long)
    
    /**
     * Validates a contact before saving to local storage.
     * Can modify or reject contacts based on group method compatibility.
     */
    fun verifyContactBeforeSaving(contact: Contact): Contact
    
    /**
     * Called after downloading all contacts and groups.
     * Applies pending group memberships and cleans up.
     *
     * @param addressBookId The ID of the address book being synced
     */
    suspend fun postProcess(addressBookId: Long)
}
