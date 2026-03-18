package com.davy.data.remote

import okhttp3.Response
import timber.log.Timber

/**
 * Exception thrown when Cloudflare blocks WebDAV methods (PROPFIND, REPORT, etc.)
 * with HTTP 403 "error code: 1000".
 *
 * This happens when the server's DNS is proxied through Cloudflare (orange cloud),
 * which blocks non-standard HTTP methods. The fix is server-side:
 * - Switch to DNS-only mode (gray cloud) in Cloudflare, OR
 * - Use a Cloudflare Tunnel (cloudflared) which passes all HTTP methods, OR
 * - Add a Cloudflare WAF rule to allow WebDAV methods
 */
class CloudflareBlockingException(
    message: String = "Your server is behind Cloudflare, which blocks CalDAV/CardDAV traffic. " +
        "Please set your domain to DNS-only mode in Cloudflare, or use a Cloudflare Tunnel.",
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        /**
         * Check if an OkHttp response indicates Cloudflare is blocking WebDAV methods.
         *
         * Cloudflare returns HTTP 403 with:
         * - `server: cloudflare` header
         * - `cf-ray` header present
         * - Body containing "error code: 1000" (DNS resolution error for proxied domains)
         *
         * @return true if this is a Cloudflare blocking response
         */
        fun isCloudflareBlock(response: Response): Boolean {
            if (response.code != 403) return false

            val serverHeader = response.header("server")
            val cfRayHeader = response.header("cf-ray")

            Timber.d("CF check: code=%d server='%s' cf-ray='%s' allHeaders=%s",
                response.code, serverHeader, cfRayHeader, response.headers.names())

            return (serverHeader?.contains("cloudflare", ignoreCase = true) == true ||
                cfRayHeader != null)
        }

        /**
         * Check a CalDAVResponse for Cloudflare blocking indicators.
         */
        fun isCloudflareBlock(statusCode: Int, headers: Map<String, List<String>>): Boolean {
            if (statusCode != 403) return false

            // Case-insensitive header lookup — HTTP/2 uses lowercase, HTTP/1.1 may capitalize
            val serverHeader = headers.entries.firstOrNull { it.key.equals("server", ignoreCase = true) }?.value?.firstOrNull()
            val cfRayHeader = headers.entries.firstOrNull { it.key.equals("cf-ray", ignoreCase = true) }?.value?.firstOrNull()

            Timber.d("CF check (map): code=%d server='%s' cf-ray='%s' keys=%s",
                statusCode, serverHeader, cfRayHeader, headers.keys)

            return (serverHeader?.contains("cloudflare", ignoreCase = true) == true ||
                cfRayHeader != null)
        }

        /**
         * Throw CloudflareBlockingException if the response is a Cloudflare block.
         */
        fun throwIfCloudflare(response: Response, context: String = "") {
            if (isCloudflareBlock(response)) {
                val cfRay = response.header("cf-ray") ?: "unknown"
                Timber.e("Cloudflare blocking WebDAV request (cf-ray: %s) %s", cfRay, context)
                throw CloudflareBlockingException()
            }
        }
    }
}
