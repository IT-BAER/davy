package com.davy.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 16,  // Bumped to add lastSynced field to AddressBookEntity
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
        
        /**
         * Manual migration from version 15 to 16.
         * Adds lastSynced column to address_books table.
         * Initializes lastSynced with updated_at for existing synced address books (those with ctag).
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add last_synced column to address_books table
                db.execSQL("ALTER TABLE address_books ADD COLUMN last_synced INTEGER")
                
                // Initialize last_synced with updated_at for existing address books that have been synced (have ctag)
                // This preserves the existing sync history
                db.execSQL("UPDATE address_books SET last_synced = updated_at WHERE ctag IS NOT NULL")
            }
        }
    }
}
