package com.davy.startup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.Initializer
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * Ensures WorkManager is initialized with the Hilt-provided WorkerFactory.
 *
 * When we disable WorkManager's default initializer we must initialize it manually.
 * This initializer runs early in the App Startup chain so any later code can
 * safely enqueue work requests.
 */
class WorkManagerSetupInitializer : Initializer<Unit> {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkManagerDependencies {
        fun hiltWorkerFactory(): HiltWorkerFactory
    }

    override fun create(context: Context) {
        val appContext = context.applicationContext

        if (WorkManager.isInitialized()) {
            Timber.d("WorkManagerSetupInitializer: WorkManager already initialized")
            return
        }

        Timber.d("WorkManagerSetupInitializer: Initializing WorkManager with HiltWorkerFactory")
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WorkManagerDependencies::class.java
        )

        val configuration = Configuration.Builder()
            .setWorkerFactory(entryPoint.hiltWorkerFactory())
            .build()

        WorkManager.initialize(appContext, configuration)
        Timber.d("WorkManagerSetupInitializer: WorkManager initialization complete")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
