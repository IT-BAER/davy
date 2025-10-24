package com.davy.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServiceDiscoveryTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var discovery: ServiceDiscovery

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        discovery = ServiceDiscovery(client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `well-known redirect to DAV root is handled and validated`() = runBlocking {
        // 1) .well-known/caldav -> 302 to /dav
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/dav")
        )
        // 2) .well-known/carddav -> 302 to /dav (enqueue again for second call)
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/dav")
        )
        // 3) PROPFIND validation for CalDAV
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(multistatusWithHomeSets())
        )
        // 4) PROPFIND validation for CardDAV
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(multistatusWithHomeSets())
        )

        val baseUrl = server.url("/").toString().removeSuffix("/")
        val result = discovery.discoverServices(baseUrl, "user", "pass")

        assertThat(result.hasAnyService()).isTrue()
        assertThat(result.calDavUrl).isEqualTo("$baseUrl/dav")
        assertThat(result.cardDavUrl).isEqualTo("$baseUrl/dav")
    }

    @Test
    fun `nextcloud owncloud path accepted when PROPFIND returns 401`() = runBlocking {
        // 1) .well-known/caldav -> 404
        server.enqueue(MockResponse().setResponseCode(404))
        // 2) .well-known/carddav -> 404
        server.enqueue(MockResponse().setResponseCode(404))
        // 3) PROPFIND for /remote.php/dav -> 401 (accepted)
        server.enqueue(MockResponse().setResponseCode(401))

        val baseUrl = server.url("/").toString().removeSuffix("/")
        val result = discovery.discoverServices(baseUrl, "user", "pass")

        assertThat(result.hasAnyService()).isTrue()
        assertThat(result.calDavUrl).isEqualTo("$baseUrl/remote.php/dav")
        assertThat(result.cardDavUrl).isEqualTo("$baseUrl/remote.php/dav")
    }

    @Test
    fun `common DAV paths probed when nextcloud owncloud not present`() = runBlocking {
        // 1) .well-known/caldav -> 404
        server.enqueue(MockResponse().setResponseCode(404))
        // 2) .well-known/carddav -> 404
        server.enqueue(MockResponse().setResponseCode(404))
        // 3) PROPFIND for /remote.php/dav -> 404 (not present)
        server.enqueue(MockResponse().setResponseCode(404))
        // 4) PROPFIND for /remote.php/webdav -> 404 (not present)
        server.enqueue(MockResponse().setResponseCode(404))
        // 5) Probe PROPFIND for /dav -> 401 (accepted)
        server.enqueue(MockResponse().setResponseCode(401))
        // 6) PROPFIND validation for CalDAV -> 207 with only principal (accepted by fallback)
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(multistatusWithPrincipalOnly())
        )
        // 7) PROPFIND validation for CardDAV -> 207 with only principal (accepted by fallback)
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(multistatusWithPrincipalOnly())
        )

        val baseUrl = server.url("/").toString().removeSuffix("/")
        val result = discovery.discoverServices(baseUrl, "user", "pass")

        assertThat(result.hasAnyService()).isTrue()
        assertThat(result.calDavUrl).isEqualTo("$baseUrl/dav")
        assertThat(result.cardDavUrl).isEqualTo("$baseUrl/dav")
    }

  @Test
  fun `baikal style dav php path is detected`() = runBlocking {
    // 1) .well-known/caldav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 2) .well-known/carddav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 3) PROPFIND for /remote.php/dav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 4) PROPFIND for /remote.php/webdav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 5) Probe PROPFIND for /dav.php -> 401 (accepted)
    server.enqueue(MockResponse().setResponseCode(401))
    // 6) PROPFIND validation for CalDAV -> 207 principal-only accepted
    server.enqueue(
      MockResponse()
        .setResponseCode(207)
        .setBody(multistatusWithPrincipalOnly())
    )
    // 7) PROPFIND validation for CardDAV -> 207 principal-only accepted
    server.enqueue(
      MockResponse()
        .setResponseCode(207)
        .setBody(multistatusWithPrincipalOnly())
    )

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/dav.php")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/dav.php")
  }

  @Test
  fun `caldav php or carddav php common paths are detected`() = runBlocking {
    // 1) .well-known/caldav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 2) .well-known/carddav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 3) PROPFIND for /remote.php/dav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 4) PROPFIND for /remote.php/webdav -> 404
    server.enqueue(MockResponse().setResponseCode(404))
    // 5) Probe PROPFIND for /caldav.php -> 401 (accepted)
    server.enqueue(MockResponse().setResponseCode(401))
    // 6) PROPFIND validation for CalDAV -> 207 with home-sets
    server.enqueue(
      MockResponse()
        .setResponseCode(207)
        .setBody(multistatusWithHomeSets())
    )
    // 7) PROPFIND validation for CardDAV -> 207 with home-sets
    server.enqueue(
      MockResponse()
        .setResponseCode(207)
        .setBody(multistatusWithHomeSets())
    )

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/caldav.php")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/caldav.php")
  }

    private fun multistatusWithHomeSets(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:card="urn:ietf:params:xml:ns:carddav">
          <d:response>
            <d:propstat>
              <d:prop>
                <c:calendar-home-set><d:href>/calendars/user/</d:href></c:calendar-home-set>
                <card:addressbook-home-set><d:href>/addressbooks/user/</d:href></card:addressbook-home-set>
                <d:current-user-principal><d:href>/principals/users/user/</d:href></d:current-user-principal>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    private fun multistatusWithPrincipalOnly(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:card="urn:ietf:params:xml:ns:carddav">
          <d:response>
            <d:propstat>
              <d:prop>
                <d:current-user-principal><d:href>/principals/users/user/</d:href></d:current-user-principal>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()
}
