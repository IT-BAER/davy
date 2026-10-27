package com.davy.data.remote.carddav

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Handles CardDAV addressbook-query REPORT requests.
 * 
 * Implements RFC 6352 (CardDAV) addressbook-query REPORT method
 * for querying contacts in an address book.
 * 
 * @see <a href="https://tools.ietf.org/html/rfc6352#section-8.6">RFC 6352 Section 8.6</a>
 */
class AddressBookQuery {
    
    companion object {
        private const val CARDDAV_NAMESPACE = "urn:ietf:params:xml:ns:carddav"
        private const val DAV_NAMESPACE = "DAV:"

        // Prefix-agnostic: Nextcloud writes <d:href>, Radicale (Synology) writes <href> with
        // DAV: as default namespace and CardDAV as CR:. Self-closing tags never match.
        private fun element(name: String) = Regex(
            """<(?:[\w.-]+:)?$name(?:\s[^>]*)?(?<!/)>(.*?)</(?:[\w.-]+:)?$name\s*>""",
            RegexOption.DOT_MATCHES_ALL
        )

        private val RESPONSE = element("response")
        private val HREF = element("href")
        private val GETETAG = element("getetag")
        private val ADDRESS_DATA = element("address-data")
        private val STATUS = Regex("""<(?:[\w.-]+:)?status\s*>\s*HTTP/[\d.]+ (\d+)""")
    }
    
    /**
     * Creates addressbook-query REPORT request for all contacts.
     * 
     * Requests all vCard data for contacts in address book.
     * 
     * @param addressBookUrl Address book URL
     * @return XML request body
     */
  fun createQueryAllRequest(@Suppress("UNUSED_PARAMETER") addressBookUrl: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:addressbook-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav">
              <d:prop>
                <d:getetag/>
                <c:address-data/>
              </d:prop>
            </c:addressbook-query>
        """.trimIndent()
    }
    
    /**
     * Creates addressbook-query REPORT request with filter.
     * 
     * Filters contacts by property match.
     * 
     * @param propertyName vCard property name (e.g., "FN", "EMAIL", "TEL")
     * @param matchValue Value to match (substring match)
     * @param matchType Match type: "equals", "contains", "starts-with", "ends-with"
     * @return XML request body
     */
    fun createFilteredQueryRequest(
        propertyName: String,
        matchValue: String,
        matchType: String = "contains"
    ): String {
        val matchElement = when (matchType) {
            "equals" -> """<c:text-match collation="i;unicode-casemap" match-type="equals">$matchValue</c:text-match>"""
            "starts-with" -> """<c:text-match collation="i;unicode-casemap" match-type="starts-with">$matchValue</c:text-match>"""
            "ends-with" -> """<c:text-match collation="i;unicode-casemap" match-type="ends-with">$matchValue</c:text-match>"""
            else -> """<c:text-match collation="i;unicode-casemap" match-type="contains">$matchValue</c:text-match>"""
        }
        
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:addressbook-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav">
              <d:prop>
                <d:getetag/>
                <c:address-data/>
              </d:prop>
              <c:filter>
                <c:prop-filter name="$propertyName">
                  $matchElement
                </c:prop-filter>
              </c:filter>
            </c:addressbook-query>
        """.trimIndent()
    }
    
    /**
     * Creates addressbook-query for ETags only (no vCard data).
     * 
     * Used for incremental sync to check which contacts changed.
     * 
     * @return XML request body
     */
    fun createETagOnlyRequest(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:addressbook-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav">
              <d:prop>
                <d:getetag/>
              </d:prop>
            </c:addressbook-query>
        """.trimIndent()
    }
    
    /**
     * Creates addressbook-multiget request for specific contacts.
     * 
     * Efficiently fetches multiple contacts by URL.
     * 
     * @param addressBookUrl Base address book URL
     * @param contactUrls List of contact URLs to fetch
     * @return XML request body
     */
    fun createMultigetRequest(
  @Suppress("UNUSED_PARAMETER") addressBookUrl: String,
        contactUrls: List<String>
    ): String {
        val hrefElements = contactUrls.joinToString("\n") { url ->
            "    <d:href>$url</d:href>"
        }
        
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:addressbook-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav">
              <d:prop>
                <d:getetag/>
                <c:address-data/>
              </d:prop>
              $hrefElements
            </c:addressbook-multiget>
        """.trimIndent()
    }
    
    /**
     * Creates addressbook-query with specific vCard properties.
     * 
     * Limits returned data to specific properties for efficiency.
     * 
     * @param properties List of vCard properties to return (e.g., ["FN", "EMAIL", "TEL"])
     * @return XML request body
     */
    fun createPropertyLimitedRequest(properties: List<String>): String {
        val propElements = properties.joinToString("\n") { prop ->
            "        <c:prop name=\"$prop\"/>"
        }
        
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <c:addressbook-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav">
              <d:prop>
                <d:getetag/>
                <c:address-data>
                  $propElements
                </c:address-data>
              </d:prop>
            </c:addressbook-query>
        """.trimIndent()
    }
    
    /**
     * Creates RequestBody for CardDAV REPORT.
     * 
     * @param xml XML request body
     * @return RequestBody for OkHttp
     */
    fun createRequestBody(xml: String): RequestBody {
        return xml.toRequestBody("application/xml; charset=utf-8".toMediaType())
    }
    
    /**
     * Parses addressbook-query response.
     * 
     * Extracts contact URLs, ETags, and vCard data from XML response.
     * 
     * @param responseXml XML response from server
     * @return List of fetched contacts
     */
    fun parseQueryResponse(responseXml: String): List<FetchedContact> {
        val contacts = mutableListOf<FetchedContact>()
        
        // Simple XML parsing (production should use proper XML parser)
        RESPONSE.findAll(responseXml).forEach { match ->
            val responseBlock = match.groupValues[1]

            // Extract href
            val url = HREF.find(responseBlock)?.groupValues?.get(1)?.trim()

            // Extract ETag
            val etagRaw = GETETAG.find(responseBlock)?.groupValues?.get(1)?.trim()
            // Decode HTML entities and remove quotes (server returns &quot;abc123&quot; but we need abc123)
            val etag = etagRaw?.replace("&quot;", "\"")?.trim('"')

            // Extract vCard data (CardDAV prefix varies: c:, card:, CR:)
            val vcardDataRaw = ADDRESS_DATA.find(responseBlock)?.groupValues?.get(1)?.trim()
            // Decode HTML entities (&#13; = carriage return)
            val vcardData = vcardDataRaw?.replace("&#13;", "\r")?.replace("&#10;", "\n")

            // Extract status
            val statusMatch = STATUS.find(responseBlock)
            val status = statusMatch?.groupValues?.get(1)?.toIntOrNull() ?: 200
            
            if (url != null) {
                contacts.add(
                    FetchedContact(
                        url = url,
                        etag = etag,
                        vcardData = vcardData,
                        status = status
                    )
                )
            }
        }
        
        return contacts
    }
}

/**
 * Represents a contact fetched from CardDAV server.
 * 
 * @property url Contact URL
 * @property etag ETag for version tracking
 * @property vcardData vCard data (may be null if ETag-only request)
 * @property status HTTP status code
 */
data class FetchedContact(
    val url: String,
    val etag: String?,
    val vcardData: String?,
    val status: Int
)
