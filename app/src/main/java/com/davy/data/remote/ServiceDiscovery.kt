package com.davy.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element
import timber.log.Timber
import java.io.StringReader
import java.util.Base64
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Service discovery implementation for CalDAV and CardDAV endpoints.
 * 
 * Implements RFC 6764 (SRV records) and well-known URIs (RFC 5785)
 * for discovering CalDAV and CardDAV service endpoints.
 */
class ServiceDiscovery @Inject constructor(
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val PROPFIND_METHOD = "PROPFIND"
        private const val DEPTH_HEADER = "Depth"
        private const val CONTENT_TYPE_XML = "application/xml; charset=utf-8"
        
        // Well-known URIs per RFC 6764
        private const val CALDAV_WELL_KNOWN = ".well-known/caldav"
        private const val CARDDAV_WELL_KNOWN = ".well-known/carddav"
        
        // Common server paths for CalDAV/CardDAV
        private const val NEXTCLOUD_DAV_PATH = "remote.php/dav"
        private const val OWNCLOUD_DAV_PATH = "remote.php/webdav"
        
        // WebDAV namespaces
        private const val NS_DAV = "DAV:"
        private const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
        private const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"

        // Additional common DAV paths for broader compatibility
        // Based on common implementations: Nextcloud, ownCloud, Radicale, Baikal, 
        // SabreDAV, SOGo, Kerio, DAViCal, etc.
        private val COMMON_DAV_PATHS = listOf(
            // Generic DAV paths
            "dav",
            "dav.php",
            "server.php/dav",          // Alternative Nextcloud/ownCloud path
            
            // CalDAV-specific paths
            "caldav",
            "caldav/",                 // Synology Calendar
            "caldav.php",
            "cal.php/calendars",       // Baikal CalDAV
            "calendars",
            "calendar",
            "calendar.php",
            
            // CardDAV-specific paths
            "carddav",
            "carddav/",                // Synology Contacts
            "carddav.php",
            "card.php/addressbooks",   // Baikal CardDAV
            "addressbooks",
            "addressbook",
            "contacts",
            "card.php",
            
            // Radicale paths
            "radicale",
            "radicale.py",
            
            // SOGo paths
            "SOGo/dav",
            
            // DAViCal paths
            "davical/caldav.php",
            "caldav.php/principals",
            
            // Kerio paths
            "dav/Calendar",
            "dav/Contacts",
            
            // SabreDAV paths
            "server.php",
            "calendarserver.php",
            "addressbookserver.php",
            
            // Other common paths
            "principals",
            "dav/principals",
            ".well-known/caldav",      // Explicit fallback
            ".well-known/carddav"      // Explicit fallback
        )
    }

    /**
     * Discover CalDAV and CardDAV service endpoints for a given server.
     * 
     * @param serverUrl Base server URL (e.g., "https://nextcloud.example.com")
     * @param username Username for authentication
     * @param password Password for authentication
     * @return ServiceEndpoints containing discovered CalDAV and CardDAV URLs
     * @throws ServiceDiscoveryException if discovery fails
     */
    suspend fun discoverServices(
        serverUrl: String,
        username: String,
        password: String
    ): ServiceEndpoints {
        val normalizedUrl = normalizeServerUrl(serverUrl)

        Timber.d("Starting service discovery for: %s", normalizedUrl)

        // Track if any response was from Cloudflare (blocking WebDAV methods)
        var cloudflareDetected = false
        val onCloudflareDetected = { cloudflareDetected = true }
        val isCloudflareDetected = { cloudflareDetected }

        val validatedCalDav = discoverService(normalizedUrl, CALDAV_WELL_KNOWN, true, username, password, isCloudflareDetected, onCloudflareDetected)
        val validatedCardDav = discoverService(normalizedUrl, CARDDAV_WELL_KNOWN, false, username, password, isCloudflareDetected, onCloudflareDetected)

        if (validatedCalDav == null && validatedCardDav == null) {
            // If Cloudflare was blocking requests, throw specific exception
            if (cloudflareDetected) {
                throw CloudflareBlockingException()
            }
            throw ServiceDiscoveryException("No CalDAV or CardDAV services found at $serverUrl")
        }
        
        return ServiceEndpoints(
            calDavUrl = validatedCalDav,
            cardDavUrl = validatedCardDav
        )
    }

    /**
     * Finds one service: well-known URI (host root per RFC 6764, then typed URL), the typed URL itself,
     * then Nextcloud/ownCloud and common paths. The first candidate that validates wins.
     */
    private suspend fun discoverService(
        normalizedUrl: String,
        wellKnownPath: String,
        isCalDAV: Boolean,
        username: String,
        password: String,
        isCloudflareDetected: () -> Boolean,
        onCloudflareDetected: () -> Unit
    ): String? {
        val validate: suspend (String) -> String? = { validateEndpoint(it, username, password, isCalDAV, onCloudflareDetected) }
        val typedUrl = normalizedUrl.toHttpUrlOrNull()
        val hasPath = typedUrl != null && typedUrl.encodedPath != "/"

        // Root first: Synology answers well-known below /caldav/ with the Calendar backend for both services
        val wellKnownBases = listOfNotNull(typedUrl?.resolve("/")?.toString()?.removeSuffix("/"), normalizedUrl).distinct()
        for (base in wellKnownBases) {
            discoverWellKnownEndpoint(base, wellKnownPath, username, password, onCloudflareDetected)
                ?.let { validate(it) }
                ?.let { return it }
        }

        // Collection URLs need the trailing slash that normalizeServerUrl removed (Synology answers 404/405 without it)
        if (hasPath) validate("$normalizedUrl/")?.let { return it }

        for (path in listOf(NEXTCLOUD_DAV_PATH, OWNCLOUD_DAV_PATH)) {
            validate("$normalizedUrl/$path")?.let { return it }
        }

        // If Cloudflare was already detected, don't waste time probing more paths
        if (isCloudflareDetected()) return null
        for (path in COMMON_DAV_PATHS) {
            validate("$normalizedUrl/$path")?.let { return it }
        }
        return null
    }

    /**
     * Discover service endpoint using well-known URI.
     * 
     * @param serverUrl Base server URL
     * @param wellKnownPath Well-known path (e.g., .well-known/caldav)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Discovered endpoint URL or null if not found
     */
    private suspend fun discoverWellKnownEndpoint(
        serverUrl: String,
        wellKnownPath: String,
        username: String,
        password: String,
        onCloudflareDetected: () -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val wellKnownUrl = "$serverUrl/$wellKnownPath"
        
        return@withContext try {
            val request = Request.Builder()
                .url(wellKnownUrl)
                .header("Authorization", createBasicAuthHeader(username, password))
                .build()
            
            // Disable automatic redirects here so we can explicitly read the Location header and build the absolute URL
            val noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                when {
                    response.isRedirect -> response.header("Location")?.let { resolveRedirect(request.url, it) }
                    // 200 OK - this IS the endpoint
                    response.isSuccessful -> wellKnownUrl
                    // Cloudflare blocking WebDAV methods
                    else -> {
                        if (CloudflareBlockingException.isCloudflareBlock(response)) {
                            Timber.w("Cloudflare blocking well-known endpoint: %s (cf-ray: %s)", wellKnownUrl, response.header("cf-ray"))
                            onCloudflareDetected()
                        }
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null // Discovery failed, will try alternatives
        }
    }

    /**
     * Resolves a redirect target against the request URL. Synology Calendar redirects to its
     * internal backend (http://localhost:/caldav.php/); a loopback target keeps its path on the request's origin.
     * An https to http downgrade is refused because the target receives Basic credentials.
     */
    private fun resolveRedirect(requestUrl: HttpUrl, location: String): String? {
        val target = requestUrl.resolve(location) ?: return null
        val isLoopback = target.host == "localhost" || target.host == "::1" || target.host.startsWith("127.")
        if (!isLoopback) return if (requestUrl.isHttps && !target.isHttps) null else target.toString()
        return target.newBuilder()
            .scheme(requestUrl.scheme)
            .host(requestUrl.host)
            .port(requestUrl.port)
            .build()
            .toString()
    }

    /**
     * Validate endpoint by performing PROPFIND request.
     * 
     * @param endpointUrl Endpoint URL to validate
     * @param username Username for authentication
     * @param password Password for authentication
     * @param isCalDAV Whether this is a CalDAV endpoint (vs CardDAV)
     * @return Validated endpoint URL or null if validation fails
     */
    private suspend fun validateEndpoint(
        endpointUrl: String,
        username: String,
        password: String,
        isCalDAV: Boolean,
        onCloudflareDetected: () -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val propfindBody = if (isCalDAV) {
            createCalDAVPropfindBody()
        } else {
            createCardDAVPropfindBody()
        }
        
        return@withContext try {
            val request = Request.Builder()
                .url(endpointUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                // Only accept 207 Multi-Status as valid DAV response
                // This ensures we're actually talking to a DAV server
                if (response.code == 207) {
                    val responseBody = response.body?.string()
                    if (responseBody != null && validatePropfindResponse(responseBody, isCalDAV)) {
                        Timber.d("Validated %s endpoint at: %s", if (isCalDAV) "CalDAV" else "CardDAV", endpointUrl)
                        endpointUrl
                    } else {
                        Timber.w("Invalid DAV response from %s - missing required properties", endpointUrl)
                        null
                    }
                } else {
                    if (CloudflareBlockingException.isCloudflareBlock(response)) {
                        Timber.w("Cloudflare blocking PROPFIND validation to %s (cf-ray: %s)", endpointUrl, response.header("cf-ray"))
                        onCloudflareDetected()
                    } else {
                        Timber.w("Non-DAV response code %d from %s", response.code, endpointUrl)
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to validate endpoint: %s", endpointUrl)
            null
        }
    }

    /**
     * Create PROPFIND request body for CalDAV endpoint validation.
     */
    private fun createCalDAVPropfindBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <d:resourcetype />
                    <d:displayname />
                    <c:calendar-home-set />
                    <d:current-user-principal />
                </d:prop>
            </d:propfind>
        """.trimIndent()
    }

    /**
     * Create PROPFIND request body for CardDAV endpoint validation.
     */
    private fun createCardDAVPropfindBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <d:prop>
                    <d:resourcetype />
                    <d:displayname />
                    <card:addressbook-home-set />
                    <d:current-user-principal />
                </d:prop>
            </d:propfind>
        """.trimIndent()
    }

    /**
     * Validate PROPFIND response contains expected properties.
     * 
     * @param responseXml XML response body
     * @param isCalDAV Whether this is a CalDAV response (vs CardDAV)
     * @return true if response is valid
     */
    private fun validatePropfindResponse(responseXml: String, isCalDAV: Boolean): Boolean {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document: Document = builder.parse(org.xml.sax.InputSource(StringReader(responseXml)))
            
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Check for multistatus response
            val multistatus = xpath.evaluate("//*[local-name()='multistatus']", document, XPathConstants.NODE)
            
            if (multistatus == null) {
                return false
            }
            
            // Look for calendar-home-set or addressbook-home-set
            val homeSetProperty = if (isCalDAV) {
                "//*[local-name()='calendar-home-set']"
            } else {
                "//*[local-name()='addressbook-home-set']"
            }
            
            val homeSet = xpath.evaluate(homeSetProperty, document, XPathConstants.NODE)
            if (homeSet != null) return true

            // Fallback: some servers expose only current-user-principal at the root
            val principal = xpath.evaluate("//*[local-name()='current-user-principal']", document, XPathConstants.NODE)
            principal != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalize server URL by removing trailing slashes and ensuring HTTPS.
     */
    private fun normalizeServerUrl(url: String): String {
        var normalized = url.trim().removeSuffix("/")
        
        // Add https:// if no protocol specified
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        
        return normalized
    }

    /**
     * Create Basic Authentication header value.
     */
    private fun createBasicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }
}

/**
 * Result of service discovery containing discovered endpoints.
 */
data class ServiceEndpoints(
    val calDavUrl: String?,
    val cardDavUrl: String?
) {
    fun hasCalDAV(): Boolean = calDavUrl != null
    fun hasCardDAV(): Boolean = cardDavUrl != null
    fun hasAnyService(): Boolean = hasCalDAV() || hasCardDAV()
}

/**
 * Exception thrown when service discovery fails.
 */
class ServiceDiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)
