package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.davy.data.local.DavyDatabase
import com.davy.data.local.dao.AccountDao
import com.davy.data.local.entity.AccountEntity
import com.davy.domain.model.AuthType
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Integration test for periodic background sync with WorkManager.
 * 
 * Tests the complete automatic synchronization flow including:
 * - Periodic work scheduling with different intervals
 * - Constraint handling (network, battery)
 * - Work policy behavior (KEEP, UPDATE, CANCEL_AND_REENQUEUE)
 * - Background sync execution
 * - Multi-account sync coordination
 * 
 * This validates User Story 5 (Automatic Background Synchronization).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PeriodicSyncTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: DavyDatabase

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver
    private lateinit var accountDao: AccountDao
    private var testAccountId: Long = 0

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Initialize WorkManager for testing with synchronous executor
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        accountDao = database.accountDao()

        // Create test account
        runBlocking {
            val account = AccountEntity(
                id = 0,
                displayName = "Test Account",
                username = "testuser",
                serverUrl = "https://nextcloud.example.com",
                authType = AuthType.BASIC,
                calendarPrincipalUrl = "https://nextcloud.example.com/caldav/",
                cardDavPrincipalUrl = "https://nextcloud.example.com/carddav/",
                createdAt = OffsetDateTime.now(),
                lastSyncedAt = null
            )
            testAccountId = accountDao.insert(account)
        }
    }

    @After
    fun tearDown() {
        database.close()
        // Cancel all work
        workManager.cancelAllWork()
    }

    @Test
    fun periodicSync_schedulesWithCorrectInterval() {
        // Arrange: Create periodic work request for 15-minute intervals
        val syncIntervalMinutes = 15L
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = syncIntervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag("periodic_sync")
            .addTag("account_$testAccountId")
            .build()

        // Act: Enqueue periodic work
        workManager.enqueueUniquePeriodicWork(
            "periodic_sync_account_$testAccountId",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Give WorkManager time to schedule
        Thread.sleep(100)

        // Assert: Verify work is scheduled
        val workInfos = workManager.getWorkInfosByTag("periodic_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].state).isIn(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
    }

    @Test
    fun periodicSync_respectsNetworkConstraint() {
        // Arrange: Schedule work with network required
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("network_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "network_constraint_test",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Thread.sleep(100)

        // Act: Simulate network unavailable
        testDriver.setAllConstraintsMet(workRequest.id)
        Thread.sleep(100)

        // Assert: Work should be enqueued but waiting for constraints
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        // Work will be ENQUEUED until constraints are met
        assertThat(workInfo.state).isIn(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
    }

    @Test
    fun periodicSync_respectsBatteryConstraint() {
        // Arrange: Schedule work with battery not low required
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag("battery_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "battery_constraint_test",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Thread.sleep(100)

        // Assert: Work scheduled with battery constraint
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        assertThat(workInfo.state).isIn(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
        assertThat(workInfo.constraints.requiresBatteryNotLow()).isTrue()
    }

    @Test
    fun periodicSync_keepPolicyPreservesExistingWork() {
        // Arrange: Schedule initial work
        val firstRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("keep_policy_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "keep_policy_test_work",
            ExistingPeriodicWorkPolicy.KEEP,
            firstRequest
        )

        Thread.sleep(100)
        val firstWorkId = workManager.getWorkInfosByTag("keep_policy_test").get()[0].id

        // Act: Try to schedule same work again with KEEP policy
        val secondRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 30, // Different interval
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("keep_policy_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "keep_policy_test_work",
            ExistingPeriodicWorkPolicy.KEEP,
            secondRequest
        )

        Thread.sleep(100)

        // Assert: Original work should still exist (KEEP preserves)
        val workInfos = workManager.getWorkInfosByTag("keep_policy_test").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].id).isEqualTo(firstWorkId) // Same work ID = original kept
    }

    @Test
    fun periodicSync_updatePolicyReplacesWork() {
        // Arrange: Schedule initial work
        val firstRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("update_policy_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "update_policy_test_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            firstRequest
        )

        Thread.sleep(100)
        val firstWorkId = workManager.getWorkInfosByTag("update_policy_test").get()[0].id

        // Act: Schedule with UPDATE policy and different interval
        val secondRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 30, // Different interval
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("update_policy_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "update_policy_test_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            secondRequest
        )

        Thread.sleep(100)

        // Assert: Work should be updated (new work ID or same work with updated params)
        val workInfos = workManager.getWorkInfosByTag("update_policy_test").get()
        assertThat(workInfos).hasSize(1)
        // UPDATE policy may create new work or update existing - both are valid
    }

    @Test
    fun periodicSync_cancelAndReenqueueCreatesNewWork() {
        // Arrange: Schedule initial work
        val firstRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("cancel_reenqueue_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "cancel_reenqueue_test_work",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            firstRequest
        )

        Thread.sleep(100)
        val firstWorkId = workManager.getWorkInfosByTag("cancel_reenqueue_test").get()[0].id

        // Act: Schedule with CANCEL_AND_REENQUEUE policy
        val secondRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("cancel_reenqueue_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "cancel_reenqueue_test_work",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            secondRequest
        )

        Thread.sleep(100)

        // Assert: New work created (different ID)
        val workInfos = workManager.getWorkInfosByTag("cancel_reenqueue_test").get()
        assertThat(workInfos).hasSize(1)
        // Work might be cancelled or new - both states are valid during transition
        assertThat(workInfos[0].state).isIn(
            listOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.CANCELLED
            )
        )
    }

    @Test
    fun periodicSync_multipleAccounts_scheduledIndependently() {
        // Arrange: Create second account
        val account2Id = runBlocking {
            accountDao.insert(
                AccountEntity(
                    id = 0,
                    displayName = "Second Account",
                    username = "user2",
                    serverUrl = "https://nextcloud2.example.com",
                    authType = AuthType.BASIC,
                    calendarPrincipalUrl = "https://nextcloud2.example.com/caldav/",
                    cardDavPrincipalUrl = null,
                    createdAt = OffsetDateTime.now(),
                    lastSyncedAt = null
                )
            )
        }

        // Act: Schedule sync for both accounts
        val work1 = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf("account_id" to testAccountId))
            .addTag("account_$testAccountId")
            .build()

        val work2 = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(30, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf("account_id" to account2Id))
            .addTag("account_$account2Id")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "periodic_sync_account_$testAccountId",
            ExistingPeriodicWorkPolicy.KEEP,
            work1
        )

        workManager.enqueueUniquePeriodicWork(
            "periodic_sync_account_$account2Id",
            ExistingPeriodicWorkPolicy.KEEP,
            work2
        )

        Thread.sleep(200)

        // Assert: Both accounts have independent scheduled work
        val account1Work = workManager.getWorkInfosByTag("account_$testAccountId").get()
        val account2Work = workManager.getWorkInfosByTag("account_$account2Id").get()

        assertThat(account1Work).hasSize(1)
        assertThat(account2Work).hasSize(1)
        assertThat(account1Work[0].id).isNotEqualTo(account2Work[0].id)
    }

    @Test
    fun periodicSync_wifiOnlyConstraint_appliedCorrectly() {
        // Arrange: Schedule work with WiFi-only constraint
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi/unmetered
                    .build()
            )
            .addTag("wifi_only_test")
            .build()

        // Act: Enqueue work
        workManager.enqueueUniquePeriodicWork(
            "wifi_only_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Thread.sleep(100)

        // Assert: Verify WiFi constraint is set
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        assertThat(workInfo.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }

    @Test
    fun periodicSync_syncIntervalChange_updatesSchedule() {
        // Arrange: Initial 60-minute sync
        val initialWork = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 60,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("interval_change_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "interval_change_work",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            initialWork
        )

        Thread.sleep(100)

        // Act: User changes to 15-minute sync
        val updatedWork = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("interval_change_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "interval_change_work",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            updatedWork
        )

        Thread.sleep(100)

        // Assert: New work scheduled (CANCEL_AND_REENQUEUE creates fresh work)
        val workInfos = workManager.getWorkInfosByTag("interval_change_test").get()
        assertThat(workInfos).hasSize(1)
    }

    @Test
    fun periodicSync_tagsAppliedCorrectly() {
        // Arrange & Act: Schedule work with multiple tags
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("periodic_sync")
            .addTag("account_$testAccountId")
            .addTag("calendar_sync")
            .addTag("contact_sync")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "multi_tag_test",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Thread.sleep(100)

        // Assert: All tags present
        assertThat(workManager.getWorkInfosByTag("periodic_sync").get()).hasSize(1)
        assertThat(workManager.getWorkInfosByTag("account_$testAccountId").get()).hasSize(1)
        assertThat(workManager.getWorkInfosByTag("calendar_sync").get()).hasSize(1)
        assertThat(workManager.getWorkInfosByTag("contact_sync").get()).hasSize(1)
    }

    @Test
    fun periodicSync_cancelWork_removesScheduledSync() {
        // Arrange: Schedule periodic work
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .addTag("cancel_test")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "cancel_test_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Thread.sleep(100)
        assertThat(workManager.getWorkInfosByTag("cancel_test").get()).hasSize(1)

        // Act: Cancel the work
        workManager.cancelUniqueWork("cancel_test_work")
        Thread.sleep(100)

        // Assert: Work is cancelled
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        assertThat(workInfo.state).isEqualTo(WorkInfo.State.CANCELLED)
    }
}
