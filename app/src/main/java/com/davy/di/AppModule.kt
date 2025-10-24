package com.davy.di

import android.content.Context
import com.davy.data.local.CredentialStore
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 * 
 * Provides:
 * - Application context
 * - Credential store
 * - Timber logger (if needed as dependency)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context
    
    @Provides
    @Singleton
    fun provideCredentialStore(
        @ApplicationContext context: Context
    ): CredentialStore {
        Timber.d("Providing CredentialStore")
        return CredentialStore(context)
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}
