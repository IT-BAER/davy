package com.davy.di

import android.content.Context
import androidx.room.Room
import com.davy.data.local.DavyDatabase
import com.davy.data.local.dao.AccountDao
import com.davy.data.local.dao.AddressBookDao
import com.davy.data.local.dao.CalendarDao
import com.davy.data.local.dao.CalendarEventDao
import com.davy.data.local.dao.ContactDao
import com.davy.data.local.dao.SyncConfigurationDao
import com.davy.data.local.dao.SyncStatusDao
import com.davy.data.local.dao.TaskDao
import com.davy.data.local.dao.TaskListDao
import com.davy.data.local.dao.WebCalSubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt module providing database-related dependencies.
 * 
 * Provides:
 * - Room database instance
 * - All DAOs (AccountDao, SyncConfigurationDao, SyncStatusDao)
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDavyDatabase(
        @ApplicationContext context: Context
    ): DavyDatabase {
        Timber.d("Creating DavyDatabase")
        return Room.databaseBuilder(
            context,
            DavyDatabase::class.java,
            DavyDatabase.DATABASE_NAME
        )
            .addMigrations(DavyDatabase.MIGRATION_15_16)
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAccountDao(
        database: DavyDatabase
    ): AccountDao {
        return database.accountDao()
    }
    
    @Provides
    @Singleton
    fun provideSyncConfigurationDao(
        database: DavyDatabase
    ): SyncConfigurationDao {
        return database.syncConfigurationDao()
    }
    
    @Provides
    @Singleton
    fun provideSyncStatusDao(
        database: DavyDatabase
    ): SyncStatusDao {
        return database.syncStatusDao()
    }
    
    @Provides
    @Singleton
    fun provideCalendarDao(
        database: DavyDatabase
    ): CalendarDao {
        return database.calendarDao()
    }
    
    @Provides
    @Singleton
    fun provideCalendarEventDao(
        database: DavyDatabase
    ): CalendarEventDao {
        return database.calendarEventDao()
    }
    
    @Provides
    @Singleton
    fun provideContactDao(
        database: DavyDatabase
    ): ContactDao {
        return database.contactDao()
    }
    
    @Provides
    @Singleton
    fun provideAddressBookDao(
        database: DavyDatabase
    ): AddressBookDao {
        return database.addressBookDao()
    }
    
    @Provides
    @Singleton
    fun provideTaskListDao(
        database: DavyDatabase
    ): TaskListDao {
        return database.taskListDao()
    }
    
    @Provides
    @Singleton
    fun provideTaskDao(
        database: DavyDatabase
    ): TaskDao {
        return database.taskDao()
    }
    
    @Provides
    @Singleton
    fun provideWebCalSubscriptionDao(
        database: DavyDatabase
    ): WebCalSubscriptionDao {
        return database.webCalSubscriptionDao()
    }
}
