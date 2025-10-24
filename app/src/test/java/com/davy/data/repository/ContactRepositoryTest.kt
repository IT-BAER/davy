package com.davy.data.repository

import com.davy.data.local.dao.ContactDao
import com.davy.data.local.entity.ContactEntity
import com.davy.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ContactRepository.
 * 
 * Tests the repository layer's interaction with the ContactDao
 * and proper domain/entity mapping including complex data types
 * (phone numbers, emails, postal addresses).
 * 
 * TDD: This test is written BEFORE the implementation is complete.
 */
class ContactRepositoryTest {

    private lateinit var contactDao: ContactDao
    private lateinit var repository: ContactRepository

    private val testAddressBookId = 100L
    private val testContactId = 200L
    private val testUid = "test-contact-uid-001"
    private val testContactUrl = "https://nextcloud.example.com/remote.php/dav/addressbooks/users/user/contacts/test-contact.vcf"
    
    private val testPhoneNumbers = listOf(
        PhoneNumber(number = "+1234567890", type = PhoneType.MOBILE),
        PhoneNumber(number = "+0987654321", type = PhoneType.HOME)
    )
    
    private val testEmails = listOf(
        Email(email = "test@example.com", type = EmailType.WORK),
        Email(email = "personal@example.com", type = EmailType.HOME)
    )
    
    private val testAddresses = listOf(
        PostalAddress(
            street = "123 Main St",
            city = "Springfield",
            region = "IL",
            postalCode = "62701",
            country = "USA",
            type = AddressType.HOME
        )
    )
    
