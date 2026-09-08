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
        try {
            val request = Request.Builder()
                .url(addressBookUrl)
                .method("REPORT", query.createRequestBody(query.createETagOnlyRequest()))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Authorization", Credentials.basic(username, password))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("ETag listing failed for $addressBookUrl: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                query.parseQueryResponse(body)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception listing ETags for $addressBookUrl")
            null
        }
    }
}
