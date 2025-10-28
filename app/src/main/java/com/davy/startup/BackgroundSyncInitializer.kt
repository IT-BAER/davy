package com.davy.startup

import android.content.Context
import androidx.startup.Initializer
import com.davy.sync.AccountSyncConfigurationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Initializes background sync scheduling using App Startup library.
 * This defers WorkManager setup away from Application.onCreate().
 */
class BackgroundSyncInitializer : Initializer<Unit> {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackgroundSyncDependencies {
        fun accountSyncConfigurationManager(): AccountSyncConfigurationManager
    }

    override fun create(context: Context) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            BackgroundSyncDependencies::class.java
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                Timber.d("BackgroundSyncInitializer: Starting")

                val accountSyncConfigurationManager = entryPoint.accountSyncConfigurationManager()

                // Apply per-account sync configurations with service-specific intervals
                accountSyncConfigurationManager.applyAllSyncConfigurationsPreserving()

                Timber.d("BackgroundSyncInitializer: Completed")
            } catch (e: Exception) {
                Timber.e(e, "BackgroundSyncInitializer: Failed")
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(WorkManagerSetupInitializer::class.java)
}
