package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
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
 * Unit tests for ContactSyncWorker.
 * 
 * Tests the WorkManager worker for contact synchronization with mocked dependencies.
 * 
 * TDD: This test is written BEFORE the implementation.
 */
@DisplayName("ContactSyncWorker Tests")
class ContactSyncWorkerTest {
    
    private lateinit var context: Context
    private lateinit var addressBookRepository: AddressBookRepository
    private lateinit var contactRepository: ContactRepository
    
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
    
    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        addressBookRepository = mockk()
        contactRepository = mockk()
    }
    
    @Test
    @DisplayName("doWork with valid account ID returns success")
    fun `doWork with valid account ID returns success`() = runTest {
        // Given
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(testAccountId) } returns listOf(testAddressBook)
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify { addressBookRepository.getSyncEnabledByAccountId(testAccountId) }
    }
    
    @Test
    @DisplayName("doWork with no account ID syncs all accounts")
    fun `doWork with no account ID syncs all accounts`() = runTest {
        // Given
        val inputData = workDataOf()
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(any()) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }
    
    @Test
    @DisplayName("doWork with sync-disabled address books skips them")
    fun `doWork with sync-disabled address books skips them`() = runTest {
        // Given
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(testAccountId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 0) { addressBookRepository.update(any()) }
    }
    
    @Test
    @DisplayName("doWork with repository exception returns retry")
    fun `doWork with repository exception returns retry`() = runTest {
        // Given
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(testAccountId) } throws RuntimeException("Network error")
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .setRunAttemptCount(0)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
    }
    
    @Test
    @DisplayName("doWork after max retries returns failure")
    fun `doWork after max retries returns failure`() = runTest {
        // Given
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(testAccountId) } throws RuntimeException("Network error")
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .setRunAttemptCount(3) // Simulate max retries reached
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }
    
    @Test
    @DisplayName("doWork syncs multiple address books for account")
    fun `doWork syncs multiple address books for account`() = runTest {
        // Given
        val addressBook1 = testAddressBook.copy(id = 100L, displayName = "Personal")
        val addressBook2 = testAddressBook.copy(id = 200L, displayName = "Work")
        val addressBook3 = testAddressBook.copy(id = 300L, displayName = "Family")
        
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(testAccountId) } returns listOf(
            addressBook1, addressBook2, addressBook3
        )
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(any()) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 3) { addressBookRepository.update(any()) }
    }
    
    @Test
    @DisplayName("doWork updates address book timestamps after sync")
    fun `doWork updates address book timestamps after sync`() = runTest {
        // Given
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to testAccountId
        )
        
        val originalTimestamp = System.currentTimeMillis() - 10000
        val addressBookWithOldTimestamp = testAddressBook.copy(updatedAt = originalTimestamp)
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(testAccountId) } returns listOf(addressBookWithOldTimestamp)
        coEvery { addressBookRepository.update(any()) } returns Unit
        coEvery { contactRepository.getByAddressBookId(testAddressBookId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify { 
            addressBookRepository.update(match { addressBook ->
                addressBook.updatedAt > originalTimestamp
            })
        }
    }
    
    @Test
    @DisplayName("doWork with invalid account ID returns success with no work")
    fun `doWork with invalid account ID returns success with no work`() = runTest {
        // Given
        val invalidAccountId = -1L
        val inputData = workDataOf(
            ContactSyncWorker.INPUT_ACCOUNT_ID to invalidAccountId
        )
        
        coEvery { addressBookRepository.getSyncEnabledByAccountId(invalidAccountId) } returns emptyList()
        
        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify { addressBookRepository.getSyncEnabledByAccountId(invalidAccountId) }
        coVerify(exactly = 0) { addressBookRepository.update(any()) }
    }
}
