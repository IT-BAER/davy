package com.davy.data.remote.carddav

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Lists what a CardDAV collection currently holds, as href/ETag pairs without vCard bodies.
 *
 * Null means the listing is unknown and the caller must skip. An empty list means the
 * collection is genuinely empty, which is a valid instruction to remove local contacts,
 * so the two must never be conflated.
 */
class AddressBookEtagLister @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val query = AddressBookQuery()

    suspend fun list(
        addressBookUrl: String,
        username: String,
        password: String
    ): List<FetchedContact>? = withContext(Dispatchers.IO) {
        val etagOnly = report(addressBookUrl, username, password, query.createETagOnlyRequest())
            ?: return@withContext null
        if (etagOnly.isNotEmpty()) {
            return@withContext etagOnly
        }
        // An empty parse and a genuinely empty collection are indistinguishable here, and
        // the callers delete every local contact on an empty listing. Confirm with the
        // full-payload request the sync used before, which is known to parse.
        Timber.w("ETag listing for $addressBookUrl parsed empty, confirming with a full query")
        report(addressBookUrl, username, password, query.createQueryAllRequest(addressBookUrl))
    }

    private fun report(
        addressBookUrl: String,
        username: String,
        password: String,
        requestXml: String
    ): List<FetchedContact>? {
        return try {
            val request = Request.Builder()
                .url(addressBookUrl)
                .method("REPORT", query.createRequestBody(requestXml))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Authorization", Credentials.basic(username, password))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("ETag listing failed for $addressBookUrl: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                query.parseQueryResponse(body)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception listing ETags for $addressBookUrl")
            null
        }
    }
}
