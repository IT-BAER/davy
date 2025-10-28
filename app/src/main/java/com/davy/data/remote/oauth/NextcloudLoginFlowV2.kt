package com.davy.data.remote.oauth

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nextcloud Login Flow v2 implementation.
 * 
 * Implements the Nextcloud Login Flow v2 API for passwordless authentication:
 * https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html
 * 
 * Flow:
 * 1. Client initiates login flow and receives poll endpoint + login URL
 * 2. User opens login URL in browser (Custom Tab) and authenticates
 * 3. Client polls endpoint until user grants access
 * 4. Client receives app password/token and server info
 * 
 * Advantages over username/password:
 * - No need to store user's actual password
 * - Works with 2FA enabled accounts
 * - User can revoke app access from Nextcloud settings
 * - More secure than traditional Basic Auth
 */
@Singleton
class NextcloudLoginFlowV2 @Inject constructor(
    private val httpClient: OkHttpClient
) {
    
    companion object {
        private const val LOGIN_FLOW_V2_PATH = "/index.php/login/v2"
        private const val POLL_INTERVAL_MS = 1000L // Poll every 1 second
        private const val MAX_POLL_ATTEMPTS = 300 // 5 minutes max
    }
    
    /**
     * Initiate login flow and get login URL.
     * 
     * @param serverUrl Nextcloud server base URL (e.g., https://cloud.example.com)
     * @return LoginFlowInitiation with login URL and poll token
     * @throws NextcloudLoginException if initiation fails
     */
    suspend fun initiateLogin(serverUrl: String): LoginFlowInitiation {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val initiationUrl = "$normalizedUrl$LOGIN_FLOW_V2_PATH"
        
        Timber.d("Initiating Nextcloud Login Flow v2 at: $initiationUrl")
        
        val request = Request.Builder()
            .url(initiationUrl)
            .post("".toRequestBody())
            .build()
        
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw NextcloudLoginException(
                        "Failed to initiate login flow: HTTP ${response.code}",
                        ErrorCode.INITIATION_FAILED
                    )
                }
                
                val responseBody = response.body?.string()
                    ?: throw NextcloudLoginException(
                        "Empty response from login flow initiation",
                        ErrorCode.INITIATION_FAILED
                    )
                
                Timber.d("Login flow initiation response: %s", responseBody)
                System.err.println("initiateLogin raw response: $responseBody")

                val json = JSONObject(responseBody)
                val keys = buildList {
                    val iterator = json.keys()
                    while (iterator.hasNext()) {
                        add(iterator.next())
                    }
                }
                System.err.println("poll value class: ${json.opt("poll")?.javaClass}")
                System.err.println("json keys: $keys")

                // Parse response - Nextcloud returns flat structure with poll and login objects
                val pollJson = when (val pollValue = json.opt("poll")) {
                    is JSONObject -> pollValue
                    is String -> runCatching { JSONObject(pollValue) }.getOrNull()
                    else -> null
                } ?: throw NextcloudLoginException(
                    "Missing poll object in login flow response",
                    ErrorCode.PARSE_ERROR
                )

                val loginUrl = json.optString("login").takeIf { it.isNotBlank() }
                    ?: throw NextcloudLoginException(
                        "Missing login URL in login flow response",
                        ErrorCode.PARSE_ERROR
                    )

                val pollToken = pollJson.optString("token").takeIf { it.isNotBlank() }
                    ?: throw NextcloudLoginException(
                        "Missing poll token in login flow response",
                        ErrorCode.PARSE_ERROR
                    )

                val pollEndpoint = pollJson.optString("endpoint").takeIf { it.isNotBlank() }
                    ?: throw NextcloudLoginException(
                        "Missing poll endpoint in login flow response",
                        ErrorCode.PARSE_ERROR
                    )

                return LoginFlowInitiation(
                    loginUrl = loginUrl,
                    pollToken = pollToken,
                    pollEndpoint = pollEndpoint
                )
            }
        } catch (e: NextcloudLoginException) {
            throw e
        } catch (e: IOException) {
            throw NextcloudLoginException(
                "Network error during login flow initiation: ${e.message}",
                ErrorCode.NETWORK_ERROR,
                e
            )
        } catch (e: Exception) {
            throw NextcloudLoginException(
                "Error parsing login flow response: ${e.message}",
                ErrorCode.PARSE_ERROR,
                e
            )
        }
    }
    
    /**
     * Poll for login completion.
     * 
     * This suspending function polls the endpoint until:
     * - User completes authentication (returns credentials)
     * - User cancels/times out (throws exception)
     * - Max poll attempts reached (throws exception)
     * 
     * @param initiation LoginFlowInitiation from initiateLogin()
     * @return LoginFlowCredentials with app password and user info
     * @throws NextcloudLoginException if polling fails or times out
     */
    suspend fun pollForCredentials(initiation: LoginFlowInitiation): LoginFlowCredentials {
        Timber.d("Starting to poll for login completion...")
        
        var attempts = 0
        while (attempts < MAX_POLL_ATTEMPTS) {
            attempts++
            
            try {
                val requestBodyJson = buildString {
                    append("{\"token\":")
                    append(JSONObject.quote(initiation.pollToken))
                    append("}")
                }

                val request = Request.Builder()
                    .url(initiation.pollEndpoint)
                    .post(
                        requestBodyJson.toRequestBody("application/json".toMediaType())
                    )
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            // Login completed successfully
                            val responseBody = response.body?.string()
                                ?: throw NextcloudLoginException(
                                    "Empty response from poll endpoint",
                                    ErrorCode.POLL_ERROR
                                )
                            
                            val json = JSONObject(responseBody)
                            System.err.println("pollForCredentials success response: $responseBody")
                            val serverUrl = json.optString("server").takeIf { it.isNotBlank() }
                                ?: throw NextcloudLoginException(
                                    "Missing server URL in poll response",
                                    ErrorCode.POLL_ERROR
                                )
                            val loginName = json.optString("loginName").takeIf { it.isNotBlank() }
                                ?: throw NextcloudLoginException(
                                    "Missing login name in poll response",
                                    ErrorCode.POLL_ERROR
                                )
                            val appPassword = json.optString("appPassword").takeIf { it.isNotBlank() }
                                ?: throw NextcloudLoginException(
                                    "Missing app password in poll response",
                                    ErrorCode.POLL_ERROR
                                )
                            Timber.d("Login flow completed successfully!")
                            
                            return LoginFlowCredentials(
                                serverUrl = serverUrl,
                                loginName = loginName,
                                appPassword = appPassword
                            )
                        }
                        
                        404 -> {
                            // Still waiting for user to complete login
                            Timber.v("Polling attempt $attempts/$MAX_POLL_ATTEMPTS - waiting for user...")
                            delay(POLL_INTERVAL_MS)
                        }
                        
                        else -> {
                            throw NextcloudLoginException(
                                "Unexpected response from poll endpoint: HTTP ${response.code}",
                                ErrorCode.POLL_ERROR
                            )
                        }
                    }
                }
            } catch (e: NextcloudLoginException) {
                throw e
            } catch (e: IOException) {
                Timber.w(e, "Network error during polling, will retry...")
                delay(POLL_INTERVAL_MS)
            } catch (e: Exception) {
                throw NextcloudLoginException(
                    "Error during polling: ${e.message}",
                    ErrorCode.POLL_ERROR,
                    e
                )
            }
        }
        
        throw NextcloudLoginException(
            "Login flow timed out after $MAX_POLL_ATTEMPTS attempts",
            ErrorCode.TIMEOUT
        )
    }
    
    /**
     * Normalize server URL to ensure it has https:// and no trailing slash.
     */
    private fun normalizeServerUrl(serverUrl: String): String {
        var normalized = serverUrl.trim()
        
        // Remove trailing slash
        normalized = normalized.trimEnd('/')
        
        // Add https:// if no protocol specified
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            // Use http:// for localhost, https:// for everything else
            normalized = if (normalized.contains("localhost") || normalized.startsWith("127.0.0.1")) {
                "http://$normalized"
            } else {
                "https://$normalized"
            }
        }
        
        return normalized
    }
}

/**
 * Result of login flow initiation.
 */
data class LoginFlowInitiation(
    val loginUrl: String,
    val pollToken: String,
    val pollEndpoint: String
)

/**
 * Credentials received from successful login flow.
 */
data class LoginFlowCredentials(
    val serverUrl: String,
    val loginName: String,
    val appPassword: String
)

/**
 * Exception thrown when Nextcloud login flow fails.
 */
class NextcloudLoginException(
    message: String,
    val errorCode: ErrorCode,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Error codes for Nextcloud login flow.
 */
enum class ErrorCode {
    /** Failed to initiate login flow */
    INITIATION_FAILED,
    
    /** Network error during communication */
    NETWORK_ERROR,
    
    /** Error parsing server response */
    PARSE_ERROR,
    
    /** Error during polling */
    POLL_ERROR,
    
    /** User cancelled login */
    USER_CANCELLED,
    
    /** Login flow timed out */
    TIMEOUT
}
