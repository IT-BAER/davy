package com.davy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import com.davy.R
import android.app.Activity
import android.content.Context
import android.security.KeyChain
import com.davy.data.remote.AuthenticationManager
import com.davy.data.remote.AuthenticationResult
import com.davy.data.remote.caldav.PrincipalDiscovery as CalDAVPrincipalDiscovery
import com.davy.data.remote.carddav.PrincipalDiscovery as CardDAVPrincipalDiscovery
import com.davy.data.remote.oauth.NextcloudLoginFlowV2
import com.davy.data.remote.oauth.LoginFlowInitiation
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
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val HTTPS_PREFIX = "https://"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AddAccountScreen(
    onNavigateBack: () -> Unit,
    onAccountCreated: () -> Unit,
    viewModel: AddAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCertificateDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Track when Custom Tab was opened for Nextcloud login
    var nextcloudLoginStartTime by remember { mutableStateOf<Long?>(null) }
    
    // Cancel Nextcloud login polling when screen is disposed or user navigates away
    DisposableEffect(Unit) {
        onDispose {
            if (uiState.isNextcloudLogin) {
                viewModel.cancelNextcloudLogin()
            }
        }
    }
    
    // Detect when user returns to app from Custom Tab without completing login
    DisposableEffect(lifecycleOwner, uiState.isNextcloudLogin) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // If Nextcloud login is in progress and user returned to app
                    if (uiState.isNextcloudLogin && nextcloudLoginStartTime != null) {
                        val timeSinceStart = System.currentTimeMillis() - nextcloudLoginStartTime!!
                        // Wait a bit to give polling a chance to complete
                        // If more than 3 seconds have passed since opening, check if still pending
                        if (timeSinceStart > 3000) {
                            scope.launch {
                                // Give polling 2 more seconds to complete after user returns
                                kotlinx.coroutines.delay(2000)
                                // If still in Nextcloud login state, user likely didn't complete it
                                if (uiState.isNextcloudLogin) {
                                    Timber.d("Login flow still pending after user return, cancelling...")
                                    viewModel.cancelNextcloudLogin()
                                    nextcloudLoginStartTime = null
                                    snackbarHostState.showSnackbar(
                                        "Login cancelled",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Track when Custom Tab was opened
                    if (uiState.isNextcloudLogin && nextcloudLoginStartTime == null) {
                        nextcloudLoginStartTime = System.currentTimeMillis()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Login method selection with animated expanding forms
            Text(
                text = stringResource(R.string.choose_login_method),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Nextcloud button
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    viewModel.onLoginMethodSelected(
                        if (uiState.loginMethod == LoginMethod.NEXTCLOUD) null 
                        else LoginMethod.NEXTCLOUD
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF0082C9) // Nextcloud blue
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.nextcloud),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.nextcloud_login_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (uiState.loginMethod == LoginMethod.NEXTCLOUD) 
                            Icons.Default.ExpandLess 
                        else 
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Animated Nextcloud form expansion
            AnimatedVisibility(
                visible = uiState.loginMethod == LoginMethod.NEXTCLOUD,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Server URL field for Nextcloud
                        OutlinedTextField(
                            value = uiState.serverUrl,
                            onValueChange = viewModel::onServerUrlChanged,
                            label = { Text(stringResource(R.string.nextcloud_server_url)) },
                            placeholder = { Text(stringResource(R.string.nextcloud_server_url_placeholder)) },
                            leadingIcon = {
                                Text(
                                    text = uiState.protocol,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            singleLine = true,
                            isError = uiState.serverUrlError != null,
                            supportingText = uiState.serverUrlError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.onNextcloudLoginClicked() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Login with Nextcloud button
                        Button(
                            onClick = viewModel::onNextcloudLoginClicked,
                            enabled = !uiState.isLoading && !uiState.isNextcloudLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (uiState.isLoading || uiState.isNextcloudLogin) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.connecting))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.login_with_nextcloud))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // iCloud Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    viewModel.onLoginMethodSelected(
                        if (uiState.loginMethod == LoginMethod.ICLOUD) null 
                        else LoginMethod.ICLOUD
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF007AFF) // Apple blue
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.icloud),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.icloud_login_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (uiState.loginMethod == LoginMethod.ICLOUD) 
                            Icons.Default.ExpandLess 
                        else 
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Animated iCloud form expansion
            AnimatedVisibility(
                visible = uiState.loginMethod == LoginMethod.ICLOUD,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Email field with autofill
                        val autofillNodeEmail = AutofillNode(
                            autofillTypes = listOf(AutofillType.Username),
                            onFill = { viewModel.onUsernameChanged(it) }
                        )
                        val autofillEmail = LocalAutofill.current
                        LocalAutofillTree.current += autofillNodeEmail
                        
                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = viewModel::onUsernameChanged,
                            label = { Text(stringResource(R.string.icloud_email_label)) },
                            placeholder = { Text(stringResource(R.string.icloud_email_placeholder)) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            },
                            singleLine = true,
                            isError = uiState.usernameError != null,
                            supportingText = uiState.usernameError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    autofillNodeEmail.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    autofillEmail?.run {
                                        if (focusState.isFocused) {
                                            requestAutofillForNode(autofillNodeEmail)
                                        } else {
                                            cancelAutofillForNode(autofillNodeEmail)
                                        }
                                    }
                                }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // App-specific password field with autofill
                        val autofillNodePassword = AutofillNode(
                            autofillTypes = listOf(AutofillType.Password),
                            onFill = { viewModel.onPasswordChanged(it) }
                        )
                        val autofillPassword = LocalAutofill.current
                        LocalAutofillTree.current += autofillNodePassword
                        
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = viewModel::onPasswordChanged,
                            label = { Text(stringResource(R.string.icloud_app_password_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = viewModel::togglePasswordVisibility) {
                                    Icon(
                                        imageVector = if (uiState.passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(
                                            if (uiState.passwordVisible) R.string.hide_password else R.string.show_password
                                        )
                                    )
                                }
                            },
                            visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            isError = uiState.passwordError != null,
                            supportingText = uiState.passwordError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.onICloudLoginClicked() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    autofillNodePassword.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    autofillPassword?.run {
                                        if (focusState.isFocused) {
                                            requestAutofillForNode(autofillNodePassword)
                                        } else {
                                            cancelAutofillForNode(autofillNodePassword)
                                        }
                                    }
                                }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Instructions card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.icloud_password_instructions),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.icloud_password_step_1),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.icloud_password_step_2),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.icloud_password_step_3),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.icloud_password_step_4),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.icloud_password_step_5),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Open Apple ID button
                        OutlinedButton(
                            onClick = {
                                val intent = CustomTabsIntent.Builder().build()
                                intent.launchUrl(context, Uri.parse("https://appleid.apple.com/account/manage"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_app_password))
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Continue button
                        Button(
                            onClick = viewModel::onICloudLoginClicked,
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.connecting))
                            } else {
                                Text(stringResource(R.string.continue_button))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Fastmail Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    viewModel.onLoginMethodSelected(
                        if (uiState.loginMethod == LoginMethod.FASTMAIL) null 
                        else LoginMethod.FASTMAIL
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF2F3E48) // Fastmail dark gray
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.fastmail),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.fastmail_login_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (uiState.loginMethod == LoginMethod.FASTMAIL) 
                            Icons.Default.ExpandLess 
                        else 
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Animated Fastmail form expansion
            AnimatedVisibility(
                visible = uiState.loginMethod == LoginMethod.FASTMAIL,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Email field with autofill
                        val autofillNodeEmail = AutofillNode(
                            autofillTypes = listOf(AutofillType.Username),
                            onFill = { viewModel.onUsernameChanged(it) }
                        )
                        val autofillEmail = LocalAutofill.current
                        LocalAutofillTree.current += autofillNodeEmail
                        
                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = viewModel::onUsernameChanged,
                            label = { Text(stringResource(R.string.fastmail_email_label)) },
                            placeholder = { Text(stringResource(R.string.fastmail_email_placeholder)) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            },
                            singleLine = true,
                            isError = uiState.usernameError != null,
                            supportingText = uiState.usernameError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    autofillNodeEmail.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    autofillEmail?.run {
                                        if (focusState.isFocused) {
                                            requestAutofillForNode(autofillNodeEmail)
                                        } else {
                                            cancelAutofillForNode(autofillNodeEmail)
                                        }
                                    }
                                }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Password field with autofill
                        val autofillNodePassword = AutofillNode(
                            autofillTypes = listOf(AutofillType.Password),
                            onFill = { viewModel.onPasswordChanged(it) }
                        )
                        val autofillPassword = LocalAutofill.current
                        LocalAutofillTree.current += autofillNodePassword
                        
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = viewModel::onPasswordChanged,
                            label = { Text(stringResource(R.string.fastmail_password_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = viewModel::togglePasswordVisibility) {
                                    Icon(
                                        imageVector = if (uiState.passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(
                                            if (uiState.passwordVisible) R.string.hide_password else R.string.show_password
                                        )
                                    )
                                }
                            },
                            visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            isError = uiState.passwordError != null,
                            supportingText = uiState.passwordError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.onFastmailLoginClicked() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    autofillNodePassword.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    autofillPassword?.run {
                                        if (focusState.isFocused) {
                                            requestAutofillForNode(autofillNodePassword)
                                        } else {
                                            cancelAutofillForNode(autofillNodePassword)
                                        }
                                    }
                                }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Instructions card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.fastmail_password_instructions),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.fastmail_password_step_1),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.fastmail_password_step_2),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.fastmail_password_step_3),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.fastmail_password_step_4),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.fastmail_password_step_5),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Open Fastmail settings button
                        OutlinedButton(
                            onClick = {
                                val intent = CustomTabsIntent.Builder().build()
                                intent.launchUrl(context, Uri.parse("https://www.fastmail.com/settings/security/devicekeys"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_fastmail_app_password))
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Continue button
                        Button(
                            onClick = viewModel::onFastmailLoginClicked,
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.connecting))
                            } else {
                                Text(stringResource(R.string.continue_button))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Other Server button
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    viewModel.onLoginMethodSelected(
                        if (uiState.loginMethod == LoginMethod.OTHER_SERVER) null 
                        else LoginMethod.OTHER_SERVER
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.other_server),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.other_server_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (uiState.loginMethod == LoginMethod.OTHER_SERVER) 
                            Icons.Default.ExpandLess 
                        else 
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Animated Other Server form expansion
            AnimatedVisibility(
                visible = uiState.loginMethod == LoginMethod.OTHER_SERVER,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                        
                        // Server URL with Protocol Prefix
                        OutlinedTextField(
                            value = uiState.serverUrl,
                            onValueChange = viewModel::onServerUrlChanged,
                            label = { Text(stringResource(id = R.string.server_url)) },
                            placeholder = { Text(stringResource(id = R.string.server_url_placeholder)) },
                            leadingIcon = {
                                Text(
                                    text = uiState.protocol,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // URL guidance info card - only show if autodiscovery failed
                        if (uiState.autodiscoveryFailed) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.server_url_guidance_title),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(id = R.string.server_url_guidance_autodiscovery),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(id = R.string.server_url_guidance_manual),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(
                                                text = stringResource(id = R.string.server_url_example_nextcloud),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = stringResource(id = R.string.server_url_example_baikal),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = stringResource(id = R.string.server_url_example_radicale),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
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
                        
                        // Client Certificate (optional)
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showCertificateDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.client_certificate),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                        )
                                        Text(
                                            text = if (uiState.certificateAlias.isNotBlank()) {
                                                uiState.certificateAlias
                                            } else {
                                                stringResource(id = R.string.optional_tap_to_select)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Buttons row - Test and Add side by side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Error message display
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
            
            // Demo mode hint (only show on main selection screen)
            if (uiState.loginMethod == null) {
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
    }
    
    // Certificate selection dialog
    if (showCertificateDialog) {
        var tempCertificateAlias by remember { mutableStateOf(uiState.certificateAlias) }
        
        AlertDialog(
            onDismissRequest = { showCertificateDialog = false },
            title = { Text(stringResource(id = R.string.client_certificate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(id = R.string.select_client_certificate_for_auth))
                    
                    // Option 1: Choose from Android KeyChain
                    OutlinedButton(
                        onClick = {
                            val activity = context as Activity
                            showCertificateDialog = false
                            KeyChain.choosePrivateKeyAlias(
                                activity,
                                { alias ->
                                    // Update certificate alias with selected alias
                                    if (alias != null) {
                                        viewModel.onCertificateAliasChanged(alias)
                                    } else {
                                        // Show snackbar when no certificate selected (or none installed)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.no_certificate_selected),
                                                actionLabel = context.getString(R.string.install_certificate),
                                                duration = SnackbarDuration.Long
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                // Open Android certificate installer
                                                val intent = KeyChain.createInstallIntent()
                                                if (intent.resolveActivity(context.packageManager) != null) {
                                                    context.startActivity(intent)
                                                }
                                            }
                                        }
                                    }
                                },
                                null, // keyTypes (null = all types)
                                null, // issuers (null = all issuers)
                                null, // host
                                -1,   // port
                                uiState.certificateAlias.ifBlank { null } // alias to preselect
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(id = R.string.choose_from_system_certificates))
                    }
                    
                    HorizontalDivider()
                    
                    // Option 2: Manual entry
                    Text(
                        stringResource(id = R.string.enter_certificate_alias_manually),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempCertificateAlias,
                        onValueChange = { tempCertificateAlias = it },
                        singleLine = true,
                        label = { Text(stringResource(id = R.string.certificate_alias)) },
                        placeholder = { Text(stringResource(id = R.string.enter_certificate_alias)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onCertificateAliasChanged(tempCertificateAlias)
                    showCertificateDialog = false
                }) {
                    Text(stringResource(id = R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.onCertificateAliasChanged("")
                    showCertificateDialog = false
                }) {
                    Text(stringResource(id = R.string.remove))
                }
            }
        )
    }
    
    // Success navigation
    LaunchedEffect(uiState.accountCreated) {
        if (uiState.accountCreated) {
            onAccountCreated()
        }
    }
    
    // Nextcloud Login Flow: Open Custom Tab when login URL is available
    LaunchedEffect(uiState.nextcloudLoginUrl) {
        uiState.nextcloudLoginUrl?.let { loginUrl ->
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                
                customTabsIntent.launchUrl(context, Uri.parse(loginUrl))
                
                // Start polling after opening Custom Tab
                viewModel.startNextcloudLoginPolling()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to open Nextcloud login in Custom Tab")
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Failed to open browser: ${e.message}",
                        duration = SnackbarDuration.Long
                    )
                }
                viewModel.cancelNextcloudLogin()
            }
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
        placeholder = { Text(stringResource(id = R.string.password_placeholder)) },
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
    val loginMethod: LoginMethod? = null, // null = showing method selection, non-null = method selected
    val accountName: String = "",
    val protocol: String = HTTPS_PREFIX,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val certificateAlias: String = "",
    val accountNameError: String? = null,
    val serverUrlError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val accountCreated: Boolean = false,
    val isTestingCredentials: Boolean = false,
    val credentialTestResult: String? = null,
    val isNextcloudLogin: Boolean = false,
    val nextcloudLoginUrl: String? = null,
    val autodiscoveryFailed: Boolean = false,
    val passwordVisible: Boolean = false
)

enum class LoginMethod {
    NEXTCLOUD,
    ICLOUD,
    FASTMAIL,
    OTHER_SERVER
}

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
    private val nextcloudLoginFlow: NextcloudLoginFlowV2,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()
    
    private var loginFlowInitiation: LoginFlowInitiation? = null
    private var pollingJob: kotlinx.coroutines.Job? = null
    
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
            serverUrlError = null,
            autodiscoveryFailed = false
        )
    }
    
    fun onLoginMethodSelected(method: LoginMethod?) {
        _uiState.value = _uiState.value.copy(
            loginMethod = method,
            errorMessage = null
        )
    }
    
    fun onBackToMethodSelection() {
        _uiState.value = _uiState.value.copy(
            loginMethod = null,
            serverUrl = "",
            accountName = "",
            username = "",
            password = "",
            serverUrlError = null,
            accountNameError = null,
            usernameError = null,
            passwordError = null,
            errorMessage = null,
            autodiscoveryFailed = false,
            isNextcloudLogin = false,
            nextcloudLoginUrl = null
        )
        cancelNextcloudLogin()
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
    
    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            passwordVisible = !_uiState.value.passwordVisible
        )
    }
    
    fun onCertificateAliasChanged(alias: String) {
        _uiState.value = _uiState.value.copy(
            certificateAlias = alias
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
                errorMessage = null,
                autodiscoveryFailed = false
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
                    certificateFingerprint = state.certificateAlias.ifBlank { null }
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
                    autodiscoveryFailed = true,
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
                6 -> {
                    // #RRGGBB -> add full opacity
                    0xFF000000.toInt() or hex.toInt(16)
                }
                8 -> {
                    // #RRGGBBAA -> convert to Android's #AARRGGBB format
                    val rgb = hex.substring(0, 6).toInt(16)
                    val alpha = hex.substring(6, 8).toInt(16)
                    (alpha shl 24) or rgb
                }
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
    
    /**
     * Start Nextcloud Login Flow v2.
     * Initiates the login flow and returns the login URL to open in browser.
     */
    fun onNextcloudLoginClicked() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            try {
                val state = _uiState.value
                val fullUrl = state.protocol + state.serverUrl
                
                // Initiate login flow
                val initiation = nextcloudLoginFlow.initiateLogin(fullUrl)
                loginFlowInitiation = initiation
                
                Timber.d("Nextcloud login flow initiated, URL: ${initiation.loginUrl}")
                
                // Update UI with login URL (will trigger Custom Tab)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isNextcloudLogin = true,
                    nextcloudLoginUrl = initiation.loginUrl
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to initiate Nextcloud login flow")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to start Nextcloud login: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Start polling for Nextcloud login completion.
     * Should be called after Custom Tab is opened.
     */
    fun startNextcloudLoginPolling() {
        val initiation = loginFlowInitiation ?: run {
            Timber.e("No login flow initiation found")
            return
        }
        
        // Cancel any existing polling job
        pollingJob?.cancel()
        
        pollingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            try {
                Timber.d("Starting to poll for Nextcloud login completion...")
                
                // Poll for credentials with timeout
                val credentials = withTimeout(5 * 60 * 1000L) { // 5 minutes timeout
                    nextcloudLoginFlow.pollForCredentials(initiation)
                }
                
                Timber.d("Nextcloud login successful! User: ${credentials.loginName}")
                
                // Create account with received credentials
                createNextcloudAccount(
                    serverUrl = credentials.serverUrl,
                    username = credentials.loginName,
                    appPassword = credentials.appPassword
                )
                
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.w("Nextcloud login flow timed out")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isNextcloudLogin = false,
                    nextcloudLoginUrl = null,
                    errorMessage = "Login timed out. Please try again."
                )
                loginFlowInitiation = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("Nextcloud login flow was cancelled")
                // Don't update UI state - cancellation is intentional
            } catch (e: Exception) {
                Timber.e(e, "Nextcloud login flow failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isNextcloudLogin = false,
                    nextcloudLoginUrl = null,
                    errorMessage = "Nextcloud login failed: ${e.message}"
                )
                loginFlowInitiation = null
            }
        }
    }
    
    /**
     * Cancel Nextcloud login flow.
     */
    fun cancelNextcloudLogin() {
        pollingJob?.cancel()
        pollingJob = null
        loginFlowInitiation = null
        _uiState.value = _uiState.value.copy(
            isNextcloudLogin = false,
            nextcloudLoginUrl = null,
            isLoading = false
        )
    }
    
    /**
     * Handle iCloud login - pre-configure with iCloud servers.
     */
    fun onICloudLoginClicked() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                usernameError = null,
                passwordError = null
            )
            
            try {
                val state = _uiState.value
                val email = state.username.trim()
                val password = state.password
                
                // Validate inputs
                if (email.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        usernameError = appContext.getString(R.string.username_required)
                    )
                    return@launch
                }
                
                if (password.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        passwordError = appContext.getString(R.string.icloud_app_password_required)
                    )
                    return@launch
                }
                
                // iCloud uses standard CalDAV/CardDAV endpoints
                val caldavUrl = "https://caldav.icloud.com/"
                val carddavUrl = "https://carddav.icloud.com/"
                
                // Create account with iCloud configuration
                createProviderAccount(
                    providerName = "iCloud",
                    email = email,
                    password = password,
                    caldavUrl = caldavUrl,
                    carddavUrl = carddavUrl
                )
                
            } catch (e: Exception) {
                Timber.e(e, "iCloud login failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to connect to iCloud: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Handle Fastmail login - pre-configure with Fastmail servers.
     */
    fun onFastmailLoginClicked() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                usernameError = null,
                passwordError = null
            )
            
            try {
                val state = _uiState.value
                val email = state.username.trim()
                val password = state.password
                
                // Validate inputs
                if (email.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        usernameError = appContext.getString(R.string.username_required)
                    )
                    return@launch
                }
                
                if (password.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        passwordError = appContext.getString(R.string.password_required)
                    )
                    return@launch
                }
                
                // Fastmail uses direct CalDAV/CardDAV URLs (auto-discovery supported)
                val caldavUrl = "https://caldav.fastmail.com/"
                val carddavUrl = "https://carddav.fastmail.com/"
                
                // Create account with Fastmail configuration
                createProviderAccount(
                    providerName = "Fastmail",
                    email = email,
                    password = password,
                    caldavUrl = caldavUrl,
                    carddavUrl = carddavUrl
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Fastmail login failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to connect to Fastmail: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Create account for a specific provider with pre-configured URLs.
     */
    private suspend fun createProviderAccount(
        providerName: String,
        email: String,
        password: String,
        caldavUrl: String,
        carddavUrl: String
    ) {
        try {
            val state = _uiState.value
            val accountName = state.accountName.ifBlank { "$providerName - $email" }
            
            // Check for duplicate account
            val existingAccount = accountRepository.findByServerAndUsername(caldavUrl, email)
            if (existingAccount != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = appContext.getString(R.string.account_exists_message, existingAccount.accountName)
                )
                return
            }
            
            // Discover principals directly using provided URLs
            val calDavPrincipal = try {
                caldavPrincipalDiscovery.discoverPrincipal(caldavUrl, email, password)
            } catch (e: Exception) {
                Timber.w(e, "Failed to discover CalDAV principal for $providerName")
                null
            }
            
            val cardDavPrincipal = try {
                carddavPrincipalDiscovery.discoverPrincipal(carddavUrl, email, password)
            } catch (e: Exception) {
                Timber.w(e, "Failed to discover CardDAV principal for $providerName")
                null
            }
            
            // Ensure at least one service is available
            if (calDavPrincipal == null && cardDavPrincipal == null) {
                throw Exception("Could not connect to $providerName. Please check your credentials.")
            }
            
            // Create account
            val account = Account(
                id = 0,
                accountName = accountName,
                serverUrl = caldavUrl,
                username = email,
                displayName = accountName,
                email = email,
                calendarEnabled = calDavPrincipal != null,
                contactsEnabled = cardDavPrincipal != null,
                tasksEnabled = calDavPrincipal != null,
                createdAt = System.currentTimeMillis(),
                lastAuthenticatedAt = System.currentTimeMillis(),
                authType = com.davy.domain.model.AuthType.BASIC,
                certificateFingerprint = null
            )
            
            val accountId = accountRepository.insert(account)
            credentialStore.storePassword(accountId, password)
            
            // Create Android account
            androidAccountManager.createOrUpdateAccount(account.accountName, password)
            
            // Discover and create calendars
            if (calDavPrincipal != null) {
                try {
                    val calendars = caldavPrincipalDiscovery.discoverCalendars(
                        calendarHomeSetUrl = calDavPrincipal.calendarHomeSet,
                        username = email,
                        password = password
                    )
                    
                    calendars.forEach { calendarInfo ->
                        val calendar = com.davy.domain.model.Calendar(
                            id = 0,
                            accountId = accountId,
                            calendarUrl = calendarInfo.url,
                            displayName = calendarInfo.displayName,
                            description = calendarInfo.description,
                            color = parseColor(calendarInfo.color),
                            supportsVTODO = calendarInfo.supportsVTODO,
                            supportsVJOURNAL = calendarInfo.supportsVJOURNAL,
                            syncEnabled = true,
                            visible = true,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            lastSyncedAt = null
                        )
                        calendarRepository.insert(calendar)
                    }
                    
                    calendarContractSync.syncToCalendarProvider(accountId)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to discover/sync calendars for $providerName")
                }
            }
            
            // Discover and create address books
            if (cardDavPrincipal != null) {
                try {
                    val addressBooks = carddavPrincipalDiscovery.discoverAddressbooks(
                        addressbookHomeSetUrl = cardDavPrincipal.addressbookHomeSet,
                        username = email,
                        password = password
                    )
                    
                    addressBooks.forEach { addressBookInfo ->
                        val addressBook = com.davy.domain.model.AddressBook(
                            id = 0,
                            accountId = accountId,
                            url = addressBookInfo.url,
                            displayName = addressBookInfo.displayName,
                            description = addressBookInfo.description,
                            owner = addressBookInfo.owner,
                            privWriteContent = addressBookInfo.privWriteContent,
                            syncEnabled = true,
                            visible = true,
                            androidAccountName = null,
                            ctag = null,
                            color = 0xFF2196F3.toInt(),
                            forceReadOnly = false,
                            wifiOnlySync = false,
                            syncIntervalMinutes = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        val addressBookId = addressBookRepository.insert(addressBook)
                        
                        // Create separate Android account for address book
                        androidAccountManager.createAddressBookAccount(
                            mainAccountName = account.accountName,
                            addressBookName = addressBookInfo.displayName,
                            addressBookId = addressBookId,
                            addressBookUrl = addressBookInfo.url
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to discover address books for $providerName")
                }
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                accountCreated = true
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create $providerName account")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to create $providerName account: ${e.message}"
            )
        }
    }
    
    /**
     * Create account using authentication result from service discovery.
     */
    private suspend fun createProviderAccountFromAuth(
        providerName: String,
        email: String,
        password: String,
        authResult: com.davy.data.remote.AuthenticationResult
    ) {
        try {
            val state = _uiState.value
            val accountName = state.accountName.ifBlank { "$providerName - $email" }
            
            // Check for duplicate account
            val existingAccount = accountRepository.findByServerAndUsername(authResult.serverUrl, email)
            if (existingAccount != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = appContext.getString(R.string.account_exists_message, existingAccount.accountName)
                )
                return
            }
            
            // Ensure at least one service is available
            if (!authResult.hasCalDAV() && !authResult.hasCardDAV()) {
                throw Exception("Could not connect to $providerName. No CalDAV or CardDAV services found.")
            }
            
            // Create account
            val account = Account(
                id = 0,
                accountName = accountName,
                serverUrl = authResult.serverUrl,
                username = email,
                displayName = accountName,
                email = email,
                calendarEnabled = authResult.hasCalDAV(),
                contactsEnabled = authResult.hasCardDAV(),
                tasksEnabled = authResult.hasCalDAV(),
                createdAt = System.currentTimeMillis(),
                lastAuthenticatedAt = System.currentTimeMillis(),
                authType = com.davy.domain.model.AuthType.BASIC,
                certificateFingerprint = null
            )
            
            val accountId = accountRepository.insert(account)
            credentialStore.storePassword(accountId, password)
            
            // Create Android account
            androidAccountManager.createOrUpdateAccount(account.accountName, password)
            
            // Discover and create calendars
            if (authResult.hasCalDAV() && authResult.calDavPrincipal != null) {
                try {
                    val calendars = caldavPrincipalDiscovery.discoverCalendars(
                        calendarHomeSetUrl = authResult.calDavPrincipal.calendarHomeSet,
                        username = email,
                        password = password
                    )
                    
                    calendars.forEach { calendarInfo ->
                        val calendar = com.davy.domain.model.Calendar(
                            id = 0,
                            accountId = accountId,
                            calendarUrl = calendarInfo.url,
                            displayName = calendarInfo.displayName,
                            description = calendarInfo.description,
                            color = parseColor(calendarInfo.color),
                            supportsVTODO = calendarInfo.supportsVTODO,
                            supportsVJOURNAL = calendarInfo.supportsVJOURNAL,
                            syncEnabled = true,
                            visible = true,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            lastSyncedAt = null
                        )
                        calendarRepository.insert(calendar)
                    }
                    
                    calendarContractSync.syncToCalendarProvider(accountId)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to discover/sync calendars for $providerName")
                }
            }
            
            // Discover and create address books
            if (authResult.hasCardDAV() && authResult.cardDavPrincipal != null) {
                try {
                    val addressBooks = carddavPrincipalDiscovery.discoverAddressbooks(
                        addressbookHomeSetUrl = authResult.cardDavPrincipal.addressbookHomeSet,
                        username = email,
                        password = password
                    )
                    
                    addressBooks.forEach { addressBookInfo ->
                        val addressBook = com.davy.domain.model.AddressBook(
                            id = 0,
                            accountId = accountId,
                            url = addressBookInfo.url,
                            displayName = addressBookInfo.displayName,
                            description = addressBookInfo.description,
                            owner = addressBookInfo.owner,
                            privWriteContent = addressBookInfo.privWriteContent,
                            syncEnabled = true,
                            visible = true,
                            androidAccountName = null,
                            ctag = null,
                            color = 0xFF2196F3.toInt(),
                            forceReadOnly = false,
                            wifiOnlySync = false,
                            syncIntervalMinutes = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        val addressBookId = addressBookRepository.insert(addressBook)
                        
                        // Create separate Android account for address book
                        androidAccountManager.createAddressBookAccount(
                            mainAccountName = account.accountName,
                            addressBookName = addressBookInfo.displayName,
                            addressBookId = addressBookId,
                            addressBookUrl = addressBookInfo.url
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to discover address books for $providerName")
                }
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                accountCreated = true
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create $providerName account")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to create $providerName account: ${e.message}"
            )
        }
    }
    
    /**
     * Create account with Nextcloud app password from login flow.
     */
    private suspend fun createNextcloudAccount(
        serverUrl: String,
        username: String,
        appPassword: String
    ) {
        try {
            val state = _uiState.value
            val accountName = state.accountName.ifBlank { "$username@${serverUrl.substringAfter("://").substringBefore("/")}" }
            
            // Normalize server URL
            val normalizedUrl = serverUrl.trimEnd('/')
            
            // Check for duplicate account
            val existingAccount = accountRepository.findByServerAndUsername(normalizedUrl, username)
            if (existingAccount != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isNextcloudLogin = false,
                    nextcloudLoginUrl = null,
                    errorMessage = appContext.getString(R.string.account_exists_message, existingAccount.accountName)
                )
                return
            }
            
            // Authenticate and discover services
            val authResult = authenticationManager.authenticate(
                serverUrl = normalizedUrl,
                username = username,
                password = appPassword
            )
            
            // Create account
            val account = Account(
                id = 0,
                accountName = accountName,
                serverUrl = normalizedUrl,
                username = username,
                displayName = accountName,
                email = if (username.contains("@")) username else null,
                calendarEnabled = authResult.hasCalDAV(),
                contactsEnabled = authResult.hasCardDAV(),
                tasksEnabled = authResult.hasCalDAV(),
                createdAt = System.currentTimeMillis(),
                lastAuthenticatedAt = System.currentTimeMillis(),
                authType = AuthType.APP_PASSWORD, // Nextcloud app password
                certificateFingerprint = null
            )
            
            val accountId = accountRepository.insert(account)
            credentialStore.storePassword(accountId, appPassword)
            
            // Create Android account
            androidAccountManager.createOrUpdateAccount(account.accountName, appPassword)
            
            // Discover and create calendars
            if (authResult.hasCalDAV() && authResult.calDavPrincipal != null) {
                try {
                    val calendars = caldavPrincipalDiscovery.discoverCalendars(
                        calendarHomeSetUrl = authResult.calDavPrincipal.calendarHomeSet,
                        username = username,
                        password = appPassword
                    )
                    
                    calendars.forEach { calendarInfo ->
                        val calendar = com.davy.domain.model.Calendar(
                            id = 0,
                            accountId = accountId,
                            calendarUrl = calendarInfo.url,
                            displayName = calendarInfo.displayName,
                            description = calendarInfo.description,
                            color = parseColor(calendarInfo.color),
                            supportsVTODO = calendarInfo.supportsVTODO,
                            supportsVJOURNAL = calendarInfo.supportsVJOURNAL,
                            syncEnabled = true,
                            visible = true,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            lastSyncedAt = null
                        )
                        calendarRepository.insert(calendar)
                    }
                    
                    calendarContractSync.syncToCalendarProvider(accountId)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to discover/sync calendars")
                }
            }
            
            // Discover and create address books
            if (authResult.hasCardDAV() && authResult.cardDavPrincipal != null) {
                try {
                    val addressBooks = carddavPrincipalDiscovery.discoverAddressbooks(
                        addressbookHomeSetUrl = authResult.cardDavPrincipal.addressbookHomeSet,
                        username = username,
                        password = appPassword
                    )
                    
                    addressBooks.forEach { addressBookInfo ->
                        val addressBook = com.davy.domain.model.AddressBook(
                            id = 0,
                            accountId = accountId,
                            url = addressBookInfo.url,
                            displayName = addressBookInfo.displayName,
                            description = addressBookInfo.description,
                            owner = addressBookInfo.owner,
                            privWriteContent = addressBookInfo.privWriteContent,
                            syncEnabled = true,
                            visible = true,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        val addressBookId = addressBookRepository.insert(addressBook)
                        
                        androidAccountManager.createAddressBookAccount(
                            mainAccountName = account.accountName,
                            addressBookName = addressBook.displayName,
                            addressBookId = addressBookId,
                            addressBookUrl = addressBook.url
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to discover address books")
                }
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isNextcloudLogin = false,
                nextcloudLoginUrl = null,
                accountCreated = true
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create Nextcloud account")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isNextcloudLogin = false,
                nextcloudLoginUrl = null,
                errorMessage = "Failed to create account: ${e.message}"
            )
        }
    }
}
