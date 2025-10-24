package com.davy.data.contentprovider

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.davy.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for Android ContactsContract integration.
 * 
 * Tests the ContentProvider adapter for syncing contact data between
 * the app's local database and Android's native Contacts app via ContactsContract.
 * 
 * Tests include:
 * - Contact creation in Android Contacts app
 * - Contact retrieval and mapping
 * - Contact updates and sync
 * - Contact deletion
 * - Phone numbers, emails, and addresses
 * - Contact photos
 * - Bidirectional sync
 * 
 * TDD: This test is written BEFORE the full implementation.
 * 
 * Requires: READ_CONTACTS and WRITE_CONTACTS permissions
 */
@RunWith(AndroidJUnit4::class)
class ContactContentProviderTest {
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )
    
    private lateinit var context: Context
    private lateinit var adapter: ContactContentProviderAdapter
    private val testContactIds = mutableListOf<Long>()
    private val testRawContactIds = mutableListOf<Long>()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adapter = ContactContentProviderAdapter(context)
        
        // Clean up any existing test contacts
        cleanupTestContacts()
    }
    
    @After
    fun tearDown() {
        // Clean up test data
        testContactIds.forEach { contactId ->
            deleteTestContact(contactId)
        }
        testRawContactIds.forEach { rawContactId ->
            deleteTestRawContact(rawContactId)
        }
        cleanupTestContacts()
    }
    
    @Test
    fun testCreateContactInAndroidContacts() {
        // Given: Contact model from DAVy app
        val davyContact = Contact(
            id = 1L,
            addressBookId = 1L,
            uid = "test-contact-uid-001",
            contactUrl = "https://nextcloud.example.com/contact1.vcf",
            etag = "test-etag",
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
        
        // When: Insert contact into Android Contacts via ContentProvider
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // Then: Contact exists in Android Contacts app
        assertThat(androidRawContactId).isGreaterThan(0L)
        
        // Get the aggregate contact ID
        val contactId = getContactIdFromRawContact(androidRawContactId)
        assertThat(contactId).isGreaterThan(0L)
        testContactIds.add(contactId)
        
        // Verify contact name
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                androidRawContactId.toString(),
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            ),
            null
        )
        
        assertThat(cursor).isNotNull()
        cursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME))
            val givenName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME))
            val familyName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME))
            assertThat(displayName).isEqualTo("John Doe")
            assertThat(givenName).isEqualTo("John")
            assertThat(familyName).isEqualTo("Doe")
        }
    }
    
    @Test
    fun testCreateContactWithPhoneNumbers() {
        // Given: Contact with multiple phone numbers
        val davyContact = Contact(
            id = 1L,
            addressBookId = 1L,
            uid = "test-contact-uid-002",
            displayName = "Jane Smith",
            givenName = "Jane",
            familyName = "Smith",
            phoneNumbers = listOf(
                PhoneNumber("+1111111111", PhoneType.MOBILE),
                PhoneNumber("+2222222222", PhoneType.WORK),
                PhoneNumber("+3333333333", PhoneType.HOME)
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // When: Insert contact
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // Then: All phone numbers are stored
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                androidRawContactId.toString(),
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            ),
            null
        )
        
        assertThat(cursor).isNotNull()
        cursor?.use {
            assertThat(it.count).isEqualTo(3)
            val phoneNumbers = mutableListOf<String>()
            while (it.moveToNext()) {
                val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                phoneNumbers.add(number)
            }
            assertThat(phoneNumbers).containsExactly("+1111111111", "+2222222222", "+3333333333")
        }
    }
    
    @Test
    fun testCreateContactWithEmails() {
        // Given: Contact with multiple emails
        val davyContact = Contact(
            id = 1L,
            addressBookId = 1L,
            uid = "test-contact-uid-003",
            displayName = "Bob Johnson",
            emails = listOf(
                Email("bob.work@example.com", EmailType.WORK),
                Email("bob.personal@example.com", EmailType.HOME)
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // When: Insert contact
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // Then: All emails are stored
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                androidRawContactId.toString(),
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
            ),
            null
        )
        
        assertThat(cursor).isNotNull()
        cursor?.use {
            assertThat(it.count).isEqualTo(2)
            val emails = mutableListOf<String>()
            while (it.moveToNext()) {
                val email = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
                emails.add(email)
            }
            assertThat(emails).containsExactly("bob.work@example.com", "bob.personal@example.com")
        }
    }
    
    @Test
    fun testCreateContactWithPostalAddress() {
        // Given: Contact with postal address
        val davyContact = Contact(
            id = 1L,
            addressBookId = 1L,
            uid = "test-contact-uid-004",
            displayName = "Alice Brown",
            postalAddresses = listOf(
                PostalAddress(
                    street = "123 Main Street",
                    city = "San Francisco",
                    region = "CA",
                    postalCode = "94102",
                    country = "USA",
                    type = AddressType.HOME
                )
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // When: Insert contact
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // Then: Postal address is stored
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                androidRawContactId.toString(),
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
            ),
            null
        )
        
        assertThat(cursor).isNotNull()
        cursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val street = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.STREET))
            val city = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.CITY))
            val region = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.REGION))
            assertThat(street).isEqualTo("123 Main Street")
            assertThat(city).isEqualTo("San Francisco")
            assertThat(region).isEqualTo("CA")
        }
    }
    
    @Test
    fun testUpdateContactInAndroidContacts() {
        // Given: Contact exists in Android Contacts
        val davyContact = createTestDavyContact()
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // When: Update contact
        val updatedContact = davyContact.copy(
            displayName = "John Robert Doe",
            middleName = "Robert",
            phoneNumbers = listOf(
                PhoneNumber("+9999999999", PhoneType.MOBILE)
            )
        )
        val updateResult = adapter.updateContact(
            updatedContact,
            androidRawContactId,
            testAccountName,
            testAccountType
        )
        
        // Then: Contact is updated in Android Contacts
        assertThat(updateResult).isTrue()
        
        // Verify updated name
        val nameCursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                androidRawContactId.toString(),
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            ),
            null
        )
        
        nameCursor?.use {
            assertThat(it.moveToFirst()).isTrue()
            val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME))
            val middleName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME))
            assertThat(displayName).isEqualTo("John Robert Doe")
            assertThat(middleName).isEqualTo("Robert")
        }
    }
    
    @Test
    fun testDeleteContactFromAndroidContacts() {
        // Given: Contact exists in Android Contacts
        val davyContact = createTestDavyContact()
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // Verify contact exists
        val cursorBefore = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(androidRawContactId.toString()),
            null
        )
        assertThat(cursorBefore?.count).isEqualTo(1)
        cursorBefore?.close()
        
        // When: Delete contact
        val deleteResult = adapter.deleteContact(androidRawContactId)
        
        // Then: Contact is deleted from Android Contacts
        assertThat(deleteResult).isTrue()
        
        val cursorAfter = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.DELETED),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(androidRawContactId.toString()),
            null
        )
        
        // Contact should be marked as deleted
        cursorAfter?.use {
            if (it.moveToFirst()) {
                val deleted = it.getInt(it.getColumnIndexOrThrow(ContactsContract.RawContacts.DELETED))
                assertThat(deleted).isEqualTo(1)
            }
        }
    }
    
    @Test
    fun testBidirectionalSync() {
        // Given: Contact created in DAVy app
        val davyContact = createTestDavyContact()
        val androidRawContactId = adapter.insertContact(davyContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // When: Contact is modified in Android Contacts app (simulated)
        val updatedContact = davyContact.copy(
            displayName = "Modified via Android",
            androidRawContactId = androidRawContactId
        )
        adapter.updateContact(
            updatedContact,
            androidRawContactId,
            "test_account@example.com",
            "com.davy"
        )
        
        // Then: Changes can be detected and synced back to DAVy
        val syncedContact = adapter.getContact(androidRawContactId)
        assertThat(syncedContact).isNotNull()
        assertThat(syncedContact?.displayName).isEqualTo("Modified via Android")
        assertThat(syncedContact?.androidRawContactId).isEqualTo(androidRawContactId)
    }
    
    @Test
    fun testComplexContactWithAllFields() {
        // Given: Contact with all possible fields
        val complexContact = Contact(
            id = 1L,
            addressBookId = 1L,
            uid = "complex-contact-uid",
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
                Email("jane.work@techcorp.com", EmailType.WORK),
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
            websites = listOf("https://janesmith.com"),
            note = "Important contact",
            birthday = "1985-06-15",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // When: Insert complex contact
        val androidRawContactId = adapter.insertContact(complexContact, "test_account@example.com", "com.davy")
        testRawContactIds.add(androidRawContactId)
        
        // Then: All fields are properly stored
        assertThat(androidRawContactId).isGreaterThan(0L)
        
        // Retrieve and verify
        val retrievedContact = adapter.getContact(androidRawContactId)
        assertThat(retrievedContact).isNotNull()
        assertThat(retrievedContact?.displayName).isEqualTo("Dr. Jane Marie Smith PhD")
        assertThat(retrievedContact?.organization).isEqualTo("Tech Corp")
        assertThat(retrievedContact?.jobTitle).isEqualTo("Chief Scientist")
        assertThat(retrievedContact?.phoneNumbers?.size).isEqualTo(3)
        assertThat(retrievedContact?.emails?.size).isEqualTo(2)
        assertThat(retrievedContact?.postalAddresses?.size).isEqualTo(1)
    }
    
    // Helper functions
    
    private fun createTestDavyContact(): Contact {
        return Contact(
            id = 0,
            addressBookId = 1L,
            uid = "test-contact-${System.currentTimeMillis()}",
            displayName = "John Doe",
            givenName = "John",
            familyName = "Doe",
            phoneNumbers = listOf(
                PhoneNumber("+1234567890", PhoneType.MOBILE)
            ),
            emails = listOf(
                Email("john@example.com", EmailType.HOME)
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    private fun getContactIdFromRawContact(rawContactId: Long): Long {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )
        
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID))
            } else {
                -1L
            }
        } ?: -1L
    }
    
    private fun deleteTestContact(contactId: Long) {
        context.contentResolver.delete(
            ContactsContract.Contacts.CONTENT_URI,
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString())
        )
    }
    
    private fun deleteTestRawContact(rawContactId: Long) {
        context.contentResolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString())
        )
    }
    
    private fun cleanupTestContacts() {
        // Delete all contacts for test account
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf("test_account@example.com", "com.davy"),
            null
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val rawContactId = it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
                deleteTestRawContact(rawContactId)
            }
        }
    }
}
