package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model for a task alarm/reminder (VALARM component).
 *
 * Represents a reminder for a task from a CalDAV task list.
 * Supports all major VALARM properties from RFC 5545.
 *
 * @property id Unique identifier (local database ID)
 * @property taskId Task ID this alarm belongs to
 * @property action Alarm action type (DISPLAY, AUDIO, EMAIL)
 * @property triggerMinutesBefore Minutes before due/start to trigger (for relative triggers)
 * @property triggerAbsolute Absolute trigger time in milliseconds (for absolute triggers)
 * @property triggerRelativeTo What the relative trigger is relative to (START or END/DUE)
 * @property description Alarm description/message
 * @property summary Alarm summary (for EMAIL action)
 */
@Immutable
data class TaskAlarm(
    val id: Long = 0,
    val taskId: Long,
    val action: AlarmAction = AlarmAction.DISPLAY,
    val triggerMinutesBefore: Int? = null,
    val triggerAbsolute: Long? = null,
    val triggerRelativeTo: AlarmRelativeTo = AlarmRelativeTo.START,
    val description: String? = null,
    val summary: String? = null
) {
    /**
     * Checks if this is a relative trigger (vs absolute).
     */
    fun isRelativeTrigger(): Boolean = triggerMinutesBefore != null
    
    /**
     * Gets a human-readable description of the trigger time.
     */
    fun getTriggerDescription(): String {
        return when {
            triggerMinutesBefore != null -> {
                val hours = triggerMinutesBefore / 60
                val minutes = triggerMinutesBefore % 60
                when {
                    hours > 0 && minutes > 0 -> "$hours hour(s) $minutes minute(s) before"
                    hours > 0 -> "$hours hour(s) before"
                    minutes > 0 -> "$minutes minute(s) before"
                    else -> "At time of event"
                }
            }
            triggerAbsolute != null -> "At specific time"
            else -> "Unknown"
        }
    }
}

/**
 * Alarm action types from RFC 5545.
 */
enum class AlarmAction {
    /** Display a notification */
    DISPLAY,
    /** Play a sound */
    AUDIO,
    /** Send an email */
    EMAIL;
    
    companion object {
        fun fromValue(value: String?): AlarmAction {
            return when (value?.uppercase()) {
                "DISPLAY" -> DISPLAY
                "AUDIO" -> AUDIO
                "EMAIL" -> EMAIL
                else -> DISPLAY
            }
        }
    }
}

/**
 * What the alarm trigger is relative to.
 */
enum class AlarmRelativeTo {
    /** Relative to DTSTART */
    START,
    /** Relative to DUE (END) */
    END;
    
    companion object {
        fun fromValue(value: String?): AlarmRelativeTo {
            return when (value?.uppercase()) {
                "START" -> START
                "END" -> END
                else -> START
            }
        }
    }
}
