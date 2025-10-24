package com.davy.data.sync

import android.content.Context
import android.provider.ContactsContract
import com.davy.data.contentprovider.ContactContentProviderAdapter
import com.davy.data.local.CredentialStore
import com.davy.data.remote.carddav.AddressBookPropFind
import com.davy.data.remote.carddav.AddressBookQuery
import com.davy.data.remote.carddav.ContactDelete
import com.davy.data.remote.carddav.ContactPut
import com.davy.data.remote.carddav.VCardParser
import com.davy.data.remote.carddav.VCardSerializer
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.ContactRepository
import com.davy.domain.model.Account
import com.davy.domain.model.AddressBook
import com.davy.domain.model.Contact
import com.davy.sync.account.AndroidAccountManager
import com.davy.sync.contacts.ContactsContentObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for syncing contacts using CardDAV protocol.
 */
@Singleton
class CardDAVSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addressBookRepository: AddressBookRepository,
    private val contactRepository: ContactRepository,
    private val credentialStore: CredentialStore,
    private val vCardParser: VCardParser,
    private val vCardSerializer: VCardSerializer,
    private val contactContentProviderAdapter: ContactContentProviderAdapter,
    private val androidAccountManager: AndroidAccountManager,
    private val httpClient: OkHttpClient
) {

    data class SyncResult(
        val addressBooksAdded: Int = 0,
        val contactsDownloaded: Int = 0,
        val contactsUploaded: Int = 0,
        val contactsDeleted: Int = 0,
        val errors: Int = 0
    )

    suspend fun syncAccount(account: Account): SyncResult = withContext(Dispatchers.IO) {
        Timber.d("========================================")
        Timber.d("Starting CardDAV sync for account: ${account.accountName}")
        Timber.d("========================================")

        try {
            syncAccountInternal(account)
        } catch (e: Exception) {
            Timber.e(e, "CardDAV sync failed for account: ${account.accountName}")
            SyncResult(errors = 1)
        }
    }
    
    /**
     * Sync a specific address book.
     * Returns SyncResult with statistics for this address book only.
     */
    suspend fun syncAddressBook(account: Account, addressBook: AddressBook): SyncResult = withContext(Dispatchers.IO) {
        Timber.d("========================================")
        Timber.d("Starting CardDAV sync for address book: ${addressBook.displayName}")
        Timber.d("========================================")
        
        try {
            val password = credentialStore.getPassword(account.id)
            if (password == null) {
                Timber.e("No credentials found for account: ${account.id}")
                return@withContext SyncResult(errors = 1)
            }
            
            if (!addressBook.syncEnabled) {
                Timber.d("Address book sync is disabled: ${addressBook.displayName}")
                return@withContext SyncResult()
            }
            
            Timber.d("Syncing address book: ${addressBook.displayName} (${addressBook.url})")
            val result = syncAddressBook(account, addressBook, password)
            Timber.d("Address book sync completed: ↓${result.contactsDownloaded} ↑${result.contactsUploaded} ×${result.contactsDeleted} contacts")
            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "CardDAV sync failed for address book: ${addressBook.displayName}")
            return@withContext SyncResult(errors = 1)
        }
    }

    private suspend fun syncAccountInternal(account: Account): SyncResult {
        // Get password from credential store
        val password = credentialStore.getPassword(account.id)
        if (password == null) {
            Timber.e("No credentials found for account: ${account.id}")
            return SyncResult(errors = 1)
        }

        var addressBooksAdded = 0
        var contactsDownloaded = 0
        var contactsUploaded = 0
        var contactsDeleted = 0
        var errors = 0

        // Get address books for this account
        val addressBooks = addressBookRepository.getByAccountId(account.id)
        
        if (addressBooks.isEmpty()) {
            Timber.w("No address books found for account: ${account.accountName}")
            return SyncResult()
        }

        Timber.d("Found ${addressBooks.size} address book(s) for account: ${account.accountName}")

        // Sync each address book IN PARALLEL
        coroutineScope {
            val syncJobs = addressBooks.map { addressBook ->
                async {
                    try {
                        Timber.d("Syncing address book: ${addressBook.displayName}")
                        syncAddressBook(account, addressBook, password)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to sync address book: ${addressBook.displayName}")
                        errors++
                        SyncResult(errors = 1)
                    }
                }
            }
            
            // Collect all results
            val results = syncJobs.map { it.await() }
            contactsDownloaded = results.sumOf { it.contactsDownloaded }
            contactsUploaded = results.sumOf { it.contactsUploaded }
            contactsDeleted = results.sumOf { it.contactsDeleted }
        }

        val result = SyncResult(
            addressBooksAdded = addressBooksAdded,
            contactsDownloaded = contactsDownloaded,
            contactsUploaded = contactsUploaded,
            contactsDeleted = contactsDeleted,
            errors = errors
        )

        Timber.d("CardDAV sync completed: +$addressBooksAdded address books, ↓$contactsDownloaded ↑$contactsUploaded ×$contactsDeleted contacts, $errors errors")
        return result
    }

    private suspend fun syncAddressBook(account: Account, addressBook: AddressBook, password: String): SyncResult {
        Timber.d("Syncing address book: ${addressBook.displayName} (${addressBook.url})")
        
        // Set sync flag to prevent ContentObserver loops
        ContactsContentObserver.isSyncInProgress = true
        
        var contactsDownloaded = 0
        var contactsUploaded = 0
        var contactsDeleted = 0
        
        try {
            // Step 1: Fetch server ctag to check if anything changed
            val serverCtag = fetchServerCtag(addressBook.url, account.username, password)
            if (serverCtag == null) {
                Timber.e("Failed to fetch server ctag for ${addressBook.displayName}")
                return SyncResult()
            }
            
            // Check if we have any contacts locally for this address book
            val localContacts = contactRepository.getByAddressBookId(addressBook.id)
            val hasLocalContacts = localContacts.isNotEmpty()
            
            val hasChanged = addressBook.ctag != serverCtag
            Timber.d("  Server ctag: $serverCtag, Local ctag: ${addressBook.ctag}, Changed: $hasChanged, Local contacts: ${localContacts.size}")
            
            // Step 2: Upload dirty contacts FIRST (before download to prevent overwriting local edits)
            Timber.d("  Uploading dirty contacts...")
            contactsUploaded = uploadDirtyContacts(account, addressBook, password)
            if (contactsUploaded > 0) {
                Timber.d("  Uploaded $contactsUploaded contacts")
            }
            
            // Step 3: Delete removed contacts (before download)
            Timber.d("  Deleting removed contacts...")
            contactsDeleted = deleteRemovedContacts(account, addressBook, password)
            if (contactsDeleted > 0) {
                Timber.d("  Deleted $contactsDeleted contacts")
            }
            
            // Step 4: Download contacts if address book changed OR if we have no local contacts (initial sync)
            // This comes AFTER upload/delete so local changes aren't overwritten
            // NOTE: Always download to check for server changes because some servers (like Nextcloud)
            // don't reliably update ctag when contacts are edited via web interface
            if (hasChanged || addressBook.ctag == null || !hasLocalContacts || true) {
                Timber.d("  Downloading contacts (ctag changed: $hasChanged, no local: ${!hasLocalContacts})...")
                contactsDownloaded = downloadContacts(account, addressBook, password)
                Timber.d("  Downloaded $contactsDownloaded contacts")
                
                // Update ctag
                addressBookRepository.updateCTag(addressBook.id, serverCtag)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync address book: ${addressBook.displayName}")
        } finally {
            // Clear sync flag after sync completes
            ContactsContentObserver.isSyncInProgress = false
        }
        
        return SyncResult(
            contactsDownloaded = contactsDownloaded,
            contactsUploaded = contactsUploaded,
            contactsDeleted = contactsDeleted
        )
    }
    
    private suspend fun fetchServerCtag(url: String, username: String, password: String): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = AddressBookPropFind.createCTagOnlyRequest()
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", requestBody)
                .header("Depth", "0")
                .header("Authorization", Credentials.basic(username, password))
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("PROPFIND failed: ${response.code} ${response.message}")
                return@withContext null
            }
            
            val responseXml = response.body?.string() ?: return@withContext null
            
            // Parse ctag directly from XML response - support any namespace prefix
            val ctagPattern = Regex("<\\w*:?getctag[^>]*>(.*?)</\\w*:?getctag>", RegexOption.DOT_MATCHES_ALL)
            val match = ctagPattern.find(responseXml)
            val ctag = match?.groupValues?.get(1)?.trim()
            
            if (ctag.isNullOrEmpty()) {
                Timber.e("No ctag found in response")
                null
            } else {
                ctag
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception fetching server ctag")
            null
        }
    }
    
    private suspend fun downloadContacts(account: Account, addressBook: AddressBook, password: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val addressBookQuery = AddressBookQuery()
            val requestXml = addressBookQuery.createQueryAllRequest(addressBook.url)
            val requestBody = addressBookQuery.createRequestBody(requestXml)
            val request = Request.Builder()
                .url(addressBook.url)
                .method("REPORT", requestBody)
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Authorization", Credentials.basic(account.username, password))
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("addressbook-query REPORT failed: ${response.code} ${response.message}")
                return@withContext 0
            }
            
            val responseXml = response.body?.string() ?: return@withContext 0
            
            // Log a sample of the XML response to diagnose parsing issues
            Timber.d("  === RAW XML RESPONSE (first 2000 chars) ===")
            Timber.d(responseXml.take(2000))
            Timber.d("  === END RAW XML ===")
            
            val fetchedContacts = addressBookQuery.parseQueryResponse(responseXml)
            
            Timber.d("  Fetched ${fetchedContacts.size} contacts from server")
            
            for ((index, fetched) in fetchedContacts.withIndex()) {
                Timber.d("  Contact #${index+1}: URL=${fetched.url}")
                Timber.d("  Contact #${index+1}: etag=${fetched.etag}")
                Timber.d("  Contact #${index+1}: vCardData present=${fetched.vcardData != null} (${fetched.vcardData?.length ?: 0} chars)")
                
                if (fetched.vcardData == null) {
                    Timber.w("  Contact #${index+1}: Skipping - vCardData is null")
                    continue
                }
                
                if (fetched.etag == null) {
                    Timber.w("  Contact #${index+1}: Skipping - etag is null")
                    continue
                }
                
                if (processDownloadedContact(account, addressBook, fetched.url, fetched.vcardData, fetched.etag)) {
                    count++
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception downloading contacts")
        }
        count
    }
    
    private suspend fun processDownloadedContact(
        account: Account,
        addressBook: AddressBook,
        contactUrl: String,
        vcardData: String,
        etag: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Convert relative URL to absolute URL if needed
            val absoluteContactUrl = if (contactUrl.startsWith("http://") || contactUrl.startsWith("https://")) {
                contactUrl
            } else {
                // Build absolute URL from address book base URL
                val baseUrl = addressBook.url.substringBefore("/remote.php") // Get scheme://host:port
                if (contactUrl.startsWith("/")) {
                    "$baseUrl$contactUrl"
                } else {
                    "${addressBook.url.trimEnd('/')}/$contactUrl"
                }
            }
            
            Timber.d("    ==== Processing contact: $absoluteContactUrl ====")
            
            // Parse vCard
            val contact = vCardParser.parse(
                vcardData = vcardData,
                addressBookId = addressBook.id,
                contactUrl = absoluteContactUrl,
                etag = etag
            )
            
            if (contact == null) {
                Timber.w("    Failed to parse vCard from $contactUrl")
                return@withContext false
            }
            
            Timber.d("    Parsed contact: ${contact.displayName} (UID: ${contact.uid})")
            
            // Check if contact exists locally (use absolute URL)
            val existingContact = contactRepository.getByUrl(absoluteContactUrl)
            
            if (existingContact == null) {
                Timber.d("    New contact - inserting into DAVy database")
                
                // New contact: Insert
                val contactId = contactRepository.insert(contact)
                Timber.d("    Inserted into DAVy DB with ID: $contactId")
                
                // Get the address book Android account (separate account per address book)
                val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
                val addressBookAccount = addressBookAccounts.firstOrNull { acct ->
                    val addressBookId = androidAccountManager.getAddressBookId(acct)
                    addressBookId == addressBook.id
                }
                
                if (addressBookAccount == null) {
                    Timber.e("    FAILED: Address book Android account not found for address book ID: ${addressBook.id}")
                    return@withContext false
                }
                
                // Sync to Android Contacts using the address book account
                Timber.d("    Syncing to Android Contacts Provider using account: ${addressBookAccount.name}")
                val syncResult = contactContentProviderAdapter.insertContact(
                    contact = contact.copy(id = contactId),
                    androidAccountName = addressBookAccount.name,
                    androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                )
                
                if (!syncResult.success) {
                    Timber.e("    FAILED to sync contact to Android Contacts Provider")
                    return@withContext false
                }
                
                Timber.d("    Android Contacts sync result: success=${syncResult.success}, contactId=${syncResult.contactId}, rawContactId=${syncResult.rawContactId}")
                
                // Update Android IDs
                if (syncResult.contactId != null && syncResult.rawContactId != null) {
                    contactRepository.updateAndroidContactIds(
                        id = contactId,
                        contactId = syncResult.contactId,
                        rawContactId = syncResult.rawContactId
                    )
                    Timber.d("    Updated Android IDs in DAVy database")
                    
                    // CRITICAL: Clear dirty flag so contact is not read-only
                    contactContentProviderAdapter.clearDirtyFlag(
                        rawContactId = syncResult.rawContactId,
                        accountName = addressBookAccount.name,
                        accountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                    )
                    Timber.d("    Cleared dirty flag for raw contact ${syncResult.rawContactId}")
                }
                
                Timber.d("    ✓ Successfully inserted contact: ${contact.displayName}")
                return@withContext true
            } else {
                Timber.d("    Contact exists locally (ID: ${existingContact.id})")
                // Determine if the raw contact still exists in the Contacts provider
                val existingRawId = existingContact.androidRawContactId
                val rawExists = existingRawId?.let { contactContentProviderAdapter.rawContactExists(it) } ?: false

                // If the raw contact is missing (e.g., after disable), re-insert even if ETag didn't change
                if (!rawExists) {
                    Timber.d("    Raw contact missing in provider -> re-inserting under address book account")

                    // Get the address book Android account (separate account per address book)
                    val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
                    val addressBookAccount = addressBookAccounts.firstOrNull { acct ->
                        val addressBookId = androidAccountManager.getAddressBookId(acct)
                        addressBookId == addressBook.id
                    }

                    if (addressBookAccount == null) {
                        Timber.e("    FAILED: Address book Android account not found for address book ID: ${addressBook.id}")
                        return@withContext false
                    }

                    Timber.d("    Inserting missing contact into Android Contacts Provider using account: ${addressBookAccount.name}")
                    val insertResult = contactContentProviderAdapter.insertContact(
                        contact = contact.copy(id = existingContact.id),
                        androidAccountName = addressBookAccount.name,
                        androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                    )

                    if (!insertResult.success) {
                        Timber.e("    FAILED to (re)insert contact into Android Contacts Provider")
                        return@withContext false
                    }

                    // Update Android IDs on the existing contact row
                    if (insertResult.contactId != null && insertResult.rawContactId != null) {
                        contactRepository.updateAndroidContactIds(
                            id = existingContact.id,
                            contactId = insertResult.contactId,
                            rawContactId = insertResult.rawContactId
                        )
                        Timber.d("    Updated Android IDs in DAVy database for existing contact")

                        // Clear dirty flag to mark as synced
                        contactContentProviderAdapter.clearDirtyFlag(
                            rawContactId = insertResult.rawContactId,
                            accountName = addressBookAccount.name,
                            accountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                        )
                        Timber.d("    Cleared dirty flag for raw contact ${insertResult.rawContactId}")
                    }

                    Timber.d("    ✓ Successfully (re)inserted contact: ${contact.displayName}")
                    return@withContext true
                }

                // Existing contact: Update if etag changed
                if (existingContact.etag != etag) {
                    Timber.d("    ETag changed - updating contact")
                    
                    val updatedContact = contact.copy(
                        id = existingContact.id,
                        androidContactId = existingContact.androidContactId,
                        androidRawContactId = existingContact.androidRawContactId
                    )
                    contactRepository.update(updatedContact)
                    
                    // Update Android Contacts
                    if (existingContact.androidRawContactId != null) {
                        // Get the address book Android account
                        val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
                        val addressBookAccount = addressBookAccounts.firstOrNull { acct ->
                            val addressBookId = androidAccountManager.getAddressBookId(acct)
                            addressBookId == addressBook.id
                        }

                        if (addressBookAccount != null) {
                            contactContentProviderAdapter.updateContact(
                                contact = updatedContact,
                                rawContactId = existingContact.androidRawContactId,
                                androidAccountName = addressBookAccount.name,
                                androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                            )

                            // CRITICAL: Clear dirty flag so contact is not read-only
                            contactContentProviderAdapter.clearDirtyFlag(
                                rawContactId = existingContact.androidRawContactId,
                                accountName = addressBookAccount.name,
                                accountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                            )
                            Timber.d("    Cleared dirty flag for raw contact ${existingContact.androidRawContactId}")
                        } else {
                            Timber.w("    Address book account not found for update")
                        }
                    } else {
                        // No raw contact id (e.g., after provider cleanup) -> insert anew
                        val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
                        val addressBookAccount = addressBookAccounts.firstOrNull { acct ->
                            val addressBookId = androidAccountManager.getAddressBookId(acct)
                            addressBookId == addressBook.id
                        }

                        if (addressBookAccount != null) {
                            val insertResult = contactContentProviderAdapter.insertContact(
                                contact = updatedContact,
                                androidAccountName = addressBookAccount.name,
                                androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                            )
                            if (insertResult.success && insertResult.contactId != null && insertResult.rawContactId != null) {
                                contactRepository.updateAndroidContactIds(
                                    id = updatedContact.id,
                                    contactId = insertResult.contactId,
                                    rawContactId = insertResult.rawContactId
                                )
                                contactContentProviderAdapter.clearDirtyFlag(
                                    rawContactId = insertResult.rawContactId,
                                    accountName = addressBookAccount.name,
                                    accountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                                )
                            }
                        } else {
                            Timber.w("    Address book account not found for update (no raw contact id)")
                        }
                    }
                    
                    Timber.d("    ✓ Successfully updated contact: ${contact.displayName}")
                    return@withContext true
                } else {
                    // ETag unchanged and raw contact exists -> nothing to do
                    Timber.d("    ETag unchanged and contact present - skipping")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception processing downloaded contact from $contactUrl")
            return@withContext false
        }
    }
    
    private suspend fun uploadDirtyContacts(account: Account, addressBook: AddressBook, password: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            // Get the address book Android account
            val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
            val addressBookAccount = addressBookAccounts.firstOrNull { acct ->
                val addressBookId = androidAccountManager.getAddressBookId(acct)
                addressBookId == addressBook.id
            }
            
            if (addressBookAccount == null) {
                Timber.w("Address book account not found for uploading dirty contacts")
                return@withContext 0
            }
            
            // Get dirty contacts from Android ContactsContract (where dirty=1)
            val dirtyRawContactIds = contactContentProviderAdapter.getDirtyRawContactIds(
                addressBookAccount.name,
                AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
            )
            
            Timber.d("  Found ${dirtyRawContactIds.size} dirty contacts in Android ContactsProvider")
            
            for (rawContactId in dirtyRawContactIds) {
                try {
                    // ALWAYS read fresh data from ContentProvider for dirty contacts
                    // The dirty flag means the user edited it in Contacts app, so ContentProvider has the latest data
                    Timber.d("    Reading dirty contact from ContentProvider (raw ID: $rawContactId)")
                    var contact = contactContentProviderAdapter.readContactFromProvider(rawContactId)
                    
                    if (contact == null) {
                        Timber.w("    Failed to read contact from ContentProvider for raw contact ID: $rawContactId")
                        continue
                    }
                    
                    Timber.d("    Read contact from ContentProvider: ${contact.displayName}")
                    
                    // Check if this contact exists in our database to get the internal ID and address book
                    val existingContact = contactRepository.getByAndroidRawContactId(rawContactId)
                    
                    if (existingContact != null) {
                        // Existing contact - merge IDs from database
                        contact = contact.copy(
                            id = existingContact.id,
                            addressBookId = existingContact.addressBookId,
                            contactUrl = existingContact.contactUrl,
                            etag = existingContact.etag
                        )
                        Timber.d("    Contact exists in database (ID: ${existingContact.id}), using existing URL and etag")
                    } else {
                        // New contact - set address book ID and insert into database
                        contact = contact.copy(addressBookId = addressBook.id)
                        val newContactId = contactRepository.insert(contact)
                        contact = contact.copy(id = newContactId)
                        Timber.d("    New contact added to database: ${contact.displayName} (ID: $newContactId)")
                    }
                    
                    if (contact.uid.isEmpty()) {
                        Timber.w("    Contact has empty UID, skipping upload: ${contact.displayName}")
                        continue
                    }
                    
                    Timber.d("    Uploading dirty contact: ${contact.displayName}")
                    
                    val vcardData = vCardSerializer.serializeV3(contact)
                    val requestBody = ContactPut.createRequestBody(vcardData)
                    val headers = ContactPut.buildHeaders(
                        isNew = contact.contactUrl.isNullOrEmpty(),
                        etag = contact.etag ?: ""
                    )
                    
                    // Get or build contact URL
                    var contactUrl = if (contact.contactUrl.isNullOrEmpty()) {
                        ContactPut.buildContactUrl(addressBook.url, contact.uid)
                    } else {
                        contact.contactUrl!!
                    }
                    
                    // Fix relative URLs (legacy contacts might have relative paths)
                    if (!contactUrl.startsWith("http://") && !contactUrl.startsWith("https://")) {
                        val baseUrl = addressBook.url.substringBefore("/remote.php")
                        contactUrl = if (contactUrl.startsWith("/")) {
                            "$baseUrl$contactUrl"
                        } else {
                            "${addressBook.url.trimEnd('/')}/$contactUrl"
                        }
                        Timber.d("    Fixed relative URL to: $contactUrl")
                    }
                    
                    val requestBuilder = Request.Builder()
                        .url(contactUrl)
                        .put(requestBody)
                        .header("Authorization", Credentials.basic(account.username, password))
                    
                    for ((key, value) in headers) {
                        requestBuilder.header(key, value)
                    }
                    
                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val putResult = ContactPut.parsePutResponse(
                        status = response.code,
                        headers = response.headers.toMultimap()
                    )
                    
                    if (putResult.isSuccess()) {
                        // Extract filename from contact URL for SOURCE_ID
                        val sourceId = contactUrl.substringAfterLast("/")
                        
                        // Update DAVy database
                        val updatedContact = contact.copy(
                            contactUrl = contactUrl,
                            etag = putResult.etag ?: contact.etag,
                            isDirty = false
                        )
                        contactRepository.update(updatedContact)
                        
                        // Update ContentProvider with UID, SOURCE_ID, and etag
                        val uri = ContactsContract.RawContacts.CONTENT_URI
                            .buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, addressBookAccount.name)
                            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK)
                            .build()
                        
                        val values = android.content.ContentValues().apply {
                            put(ContactsContract.RawContacts.SOURCE_ID, sourceId)
                            put(ContactsContract.RawContacts.SYNC1, contact.uid)
                            put(ContactsContract.RawContacts.SYNC2, putResult.etag ?: contact.etag)
                            put(ContactsContract.RawContacts.DIRTY, 0)
                        }
                        
                        context.contentResolver.update(
                            android.content.ContentUris.withAppendedId(uri, rawContactId),
                            values,
                            null,
                            null
                        )
                        
                        count++
                        Timber.d("    ✓ Uploaded contact: ${contact.displayName}")
                    } else {
                        Timber.w("    Upload failed for contact: ${contact.displayName}, status: ${response.code}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "    Failed to upload contact with raw ID: $rawContactId")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception uploading dirty contacts")
        }
        count
    }
    
    private suspend fun deleteRemovedContacts(account: Account, addressBook: AddressBook, password: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            // Get the address book Android account
            val addressBookAccounts = androidAccountManager.getAddressBookAccounts(account.accountName)
            val addressBookAccount = addressBookAccounts.firstOrNull { acct ->
                val addressBookId = androidAccountManager.getAddressBookId(acct)
                addressBookId == addressBook.id
            }
            
            if (addressBookAccount == null) {
                Timber.w("Address book account not found for deleting contacts")
                return@withContext 0
            }
            
            // Part 1: Handle locally deleted contacts (deleted=1) - upload deletions to server
            Timber.d("  Checking for locally deleted contacts (deleted=1)...")
            
            val deletedRawContactIds = contactContentProviderAdapter.getDeletedRawContactIds(
                addressBookAccount.name,
                AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
            )
            
            Timber.d("  Found ${deletedRawContactIds.size} locally deleted contacts")
            
            for ((rawContactId, sourceId) in deletedRawContactIds) {
                try {
                    // Try to find contact in DAVy database
                    var contact = contactRepository.getByAndroidRawContactId(rawContactId)
                    
                    if (contact == null && sourceId != null) {
                        // Try by SOURCE_ID
                        contact = contactRepository.getBySourceId(sourceId, addressBook.id)
                    }
                    
                    if (contact == null || contact.contactUrl.isNullOrEmpty()) {
                        // Contact never synced to server - just remove from ContentProvider
                        Timber.d("    Contact never synced, removing locally (raw ID: $rawContactId)")
                        contactContentProviderAdapter.deleteContact(
                            rawContactId = rawContactId,
                            androidAccountName = addressBookAccount.name,
                            androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                        )
                        if (contact != null) {
                            contactRepository.delete(contact)
                        }
                        count++
                    } else {
                        // DELETE request to server
                        Timber.d("    Deleting contact on server: ${contact.displayName} (${contact.contactUrl})")
                        
                        // Fix relative URLs (legacy contacts might have relative paths)
                        var contactUrl = contact.contactUrl!!
                        if (!contactUrl.startsWith("http://") && !contactUrl.startsWith("https://")) {
                            val baseUrl = addressBook.url.substringBefore("/remote.php")
                            contactUrl = if (contactUrl.startsWith("/")) {
                                "$baseUrl$contactUrl"
                            } else {
                                "${addressBook.url.trimEnd('/')}/$contactUrl"
                            }
                            Timber.d("    Fixed relative URL to: $contactUrl")
                        }
                        
                        // Try with ETag first
                        var requestBuilder = Request.Builder()
                            .url(contactUrl)
                            .delete()
                            .header("Authorization", Credentials.basic(account.username, password))
                        
                        val etag = contact.etag
                        if (!etag.isNullOrEmpty()) {
                            requestBuilder.header("If-Match", ContactDelete.buildIfMatchHeader(etag))
                        }
                        
                        var response = httpClient.newCall(requestBuilder.build()).execute()
                        var deleteResult = ContactDelete.parseDeleteResponse(response.code)
                        
                        // If ETag mismatch (412), retry without If-Match to force delete
                        if (deleteResult.isPreconditionFailed) {
                            Timber.d("    ETag mismatch (412), retrying deletion without If-Match")
                            requestBuilder = Request.Builder()
                                .url(contactUrl)
                                .delete()
                                .header("Authorization", Credentials.basic(account.username, password))
                            
                            response = httpClient.newCall(requestBuilder.build()).execute()
                            deleteResult = ContactDelete.parseDeleteResponse(response.code)
                        }
                        
                        if (deleteResult.isSuccess()) {
                            Timber.d("    ✓ Server deletion successful (status: ${response.code})")
                            
                            // Delete from DAVy database
                            contactRepository.delete(contact)
                            
                            // Remove from Android ContentProvider
                            contactContentProviderAdapter.deleteContact(
                                rawContactId = rawContactId,
                                androidAccountName = addressBookAccount.name,
                                androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                            )
                            
                            count++
                            Timber.d("    ✓ Deleted contact: ${contact.displayName}")
                        } else {
                            Timber.w("    Server deletion failed with status: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "    Failed to delete contact with raw ID: $rawContactId")
                }
            }
            
            // Part 2: Detect contacts deleted on server - compare local vs server lists
            Timber.d("  Checking for contacts deleted on server...")
            
            // Get all contacts from server
            val addressBookQuery = AddressBookQuery()
            val requestXml = addressBookQuery.createQueryAllRequest(addressBook.url)
            val requestBody = addressBookQuery.createRequestBody(requestXml)
            val request = Request.Builder()
                .url(addressBook.url)
                .method("REPORT", requestBody)
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Authorization", Credentials.basic(account.username, password))
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseXml = response.body?.string() ?: ""
                val serverContacts = addressBookQuery.parseQueryResponse(responseXml)
                
                // Extract server filenames (SOURCE_ID)
                val serverSourceIds = serverContacts.mapNotNull { contact ->
                    contact.url.substringAfterLast("/").takeIf { it.isNotEmpty() }
                }.toSet()
                
                // Get local SOURCE_IDs
                val localSourceIds = contactContentProviderAdapter.getSourceIds(
                    addressBookAccount.name,
                    AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                )
                
                Timber.d("  Server has ${serverSourceIds.size} contacts, local has ${localSourceIds.size} contacts")
                
                // Find contacts that exist locally but not on server
                val deletedOnServer = localSourceIds.filter { it !in serverSourceIds }
                
                if (deletedOnServer.isNotEmpty()) {
                    Timber.d("  Found ${deletedOnServer.size} contacts deleted on server")
                    
                    for (sourceId in deletedOnServer) {
                        try {
                            // Find contact in DAVy database by SOURCE_ID (filename)
                            val contact = contactRepository.getBySourceId(sourceId, addressBook.id)
                            
                            if (contact != null) {
                                // Delete from DAVy database
                                contactRepository.delete(contact)
                                
                                // Delete from Android Contacts
                                if (contact.androidRawContactId != null) {
                                    contactContentProviderAdapter.deleteContact(
                                        rawContactId = contact.androidRawContactId,
                                        androidAccountName = addressBookAccount.name,
                                        androidAccountType = AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                                    )
                                }
                                
                                count++
                                Timber.d("    ✓ Deleted contact (removed on server): ${contact.displayName}")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "    Failed to delete contact with sourceId: $sourceId")
                        }
                    }
                }
            }
            
            // Purge old soft-deleted contacts
            contactRepository.purgeSoftDeleted()
        } catch (e: Exception) {
            Timber.e(e, "Exception deleting removed contacts")
        }
        count
    }
}

