package com.davy.data.remote.carddav

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * CardDAV PUT implementation for contact upload.
 * Based on RFC 6352 Section 6.3.2 (Creating/Updating Address Object Resources).
 *
 * This class provides:
 * - Contact creation (new contacts)
 * - Contact update (existing contacts)
 * - Conditional PUT using If-Match with ETag to prevent conflicts
 * - If-None-Match for ensuring contact doesn't exist (creation)
 * - ETag extraction from response headers
 */
object ContactPut {

    /**
     * Result of a PUT request for a contact.
     *
     * @property etag The new ETag from the response header (for successful creation/update)
     * @property status HTTP status code (201 = created, 204 = updated, 412 = precondition failed)
     * @property isCreated Whether the contact was created (201 response)
     * @property isUpdated Whether the contact was updated (204 or 200 response)
     * @property isPreconditionFailed Whether the precondition failed (412 response)
     */
    data class PutResult(
        val etag: String?,
        val status: Int,
        val isCreated: Boolean = status == 201,
        val isUpdated: Boolean = status in listOf(200, 204),
        val isPreconditionFailed: Boolean = status == 412
    ) {
        /**
         * Whether the PUT was successful (created or updated).
         */
        fun isSuccess(): Boolean = isCreated || isUpdated

        /**
         * Whether the contact already exists (precondition failed on If-None-Match).
         */
        fun alreadyExists(): Boolean = isPreconditionFailed
    }

    /**
     * Creates a request body for contact upload.
     *
     * @param vcardData The vCard data as a string
     * @return RequestBody with vCard content type
     */
    fun createRequestBody(vcardData: String): RequestBody {
        return vcardData.toRequestBody("text/vcard; charset=utf-8".toMediaTypeOrNull())
    }

    /**
     * Builds the If-Match header value for conditional PUT (update).
     *
     * If-Match is used to ensure the contact hasn't been modified by another client.
     * If the ETag doesn't match, the server returns 412 Precondition Failed.
     *
     * This prevents the "lost update" problem where two clients update the same contact.
     *
     * @param etag The ETag to include in the If-Match header
     * @return Header value with quoted ETag
     */
    fun buildIfMatchHeader(etag: String): String {
        // ETags must be quoted in HTTP headers
        return "\"$etag\""
    }

    /**
     * Builds the If-None-Match header for contact creation.
     *
     * If-None-Match: * is used to ensure the contact doesn't already exist.
     * If the contact exists, the server returns 412 Precondition Failed.
     *
     * This prevents accidental overwrites when creating new contacts.
     *
     * @return Header value "*"
     */
    fun buildIfNoneMatchHeader(): String {
        return "*"
    }

    /**
     * Extracts the ETag from response headers.
     *
     * The ETag is returned after successful PUT operations and is used for:
     * - Future conditional GET requests (If-None-Match)
     * - Future conditional PUT requests (If-Match)
     * - Future conditional DELETE requests (If-Match)
     *
     * @param headers Map of response headers (case-insensitive)
     * @return ETag value without quotes, or null if not present
     */
    fun extractETag(headers: Map<String, List<String>>): String? {
        // Find ETag header (case-insensitive)
        val etagValues = headers.entries.find { 
            it.key.equals("ETag", ignoreCase = true) 
        }?.value

        if (etagValues.isNullOrEmpty()) {
            return null
        }

        // Get first ETag value
        val etag = etagValues.first()

        // Remove surrounding quotes if present
        // ETags are typically quoted: "3145-1234567890"
        return etag.trim().removeSurrounding("\"")
    }

    /**
     * Parses a PUT response into a PutResult.
     *
     * This is a helper method to standardize response handling.
     *
     * @param status HTTP status code
     * @param headers Response headers
     * @return PutResult containing parsed data
     */
    fun parsePutResponse(
        status: Int,
        headers: Map<String, List<String>>
    ): PutResult {
        val etag = extractETag(headers)

        return PutResult(
            etag = etag,
            status = status
        )
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
     * Validates vCard data before upload.
     *
     * Performs basic validation:
     * - Must start with BEGIN:VCARD
     * - Must end with END:VCARD
     * - Must have VERSION property
     * - Must have FN (formatted name) property
     * - Must have UID property
     *
     * @param vcardData The vCard data to validate
     * @return True if validation passes, false otherwise
     */
    fun isValidVCard(vcardData: String): Boolean {
        val trimmed = vcardData.trim()

        // Must start with BEGIN:VCARD
        if (!trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            return false
        }

        // Must end with END:VCARD
        if (!trimmed.endsWith("END:VCARD", ignoreCase = true)) {
            return false
        }

        // Must have VERSION property
        if (!trimmed.contains(Regex("\\bVERSION:", RegexOption.IGNORE_CASE))) {
            return false
        }

        // Must have FN (formatted name) property
        // This is required by RFC 6350
        if (!trimmed.contains(Regex("\\bFN:", RegexOption.IGNORE_CASE))) {
            return false
        }

        // Must have UID property
        // This is required for CardDAV
        if (!trimmed.contains(Regex("\\bUID:", RegexOption.IGNORE_CASE))) {
            return false
        }

        return true
    }

    /**
     * Determines the appropriate HTTP method for the operation.
     *
     * - PUT is used for both creation and update
     * - Some servers may require different handling based on If-Match vs If-None-Match
     *
     * @param isNew Whether this is a new contact (true) or an update (false)
     * @return HTTP method ("PUT")
     */
    fun getHttpMethod(@Suppress("UNUSED_PARAMETER") isNew: Boolean): String {
        // CardDAV uses PUT for both creation and update
        return "PUT"
    }

    /**
     * Builds the appropriate headers for a PUT request.
     *
     * For new contacts:
     * - If-None-Match: * (ensures contact doesn't exist)
     *
     * For updates:
     * - If-Match: "etag" (ensures contact hasn't changed)
     *
     * @param isNew Whether this is a new contact
     * @param etag The current ETag (required for updates, ignored for new contacts)
     * @return Map of headers to include in the request
     */
    fun buildHeaders(isNew: Boolean, etag: String?): Map<String, String> {
        return if (isNew) {
            mapOf("If-None-Match" to buildIfNoneMatchHeader())
        } else {
            if (etag != null) {
                mapOf("If-Match" to buildIfMatchHeader(etag))
            } else {
                // No ETag available, proceed without conditional PUT
                // This may result in lost updates if the contact was modified
                emptyMap()
            }
        }
    }
}
