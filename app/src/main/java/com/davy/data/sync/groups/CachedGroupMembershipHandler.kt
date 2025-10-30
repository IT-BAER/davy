package com.davy.data.sync.groups

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import timber.log.Timber

/**
 * Reads group memberships from Android ContactsProvider.
 *
 * Caches group membership data from the Data table for a raw contact.
 * Used to determine which groups a contact belongs to when uploading to server.
 */
class CachedGroupMembershipHandler(
    private val contentResolver: ContentResolver
) {
    
    companion object {
        const val MIMETYPE = ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
    }
    
    private val groupMemberships = mutableSetOf<Long>() // Set of Android group IDs
    private val groupTitles = mutableSetOf<String>() // Set of group titles
    
    /**
     * Load group memberships for a raw contact.
     *
     * @param rawContactId The raw contact ID to load memberships for
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     */
    fun load(rawContactId: Long, androidAccountName: String, androidAccountType: String) {
        groupMemberships.clear()
        groupTitles.clear()
        
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
        )
        
        val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(rawContactId.toString(), MIMETYPE)
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            val groupRowIdIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
            
            while (it.moveToNext()) {
                val groupRowId = it.getLong(groupRowIdIdx)
                groupMemberships.add(groupRowId)
                
                // Look up group title
                val groupTitle = lookupGroupTitle(groupRowId, androidAccountName, androidAccountType)
                if (groupTitle != null) {
                    groupTitles.add(groupTitle)
                    Timber.d("Loaded group membership: $groupTitle (id=$groupRowId)")
                }
            }
        }
        
        Timber.d("Loaded ${groupMemberships.size} group memberships for rawContact=$rawContactId")
    }
    
    /**
     * Looks up the group title for a given group ID.
     *
     * @param groupId The Android group ID
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @return The group title, or null if not found
     */
    private fun lookupGroupTitle(groupId: Long, androidAccountName: String, androidAccountType: String): String? {
        val projection = arrayOf(
            ContactsContract.Groups.TITLE
        )
        
        val selection = "${ContactsContract.Groups._ID} = ? AND ${ContactsContract.Groups.ACCOUNT_NAME} = ? AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(groupId.toString(), androidAccountName, androidAccountType)
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val titleIdx = it.getColumnIndex(ContactsContract.Groups.TITLE)
                return it.getString(titleIdx)
            }
        }
        
        return null
    }
    
    /**
     * Get the set of group titles this contact belongs to.
     */
    fun getGroupTitles(): Set<String> = groupTitles.toSet()
    
    /**
     * Get the set of Android group IDs this contact belongs to.
     */
    fun getGroupIds(): Set<Long> = groupMemberships.toSet()
}
