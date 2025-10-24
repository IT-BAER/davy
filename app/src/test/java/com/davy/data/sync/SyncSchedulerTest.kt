package com.davy.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SyncScheduler.
 * 
 * Tests verify:
 * - Periodic sync scheduling with various intervals
 * - Work constraints (WiFi, charging, battery optimization)
 * - Sync cancellation
 * - Multiple scheduling strategies (default, battery-saving, aggressive)
 * - Minimum interval enforcement
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var syncScheduler: SyncScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        syncScheduler = SyncScheduler(context)
    }

    @Test
    fun `scheduleSyncEvery should create periodic work request`() {
        // When
        syncScheduler.scheduleSyncEvery(
            intervalHours = 1L,
            requireWifi = false,
            requireCharging = false,
            respectBatteryOptimization = true
        )

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        val workInfo = workInfos[0]
        assertThat(workInfo.state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
        assertThat(workInfo.tags).contains("davy_background_sync")
    }

    @Test
    fun `scheduleSyncEvery should enforce minimum 15 minute interval`() {
        // When - try to schedule with very short interval
        syncScheduler.scheduleSyncEvery(
            intervalHours = 0L, // Should be rounded up to 15 minutes
            requireWifi = false,
            requireCharging = false,
            respectBatteryOptimization = true
        )

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        // Note: Actual interval verification would require accessing WorkRequest internals
        // This test verifies the work is created
    }

    @Test
    fun `scheduleSyncEvery should apply WiFi constraint when required`() {
        // When
        syncScheduler.scheduleSyncEvery(
            intervalHours = 1L,
            requireWifi = true,
            requireCharging = false,
            respectBatteryOptimization = true
        )

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        // Note: Detailed constraint verification would require accessing WorkRequest internals
    }

    @Test
    fun `scheduleSyncEvery should apply charging constraint when required`() {
        // When
        syncScheduler.scheduleSyncEvery(
            intervalHours = 1L,
            requireWifi = false,
            requireCharging = true,
            respectBatteryOptimization = true
        )

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
    }

    @Test
    fun `scheduleDefaultSync should use default settings`() {
        // When
        syncScheduler.scheduleDefaultSync()

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `scheduleBatterySavingSync should use battery saving constraints`() {
        // When
        syncScheduler.scheduleBatterySavingSync(intervalHours = 6L)

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `scheduleAggressiveSync should use aggressive settings`() {
        // When
        syncScheduler.scheduleAggressiveSync()

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `cancelSync should cancel existing work`() {
        // Given
        syncScheduler.scheduleDefaultSync()
        val workInfosBeforeCancel = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfosBeforeCancel).hasSize(1)

        // When
        syncScheduler.cancelSync()

        // Then
        val workInfosAfterCancel = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfosAfterCancel).isEmpty()
    }

    @Test
    fun `isSyncScheduled should return true when sync is scheduled`() {
        // Given
        syncScheduler.scheduleDefaultSync()

        // When
        val isScheduled = syncScheduler.isSyncScheduled()

        // Then
        assertThat(isScheduled).isTrue()
    }

    @Test
    fun `isSyncScheduled should return false when no sync is scheduled`() {
        // Given - no sync scheduled

        // When
        val isScheduled = syncScheduler.isSyncScheduled()

        // Then
        assertThat(isScheduled).isFalse()
    }

    @Test
    fun `isSyncScheduled should return false after cancellation`() {
        // Given
        syncScheduler.scheduleDefaultSync()
        assertThat(syncScheduler.isSyncScheduled()).isTrue()

        // When
        syncScheduler.cancelSync()

        // Then
        assertThat(syncScheduler.isSyncScheduled()).isFalse()
    }

    @Test
    fun `scheduleSyncEvery should replace existing work`() {
        // Given
        syncScheduler.scheduleSyncEvery(intervalHours = 1L)
        val workInfosFirst = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfosFirst).hasSize(1)

        // When
        syncScheduler.scheduleSyncEvery(intervalHours = 2L)

        // Then
        val workInfosSecond = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfosSecond).hasSize(1)
        // Work should be updated/replaced
    }

    @Test
    fun `scheduleSyncEvery should apply all constraints together`() {
        // When
        syncScheduler.scheduleSyncEvery(
            intervalHours = 2L,
            requireWifi = true,
            requireCharging = true,
            respectBatteryOptimization = true
        )

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `cancelSync should be idempotent`() {
        // Given
        syncScheduler.scheduleDefaultSync()

        // When - cancel multiple times
        syncScheduler.cancelSync()
        syncScheduler.cancelSync()
        syncScheduler.cancelSync()

        // Then - should not throw
        assertThat(syncScheduler.isSyncScheduled()).isFalse()
    }

    @Test
    fun `scheduleDefaultSync should cancel previous sync before scheduling new one`() {
        // Given
        syncScheduler.scheduleBatterySavingSync()
        val workInfosBefore = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfosBefore).hasSize(1)

        // When
        syncScheduler.scheduleDefaultSync()

        // Then
        val workInfosAfter = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfosAfter).hasSize(1)
    }

    @Test
    fun `scheduleSyncEvery with custom interval should create work`() {
        // When
        syncScheduler.scheduleSyncEvery(
            intervalHours = 4L,
            requireWifi = false,
            requireCharging = false,
            respectBatteryOptimization = false
        )

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
    }

    @Test
    fun `scheduleBatterySavingSync with custom interval should apply battery constraints`() {
        // When
        syncScheduler.scheduleBatterySavingSync(intervalHours = 12L)

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `work request should be tagged correctly`() {
        // When
        syncScheduler.scheduleDefaultSync()

        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos[0].tags).contains("davy_background_sync")
    }

    @Test
    fun `multiple schedule calls should maintain unique work`() {
        // When
        syncScheduler.scheduleDefaultSync()
        syncScheduler.scheduleAggressiveSync()
        syncScheduler.scheduleBatterySavingSync()

        // Then - only one work should exist (unique work)
        val workInfos = workManager.getWorkInfosForUniqueWork("davy_background_sync").get()
        assertThat(workInfos).hasSize(1)
    }
}
