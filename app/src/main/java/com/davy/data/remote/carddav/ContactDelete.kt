package com.davy.data.remote.carddav

/**
 * CardDAV DELETE implementation for contact deletion.
 * Based on RFC 6352 Section 6.3.2 (Deleting Address Object Resources).
 *
 * This class provides:
 * - Contact deletion via HTTP DELETE
 * - Conditional DELETE using If-Match with ETag to prevent accidental deletion
 * - Status code interpretation
 * - Error handling
 */
object ContactDelete {

    /**
     * Result of a DELETE request for a contact.
     *
     * @property status HTTP status code
     * @property isDeleted Whether the contact was successfully deleted (200, 204, or 404)
     * @property isPreconditionFailed Whether the precondition failed (412 response)
     * @property isForbidden Whether the deletion was forbidden (403 response)
     * @property isLocked Whether the resource is locked (423 response)
     */
    data class DeleteResult(
        val status: Int,
        val isDeleted: Boolean = status in listOf(200, 204, 404),
        val isPreconditionFailed: Boolean = status == 412,
        val isForbidden: Boolean = status == 403,
        val isLocked: Boolean = status == 423
    ) {
        /**
         * Whether the DELETE was successful.
         * Note: 404 is considered success as the contact is gone.
         */
        fun isSuccess(): Boolean = isDeleted

        /**
         * Whether the contact was not found (404 Not Found).
         * This is still considered a successful deletion.
         */
        fun isNotFound(): Boolean = status == 404
    }

    /**
     * Builds the If-Match header value for conditional DELETE.
     *
     * If-Match is used to ensure the contact hasn't been modified by another client.
     * If the ETag doesn't match, the server returns 412 Precondition Failed.
     *
     * This prevents accidental deletion of contacts that have been modified.
     *
     * @param etag The ETag to include in the If-Match header
     * @return Header value with quoted ETag
     */
    fun buildIfMatchHeader(etag: String): String {
        // ETags must be quoted in HTTP headers
        return "\"$etag\""
    }

    /**
     * Parses a DELETE response into a DeleteResult.
     *
     * This is a helper method to standardize response handling.
     *
     * Common status codes:
     * - 200 OK: Deletion successful, response body may contain information
     * - 204 No Content: Deletion successful, no response body
     * - 404 Not Found: Contact already deleted or never existed (considered success)
     * - 412 Precondition Failed: ETag mismatch (contact was modified)
     * - 403 Forbidden: No permission to delete
     * - 423 Locked: Resource is locked (WebDAV locking)
     * - 500 Internal Server Error: Server error
     *
     * @param status HTTP status code
     * @return DeleteResult containing parsed data
     */
    fun parseDeleteResponse(status: Int): DeleteResult {
        return DeleteResult(status = status)
    }

    /**
     * Determines the contact URL from an address book URL and contact UID.
     *
     * CardDAV servers typically use the UID with .vcf extension as the resource name.
     *
     * Example:
     * - Address book: https://server.com/addressbooks/user/contacts/
     * - UID: 12345678-1234-1234-1234-123456789012
     * - Contact URL: https://server.com/addressbooks/user/contacts/12345678-1234-1234-1234-123456789012.vcf
     *
     * @param addressBookUrl The address book URL (must end with /)
     * @param uid The contact UID
     * @return The full contact URL
     */
    fun buildContactUrl(addressBookUrl: String, uid: String): String {
        val baseUrl = if (addressBookUrl.endsWith("/")) {
            addressBookUrl
        } else {
            "$addressBookUrl/"
        }

        return "${baseUrl}${uid}.vcf"
    }

    /**
     * Builds the appropriate headers for a DELETE request.
     *
     * If ETag is provided:
     * - If-Match: "etag" (ensures contact hasn't changed)
     *
     * If ETag is not provided:
     * - No conditional headers (may result in accidental deletion of modified contacts)
     *
     * @param etag The current ETag (recommended for safe deletion)
     * @return Map of headers to include in the request
     */
    fun buildHeaders(etag: String?): Map<String, String> {
        return if (etag != null) {
            mapOf("If-Match" to buildIfMatchHeader(etag))
        } else {
            // No ETag available, proceed without conditional DELETE
            // This may result in accidental deletion if the contact was modified
            emptyMap()
        }
    }

    /**
     * Determines the deletion strategy based on the result.
     *
     * This helps the caller decide what to do after a DELETE request:
     * - SUCCESS: Remove from local database
     * - PRECONDITION_FAILED: Fetch latest version, show conflict dialog
     * - FORBIDDEN: Show error, keep in database
     * - LOCKED: Show error, retry later
     * - NOT_FOUND: Already deleted, remove from local database
     *
     * @param result The DeleteResult from parseDeleteResponse
     * @return Strategy enum value
     */
    fun getDeletionStrategy(result: DeleteResult): DeletionStrategy {
        return when {
            result.isSuccess() -> DeletionStrategy.SUCCESS
            result.isPreconditionFailed -> DeletionStrategy.PRECONDITION_FAILED
            result.isForbidden -> DeletionStrategy.FORBIDDEN
            result.isLocked -> DeletionStrategy.LOCKED
            else -> DeletionStrategy.ERROR
        }
    }

    /**
     * Deletion strategy enum for handling different DELETE outcomes.
     */
    enum class DeletionStrategy {
        /**
         * Deletion successful - remove from local database.
         */
        SUCCESS,

        /**
         * Precondition failed (ETag mismatch) - fetch latest version and handle conflict.
         */
        PRECONDITION_FAILED,

        /**
         * Forbidden (no permission) - show error, keep in database.
         */
        FORBIDDEN,

        /**
         * Resource locked - retry later.
         */
        LOCKED,

        /**
         * Other error - show error, keep in database.
         */
        ERROR
    }

    /**
     * Validates whether a DELETE should be attempted.
     *
     * Checks:
     * - Contact URL is not empty
     * - Contact URL is valid (starts with http:// or https://)
     *
     * @param contactUrl The contact URL to delete
     * @return True if DELETE should be attempted, false otherwise
     */
    fun shouldAttemptDelete(contactUrl: String): Boolean {
        if (contactUrl.isBlank()) {
            return false
        }

        if (!contactUrl.startsWith("http://", ignoreCase = true) && 
            !contactUrl.startsWith("https://", ignoreCase = true)) {
            return false
        }

        return true
    }

    /**
     * Determines whether to use conditional DELETE (If-Match).
     *
     * Conditional DELETE is recommended when:
     * - ETag is available (contact has been synced)
     * - Contact may have been modified by another client
     *
     * Non-conditional DELETE may be used when:
     * - Contact is newly created and not yet synced
     * - ETag is not available (though this is risky)
     *
     * @param etag The current ETag (null if not available)
     * @return True if conditional DELETE should be used, false otherwise
     */
    fun shouldUseConditionalDelete(etag: String?): Boolean {
        return !etag.isNullOrBlank()
    }
}
