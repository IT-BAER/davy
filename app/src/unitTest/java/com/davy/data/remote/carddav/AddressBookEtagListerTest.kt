package com.davy.data.remote.carddav

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Guards the request DAVy sends when it only needs to know which contacts exist.
 *
 * Asking for address-data here downloads every vCard in the collection on every sync,
 * which is what made the client the heaviest mobile-radio consumer on the test device.
 */
class AddressBookEtagListerTest {

    private lateinit var server: MockWebServer
    private lateinit var lister: AddressBookEtagLister

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        lister = AddressBookEtagLister(OkHttpClient.Builder().build())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `request asks for etags and never for vcard bodies`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatusWithTwoContacts()))

        lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertThat(request.method).isEqualTo("REPORT")
        assertThat(request.getHeader("Depth")).isEqualTo("1")
        assertThat(request.getHeader("Authorization")).isNotNull()
        assertThat(body).contains("<d:getetag/>")
        assertThat(body).doesNotContain("address-data")
    }

    @Test
    fun `parses hrefs and etags from a multistatus response`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatusWithTwoContacts()))

        val entries = lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

        assertThat(entries).isNotNull()
        assertThat(entries!!).hasSize(2)
        assertThat(entries.map { it.url }).containsExactly(
            "/remote.php/dav/addressbooks/users/rog/contacts/alice.vcf",
            "/remote.php/dav/addressbooks/users/rog/contacts/bob.vcf"
        )
        assertThat(entries.map { it.etag }).containsExactly("etag-alice", "etag-bob")
        assertThat(entries.map { it.vcardData }).containsExactly(null, null)
    }

    @Test
    fun `an empty collection is reported as an empty list, not as a failure`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(207).setBody(emptyMultistatus()))
        server.enqueue(MockResponse().setResponseCode(207).setBody(emptyMultistatus()))

        val entries = lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

        assertThat(entries).isNotNull()
        assertThat(entries!!).isEmpty()
        // Emptiness is confirmed with the full-payload query before callers act on it.
        assertThat(server.requestCount).isEqualTo(2)
        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).contains("address-data")
    }

    @Test
    fun `a successful response the parser cannot read is not reported as an empty collection`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(207).setBody("<html>proxy notice</html>"))
            server.enqueue(MockResponse().setResponseCode(207).setBody(multistatusWithTwoContacts()))

            val entries = lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

            assertThat(entries).isNotNull()
            assertThat(entries!!).hasSize(2)
        }

    @Test
    fun `a failed confirmation returns null rather than an empty list`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(207).setBody(emptyMultistatus()))
        server.enqueue(MockResponse().setResponseCode(500))

        val entries = lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

        assertThat(entries).isNull()
    }

    @Test
    fun `an auth failure returns null so callers skip instead of seeing zero contacts`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val entries = lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

        assertThat(entries).isNull()
    }

    @Test
    fun `a server error returns null rather than an empty list`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val entries = lister.list(server.url("/addressbooks/rog/contacts/").toString(), "rog", "secret")

        assertThat(entries).isNull()
    }

    private fun multistatusWithTwoContacts() = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
          <d:response>
            <d:href>/remote.php/dav/addressbooks/users/rog/contacts/alice.vcf</d:href>
            <d:propstat>
              <d:prop><d:getetag>&quot;etag-alice&quot;</d:getetag></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/addressbooks/users/rog/contacts/bob.vcf</d:href>
            <d:propstat>
              <d:prop><d:getetag>&quot;etag-bob&quot;</d:getetag></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    private fun emptyMultistatus() = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
        </d:multistatus>
    """.trimIndent()
}
