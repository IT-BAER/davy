package com.davy.data.repository

import com.davy.domain.model.Account
import com.davy.domain.model.AuthType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake AccountRepository implementation for testing.
 * 
 * Provides in-memory storage for accounts without database dependencies.
 * Useful for testing ViewModels and use cases.
 */
class FakeAccountRepository {
    
    private val accounts = mutableListOf<Account>()
    private var nextId = 1L
    
    /**
     * Get all accounts as Flow.
     */
    fun getAllFlow(): Flow<List<Account>> {
        return flowOf(accounts.toList())
    }
    
    /**
     * Get account by ID.
     */
    suspend fun getById(id: Long): Account? {
        return accounts.find { it.id == id }
    }
    
    /**
     * Get account by account name.
     */
    suspend fun getByAccountName(accountName: String): Account? {
        return accounts.find { it.accountName == accountName }
    }
    
    /**
     * Insert new account.
     */
    suspend fun insert(account: Account): Long {
        val id = nextId++
        val accountWithId = account.copy(id = id)
        accounts.add(accountWithId)
        return id
    }
    
    /**
     * Update existing account.
     */
    suspend fun update(account: Account) {
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
        }
    }
    
    /**
     * Delete account.
     */
    suspend fun delete(account: Account) {
        accounts.removeIf { it.id == account.id }
    }
    
    /**
     * Delete all accounts.
     */
    suspend fun deleteAll() {
        accounts.clear()
    }
    
    /**
     * Get count of accounts.
     */
    suspend fun count(): Int {
        return accounts.size
    }
    
    /**
     * Seed with test data.
     */
    fun seedTestData() {
        val testAccounts = listOf(
            Account(
                id = nextId++,
                accountName = "test1@nextcloud.example.com",
                serverUrl = "https://nextcloud.example.com",
                username = "testuser1",
                displayName = "Test User 1",
                email = "test1@example.com",
                calendarEnabled = true,
                contactsEnabled = true,
                tasksEnabled = true,
                createdAt = System.currentTimeMillis(),
                lastAuthenticatedAt = System.currentTimeMillis(),
                authType = AuthType.BASIC,
                certificateFingerprint = null
            ),
            Account(
                id = nextId++,
                accountName = "test2@nextcloud.example.com",
                serverUrl = "https://nextcloud2.example.com",
                username = "testuser2",
                displayName = "Test User 2",
                email = "test2@example.com",
                calendarEnabled = true,
                contactsEnabled = false,
                tasksEnabled = true,
                createdAt = System.currentTimeMillis(),
                lastAuthenticatedAt = System.currentTimeMillis(),
                authType = AuthType.APP_PASSWORD,
                certificateFingerprint = null
            )
        )
        accounts.addAll(testAccounts)
    }
}
