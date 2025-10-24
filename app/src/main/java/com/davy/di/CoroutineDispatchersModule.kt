package com.davy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Coroutine dispatcher qualifiers following reference implementation pattern.
 * 
 * Provides properly scoped dispatchers for different work types:
 * - DefaultDispatcher: CPU-intensive work
 * - IoDispatcher: I/O operations (network, database)
 * - MainDispatcher: UI updates and coordination
 * - SyncDispatcher: Limited parallelism for sync operations
 * 
 * Reference: reference implementation CoroutineDispatchersModule.kt
 */

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SyncDispatcher

@Module
@InstallIn(SingletonComponent::class)
class CoroutineDispatchersModule {

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * A dispatcher for background sync operations following reference implementation pattern.
     * 
     * Uses limited parallelism to prevent blocking other I/O operations.
     * Long-running sync operations get their own pool to avoid starving
     * database access and other I/O operations needed by the UI.
     * 
     * Limits parallelism to available processor count to prevent thread explosion.
     * 
     * Reference: reference implementation - syncDispatcher implementation
     */
    @Provides
    @SyncDispatcher
    @Singleton
    fun syncDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

}
