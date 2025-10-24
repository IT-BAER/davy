package com.davy.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.davy.data.local.entity.AccountEntity
import com.davy.data.local.entity.AddressBookEntity
import com.davy.data.local.entity.CalendarEntity
import com.davy.data.local.entity.CalendarEventEntity
import com.davy.data.local.entity.ContactEntity
import com.davy.data.local.entity.SyncConfigurationEntity
import com.davy.data.local.entity.SyncStatusEntity
import com.davy.data.local.entity.TaskEntity
import com.davy.data.local.entity.TaskListEntity
import com.davy.data.local.entity.WebCalSubscriptionEntity

/**
 * Room database for DAVy application.
 * 
 * Stores local copies of:
 * - Account configurations
 * - Sync settings and status
 * - Calendar events
 * - Contacts
 * - Tasks (to be added)
 * 
 * @see AccountEntity for server account information
 * @see SyncConfigurationEntity for sync preferences
 * @see SyncStatusEntity for sync operation tracking
 * @see CalendarEntity for calendar information
 * @see CalendarEventEntity for calendar events
 * @see AddressBookEntity for address book information
 * @see ContactEntity for contacts
 */
@Database(
    entities = [
        AccountEntity::class,
        SyncConfigurationEntity::class,
        SyncStatusEntity::class,
        CalendarEntity::class,
        CalendarEventEntity::class,
        AddressBookEntity::class,
        ContactEntity::class,
        TaskListEntity::class,
        TaskEntity::class,
        WebCalSubscriptionEntity::class
    ],
    version = 15,  // Bumped for AddressBookEntity.forceReadOnly addition
    autoMigrations = [
        AutoMigration(from = 14, to = 15)
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DavyDatabase : RoomDatabase() {
    
    /**
     * DAO for account operations
     */
    abstract fun accountDao(): AccountDao
    
    /**
     * DAO for sync configuration operations
     */
    abstract fun syncConfigurationDao(): SyncConfigurationDao
    
    /**
     * DAO for sync status operations
     */
    abstract fun syncStatusDao(): SyncStatusDao
    
    /**
     * DAO for calendar operations
     */
    abstract fun calendarDao(): CalendarDao
    
    /**
     * DAO for calendar event operations
     */
    abstract fun calendarEventDao(): CalendarEventDao
    
    /**
     * DAO for address book operations
     */
    abstract fun addressBookDao(): AddressBookDao
    
    /**
     * DAO for contact operations
     */
    abstract fun contactDao(): ContactDao
    
    /**
     * DAO for task list operations
     */
    abstract fun taskListDao(): TaskListDao
    
    /**
     * DAO for task operations
     */
    abstract fun taskDao(): TaskDao
    
    /**
     * DAO for WebCal subscription operations
     */
    abstract fun webCalSubscriptionDao(): WebCalSubscriptionDao
    
    companion object {
        const val DATABASE_NAME = "davy_database"
    }
}
