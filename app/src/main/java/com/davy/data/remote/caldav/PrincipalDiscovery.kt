package com.davy.data.remote.caldav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import java.io.StringReader
import timber.log.Timber

/**
 * CalDAV principal discovery implementation.
 * 
 * Implements RFC 4791 (CalDAV) and RFC 3744 (WebDAV ACL) for discovering
 * the current user's principal URL and calendar home set.
 * 
 * Discovery steps:
 * 1. Find current-user-principal
 * 2. Get calendar-home-set from principal
 * 3. Enumerate available calendars
 */
class PrincipalDiscovery @Inject constructor(
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val PROPFIND_METHOD = "PROPFIND"
        private const val DEPTH_HEADER = "Depth"
        private const val CONTENT_TYPE_XML = "application/xml; charset=utf-8"
        
        // WebDAV namespaces
        private const val NS_DAV = "DAV:"
        private const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    }

    /**
     * Discover CalDAV principal information for the authenticated user.
     * 
     * @param baseUrl CalDAV service base URL
     * @param username Username for authentication
     * @param password Password for authentication
     * @return PrincipalInfo containing principal URL and calendar home set
     * @throws PrincipalDiscoveryException if discovery fails
     */
    suspend fun discoverPrincipal(
        baseUrl: String,
        username: String,
        password: String
    ): PrincipalInfo {
        // Step 1: Find current-user-principal
        val principalUrl = findCurrentUserPrincipal(baseUrl, username, password)
            ?: throw PrincipalDiscoveryException(
                "Could not find current-user-principal. " +
                "This may be due to:\n" +
                "• Server rate limiting (too many requests) - wait 5-10 minutes and try again\n" +
                "• Incorrect server URL\n" +
                "• Server does not support CalDAV\n" +
                "• Network connectivity issues"
            )
        
        // Step 2: Get calendar-home-set from principal
        val calendarHomeSet = findCalendarHomeSet(principalUrl, username, password)
            ?: throw PrincipalDiscoveryException("Could not find calendar-home-set")
        
        // Step 3: Get display name if available
        val displayName = findDisplayName(principalUrl, username, password)
        
        return PrincipalInfo(
            principalUrl = principalUrl,
            calendarHomeSet = calendarHomeSet,
            displayName = displayName
        )
    }

    /**
     * Find current-user-principal URL.
     * 
     * Per RFC 5397 and reference implementation implementation: Query the SERVICE ENDPOINT (not a principal),
     * with Depth: 0, to get the current-user-principal property which contains the principal URL.
     * 
     * @param baseUrl Base URL to query (should be the service endpoint like /remote.php/dav)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Principal URL or null if not found
     */
    private suspend fun findCurrentUserPrincipal(
        baseUrl: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        Timber.tag("PrincipalDiscovery").d("Finding current-user-principal at: %s", baseUrl)
        
        // Query for current-user-principal AND calendar-home-set to minimize round trips
        // This follows RFC 6764 Section 6 and reference implementation's approach
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <d:current-user-principal />
                    <c:calendar-home-set />
                    <d:displayname />
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return@withContext try {
            val request = Request.Builder()
                .url(baseUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()
            
            Timber.tag("PrincipalDiscovery").d("Sending PROPFIND to: %s", baseUrl)
            val response = httpClient.newCall(request).execute()
            
            Timber.tag("PrincipalDiscovery").d("Response code: %s", response.code)
            
            // Accept 207 Multi-Status (proper WebDAV response), 200 OK, and 429 (rate limited but valid)
            if (response.code == 207 || response.code == 200) {
                val responseBody = response.body?.string()
                Timber.tag("PrincipalDiscovery").d("Response body:\n%s", responseBody)
                responseBody?.let { 
                    val result = extractPrincipalUrl(it, baseUrl)
                    Timber.tag("PrincipalDiscovery").d("Extracted principal URL: %s", result)
                    result
                }
            } else if (response.code == 429) {
                Timber.tag("PrincipalDiscovery").w("Server rate limiting (429) - endpoint is valid but temporarily blocked. Wait a few minutes and try again.")
                null
            } else {
                Timber.tag("PrincipalDiscovery").w("Unexpected response code %s: %s", response.code, response.body?.string())
                null
            }
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error finding current-user-principal")
            e.printStackTrace()
            null
        }
    }

    /**
     * Find calendar-home-set URL from principal.
     * 
     * @param principalUrl Principal URL
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Calendar home set URL or null if not found
     */
    private suspend fun findCalendarHomeSet(
        principalUrl: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        Timber.tag("PrincipalDiscovery").d("Finding calendar-home-set at principal: %s", principalUrl)
        
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <c:calendar-home-set />
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return@withContext try {
            val request = Request.Builder()
                .url(principalUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()
            
            Timber.tag("PrincipalDiscovery").d("Sending PROPFIND for calendar-home-set to: %s", principalUrl)
            val response = httpClient.newCall(request).execute()
            
            Timber.tag("PrincipalDiscovery").d("calendar-home-set response code: %s", response.code)
            
            if (response.code == 207 || response.code == 200) {
                val responseBody = response.body?.string()
                Timber.tag("PrincipalDiscovery").d("calendar-home-set response body:\n%s", responseBody)
                responseBody?.let { 
                    val result = extractCalendarHomeSet(it, principalUrl)
                    Timber.tag("PrincipalDiscovery").d("Extracted calendar-home-set: %s", result)
                    result
                }
            } else {
                Timber.tag("PrincipalDiscovery").w("Failed to get calendar-home-set: %s - %s", response.code, response.body?.string())
                null
            }
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error finding calendar-home-set")
            e.printStackTrace()
            null
        }
    }

    /**
     * Find display name from principal.
     * 
     * @param principalUrl Principal URL
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Display name or null if not found
     */
    private suspend fun findDisplayName(
        principalUrl: String,
        username: String,
        password: String
    ): String? {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname />
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return try {
            val request = Request.Builder()
                .url(principalUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { extractDisplayName(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract principal URL from PROPFIND response.
     */
    private fun extractPrincipalUrl(responseXml: String, baseUrl: String): String? {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Look for current-user-principal/href
            val hrefNode = xpath.evaluate(
                "//*[local-name()='current-user-principal']/*[local-name()='href']",
                document,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node
            
            hrefNode?.textContent?.let { href ->
                resolveUrl(baseUrl, href)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract calendar-home-set URL from PROPFIND response.
     */
    private fun extractCalendarHomeSet(responseXml: String, baseUrl: String): String? {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Look for calendar-home-set/href
            val hrefNode = xpath.evaluate(
                "//*[local-name()='calendar-home-set']/*[local-name()='href']",
                document,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node
            
            hrefNode?.textContent?.let { href ->
                resolveUrl(baseUrl, href)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract display name from PROPFIND response.
     */
    private fun extractDisplayName(responseXml: String): String? {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Look for displayname
            val displayNameNode = xpath.evaluate(
                "//*[local-name()='displayname']",
                document,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node
            
            displayNameNode?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse XML document from string.
     */
    private fun parseXmlDocument(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(org.xml.sax.InputSource(StringReader(xml)))
    }

    /**
     * Resolve relative URL against base URL.
     * 
     * @param baseUrl Base URL
     * @param href Relative or absolute href
     * @return Resolved absolute URL
     */
    private fun resolveUrl(baseUrl: String, href: String): String {
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("/") -> {
                // Absolute path - combine with base scheme and host
                val base = java.net.URL(baseUrl)
                "${base.protocol}://${base.host}${if (base.port != -1 && base.port != base.defaultPort) ":${base.port}" else ""}$href"
            }
            else -> {
                // Relative path - combine with base URL
                val normalizedBase = baseUrl.removeSuffix("/")
                "$normalizedBase/$href"
            }
        }
    }

    /**
     * Create Basic Authentication header value.
     */
    private fun createBasicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = android.util.Base64.encodeToString(
            credentials.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "Basic $encoded"
    }

    /**
     * Discover calendar collections from calendar-home-set.
     * 
     * Uses PROPFIND with Depth: 1 to list all calendar collections under the home-set.
     * This follows the reference implementation pattern from HomeSetRefresher.kt.
     * 
     * @param calendarHomeSetUrl Calendar home set URL (from PrincipalInfo)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return List of discovered calendar collections
     * @throws PrincipalDiscoveryException if discovery fails
     */
    suspend fun discoverCalendars(
        calendarHomeSetUrl: String,
        username: String,
        password: String
    ): List<CalendarCollectionInfo> = withContext(Dispatchers.IO) {
        Timber.tag("PrincipalDiscovery").d("Discovering calendars at: %s", calendarHomeSetUrl)
        
        // PROPFIND request body - request all relevant calendar properties
        // Including DAV:owner to identify shared calendars
        // Including CS:source to identify webcal subscriptions
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:apple="http://apple.com/ns/ical/">
                <d:prop>
                    <d:resourcetype />
                    <d:displayname />
                    <d:owner />
                    <c:calendar-description />
                    <apple:calendar-color />
                    <c:supported-calendar-component-set />
                    <d:current-user-privilege-set />
                    <cs:source />
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return@withContext try {
            val request = Request.Builder()
                .url(calendarHomeSetUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "1") // CRITICAL: Depth 1 lists children (the actual calendars)
                .build()
            
            Timber.tag("PrincipalDiscovery").d("Sending PROPFIND Depth: 1 for calendars")
            val response = httpClient.newCall(request).execute()
            
            Timber.tag("PrincipalDiscovery").d("Calendar discovery response code: %s", response.code)
            
            if (response.code == 207 || response.code == 200) {
                val responseBody = response.body?.string()
                Timber.tag("PrincipalDiscovery").d("Calendar discovery response body:\n%s", responseBody)
                
                if (responseBody != null) {
                    val calendars = parseCalendarCollections(responseBody, calendarHomeSetUrl)
                    Timber.tag("PrincipalDiscovery").d("Discovered %s calendars: %s", calendars.size, calendars.map { it.displayName })
                    calendars
                } else {
                    emptyList()
                }
            } else {
                Timber.tag("PrincipalDiscovery").w("Failed to discover calendars: %s - %s", response.code, response.body?.string())
                throw PrincipalDiscoveryException("Failed to discover calendars: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error discovering calendars")
            e.printStackTrace()
            throw PrincipalDiscoveryException("Calendar discovery failed: ${e.message}", e)
        }
    }

    /**
     * Parse calendar collections from PROPFIND Depth: 1 response.
     * 
     * Extracts all responses where resourcetype contains <calendar/>.
     */
    private fun parseCalendarCollections(responseXml: String, baseUrl: String): List<CalendarCollectionInfo> {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Find all response elements
            val responses = xpath.evaluate(
                "//*[local-name()='response']",
                document,
                XPathConstants.NODESET
            ) as org.w3c.dom.NodeList
            
            val calendars = mutableListOf<CalendarCollectionInfo>()
            
            for (i in 0 until responses.length) {
                val responseNode = responses.item(i)
                
                // Get href (calendar URL)
                val href = xpath.evaluate(
                    "*[local-name()='href']",
                    responseNode,
                    XPathConstants.STRING
                ) as String
                
                // Skip if href is empty or is the home-set itself
                if (href.isBlank() || href.removeSuffix("/") == baseUrl.removeSuffix("/")) {
                    continue
                }
                
                // Check if this is a calendar or webcal subscription
                // Regular calendars have <cal:calendar/> in resourcetype
                // Webcal subscriptions have <cs:subscribed/> in resourcetype
                val isCalendar = responseNode.let { node ->
                    val calendarNode = xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='resourcetype']/*[local-name()='calendar']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    calendarNode != null
                }
                
                val isSubscribed = responseNode.let { node ->
                    val subscribedNode = xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='resourcetype']/*[local-name()='subscribed']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    subscribedNode != null
                }
                
                // Skip if not a calendar or subscription
                if (!isCalendar && !isSubscribed) {
                    Timber.tag("PrincipalDiscovery").d("Skipping non-calendar collection: %s", href)
                    continue
                }
                
                // Extract calendar properties
                val displayName = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='displayname']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                val description = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='calendar-description']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                val color = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='calendar-color']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                // Extract owner (for identifying shared calendars)
                val owner = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='owner']/*[local-name()='href']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                // Extract source (for webcal subscriptions - external calendar URL)
                val source = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='source']/*[local-name()='href']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                // Parse supported calendar component set (VEVENT, VTODO, VJOURNAL)
                val componentSetNode = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='supported-calendar-component-set']",
                    responseNode,
                    XPathConstants.NODE
                ) as? org.w3c.dom.Node
                
                val supportsVEVENT = componentSetNode?.let { node ->
                    val veventNode = xpath.evaluate(
                        "*[local-name()='comp'][@name='VEVENT']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    veventNode != null
                } ?: true // Default to true if not specified
                
                val supportsVTODO = componentSetNode?.let { node ->
                    val vtodoNode = xpath.evaluate(
                        "*[local-name()='comp'][@name='VTODO']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    vtodoNode != null
                } ?: false
                
                val supportsVJOURNAL = componentSetNode?.let { node ->
                    val vjournalNode = xpath.evaluate(
                        "*[local-name()='comp'][@name='VJOURNAL']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    vjournalNode != null
                } ?: false
                
                // Parse write privilege from current-user-privilege-set (CalDAV ACL)
                // Look for <privilege><write-content/></privilege>
                // See reference implementation: Collection.privWriteContent determination
                val privWriteContent = responseNode.let { node ->
                    val writeContentNode = xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='current-user-privilege-set']/*[local-name()='privilege']/*[local-name()='write-content']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    writeContentNode != null
                }
                
                // Parse unbind (delete) privilege from current-user-privilege-set (CalDAV ACL)
                // Look for <privilege><unbind/></privilege>
                // See reference implementation: Collection.privUnbind determination
                val privUnbind = responseNode.let { node ->
                    val unbindNode = xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='current-user-privilege-set']/*[local-name()='privilege']/*[local-name()='unbind']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    unbindNode != null
                }
                
                val calendarUrl = resolveUrl(baseUrl, href)
                val resolvedOwner = owner?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveUrl(baseUrl, it) }
                val resolvedSource = source?.trim()?.takeIf { it.isNotEmpty() }
                
                // Log calendar discovery for debugging shared calendars and webcal subscriptions
                Timber.tag("PrincipalDiscovery").d("Discovered calendar: %s", calendarUrl)
                Timber.tag("PrincipalDiscovery").d("  - Display name: %s", displayName)
                Timber.tag("PrincipalDiscovery").d("  - Owner: %s", resolvedOwner)
                Timber.tag("PrincipalDiscovery").d("  - Source: %s", resolvedSource)
                Timber.tag("PrincipalDiscovery").d("  - VEVENT: %s, VTODO: %s", supportsVEVENT, supportsVTODO)
                Timber.tag("PrincipalDiscovery").d("  - Write privilege: %s", privWriteContent)
                Timber.tag("PrincipalDiscovery").d("  - Unbind privilege: %s", privUnbind)
                
                calendars.add(
                    CalendarCollectionInfo(
                        url = calendarUrl,
                        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } 
                            ?: calendarUrl.substringAfterLast('/').removeSuffix("/"),
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        color = color?.trim()?.takeIf { it.isNotEmpty() },
                        owner = resolvedOwner,
                        source = resolvedSource,
                        supportsVEVENT = supportsVEVENT,
                        supportsVTODO = supportsVTODO,
                        supportsVJOURNAL = supportsVJOURNAL,
                        privWriteContent = privWriteContent,
                        privUnbind = privUnbind
                    )
                )
            }
            
            calendars
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error parsing calendar collections")
            e.printStackTrace()
            emptyList()
        }
    }
}

/**
 * Principal information discovered from CalDAV server.
 */
data class PrincipalInfo(
    val principalUrl: String,
    val calendarHomeSet: String,
    val displayName: String? = null
)

/**
 * Calendar collection information discovered from CalDAV server.
 */
data class CalendarCollectionInfo(
    val url: String,
    val displayName: String,
    val description: String? = null,
    val color: String? = null,
    val owner: String? = null,            // Owner principal URL (for identifying shared calendars)
    val source: String? = null,           // Source URL (for webcal subscriptions - external calendar URL)
    val supportsVEVENT: Boolean = true,  // Events
    val supportsVTODO: Boolean = false,   // Tasks/Todos
    val supportsVJOURNAL: Boolean = false, // Journal entries
    val privWriteContent: Boolean = true,  // Write permission from DAV:current-user-privilege-set
    val privUnbind: Boolean = true        // Delete/unbind permission from DAV:current-user-privilege-set
)

/**
 * Exception thrown when principal discovery fails.
 */
class PrincipalDiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)
