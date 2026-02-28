package com.davy.di

import com.davy.data.local.dao.AccountDao
import com.davy.data.local.dao.CalendarDao
import com.davy.data.local.dao.CalendarEventDao
import com.davy.data.local.dao.ContactDao
import com.davy.data.local.dao.TaskAlarmDao
import com.davy.data.local.dao.TaskDao
import com.davy.data.local.dao.TaskListDao
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.CalendarEventRepository
import com.davy.data.repository.ContactRepository
import com.davy.data.repository.TaskListRepository
import com.davy.data.repository.TaskRepository
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
 * - CalendarEventRepository ✓
 * - ContactRepository ✓
 * - TaskRepository ✓
 * - TaskListRepository ✓
 * 
 * Planned:
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

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        taskAlarmDao: TaskAlarmDao
    ): TaskRepository {
        return TaskRepository(taskDao, taskAlarmDao)
    }

    @Provides
    @Singleton
    fun provideTaskListRepository(
        taskListDao: TaskListDao
    ): TaskListRepository {
        return TaskListRepository(taskListDao)
    }
}
