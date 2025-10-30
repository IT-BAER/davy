package com.davy.data.sync.groups

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import com.davy.domain.model.Contact
import timber.log.Timber

/**
 * Handles group membership synchronization to Android ContactsProvider.
 *
 * Syncs contact's group memberships (categories) to Android's GroupMembership data rows.
 * Each category in Contact.categories becomes a GroupMembership row in the Data table.
 */
class GroupMembershipHandler {
    
    companion object {
        const val MIMETYPE = ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
    }
    
    /**
     * Builds group membership data rows for a contact.
     *
     * @param operations List to add ContentProviderOperations to
     * @param rawContactId The raw contact ID (if updating existing contact)
     * @param rawContactBackRef The back reference index (if inserting new contact)
     * @param contact The contact with categories to sync
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @param androidGroupIds Map of group titles to Android group IDs
     */
    fun handle(
        operations: ArrayList<ContentProviderOperation>,
        rawContactId: Long?,
        rawContactBackRef: Int?,
        contact: Contact,
        androidAccountName: String,
        androidAccountType: String,
        androidGroupIds: Map<String, Long>
    ) {
        if (contact.categories.isEmpty()) {
            Timber.d("No categories for contact ${contact.displayName}")
            return
        }
        
        Timber.d("Adding group memberships for contact ${contact.displayName}: ${contact.categories}")
        
        contact.categories.forEach { categoryTitle ->
            val groupId = androidGroupIds[categoryTitle]
            if (groupId == null) {
                Timber.w("Group '$categoryTitle' not found in Android - will be created during sync")
                return@forEach
            }
            
            val builder = if (rawContactId != null) {
                // Updating existing contact
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            } else {
                // Inserting new contact - use back reference
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactBackRef!!)
            }
            
            builder
                .withValue(ContactsContract.Data.MIMETYPE, MIMETYPE)
                .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
            
            operations.add(builder.build())
            Timber.d("  Added membership: $categoryTitle (groupId=$groupId)")
        }
    }
}
