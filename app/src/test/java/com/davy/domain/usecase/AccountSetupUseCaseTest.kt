package com.davy.domain.usecase

import com.davy.data.repository.FakeAccountRepository
import com.davy.data.local.CredentialStore
import com.davy.data.remote.NetworkClient
import com.davy.domain.model.AuthType
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for AccountSetupUseCase.
 * 
 * Tests the account setup flow with mocked dependencies.
 */
class AccountSetupUseCaseTest {
    
    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var credentialStore: CredentialStore
    private lateinit var networkClient: NetworkClient
    private lateinit var validateServerUrlUseCase: ValidateServerUrlUseCase
    private lateinit var validateCredentialsUseCase: ValidateCredentialsUseCase
    private lateinit var discoverResourcesUseCase: DiscoverResourcesUseCase
    private lateinit var useCase: AccountSetupUseCase
    
    @BeforeEach
    fun setup() {
        accountRepository = FakeAccountRepository()
        credentialStore = mockk(relaxed = true)
        networkClient = mockk()
        validateServerUrlUseCase = ValidateServerUrlUseCase()
        validateCredentialsUseCase = mockk()
        discoverResourcesUseCase = mockk()
        
        useCase = AccountSetupUseCase(
            accountRepository = accountRepository,
            credentialStore = credentialStore,
            networkClient = networkClient,
            validateServerUrlUseCase = validateServerUrlUseCase,
            validateCredentialsUseCase = validateCredentialsUseCase,
            discoverResourcesUseCase = discoverResourcesUseCase
        )
    }
    
    @Test
    fun `invoke with valid credentials returns Success`() = runTest {
        // Given
        val serverUrl = "https://nextcloud.example.com"
        val username = "testuser"
        val password = "testpass"
        
        coEvery {
            validateCredentialsUseCase(any(), any(), any(), any())
        } returns ValidateCredentialsUseCase.ValidationResult(isValid = true)
        
        coEvery {
            discoverResourcesUseCase(any(), any(), any(), any())
        } returns DiscoverResourcesUseCase.DiscoveryResult(
            hasCalendar = true,
            hasContacts = true,
            hasTasks = true
        )
        
        // When
        val result = useCase(
            serverUrl = serverUrl,
            username = username,
            password = password,
            authType = AuthType.BASIC
        )
        
        // Then
        assertThat(result).isInstanceOf(AccountSetupUseCase.Result.Success::class.java)
        val successResult = result as AccountSetupUseCase.Result.Success
        assertThat(successResult.accountId).isGreaterThan(0)
        
        // Verify credentials were stored
        verify { credentialStore.storePassword(any(), password) }
    }
    
    @Test
    fun `invoke with invalid server URL returns Error`() = runTest {
        // Given
        val serverUrl = "invalid url"
        val username = "testuser"
        val password = "testpass"
        
        // When
        val result = useCase(
            serverUrl = serverUrl,
            username = username,
            password = password
        )
        
        // Then
        assertThat(result).isInstanceOf(AccountSetupUseCase.Result.Error::class.java)
        val errorResult = result as AccountSetupUseCase.Result.Error
        assertThat(errorResult.code).isEqualTo(AccountSetupUseCase.ErrorCode.INVALID_SERVER_URL)
    }
    
    @Test
    fun `invoke with duplicate account returns Error`() = runTest {
        // Given
        val serverUrl = "https://nextcloud.example.com"
        val username = "testuser"
        val password = "testpass"
        
        // Setup existing account
        accountRepository.seedTestData()
        
        coEvery {
            validateCredentialsUseCase(any(), any(), any(), any())
        } returns ValidateCredentialsUseCase.ValidationResult(isValid = true)
        
        // When
        val result = useCase(
            serverUrl = "https://nextcloud.example.com",
            username = "testuser1", // Already exists in seeded data
            password = password
        )
        
        // Then
        assertThat(result).isInstanceOf(AccountSetupUseCase.Result.Error::class.java)
        val errorResult = result as AccountSetupUseCase.Result.Error
        assertThat(errorResult.code).isEqualTo(AccountSetupUseCase.ErrorCode.DUPLICATE_ACCOUNT)
    }
    
    @Test
    fun `invoke with invalid credentials returns Error`() = runTest {
        // Given
        val serverUrl = "https://nextcloud.example.com"
        val username = "testuser"
        val password = "wrongpass"
        
        coEvery {
            validateCredentialsUseCase(any(), any(), any(), any())
        } returns ValidateCredentialsUseCase.ValidationResult(
            isValid = false,
            errorMessage = "Invalid credentials"
        )
        
        // When
        val result = useCase(
            serverUrl = serverUrl,
            username = username,
            password = password
        )
        
        // Then
        assertThat(result).isInstanceOf(AccountSetupUseCase.Result.Error::class.java)
        val errorResult = result as AccountSetupUseCase.Result.Error
        assertThat(errorResult.code).isEqualTo(AccountSetupUseCase.ErrorCode.AUTHENTICATION_FAILED)
    }
    
    @Test
    fun `invoke stores bearer token correctly`() = runTest {
        // Given
        val serverUrl = "https://nextcloud.example.com"
        val username = "testuser"
        val token = "bearer-token-123"
        
        coEvery {
            validateCredentialsUseCase(any(), any(), any(), any())
        } returns ValidateCredentialsUseCase.ValidationResult(isValid = true)
        
        coEvery {
            discoverResourcesUseCase(any(), any(), any(), any())
        } returns DiscoverResourcesUseCase.DiscoveryResult(
            hasCalendar = true,
            hasContacts = true,
            hasTasks = true
        )
        
        // When
        val result = useCase(
            serverUrl = serverUrl,
            username = username,
            password = token,
            authType = AuthType.BEARER
        )
        
        // Then
        assertThat(result).isInstanceOf(AccountSetupUseCase.Result.Success::class.java)
        verify { credentialStore.storeToken(any(), token) }
    }
    
    @Test
    fun `invoke creates account with discovered resources`() = runTest {
        // Given
        val serverUrl = "https://nextcloud.example.com"
        val username = "testuser"
        val password = "testpass"
        
        coEvery {
            validateCredentialsUseCase(any(), any(), any(), any())
        } returns ValidateCredentialsUseCase.ValidationResult(isValid = true)
        
        coEvery {
            discoverResourcesUseCase(any(), any(), any(), any())
        } returns DiscoverResourcesUseCase.DiscoveryResult(
            hasCalendar = true,
            hasContacts = false,
            hasTasks = true
        )
        
        // When
        val result = useCase(
            serverUrl = serverUrl,
            username = username,
            password = password
        )
        
        // Then
        assertThat(result).isInstanceOf(AccountSetupUseCase.Result.Success::class.java)
        val successResult = result as AccountSetupUseCase.Result.Success
        val account = accountRepository.getById(successResult.accountId)
        
        assertThat(account).isNotNull()
        assertThat(account?.calendarEnabled).isTrue()
        assertThat(account?.contactsEnabled).isFalse()
        assertThat(account?.tasksEnabled).isTrue()
    }
}
