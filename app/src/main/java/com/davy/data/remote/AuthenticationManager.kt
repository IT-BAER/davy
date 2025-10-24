package com.davy.data.remote

import com.davy.data.remote.caldav.PrincipalDiscovery as CalDAVPrincipalDiscovery
import com.davy.data.remote.carddav.PrincipalDiscovery as CardDAVPrincipalDiscovery
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication manager for DAVy.
 * 
 * Orchestrates the authentication flow for CalDAV and CardDAV services:
 * 1. Service discovery (find CalDAV and CardDAV endpoints)
 * 2. Principal discovery (find user principal and home sets)
 * 3. Credential validation
 * 
 * Currently supports Basic Authentication (RFC 7617).
 */
@Singleton
class AuthenticationManager @Inject constructor(
    private val serviceDiscovery: ServiceDiscovery,
    private val caldavPrincipalDiscovery: CalDAVPrincipalDiscovery,
    private val carddavPrincipalDiscovery: CardDAVPrincipalDiscovery
) {

    /**
     * Authenticate user and discover service endpoints.
     * 
     * @param serverUrl Server base URL (e.g., https://nextcloud.example.com)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return AuthenticationResult containing discovered services and principals
     * @throws AuthenticationException if authentication fails
     */
    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String
    ): AuthenticationResult {
        // Validate inputs
        validateServerUrl(serverUrl)
        validateCredentials(username, password)
        
        // Step 1: Discover service endpoints
        val serviceEndpoints = try {
            serviceDiscovery.discoverServices(serverUrl, username, password)
        } catch (e: Exception) {
            throw AuthenticationException(
                "Service discovery failed: ${e.message}",
                cause = e
            )
        }
        
        // Validate that at least one service was found
        if (!serviceEndpoints.hasCalDAV() && !serviceEndpoints.hasCardDAV()) {
            throw AuthenticationException(
                "No CalDAV or CardDAV services found at $serverUrl"
            )
        }
        
        // Step 2: Discover CalDAV principal if CalDAV service exists
        val caldavPrincipal = serviceEndpoints.calDavUrl?.let { calDavUrl ->
            try {
                caldavPrincipalDiscovery.discoverPrincipal(calDavUrl, username, password)
            } catch (e: Exception) {
                throw AuthenticationException(
                    "CalDAV principal discovery failed: ${e.message}",
                    cause = e
                )
            }
        }
        
        // Step 3: Discover CardDAV principal if CardDAV service exists
        val carddavPrincipal = serviceEndpoints.cardDavUrl?.let { cardDavUrl ->
            try {
                carddavPrincipalDiscovery.discoverPrincipal(cardDavUrl, username, password)
            } catch (e: Exception) {
                throw AuthenticationException(
                    "CardDAV principal discovery failed: ${e.message}",
                    cause = e
                )
            }
        }
        
        return AuthenticationResult(
            serverUrl = serverUrl,
            username = username,
            calDavEndpoint = serviceEndpoints.calDavUrl,
            cardDavEndpoint = serviceEndpoints.cardDavUrl,
            calDavPrincipal = caldavPrincipal,
            cardDavPrincipal = carddavPrincipal
        )
    }

    /**
     * Test credentials against server without full discovery.
     * 
     * Performs a minimal check to validate credentials work.
     * 
     * @param serverUrl Server base URL
     * @param username Username for authentication
     * @param password Password for authentication
     * @return true if credentials are valid
     */
    suspend fun testCredentials(
        serverUrl: String,
        username: String,
        password: String
    ): Boolean {
        return try {
            // Attempt service discovery - if it succeeds, credentials are valid
            val endpoints = serviceDiscovery.discoverServices(serverUrl, username, password)
            endpoints.hasCalDAV() || endpoints.hasCardDAV()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate server URL format.
     */
    private fun validateServerUrl(url: String) {
        if (url.isBlank()) {
            throw AuthenticationException("Server URL cannot be empty")
        }
        
        // Basic URL validation
        val normalizedUrl = url.lowercase()
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            // URL will be normalized by ServiceDiscovery, but warn about localhost non-https
            if (!normalizedUrl.contains("localhost") && !normalizedUrl.contains("127.0.0.1")) {
                // Non-localhost without https will get https:// added
            }
        }
    }

    /**
     * Validate credentials.
     */
    private fun validateCredentials(username: String, password: String) {
        if (username.isBlank()) {
            throw AuthenticationException("Username cannot be empty")
        }
        
        if (password.isBlank()) {
            throw AuthenticationException("Password cannot be empty")
        }
    }
}

/**
 * Result of successful authentication containing discovered service information.
 */
data class AuthenticationResult(
    val serverUrl: String,
    val username: String,
    val calDavEndpoint: String?,
    val cardDavEndpoint: String?,
    val calDavPrincipal: com.davy.data.remote.caldav.PrincipalInfo?,
    val cardDavPrincipal: com.davy.data.remote.carddav.PrincipalInfo?
) {
    /**
     * Check if CalDAV service is available.
     */
    fun hasCalDAV(): Boolean = calDavEndpoint != null && calDavPrincipal != null
    
    /**
     * Check if CardDAV service is available.
     */
    fun hasCardDAV(): Boolean = cardDavEndpoint != null && cardDavPrincipal != null
}

/**
 * Exception thrown when authentication fails.
 */
class AuthenticationException(message: String, cause: Throwable? = null) : Exception(message, cause)
