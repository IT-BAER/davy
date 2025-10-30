package com.davy.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.CalendarRepository
import com.davy.sync.account.AndroidAccountManager
import com.davy.util.NetworkUtils
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SyncManagerWorkManagerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        syncManager = SyncManager(
            context = context,
            networkUtils = mockk(relaxed = true),
            calendarRepository = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            accountRepository = mockk(relaxed = true),
            androidAccountManager = mockk(relaxed = true),
            syncFrameworkIntegration = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
        WorkManagerTestInitHelper.closeWorkDatabase()
        unmockkAll()
    }

    @Test
    fun schedulePeriodicSync_enqueuesUniqueWorkWithExpectedTags() {
        val accountId = 42L

        syncManager.schedulePeriodicSync(
            accountId = accountId,
            intervalMinutes = 90,
            wifiOnly = true
        )

        val workInfos = workManager
            .getWorkInfosForUniqueWork(uniquePeriodicWorkName(accountId, SyncManager.SYNC_TYPE_ALL))
            .get(5, TimeUnit.SECONDS)

        assertThat(workInfos).hasSize(1)
        val workInfo = workInfos.single()

        assertThat(workInfo.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(workInfo.tags).containsAtLeast(
            "periodic_sync",
            "account_${accountId}",
            "service_all",
            "periodic-sync/common/all/account_${accountId}"
        )

        val constraints = workInfo.constraints
        assertThat(constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)

        val inputData = workInfo.inputData
        assertThat(inputData.getLong(SyncWorker.INPUT_ACCOUNT_ID, -1L)).isEqualTo(accountId)
        assertThat(inputData.getString(SyncWorker.INPUT_SYNC_TYPE)).isEqualTo(SyncManager.SYNC_TYPE_ALL)
        assertThat(inputData.getBoolean("push_only", false)).isTrue()
        assertThat(inputData.getBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, true)).isFalse()
    }

    @Test
    fun scheduleServiceSync_webcalAddsCalendarFallbackTagsAndForcesWebCal() {
        val accountId = 7L

        syncManager.scheduleServiceSync(
            accountId = accountId,
            serviceType = "webcal",
            intervalMinutes = 180,
            wifiOnly = false
        )

        val workInfos = workManager
            .getWorkInfosForUniqueWork(uniquePeriodicWorkName(accountId, "webcal"))
            .get(5, TimeUnit.SECONDS)

        assertThat(workInfos).hasSize(1)
        val workInfo = workInfos.single()

        assertThat(workInfo.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(workInfo.tags).containsAtLeast(
            "periodic_sync",
            "account_${accountId}",
            "service_webcal",
            "service_calendar",
            "periodic-sync/common/webcal/account_${accountId}",
            "periodic-sync/common/calendar/account_${accountId}"
        )

        val constraints = workInfo.constraints
        assertThat(constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)

        val inputData = workInfo.inputData
        assertThat(inputData.getString(SyncWorker.INPUT_SYNC_TYPE)).isEqualTo(SyncManager.SYNC_TYPE_CALENDAR)
        assertThat(inputData.getBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, false)).isTrue()
        assertThat(inputData.getBoolean("push_only", false)).isTrue()
    }

    @Test
    fun scheduleServiceSync_calendarKeepsCalendarTagsAndDefaults() {
        val accountId = 11L

        syncManager.scheduleServiceSync(
            accountId = accountId,
            serviceType = SyncManager.SYNC_TYPE_CALENDAR,
            intervalMinutes = 75,
            wifiOnly = true
        )

        val workInfos = workManager
            .getWorkInfosForUniqueWork(uniquePeriodicWorkName(accountId, SyncManager.SYNC_TYPE_CALENDAR))
            .get(5, TimeUnit.SECONDS)

        assertThat(workInfos).hasSize(1)
        val workInfo = workInfos.single()

        assertThat(workInfo.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(workInfo.tags).containsAtLeast(
            "periodic_sync",
            "account_${accountId}",
            "service_calendar",
            "periodic-sync/common/calendar/account_${accountId}"
        )
        assertThat(workInfo.tags).doesNotContain("service_webcal")

        val constraints = workInfo.constraints
        assertThat(constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)

        val inputData = workInfo.inputData
        assertThat(inputData.getString(SyncWorker.INPUT_SYNC_TYPE)).isEqualTo(SyncManager.SYNC_TYPE_CALENDAR)
        assertThat(inputData.getBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, true)).isFalse()
        assertThat(inputData.getBoolean("push_only", false)).isTrue()
    }

    private fun uniquePeriodicWorkName(accountId: Long, serviceType: String): String {
        return "periodic-sync ${serviceType.lowercase(Locale.ROOT)} account_${accountId}"
    }
}
