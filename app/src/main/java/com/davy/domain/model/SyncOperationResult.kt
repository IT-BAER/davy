package com.davy.domain.model

/**
 * Result of sync operation.
 */
sealed class SyncOperationResult {
    data class Success(
        val downloaded: Int,
        val uploaded: Int,
        val deleted: Int,
        val conflicts: Int
    ) : SyncOperationResult() {
        val totalChanges: Int
            get() = downloaded + uploaded + deleted
    }
    
    data class Skipped(val reason: String) : SyncOperationResult()
    
    data class Failed(val error: Throwable) : SyncOperationResult()
}
