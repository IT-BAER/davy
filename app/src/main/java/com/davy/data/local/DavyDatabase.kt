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
import com.davy.data.local.dao.TaskAlarmDao
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
import com.davy.data.local.entity.TaskAlarmEntity
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
        TaskAlarmEntity::class,
        WebCalSubscriptionEntity::class
    ],
    version = 19,  // Bumped to add GEO, URL, ORGANIZER, SEQUENCE, COLOR, DURATION, COMMENT, all-day, timezone support
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
     * DAO for task alarm operations
     */
    abstract fun taskAlarmDao(): TaskAlarmDao
    
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
        
        /**
         * Manual migration from version 16 to 17.
         * Adds group support columns to contacts table.
         * Adds categories (JSON array of group names), is_group (boolean), and group_members (JSON array of UIDs).
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add group support columns to contacts table
                db.execSQL("ALTER TABLE contacts ADD COLUMN categories TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN is_group INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN group_members TEXT")
            }
        }
        
        /**
         * Manual migration from version 17 to 18.
         * Adds enhanced VTODO support:
         * - classification column for CLASS property (PUBLIC, PRIVATE, CONFIDENTIAL)
         * - exdates column for recurrence exception dates (JSON array)
         * - rdates column for additional recurrence dates (JSON array)
         * - parent_task_uid column for RELATED-TO resolution
         * - unknown_properties column for preserving unknown iCalendar properties
         * - task_alarms table for VALARM components
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to tasks table
                db.execSQL("ALTER TABLE tasks ADD COLUMN classification TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN exdates TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN rdates TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN parent_task_uid TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN unknown_properties TEXT")
                
                // Create task_alarms table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_alarms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        task_id INTEGER NOT NULL,
                        action TEXT NOT NULL DEFAULT 'DISPLAY',
                        trigger_minutes_before INTEGER,
                        trigger_absolute INTEGER,
                        trigger_relative_to TEXT NOT NULL DEFAULT 'START',
                        description TEXT,
                        summary TEXT,
                        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create index on task_id for faster alarm lookups
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_alarms_task_id ON task_alarms(task_id)")
            }
        }
        
        /**
         * Manual migration from version 18 to 19.
         * Adds RFC-compliant VTODO property support:
         * - geo_lat/geo_lng for GEO property (geographic position)
         * - task_color for COLOR property (RFC 7986)
         * - todo_url for URL property
         * - organizer for ORGANIZER property
         * - sequence for SEQUENCE property (conflict detection)
         * - duration for DURATION property (ISO 8601)
         * - comment for COMMENT property
         * - is_all_day for DATE vs DATE-TIME distinction
         * - timezone for VTIMEZONE and TZID support
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN geo_lat REAL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN geo_lng REAL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN task_color INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN todo_url TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN organizer TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sequence INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN duration TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN comment TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN is_all_day INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN timezone TEXT")
            }
        }
    }
}
