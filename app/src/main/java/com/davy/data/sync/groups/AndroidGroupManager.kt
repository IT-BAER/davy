package com.davy.data.sync.groups

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Manages Android contact groups in ContactsProvider.
 *
 * Handles group creation, lookup, and synchronization with Android's Groups table.
 * Maps group titles to Android group IDs for membership tracking.
 */
class AndroidGroupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver get() = context.contentResolver
    
    /**
     * Gets or creates a map of group titles to Android group IDs for an account.
     *
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @return Map of group title -> Android group ID
     */
    fun getGroupIdMap(androidAccountName: String, androidAccountType: String): Map<String, Long> {
        val groupMap = mutableMapOf<String, Long>()
        
        val projection = arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE
        )
        
        val selection = "${ContactsContract.Groups.ACCOUNT_NAME} = ? AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(androidAccountName, androidAccountType)
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.Groups._ID)
            val titleIdx = it.getColumnIndex(ContactsContract.Groups.TITLE)
            
            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                val title = it.getString(titleIdx)
                if (title != null) {
                    groupMap[title] = id
                }
            }
        }
        
        Timber.d("Found ${groupMap.size} existing groups for account $androidAccountName")
        return groupMap
    }
    
    /**
     * Finds or creates an Android group with the given title.
     *
     * @param title The group title
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @return The Android group ID, or null if creation failed
     */
    fun findOrCreateGroup(title: String, androidAccountName: String, androidAccountType: String): Long? {
        // First try to find existing group
        val existing = findGroupByTitle(title, androidAccountName, androidAccountType)
        if (existing != null) {
            Timber.d("Found existing group: $title (id=$existing)")
            return existing
        }
        
        // Create new group
        val values = ContentValues().apply {
            put(ContactsContract.Groups.TITLE, title)
            put(ContactsContract.Groups.ACCOUNT_NAME, androidAccountName)
            put(ContactsContract.Groups.ACCOUNT_TYPE, androidAccountType)
            put(ContactsContract.Groups.GROUP_VISIBLE, 1)
        }
        
        val uri = syncAdapterUri(
            ContactsContract.Groups.CONTENT_URI,
            androidAccountName,
            androidAccountType
        )
        
        val insertedUri = contentResolver.insert(uri, values)
        if (insertedUri != null) {
            val groupId = ContentUris.parseId(insertedUri)
            Timber.d("Created new group: $title (id=$groupId)")
            return groupId
        }
        
        Timber.e("Failed to create group: $title")
        return null
    }
    
    /**
     * Finds a group by title.
     *
     * @param title The group title
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @return The Android group ID, or null if not found
     */
    private fun findGroupByTitle(title: String, androidAccountName: String, androidAccountType: String): Long? {
        val projection = arrayOf(ContactsContract.Groups._ID)
        
        val selection = "${ContactsContract.Groups.TITLE} = ? AND ${ContactsContract.Groups.ACCOUNT_NAME} = ? AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(title, androidAccountName, androidAccountType)
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val idIdx = it.getColumnIndex(ContactsContract.Groups._ID)
                return it.getLong(idIdx)
            }
        }
        
        return null
    }
    
    /**
     * Ensures all groups from the given set exist in Android.
     *
     * @param groupTitles Set of group titles to ensure exist
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @return Updated map of group title -> Android group ID
     */
    fun ensureGroupsExist(
        groupTitles: Set<String>,
        androidAccountName: String,
        androidAccountType: String
    ): Map<String, Long> {
        val groupMap = getGroupIdMap(androidAccountName, androidAccountType).toMutableMap()
        
        groupTitles.forEach { title ->
            if (!groupMap.containsKey(title)) {
                val groupId = findOrCreateGroup(title, androidAccountName, androidAccountType)
                if (groupId != null) {
                    groupMap[title] = groupId
                }
            }
        }
        
        return groupMap
    }
    
    /**
     * Removes empty groups (groups with no members).
     *
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     * @return Number of groups removed
     */
    fun removeEmptyGroups(androidAccountName: String, androidAccountType: String): Int {
        var removed = 0
        val groupMap = getGroupIdMap(androidAccountName, androidAccountType)
        
        Timber.d("removeEmptyGroups: Found ${groupMap.size} groups to check")
        
        groupMap.forEach { (title, groupId) ->
            val memberCount = getGroupMemberCount(groupId)
            Timber.d("removeEmptyGroups: Group '$title' (id=$groupId) has $memberCount members")
            if (memberCount == 0) {
                Timber.d("Removing empty group: $title (id=$groupId)")
                deleteGroup(groupId, androidAccountName, androidAccountType)
                removed++
            }
        }
        
        if (removed > 0) {
            Timber.d("Removed $removed empty groups")
        } else {
            Timber.d("No empty groups found to remove")
        }
        
        return removed
    }
    
    /**
     * Gets the number of members in a group.
     *
     * @param groupId The Android group ID
     * @return The number of members
     */
    private fun getGroupMemberCount(groupId: Long): Int {
        val projection = arrayOf(ContactsContract.Data._ID)
        
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            groupId.toString()
        )
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            return it.count
        }
        
        return 0
    }
    
    /**
     * Deletes a group.
     *
     * @param groupId The Android group ID
     * @param androidAccountName The Android account name
     * @param androidAccountType The Android account type
     */
    private fun deleteGroup(groupId: Long, androidAccountName: String, androidAccountType: String) {
        val uri = syncAdapterUri(
            ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupId),
            androidAccountName,
            androidAccountType
        )
        
        contentResolver.delete(uri, null, null)
    }
    
    /**
     * Creates a sync adapter URI for the given base URI.
     */
    private fun syncAdapterUri(uri: Uri, accountName: String, accountType: String): Uri {
        return uri.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            .build()
    }
}
