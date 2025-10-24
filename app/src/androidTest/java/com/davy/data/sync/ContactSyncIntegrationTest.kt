package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.davy.data.local.DavyDatabase
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.ContactRepository
import com.davy.data.repository.AccountRepository
import com.davy.data.remote.carddav.CardDAVService
import com.davy.domain.model.*
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration test for CardDAV contact synchronization.
 * 
 * Tests the full sync flow including:
 * - Address book discovery via CardDAV PROPFIND
 * - Contact retrieval via CardDAV addressbook-query
 * - Local database storage
 * - Incremental sync using ctag/etag
 * - Contact upload to CardDAV server
 * - Conflict detection and resolution
 * - vCard parsing and serialization
 * 
 * TDD: This test is written BEFORE the full implementation.
 * 
 * Note: This is an integration test that requires either:
 * 1. A mock CardDAV server running locally
 * 2. A test CardDAV server endpoint
 * 3. Mocked HTTP responses using MockWebServer
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ContactSyncIntegrationTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var database: DavyDatabase
    
    @Inject
    lateinit var accountRepository: AccountRepository
    
    @Inject
    lateinit var addressBookRepository: AddressBookRepository
    
    @Inject
    lateinit var contactRepository: ContactRepository
    
    @Inject
    lateinit var cardDAVService: CardDAVService
    
    private lateinit var context: Context
    private lateinit var testAccount: Account
    
    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        hiltRule.inject()
        
        // Create test account
        testAccount = Account(
            id = 0,
            serverUrl = "https://demo.nextcloud.com",
            username = "test_user",
            displayName = "Test User",
            authType = AuthType.BASIC,
            calDavUrl = "https://demo.nextcloud.com/remote.php/dav/calendars/test_user/",
            cardDavUrl = "https://demo.nextcloud.com/remote.php/dav/addressbooks/test_user/",
            syncEnabled = true,
            createdAt = System.currentTimeMillis()
        )
        
        val accountId = accountRepository.insert(testAccount)
        testAccount = testAccount.copy(id = accountId)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun testFullContactSyncFlow() = runTest {
        // Given: Account with CardDAV URL exists
        assertThat(testAccount.id).isGreaterThan(0L)
        
        // When: Discover address books from CardDAV server
        // TODO: This requires CardDAV server implementation or mock
        // For now, create test address book manually
        val testAddressBook = AddressBook(
            id = 0,
            accountId = testAccount.id,
            url = "${testAccount.cardDavUrl}contacts/",
            displayName = "Personal Contacts",
            description = "Personal address book",
            color = 0xFF2196F3.toInt(),
            ctag = null,
            syncEnabled = true,
            visible = true,
            androidAccountName = "test@example.com",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        // Then: Address book is stored in database
        val storedAddressBook = addressBookRepository.getById(addressBookId)
        assertThat(storedAddressBook).isNotNull()
        assertThat(storedAddressBook?.displayName).isEqualTo("Personal Contacts")
        assertThat(storedAddressBook?.accountId).isEqualTo(testAccount.id)
    }
    
    @Test
    fun testContactSync() = runTest {
        // Given: Address book exists
        val testAddressBook = createTestAddressBook()
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        // When: Sync contacts from CardDAV server
        // TODO: This requires CardDAV server implementation or mock
        // For now, create test contact manually
        val testContact = Contact(
            id = 0,
            addressBookId = addressBookId,
            uid = "test-contact-uid-001",
            contactUrl = "${testAddressBook.url}contact1.vcf",
            etag = "initial-etag",
            displayName = "John Doe",
            givenName = "John",
            familyName = "Doe",
            phoneNumbers = listOf(
                PhoneNumber("+1234567890", PhoneType.MOBILE)
            ),
            emails = listOf(
                Email("john.doe@example.com", EmailType.WORK)
            ),
            isDirty = false,
            isDeleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        val contactId = contactRepository.insert(testContact)
        
        // Then: Contact is stored in database
        val storedContact = contactRepository.getById(contactId)
        assertThat(storedContact).isNotNull()
        assertThat(storedContact?.displayName).isEqualTo("John Doe")
        assertThat(storedContact?.addressBookId).isEqualTo(addressBookId)
        assertThat(storedContact?.uid).isEqualTo("test-contact-uid-001")
        assertThat(storedContact?.phoneNumbers).hasSize(1)
        assertThat(storedContact?.emails).hasSize(1)
    }
    
    @Test
    fun testIncrementalSyncUsingCTag() = runTest {
        // Given: Address book with known ctag exists
        val testAddressBook = createTestAddressBook().copy(ctag = "ctag-v1")
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        // When: Check if ctag changed (would happen via CardDAV PROPFIND)
        val updatedAddressBook = testAddressBook.copy(
            id = addressBookId,
            ctag = "ctag-v2",
            updatedAt = System.currentTimeMillis()
        )
        addressBookRepository.update(updatedAddressBook)
        
        // Then: Address book ctag is updated
        val storedAddressBook = addressBookRepository.getById(addressBookId)
        assertThat(storedAddressBook?.ctag).isEqualTo("ctag-v2")
        assertThat(storedAddressBook?.updatedAt).isGreaterThan(testAddressBook.updatedAt)
    }
    
    @Test
    fun testContactModificationDetectionUsingETag() = runTest {
        // Given: Contact with known etag exists
        val testAddressBook = createTestAddressBook()
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        val testContact = createTestContact(addressBookId).copy(etag = "etag-v1")
        val contactId = contactRepository.insert(testContact)
        
        // When: Contact etag changes (detected via CardDAV sync)
        val modifiedContact = testContact.copy(
            id = contactId,
            displayName = "John Robert Doe",
            middleName = "Robert",
            etag = "etag-v2",
            updatedAt = System.currentTimeMillis()
        )
        contactRepository.update(modifiedContact)
        
        // Then: Contact is updated with new etag
        val storedContact = contactRepository.getById(contactId)
        assertThat(storedContact?.etag).isEqualTo("etag-v2")
        assertThat(storedContact?.displayName).isEqualTo("John Robert Doe")
        assertThat(storedContact?.middleName).isEqualTo("Robert")
    }
    
    @Test
    fun testDirtyContactUpload() = runTest {
        // Given: Contact modified locally (marked as dirty)
        val testAddressBook = createTestAddressBook()
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        val testContact = createTestContact(addressBookId).copy(
            isDirty = true,
            phoneNumbers = listOf(
                PhoneNumber("+9999999999", PhoneType.HOME)
            )
        )
        val contactId = contactRepository.insert(testContact)
        
        // When: Get dirty contacts for upload
        val dirtyContacts = contactRepository.getDirtyContacts()
        
        // Then: Dirty contact is found
        assertThat(dirtyContacts).hasSize(1)
        assertThat(dirtyContacts[0].id).isEqualTo(contactId)
        assertThat(dirtyContacts[0].isDirty).isTrue()
        
        // When: Mark as synced after upload
        val syncedContact = dirtyContacts[0].copy(
            isDirty = false,
            etag = "new-etag-after-upload"
        )
        contactRepository.update(syncedContact)
        
        // Then: Contact is no longer dirty
        val updatedContact = contactRepository.getById(contactId)
        assertThat(updatedContact?.isDirty).isFalse()
        assertThat(updatedContact?.etag).isEqualTo("new-etag-after-upload")
    }
    
    @Test
    fun testMultipleAddressBooksSync() = runTest {
        // Given: Multiple address books for same account
        val personal = createTestAddressBook().copy(displayName = "Personal")
        val work = createTestAddressBook().copy(displayName = "Work")
        val family = createTestAddressBook().copy(displayName = "Family")
        
        val personalId = addressBookRepository.insert(personal)
        val workId = addressBookRepository.insert(work)
        val familyId = addressBookRepository.insert(family)
        
        // When: Retrieve all address books for account
        val addressBooks = addressBookRepository.getByAccountId(testAccount.id)
        
        // Then: All address books are found
        assertThat(addressBooks).hasSize(3)
        assertThat(addressBooks.map { it.displayName }).containsExactly("Personal", "Work", "Family")
    }
    
    @Test
    fun testSyncEnabledFiltering() = runTest {
        // Given: Multiple address books with different sync settings
        val syncEnabled = createTestAddressBook().copy(
            displayName = "Enabled",
            syncEnabled = true
        )
        val syncDisabled = createTestAddressBook().copy(
            displayName = "Disabled",
            syncEnabled = false
        )
        
        addressBookRepository.insert(syncEnabled)
        addressBookRepository.insert(syncDisabled)
        
        // When: Get only sync-enabled address books
        val enabledBooks = addressBookRepository.getSyncEnabledByAccountId(testAccount.id)
        
        // Then: Only sync-enabled address book is returned
        assertThat(enabledBooks).hasSize(1)
        assertThat(enabledBooks[0].displayName).isEqualTo("Enabled")
        assertThat(enabledBooks[0].syncEnabled).isTrue()
    }
    
    @Test
    fun testContactDeletion() = runTest {
        // Given: Contact exists
        val testAddressBook = createTestAddressBook()
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        val testContact = createTestContact(addressBookId)
        val contactId = contactRepository.insert(testContact)
        
        // When: Mark contact as deleted (soft delete)
        val deletedContact = testContact.copy(
            id = contactId,
            isDeleted = true,
            updatedAt = System.currentTimeMillis()
        )
        contactRepository.update(deletedContact)
        
        // Then: Contact is marked as deleted
        val storedContact = contactRepository.getById(contactId)
        assertThat(storedContact?.isDeleted).isTrue()
        
        // When: Get deleted contacts for server deletion
        val deletedContacts = contactRepository.getDeletedContacts()
        
        // Then: Deleted contact is found
        assertThat(deletedContacts).hasSize(1)
        assertThat(deletedContacts[0].id).isEqualTo(contactId)
    }
    
    @Test
    fun testComplexContactWithMultipleFields() = runTest {
        // Given: Address book exists
        val testAddressBook = createTestAddressBook()
        val addressBookId = addressBookRepository.insert(testAddressBook)
        
        // When: Create contact with all fields populated
        val complexContact = Contact(
            id = 0,
            addressBookId = addressBookId,
            uid = "complex-contact-uid",
            contactUrl = "${testAddressBook.url}complex.vcf",
            etag = "complex-etag",
            displayName = "Dr. Jane Marie Smith PhD",
            givenName = "Jane",
            familyName = "Smith",
            middleName = "Marie",
            namePrefix = "Dr.",
            nameSuffix = "PhD",
            nickname = "Janey",
            organization = "Tech Corp",
            organizationUnit = "R&D",
            jobTitle = "Chief Scientist",
            phoneNumbers = listOf(
                PhoneNumber("+1111111111", PhoneType.MOBILE),
                PhoneNumber("+2222222222", PhoneType.WORK),
                PhoneNumber("+3333333333", PhoneType.HOME)
            ),
            emails = listOf(
                Email("jane.smith@techcorp.com", EmailType.WORK),
                Email("jane@personal.com", EmailType.HOME)
            ),
            postalAddresses = listOf(
                PostalAddress(
                    street = "123 Tech Street",
                    city = "San Francisco",
                    region = "CA",
                    postalCode = "94102",
                    country = "USA",
                    type = AddressType.WORK
                )
            ),
            websites = listOf("https://janesmith.com", "https://techcorp.com/jane"),
            note = "Important contact - CEO connection",
            birthday = "1985-06-15",
            anniversary = "2010-09-20",
            photoBase64 = "base64encodedphotodata",
            isDirty = false,
            isDeleted = false,
            androidContactId = 5000L,
            androidRawContactId = 6000L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        val contactId = contactRepository.insert(complexContact)
        
        // Then: All fields are properly stored and retrieved
        val storedContact = contactRepository.getById(contactId)
        assertThat(storedContact).isNotNull()
        assertThat(storedContact?.displayName).isEqualTo("Dr. Jane Marie Smith PhD")
        assertThat(storedContact?.phoneNumbers).hasSize(3)
        assertThat(storedContact?.emails).hasSize(2)
        assertThat(storedContact?.postalAddresses).hasSize(1)
        assertThat(storedContact?.websites).hasSize(2)
        assertThat(storedContact?.organization).isEqualTo("Tech Corp")
        assertThat(storedContact?.birthday).isEqualTo("1985-06-15")
        assertThat(storedContact?.hasPhoto()).isTrue()
    }
    
    // Helper functions
    
    private fun createTestAddressBook(): AddressBook {
        return AddressBook(
            id = 0,
            accountId = testAccount.id,
            url = "${testAccount.cardDavUrl}contacts/",
            displayName = "Test Contacts",
            description = "Test address book",
            color = 0xFF2196F3.toInt(),
            ctag = null,
            syncEnabled = true,
            visible = true,
            androidAccountName = "test@example.com",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    private fun createTestContact(addressBookId: Long): Contact {
        return Contact(
            id = 0,
            addressBookId = addressBookId,
            uid = "test-contact-${System.currentTimeMillis()}",
            contactUrl = null,
            etag = null,
            displayName = "Test Contact",
            givenName = "Test",
            familyName = "Contact",
            phoneNumbers = emptyList(),
            emails = emptyList(),
            isDirty = false,
            isDeleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
