package com.davy.data.contentprovider

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.davy.domain.model.Contact
import com.davy.domain.model.Email
import com.davy.domain.model.PhoneNumber
import com.davy.domain.model.PostalAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber

/**
 * Adapter for Android ContactsContract ContentProvider.
 *
 * Provides bidirectional sync between:
 * - Contact domain model (from CardDAV/vCard)
 * - Android ContactsContract (system contacts)
 *
 * This class handles:
 * - Inserting new contacts into Android Contacts
 * - Updating existing contacts in Android Contacts
 * - Deleting contacts from Android Contacts
 * - Reading contacts from Android Contacts
 * - Mapping between domain model and ContactsContract data structures
 */
class ContactContentProviderAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val typeMapper: TypeMapper
) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Creates a sync adapter URI for the given base URI.
     * This marks operations as coming from a sync adapter, which is required for
     * contacts to be editable by the sync adapter and not appear as "read-only".
     */
    private fun syncAdapterUri(uri: Uri, accountName: String, accountType: String): Uri {
        return uri.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            .build()
    }

    /**
     * Checks if a given RawContact still exists (and is not marked deleted).
     */
    fun rawContactExists(rawContactId: Long): Boolean {
        return try {
            val cursor = contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts._ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
                arrayOf(rawContactId.toString()),
                null
            )
            cursor?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes all contacts for a given Android account from the Contacts provider.
     * Uses the sync-adapter URI to ensure proper permissions and that only
     * contacts for the specified account are removed.
     *
     * @param androidAccountName The account name to delete contacts for
     * @param androidAccountType The account type (e.g., AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK)
     * @return number of raw contacts deleted
     */
    fun deleteAllContactsForAccount(
        androidAccountName: String,
        androidAccountType: String
    ): Int {
        return try {
            val uri = syncAdapterUri(
                ContactsContract.RawContacts.CONTENT_URI,
                androidAccountName,
                androidAccountType
            )
            // Passing null selection deletes all raw contacts for this account when using sync-adapter URI
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Clears the DIRTY flag for contacts that were synced from the server.
     * This is critical: contacts inserted/updated from the server must have dirty=0,
     * otherwise Android treats them as locally modified and marks them read-only.
     *
     * Based on reference implementation's clearDirty() method in LocalContact.kt (line 87-104).
     *
     * @param rawContactId The raw contact ID to clear the dirty flag for
     * @param accountName The Android account name
     * @param accountType The Android account type
     */
    fun clearDirtyFlag(rawContactId: Long, accountName: String, accountType: String) {
        val uri = syncAdapterUri(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId),
            accountName,
            accountType
        )
        val values = android.content.ContentValues().apply {
            put(ContactsContract.RawContacts.DIRTY, 0)
        }
        contentResolver.update(uri, values, null, null)
    }

    /**
     * Result of a contact insert/update operation.
     *
     * @property contactId The Android ContactsContract.Contacts._ID
     * @property rawContactId The Android ContactsContract.RawContacts._ID
     * @property success Whether the operation was successful
     */
    data class SyncResult(
        val contactId: Long?,
        val rawContactId: Long?,
        val success: Boolean
    )

    /**
     * Inserts a new contact into Android Contacts.
     *
     * This creates:
     * 1. A RawContact entry linked to the account
     * 2. Data rows for all contact fields (name, phone, email, etc.)
     *
     * @param contact The contact to insert
     * @param androidAccountName The Android account name (e.g., "user@example.com")
     * @param androidAccountType The Android account type (e.g., "com.davy.carddav")
     * @return SyncResult with the new contact/raw contact IDs
     */
    fun insertContact(
        contact: Contact,
        androidAccountName: String,
        androidAccountType: String
    ): SyncResult {
        val operations = ArrayList<ContentProviderOperation>()

        // Use sync adapter URI for all operations to mark as sync adapter
        val rawContactInsertUri = syncAdapterUri(
            ContactsContract.RawContacts.CONTENT_URI,
            androidAccountName,
            androidAccountType
        )

        // Insert RawContact
        // Match reference implementation structure:
        // - SOURCE_ID = filename (e.g., "UUID.vcf")
        // - SYNC1 = UID
        // - SYNC2 = ETag (without quotes - already stripped in AddressBookQuery)
        // - SYNC3 = NULL
        // - SYNC4 = 1 (reference implementation sets this - appears to mark contact as editable)
        val filename = contact.contactUrl?.substringAfterLast('/') ?: "${contact.uid}.vcf"
        
        operations.add(
            ContentProviderOperation.newInsert(rawContactInsertUri)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, androidAccountName)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, androidAccountType)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, filename) // Filename, not UID!
                // Store metadata in SYNC columns (matching reference implementation structure)
                .withValue(ContactsContract.RawContacts.SYNC1, contact.uid) // UID
                .withValue(ContactsContract.RawContacts.SYNC2, contact.etag) // ETag (already clean)
                // SYNC3 stays NULL (reference implementation doesn't use it)
                .withValue(ContactsContract.RawContacts.SYNC4, 1) // reference implementation sets this to 1
                // CRITICAL: Set raw contact as NOT read-only (0 = editable, 1 = read-only)
                .withValue("raw_contact_is_read_only", 0)
                .build()
        )

        val rawContactIndex = 0

        // Add data rows
        addNameData(operations, rawContactIndex, contact)
        addNicknameData(operations, rawContactIndex, contact)
        addOrganizationData(operations, rawContactIndex, contact)
        addPhoneData(operations, rawContactIndex, contact)
        addEmailData(operations, rawContactIndex, contact)
        addAddressData(operations, rawContactIndex, contact)
        addWebsiteData(operations, rawContactIndex, contact)
        addNoteData(operations, rawContactIndex, contact)
        addBirthdayData(operations, rawContactIndex, contact)
        addAnniversaryData(operations, rawContactIndex, contact)
        addPhotoData(operations, rawContactIndex, contact)

        return try {
            Timber.tag("ContactContentProviderAdapter").d("Inserting contact for account: %s (%s)", androidAccountName, androidAccountType)
            Timber.tag("ContactContentProviderAdapter").d("Contact: %s, UID: %s", contact.displayName, contact.uid)
            
            // Execute batch operation
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)

            // Extract RawContact URI from first result
            val rawContactInsertResultUri = results[0].uri
            if (rawContactInsertResultUri == null) {
                Timber.tag("ContactContentProviderAdapter").e("Failed to insert contact: RawContact URI is null")
                return SyncResult(null, null, false)
            }

            val rawContactId = ContentUris.parseId(rawContactInsertResultUri)
            Timber.tag("ContactContentProviderAdapter").d("RawContact inserted with ID: %s", rawContactId)

            // Query for Contact ID
            val contactId = queryContactId(rawContactId)
            Timber.tag("ContactContentProviderAdapter").d("Contact ID: %s", contactId)

            SyncResult(contactId, rawContactId, true)
        } catch (e: Exception) {
            Timber.tag("ContactContentProviderAdapter").e(e, "Failed to insert contact: %s", e.message)
            e.printStackTrace()
            SyncResult(null, null, false)
        }
    }

    /**
     * Updates an existing contact in Android Contacts.
     *
     * This:
     * 1. Deletes all existing data rows for the RawContact
     * 2. Inserts new data rows with updated values
     *
     * @param contact The contact with updated data
     * @param rawContactId The existing RawContact ID
     * @return SyncResult with success status
     */
    fun updateContact(
        contact: Contact,
        rawContactId: Long,
        androidAccountName: String,
        androidAccountType: String
    ): SyncResult {
        val operations = ArrayList<ContentProviderOperation>()

        // Use sync adapter URI for delete operation
        val dataUri = syncAdapterUri(
            ContactsContract.Data.CONTENT_URI,
            androidAccountName,
            androidAccountType
        )

        // Delete existing data rows (except photo)
        operations.add(
            ContentProviderOperation.newDelete(dataUri)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} != ?",
                    arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                )
                .build()
        )

        // Update RawContact SYNC columns with new metadata (matching reference implementation structure)
        val filename = contact.contactUrl?.substringAfterLast('/') ?: "${contact.uid}.vcf"
        
        val rawContactUpdateUri = syncAdapterUri(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId),
            androidAccountName,
            androidAccountType
        )
        operations.add(
            ContentProviderOperation.newUpdate(rawContactUpdateUri)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, filename) // Filename
                .withValue(ContactsContract.RawContacts.SYNC1, contact.uid) // UID
                .withValue(ContactsContract.RawContacts.SYNC2, contact.etag) // ETag (already clean)
                .withValue(ContactsContract.RawContacts.SYNC4, 1) // reference implementation sets this to 1
                .build()
        )

        // Add new data rows
        addNameDataForUpdate(operations, rawContactId, contact)
        addNicknameDataForUpdate(operations, rawContactId, contact)
        addOrganizationDataForUpdate(operations, rawContactId, contact)
        addPhoneDataForUpdate(operations, rawContactId, contact)
        addEmailDataForUpdate(operations, rawContactId, contact)
        addAddressDataForUpdate(operations, rawContactId, contact)
        addWebsiteDataForUpdate(operations, rawContactId, contact)
        addNoteDataForUpdate(operations, rawContactId, contact)
        addBirthdayDataForUpdate(operations, rawContactId, contact)
        addAnniversaryDataForUpdate(operations, rawContactId, contact)

        // Update photo separately (to avoid deletion)
        updatePhotoData(operations, rawContactId, contact)

        return try {
            Timber.tag("ContactContentProviderAdapter").d("Updating contact with rawContactId: %s", rawContactId)
            Timber.tag("ContactContentProviderAdapter").d("Contact: %s", contact.displayName)
            
            contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val contactId = queryContactId(rawContactId)
            
            Timber.tag("ContactContentProviderAdapter").d("Contact updated successfully, contactId: %s", contactId)
            SyncResult(contactId, rawContactId, true)
        } catch (e: Exception) {
            Timber.tag("ContactContentProviderAdapter").e(e, "Failed to update contact: %s", e.message)
            e.printStackTrace()
            SyncResult(null, null, false)
        }
    }

    /**
     * Deletes a contact from Android Contacts.
     *
     * This deletes the RawContact, which cascades to all data rows.
     *
     * @param rawContactId The RawContact ID to delete
     * @param androidAccountName The account name
     * @param androidAccountType The account type
     * @return True if deletion was successful
     */
    fun deleteContact(
        rawContactId: Long,
        androidAccountName: String,
        androidAccountType: String
    ): Boolean {
        return try {
            val baseUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId)
            val uri = syncAdapterUri(baseUri, androidAccountName, androidAccountType)
            contentResolver.delete(uri, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Queries the Contact ID from a RawContact ID.
     */
    private fun queryContactId(rawContactId: Long): Long? {
        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID))
            } else null
        }
    }

    // --- Data insertion helpers for new contact ---

    private fun addNameData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, contact.namePrefix)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, contact.nameSuffix)
                .build()
        )
    }

    private fun addNicknameData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        if (contact.nickname != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, contact.nickname)
                    .build()
            )
        }
    }

    private fun addOrganizationData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        if (contact.organization != null || contact.jobTitle != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.organization)
                    .withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, contact.organizationUnit)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.jobTitle)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build()
            )
        }
    }

    private fun addPhoneData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        contact.phoneNumbers.forEach { phone ->
            val androidType = typeMapper.phoneTypeToAndroid(phone.type)
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, androidType)
                    .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phone.label)
                    .build()
            )
        }
    }

    private fun addEmailData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        contact.emails.forEach { email ->
            val androidType = typeMapper.emailTypeToAndroid(email.type)
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, androidType)
                    .withValue(ContactsContract.CommonDataKinds.Email.LABEL, email.label)
                    .build()
            )
        }
    }

    private fun addAddressData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        contact.postalAddresses.forEach { addr ->
            val androidType = typeMapper.addressTypeToAndroid(addr.type)
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, addr.street)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, addr.city)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, addr.region)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, addr.postalCode)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, addr.country)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, androidType)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, addr.label)
                    .build()
            )
        }
    }

    private fun addWebsiteData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        contact.websites.forEach { website ->
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, website)
                    .withValue(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE)
                    .build()
            )
        }
    }

    private fun addNoteData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        if (contact.note != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.note)
                    .build()
            )
        }
    }

    private fun addBirthdayData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        if (contact.birthday != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, contact.birthday)
                    .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                    .build()
            )
        }
    }

    private fun addAnniversaryData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        if (contact.anniversary != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, contact.anniversary)
                    .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY)
                    .build()
            )
        }
    }

    private fun addPhotoData(
        operations: ArrayList<ContentProviderOperation>,
        rawContactIndex: Int,
        contact: Contact
    ) {
        if (contact.photoBase64 != null) {
            try {
                val photoData = java.util.Base64.getDecoder().decode(contact.photoBase64)
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData)
                        .build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Data insertion helpers for update (with explicit raw contact ID) ---

    private fun addNameDataForUpdate(
        operations: ArrayList<ContentProviderOperation>,
        rawContactId: Long,
        contact: Contact
    ) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, contact.namePrefix)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, contact.nameSuffix)
                .build()
        )
    }

    private fun addNicknameDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        if (contact.nickname != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, contact.nickname)
                    .build()
            )
        }
    }

    private fun addOrganizationDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        if (contact.organization != null || contact.jobTitle != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.organization)
                    .withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, contact.organizationUnit)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.jobTitle)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build()
            )
        }
    }

    private fun addPhoneDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        contact.phoneNumbers.forEach { phone ->
            val androidType = typeMapper.phoneTypeToAndroid(phone.type)
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, androidType)
                    .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phone.label)
                    .build()
            )
        }
    }

    private fun addEmailDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        contact.emails.forEach { email ->
            val androidType = typeMapper.emailTypeToAndroid(email.type)
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, androidType)
                    .withValue(ContactsContract.CommonDataKinds.Email.LABEL, email.label)
                    .build()
            )
        }
    }

    private fun addAddressDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        contact.postalAddresses.forEach { addr ->
            val androidType = typeMapper.addressTypeToAndroid(addr.type)
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, addr.street)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, addr.city)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, addr.region)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, addr.postalCode)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, addr.country)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, androidType)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, addr.label)
                    .build()
            )
        }
    }

    private fun addWebsiteDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        contact.websites.forEach { website ->
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, website)
                    .withValue(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE)
                    .build()
            )
        }
    }

    private fun addNoteDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        if (contact.note != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.note)
                    .build()
            )
        }
    }

    private fun addBirthdayDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        if (contact.birthday != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, contact.birthday)
                    .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                    .build()
            )
        }
    }

    private fun addAnniversaryDataForUpdate(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        if (contact.anniversary != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, contact.anniversary)
                    .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY)
                    .build()
            )
        }
    }

    private fun updatePhotoData(operations: ArrayList<ContentProviderOperation>, rawContactId: Long, contact: Contact) {
        // Delete existing photo
        operations.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                )
                .build()
        )

        // Add new photo if present
        if (contact.photoBase64 != null) {
            try {
                val photoData = java.util.Base64.getDecoder().decode(contact.photoBase64)
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData)
                        .build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reads a contact from Android ContactsContract by raw contact ID.
     * Converts it to a Contact domain object.
     */
    fun readContactFromProvider(rawContactId: Long): Contact? {
        // Query raw contact info
        val rawContactCursor = contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId),
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.SOURCE_ID,
                ContactsContract.RawContacts.SYNC1, // UID
                ContactsContract.RawContacts.SYNC2  // etag
            ),
            null,
            null,
            null
        )

        rawContactCursor?.use {
            if (!it.moveToFirst()) return null

            val contactId = it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID))
            val uid = it.getString(it.getColumnIndexOrThrow(ContactsContract.RawContacts.SYNC1))
            val etag = it.getString(it.getColumnIndexOrThrow(ContactsContract.RawContacts.SYNC2))

            // Query all data for this raw contact
            val dataCursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                arrayOf(rawContactId.toString()),
                null
            )

            var displayName: String? = null
            var givenName: String? = null
            var familyName: String? = null
            var middleName: String? = null
            var namePrefix: String? = null
            var nameSuffix: String? = null
            val phoneNumbers = mutableListOf<PhoneNumber>()
            val emails = mutableListOf<Email>()
            val addresses = mutableListOf<PostalAddress>()
            var organization: String? = null
            var jobTitle: String? = null
            var note: String? = null
            var photoBase64: String? = null

            dataCursor?.use { cursor ->
                val mimetypeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimetypeIndex)
                    
                    when (mimeType) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME))
                            givenName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME))
                            familyName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME))
                            middleName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME))
                            namePrefix = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.PREFIX))
                            nameSuffix = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.SUFFIX))
                        }
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                            val label = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL))
                            if (number != null) {
                                phoneNumbers.add(PhoneNumber(
                                    number = number,
                                    type = typeMapper.androidToPhoneType(type),
                                    label = if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) label else null
                                ))
                            }
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val address = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
                            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE))
                            val label = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.LABEL))
                            if (address != null) {
                                emails.add(Email(
                                    email = address,
                                    type = typeMapper.androidToEmailType(type),
                                    label = if (type == ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM) label else null
                                ))
                            }
                        }
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val street = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.STREET))
                            val city = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.CITY))
                            val region = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.REGION))
                            val postcode = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE))
                            val country = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY))
                            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.TYPE))
                            val label = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.LABEL))
                            addresses.add(PostalAddress(
                                street = street,
                                city = city,
                                region = region,
                                postalCode = postcode,
                                country = country,
                                type = typeMapper.androidToAddressType(type),
                                label = if (type == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM) label else null
                            ))
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            organization = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY))
                            jobTitle = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.TITLE))
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            note = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Note.NOTE))
                        }
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> {
                            val photoBytes = cursor.getBlob(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Photo.PHOTO))
                            if (photoBytes != null) {
                                photoBase64 = java.util.Base64.getEncoder().encodeToString(photoBytes)
                            }
                        }
                    }
                }
            }

            return Contact(
                id = 0, // Will be assigned when saved to database
                addressBookId = 0, // Will be set by caller
                uid = uid ?: java.util.UUID.randomUUID().toString(),
                displayName = displayName ?: "Unknown",
                givenName = givenName,
                familyName = familyName,
                middleName = middleName,
                namePrefix = namePrefix,
                nameSuffix = nameSuffix,
                phoneNumbers = phoneNumbers,
                emails = emails,
                postalAddresses = addresses,
                organization = organization,
                jobTitle = jobTitle,
                note = note,
                photoBase64 = photoBase64,
                contactUrl = null, // Will be generated during upload
                etag = etag,
                androidContactId = contactId,
                androidRawContactId = rawContactId,
                isDirty = false,
                isDeleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * Get list of raw contact IDs that have been modified locally (dirty=1).
     * These contacts need to be uploaded to the server.
     */
    fun getDirtyRawContactIds(accountName: String, accountType: String): List<Long> {
        val dirtyIds = mutableListOf<Long>()
        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.DIRTY} = 1 AND " +
                    "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(accountName, accountType),
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.RawContacts._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                dirtyIds.add(id)
            }
        }
        return dirtyIds
    }

    /**
     * Get list of raw contact IDs that have been deleted locally (deleted=1).
     * These contacts need to be deleted on the server.
     */
    fun getDeletedRawContactIds(accountName: String, accountType: String): List<Pair<Long, String?>> {
        val deletedContacts = mutableListOf<Pair<Long, String?>>()
        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.SOURCE_ID
            ),
            "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.DELETED} = 1",
            arrayOf(accountName, accountType),
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.RawContacts._ID)
            val sourceIdIndex = it.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID)
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val sourceId = if (sourceIdIndex >= 0) it.getString(sourceIdIndex) else null
                deletedContacts.add(Pair(id, sourceId))
            }
        }
        return deletedContacts
    }

    /**
     * Get all server-side filenames (SOURCE_ID) for an account.
     * Used to detect contacts that exist locally but were deleted on server.
     */
    fun getSourceIds(accountName: String, accountType: String): List<String> {
        val sourceIds = mutableListOf<String>()
        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.SOURCE_ID),
            "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(accountName, accountType),
            null
        )

        cursor?.use {
            val sourceIdIndex = it.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID)
            while (it.moveToNext()) {
                val sourceId = if (sourceIdIndex >= 0) it.getString(sourceIdIndex) else null
                if (!sourceId.isNullOrEmpty()) {
                    sourceIds.add(sourceId)
                }
            }
        }
        return sourceIds
    }
}
