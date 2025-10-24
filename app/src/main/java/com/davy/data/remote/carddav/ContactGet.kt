package com.davy.data.remote.carddav

/**
 * CardDAV GET implementation for contact retrieval.
 * Based on RFC 6352 Section 6.3.2 (Retrieving Address Object Resources).
 *
 * This class provides:
 * - Contact retrieval via HTTP GET
 * - Conditional GET using If-None-Match with ETag
 * - ETag extraction from response headers
 * - vCard content validation
 */
object ContactGet {

    /**
     * Result of a GET request for a contact.
     *
     * @property vcardData The vCard data as a string (null if 304 Not Modified)
     * @property etag The ETag from the response header
     * @property status HTTP status code (200 = success, 304 = not modified, 404 = not found)
     * @property isNotModified Whether the contact has not been modified (304 response)
     */
    data class GetResult(
        val vcardData: String?,
        val etag: String?,
        val status: Int,
        val isNotModified: Boolean = status == 304
    ) {
        /**
         * Whether the GET was successful (200 OK).
         */
        fun isSuccess(): Boolean = status == 200

        /**
         * Whether the contact was not found (404 Not Found).
         */
        fun isNotFound(): Boolean = status == 404
    }

    /**
     * Extracts the ETag from response headers.
     *
     * ETags are used for:
     * - Conditional GET (If-None-Match) to avoid unnecessary downloads
     * - Conditional PUT (If-Match) to prevent conflicts
     * - Conditional DELETE (If-Match) to prevent accidental deletion
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
     * Validates that the response content type is vCard.
     *
     * Valid content types:
     * - text/vcard
     * - text/vcard; charset=utf-8
     * - text/x-vcard (legacy)
     *
     * @param headers Map of response headers (case-insensitive)
     * @return True if content type is vCard, false otherwise
     */
    fun isVCardContentType(headers: Map<String, List<String>>): Boolean {
        // Find Content-Type header (case-insensitive)
        val contentTypeValues = headers.entries.find { 
            it.key.equals("Content-Type", ignoreCase = true) 
        }?.value

        if (contentTypeValues.isNullOrEmpty()) {
            return false
        }

        // Get first Content-Type value
        val contentType = contentTypeValues.first().lowercase()

        // Check if it's a vCard content type
        return contentType.contains("text/vcard") || contentType.contains("text/x-vcard")
    }

    /**
     * Builds the If-None-Match header value for conditional GET.
     *
     * If-None-Match is used to check if the resource has been modified.
     * If the ETag matches, the server returns 304 Not Modified without the body.
     *
     * @param etag The ETag to include in the If-None-Match header
     * @return Header value with quoted ETag
     */
    fun buildIfNoneMatchHeader(etag: String): String {
        // ETags must be quoted in HTTP headers
        return "\"$etag\""
    }

    /**
     * Parses a GET response into a GetResult.
     *
     * This is a helper method to standardize response handling.
     *
     * @param status HTTP status code
     * @param headers Response headers
     * @param body Response body (may be null for 304)
     * @return GetResult containing parsed data
     */
    fun parseGetResponse(
        status: Int,
        headers: Map<String, List<String>>,
        body: String?
    ): GetResult {
        val etag = extractETag(headers)

        return when (status) {
            200 -> {
                // Validate content type
                if (!isVCardContentType(headers)) {
                    // Invalid content type, treat as error
                    GetResult(
                        vcardData = null,
                        etag = etag,
                        status = status
                    )
                } else {
                    GetResult(
                        vcardData = body,
                        etag = etag,
                        status = status
                    )
                }
            }
            304 -> {
                // Not Modified - no body returned
                GetResult(
                    vcardData = null,
                    etag = etag,
                    status = status,
                    isNotModified = true
                )
            }
            else -> {
                // Error status (404, 403, 500, etc.)
                GetResult(
                    vcardData = null,
                    etag = etag,
                    status = status
                )
            }
        }
    }

    /**
     * Validates vCard data format.
     *
     * Performs basic validation:
     * - Must start with BEGIN:VCARD
     * - Must end with END:VCARD
     * - Must have FN (formatted name) property
     *
     * @param vcardData The vCard data to validate
     * @return True if basic validation passes, false otherwise
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

        // Must have FN (formatted name) property
        // This is required by RFC 6350
        if (!trimmed.contains(Regex("\\bFN:", RegexOption.IGNORE_CASE))) {
            return false
        }

        return true
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
}
