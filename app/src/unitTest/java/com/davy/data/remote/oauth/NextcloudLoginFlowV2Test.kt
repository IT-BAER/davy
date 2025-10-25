package com.davy.data.remote.oauth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        httpClient = OkHttpClient.Builder().build()
        loginFlow = NextcloudLoginFlowV2(httpClient)
    }
    
    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }
    
    @Test
    fun `initiateLogin returns LoginFlowInitiation with valid response`() = runBlocking {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        val responseJson = """
            {
                "poll": {
                    "token": "test-token-123",
                    "endpoint": "$serverUrl/login/v2/poll"
                },
                "login": {
                    "url": "$serverUrl/login/v2/flow/test-token-123"
                }
            }
        """.trimIndent()
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json")
        )
        
        // When
        val result = loginFlow.initiateLogin(serverUrl)
        
        // Then
        assertThat(result.loginUrl).contains("/login/v2/flow/")
        assertThat(result.pollToken).isEqualTo("test-token-123")
        assertThat(result.pollEndpoint).contains("/login/v2/poll")
    }
    
    @Test
    fun `initiateLogin throws NextcloudLoginException on HTTP error`() = runBlocking {
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
    fun `normalizeServerUrl adds https prefix for non-localhost`() = runBlocking {
        // Given
        val serverUrl = "cloud.example.com"
        val responseJson = """
            {
                "poll": {
                    "token": "test-token",
                    "endpoint": "https://cloud.example.com/login/v2/poll"
                },
                "login": {
                    "url": "https://cloud.example.com/login/v2/flow"
                }
            }
        """.trimIndent()
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json")
        )
        
        // When
        val result = loginFlow.initiateLogin(serverUrl)
        
        // Then
        assertThat(result.loginUrl).startsWith("https://")
    }
    
    @Test
    fun `pollForCredentials returns credentials on successful login`() = runBlocking {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        val initiation = LoginFlowInitiation(
            loginUrl = "$serverUrl/login/v2/flow/test",
            pollToken = "test-token",
            pollEndpoint = "$serverUrl/login/v2/poll"
        )
        
        val credentialsJson = """
            {
                "server": "https://cloud.example.com",
                "loginName": "john.doe",
                "appPassword": "app-password-xyz-123"
            }
        """.trimIndent()
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(credentialsJson)
                .setHeader("Content-Type", "application/json")
        )
        
        // When
        val result = loginFlow.pollForCredentials(initiation)
        
        // Then
        assertThat(result.serverUrl).isEqualTo("https://cloud.example.com")
        assertThat(result.loginName).isEqualTo("john.doe")
        assertThat(result.appPassword).isEqualTo("app-password-xyz-123")
    }
    
    @Test
    fun `pollForCredentials waits for user on 404 response`() = runBlocking {
        // Given
        val serverUrl = mockWebServer.url("/").toString().trimEnd('/')
        val initiation = LoginFlowInitiation(
            loginUrl = "$serverUrl/login/v2/flow/test",
            pollToken = "test-token",
            pollEndpoint = "$serverUrl/login/v2/poll"
        )
        
        // Enqueue 404 (waiting), then success
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        
        val credentialsJson = """
            {
                "server": "https://cloud.example.com",
                "loginName": "jane.doe",
                "appPassword": "app-password-abc"
            }
        """.trimIndent()
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(credentialsJson)
                .setHeader("Content-Type", "application/json")
        )
        
        // When
        val result = loginFlow.pollForCredentials(initiation)
        
        // Then
        assertThat(result.loginName).isEqualTo("jane.doe")
        assertThat(mockWebServer.requestCount).isEqualTo(3) // 2 polls + 1 success
    }
}
