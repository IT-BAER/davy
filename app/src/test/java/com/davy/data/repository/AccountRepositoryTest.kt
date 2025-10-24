package com.davy.data.repository

import com.davy.domain.model.Account
import com.davy.domain.model.AuthType
import com.davy.util.TestUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for AccountRepository.
 * 
 * Tests account CRUD operations using FakeAccountRepository.
 */
class AccountRepositoryTest {
    
    private lateinit var repository: FakeAccountRepository
    
    @BeforeEach
    fun setup() {
        repository = FakeAccountRepository()
    }
    
    @Test
    fun `insert account returns new ID`() = runTest {
        // Given
        val account = Account(
            id = 0,
            accountName = "test@nextcloud.example.com",
            serverUrl = "https://nextcloud.example.com",
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com",
            calendarEnabled = true,
            contactsEnabled = true,
            tasksEnabled = true,
            createdAt = System.currentTimeMillis(),
            lastAuthenticatedAt = System.currentTimeMillis(),
            authType = AuthType.BASIC,
            certificateFingerprint = null
        )
        
        // When
        val id = repository.insert(account)
        
        // Then
        assertThat(id).isGreaterThan(0)
    }
    
    @Test
    fun `getById returns correct account`() = runTest {
        // Given
        val account = Account(
            id = 0,
            accountName = "test@nextcloud.example.com",
            serverUrl = "https://nextcloud.example.com",
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com",
            calendarEnabled = true,
            contactsEnabled = true,
            tasksEnabled = true,
            createdAt = System.currentTimeMillis(),
            lastAuthenticatedAt = System.currentTimeMillis(),
            authType = AuthType.BASIC,
            certificateFingerprint = null
        )
        val insertedId = repository.insert(account)
        
        // When
        val retrieved = repository.getById(insertedId)
        
        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.accountName).isEqualTo("test@nextcloud.example.com")
    }
    
    @Test
    fun `getByAccountName returns correct account`() = runTest {
        // Given
        val account = Account(
            id = 0,
            accountName = "test@nextcloud.example.com",
            serverUrl = "https://nextcloud.example.com",
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com",
            calendarEnabled = true,
            contactsEnabled = true,
            tasksEnabled = true,
            createdAt = System.currentTimeMillis(),
            lastAuthenticatedAt = System.currentTimeMillis(),
            authType = AuthType.BASIC,
            certificateFingerprint = null
        )
        repository.insert(account)
        
        // When
        val retrieved = repository.getByAccountName("test@nextcloud.example.com")
        
        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.username).isEqualTo("testuser")
    }
    
    @Test
    fun `update account modifies existing account`() = runTest {
        // Given
        val account = Account(
            id = 0,
            accountName = "test@nextcloud.example.com",
            serverUrl = "https://nextcloud.example.com",
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com",
            calendarEnabled = true,
            contactsEnabled = true,
            tasksEnabled = true,
            createdAt = System.currentTimeMillis(),
            lastAuthenticatedAt = System.currentTimeMillis(),
            authType = AuthType.BASIC,
            certificateFingerprint = null
        )
        val insertedId = repository.insert(account)
        val updatedAccount = account.copy(id = insertedId, displayName = "Updated User")
        
        // When
        repository.update(updatedAccount)
        val retrieved = repository.getById(insertedId)
        
        // Then
        assertThat(retrieved?.displayName).isEqualTo("Updated User")
    }
    
    @Test
    fun `delete account removes it from repository`() = runTest {
        // Given
        val account = Account(
            id = 0,
            accountName = "test@nextcloud.example.com",
            serverUrl = "https://nextcloud.example.com",
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com",
            calendarEnabled = true,
            contactsEnabled = true,
            tasksEnabled = true,
            createdAt = System.currentTimeMillis(),
            lastAuthenticatedAt = System.currentTimeMillis(),
            authType = AuthType.BASIC,
            certificateFingerprint = null
        )
        val insertedId = repository.insert(account)
        val insertedAccount = account.copy(id = insertedId)
        
        // When
        repository.delete(insertedAccount)
        val retrieved = repository.getById(insertedId)
        
        // Then
        assertThat(retrieved).isNull()
    }
    
    @Test
    fun `getAllFlow emits all accounts`() = runTest {
        // Given
        repository.seedTestData()
        
        // When
        val accounts = repository.getAllFlow().first()
        
        // Then
        assertThat(accounts).hasSize(2)
        assertThat(accounts.map { it.accountName }).contains("test1@nextcloud.example.com")
        assertThat(accounts.map { it.accountName }).contains("test2@nextcloud.example.com")
    }
    
    @Test
    fun `deleteAll removes all accounts`() = runTest {
        // Given
        repository.seedTestData()
        
        // When
        repository.deleteAll()
        val count = repository.count()
        
        // Then
        assertThat(count).isEqualTo(0)
    }
    
    @Test
    fun `count returns correct number of accounts`() = runTest {
        // Given
        repository.seedTestData()
        
        // When
        val count = repository.count()
        
        // Then
        assertThat(count).isEqualTo(2)
    }
}
