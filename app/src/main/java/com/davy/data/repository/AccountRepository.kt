package com.davy.data.repository

import com.davy.data.local.dao.AccountDao
import com.davy.data.local.entity.AccountEntity
import com.davy.domain.model.Account
import com.davy.domain.model.AuthType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for account management.
 * 
 * Provides data access for account CRUD operations,
 * mapping between entity and domain models.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {
    
    /**
     * Get all accounts as Flow.
     */
    fun getAllFlow(): Flow<List<Account>> {
        return accountDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all accounts.
     */
    suspend fun getAll(): List<Account> {
        return accountDao.getAll().map { it.toDomain() }
    }
    
    /**
     * Get account by ID.
     */
    suspend fun getById(id: Long): Account? {
        return accountDao.getById(id)?.toDomain()
    }

    /**
     * Observe an account by ID.
     * Useful for reflecting changes (like rename) across screens/viewmodels instantly.
     */
    fun getByIdFlow(id: Long): Flow<Account?> {
        return accountDao.getByIdFlow(id).map { it?.toDomain() }
    }
    
    /**
     * Get account by account name.
     */
    suspend fun getByAccountName(accountName: String): Account? {
        return accountDao.getByAccountName(accountName)?.toDomain()
    }
    
    /**
     * Find account by server URL and username.
     * Used for duplicate detection when adding new accounts.
     * 
     * @param serverUrl The server URL
     * @param username The username
     * @return The existing account, or null if no duplicate exists
     */
    suspend fun findByServerAndUsername(serverUrl: String, username: String): Account? {
        return accountDao.findByServerAndUsername(serverUrl, username)?.toDomain()
    }
    
    /**
     * Insert new account.
     * 
     * @return The ID of the inserted account
     */
    suspend fun insert(account: Account): Long {
        return accountDao.insert(account.toEntity())
    }
    
    /**
     * Update existing account.
     */
    suspend fun update(account: Account) {
        accountDao.update(account.toEntity())
    }
    
    /**
     * Delete account.
     */
    suspend fun delete(account: Account) {
        accountDao.delete(account.toEntity())
    }
    
    /**
     * Delete all accounts.
     */
    suspend fun deleteAll() {
        accountDao.deleteAll()
    }
    
    /**
     * Get count of accounts.
     */
    suspend fun count(): Int {
        return accountDao.count()
    }
    
    /**
     * Get all accounts with calendar sync enabled as Flow.
     */
    fun getCalendarEnabledFlow(): Flow<List<Account>> {
        return accountDao.getCalendarEnabledFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all accounts with contacts sync enabled as Flow.
     */
    fun getContactsEnabledFlow(): Flow<List<Account>> {
        return accountDao.getContactsEnabledFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get all accounts with tasks sync enabled as Flow.
     */
    fun getTasksEnabledFlow(): Flow<List<Account>> {
        return accountDao.getTasksEnabledFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Convert AccountEntity to Account domain model.
     */
    private fun AccountEntity.toDomain(): Account {
        return Account(
            id = id,
            accountName = accountName,
            serverUrl = serverUrl,
            username = username,
            displayName = displayName,
            email = email,
            calendarEnabled = calendarEnabled,
            contactsEnabled = contactsEnabled,
            tasksEnabled = tasksEnabled,
            createdAt = createdAt,
            lastAuthenticatedAt = lastAuthenticatedAt,
            authType = AuthType.valueOf(authType),
            certificateFingerprint = certificateFingerprint,
            notes = notes
        )
    }
    
    /**
     * Convert Account domain model to AccountEntity.
     */
    private fun Account.toEntity(): AccountEntity {
        return AccountEntity(
            id = id,
            accountName = accountName,
            serverUrl = serverUrl,
            username = username,
            displayName = displayName,
            email = email,
            calendarEnabled = calendarEnabled,
            contactsEnabled = contactsEnabled,
            tasksEnabled = tasksEnabled,
            createdAt = createdAt,
            lastAuthenticatedAt = lastAuthenticatedAt,
            authType = authType.name,
            certificateFingerprint = certificateFingerprint,
            notes = notes
        )
    }
}
