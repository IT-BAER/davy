package com.davy.domain.usecase

import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.ContactRepository
import com.davy.domain.model.AddressBook
import com.davy.domain.model.Contact
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Unit tests for SyncContactsUseCase.
 * 
 * Tests the contact synchronization logic with mocked dependencies.
 * 
 * TDD: This test is written BEFORE the implementation.
 */
@DisplayName("SyncContactsUseCase Tests")
class SyncContactsUseCaseTest {
    
    private lateinit var addressBookRepository: AddressBookRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var useCase: SyncContactsUseCase
    
    private val testAccountId = 1L
    private val testAddressBookId = 100L
    private val testAddressBook = AddressBook(
        id = testAddressBookId,
        accountId = testAccountId,
        url = "https://nextcloud.example.com/remote.php/dav/addressbooks/users/user/contacts/",
        displayName = "Test Contacts",
        description = "Test address book",
        color = 0xFF2196F3.toInt(),
        ctag = "initial-ctag",
        syncEnabled = true,
        visible = true,
        androidAccountName = "test@example.com",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
    
    private val testContact = Contact(
        id = 1L,
        addressBookId = testAddressBookId,
        uid = "test-contact-001",
        contactUrl = "https://nextcloud.example.com/remote.php/dav/addressbooks/users/user/contacts/test.vcf",
        etag = "etag-v1",
        displayName = "John Doe",
        givenName = "John",
        familyName = "Doe",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
    
    @BeforeEach
    fun setup() {
        addressBookRepository = mockk()
        contactRepository = mockk()
        
        useCase = SyncContactsUseCase(
            addressBookRepository = addressBookRepository,
            contactRepository = contactRepository
        )
    }
    
    @Test
    @DisplayName("invoke with valid address book ID performs sync successfully")
    fun `invoke with valid address book ID performs sync successfully`() = runTest {
        // Given
        coEvery { addressBookRepository.getById(testAddressBookId) } returns testAddressBook
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result).isInstanceOf(SyncContactsUseCase.SyncResult::class.java)
        assertThat(result.success).isTrue()
        coVerify { addressBookRepository.getById(testAddressBookId) }
        coVerify { addressBookRepository.update(any()) }
    }
    
    @Test
    @DisplayName("invoke with non-existent address book returns failure")
    fun `invoke with non-existent address book returns failure`() = runTest {
        // Given
        val nonExistentAddressBookId = 999L
        coEvery { addressBookRepository.getById(nonExistentAddressBookId) } returns null
        
        // When
        val result = useCase(nonExistentAddressBookId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Address book not found")
    }
    
    @Test
    @DisplayName("invoke with disabled sync address book returns failure")
    fun `invoke with disabled sync address book returns failure`() = runTest {
        // Given
        val disabledAddressBook = testAddressBook.copy(syncEnabled = false)
        coEvery { addressBookRepository.getById(testAddressBookId) } returns disabledAddressBook
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Sync is disabled")
    }
    
    @Test
    @DisplayName("invoke updates address book lastSyncedAt timestamp")
    fun `invoke updates address book lastSyncedAt timestamp`() = runTest {
        // Given
        val originalTimestamp = System.currentTimeMillis() - 10000
        val addressBookWithOldTimestamp = testAddressBook.copy(updatedAt = originalTimestamp)
        coEvery { addressBookRepository.getById(testAddressBookId) } returns addressBookWithOldTimestamp
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isTrue()
        coVerify { 
            addressBookRepository.update(match { addressBook ->
                addressBook.updatedAt > originalTimestamp
            })
        }
    }
    
    @Test
    @DisplayName("invoke handles sync failure gracefully")
    fun `invoke handles sync failure gracefully`() = runTest {
        // Given
        coEvery { addressBookRepository.getById(testAddressBookId) } returns testAddressBook
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } throws RuntimeException("Network error")
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Network error")
    }
    
    @Test
    @DisplayName("invoke returns correct sync statistics")
    fun `invoke returns correct sync statistics`() = runTest {
        // Given
        val existingContacts = listOf(
            testContact.copy(id = 1L),
            testContact.copy(id = 2L),
            testContact.copy(id = 3L)
        )
        coEvery { addressBookRepository.getById(testAddressBookId) } returns testAddressBook
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns existingContacts
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isTrue()
        assertThat(result.contactsProcessed).isEqualTo(3)
    }
    
    @Test
    @DisplayName("invoke with empty address book returns success with zero contacts")
    fun `invoke with empty address book returns success with zero contacts`() = runTest {
        // Given
        coEvery { addressBookRepository.getById(testAddressBookId) } returns testAddressBook
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isTrue()
        assertThat(result.contactsProcessed).isEqualTo(0)
    }
    
    @Test
    @DisplayName("invoke updates ctag after successful sync")
    fun `invoke updates ctag after successful sync`() = runTest {
        // Given
        val oldCtag = "old-ctag"
        val addressBookWithOldCtag = testAddressBook.copy(ctag = oldCtag)
        coEvery { addressBookRepository.getById(testAddressBookId) } returns addressBookWithOldCtag
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isTrue()
        coVerify { 
            addressBookRepository.update(match { addressBook ->
                addressBook.ctag != oldCtag
            })
        }
    }
    
    @Test
    @DisplayName("invoke handles exception during address book update")
    fun `invoke handles exception during address book update`() = runTest {
        // Given
        coEvery { addressBookRepository.getById(testAddressBookId) } returns testAddressBook
        coEvery { addressBookRepository.update(any()) } throws RuntimeException("Database error")
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        // When
        val result = useCase(testAddressBookId)
        
        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Database error")
    }
}
