package com.davy.data.sync.groups

import com.davy.data.repository.ContactRepository
import com.davy.domain.model.Contact
import timber.log.Timber
import javax.inject.Inject

/**
 * CATEGORIES group strategy.
 *
 * Groups are represented as CATEGORIES properties in each contact's vCard.
 * - Each contact lists its group memberships as categories
 * - No separate group vCards exist
 * - When group membership changes, contact must be marked dirty and re-uploaded
 * - Empty groups are automatically removed after sync
 *
 * Compatible with vCard 3.0 and 4.0.
 */
class CategoriesStrategy @Inject constructor(
    private val contactRepository: ContactRepository
) : ContactGroupStrategy {
    
    override suspend fun beforeUploadDirty(addressBookId: Long) {
        Timber.d("CategoriesStrategy: beforeUploadDirty - marking affected contacts as dirty")
        
        // For CATEGORIES method:
        // 1. Find groups that were deleted (isDeleted=true, isGroup=true)
        // 2. Find all contacts with those groups in categories
        // 3. Mark those contacts as dirty (they need categories updated)
        
        val allContacts = contactRepository.getByAddressBookId(addressBookId)
        val deletedGroups = allContacts.filter { it.isGroup && it.isDeleted }
        
        deletedGroups.forEach { group ->
            Timber.d("Group '${group.displayName}' deleted - finding members")
            
            // Find contacts with this group in categories
            val affectedContacts = allContacts
                .filter { !it.isGroup && !it.isDeleted && group.displayName in it.categories }
            
            affectedContacts.forEach { contact ->
                Timber.d("Marking contact '${contact.displayName}' dirty (member of deleted group)")
                
                // Remove category and mark dirty
                val updatedCategories = contact.categories - group.displayName
                val updatedContact = contact.copy(
                    categories = updatedCategories,
                    isDirty = true
                )
                contactRepository.update(updatedContact)
            }
        }
        
        // Similarly handle groups that were modified (isDirty=true, isGroup=true)
        val dirtyGroups = allContacts
            .filter { it.isGroup && it.isDirty && !it.isDeleted }
        
        dirtyGroups.forEach { group ->
            Timber.d("Group '${group.displayName}' modified - marking members as dirty")
            
            val members = allContacts
                .filter { !it.isGroup && !it.isDeleted && group.displayName in it.categories }
            
            members.forEach { contact ->
                Timber.d("Marking contact '${contact.displayName}' dirty (member of modified group)")
                contactRepository.update(contact.copy(isDirty = true))
            }
        }
    }
    
    override fun verifyContactBeforeSaving(contact: Contact): Contact {
        // If server sends a group vCard despite CATEGORIES method, convert it to regular contact
        return if (contact.isGroup || contact.groupMembers.isNotEmpty()) {
            Timber.w("Received group vCard with KIND:group, but using CATEGORIES method - converting to regular contact")
            contact.copy(
                isGroup = false,
                groupMembers = emptyList()
            )
        } else {
            contact
        }
    }
    
    override suspend fun postProcess(addressBookId: Long) {
        Timber.d("CategoriesStrategy: postProcess - removing empty groups")
        
        // Remove groups that have no members
        // In CATEGORIES method, groups are implicit - a group exists if any contact has it in categories
        // So we can safely delete group vCards that have no contacts referencing them
        
        val allContacts = contactRepository.getByAddressBookId(addressBookId)
        val allGroups = allContacts.filter { it.isGroup && !it.isDeleted }
        val activeCategories = allContacts
            .filter { !it.isGroup && !it.isDeleted }
            .flatMap { it.categories }
            .toSet()
        
        allGroups.forEach { group ->
            if (group.displayName !in activeCategories) {
                Timber.d("Removing empty group: ${group.displayName}")
                contactRepository.delete(group.id)
            }
        }
    }
}
