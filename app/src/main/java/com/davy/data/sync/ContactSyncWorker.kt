package com.davy.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davy.data.contentprovider.ContactContentProviderAdapter
import com.davy.data.remote.carddav.AddressBookPropFind
import com.davy.data.remote.carddav.AddressBookQuery
import com.davy.data.remote.carddav.ContactDelete
import com.davy.data.remote.carddav.ContactGet
import com.davy.data.remote.carddav.ContactPut
import com.davy.data.remote.carddav.FetchedContact
import com.davy.data.remote.carddav.VCardParser
import com.davy.data.remote.carddav.VCardSerializer
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.ContactRepository
import com.davy.domain.model.AddressBook
import com.davy.domain.model.Contact
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Worker for synchronizing contacts with CardDAV server.
 *
 * This worker performs bidirectional sync:
 * 1. Download: Fetch changes from server and update local database
 * 2. Upload: Upload local changes to server
 * 3. Delete: Remove deleted contacts from server
 *
 * Sync strategy:
 * - Incremental sync using ctag (collection tag) and etag (entity tag)
 * - Full sync on first run or when ctag changes
 * - Efficient multiget for batch downloads
 * - Conflict resolution using etag
 */
@HiltWorker
class ContactSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val addressBookRepository: AddressBookRepository,
    private val contactRepository: ContactRepository,
    private val contentProviderAdapter: ContactContentProviderAdapter,
    private val httpClient: OkHttpClient,
    private val vCardParser: VCardParser,
    private val vCardSerializer: VCardSerializer
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ContactSyncWorker"
        const val KEY_ADDRESS_BOOK_ID = "address_book_id"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_FORCE_FULL_SYNC = "force_full_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val addressBookId = inputData.getLong(KEY_ADDRESS_BOOK_ID, -1)
            val forceFullSync = inputData.getBoolean(KEY_FORCE_FULL_SYNC, false)

            if (addressBookId == -1L) {
                return@withContext Result.failure()
            }

            // Get address book
            val addressBook = addressBookRepository.getById(addressBookId)
                ?: return@withContext Result.failure()

            // Check if sync is enabled
            if (!addressBook.syncEnabled) {
                return@withContext Result.success()
            }

            // Perform sync
            syncAddressBook(addressBook, forceFullSync)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Contact sync failed")
            e.printStackTrace()
            Result.retry()
        }
    }

    /**
     * Synchronizes an address book with the server.
     */
    private suspend fun syncAddressBook(addressBook: AddressBook, forceFullSync: Boolean) {
        // Step 1: Check if address book has changed (using ctag)
        val serverCtag = fetchServerCtag(addressBook.url) ?: return
        val hasChanged = addressBook.ctag != serverCtag || forceFullSync

        if (hasChanged) {
            // Full sync: Download all contacts
            performFullSync(addressBook, serverCtag)
        } else {
            // No changes on server, but check for local changes
            performLocalChangesSync(addressBook)
        }

        // Step 2: Upload local changes (dirty contacts)
        uploadDirtyContacts(addressBook)

        // Step 3: Delete removed contacts
        deleteRemovedContacts(addressBook)

        // Step 4: Update ctag
        addressBookRepository.updateCTag(addressBook.id, serverCtag)
    }

    /**
     * Fetches the current ctag from the server.
     */
    private suspend fun fetchServerCtag(addressBookUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // PROPFIND request for ctag
                val requestBody = AddressBookPropFind.createCTagOnlyRequest()
                val request = Request.Builder()
                    .url(addressBookUrl)
                    .method("PROPFIND", requestBody)
                    .header("Depth", "0")
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseXml = response.body?.string() ?: return@withContext null

                // Parse response
                val addressBooks = AddressBookPropFind.parsePropFindResponse(responseXml)
                addressBooks.firstOrNull()?.ctag
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch server ctag")
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Performs full sync: Downloads all contacts from server.
     */
    private suspend fun performFullSync(addressBook: AddressBook, @Suppress("UNUSED_PARAMETER") newCtag: String) {
        withContext(Dispatchers.IO) {
            try {
                // Query all contacts with ETags
                val query = AddressBookQuery()
                val requestXml = query.createQueryAllRequest(addressBook.url)
                val requestBody = query.createRequestBody(requestXml)
                val request = Request.Builder()
                    .url(addressBook.url)
                    .method("REPORT", requestBody)
                    .header("Depth", "1")
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseXml = response.body?.string() ?: return@withContext

                // Parse response
                val fetchedContacts: List<FetchedContact> = query.parseQueryResponse(responseXml)

                // Process each contact
                for (fetched: FetchedContact in fetchedContacts) {
                    val vcard = fetched.vcardData
                    val tag = fetched.etag
                    if (vcard != null && tag != null) {
                        processDownloadedContact(addressBook, fetched.url, vcard, tag)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to perform full sync")
                e.printStackTrace()
            }
        }
    }

    /**
     * Performs local changes sync: Only uploads local modifications.
     */
    private suspend fun performLocalChangesSync(@Suppress("UNUSED_PARAMETER") addressBook: AddressBook) {
        // No server changes, just upload local changes
        // This is handled by uploadDirtyContacts()
    }

    /**
     * Processes a downloaded contact from the server.
     */
    private suspend fun processDownloadedContact(
        addressBook: AddressBook,
        contactUrl: String,
        vcardData: String,
        etag: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Parse vCard
                val contact = vCardParser.parse(
                    vcardData = vcardData,
                    addressBookId = addressBook.id,
                    contactUrl = contactUrl,
                    etag = etag
                ) ?: return@withContext

                // Check if contact exists locally
                val existingContact = contactRepository.getByUrl(contactUrl)

                if (existingContact == null) {
                    // New contact: Insert
                    val contactId = contactRepository.insert(contact)

                    // Sync to Android Contacts
                    val syncResult = contentProviderAdapter.insertContact(
                        contact = contact.copy(id = contactId),
                        androidAccountName = addressBook.androidAccountName ?: "",
                        androidAccountType = "com.davy.carddav"
                    )

                    // Update Android IDs
                    if (syncResult.success && syncResult.contactId != null && syncResult.rawContactId != null) {
                        contactRepository.updateAndroidContactIds(
                            id = contactId,
                            contactId = syncResult.contactId,
                            rawContactId = syncResult.rawContactId
                        )
                    }
                } else {
                    // Existing contact: Update if etag changed
                    if (existingContact.etag != etag) {
                        val updatedContact = contact.copy(
                            id = existingContact.id,
                            androidContactId = existingContact.androidContactId,
                            androidRawContactId = existingContact.androidRawContactId
                        )
                        contactRepository.update(updatedContact)

                        // Update Android Contacts
                        if (existingContact.androidRawContactId != null) {
                            // TODO: Need to get address book account name/type for sync adapter
                            // This code path might not be used anymore since we're using CardDAVSyncService
                            Timber.w("Update contact in ContactSyncWorker not fully implemented - missing account info")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to process downloaded contact")
                e.printStackTrace()
            }
        }
    }

    /**
     * Uploads dirty contacts (locally modified) to the server.
     */
    private suspend fun uploadDirtyContacts(addressBook: AddressBook) {
        withContext(Dispatchers.IO) {
            try {
                val dirtyContacts = contactRepository.getDirtyContacts()

                for (contact in dirtyContacts) {
                    // Only upload contacts from this address book
                    if (contact.addressBookId != addressBook.id) continue

                    // Serialize to vCard
                    val vcardData = vCardSerializer.serializeV3(contact)

                    // PUT request
                    val requestBody = ContactPut.createRequestBody(vcardData)
                    val headers = ContactPut.buildHeaders(
                        isNew = contact.contactUrl.isNullOrEmpty(),
                        etag = contact.etag
                    )

                    val contactUrl = if (contact.contactUrl.isNullOrEmpty()) {
                        ContactPut.buildContactUrl(addressBook.url, contact.uid)
                    } else {
                        contact.contactUrl
                    }

                    val requestBuilder = Request.Builder()
                        .url(contactUrl)
                        .put(requestBody)

                    headers.forEach { (key, value) ->
                        requestBuilder.header(key, value)
                    }

                    val response = httpClient.newCall(requestBuilder.build()).execute()

                    // Parse response
                    val putResult = ContactPut.parsePutResponse(
                        status = response.code,
                        headers = response.headers.toMultimap()
                    )

                    if (putResult.isSuccess()) {
                        // Update contact with new etag and URL
                        val updatedContact = contact.copy(
                            contactUrl = contactUrl,
                            etag = putResult.etag ?: contact.etag ?: "",
                            isDirty = false
                        )
                        contactRepository.update(updatedContact)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload dirty contacts")
                e.printStackTrace()
            }
        }
    }

    /**
     * Deletes removed contacts (soft-deleted locally) from the server.
     */
    private suspend fun deleteRemovedContacts(addressBook: AddressBook) {
        withContext(Dispatchers.IO) {
            try {
                val deletedContacts = contactRepository.getDeletedContacts()

                for (contact in deletedContacts) {
                    // Only delete contacts from this address book
                    if (contact.addressBookId != addressBook.id) continue

                    // Skip if no URL (never synced)
                    val url = contact.contactUrl
                    if (url.isNullOrEmpty()) {
                        // Just remove from local database
                        contactRepository.delete(contact)
                        continue
                    }

                    // DELETE request
                    val headers = ContactDelete.buildHeaders(contact.etag)

                    val requestBuilder = Request.Builder()
                        .url(url)
                        .delete()

                    headers.forEach { (key, value) ->
                        requestBuilder.header(key, value)
                    }

                    val response = httpClient.newCall(requestBuilder.build()).execute()

                    // Parse response
                    val deleteResult = ContactDelete.parseDeleteResponse(response.code)

                    if (deleteResult.isSuccess()) {
                        // Remove from local database
                        contactRepository.delete(contact)

                        // Remove from Android Contacts
                        if (contact.androidRawContactId != null) {
                            // TODO: Need to get address book account name/type for sync adapter
                            // This code path might not be used anymore since we're using CardDAVSyncService
                            Timber.w("Delete contact in ContactSyncWorker not fully implemented - missing account info")
                        }
                    }
                }

                // Purge old soft-deleted contacts
                contactRepository.purgeSoftDeleted()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete removed contacts")
                e.printStackTrace()
            }
        }
    }
}
