package com.davy.ui

import androidx.compose.runtime.compositionLocalOf
import com.davy.sync.SyncManager

/**
 * Composition locals for providing app-level dependencies to composables.
 * 
 * This approach prevents creating heavy objects in remember blocks
 * and enables proper dependency injection in Compose.
 * 
 * Usage:
 * ```
 * CompositionLocalProvider(LocalSyncManager provides syncManager) {
 *     MyScreen()
 * }
 * 
 * // In screen:
 * val syncManager = LocalSyncManager.current
 * ```
 */

/**
 * Provides SyncManager to composables without manual instantiation.
 * Prevents performance issues from creating SyncManager in remember blocks.
 */
val LocalSyncManager = compositionLocalOf<SyncManager> {
    error("No SyncManager provided. Make sure to provide LocalSyncManager in your composition tree.")
}
