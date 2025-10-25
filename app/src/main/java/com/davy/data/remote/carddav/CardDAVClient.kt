package com.davy.data.remote.carddav

import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CardDAV HTTP client for address book operations.
 * Implements CardDAV protocol methods following DAVx5 patterns:
 * - DELETE: Remove address books (with 404/410 as success)
 */
@Singleton
class CardDAVClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    
    /**
     * DELETE request to remove an address book from server.
     * Follows DAVx5 pattern: treats 404/410 as successful deletion (resource already gone).
     * 
     * @param addressBookUrl URL of the address book to delete
     * @param username Account username for authentication
     * @param password Account password for authentication
     * @return CardDAVResponse with isSuccessful=true for 2xx/204/404/410 responses
     */
    fun deleteAddressBook(
        addressBookUrl: String,
        username: String,
        password: String
    ): CardDAVResponse {
        val request = Request.Builder()
            .url(addressBookUrl)
            .delete()
            .addHeader("Authorization", okhttp3.Credentials.basic(username, password))
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            CardDAVResponse(
                statusCode = response.code,
                body = response.body?.string(),
                headers = response.headers.names().associateWith { name -> response.headers.values(name) },
                // DAVx5 pattern: 404 Not Found or 410 Gone means resource is already deleted → success
                isSuccessful = response.isSuccessful || response.code == 204 || response.code == 404 || response.code == 410
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to DELETE address book: $addressBookUrl")
            CardDAVResponse(
                statusCode = -1,
                body = null,
                headers = emptyMap(),
                isSuccessful = false,
                error = e.message
            )
        }
    }
}

/**
 * Response from CardDAV HTTP operations.
 * 
 * @param statusCode HTTP status code (-1 for network errors)
 * @param body Response body text (null if empty or error)
 * @param headers Response headers map
 * @param isSuccessful True if operation succeeded (includes 404/410 for DELETE)
 * @param error Error message if request failed
 */
data class CardDAVResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, List<String>>,
    val isSuccessful: Boolean,
    val error: String? = null
)
