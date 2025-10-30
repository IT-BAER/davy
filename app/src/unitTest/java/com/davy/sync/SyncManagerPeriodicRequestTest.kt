package com.davy.sync

import android.content.Context
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.impl.model.WorkSpec
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncManagerPeriodicRequestTest {

    private lateinit var syncManager: SyncManager

    @BeforeEach
    fun setUp() {
        mockkStatic(WorkManager::class)
        val mockWorkManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        syncManager = SyncManager(
            context = mockk<Context>(relaxed = true),
            networkUtils = mockk(relaxed = true),
            calendarRepository = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            accountRepository = mockk(relaxed = true),
            androidAccountManager = mockk(relaxed = true),
            syncFrameworkIntegration = mockk(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun accountRequestRespectsMinimumIntervalAndAddsPushFlag() {
        val result = invokeAccountRequest(
            accountId = 123L,
            intervalMinutes = 5,
            wifiOnly = true,
            includePushOnlyFlag = true
        )

        val effectiveInterval = result.effectiveIntervalMinutes()
        val minInterval = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        assertThat(effectiveInterval).isEqualTo(minInterval)

        val request = result.periodicRequest()
        val workSpec = request.extractWorkSpec()
        assertThat(workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(workSpec.input.getBoolean("push_only", false)).isTrue()
        assertThat(workSpec.input.getString(SyncWorker.INPUT_SYNC_TYPE)).isEqualTo(SyncManager.SYNC_TYPE_ALL)

        val tags = request.tags
        assertThat(tags).contains("periodic_sync")
        assertThat(tags).contains("account_123")
        assertThat(tags).contains("service_all")
    }

    @Test
    fun serviceRequestUsesRequestedIntervalAndOmitsPushFlagWhenExcluded() {
        val result = invokeServiceRequest(
            accountId = 456L,
            serviceType = "calendar",
            intervalMinutes = 45,
            wifiOnly = false,
            includePushOnlyFlag = false
        )

        val effectiveInterval = result.effectiveIntervalMinutes()
        assertThat(effectiveInterval).isEqualTo(45)

        val request = result.periodicRequest()
        val workSpec = request.extractWorkSpec()
        assertThat(workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    assertThat(workSpec.input.keyValueMap.containsKey("push_only")).isFalse()
        assertThat(workSpec.input.getString(SyncWorker.INPUT_SYNC_TYPE)).isEqualTo(SyncManager.SYNC_TYPE_CALENDAR)
        assertThat(workSpec.input.getBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, true)).isFalse()

        val tags = request.tags
        assertThat(tags).contains("periodic_sync")
        assertThat(tags).contains("account_456")
        assertThat(tags).contains("service_calendar")
    }

    @Test
    fun webcalRequestAddsCalendarTagsAndForcesWebcal() {
        val result = invokeServiceRequest(
            accountId = 789L,
            serviceType = "webcal",
            intervalMinutes = 30,
            wifiOnly = true,
            includePushOnlyFlag = true
        )

        val effectiveInterval = result.effectiveIntervalMinutes()
        val minInterval = (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / TimeUnit.MINUTES.toMillis(1)).toInt()
        assertThat(effectiveInterval).isEqualTo(maxOf(30, minInterval))

        val request = result.periodicRequest()
        val workSpec = request.extractWorkSpec()
        assertThat(workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(workSpec.input.getBoolean("push_only", false)).isTrue()
        assertThat(workSpec.input.getString(SyncWorker.INPUT_SYNC_TYPE)).isEqualTo(SyncManager.SYNC_TYPE_CALENDAR)
        assertThat(workSpec.input.getBoolean(SyncWorker.INPUT_FORCE_WEB_CAL, false)).isTrue()

        val tags = request.tags
        assertThat(tags).contains("periodic_sync")
        assertThat(tags).contains("account_789")
        assertThat(tags).contains("service_webcal")
        assertThat(tags).contains("service_calendar")
    }

    private fun invokeAccountRequest(
        accountId: Long,
        intervalMinutes: Int,
        wifiOnly: Boolean,
        includePushOnlyFlag: Boolean
    ): Any {
        val method = SyncManager::class.java.getDeclaredMethod(
            "createAccountPeriodicRequest",
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE
        )
        method.isAccessible = true
        return requireNotNull(method.invoke(syncManager, accountId, intervalMinutes, wifiOnly, includePushOnlyFlag))
    }

    private fun invokeServiceRequest(
        accountId: Long,
        serviceType: String,
        intervalMinutes: Int,
        wifiOnly: Boolean,
        includePushOnlyFlag: Boolean
    ): Any {
        val method = SyncManager::class.java.getDeclaredMethod(
            "createServicePeriodicRequest",
            java.lang.Long.TYPE,
            String::class.java,
            java.lang.Integer.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE
        )
        method.isAccessible = true
        return requireNotNull(method.invoke(syncManager, accountId, serviceType, intervalMinutes, wifiOnly, includePushOnlyFlag))
    }

    private fun Any.periodicRequest(): PeriodicWorkRequest {
        val field = this.javaClass.getDeclaredField("request")
        field.isAccessible = true
        return field.get(this) as PeriodicWorkRequest
    }

    private fun Any.effectiveIntervalMinutes(): Int {
        val field = this.javaClass.getDeclaredField("effectiveIntervalMinutes")
        field.isAccessible = true
        return field.getInt(this)
    }

    private fun PeriodicWorkRequest.extractWorkSpec(): WorkSpec {
        val workRequestClass = WorkRequest::class.java
        val workSpecField = workRequestClass.declaredFields
            .firstOrNull { WorkSpec::class.java.isAssignableFrom(it.type) }
            ?: throw IllegalStateException("Unable to locate WorkSpec field on WorkRequest")

        workSpecField.isAccessible = true
        return workSpecField.get(this) as WorkSpec
    }
}
