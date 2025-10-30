package com.davy.data.sync.groups

import com.davy.data.repository.ContactRepository
import com.davy.domain.model.Contact
import timber.log.Timber
import javax.inject.Inject

/**
 * GROUP_VCARDS (vCard 4.0) group strategy.
 *
 * Groups are represented as separate vCard files with KIND:group.
 * - Each group vCard contains a list of member UIDs
 * - Contacts don't have CATEGORIES - membership is in the group vCard
 * - When downloaded, member UIDs are stored temporarily and applied after all vcards synced
 * - When contact membership changes, the group must be marked dirty
 *
 * Requires vCard 4.0 for KIND:group support.
 */
class VCard4Strategy @Inject constructor(
    private val contactRepository: ContactRepository
) : ContactGroupStrategy {
    
    /**
     * Track pending memberships per address book: addressBookId -> (groupUid -> Set of member UIDs)
     * Populated during download, applied in postProcess
     */
    private val pendingMemberships = mutableMapOf<Long, MutableMap<String, Set<String>>>()
    
    override suspend fun beforeUploadDirty(addressBookId: Long) {
        Timber.d("VCard4Strategy: beforeUploadDirty - marking groups with changed memberships as dirty")
        
        // For GROUP_VCARDS method:
        // 1. Find dirty contacts
        // 2. Check if their group memberships changed (compare categories if stored)
        // 3. Mark affected groups as dirty
        
        val allContacts = contactRepository.getByAddressBookId(addressBookId)
        val dirtyContacts = allContacts
            .filter { it.isDirty && !it.isGroup && !it.isDeleted }
        
        dirtyContacts.forEach { contact ->
            // Find groups that list this contact as a member
            val groupsContainingContact = allContacts
                .filter { it.isGroup && !it.isDeleted && contact.uid in it.groupMembers }
            
            groupsContainingContact.forEach { group ->
                Timber.d("Contact '${contact.displayName}' changed - marking group '${group.displayName}' dirty")
                contactRepository.update(group.copy(isDirty = true))
            }
        }
    }
    
    override fun verifyContactBeforeSaving(contact: Contact): Contact {
        // Store pending memberships for groups
        if (contact.isGroup && contact.groupMembers.isNotEmpty()) {
            Timber.d("Storing pending memberships for group: ${contact.displayName} (${contact.groupMembers.size} members)")
            val addressBookMemberships = pendingMemberships.getOrPut(contact.addressBookId) { mutableMapOf() }
            addressBookMemberships[contact.uid] = contact.groupMembers.toSet()
        }
        
        // No modifications needed
        return contact
    }
    
    override suspend fun postProcess(addressBookId: Long) {
        Timber.d("VCard4Strategy: postProcess - applying pending group memberships for addressBook $addressBookId")
        
        val addressBookMemberships = pendingMemberships[addressBookId]
        if (addressBookMemberships.isNullOrEmpty()) {
            Timber.d("No pending memberships to apply for addressBook $addressBookId")
            return
        }
        
        // Apply pending memberships
        // For each group with pending members:
        // 1. Resolve member UIDs to actual contacts
        // 2. Update those contacts to include this group in their categories (for UI/Android display)
        
        val allContacts = contactRepository.getByAddressBookId(addressBookId)
        val contactsByUid = allContacts.filter { !it.isGroup }.associateBy { it.uid }
        
        addressBookMemberships.forEach { (groupUid, memberUids) ->
            val group = allContacts.find { it.uid == groupUid && it.isGroup }
            if (group == null) {
                Timber.w("Group with UID $groupUid not found in local database")
                return@forEach
            }
            
            Timber.d("Applying memberships for group '${group.displayName}': ${memberUids.size} members")
            
            memberUids.forEach { memberUid ->
                val contact = contactsByUid[memberUid]
                if (contact == null) {
                    Timber.w("Contact with UID $memberUid not found (member of group '${group.displayName}')")
                    return@forEach
                }
                
                // Add group name to contact's categories (for local display/Android sync)
                if (group.displayName !in contact.categories) {
                    Timber.d("Adding contact '${contact.displayName}' to group '${group.displayName}'")
                    val updatedContact = contact.copy(
                        categories = contact.categories + group.displayName
                    )
                    contactRepository.update(updatedContact)
                }
            }
        }
        
        // Clear pending memberships for this address book
        pendingMemberships.remove(addressBookId)
        
        Timber.d("Finished applying pending group memberships for addressBook $addressBookId")
    }
}
