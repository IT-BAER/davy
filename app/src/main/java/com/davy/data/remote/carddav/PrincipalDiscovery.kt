package com.davy.data.remote.carddav

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
 * CardDAV principal discovery implementation.
 * 
 * Implements RFC 6352 (CardDAV) and RFC 3744 (WebDAV ACL) for discovering
 * the current user's principal URL and addressbook home set.
 * 
 * Discovery steps:
 * 1. Find current-user-principal
 * 2. Get addressbook-home-set from principal
 * 3. Enumerate available address books
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
        private const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    }

    /**
     * Discover CardDAV principal information for the authenticated user.
     * 
     * @param baseUrl CardDAV service base URL
     * @param username Username for authentication
     * @param password Password for authentication
     * @return PrincipalInfo containing principal URL and addressbook home set
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
                "• Server does not support CardDAV\n" +
                "• Network connectivity issues"
            )
        
        // Step 2: Get addressbook-home-set from principal
        val addressbookHomeSet = findAddressbookHomeSet(principalUrl, username, password)
            ?: throw PrincipalDiscoveryException("Could not find addressbook-home-set")
        
        // Step 3: Get display name if available
        val displayName = findDisplayName(principalUrl, username, password)
        
        return PrincipalInfo(
            principalUrl = principalUrl,
            addressbookHomeSet = addressbookHomeSet,
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
        
        // Query for current-user-principal AND addressbook-home-set to minimize round trips
        // This follows RFC 6764 Section 6 and reference implementation's approach
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <d:prop>
                    <d:current-user-principal />
                    <card:addressbook-home-set />
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
     * Find addressbook-home-set URL from principal.
     * 
     * @param principalUrl Principal URL
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Addressbook home set URL or null if not found
     */
    private suspend fun findAddressbookHomeSet(
        principalUrl: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        Timber.tag("PrincipalDiscovery").d("Finding addressbook-home-set at principal: %s", principalUrl)
        
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <d:prop>
                    <card:addressbook-home-set />
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
            
            Timber.tag("PrincipalDiscovery").d("Sending PROPFIND for addressbook-home-set to: %s", principalUrl)
            val response = httpClient.newCall(request).execute()
            
            Timber.tag("PrincipalDiscovery").d("addressbook-home-set response code: %s", response.code)
            
            if (response.code == 207 || response.code == 200) {
                val responseBody = response.body?.string()
                Timber.tag("PrincipalDiscovery").d("addressbook-home-set response body:\n%s", responseBody)
                responseBody?.let { 
                    val result = extractAddressbookHomeSet(it, principalUrl)
                    Timber.tag("PrincipalDiscovery").d("Extracted addressbook-home-set: %s", result)
                    result
                }
            } else {
                Timber.tag("PrincipalDiscovery").w("Failed to get addressbook-home-set: %s - %s", response.code, response.body?.string())
                null
            }
        } catch (e: Exception) {
            Timber.tag("PrincipalDiscovery").e(e, "Error finding addressbook-home-set")
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
     * Extract addressbook-home-set URL from PROPFIND response.
     */
    private fun extractAddressbookHomeSet(responseXml: String, baseUrl: String): String? {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Look for addressbook-home-set/href
            val hrefNode = xpath.evaluate(
                "//*[local-name()='addressbook-home-set']/*[local-name()='href']",
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
     * Discover addressbook collections from addressbook-home-set.
     * 
     * Uses PROPFIND with Depth: 1 to list all addressbook collections under the home-set.
     * This follows the reference implementation pattern from HomeSetRefresher.kt.
     * 
     * @param addressbookHomeSetUrl Addressbook home set URL (from PrincipalInfo)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return List of discovered addressbook collections
     * @throws PrincipalDiscoveryException if discovery fails
     */
    suspend fun discoverAddressbooks(
        addressbookHomeSetUrl: String,
        username: String,
        password: String
    ): List<AddressbookCollectionInfo> = withContext(Dispatchers.IO) {
        Timber.tag("CardDAVPrincipalDiscovery").d("Discovering addressbooks at: %s", addressbookHomeSetUrl)
        
        // PROPFIND request body - request all relevant addressbook properties
        // Including DAV:owner to identify shared addressbooks
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <d:prop>
                    <d:resourcetype />
                    <d:displayname />
                    <d:owner />
                    <card:addressbook-description />
                    <d:current-user-privilege-set />
                </d:prop>
            </d:propfind>
        """.trimIndent()
        
        return@withContext try {
            val request = Request.Builder()
                .url(addressbookHomeSetUrl)
                .method(PROPFIND_METHOD, propfindBody.toRequestBody(CONTENT_TYPE_XML.toMediaType()))
                .header("Authorization", createBasicAuthHeader(username, password))
                .header(DEPTH_HEADER, "1") // CRITICAL: Depth 1 lists children (the actual addressbooks)
                .build()
            
            Timber.tag("CardDAVPrincipalDiscovery").d("Sending PROPFIND Depth: 1 for addressbooks")
            val response = httpClient.newCall(request).execute()
            
            Timber.tag("CardDAVPrincipalDiscovery").d("Addressbook discovery response code: %s", response.code)
            
            if (response.code == 207 || response.code == 200) {
                val responseBody = response.body?.string()
                Timber.tag("CardDAVPrincipalDiscovery").d("Addressbook discovery response body:\n%s", responseBody)
                
                if (responseBody != null) {
                    val addressbooks = parseAddressbookCollections(responseBody, addressbookHomeSetUrl)
                    Timber.tag("CardDAVPrincipalDiscovery").d("Discovered %s addressbooks: %s", addressbooks.size, addressbooks.map { it.displayName })
                    addressbooks
                } else {
                    emptyList()
                }
            } else {
                Timber.tag("CardDAVPrincipalDiscovery").w("Failed to discover addressbooks: %s - %s", response.code, response.body?.string())
                throw PrincipalDiscoveryException("Failed to discover addressbooks: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Timber.tag("CardDAVPrincipalDiscovery").e(e, "Error discovering addressbooks")
            e.printStackTrace()
            throw PrincipalDiscoveryException("Addressbook discovery failed: ${e.message}", e)
        }
    }

    /**
     * Parse addressbook collections from PROPFIND Depth: 1 response.
     * 
     * Extracts all responses where resourcetype contains <addressbook/>.
     */
    private fun parseAddressbookCollections(responseXml: String, baseUrl: String): List<AddressbookCollectionInfo> {
        return try {
            val document = parseXmlDocument(responseXml)
            val xpath = XPathFactory.newInstance().newXPath()
            
            // Find all response elements
            val responses = xpath.evaluate(
                "//*[local-name()='response']",
                document,
                XPathConstants.NODESET
            ) as org.w3c.dom.NodeList
            
            val addressbooks = mutableListOf<AddressbookCollectionInfo>()
            
            for (i in 0 until responses.length) {
                val responseNode = responses.item(i)
                
                // Get href (addressbook URL)
                val href = xpath.evaluate(
                    "*[local-name()='href']",
                    responseNode,
                    XPathConstants.STRING
                ) as String
                
                // Skip if href is empty or is the home-set itself
                if (href.isBlank() || href.removeSuffix("/") == baseUrl.removeSuffix("/")) {
                    continue
                }
                
                // Check if this is an addressbook (resourcetype contains <addressbook/>)
                val isAddressbook = responseNode.let { node ->
                    val addressbookNode = xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='resourcetype']/*[local-name()='addressbook']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    addressbookNode != null
                }
                
                if (!isAddressbook) {
                    continue
                }
                
                // Extract addressbook properties
                val displayName = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='displayname']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                val description = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='addressbook-description']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                // Extract owner (for identifying shared addressbooks)
                val owner = xpath.evaluate(
                    "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='owner']/*[local-name()='href']",
                    responseNode,
                    XPathConstants.STRING
                ) as? String
                
                // Parse write privilege from current-user-privilege-set
                // Look for <privilege><write-content/></privilege> 
                // See reference implementation: Collection.privWriteContent determination
                val privWriteContent = responseNode.let { node ->
                    val writeContentNode = xpath.evaluate(
                        "*[local-name()='propstat']/*[local-name()='prop']/*[local-name()='current-user-privilege-set']/*[local-name()='privilege']/*[local-name()='write-content']",
                        node,
                        XPathConstants.NODE
                    ) as? org.w3c.dom.Node
                    
                    Timber.tag("CardDAVPrincipalDiscovery").d("Address book '%s': privWriteContent = %s", displayName, writeContentNode != null)
                    writeContentNode != null
                }
                
                val addressbookUrl = resolveUrl(baseUrl, href)
                val resolvedOwner = owner?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveUrl(baseUrl, it) }
                
                // Log addressbook discovery for debugging shared addressbooks
                Timber.tag("CardDAVPrincipalDiscovery").d("Discovered addressbook: %s", addressbookUrl)
                Timber.tag("CardDAVPrincipalDiscovery").d("  - Display name: %s", displayName)
                Timber.tag("CardDAVPrincipalDiscovery").d("  - Owner: %s", resolvedOwner)
                Timber.tag("CardDAVPrincipalDiscovery").d("  - Write privilege: %s", privWriteContent)
                
                addressbooks.add(
                    AddressbookCollectionInfo(
                        url = addressbookUrl,
                        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } 
                            ?: addressbookUrl.substringAfterLast('/').removeSuffix("/"),
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        owner = resolvedOwner,
                        privWriteContent = privWriteContent
                    )
                )
            }
            
            addressbooks
        } catch (e: Exception) {
            Timber.tag("CardDAVPrincipalDiscovery").e(e, "Error parsing addressbook collections")
            e.printStackTrace()
            emptyList()
        }
    }
}

/**
 * Principal information discovered from CardDAV server.
 */
data class PrincipalInfo(
    val principalUrl: String,
    val addressbookHomeSet: String,
    val displayName: String? = null
)

/**
 * Addressbook collection information discovered from CardDAV server.
 */
data class AddressbookCollectionInfo(
    val url: String,
    val displayName: String,
    val description: String? = null,
    val owner: String? = null,            // Owner principal URL (for identifying shared addressbooks)
    val privWriteContent: Boolean = true  // Write permission from DAV:current-user-privilege-set
)

/**
 * Exception thrown when principal discovery fails.
 */
class PrincipalDiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)

