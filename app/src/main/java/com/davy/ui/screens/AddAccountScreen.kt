package com.davy.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.R
import android.content.Context
import com.davy.data.remote.AuthenticationManager
import com.davy.data.remote.AuthenticationResult
import com.davy.data.remote.caldav.PrincipalDiscovery as CalDAVPrincipalDiscovery
import com.davy.data.remote.carddav.PrincipalDiscovery as CardDAVPrincipalDiscovery
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.CalendarRepository
import com.davy.data.repository.WebCalSubscriptionRepository
import com.davy.data.local.CredentialStore
import com.davy.domain.model.Account
import com.davy.domain.model.AddressBook
import com.davy.domain.model.AuthType
import com.davy.domain.model.Calendar
import com.davy.domain.model.WebCalSubscription
import com.davy.domain.validator.ServerUrlValidator
import com.davy.domain.validator.ValidationResult
import com.davy.sync.account.AndroidAccountManager
import com.davy.sync.calendar.CalendarContractSync
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val HTTPS_PREFIX = "https://"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onNavigateBack: () -> Unit,
    onAccountCreated: () -> Unit,
    viewModel: AddAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.add_account)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account type info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.caldav_carddav_account_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(id = R.string.caldav_carddav_account_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Account name
            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = viewModel::onAccountNameChanged,
                label = { Text(stringResource(id = R.string.account_name)) },
                placeholder = { Text(stringResource(id = R.string.account_name_placeholder)) },
                singleLine = true,
                isError = uiState.accountNameError != null,
                supportingText = uiState.accountNameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Server URL with Protocol Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Protocol field (HTTPS only)
                OutlinedTextField(
                    value = uiState.protocol,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(id = R.string.protocol_label)) },
                    modifier = Modifier
                        .width(130.dp)
                )
                
                // Server URL field (without protocol)
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = viewModel::onServerUrlChanged,
                    label = { Text(stringResource(id = R.string.server_url)) },
                    placeholder = { Text(stringResource(id = R.string.server_url_placeholder)) },
                    singleLine = true,
                    isError = uiState.serverUrlError != null,
                    supportingText = uiState.serverUrlError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Username
            UsernameTextFieldWithAutofill(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                isError = uiState.usernameError != null,
                supportingText = uiState.usernameError?.let { { Text(it) } },
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Password
            var passwordVisible by remember { mutableStateOf(false) }
            PasswordTextFieldWithAutofill(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                passwordVisible = passwordVisible,
                onPasswordVisibilityChanged = { passwordVisible = it },
                isError = uiState.passwordError != null,
                supportingText = uiState.passwordError?.let { { Text(it) } },
                onDone = {
                    focusManager.clearFocus()
                    viewModel.onAddAccountClicked()
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Error message
            if (uiState.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Buttons row - Test and Add side by side with increased height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Test Credentials button
                OutlinedButton(
                    onClick = viewModel::onTestCredentialsClicked,
                    enabled = !uiState.isLoading && !uiState.isTestingCredentials,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    when {
                        uiState.isTestingCredentials -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.testing))
                        }
                        uiState.credentialTestResult?.startsWith("✓") == true -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(
                                        animateFloatAsState(
                                            targetValue = 1.2f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ), label = "test_success_scale_row"
                                        ).value
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.valid))
                        }
                        uiState.credentialTestResult?.startsWith("✗") == true -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.failed))
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.test))
                        }
                    }
                }
                
                // Add account button with + icon
                Button(
                    onClick = viewModel::onAddAccountClicked,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.connecting))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.add_account))
                    }
                }
            }
            
            // Help text
            Text(
                text = stringResource(id = R.string.auto_discover_services_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            // Demo mode hint
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.demo_explore_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(id = R.string.demo_credentials_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.bullet_server, "demo.local"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(id = R.string.bullet_username, "demo"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(id = R.string.bullet_password, "demo"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
    
    // Success navigation
    LaunchedEffect(uiState.accountCreated) {
        if (uiState.accountCreated) {
            onAccountCreated()
        }
    }
}

/**
 * Username text field with autofill support.
 * 
 * Enables password managers to recognize and autofill username.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UsernameTextFieldWithAutofill(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: @Composable (() -> Unit)?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val autofillNode = AutofillNode(
        autofillTypes = listOf(AutofillType.Username),
        onFill = { onValueChange(it) }
    )
    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNode
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(id = R.string.username)) },
        placeholder = { Text(stringResource(id = R.string.username_placeholder)) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() }
        ),
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                autofillNode.boundingBox = coordinates.boundsInWindow()
            }
            .onFocusChanged { focusState ->
                autofill?.run {
                    if (focusState.isFocused) {
                        requestAutofillForNode(autofillNode)
                    } else {
                        cancelAutofillForNode(autofillNode)
                    }
                }
            }
    )
}

/**
 * Password text field with autofill support.
 * 
 * Enables password managers to recognize and autofill password.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasswordTextFieldWithAutofill(
    value: String,
    onValueChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityChanged: (Boolean) -> Unit,
    isError: Boolean,
    supportingText: @Composable (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val autofillNode = AutofillNode(
        autofillTypes = listOf(AutofillType.Password),
        onFill = { onValueChange(it) }
    )
    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNode
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(id = R.string.password)) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        trailingIcon = {
            IconButton(onClick = { onPasswordVisibilityChanged(!passwordVisible) }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password)
                )
            }
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                autofillNode.boundingBox = coordinates.boundsInWindow()
            }
            .onFocusChanged { focusState ->
                autofill?.run {
                    if (focusState.isFocused) {
                        requestAutofillForNode(autofillNode)
                    } else {
                        cancelAutofillForNode(autofillNode)
                    }
                }
            }
    )
}

data class AddAccountUiState(
    val accountName: String = "",
    val protocol: String = HTTPS_PREFIX,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val accountNameError: String? = null,
    val serverUrlError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val accountCreated: Boolean = false,
    val isTestingCredentials: Boolean = false,
    val credentialTestResult: String? = null
)

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val addressBookRepository: AddressBookRepository,
    private val webCalSubscriptionRepository: WebCalSubscriptionRepository,
    private val authenticationManager: AuthenticationManager,
    private val caldavPrincipalDiscovery: CalDAVPrincipalDiscovery,
    private val carddavPrincipalDiscovery: CardDAVPrincipalDiscovery,
    private val serverUrlValidator: ServerUrlValidator,
    private val credentialStore: CredentialStore,
    private val androidAccountManager: AndroidAccountManager,
    private val calendarContractSync: CalendarContractSync,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()
    
    fun onAccountNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(
            accountName = name,
            accountNameError = null
        )
    }
    
    fun onServerUrlChanged(url: String) {
        // Strip any protocol prefix if user accidentally included it
        val cleanUrl = url
            .removePrefix(HTTPS_PREFIX)
            .removePrefix("http://")
        
        _uiState.value = _uiState.value.copy(
            serverUrl = cleanUrl,
            serverUrlError = null
        )
    }
    
    fun onUsernameChanged(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            usernameError = null
        )
    }
    
    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null
        )
    }
    
    fun onTestCredentialsClicked() {
        val state = _uiState.value
        
        // Validate required fields
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = appContext.getString(R.string.test_credentials_fill_all_fields)
            )
            return
        }
        
    val fullUrl = HTTPS_PREFIX + state.serverUrl
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingCredentials = true,
                credentialTestResult = null,
                errorMessage = null
            )
            
            try {
                val isValid = authenticationManager.testCredentials(
                    fullUrl,
                    state.username,
                    state.password
                )
                
                _uiState.value = _uiState.value.copy(
                    isTestingCredentials = false,
                    credentialTestResult = if (isValid) "✓ Credentials valid" else "✗ Authentication failed"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingCredentials = false,
                    credentialTestResult = "✗ ${e.message ?: appContext.getString(R.string.connection_failed)}"
                )
            }
        }
    }
    
    fun onAddAccountClicked() {
        Timber.d("=== onAddAccountClicked called ===")
        val state = _uiState.value
        
        // Validate inputs
        var hasError = false
        
        if (state.accountName.isBlank()) {
            _uiState.value = state.copy(accountNameError = appContext.getString(R.string.account_name_required))
            hasError = true
        }
        
        if (state.serverUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(serverUrlError = appContext.getString(R.string.server_url_required))
            hasError = true
        }
        
        if (state.username.isBlank()) {
            _uiState.value = _uiState.value.copy(usernameError = appContext.getString(R.string.username_required))
            hasError = true
        }
        
        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = appContext.getString(R.string.password_required))
            hasError = true
        }
        
        if (hasError) return
        
        // Check for demo account credentials
        val isDemoAccount = state.username.equals("demo", ignoreCase = true) && 
                           state.password.equals("demo", ignoreCase = true) &&
                           state.serverUrl.equals("demo.local", ignoreCase = true)
        
        if (isDemoAccount) {
            Timber.d("=== Demo account detected - creating local-only demo account ===")
            createDemoAccount(state)
            return
        }
        
        // Combine protocol + serverUrl for validation
    val fullUrl = HTTPS_PREFIX + state.serverUrl
        
        // Validate server URL
        val urlValidation = serverUrlValidator.validate(fullUrl)
        if (!urlValidation.isValid()) {
            _uiState.value = _uiState.value.copy(
                serverUrlError = when (urlValidation) {
                    is ValidationResult.Error -> urlValidation.message
                    else -> appContext.getString(R.string.invalid_server_url)
                }
            )
            return
        }
        
        val normalizedUrl = urlValidation.getUrlOrNull()!!
        
        // Start authentication and discovery
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            try {
                // Step 1: Check for duplicate account
                val existingAccount = accountRepository.findByServerAndUsername(
                    serverUrl = normalizedUrl,
                    username = state.username
                )
                
                if (existingAccount != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = appContext.getString(R.string.account_exists_message, existingAccount.accountName)
                    )
                    return@launch
                }
                
                // Step 2: Authenticate and discover services
                val authResult = authenticationManager.authenticate(
                    serverUrl = normalizedUrl,
                    username = state.username,
                    password = state.password
                )
                
                // Step 3: Create account
                val account = Account(
                    id = 0, // Will be auto-generated
                    accountName = state.accountName,
                    serverUrl = normalizedUrl,
                    username = state.username,
                    displayName = state.accountName,
                    email = if (state.username.contains("@")) state.username else null,
                    calendarEnabled = authResult.hasCalDAV(),
                    contactsEnabled = authResult.hasCardDAV(),
                    tasksEnabled = authResult.hasCalDAV(), // Tasks are stored in CalDAV
                    createdAt = System.currentTimeMillis(),
                    lastAuthenticatedAt = System.currentTimeMillis(),
                    authType = AuthType.BASIC,
                    certificateFingerprint = null
                )
                
                val accountId = accountRepository.insert(account)
                
                // Store password in secure credential store
                credentialStore.storePassword(accountId, state.password)
                Timber.d("Stored password for account ID: %s", accountId)
                
                // Create Android account for sync framework
                val androidAccountCreated = androidAccountManager.createOrUpdateAccount(
                    account.accountName,
                    state.password
                )
                
                if (!androidAccountCreated) {
                    Timber.e("Failed to create Android account for: %s", account.accountName)
                } else {
                    Timber.d("Created Android account for: %s", account.accountName)
                }
                
                // Step 3: Discover calendars if CalDAV is available
                if (authResult.hasCalDAV() && authResult.calDavPrincipal != null) {
                    Timber.d("=== Starting calendar discovery ===")
                    Timber.d("Calendar home-set URL: %s", authResult.calDavPrincipal.calendarHomeSet)
                    try {
                        val calendars = caldavPrincipalDiscovery.discoverCalendars(
                            calendarHomeSetUrl = authResult.calDavPrincipal.calendarHomeSet,
                            username = state.username,
                            password = state.password
                        )
                        
                        Timber.d("=== Discovered %s calendars ===", calendars.size)
                        calendars.forEach { cal ->
                            Timber.d("Calendar: %s at %s", cal.displayName, cal.url)
                        }
                        
                        // Save discovered calendars and webcal subscriptions
                        calendars.forEach { calendarInfo ->
                            // Check if this is a webcal subscription (has source URL)
                            if (calendarInfo.source != null) {
                                // Create WebCal subscription
                                val subscription = WebCalSubscription(
                                    id = 0,
                                    accountId = accountId,
                                    subscriptionUrl = calendarInfo.source, // External webcal URL
                                    displayName = calendarInfo.displayName,
                                    description = calendarInfo.description,
                                    color = parseColor(calendarInfo.color),
                                    syncEnabled = true
                                )
                                webCalSubscriptionRepository.insert(subscription)
                                Timber.d("Saved webcal subscription: %s from %s", calendarInfo.displayName, calendarInfo.source)
                            } else {
                                // Create regular calendar
                                val calendar = Calendar(
                                    id = 0,
                                    accountId = accountId,
                                    calendarUrl = calendarInfo.url,
                                    displayName = calendarInfo.displayName,
                                    description = calendarInfo.description,
                                    color = parseColor(calendarInfo.color),
                                    syncEnabled = true,
                                    visible = true,
                                    owner = calendarInfo.owner,  // Save owner for shared calendar detection
                                    privWriteContent = calendarInfo.privWriteContent,
                                    privUnbind = calendarInfo.privUnbind,
                                    supportsVTODO = calendarInfo.supportsVTODO,
                                    supportsVJOURNAL = calendarInfo.supportsVJOURNAL,
                                    source = calendarInfo.source
                                )
                                calendarRepository.insert(calendar)
                                Timber.d("Saved calendar: %s", calendarInfo.displayName)
                            }
                        }
                        
                        Timber.d("Discovered and saved %s calendars", calendars.size)
                        
                        // IMPORTANT: Immediately sync calendars to Calendar Provider
                        // This ensures calendars appear in the system calendar app right away
                        if (androidAccountCreated) {
                            Timber.d("Syncing calendars to Calendar Provider...")
                            try {
                                calendarContractSync.syncToCalendarProvider(accountId)
                                Timber.d("Successfully synced calendars to Calendar Provider")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to sync calendars to Calendar Provider")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to discover calendars")
                        // Don't fail the whole flow if calendar discovery fails
                    }
                }
                
                // Step 4: Discover addressbooks if CardDAV is available
                if (authResult.hasCardDAV() && authResult.cardDavPrincipal != null) {
                    Timber.d("=== Starting address book discovery ===")
                    Timber.d("Address book home-set URL: %s", authResult.cardDavPrincipal.addressbookHomeSet)
                    try {
                        val addressbooks = carddavPrincipalDiscovery.discoverAddressbooks(
                            addressbookHomeSetUrl = authResult.cardDavPrincipal.addressbookHomeSet,
                            username = state.username,
                            password = state.password
                        )
                        
                        Timber.d("=== Discovered %s address book(s) ===", addressbooks.size)
                        addressbooks.forEach { ab ->
                            Timber.d("Address Book: %s at %s", ab.displayName, ab.url)
                        }
                        
                        // Save discovered address books and create Android accounts for each
                        addressbooks.forEach { addressbookInfo ->
                            val addressBook = AddressBook(
                                id = 0,
                                accountId = accountId,
                                url = addressbookInfo.url,
                                displayName = addressbookInfo.displayName,
                                description = addressbookInfo.description,
                                ctag = null, // Will be fetched during first sync
                                syncEnabled = true,
                                visible = true,
                                owner = addressbookInfo.owner,  // Save owner for shared addressbook detection
                                privWriteContent = addressbookInfo.privWriteContent  // Server permission
                            )
                            val insertedId = addressBookRepository.insert(addressBook)
                            
                            // Create separate Android account for this address book (reference implementation architecture)
                            val addressBookAccount = androidAccountManager.createAddressBookAccount(
                                mainAccountName = account.accountName,
                                addressBookName = addressbookInfo.displayName,
                                addressBookId = insertedId,
                                addressBookUrl = addressbookInfo.url
                            )
                            
                            if (addressBookAccount != null) {
                                Timber.d("Created address book account: %s", addressBookAccount.name)
                            } else {
                                Timber.e("Failed to create address book account for: %s", addressbookInfo.displayName)
                            }
                        }
                        
                        Timber.d("Discovered and saved %s address book(s)", addressbooks.size)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to discover addressbooks")
                        // Don't fail the whole flow if addressbook discovery fails
                    }
                }
                
                // Step 5: Now that calendars and addressbooks are discovered and saved,
                // do NOT trigger automatic sync - let sync happen based on:
                // 1. Sync interval settings (periodic background sync)
                // 2. User manual sync trigger
                if (androidAccountCreated) {
                    Timber.d("Account created: %s", account.accountName)
                    Timber.d("Periodic sync will run based on sync interval settings")
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    accountCreated = true
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = appContext.getString(R.string.failed_to_add_account, e.message ?: appContext.getString(R.string.unknown))
                )
            }
        }
    }
    
    /**
     * Parse calendar color from hex string to integer.
     * 
     * Returns a default blue color if parsing fails.
     */
    private fun parseColor(colorHex: String?): Int {
        if (colorHex.isNullOrBlank()) {
            return 0xFF2196F3.toInt() // Default Material Blue
        }
        
        return try {
            // Remove # if present
            val hex = colorHex.removePrefix("#")
            
            // Parse color - handle both RGB and ARGB formats
            when (hex.length) {
                6 -> ("FF" + hex).toLong(16).toInt() // RGB -> ARGB
                8 -> hex.toLong(16).toInt() // Already ARGB
                else -> 0xFF2196F3.toInt() // Invalid format, use default
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse color: %s", colorHex)
            0xFF2196F3.toInt() // Default blue
        }
    }
    
    /**
     * Create a local-only demo account with pre-populated sample data.
     * Bypasses server authentication and creates account with demo calendars/contacts.
     */
    private fun createDemoAccount(state: AddAccountUiState) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            try {
                Timber.d("=== Creating demo account ===")
                
                // Create demo account with fixed demo URL
                val account = Account(
                    id = 0,
                    accountName = state.accountName,
                    serverUrl = "https://demo.local",
                    username = "demo",
                    displayName = appContext.getString(R.string.demo_account_display_name),
                    email = "demo@davy.app",
                    calendarEnabled = true,
                    contactsEnabled = true,
                    tasksEnabled = true,
                    createdAt = System.currentTimeMillis(),
                    lastAuthenticatedAt = System.currentTimeMillis(),
                    authType = AuthType.BASIC,
                    certificateFingerprint = null,
                    notes = appContext.getString(R.string.demo_account_notes)
                )
                
                val accountId = accountRepository.insert(account)
                Timber.d("Created demo account with ID: %d", accountId)
                
                // Store demo password
                credentialStore.storePassword(accountId, "demo")
                
                // Create Android account (required for sync framework)
                val androidAccountCreated = androidAccountManager.createOrUpdateAccount(
                    account.accountName,
                    "demo"
                )
                
                if (!androidAccountCreated) {
                    Timber.e("Failed to create Android account for demo")
                } else {
                    Timber.d("Created Android account for demo")
                }
                
                val accountBaseUrl = "https://demo.local/accounts/$accountId"
                val calendarBaseUrl = "$accountBaseUrl/calendars"
                val contactsBaseUrl = "$accountBaseUrl/contacts"
                val webCalBaseUrl = "$accountBaseUrl/webcal"
                val now = System.currentTimeMillis()

                val personalCalendarSyncedAt = now - TimeUnit.MINUTES.toMillis(5)
                val workCalendarSyncedAt = now - TimeUnit.MINUTES.toMillis(45)
                val familyCalendarSyncedAt = now - TimeUnit.MINUTES.toMillis(120)

                // Create sample calendars with per-account URLs to avoid conflicts across demo accounts
                val sampleCalendars = listOf(
                    com.davy.domain.model.Calendar(
                        id = 0,
                        accountId = accountId,
                        calendarUrl = "$calendarBaseUrl/personal",
                        displayName = appContext.getString(R.string.personal_calendar),
                        description = appContext.getString(R.string.personal_events_description),
                        color = 0xFF2196F3.toInt(), // Blue
                        supportsVTODO = true,
                        supportsVJOURNAL = false,
                        syncEnabled = true,
                        visible = true,
                        createdAt = personalCalendarSyncedAt,
                        updatedAt = personalCalendarSyncedAt,
                        lastSyncedAt = personalCalendarSyncedAt
                    ),
                    com.davy.domain.model.Calendar(
                        id = 0,
                        accountId = accountId,
                        calendarUrl = "$calendarBaseUrl/work",
                        displayName = appContext.getString(R.string.work_calendar),
                        description = appContext.getString(R.string.work_meetings_tasks),
                        color = 0xFFF44336.toInt(), // Red
                        supportsVTODO = true,
                        supportsVJOURNAL = false,
                        syncEnabled = true,
                        visible = true,
                        createdAt = workCalendarSyncedAt,
                        updatedAt = workCalendarSyncedAt,
                        lastSyncedAt = workCalendarSyncedAt
                    ),
                    com.davy.domain.model.Calendar(
                        id = 0,
                        accountId = accountId,
                        calendarUrl = "$calendarBaseUrl/family",
                        displayName = appContext.getString(R.string.family_events),
                        description = appContext.getString(R.string.family_birthdays_gatherings),
                        color = 0xFF4CAF50.toInt(), // Green
                        supportsVTODO = false,
                        supportsVJOURNAL = false,
                        syncEnabled = false,
                        visible = true,
                        createdAt = familyCalendarSyncedAt,
                        updatedAt = familyCalendarSyncedAt,
                        lastSyncedAt = familyCalendarSyncedAt
                    )
                )

                // Insert sample calendars
                sampleCalendars.forEach { calendar ->
                    val calendarId = calendarRepository.insert(calendar)
                    Timber.d("Created demo calendar: %s (ID: %d)", calendar.displayName, calendarId)
                }
                
                try {
                    calendarContractSync.syncToCalendarProvider(accountId)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to sync demo calendars to provider")
                }
                
                // Create sample address books
                val personalContactsSyncedAt = now - TimeUnit.MINUTES.toMillis(3)
                val businessContactsSyncedAt = now - TimeUnit.MINUTES.toMillis(60)

                val sampleAddressBooks = listOf(
                    com.davy.domain.model.AddressBook(
                        id = 0,
                        accountId = accountId,
                        url = "$contactsBaseUrl/personal",
                        displayName = appContext.getString(R.string.personal_contacts),
                        description = appContext.getString(R.string.my_personal_contacts),
                        ctag = "demo-personal-ctag",
                        syncEnabled = true,
                        createdAt = personalContactsSyncedAt,
                        updatedAt = personalContactsSyncedAt
                    ),
                    com.davy.domain.model.AddressBook(
                        id = 0,
                        accountId = accountId,
                        url = "$contactsBaseUrl/business",
                        displayName = appContext.getString(R.string.business_contacts),
                        description = appContext.getString(R.string.work_related_contacts),
                        ctag = "demo-business-ctag",
                        syncEnabled = true,
                        createdAt = businessContactsSyncedAt,
                        updatedAt = businessContactsSyncedAt
                    )
                )
                
                // Insert sample address books
                sampleAddressBooks.forEach { addressBook ->
                    val addressBookId = addressBookRepository.insert(addressBook)
                    Timber.d("Created demo address book: %s (ID: %d)", addressBook.displayName, addressBookId)

                    androidAccountManager.createAddressBookAccount(
                        mainAccountName = account.accountName,
                        addressBookName = addressBook.displayName,
                        addressBookId = addressBookId,
                        addressBookUrl = addressBook.url
                    )?.let {
                        Timber.d("Created demo address book account: %s", it.name)
                    }
                }

                // Create sample WebCal subscription with simulated sync timestamp
                val webCalSyncedAt = now - TimeUnit.MINUTES.toMillis(15)
                val sampleWebCals = listOf(
                    WebCalSubscription(
                        id = 0,
                        accountId = accountId,
                        subscriptionUrl = "$webCalBaseUrl/public-holidays.ics",
                        displayName = appContext.getString(R.string.webcal),
                        description = appContext.getString(R.string.webcal_subscription_description),
                        color = 0xFF9C27B0.toInt(),
                        syncEnabled = true,
                        visible = true,
                        createdAt = webCalSyncedAt,
                        updatedAt = webCalSyncedAt,
                        lastSyncedAt = webCalSyncedAt
                    )
                )

                sampleWebCals.forEach { subscription ->
                    webCalSubscriptionRepository.insert(subscription)
                    Timber.d("Created demo webcal subscription: %s", subscription.displayName)
                }
                
                Timber.d("=== Demo account setup complete ===")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    accountCreated = true
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to create demo account")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = appContext.getString(R.string.failed_to_create_demo_account, e.message ?: appContext.getString(R.string.unknown))
                )
            }
        }
    }
}
