package com.davy.domain.validator

import java.net.IDN
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validator for server URLs.
 * 
 * Validates and normalizes server URLs for CalDAV/CardDAV connections.
 * Ensures URLs are properly formatted and accessible.
 */
@Singleton
class ServerUrlValidator @Inject constructor() {

    companion object {
        private const val HTTPS_SCHEME = "https"
        private const val HTTP_SCHEME = "http"
        private const val DEFAULT_HTTPS_PORT = 443
        private const val DEFAULT_HTTP_PORT = 80
        
        // Localhost patterns
        private val LOCALHOST_PATTERNS = listOf(
            "localhost",
            "127.0.0.1",
            "::1",
            "[::1]"
        )
        
        // Reserved/private IP ranges (for additional validation if needed)
        private val PRIVATE_IP_PATTERNS = listOf(
            Regex("""^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""),
            Regex("""^172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}$"""),
            Regex("""^192\.168\.\d{1,3}\.\d{1,3}$""")
        )
    }

    /**
     * Validate and normalize server URL.
     * 
     * @param url Raw URL input from user
     * @return ValidationResult containing normalized URL or error message
     */
    fun validate(url: String): ValidationResult {
        // Check for empty/blank input
        if (url.isBlank()) {
            return ValidationResult.Error("Server URL cannot be empty")
        }
        
        // Normalize whitespace
        val trimmedUrl = url.trim()
        
        // Add scheme if missing
        val urlWithScheme = when {
            trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://") -> trimmedUrl
            isLocalhost(trimmedUrl) -> "http://$trimmedUrl" // Allow HTTP for localhost workflows
            else -> "https://$trimmedUrl" // Default to HTTPS for security
        }
        
        // Parse URL
        val parsedUrl = try {
            URL(urlWithScheme)
        } catch (e: Exception) {
            return ValidationResult.Error("Invalid URL format: ${e.message}")
        }
        
        // Validate scheme
        if (parsedUrl.protocol != HTTPS_SCHEME && parsedUrl.protocol != HTTP_SCHEME) {
            return ValidationResult.Error("Only HTTP and HTTPS schemes are supported")
        }
        
        // Validate host
        val hostValidation = validateHost(parsedUrl.host)
        if (hostValidation is ValidationResult.Error) {
            return hostValidation
        }
        
        // Block HTTP for non-localhost targets
        if (parsedUrl.protocol == HTTP_SCHEME && !isLocalhost(parsedUrl.host)) {
            return ValidationResult.Error("HTTP is not supported. Please use an HTTPS server URL.")
        }
        
        // Success
        return ValidationResult.Success(normalizeUrl(parsedUrl))
    }

    /**
     * Validate host portion of URL.
     */
    private fun validateHost(host: String): ValidationResult {
        if (host.isBlank()) {
            return ValidationResult.Error("Server host cannot be empty")
        }
        
        // Check for localhost
        if (isLocalhost(host)) {
            return ValidationResult.Success(host)
        }
        
        // Validate domain name (convert IDN to ASCII for validation)
        return try {
            val asciiHost = IDN.toASCII(host)
            
            // Basic domain validation
            if (!isValidDomainOrIP(asciiHost)) {
                ValidationResult.Error("Invalid domain name or IP address")
            } else {
                ValidationResult.Success(host)
            }
        } catch (e: Exception) {
            ValidationResult.Error("Invalid domain name: ${e.message}")
        }
    }

    /**
     * Check if host is localhost.
     */
    private fun isLocalhost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        return LOCALHOST_PATTERNS.any { normalizedHost.contains(it) }
    }

    /**
     * Check if host is a private IP.
     */
    private fun isPrivateIP(host: String): Boolean {
        return PRIVATE_IP_PATTERNS.any { it.matches(host) }
    }

    /**
     * Validate domain name or IP address format.
     */
    private fun isValidDomainOrIP(host: String): Boolean {
        // Check for IP address (IPv4)
        val ipv4Pattern = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipv4Pattern.matches(host)) {
            // Validate IP octets
            return host.split(".").all { octet ->
                octet.toIntOrNull()?.let { it in 0..255 } ?: false
            }
        }
        
        // Check for IPv6 (simplified - already validated by URL parser)
        if (host.startsWith("[") && host.endsWith("]")) {
            return true
        }
        
        // Check for domain name
        val domainPattern = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""")
        return domainPattern.matches(host)
    }

    /**
     * Normalize URL to standard format.
     * 
     * - Removes default ports (80 for HTTP, 443 for HTTPS)
     * - Removes trailing slash
     * - Preserves path if present
     */
    private fun normalizeUrl(url: URL): String {
        val scheme = url.protocol
        val host = url.host
        val port = url.port
        val path = url.path.removeSuffix("/")
        
        // Build normalized URL
        val normalizedUrl = buildString {
            append(scheme)
            append("://")
            append(host)
            
            // Add port only if non-default
            if (port != -1 && !isDefaultPort(scheme, port)) {
                append(":")
                append(port)
            }
            
            // Add path if present
            if (path.isNotEmpty()) {
                append(path)
            }
        }
        
        return normalizedUrl
    }

    /**
     * Check if port is default for scheme.
     */
    private fun isDefaultPort(scheme: String, port: Int): Boolean {
        return when (scheme) {
            HTTPS_SCHEME -> port == DEFAULT_HTTPS_PORT
            HTTP_SCHEME -> port == DEFAULT_HTTP_PORT
            else -> false
        }
    }

    /**
     * Extract base URL (scheme + host + port) from full URL.
     */
    fun extractBaseUrl(url: String): String? {
        return try {
            val parsedUrl = URL(url)
            val scheme = parsedUrl.protocol
            val host = parsedUrl.host
            val port = parsedUrl.port
            
            buildString {
                append(scheme)
                append("://")
                append(host)
                if (port != -1 && !isDefaultPort(scheme, port)) {
                    append(":")
                    append(port)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Result of URL validation.
 */
sealed class ValidationResult {
    /**
     * URL is valid and normalized.
     */
    data class Success(val normalizedUrl: String) : ValidationResult()
    
    /**
     * URL is valid but has a warning (e.g., using HTTP).
     */
    data class Warning(val normalizedUrl: String, val message: String) : ValidationResult()
    
    /**
     * URL is invalid.
     */
    data class Error(val message: String) : ValidationResult()
    
    /**
     * Check if validation succeeded (success or warning).
     */
    fun isValid(): Boolean = this is Success || this is Warning
    
    /**
     * Get normalized URL if validation succeeded.
     */
    fun getUrlOrNull(): String? = when (this) {
        is Success -> normalizedUrl
        is Warning -> normalizedUrl
        is Error -> null
    }
}
