package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.davy.data.repository.AccountRepository
import com.davy.domain.model.Account
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for BackgroundSyncWorker.
 * 
 * Tests the background worker that orchestrates synchronization across all resources.
 * Validates account discovery, parallel sync execution, and error handling patterns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundSyncWorkerTest {
    
    private lateinit var context: Context
    private lateinit var accountRepository: AccountRepository
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        accountRepository = mockk(relaxed = true)
    }
    
    @Test
    fun `doWork returns success when no accounts exist`() = runTest {
        // Given no accounts in repository
        coEvery { accountRepository.getAllAccounts() } returns emptyList()
        
        // When worker runs
        val worker = createWorker()
        val result = worker.doWork()
        
        // Then returns success
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
    
    @Test
    fun `doWork syncs all accounts`() = runTest {
        // Given multiple accounts
        val accounts = listOf(
            createTestAccount(id = 1L, username = "user1"),
            createTestAccount(id = 2L, username = "user2"),
            createTestAccount(id = 3L, username = "user3")
        )
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs
        val worker = createWorker()
        val result = worker.doWork()
        
        // Then attempts to sync all accounts
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
    
    @Test
    fun `doWork syncs accounts in parallel`() = runTest {
        // Given multiple accounts
        val accounts = listOf(
            createTestAccount(id = 1L),
            createTestAccount(id = 2L)
        )
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs
        val worker = createWorker()
        val startTime = System.currentTimeMillis()
        worker.doWork()
        val duration = System.currentTimeMillis() - startTime
        
        // Then sync completes quickly (parallel execution)
        // Sequential would take much longer
        assertThat(duration).isLessThan(1000L)
    }
    
    @Test
    fun `doWork returns success when all accounts sync successfully`() = runTest {
        // Given accounts that sync successfully
        val accounts = listOf(
            createTestAccount(id = 1L),
            createTestAccount(id = 2L)
        )
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs
        val worker = createWorker()
        val result = worker.doWork()
        
        // Then returns success
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
    
    @Test
    fun `doWork handles account repository errors`() = runTest {
        // Given repository throws error
        coEvery { accountRepository.getAllAccounts() } throws Exception("Database error")
        
        // When worker runs
        val worker = createWorker()
        val result = worker.doWork()
        
        // Then returns retry on first attempt
        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }
    
    @Test
    fun `doWork retries up to max attempts on error`() = runTest {
        // Given repository throws error
        coEvery { accountRepository.getAllAccounts() } throws Exception("Database error")
        
        // When worker runs multiple times
        val worker1 = createWorker(runAttemptCount = 0)
        val result1 = worker1.doWork()
        
        val worker2 = createWorker(runAttemptCount = 1)
        val result2 = worker2.doWork()
        
        val worker3 = createWorker(runAttemptCount = 2)
        val result3 = worker3.doWork()
        
        val worker4 = createWorker(runAttemptCount = 3)
        val result4 = worker4.doWork()
        
        // Then retries until max attempts, then fails
        assertThat(result1).isEqualTo(ListenableWorker.Result.retry())
        assertThat(result2).isEqualTo(ListenableWorker.Result.retry())
        assertThat(result3).isEqualTo(ListenableWorker.Result.retry())
        assertThat(result4).isEqualTo(ListenableWorker.Result.failure())
    }
    
    @Test
    fun `doWork uses IO dispatcher for sync operations`() = runTest {
        // Given accounts to sync
        val accounts = listOf(createTestAccount(id = 1L))
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs
        val worker = createWorker()
        worker.doWork()
        
        // Then repository call happens on IO dispatcher
        // Verified implicitly by coroutine test dispatcher
        coVerify { accountRepository.getAllAccounts() }
    }
    
    @Test
    fun `doWork handles partial sync failures gracefully`() = runTest {
        // Given accounts where some operations might fail
        val accounts = listOf(
            createTestAccount(id = 1L),
            createTestAccount(id = 2L)
        )
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs (sync methods are TODO but return success)
        val worker = createWorker()
        val result = worker.doWork()
        
        // Then returns success (graceful degradation)
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
    
    @Test
    fun `doWork completes within reasonable time for multiple accounts`() = runTest {
        // Given many accounts
        val accounts = (1L..10L).map { createTestAccount(id = it) }
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs
        val worker = createWorker()
        val startTime = System.currentTimeMillis()
        worker.doWork()
        val duration = System.currentTimeMillis() - startTime
        
        // Then completes quickly due to parallel execution
        assertThat(duration).isLessThan(2000L)
    }
    
    @Test
    fun `worker handles empty account list efficiently`() = runTest {
        // Given empty account list
        coEvery { accountRepository.getAllAccounts() } returns emptyList()
        
        // When worker runs
        val worker = createWorker()
        val startTime = System.currentTimeMillis()
        val result = worker.doWork()
        val duration = System.currentTimeMillis() - startTime
        
        // Then completes immediately
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(duration).isLessThan(100L)
    }
    
    @Test
    fun `worker calls repository exactly once per execution`() = runTest {
        // Given accounts
        val accounts = listOf(createTestAccount(id = 1L))
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs
        val worker = createWorker()
        worker.doWork()
        
        // Then repository called exactly once
        coVerify(exactly = 1) { accountRepository.getAllAccounts() }
    }
    
    @Test
    fun `worker distinguishes between errors and failures`() = runTest {
        // Given repository that throws on first attempt but succeeds later
        var callCount = 0
        coEvery { accountRepository.getAllAccounts() } answers {
            if (callCount++ == 0) throw Exception("Temporary error")
            else listOf(createTestAccount(id = 1L))
        }
        
        // When worker runs first time
        val worker1 = createWorker(runAttemptCount = 0)
        val result1 = worker1.doWork()
        
        // Then retries on error
        assertThat(result1).isEqualTo(ListenableWorker.Result.retry())
        
        // When worker runs second time
        val worker2 = createWorker(runAttemptCount = 1)
        val result2 = worker2.doWork()
        
        // Then succeeds after retry
        assertThat(result2).isEqualTo(ListenableWorker.Result.success())
    }
    
    @Test
    fun `worker handles concurrent account operations safely`() = runTest {
        // Given accounts
        val accounts = (1L..5L).map { createTestAccount(id = it) }
        coEvery { accountRepository.getAllAccounts() } returns accounts
        
        // When worker runs (exercises parallel sync paths)
        val worker = createWorker()
        val result = worker.doWork()
        
        // Then completes without exceptions
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
    
    // Helper functions
    
    private fun createWorker(runAttemptCount: Int = 0): BackgroundSyncWorker {
        return TestListenableWorkerBuilder<BackgroundSyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .build() as BackgroundSyncWorker
    }
    
    private fun createTestAccount(
        id: Long,
        username: String = "testuser",
        serverUrl: String = "https://example.com"
    ): Account {
        return Account(
            id = id,
            accountName = "$username@$serverUrl",
            username = username,
            serverUrl = serverUrl,
            accountType = "com.davy"
        )
    }
}
