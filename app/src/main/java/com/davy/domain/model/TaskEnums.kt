package com.davy.domain.model

/**
 * Task priority levels based on RFC 5545.
 *
 * @property value Integer value (0-9)
 * @property label Human-readable label
 */
enum class TaskPriority(val value: Int, val label: String) {
    UNDEFINED(0, "No Priority"),
    HIGHEST(1, "Highest"),
    HIGH(2, "High"),
    NORMAL(5, "Normal"),
    LOW(7, "Low"),
    LOWEST(9, "Lowest");

    companion object {
        /**
         * Gets TaskPriority from integer value.
         *
         * @param value Priority value (0-9)
         * @return Closest matching TaskPriority
         */
        fun fromValue(value: Int?): TaskPriority {
            if (value == null) return UNDEFINED
            return when {
                value <= 0 -> UNDEFINED
                value == 1 -> HIGHEST
                value in 2..4 -> HIGH
                value == 5 -> NORMAL
                value in 6..8 -> LOW
                else -> LOWEST
            }
        }
    }
}

/**
 * Task status based on RFC 5545 VTODO STATUS.
 *
 * @property value iCalendar STATUS value
 * @property label Human-readable label
 */
enum class TaskStatus(val value: String, val label: String) {
    NEEDS_ACTION("NEEDS-ACTION", "To Do"),
    IN_PROCESS("IN-PROCESS", "In Progress"),
    COMPLETED("COMPLETED", "Completed"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        /**
         * Gets TaskStatus from string value.
         *
         * @param value STATUS value from iCalendar
         * @return Matching TaskStatus or NEEDS_ACTION as default
         */
        fun fromValue(value: String?): TaskStatus {
            if (value.isNullOrBlank()) return NEEDS_ACTION
            return values().find { it.value.equals(value, ignoreCase = true) } ?: NEEDS_ACTION
        }
    }
}
