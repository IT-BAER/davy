package com.davy.domain.model

/**
 * Domain model for a task list.
 *
 * Represents a CalDAV task list (calendar with VTODO support).
 * Immutable data class for clean architecture.
 *
 * @property id Unique identifier (local database ID)
 * @property accountId Account ID this task list belongs to
 * @property url CalDAV URL for this task list
 * @property displayName Display name of the task list
 * @property color Task list color (hex format: #RRGGBB or #AARRGGBB)
 * @property ctag Collection tag for change detection
 * @property syncEnabled Whether sync is enabled for this task list
 * @property visible Whether task list is visible in UI
 * @property lastSynced Last sync timestamp (milliseconds since epoch)
 */
data class TaskList(
    val id: Long,
    val accountId: Long,
    val url: String,
    val displayName: String,
    val color: String? = null,
    val ctag: String? = null,
    val syncEnabled: Boolean = true,
    val visible: Boolean = true,
    val lastSynced: Long? = null
) {
    /**
     * Checks if this task list has been synced.
     *
     * @return True if synced at least once
     */
    fun isSynced(): Boolean = lastSynced != null

    /**
     * Checks if this task list needs syncing.
     * Task lists without ctag need full sync.
     *
     * @return True if needs sync
     */
    fun needsSync(): Boolean = ctag == null || lastSynced == null
}
