package com.davy.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
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
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse()
            .setResponseCode(302)
            .addHeader("Location", "/dav")
          "/dav" -> MockResponse()
            .setResponseCode(207)
            .setBody(multistatusWithHomeSets())
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/dav")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/dav")
  }

  @Test
  fun `nextcloud owncloud path accepted when PROPFIND returns 401 then validated`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav" -> {
            // First call checks if it's Nextcloud/ownCloud (returns 401)
            // Subsequent calls are validation PROPFIND (return 207 with proper DAV response)
            if (request.getHeader("Depth") == "0" && request.body.readUtf8().contains("resourcetype")) {
              MockResponse()
                .setResponseCode(207)
                .setBody(multistatusWithHomeSets())
            } else {
              MockResponse().setResponseCode(401)
            }
          }
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/remote.php/dav")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/remote.php/dav")
  }

  @Test
  fun `common DAV paths probed when nextcloud owncloud not present`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav", "/remote.php/webdav" -> MockResponse().setResponseCode(404)
          "/dav" -> MockResponse()
            .setResponseCode(207)
            .setBody(multistatusWithPrincipalOnly())
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/dav")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/dav")
  }

  @Test
  fun `baikal style dav php path is detected`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav", "/remote.php/webdav" -> MockResponse().setResponseCode(404)
          "/dav.php" -> MockResponse()
            .setResponseCode(207)
            .setBody(multistatusWithPrincipalOnly())
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/dav.php")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/dav.php")
  }

  @Test
  fun `caldav php or carddav php common paths are detected`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav", "/remote.php/webdav" -> MockResponse().setResponseCode(404)
          "/caldav.php", "/carddav.php" -> MockResponse()
            .setResponseCode(207)
            .setBody(multistatusWithHomeSets())
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/caldav.php")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/caldav.php")
  }

  @Test
  fun `carddav php path is detected when caldav php missing`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav", "/remote.php/webdav" -> MockResponse().setResponseCode(404)
          "/caldav.php" -> MockResponse().setResponseCode(404)
          "/carddav.php" -> MockResponse()
            .setResponseCode(207)
            .setBody(multistatusWithHomeSets())
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    val result = discovery.discoverServices(baseUrl, "user", "pass")

    assertThat(result.hasAnyService()).isTrue()
    assertThat(result.calDavUrl).isEqualTo("$baseUrl/carddav.php")
    assertThat(result.cardDavUrl).isEqualTo("$baseUrl/carddav.php")
  }

  @Test
  fun `non-DAV server returning 403 or 405 is rejected`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav" -> MockResponse().setResponseCode(403) // Non-DAV server returning 403
          "/dav", "/caldav.php", "/carddav.php" -> MockResponse().setResponseCode(405) // Non-DAV returning 405
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    
    try {
      discovery.discoverServices(baseUrl, "user", "pass")
      assert(false) { "Expected ServiceDiscoveryException to be thrown" }
    } catch (e: ServiceDiscoveryException) {
      // Expected - non-DAV servers should be rejected
      assertThat(e.message).contains("No CalDAV or CardDAV services found")
    }
  }

  @Test
  fun `server returning non-DAV XML in 207 response is rejected`() = runBlocking {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/.well-known/caldav", "/.well-known/carddav" -> MockResponse().setResponseCode(404)
          "/remote.php/dav" -> MockResponse()
            .setResponseCode(207)
            .setBody("""<?xml version="1.0"?><notDAV><someOtherElement/></notDAV>""")
          else -> MockResponse().setResponseCode(404)
        }
      }
    }

    val baseUrl = server.url("/").toString().removeSuffix("/")
    
    try {
      discovery.discoverServices(baseUrl, "user", "pass")
      assert(false) { "Expected ServiceDiscoveryException to be thrown" }
    } catch (e: ServiceDiscoveryException) {
      // Expected - non-DAV XML should be rejected
      assertThat(e.message).contains("No CalDAV or CardDAV services found")
    }
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
