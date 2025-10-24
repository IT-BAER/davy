package com.davy.data.repository

import com.davy.data.local.dao.AddressBookDao
import com.davy.data.local.entity.AddressBookEntity
import com.davy.domain.model.AddressBook
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
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AddressBookRepository.
 * 
 * Tests the repository layer's interaction with the AddressBookDao
 * and proper domain/entity mapping.
 * 
 * TDD: This test is written BEFORE the implementation is complete.
 */
class AddressBookRepositoryTest {

    private lateinit var addressBookDao: AddressBookDao
    private lateinit var repository: AddressBookRepository

    private val testAccountId = 1L
    private val testAddressBookId = 100L
    private val testAddressBookUrl = "https://nextcloud.example.com/remote.php/dav/addressbooks/users/user/contacts/"
    
    private val testAddressBook = AddressBook(
        id = testAddressBookId,
        accountId = testAccountId,
        url = testAddressBookUrl,
        displayName = "Personal Contacts",
        description = "My personal contacts",
        color = 0xFF2196F3.toInt(),
        ctag = "ctag-v1",
        syncEnabled = true,
        visible = true,
        androidAccountName = "test@example.com",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
    
    private val testAddressBookEntity = AddressBookEntity(
        id = testAddressBookId,
        accountId = testAccountId,
        url = testAddressBookUrl,
        displayName = "Personal Contacts",
        description = "My personal contacts",
        color = 0xFF2196F3.toInt(),
        ctag = "ctag-v1",
        syncEnabled = true,
        visible = true,
        androidAccountName = "test@example.com",
        createdAt = testAddressBook.createdAt,
        updatedAt = testAddressBook.updatedAt
    )

    @Before
    fun setUp() {
        addressBookDao = mockk()
        repository = AddressBookRepository(addressBookDao)
    }

    @Test
    fun `insert address book calls dao insert and returns id`() = runTest {
        // Given
        val expectedId = testAddressBookId
        coEvery { addressBookDao.insert(any()) } returns expectedId

        // When
        val result = repository.insert(testAddressBook)

        // Then
        assertEquals(expectedId, result)
        coVerify { addressBookDao.insert(any()) }
    }

    @Test
    fun `insertAll address books calls dao insertAll and returns ids`() = runTest {
        // Given
        val addressBook1 = testAddressBook.copy(id = 1L, displayName = "Contacts 1")
        val addressBook2 = testAddressBook.copy(id = 2L, displayName = "Contacts 2")
        val addressBooks = listOf(addressBook1, addressBook2)
        val expectedIds = listOf(1L, 2L)
        
        coEvery { addressBookDao.insertAll(any()) } returns expectedIds

        // When
        val result = repository.insertAll(addressBooks)

        // Then
        assertEquals(expectedIds, result)
        coVerify { addressBookDao.insertAll(any()) }
    }

    @Test
    fun `update address book calls dao update`() = runTest {
        // Given
        coEvery { addressBookDao.update(any()) } returns Unit

        // When
        repository.update(testAddressBook)

        // Then
        coVerify { addressBookDao.update(any()) }
    }

    @Test
    fun `delete address book calls dao delete`() = runTest {
        // Given
        coEvery { addressBookDao.delete(testAddressBookId) } returns Unit

        // When
        repository.delete(testAddressBook)

        // Then
        coVerify { addressBookDao.delete(testAddressBookId) }
    }

    @Test
    fun `getById returns address book when found`() = runTest {
        // Given
        coEvery { addressBookDao.getById(testAddressBookId) } returns testAddressBookEntity

        // When
        val result = repository.getById(testAddressBookId)

        // Then
        assertNotNull(result)
        assertEquals(testAddressBook.displayName, result?.displayName)
        assertEquals(testAddressBook.url, result?.url)
        coVerify { addressBookDao.getById(testAddressBookId) }
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        // Given
        coEvery { addressBookDao.getById(testAddressBookId) } returns null

        // When
        val result = repository.getById(testAddressBookId)

        // Then
        assertNull(result)
        coVerify { addressBookDao.getById(testAddressBookId) }
    }

    @Test
    fun `getByUrl returns address book when found`() = runTest {
        // Given
        coEvery { addressBookDao.getByUrl(testAddressBookUrl) } returns testAddressBookEntity

        // When
        val result = repository.getByUrl(testAddressBookUrl)

        // Then
        assertNotNull(result)
        assertEquals(testAddressBook.displayName, result?.displayName)
        assertEquals(testAddressBook.url, result?.url)
        coVerify { addressBookDao.getByUrl(testAddressBookUrl) }
    }

    @Test
    fun `getByUrl returns null when not found`() = runTest {
        // Given
        coEvery { addressBookDao.getByUrl(testAddressBookUrl) } returns null

        // When
        val result = repository.getByUrl(testAddressBookUrl)

        // Then
        assertNull(result)
        coVerify { addressBookDao.getByUrl(testAddressBookUrl) }
    }

    @Test
    fun `getAllFlow returns flow of address books`() = runTest {
        // Given
        val entity1 = testAddressBookEntity.copy(id = 1L, displayName = "Contacts 1")
        val entity2 = testAddressBookEntity.copy(id = 2L, displayName = "Contacts 2")
        val entities = listOf(entity1, entity2)
        every { addressBookDao.getAllFlow() } returns flowOf(entities)

        // When
        val result = repository.getAllFlow().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("Contacts 1", result[0].displayName)
        assertEquals("Contacts 2", result[1].displayName)
    }

    @Test
    fun `getAll returns list of address books`() = runTest {
        // Given
        val entity1 = testAddressBookEntity.copy(id = 1L, displayName = "Contacts 1")
        val entity2 = testAddressBookEntity.copy(id = 2L, displayName = "Contacts 2")
        val entities = listOf(entity1, entity2)
        coEvery { addressBookDao.getAll() } returns entities

        // When
        val result = repository.getAll()

        // Then
        assertEquals(2, result.size)
        assertEquals("Contacts 1", result[0].displayName)
        assertEquals("Contacts 2", result[1].displayName)
        coVerify { addressBookDao.getAll() }
    }

    @Test
    fun `getByAccountId returns address books for account`() = runTest {
        // Given
        val entity1 = testAddressBookEntity.copy(id = 1L, displayName = "Contacts 1")
        val entity2 = testAddressBookEntity.copy(id = 2L, displayName = "Contacts 2")
        val entities = listOf(entity1, entity2)
        coEvery { addressBookDao.getByAccountId(testAccountId) } returns entities

        // When
        val result = repository.getByAccountId(testAccountId)

        // Then
        assertEquals(2, result.size)
        assertEquals(testAccountId, result[0].accountId)
        assertEquals(testAccountId, result[1].accountId)
        coVerify { addressBookDao.getByAccountId(testAccountId) }
    }

    @Test
    fun `getByAccountIdFlow returns flow of address books for account`() = runTest {
        // Given
        val entity1 = testAddressBookEntity.copy(id = 1L, displayName = "Contacts 1")
        val entity2 = testAddressBookEntity.copy(id = 2L, displayName = "Contacts 2")
        val entities = listOf(entity1, entity2)
        every { addressBookDao.getByAccountIdFlow(testAccountId) } returns flowOf(entities)

        // When
        val result = repository.getByAccountIdFlow(testAccountId).first()

        // Then
        assertEquals(2, result.size)
        assertEquals(testAccountId, result[0].accountId)
        assertEquals(testAccountId, result[1].accountId)
    }

    @Test
    fun `getSyncEnabledByAccountId returns only sync-enabled address books`() = runTest {
        // Given
        val entity1 = testAddressBookEntity.copy(id = 1L, displayName = "Enabled", syncEnabled = true)
        val entity2 = testAddressBookEntity.copy(id = 2L, displayName = "Disabled", syncEnabled = false)
        val enabledEntities = listOf(entity1) // Dao should only return sync-enabled
        coEvery { addressBookDao.getSyncEnabledByAccountId(testAccountId) } returns enabledEntities

        // When
        val result = repository.getSyncEnabledByAccountId(testAccountId)

        // Then
        assertEquals(1, result.size)
        assertEquals("Enabled", result[0].displayName)
        assertTrue(result[0].syncEnabled)
        coVerify { addressBookDao.getSyncEnabledByAccountId(testAccountId) }
    }

    @Test
    fun `address book domain model maps correctly to entity`() = runTest {
        // Given
        val addressBook = AddressBook(
            id = 0,
            accountId = 1L,
            url = "https://example.com/addressbooks/contacts/",
            displayName = "Test Contacts",
            description = "Test description",
            color = 0xFF00FF00.toInt(),
            ctag = "test-ctag",
            syncEnabled = true,
            visible = true,
            androidAccountName = "test@example.com",
            createdAt = 123456789L,
            updatedAt = 987654321L
        )
        
        coEvery { addressBookDao.insert(any()) } returns 100L

        // When
        val insertedId = repository.insert(addressBook)

        // Then
        coVerify {
            addressBookDao.insert(match { entity ->
                entity.accountId == addressBook.accountId &&
                entity.url == addressBook.url &&
                entity.displayName == addressBook.displayName &&
                entity.description == addressBook.description &&
                entity.color == addressBook.color &&
                entity.ctag == addressBook.ctag &&
                entity.syncEnabled == addressBook.syncEnabled &&
                entity.visible == addressBook.visible &&
                entity.androidAccountName == addressBook.androidAccountName
            })
        }
    }

    @Test
    fun `isSynced returns true when ctag exists`() {
        // Given
        val addressBookWithCtag = testAddressBook.copy(ctag = "some-ctag")

        // Then
        assertTrue(addressBookWithCtag.isSynced())
    }

    @Test
    fun `needsSync returns true when sync enabled and not synced`() {
        // Given
        val addressBookNeedingSync = testAddressBook.copy(
            syncEnabled = true,
            ctag = null
        )

        // Then
        assertTrue(addressBookNeedingSync.needsSync())
    }
}
