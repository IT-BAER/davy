package com.davy.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        private val COMMON_DAV_PATHS = listOf(
            "dav.php",
            "caldav.php",
            "carddav.php",
            "dav",
            "caldav",
            "carddav",
            "principals",
            "addressbooks"
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
        
        // Try well-known URIs first (most common for modern servers)
        var calDavUrl = discoverWellKnownEndpoint(normalizedUrl, CALDAV_WELL_KNOWN, username, password)
        var cardDavUrl = discoverWellKnownEndpoint(normalizedUrl, CARDDAV_WELL_KNOWN, username, password)
        
    Timber.d("Well-known discovery - CalDAV: %s, CardDAV: %s", calDavUrl, cardDavUrl)
        
        // If well-known URIs failed, try common server paths (Nextcloud, ownCloud)
        var isNextcloudPath = false
        if (calDavUrl == null && cardDavUrl == null) {
            Timber.d("Well-known URIs failed, trying Nextcloud/ownCloud paths")
            
            val nextcloudDavUrl = "$normalizedUrl/$NEXTCLOUD_DAV_PATH"
            
            // Try Nextcloud/ownCloud path
            if (isNextcloudOrOwnCloud(nextcloudDavUrl, username, password)) {
                Timber.d("Nextcloud/ownCloud detected at: %s", nextcloudDavUrl)
                calDavUrl = nextcloudDavUrl
                cardDavUrl = nextcloudDavUrl
                isNextcloudPath = true
            } else {
                // Try ownCloud-specific path
                val owncloudDavUrl = "$normalizedUrl/$OWNCLOUD_DAV_PATH"
                if (isNextcloudOrOwnCloud(owncloudDavUrl, username, password)) {
                    Timber.d("ownCloud detected at: %s", owncloudDavUrl)
                    calDavUrl = owncloudDavUrl
                    cardDavUrl = owncloudDavUrl
                    isNextcloudPath = true
                } else {
                    Timber.w("Neither Nextcloud nor ownCloud path responded")

                    // As a last resort, probe a few common DAV endpoints used by various servers
                    for (path in COMMON_DAV_PATHS) {
                        val candidate = "$normalizedUrl/$path"
                        if (probeDavEndpoint(candidate, username, password)) {
                            Timber.d("Detected DAV endpoint at common path: %s", candidate)
                            calDavUrl = candidate
                            cardDavUrl = candidate
                            break
                        }
                    }
                }
            }
        }
        
        // Validate discovered endpoints with PROPFIND
        // Skip strict validation for Nextcloud/ownCloud base paths since they're already verified
        val validatedCalDav = if (isNextcloudPath) {
            calDavUrl
        } else {
            calDavUrl?.let { validateEndpoint(it, username, password, isCalDAV = true) }
        }
        val validatedCardDav = if (isNextcloudPath) {
            cardDavUrl
        } else {
            cardDavUrl?.let { validateEndpoint(it, username, password, isCalDAV = false) }
        }
        
        if (validatedCalDav == null && validatedCardDav == null) {
            throw ServiceDiscoveryException("No CalDAV or CardDAV services found at $serverUrl")
        }
        
        return ServiceEndpoints(
            calDavUrl = validatedCalDav,
            cardDavUrl = validatedCardDav
        )
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
        password: String
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

            val response = noRedirectClient.newCall(request).execute()
            
            when {
                // 301/302 redirect - follow to actual endpoint
                response.code in 301..302 -> {
                    response.header("Location")?.let { location ->
                        if (location.startsWith("http")) {
                            location
                        } else {
                            "$serverUrl$location"
                        }
                    }
                }
                // 200 OK - this IS the endpoint
                response.isSuccessful -> wellKnownUrl
                // Not found or unauthorized
                else -> null
            }
        } catch (e: Exception) {
            null // Discovery failed, will try alternatives
        }
    }

    /**
     * Check if URL is a Nextcloud or ownCloud DAV endpoint.
     * 
     * @param davUrl DAV endpoint URL to check
     * @param username Username for authentication
     * @param password Password for authentication
     * @return true if the URL is a valid Nextcloud/ownCloud DAV endpoint
     */
    private suspend fun isNextcloudOrOwnCloud(
        davUrl: String,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Checking if %s is Nextcloud/ownCloud", davUrl)
            
            val request = Request.Builder()
                .url(davUrl)
                .method(PROPFIND_METHOD, "".toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseCode = response.code
            
            Timber.d("Nextcloud check for %s returned code: %s", davUrl, responseCode)
            
            // Nextcloud/ownCloud returns 207 Multi-Status for successful PROPFIND
            // Also accept 200 OK (some servers)
            // Accept 401 Unauthorized (endpoint exists and requires auth - this is good!)
            // Accept 403 Forbidden (endpoint exists but might need different path)
            // Accept 405 Method Not Allowed (endpoint exists but PROPFIND not allowed at root)
            // Accept 429 Too Many Requests (endpoint exists but rate-limited)
            when (responseCode) {
                207, 200, 401, 403, 405, 429 -> {
                    Timber.d("Endpoint confirmed at %s with code %s", davUrl, responseCode)
                    true
                }
                else -> {
                    Timber.w("Unexpected response code %s from %s", responseCode, davUrl)
                    false
                }
            }
        } catch (e: Exception) {
            // Log exception for debugging
            Timber.e(e, "Error checking Nextcloud/ownCloud endpoint %s: %s", davUrl, e.message)
            false
        }
    }

    /**
     * Generic DAV endpoint probe using PROPFIND. Accepts a broad set of response codes that
     * indicate a DAV resource exists (even if authentication is required at this stage).
     */
    private suspend fun probeDavEndpoint(
        davUrl: String,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url(davUrl)
                .method(PROPFIND_METHOD, "".toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "0")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseCode = response.code

            when (responseCode) {
                // 207 Multi-Status is the usual WebDAV success; also accept a few others that indicate the endpoint exists
                207, 200, 401, 403, 405, 429 -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
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
        isCalDAV: Boolean
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
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null && validatePropfindResponse(responseBody, isCalDAV)) {
                    endpointUrl
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
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
