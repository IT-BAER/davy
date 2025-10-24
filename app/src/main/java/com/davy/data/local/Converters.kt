package com.davy.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room type converters for custom types.
 */
class Converters {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
}
