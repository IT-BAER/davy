package com.davy.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration definitions for DAVy.
 * 
 * Each migration handles schema changes between versions.
 * Always test migrations with actual user data!
 */
object DatabaseMigrations {
    
    /**
     * Example migration from version 1 to 2.
     * 
     * Currently a no-op placeholder. When schema changes are needed:
     * 1. Increment database version in DavyDatabase
     * 2. Add SQL statements to modify schema
     * 3. Test migration with production-like data
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
            // Example: Add a new column
            // database.execSQL("ALTER TABLE accounts ADD COLUMN avatar_url TEXT")
            
            // Example: Create a new table
            // database.execSQL("""
            //     CREATE TABLE IF NOT EXISTS calendars (
            //         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            //         account_id INTEGER NOT NULL,
            //         name TEXT NOT NULL,
            //         FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
            //     )
            // """.trimIndent())
            
            // Example: Create index
            // database.execSQL("CREATE INDEX IF NOT EXISTS index_calendars_account_id ON calendars(account_id)")
        }
    }
    
    /**
     * Migration from version 4 to 5: Add WebCal subscriptions table.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
            // Create webcal_subscription table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS webcal_subscription (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    account_id INTEGER NOT NULL,
                    subscription_url TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    description TEXT,
                    sync_enabled INTEGER NOT NULL DEFAULT 1,
                    visible INTEGER NOT NULL DEFAULT 1,
                    android_calendar_id INTEGER,
                    etag TEXT,
                    refresh_interval_minutes INTEGER NOT NULL DEFAULT 60,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_synced_at INTEGER,
                    last_sync_error TEXT,
                    FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Create indices for webcal_subscription
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS index_webcal_subscription_account_id 
                ON webcal_subscription(account_id)
            """.trimIndent())
            
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_webcal_subscription_subscription_url 
                ON webcal_subscription(subscription_url)
            """.trimIndent())
        }
    }
    
    /**
     * Get all migrations for the database.
     * 
     * @return Array of all migrations
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_4_5
        )
    }
}
