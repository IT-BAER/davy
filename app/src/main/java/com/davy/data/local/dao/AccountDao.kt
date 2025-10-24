package com.davy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davy.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) for Account operations.
 * 
 * Provides database operations for managing Nextcloud server accounts.
 * All operations use Kotlin Coroutines and Flow for asynchronous execution.
 */
@Dao
interface AccountDao {
    
    /**
     * Insert a new account.
     * 
     * @param account The account to insert
     * @return The ID of the inserted account
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long
    
    /**
     * Update an existing account.
     * 
     * @param account The account to update
     */
    @Update
    suspend fun update(account: AccountEntity)
    
    /**
     * Delete an account.
     * 
     * @param account The account to delete
     */
    @Delete
    suspend fun delete(account: AccountEntity)
    
    /**
     * Get an account by ID.
     * 
     * @param accountId The account ID
     * @return The account, or null if not found
     */
    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getById(accountId: Long): AccountEntity?

    /**
     * Observe an account by ID as a Flow.
     * Emits updates whenever the account row changes (e.g., rename).
     */
    @Query("SELECT * FROM accounts WHERE id = :accountId")
    fun getByIdFlow(accountId: Long): kotlinx.coroutines.flow.Flow<AccountEntity?>
    
    /**
     * Get an account by account name.
     * 
     * @param accountName The unique account name
     * @return The account, or null if not found
     */
    @Query("SELECT * FROM accounts WHERE account_name = :accountName")
    suspend fun getByAccountName(accountName: String): AccountEntity?
    
    /**
     * Find account by server URL and username.
     * Used for duplicate detection.
     * 
     * @param serverUrl The server URL
     * @param username The username
     * @return The account, or null if not found
     */
    @Query("SELECT * FROM accounts WHERE server_url = :serverUrl AND username = :username")
    suspend fun findByServerAndUsername(serverUrl: String, username: String): AccountEntity?
    
    /**
     * Get all accounts as a Flow.
     * Automatically updates when accounts change.
     * 
     * @return Flow emitting list of all accounts
     */
    @Query("SELECT * FROM accounts ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<AccountEntity>>
    
    /**
     * Get all accounts (one-shot query).
     * 
     * @return List of all accounts
     */
    @Query("SELECT * FROM accounts ORDER BY created_at DESC")
    suspend fun getAll(): List<AccountEntity>
    
    /**
     * Get accounts with calendar sync enabled.
     * 
     * @return Flow emitting list of accounts with calendar enabled
     */
    @Query("SELECT * FROM accounts WHERE calendar_enabled = 1")
    fun getCalendarEnabledFlow(): Flow<List<AccountEntity>>
    
    /**
     * Get accounts with contacts sync enabled.
     * 
     * @return Flow emitting list of accounts with contacts enabled
     */
    @Query("SELECT * FROM accounts WHERE contacts_enabled = 1")
    fun getContactsEnabledFlow(): Flow<List<AccountEntity>>
    
    /**
     * Get accounts with tasks sync enabled.
     * 
     * @return Flow emitting list of accounts with tasks enabled
     */
    @Query("SELECT * FROM accounts WHERE tasks_enabled = 1")
    fun getTasksEnabledFlow(): Flow<List<AccountEntity>>
    
    /**
     * Count total accounts.
     * 
     * @return Total number of accounts
     */
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
    
    /**
     * Delete all accounts (for testing/development).
     */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
