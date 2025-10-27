package com.davy.data.remote.caldav

import android.content.Context
import com.davy.ui.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    @ApplicationContext private val context: Context,
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
    ): String? = withContext(Dispatchers.IO) {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname />
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
                    val initialCalendars = parseCalendarCollections(responseBody, calendarHomeSetUrl)
                    Timber.tag("PrincipalDiscovery").d("Discovered %s calendars: %s", initialCalendars.size, initialCalendars.map { it.displayName })

                    // Hydrate each calendar with a Depth:0 PROPFIND to ensure cs:source and ACL are present.
                    val hydrated = mutableListOf<CalendarCollectionInfo>()
                    for (cal in initialCalendars) {
                        val enriched = fetchCalendarCollection(cal.url, username, password)
                        if (enriched != null) {
                            hydrated += mergeCalendarInfo(cal, enriched)
                        } else {
                            hydrated += cal
                        }
                    }

                    // Retry up to 3 times (total ~6s) for WebCal subscriptions missing cs:source
                    // Some servers (e.g., Nextcloud) fill cs:source shortly after the collection appears.
                    val needsSource = hydrated.filter { it.isSubscribed && it.source.isNullOrBlank() }
                    if (needsSource.isNotEmpty()) {
                        Timber.tag("PrincipalDiscovery").d("%s subscribed calendars missing cs:source, retrying to hydrate...", needsSource.size)
                    }
                    var attempt = 0
                    var current: List<CalendarCollectionInfo> = hydrated
                    while (attempt < 3) {
                        val pending = current.filter { it.isSubscribed && it.source.isNullOrBlank() }
                        if (pending.isEmpty()) break
                        attempt++
                        Timber.tag("PrincipalDiscovery").d("Hydration retry #%s for %s subscribed collections", attempt, pending.size)
                        delay(1500L)
                        val updated = current.map { info ->
                            if (info.isSubscribed && info.source.isNullOrBlank()) {
                                val enrichedAgain = fetchCalendarCollection(info.url, username, password)
                                if (enrichedAgain != null) mergeCalendarInfo(info, enrichedAgain) else info
                            } else {
                                info
                            }
                        }
                        current = updated
                    }

                    current
                } else {
                    emptyList()
                }
            } else {
                Timber.tag("PrincipalDiscovery").w("Failed to discover calendars: %s - %s", response.code, response.body?.string())
                
                // Show notification for auth errors
                if (response.code == 401 || response.code == 403) {
                    NotificationHelper.showHttpErrorNotification(
                        context,
                        response.code
                    )
                }
                
                throw PrincipalDiscoveryException("Failed to discover calendars: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error discovering calendars")
            e.printStackTrace()
            throw PrincipalDiscoveryException("Calendar discovery failed: ${e.message}", e)
        }
    }

    /**
     * Fetch full properties for a single calendar collection using Depth:0.
     * Ensures we obtain cs:source (for WebCal) and DAV:current-user-privilege-set (ACL).
     */
    private suspend fun fetchCalendarCollection(
        collectionUrl: String,
        username: String,
        password: String
    ): CalendarCollectionInfo? = withContext(Dispatchers.IO) {
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
                .url(collectionUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()

            Timber.tag("PrincipalDiscovery").d("Hydrating collection (Depth:0): %s", collectionUrl)
            val response = httpClient.newCall(request).execute()
            if (response.code == 207 || response.code == 200) {
                val body = response.body?.string()
                body?.let { parseSingleCalendarCollection(it, collectionUrl) }
            } else {
                Timber.tag("PrincipalDiscovery").w("Failed to hydrate %s: %s", collectionUrl, response.code)
                null
            }
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").w(e, "Hydration error for %s", collectionUrl)
            null
        }
    }

    /**
     * Merge fields, preferring enriched values when present.
     */
    private fun mergeCalendarInfo(base: CalendarCollectionInfo, enriched: CalendarCollectionInfo): CalendarCollectionInfo {
        return base.copy(
            displayName = enriched.displayName.ifBlank { base.displayName },
            description = enriched.description ?: base.description,
            color = enriched.color ?: base.color,
            owner = enriched.owner ?: base.owner,
            source = enriched.source ?: base.source,
            supportsVEVENT = enriched.supportsVEVENT,
            supportsVTODO = enriched.supportsVTODO,
            supportsVJOURNAL = enriched.supportsVJOURNAL,
            privWriteContent = enriched.privWriteContent,
            privUnbind = enriched.privUnbind,
            isSubscribed = enriched.isSubscribed || base.isSubscribed
        )
    }

    /**
     * Parse a single collection PROPFIND response (Depth:0).
     */
    private fun parseSingleCalendarCollection(responseXml: String, requestUrl: String): CalendarCollectionInfo? {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            val responseNode = xpath.evaluate(
                "//*[local-name()='response']",
                document,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node ?: return null

            val href = xpath.evaluate(
                "*[local-name()='href']",
                responseNode,
                XPathConstants.STRING
            ) as String
            val calendarUrl = resolveUrl(requestUrl, href)

            val isCalendar = xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='resourcetype']/*[local-name()='calendar']",
                responseNode,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node != null

            val isSubscribed = xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='resourcetype']/*[local-name()='subscribed']",
                responseNode,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node != null

            if (!isCalendar && !isSubscribed) return null

            val displayName = (xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='displayname']",
                responseNode,
                XPathConstants.STRING
            ) as? String)?.trim()

            val description = (xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='calendar-description']",
                responseNode,
                XPathConstants.STRING
            ) as? String)?.trim()?.takeIf { it.isNotEmpty() }

            val color = (xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='calendar-color']",
                responseNode,
                XPathConstants.STRING
            ) as? String)?.trim()?.takeIf { it.isNotEmpty() }

            val owner = (xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='owner']/*[local-name()='href']",
                responseNode,
                XPathConstants.STRING
            ) as? String)?.let { hrefOwner -> resolveUrl(requestUrl, hrefOwner) }

            val source = (xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='source']/*[local-name()='href']",
                responseNode,
                XPathConstants.STRING
            ) as? String)?.trim()?.takeIf { it.isNotEmpty() }

            val componentSetNode = xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='supported-calendar-component-set']",
                responseNode,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node

            val supportsVEVENT = componentSetNode?.let { node ->
                xpath.evaluate(
                    "*[local-name()='comp'][@name='VEVENT']",
                    node,
                    XPathConstants.NODE
                ) as? org.w3c.dom.Node
            } != null

            val supportsVTODO = componentSetNode?.let { node ->
                xpath.evaluate(
                    "*[local-name()='comp'][@name='VTODO']",
                    node,
                    XPathConstants.NODE
                ) as? org.w3c.dom.Node
            } != null

            val supportsVJOURNAL = componentSetNode?.let { node ->
                xpath.evaluate(
                    "*[local-name()='comp'][@name='VJOURNAL']",
                    node,
                    XPathConstants.NODE
                ) as? org.w3c.dom.Node
            } != null

            val privilegeSetNode = xpath.evaluate(
                "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='current-user-privilege-set']",
                responseNode,
                XPathConstants.NODE
            ) as? org.w3c.dom.Node

            val privWriteContent = privilegeSetNode?.let { node ->
                xpath.evaluate(
                    "*[local-name()='privilege']/*[local-name()='write-content']",
                    node,
                    XPathConstants.NODE
                ) as? org.w3c.dom.Node
            } != null

            val privUnbind = privilegeSetNode?.let { node ->
                xpath.evaluate(
                    "*[local-name()='privilege']/*[local-name()='unbind']",
                    node,
                    XPathConstants.NODE
                ) as? org.w3c.dom.Node
            } != null

            CalendarCollectionInfo(
                url = calendarUrl,
                displayName = displayName?.takeIf { it.isNotEmpty() }
                    ?: calendarUrl.substringAfterLast('/').removeSuffix("/"),
                description = description,
                color = color,
                owner = owner,
                source = source,
                supportsVEVENT = supportsVEVENT,
                supportsVTODO = supportsVTODO,
                supportsVJOURNAL = supportsVJOURNAL,
                privWriteContent = privWriteContent,
                privUnbind = privUnbind,
                isSubscribed = isSubscribed
            )
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error parsing single calendar collection")
            null
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
                
                // Parse ACL privileges from current-user-privilege-set (CalDAV ACL spec)
                // CRITICAL: Use optimistic defaults (true) if privilege-set not present (DAVx5 pattern)
                // Only parse explicit privileges if server provides current-user-privilege-set
                val privilegeSetNode = responseNode.let { node ->
                    xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='current-user-privilege-set']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                }
                
                // Parse write privilege from current-user-privilege-set (CalDAV ACL)
                // Look for <privilege><write-content/></privilege>
                // CONSERVATIVE DEFAULT: Assume read-only if privilege-set missing
                // This prevents calendars from appearing writable on initial fetch when
                // the server hasn't sent complete privilege information yet
                val privWriteContent = if (privilegeSetNode != null) {
                    val writeContentNode = xpath.evaluate(
                        "*[local-name()='privilege']/*[local-name()='write-content']",
                        privilegeSetNode,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    writeContentNode != null
                } else {
                    false  // Conservative default - assume read-only until server confirms write access
                }
                
                // Parse unbind (delete) privilege from current-user-privilege-set (CalDAV ACL)
                // Look for <privilege><unbind/></privilege>
                // CONSERVATIVE DEFAULT: Assume can't delete if privilege-set missing
                val privUnbind = if (privilegeSetNode != null) {
                    val unbindNode = xpath.evaluate(
                        "*[local-name()='privilege']/*[local-name()='unbind']",
                        privilegeSetNode,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    unbindNode != null
                } else {
                    false  // Conservative default - assume can't delete until server confirms unbind permission
                }
                
                val calendarUrl = resolveUrl(baseUrl, href)
                val resolvedOwner = owner?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveUrl(baseUrl, it) }
                val resolvedSource = source?.trim()?.takeIf { it.isNotEmpty() }
                
                // Only use cs:source when explicitly provided by the server.
                // Do NOT fallback to the calendar collection URL; that is not the external WebCal feed URL.
                val finalSource = resolvedSource
                
                // Log calendar discovery for debugging shared calendars and webcal subscriptions
                Timber.tag("PrincipalDiscovery").d("Discovered calendar: %s", calendarUrl)
                Timber.tag("PrincipalDiscovery").d("  - Display name: %s", displayName)
                Timber.tag("PrincipalDiscovery").d("  - Owner: %s", resolvedOwner)
                Timber.tag("PrincipalDiscovery").d("  - Source: %s", finalSource)
                Timber.tag("PrincipalDiscovery").d("  - Is subscribed: %s", isSubscribed)
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
                        source = finalSource,
                        supportsVEVENT = supportsVEVENT,
                        supportsVTODO = supportsVTODO,
                        supportsVJOURNAL = supportsVJOURNAL,
                        privWriteContent = privWriteContent,
                        privUnbind = privUnbind,
                        isSubscribed = isSubscribed
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
    val privWriteContent: Boolean = false, // Write permission from DAV:current-user-privilege-set (conservative default)
    val privUnbind: Boolean = false,      // Delete/unbind permission from DAV:current-user-privilege-set (conservative default)
    val isSubscribed: Boolean = false      // Whether resourcetype contains cs:subscribed (WebCal)
)

/**
 * Exception thrown when principal discovery fails.
 */
class PrincipalDiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)
