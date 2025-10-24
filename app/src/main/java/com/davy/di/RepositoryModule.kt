package com.davy.di

import com.davy.data.local.dao.AccountDao
import com.davy.data.local.dao.CalendarDao
import com.davy.data.local.dao.CalendarEventDao
import com.davy.data.local.dao.ContactDao
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.data.repository.ContactRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing repository dependencies.
 * 
 * Incrementally enabling repositories as features are restored.
 * 
 * Enabled:
 * - AccountRepository ✓
 * - CalendarRepository ✓
 * 
 * Planned:
 * - ContactRepository
 * - TaskRepository
 * - SyncRepository
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao
    ): AccountRepository {
        return AccountRepository(accountDao)
    }
    
    @Provides
    @Singleton
    fun provideCalendarRepository(
        calendarDao: CalendarDao
    ): CalendarRepository {
        return CalendarRepository(calendarDao)
    }
    
    @Provides
    @Singleton
    fun provideCalendarEventRepository(
        calendarEventDao: CalendarEventDao
    ): CalendarEventRepository {
        return CalendarEventRepository(calendarEventDao)
    }
    
    @Provides
    @Singleton
    fun provideContactRepository(
        contactDao: ContactDao
    ): ContactRepository {
        return ContactRepository(contactDao)
    }
}
