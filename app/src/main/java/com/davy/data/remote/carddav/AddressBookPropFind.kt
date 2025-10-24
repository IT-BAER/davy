package com.davy.data.remote.carddav

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * CardDAV PROPFIND implementation for address book discovery.
 * Based on RFC 6352 Section 5.2 (Address Book Properties) and RFC 4918 (WebDAV PROPFIND).
 *
 * This class provides:
 * - Address book discovery via PROPFIND with different depths
 * - Property extraction (resourcetype, displayname, ctag, description)
 * - Multi-status response parsing
 */
object AddressBookPropFind {

    /**
     * Creates a PROPFIND request body to discover address books at depth 0.
     * Depth 0 means properties of the target resource only.
     *
     * Requested properties:
     * - resourcetype: Identifies if the resource is an addressbook
     * - displayname: Human-readable name for the address book
     * - current-user-privilege-set: User permissions
     * - getctag: Collection tag for sync tracking (from RFC 6578)
     * - addressbook-description: Description of the address book
     * - supported-address-data: Supported vCard versions and encodings
     *
     * @return RequestBody containing the XML PROPFIND request for depth 0
     */
    fun createDepth0Request(): RequestBody {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav" xmlns:cs="http://calendarserver.org/ns/">
    <d:prop>
        <d:resourcetype />
        <d:displayname />
        <d:current-user-privilege-set />
        <cs:getctag />
        <c:addressbook-description />
        <c:supported-address-data />
    </d:prop>
</d:propfind>"""
        return createRequestBody(xml)
    }

    /**
     * Creates a PROPFIND request body to discover address books at depth 1.
     * Depth 1 means properties of the target resource and its immediate children.
     *
     * This is typically used on the user's home collection to find all address books.
     *
     * @return RequestBody containing the XML PROPFIND request for depth 1
     */
    fun createDepth1Request(): RequestBody {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav" xmlns:cs="http://calendarserver.org/ns/">
    <d:prop>
        <d:resourcetype />
        <d:displayname />
        <d:current-user-privilege-set />
        <cs:getctag />
        <c:addressbook-description />
        <c:supported-address-data />
    </d:prop>
</d:propfind>"""
        return createRequestBody(xml)
    }

