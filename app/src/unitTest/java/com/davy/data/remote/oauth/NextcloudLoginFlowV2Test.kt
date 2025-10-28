package com.davy.data.remote.oauth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

/**
 * Unit tests for NextcloudLoginFlowV2.
 */
class NextcloudLoginFlowV2Test {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var loginFlow: NextcloudLoginFlowV2
    private lateinit var httpClient: OkHttpClient
    
    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
        loginFlow = NextcloudLoginFlowV2(httpClient)
    }
    
    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }
    
    @Test
    fun `initiateLogin returns LoginFlowInitiation with valid response`() = runTest {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        mockWebServer.enqueueInitiationSuccess(serverUrl, token = "test-token-123")

        // When
        val result = loginFlow.initiateLogin(serverUrl)

        // Then
        assertThat(result.loginUrl).isEqualTo("$serverUrl/index.php/login/v2/flow/test-token-123")
    assertThat(result.pollToken).isEqualTo("test-token-123")
    assertThat(result.pollEndpoint).isEqualTo("$serverUrl/index.php/login/v2/poll")
    }

    @Test
    fun `initiateLogin throws NextcloudLoginException on HTTP error`() = runTest {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
        )
        
        // When/Then
        val exception = assertThrows<NextcloudLoginException> {
            loginFlow.initiateLogin(serverUrl)
        }
        
        assertThat(exception.errorCode).isEqualTo(ErrorCode.INITIATION_FAILED)
    }

    @Test
    fun `json parsing sanity check`() {
        println("JSONObject class: ${JSONObject::class.java.name}, loader=${JSONObject::class.java.classLoader}")
        val response = """
            {
                "poll": {
                    "token": "abc",
                    "endpoint": "https://example.com/poll"
                },
                "login": "https://example.com/login"
            }
        """.trimIndent()

        println("raw response string: '${response.replace("\n", "\\n")}'")
        val json = JSONObject(response)
    val simple = JSONObject("""{"foo": 1}""")
    println("simple json: $simple, len=${simple.length()}, foo=${simple.opt("foo")}")
    println("json object: $json")
    println("json length: ${json.length()}")
    println("json names: ${json.names()}")
    println("is poll null: ${json.isNull("poll")}")
    println("poll raw from get: ${runCatching { json.get("poll") }.getOrElse { it }}")
        val pollValue = json.opt("poll")
        println("poll opt value: $pollValue, class=${pollValue?.javaClass}")
        assertThat(pollValue).isNotNull()
        assertThat(json.optString("login")).isEqualTo("https://example.com/login")
    }
    
    @Test
    fun `normalizeServerUrl adds https prefix for non-localhost`() {
        // When
        val normalized = loginFlow.normalizeForTest("cloud.example.com")

        // Then
        assertThat(normalized).isEqualTo("https://cloud.example.com")
    }

    @Test
    fun `pollForCredentials returns credentials on successful login`() = runTest {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        val initiation = LoginFlowInitiation(
            loginUrl = "$serverUrl/index.php/login/v2/flow/test",
            pollToken = "test-token",
            pollEndpoint = "$serverUrl/index.php/login/v2/poll"
        )
        
        mockWebServer.enqueueJsonResponse(
            code = 200,
            body = """
                {
                    "server": "https://cloud.example.com",
                    "loginName": "john.doe",
                    "appPassword": "app-password-xyz-123"
                }
            """.trimIndent()
        )
        
        // When
        val result = loginFlow.pollForCredentials(initiation)
        
        // Then
        assertThat(result.serverUrl).isEqualTo("https://cloud.example.com")
        assertThat(result.loginName).isEqualTo("john.doe")
        assertThat(result.appPassword).isEqualTo("app-password-xyz-123")
    }
    
    @Test
    fun `pollForCredentials waits for user on 404 response`() = runTest {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        val initiation = LoginFlowInitiation(
            loginUrl = "$serverUrl/index.php/login/v2/flow/test",
            pollToken = "test-token",
            pollEndpoint = "$serverUrl/index.php/login/v2/poll"
        )
        
        // Enqueue 404 (waiting), then success
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        
        mockWebServer.enqueueJsonResponse(
            code = 200,
            body = """
                {
                    "server": "https://cloud.example.com",
                    "loginName": "jane.doe",
                    "appPassword": "app-password-abc"
                }
            """.trimIndent()
        )
        
        // When
        val result = loginFlow.pollForCredentials(initiation)
        
        // Then
        assertThat(result.loginName).isEqualTo("jane.doe")
        assertThat(mockWebServer.requestCount).isEqualTo(3) // 2 polls + 1 success
    }
}

private fun NextcloudLoginFlowV2.normalizeForTest(serverUrl: String): String {
    val method = NextcloudLoginFlowV2::class.java.getDeclaredMethod("normalizeServerUrl", String::class.java)
    method.isAccessible = true
    return method.invoke(this, serverUrl) as String
}

private fun MockWebServer.enqueueInitiationSuccess(serverUrl: String, token: String) {
    val body = """
        {
            "poll": {
                "token": "$token",
                "endpoint": "$serverUrl/index.php/login/v2/poll"
            },
            "login": "$serverUrl/index.php/login/v2/flow/$token"
        }
    """.trimIndent()

    enqueueJsonResponse(code = 200, body = body)
}

private fun MockWebServer.enqueueJsonResponse(code: Int, body: String) {
    enqueue(
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    )
}