    private val testContact = Contact(
        id = testContactId,
        addressBookId = testAddressBookId,
        uid = testUid,
        contactUrl = testContactUrl,
        etag = "etag-v1",
        displayName = "John Doe",
        givenName = "John",
        familyName = "Doe",
        middleName = "Robert",
        namePrefix = "Dr.",
        nameSuffix = "Jr.",
        nickname = "Johnny",
        organization = "Acme Corp",
        organizationUnit = "Engineering",
        jobTitle = "Software Engineer",
        phoneNumbers = testPhoneNumbers,
        emails = testEmails,
        postalAddresses = testAddresses,
        websites = listOf("https://johndoe.com"),
        note = "Test contact notes",
        birthday = "1990-01-15",
        anniversary = "2015-06-20",
        photoBase64 = "base64encodedphoto",
        isDirty = false,
        isDeleted = false,
        androidContactId = 1000L,
        androidRawContactId = 2000L,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
    
    private val testContactEntity = ContactEntity(
        id = testContactId,
        addressBookId = testAddressBookId,
        uid = testUid,
        contactUrl = testContactUrl,
        etag = "etag-v1",
        displayName = "John Doe",
        givenName = "John",
        familyName = "Doe",
        middleName = "Robert",
        namePrefix = "Dr.",
        nameSuffix = "Jr.",
        nickname = "Johnny",
        organization = "Acme Corp",
        organizationUnit = "Engineering",
        jobTitle = "Software Engineer",
        phoneNumbersJson = """[{"number":"+1234567890","type":"MOBILE"},{"number":"+0987654321","type":"HOME"}]""",
        emailsJson = """[{"email":"test@example.com","type":"WORK"},{"email":"personal@example.com","type":"HOME"}]""",
        postalAddressesJson = """[{"street":"123 Main St","city":"Springfield","region":"IL","postalCode":"62701","country":"USA","type":"HOME"}]""",
        websitesJson = """["https://johndoe.com"]""",
        note = "Test contact notes",
        birthday = "1990-01-15",
        anniversary = "2015-06-20",
        photoBase64 = "base64encodedphoto",
        isDirty = false,
        isDeleted = false,
        androidContactId = 1000L,
        androidRawContactId = 2000L,
        createdAt = testContact.createdAt,
        updatedAt = testContact.updatedAt
    )

    @Before
    fun setUp() {
        contactDao = mockk()
        repository = ContactRepository(contactDao)
    }

    @Test
    fun `insert contact calls dao insert and returns id`() = runTest {
        // Given
        val expectedId = testContactId
        coEvery { contactDao.insert(any()) } returns expectedId

        // When
        val result = repository.insert(testContact)

        // Then
        assertEquals(expectedId, result)
        coVerify { contactDao.insert(any()) }
    }

    @Test
    fun `insertAll contacts calls dao insertAll and returns ids`() = runTest {
        // Given
        val contact1 = testContact.copy(id = 1L, displayName = "Contact 1")
        val contact2 = testContact.copy(id = 2L, displayName = "Contact 2")
        val contacts = listOf(contact1, contact2)
        val expectedIds = listOf(1L, 2L)
        
        coEvery { contactDao.insertAll(any()) } returns expectedIds

        // When
        val result = repository.insertAll(contacts)

        // Then
        assertEquals(expectedIds, result)
        coVerify { contactDao.insertAll(any()) }
    }

    @Test
    fun `update contact calls dao update`() = runTest {
        // Given
        coEvery { contactDao.update(any()) } returns Unit

        // When
        repository.update(testContact)

        // Then
        coVerify { contactDao.update(any()) }
    }

    @Test
    fun `delete contact calls dao delete with id`() = runTest {
        // Given
        coEvery { contactDao.delete(testContactId) } returns Unit

        // When
        repository.delete(testContactId)

        // Then
        coVerify { contactDao.delete(testContactId) }
    }

    @Test
    fun `getById returns contact when found`() = runTest {
        // Given
        coEvery { contactDao.getById(testContactId) } returns testContactEntity

        // When
        val result = repository.getById(testContactId)

        // Then
        assertNotNull(result)
        assertEquals(testContact.displayName, result?.displayName)
        assertEquals(testContact.uid, result?.uid)
        assertEquals(testContact.phoneNumbers.size, result?.phoneNumbers?.size)
        coVerify { contactDao.getById(testContactId) }
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        // Given
        coEvery { contactDao.getById(testContactId) } returns null

        // When
        val result = repository.getById(testContactId)

        // Then
        assertNull(result)
        coVerify { contactDao.getById(testContactId) }
    }

    @Test
    fun `getByUrl returns contact when found`() = runTest {
        // Given
        coEvery { contactDao.getByUrl(testContactUrl) } returns testContactEntity

        // When
        val result = repository.getByUrl(testContactUrl)

        // Then
        assertNotNull(result)
        assertEquals(testContact.displayName, result?.displayName)
        assertEquals(testContact.contactUrl, result?.contactUrl)
        coVerify { contactDao.getByUrl(testContactUrl) }
    }

    @Test
    fun `getByUrl returns null when not found`() = runTest {
        // Given
        coEvery { contactDao.getByUrl(testContactUrl) } returns null

        // When
        val result = repository.getByUrl(testContactUrl)

        // Then
        assertNull(result)
        coVerify { contactDao.getByUrl(testContactUrl) }
    }

    @Test
    fun `getByUid returns contact when found`() = runTest {
        // Given
        coEvery { contactDao.getByUid(testUid) } returns testContactEntity

        // When
        val result = repository.getByUid(testUid)

        // Then
        assertNotNull(result)
        assertEquals(testContact.displayName, result?.displayName)
        assertEquals(testContact.uid, result?.uid)
        coVerify { contactDao.getByUid(testUid) }
    }

    @Test
    fun `getByUid returns null when not found`() = runTest {
        // Given
        coEvery { contactDao.getByUid(testUid) } returns null

        // When
        val result = repository.getByUid(testUid)

        // Then
        assertNull(result)
        coVerify { contactDao.getByUid(testUid) }
    }

    @Test
    fun `getByAddressBookIdFlow returns flow of contacts`() = runTest {
        // Given
        val entity1 = testContactEntity.copy(id = 1L, displayName = "Contact 1")
        val entity2 = testContactEntity.copy(id = 2L, displayName = "Contact 2")
        val entities = listOf(entity1, entity2)
        every { contactDao.getByAddressBookIdFlow(testAddressBookId) } returns flowOf(entities)

        // When
        val result = repository.getByAddressBookIdFlow(testAddressBookId).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("Contact 1", result[0].displayName)
        assertEquals("Contact 2", result[1].displayName)
    }

    @Test
    fun `getByAddressBookId returns list of contacts`() = runTest {
        // Given
        val entity1 = testContactEntity.copy(id = 1L, displayName = "Contact 1")
        val entity2 = testContactEntity.copy(id = 2L, displayName = "Contact 2")
        val entities = listOf(entity1, entity2)
        coEvery { contactDao.getByAddressBookId(testAddressBookId) } returns entities

        // When
        val result = repository.getByAddressBookId(testAddressBookId)

        // Then
        assertEquals(2, result.size)
        assertEquals("Contact 1", result[0].displayName)
        assertEquals("Contact 2", result[1].displayName)
        coVerify { contactDao.getByAddressBookId(testAddressBookId) }
    }

    @Test
    fun `getDirtyContacts returns only dirty contacts`() = runTest {
        // Given
        val dirtyEntity = testContactEntity.copy(id = 1L, isDirty = true)
        val entities = listOf(dirtyEntity)
        coEvery { contactDao.getDirtyContacts() } returns entities

        // When
        val result = repository.getDirtyContacts()

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].isDirty)
        coVerify { contactDao.getDirtyContacts() }
    }

    @Test
    fun `getDeletedContacts returns only deleted contacts`() = runTest {
        // Given
        val deletedEntity = testContactEntity.copy(id = 1L, isDeleted = true)
        val entities = listOf(deletedEntity)
        coEvery { contactDao.getDeletedContacts() } returns entities

        // When
        val result = repository.getDeletedContacts()

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].isDeleted)
        coVerify { contactDao.getDeletedContacts() }
    }

    @Test
    fun `contact with phone numbers maps correctly`() = runTest {
        // Given
        val contactWithPhones = testContact.copy(
            phoneNumbers = listOf(
                PhoneNumber("+1111111111", PhoneType.MOBILE),
                PhoneNumber("+2222222222", PhoneType.WORK)
            )
        )
        coEvery { contactDao.insert(any()) } returns 1L

        // When
        repository.insert(contactWithPhones)

        // Then
        coVerify {
            contactDao.insert(match { entity ->
                entity.phoneNumbersJson.contains("+1111111111") &&
                entity.phoneNumbersJson.contains("MOBILE") &&
                entity.phoneNumbersJson.contains("+2222222222") &&
                entity.phoneNumbersJson.contains("WORK")
            })
        }
    }

    @Test
    fun `contact with emails maps correctly`() = runTest {
        // Given
        val contactWithEmails = testContact.copy(
            emails = listOf(
                Email("work@example.com", EmailType.WORK),
                Email("home@example.com", EmailType.HOME)
            )
        )
        coEvery { contactDao.insert(any()) } returns 1L

        // When
        repository.insert(contactWithEmails)

        // Then
        coVerify {
            contactDao.insert(match { entity ->
                entity.emailsJson.contains("work@example.com") &&
                entity.emailsJson.contains("WORK") &&
                entity.emailsJson.contains("home@example.com") &&
                entity.emailsJson.contains("HOME")
            })
        }
    }

    @Test
    fun `contact with postal addresses maps correctly`() = runTest {
        // Given
        val contactWithAddresses = testContact.copy(
            postalAddresses = listOf(
                PostalAddress(
                    street = "456 Oak Ave",
                    city = "Portland",
                    region = "OR",
                    postalCode = "97201",
                    country = "USA",
                    type = AddressType.WORK
                )
            )
        )
        coEvery { contactDao.insert(any()) } returns 1L

        // When
        repository.insert(contactWithAddresses)

        // Then
        coVerify {
            contactDao.insert(match { entity ->
                entity.postalAddressesJson.contains("456 Oak Ave") &&
                entity.postalAddressesJson.contains("Portland") &&
                entity.postalAddressesJson.contains("OR") &&
                entity.postalAddressesJson.contains("97201") &&
                entity.postalAddressesJson.contains("WORK")
            })
        }
    }

    @Test
    fun `getFullName returns formatted name`() {
        // Given
        val contact = testContact.copy(
            namePrefix = "Dr.",
            givenName = "Jane",
            middleName = "Marie",
            familyName = "Smith",
            nameSuffix = "PhD"
        )

        // When
        val fullName = contact.getFullName()

        // Then
        assertEquals("Dr. Jane Marie Smith PhD", fullName)
    }

    @Test
    fun `getFullName falls back to displayName when name parts empty`() {
        // Given
        val contact = testContact.copy(
            displayName = "Company Name",
            givenName = null,
            familyName = null,
            namePrefix = null,
            nameSuffix = null,
            middleName = null
        )

        // When
        val fullName = contact.getFullName()

        // Then
        assertEquals("Company Name", fullName)
    }

    @Test
    fun `getInitials returns first letters of names`() {
        // Given
        val contact = testContact.copy(
            givenName = "John",
            familyName = "Doe"
        )

        // When
        val initials = contact.getInitials()

        // Then
        assertEquals("JD", initials)
    }

    @Test
    fun `getInitials falls back to displayName when names empty`() {
        // Given
        val contact = testContact.copy(
            displayName = "Acme Corp",
            givenName = null,
            familyName = null
        )

        // When
        val initials = contact.getInitials()

        // Then
        assertEquals("A", initials)
    }

    @Test
    fun `hasPhoto returns true when photo exists`() {
        // Given
        val contact = testContact.copy(photoBase64 = "base64data")

        // Then
        assertTrue(contact.hasPhoto())
    }

    @Test
    fun `hasPhoto returns false when photo is null`() {
        // Given
        val contact = testContact.copy(photoBase64 = null)

        // Then
        assertFalse(contact.hasPhoto())
    }

    @Test
    fun `isSynced returns true when url and etag exist`() {
        // Given
        val contact = testContact.copy(
            contactUrl = "https://example.com/contact.vcf",
            etag = "etag-123"
        )

        // Then
        assertTrue(contact.isSynced())
    }

    @Test
    fun `isSynced returns false when url or etag missing`() {
        // Given
        val contactNoUrl = testContact.copy(contactUrl = null, etag = "etag-123")
        val contactNoEtag = testContact.copy(contactUrl = "https://example.com", etag = null)

        // Then
        assertFalse(contactNoUrl.isSynced())
        assertFalse(contactNoEtag.isSynced())
    }

    @Test
    fun `getPrimaryPhone returns mobile first`() {
        // Given
        val contact = testContact.copy(
            phoneNumbers = listOf(
                PhoneNumber("+1111111111", PhoneType.HOME),
                PhoneNumber("+2222222222", PhoneType.MOBILE),
                PhoneNumber("+3333333333", PhoneType.WORK)
            )
        )

        // When
        val primary = contact.getPrimaryPhone()

        // Then
        assertEquals("+2222222222", primary?.number)
        assertEquals(PhoneType.MOBILE, primary?.type)
    }

    @Test
    fun `getPrimaryPhone returns first phone when no mobile`() {
        // Given
        val contact = testContact.copy(
            phoneNumbers = listOf(
                PhoneNumber("+1111111111", PhoneType.HOME),
                PhoneNumber("+2222222222", PhoneType.WORK)
            )
        )

        // When
        val primary = contact.getPrimaryPhone()

        // Then
        assertEquals("+1111111111", primary?.number)
    }

    @Test
    fun `getPrimaryEmail returns work first`() {
        // Given
        val contact = testContact.copy(
            emails = listOf(
                Email("home@example.com", EmailType.HOME),
                Email("work@example.com", EmailType.WORK),
                Email("other@example.com", EmailType.OTHER)
            )
        )

        // When
        val primary = contact.getPrimaryEmail()

        // Then
        assertEquals("work@example.com", primary?.email)
        assertEquals(EmailType.WORK, primary?.type)
    }

    @Test
    fun `getPrimaryEmail returns first email when no work`() {
        // Given
        val contact = testContact.copy(
            emails = listOf(
                Email("home@example.com", EmailType.HOME),
                Email("other@example.com", EmailType.OTHER)
            )
        )

        // When
        val primary = contact.getPrimaryEmail()

        // Then
        assertEquals("home@example.com", primary?.email)
    }

    @Test
    fun `PostalAddress format returns multi-line address`() {
        // Given
        val address = PostalAddress(
            street = "123 Main St",
            city = "Springfield",
            region = "IL",
            postalCode = "62701",
            country = "USA",
            type = AddressType.HOME
        )

        // When
        val formatted = address.format()

        // Then
        assertTrue(formatted.contains("123 Main St"))
        assertTrue(formatted.contains("Springfield"))
        assertTrue(formatted.contains("IL"))
        assertTrue(formatted.contains("62701"))
        assertTrue(formatted.contains("USA"))
    }
}