    /**
     * Creates a minimal PROPFIND request for ctag-only queries.
     * Used for quick sync state checks without fetching all properties.
     *
     * @return RequestBody containing the XML PROPFIND request for ctag only
     */
    fun createCTagOnlyRequest(): RequestBody {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/">
    <d:prop>
        <cs:getctag />
    </d:prop>
</d:propfind>"""
        return createRequestBody(xml)
    }

    /**
     * Wraps XML string into OkHttp RequestBody.
     *
     * @param xml The XML string to wrap
     * @return RequestBody with proper content type
     */
    private fun createRequestBody(xml: String): RequestBody {
        return xml.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull())
    }

    /**
     * Parses a PROPFIND multi-status response and extracts discovered address books.
     *
     * Expected XML structure:
     * ```xml
     * <multistatus>
     *   <response>
     *     <href>/addressbooks/user/contacts/</href>
     *     <propstat>
     *       <prop>
     *         <resourcetype><collection/><addressbook/></resourcetype>
     *         <displayname>My Contacts</displayname>
     *         <getctag>3145</getctag>
     *         <addressbook-description>Personal contacts</addressbook-description>
     *       </prop>
     *       <status>HTTP/1.1 200 OK</status>
     *     </propstat>
     *   </response>
     * </multistatus>
     * ```
     *
     * @param responseXml The XML response from the server
     * @return List of discovered address books
     */
    fun parsePropFindResponse(responseXml: String): List<DiscoveredAddressBook> {
        val addressBooks = mutableListOf<DiscoveredAddressBook>()

        // Regex patterns for extracting response elements
        val responsePattern = Regex("<d:response>(.*?)</d:response>", RegexOption.DOT_MATCHES_ALL)
        val hrefPattern = Regex("<d:href>(.*?)</d:href>", RegexOption.DOT_MATCHES_ALL)
        val propPattern = Regex("<d:prop>(.*?)</d:prop>", RegexOption.DOT_MATCHES_ALL)
        val statusPattern = Regex("<d:status>HTTP/1\\.1 (\\d+)", RegexOption.DOT_MATCHES_ALL)

        // Extract all response blocks
        responsePattern.findAll(responseXml).forEach { responseMatch ->
            val responseBlock = responseMatch.groupValues[1]

            // Extract href (URL)
            val url = hrefPattern.find(responseBlock)?.groupValues?.get(1)?.trim() ?: return@forEach

            // Extract propstat block
            val propBlock = propPattern.find(responseBlock)?.groupValues?.get(1) ?: return@forEach

            // Extract status code
            val statusMatch = statusPattern.find(responseBlock)
            val status = statusMatch?.groupValues?.get(1)?.toIntOrNull() ?: 200

            // Only process successful responses
            if (status !in 200..299) return@forEach

            // Check if resourcetype contains addressbook
            val isAddressBook = propBlock.contains("<c:addressbook") || propBlock.contains("<addressbook")
            if (!isAddressBook) return@forEach

            // Extract properties
            val displayName = extractProperty(propBlock, "displayname")
            val ctag = extractProperty(propBlock, "getctag")
            val description = extractProperty(propBlock, "addressbook-description")

            // Extract supported-address-data
            val supportedFormats = extractSupportedAddressData(propBlock)

            addressBooks.add(
                DiscoveredAddressBook(
                    url = url,
                    displayName = displayName,
                    description = description,
                    ctag = ctag,
                    isAddressBook = true,
                    supportedFormats = supportedFormats
                )
            )
        }

        return addressBooks
    }

    /**
     * Extracts a simple property value from a prop block.
     *
     * @param propBlock The XML prop block to search
     * @param propertyName The name of the property to extract
     * @return The property value, or null if not found
     */
    private fun extractProperty(propBlock: String, propertyName: String): String? {
        // Try with namespace prefix - support any prefix (d:, cs:, c:, x1:, etc.)
        val patterns = listOf(
            Regex("<d:$propertyName>(.*?)</d:$propertyName>", RegexOption.DOT_MATCHES_ALL),
            Regex("<cs:$propertyName>(.*?)</cs:$propertyName>", RegexOption.DOT_MATCHES_ALL),
            Regex("<c:$propertyName>(.*?)</c:$propertyName>", RegexOption.DOT_MATCHES_ALL),
            Regex("<x1:$propertyName[^>]*>(.*?)</x1:$propertyName>", RegexOption.DOT_MATCHES_ALL),
            Regex("<\\w+:$propertyName[^>]*>(.*?)</\\w+:$propertyName>", RegexOption.DOT_MATCHES_ALL),
            Regex("<$propertyName>(.*?)</$propertyName>", RegexOption.DOT_MATCHES_ALL)
        )

        for (pattern in patterns) {
            val match = pattern.find(propBlock)
            if (match != null) {
                return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
            }
        }

        return null
    }

    /**
     * Extracts supported-address-data formats from the prop block.
     *
     * Example XML:
     * ```xml
     * <supported-address-data>
     *   <address-data-type content-type="text/vcard" version="3.0"/>
     *   <address-data-type content-type="text/vcard" version="4.0"/>
     * </supported-address-data>
     * ```
     *
     * @param propBlock The XML prop block to search
     * @return List of supported vCard formats (e.g., ["3.0", "4.0"])
     */
    private fun extractSupportedAddressData(propBlock: String): List<String> {
        val formats = mutableListOf<String>()

        val addressDataPattern = Regex(
            """<c:address-data-type[^>]*content-type="text/vcard"[^>]*version="([^"]+)"""",
            RegexOption.DOT_MATCHES_ALL
        )

        addressDataPattern.findAll(propBlock).forEach { match ->
            val version = match.groupValues[1]
            formats.add(version)
        }

        // Default to vCard 3.0 if no supported formats found
        if (formats.isEmpty()) {
            formats.add("3.0")
        }

        return formats
    }

    /**
     * Data class representing a discovered address book from PROPFIND.
     *
     * @property url The URL (href) of the address book
     * @property displayName Human-readable name of the address book
     * @property description Optional description of the address book
     * @property ctag Collection tag for sync tracking (null if not present)
     * @property isAddressBook Whether this resource is an address book
     * @property supportedFormats List of supported vCard versions (e.g., ["3.0", "4.0"])
     */
    data class DiscoveredAddressBook(
        val url: String,
        val displayName: String?,
        val description: String?,
        val ctag: String?,
        val isAddressBook: Boolean,
        val supportedFormats: List<String>
    )
}
