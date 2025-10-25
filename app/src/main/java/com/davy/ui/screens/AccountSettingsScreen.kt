package com.davy.ui.screens

import android.app.Activity
import android.security.KeyChain
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davy.ui.viewmodels.AccountDetailViewModel
import com.davy.ui.util.rememberDebounced
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    accountId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val account = uiState.account
    
    // Navigate back when account deletion completes
    LaunchedEffect(uiState.accountDeleted) {
        if (uiState.accountDeleted) {
            onNavigateBack()
        }
    }
    
    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = com.davy.R.string.account_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(id = com.davy.R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (account == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            AccountSettingsContent(
                account = account,
                uiState = uiState,
                modifier = Modifier.padding(paddingValues),
                onUpdateAccount = { updatedAccount, newPassword ->
                    viewModel.updateAccount(updatedAccount, newPassword)
                },
                onRenameAccount = { newName ->
                    viewModel.renameAccount(newName)
                },
                onUpdateSyncConfiguration = {
                    viewModel.updateSyncConfiguration(accountId)
                },
                onDeleteAccount = {
                    viewModel.deleteAccount()
                },
                onDeleteOldEvents = { daysThreshold ->
                    viewModel.deleteOldEvents(accountId, daysThreshold)
                },
                onCreateBackup = { accId ->
                    viewModel.createBackup(accId)
                },
                onRestoreBackup = { backupJson ->
                    viewModel.restoreBackup(backupJson, overwriteExisting = false)
                },
                onTestCredentials = { serverUrl, username, password ->
                    viewModel.onTestCredentialsClicked(serverUrl, username, password)
                }
            )
        }
    }
}

@Composable
private fun AccountSettingsContent(
    account: com.davy.domain.model.Account,
    uiState: com.davy.ui.viewmodels.AccountDetailUiState,
    modifier: Modifier = Modifier,
    onUpdateAccount: (com.davy.domain.model.Account, String?) -> Unit,
    onRenameAccount: (String) -> Unit,
    onUpdateSyncConfiguration: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDeleteOldEvents: (Int) -> Unit,
    onCreateBackup: suspend (Long) -> com.davy.domain.manager.BackupRestoreManager.BackupResult,
    onRestoreBackup: suspend (String) -> com.davy.domain.manager.BackupRestoreManager.RestoreResult,
    onTestCredentials: (String, String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("sync_config_${account.id}", android.content.Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Authentication settings
    var username by remember { mutableStateOf(account.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var certificatePath by remember { mutableStateOf(account.certificateFingerprint ?: "") }
    var showCertificateDialog by remember { mutableStateOf(false) }
    
    // Track if auth fields have changed
    val authFieldsChanged by remember(username, password, certificatePath) {
        derivedStateOf {
            username != account.username || 
            password.isNotBlank() || 
            certificatePath != (account.certificateFingerprint ?: "")
        }
    }
    
    // Debounce user input to prevent UI thread blocking on every keystroke
    val debouncedCertificatePath = rememberDebounced(certificatePath, delayMillis = 500L)
    
    
    // Save certificate changes with debouncing
    LaunchedEffect(debouncedCertificatePath) {
        if (debouncedCertificatePath != (account.certificateFingerprint ?: "")) {
            withContext(Dispatchers.IO) {
                val updatedAccount = account.copy(
                    certificateFingerprint = debouncedCertificatePath.ifBlank { null }
                )
                onUpdateAccount(updatedAccount, null)
            }
        }
    }
    
    // NOTE: Removed auto-save for username and password - now requires Apply button
    
    // Sync interval options (in minutes, -1 = manual)
    val syncIntervalOptions = listOf(
        -1 to "Manually",
        15 to "15 minutes",
        30 to "30 minutes",
        60 to "1 hour",
        120 to "2 hours",
        240 to "4 hours",
        720 to "12 hours",
        1440 to "1 day"
    )
    
    // Load saved intervals from SharedPreferences
    var calendarSyncInterval by remember { mutableStateOf(prefs.getInt("calendar_sync_interval", 60)) }
    var contactSyncInterval by remember { mutableStateOf(prefs.getInt("contact_sync_interval", 60)) }
    var webCalSyncInterval by remember { mutableStateOf(prefs.getInt("webcal_sync_interval", 60)) }
    var showCalendarIntervalDialog by remember { mutableStateOf(false) }
    var showContactIntervalDialog by remember { mutableStateOf(false) }
    var showWebCalIntervalDialog by remember { mutableStateOf(false) }
    
    var syncOnlyOnWifi by remember { mutableStateOf(prefs.getBoolean("wifi_only", false)) }
    var manageCalendarColors by remember { mutableStateOf(prefs.getBoolean("manage_calendar_colors", true)) }
    var eventColors by remember { mutableStateOf(prefs.getBoolean("event_colors", false)) }
    
    // CardDAV settings
    val contactGroupMethods = listOf("GROUP_VCARDS", "CATEGORIES")
    var contactGroupMethod by remember { mutableStateOf(prefs.getString("contact_group_method", "CATEGORIES") ?: "CATEGORIES") }
    var showGroupMethodDialog by remember { mutableStateOf(false) }
    
    // CalDAV settings
    val eventAgeDays = listOf(
        -1 to "All events",
        7 to "7 days",
        30 to "30 days",
        90 to "90 days",
        180 to "180 days",
        365 to "1 year"
    )
    var skipEventsDays by remember { mutableStateOf(prefs.getInt("skip_events_days", -1)) }
    var showEventAgeDialog by remember { mutableStateOf(false) }
    
    // Consolidate all preferences into a single state object
    data class PreferencesState(
        val calendarInterval: Int,
        val contactInterval: Int,
        val webCalInterval: Int,
        val wifiOnly: Boolean,
        val manageColors: Boolean,
        val eventColors: Boolean,
        val groupMethod: String,
        val skipDays: Int
    )
    
    val prefsState = PreferencesState(
        calendarSyncInterval,
        contactSyncInterval,
        webCalSyncInterval,
        syncOnlyOnWifi,
        manageCalendarColors,
        eventColors,
        contactGroupMethod,
        skipEventsDays
    )
    
    // Debounce all preference changes together to batch writes
    val debouncedPrefs = rememberDebounced(prefsState, delayMillis = 1000L)
    
    // Save all preference changes in one batched operation on background thread
    LaunchedEffect(debouncedPrefs) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putInt("calendar_sync_interval", debouncedPrefs.calendarInterval)
                .putInt("contact_sync_interval", debouncedPrefs.contactInterval)
                .putInt("webcal_sync_interval", debouncedPrefs.webCalInterval)
                .putBoolean("wifi_only", debouncedPrefs.wifiOnly)
                .putBoolean("manage_calendar_colors", debouncedPrefs.manageColors)
                .putBoolean("event_colors", debouncedPrefs.eventColors)
                .putString("contact_group_method", debouncedPrefs.groupMethod)
                .putInt("skip_events_days", debouncedPrefs.skipDays)
                .apply()
            
            // Trigger sync configuration update if sync-related settings changed
            if (debouncedPrefs.calendarInterval != calendarSyncInterval ||
                debouncedPrefs.contactInterval != contactSyncInterval ||
                debouncedPrefs.webCalInterval != webCalSyncInterval ||
                debouncedPrefs.wifiOnly != syncOnlyOnWifi) {
                onUpdateSyncConfiguration()
            }
            
            // Delete old events if skip days changed
            if (debouncedPrefs.skipDays != skipEventsDays) {
                onDeleteOldEvents(debouncedPrefs.skipDays)
            }
        }
    }
    
    // Account actions
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Account Info Section
        Text(
            text = stringResource(id = com.davy.R.string.account_information),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Account Name with Edit Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRenameDialog = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = account.accountName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(id = com.davy.R.string.rename_account),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = stringResource(id = com.davy.R.string.server_with_value, account.serverUrl),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Authentication Section
        Text(
            text = stringResource(id = com.davy.R.string.authentication),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        com.davy.ui.screens.UsernameTextFieldWithAutofill(
            value = username,
            onValueChange = { username = it },
            isError = false,
            supportingText = null,
            onNext = { /* Focus password field */ },
            modifier = Modifier.fillMaxWidth()
        )
        
        com.davy.ui.screens.PasswordTextFieldWithAutofill(
            value = password,
            onValueChange = { password = it },
            passwordVisible = passwordVisible,
            onPasswordVisibilityChanged = { passwordVisible = it },
            isError = false,
            supportingText = {
                Text(
                    text = stringResource(id = com.davy.R.string.leave_empty_keep_password),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            onDone = { /* Apply changes if modified */ },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Animated buttons row - shown only when credentials changed
        AnimatedVisibility(
            visible = authFieldsChanged,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Test Credentials button
                var testButtonState by remember { mutableStateOf<ButtonState>(ButtonState.Normal) }
                
                // Reset test button state when credentials change
                LaunchedEffect(username, password) {
                    if (testButtonState != ButtonState.Normal) {
                        testButtonState = ButtonState.Normal
                    }
                }
                
                OutlinedButton(
                    onClick = {
                        testButtonState = ButtonState.Loading
                        onTestCredentials(
                            account.serverUrl,
                            username.ifBlank { account.username },
                            password
                        )
                    },
                    enabled = !uiState.isTestingCredentials && account.serverUrl.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    when {
                        uiState.isTestingCredentials -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.testing))
                        }
                        uiState.credentialTestResult?.startsWith("✓") == true -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.valid))
                        }
                        uiState.credentialTestResult?.startsWith("✗") == true -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.failed))
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.test))
                        }
                    }
                }
                
                // Apply button with success animation
                var applyButtonState by remember { mutableStateOf<ButtonState>(ButtonState.Normal) }
                var showApplySuccess by remember { mutableStateOf(false) }
                
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            applyButtonState = ButtonState.Loading
                            withContext(Dispatchers.IO) {
                                val updatedAccount = account.copy(username = username)
                                val newPassword = password.ifBlank { null }
                                onUpdateAccount(updatedAccount, newPassword)
                            }
                            applyButtonState = ButtonState.Success
                            showApplySuccess = true
                            
                            // Clear password field after applying
                            password = ""
                            
                            // Reset success state after delay
                            delay(2000)
                            showApplySuccess = false
                            applyButtonState = ButtonState.Normal
                        }
                    },
                    enabled = applyButtonState != ButtonState.Loading,
                    modifier = Modifier.weight(1f)
                ) {
                    when (applyButtonState) {
                        ButtonState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.applying))
                        }
                        ButtonState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier
                                    .size(20.dp)
                                    .then(
                                        if (showApplySuccess) {
                                            Modifier.scale(
                                                animateFloatAsState(
                                                    targetValue = 1.2f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ), label = "success_scale"
                                                ).value
                                            )
                                        } else Modifier
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.applied))
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(id = com.davy.R.string.apply_changes),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = com.davy.R.string.apply))
                        }
                    }
                }
            }
        }
        
        // Client certificate selection
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.client_certificate),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (certificatePath.isNotBlank()) certificatePath else stringResource(id = com.davy.R.string.none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Sync Settings Section
        Text(
            text = stringResource(id = com.davy.R.string.settings_sync_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // WiFi-only sync
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = stringResource(id = com.davy.R.string.sync_on_wifi_only),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                        text = stringResource(id = com.davy.R.string.disable_mobile_data_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = syncOnlyOnWifi,
                onCheckedChange = { syncOnlyOnWifi = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Calendar sync interval
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showCalendarIntervalDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.calendar_sync_interval),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = intervalLabel(calendarSyncInterval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Contact sync interval
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showContactIntervalDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.contact_sync_interval),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = intervalLabel(contactSyncInterval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // WebCal sync interval
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showWebCalIntervalDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.webcal_sync_interval),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = intervalLabel(webCalSyncInterval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // CalDAV Settings
        Text(
            text = stringResource(id = com.davy.R.string.caldav_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Skip events older than
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showEventAgeDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.skip_events_older_than),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = eventAgeLabel(skipEventsDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = com.davy.R.string.manage_calendar_colors),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(id = com.davy.R.string.use_server_side_calendar_colors),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = manageCalendarColors,
                onCheckedChange = { manageCalendarColors = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = com.davy.R.string.event_colors),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(id = com.davy.R.string.use_colors_from_individual_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = eventColors,
                onCheckedChange = { eventColors = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // CardDAV Settings
        Text(
            text = stringResource(id = com.davy.R.string.carddav_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Contact Group Method
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showGroupMethodDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.contact_group_method),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when (contactGroupMethod) {
                            "GROUP_VCARDS" -> stringResource(id = com.davy.R.string.group_vcards)
                            else -> stringResource(id = com.davy.R.string.categories)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Account Actions Section
        Text(
            text = stringResource(id = com.davy.R.string.account_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Backup Settings
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showBackupDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.backup_settings),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(id = com.davy.R.string.create_backup_of_account_configuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        // Restore Settings
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showRestoreDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.restore_settings),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(id = com.davy.R.string.restore_account_from_backup_file),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        // Delete Account
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDeleteDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.davy.R.string.delete_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(id = com.davy.R.string.permanently_remove_this_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Contact Group Method Dialog
    if (showGroupMethodDialog) {
        AlertDialog(
            onDismissRequest = { showGroupMethodDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.contact_group_method)) },
            text = {
                Column {
                    contactGroupMethods.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    contactGroupMethod = method
                                    showGroupMethodDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = contactGroupMethod == method,
                                onClick = {
                                    contactGroupMethod = method
                                    showGroupMethodDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (method) {
                                    "GROUP_VCARDS" -> stringResource(id = com.davy.R.string.group_vcards)
                                    else -> stringResource(id = com.davy.R.string.categories)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupMethodDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.cancel))
                }
            }
        )
    }
    
    // Event Age Dialog
    if (showEventAgeDialog) {
        AlertDialog(
            onDismissRequest = { showEventAgeDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.skip_events_older_than)) },
            text = {
                Column {
                    eventAgeDays.forEach { (days, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    skipEventsDays = days
                                    showEventAgeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = skipEventsDays == days,
                                onClick = {
                                    skipEventsDays = days
                                    showEventAgeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(eventAgeLabel(days))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEventAgeDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.cancel))
                }
            }
        )
    }
    
    // Calendar Sync Interval Dialog
    if (showCalendarIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarIntervalDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.calendar_sync_interval)) },
            text = {
                Column {
                    syncIntervals.forEach { interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    calendarSyncInterval = interval
                                    showCalendarIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = calendarSyncInterval == interval,
                                onClick = {
                                    calendarSyncInterval = interval
                                    showCalendarIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(intervalLabel(interval))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarIntervalDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.cancel))
                }
            }
        )
    }
    
    // Contact Sync Interval Dialog
    if (showContactIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showContactIntervalDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.contact_sync_interval)) },
            text = {
                Column {
                    syncIntervals.forEach { interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    contactSyncInterval = interval
                                    showContactIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = contactSyncInterval == interval,
                                onClick = {
                                    contactSyncInterval = interval
                                    showContactIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(intervalLabel(interval))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContactIntervalDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.cancel))
                }
            }
        )
    }
    
    // WebCal Sync Interval Dialog
    if (showWebCalIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showWebCalIntervalDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.webcal_sync_interval)) },
            text = {
                Column {
                    syncIntervals.forEach { interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    webCalSyncInterval = interval
                                    showWebCalIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = webCalSyncInterval == interval,
                                onClick = {
                                    webCalSyncInterval = interval
                                    showWebCalIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(intervalLabel(interval))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWebCalIntervalDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.cancel))
                }
            }
        )
    }
    
    // Rename Account Dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(account.accountName) }
        
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.rename_account_title)) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(id = com.davy.R.string.account_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        timber.log.Timber.d("RENAME DIALOG: Renaming account from '${account.accountName}' to '$newName'")
                        onRenameAccount(newName)
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(id = com.davy.R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.cancel))
                }
            }
        )
    }
    
    // Delete Account Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.delete_account)) },
            text = {
                Text(
                    stringResource(
                        id = com.davy.R.string.delete_account_confirmation_named,
                        account.accountName
                    ) + " " + stringResource(id = com.davy.R.string.delete_account_data_removal_note)
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete icon button on the left
                    IconButton(
                        onClick = {
                            timber.log.Timber.d("DELETE DIALOG: Deleting account '${account.accountName}'")
                            onDeleteAccount()
                            showDeleteDialog = false
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(id = com.davy.R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Cancel button on the right
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(id = com.davy.R.string.cancel))
                    }
                }
            },
            dismissButton = null
        )
    }
    
    // Backup Dialog with file picker
    if (showBackupDialog) {
        var backupResult by remember { mutableStateOf<String?>(null) }
        var backupError by remember { mutableStateOf<String?>(null) }
        var isBackingUp by remember { mutableStateOf(false) }
        
        // File saver launcher
        val backupFileSaver = com.davy.ui.util.rememberBackupFileSaver { uri ->
            if (uri != null) {
                isBackingUp = true
                scope.launch {
                    val result = onCreateBackup(account.id)
                    when (result) {
                        is com.davy.domain.manager.BackupRestoreManager.BackupResult.Success -> {
                            // Write backup to selected location
                            val writeSuccess = com.davy.ui.util.writeBackupToUri(
                                context,
                                uri,
                                result.backupJson
                            )
                            if (writeSuccess) {
                                val filename = com.davy.ui.util.getFileNameFromUri(context, uri)
                                backupResult = filename
                                backupError = null
                            } else {
                                backupError = "Failed to write backup file"
                                backupResult = null
                            }
                        }
                        is com.davy.domain.manager.BackupRestoreManager.BackupResult.Error -> {
                            backupError = result.message
                            backupResult = null
                        }
                    }
                    isBackingUp = false
                }
            } else {
                // User cancelled
                showBackupDialog = false
            }
        }
        
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.backup_settings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isBackingUp -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(stringResource(id = com.davy.R.string.creating_backup))
                            }
                        }
                        backupError != null -> {
                            Text(
                                text = stringResource(id = com.davy.R.string.error) + ": " + backupError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        backupResult != null -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Animated success checkmark
                                com.davy.ui.components.AnimatedSuccessCheck(
                                    size = 64.dp,
                                    strokeWidth = 5.dp
                                )
                                
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(id = com.davy.R.string.full_backup_created_successfully),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = Color(0xFF4CAF50)  // Green
                                    )
                                    Text(
                                        text = stringResource(id = com.davy.R.string.saved_as) + " " + backupResult,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(id = com.davy.R.string.passwords_not_included_backup),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(id = com.davy.R.string.create_account_backup_description))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = com.davy.R.string.choose_where_to_save_backup),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = com.davy.R.string.passwords_not_backed_up_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (backupResult == null && !isBackingUp) {
                    TextButton(
                        onClick = {
                            // Launch file picker with default filename
                            val timestamp = java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date())
                            backupFileSaver.launch("davy_account_${account.id}_backup_$timestamp.json")
                        }
                    ) {
                        Text(stringResource(id = com.davy.R.string.choose_location))
                    }
                } else {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text(stringResource(id = com.davy.R.string.close))
                    }
                }
            },
            dismissButton = {
                if (backupResult == null && !isBackingUp) {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text(stringResource(id = com.davy.R.string.cancel))
                    }
                }
            }
        )
    }
    
    // Restore Dialog with file picker
    if (showRestoreDialog) {
        var restoreResult by remember { mutableStateOf<String?>(null) }
        var restoreError by remember { mutableStateOf<String?>(null) }
        var isRestoring by remember { mutableStateOf(false) }
        
        // File opener launcher
        val backupFileOpener = com.davy.ui.util.rememberBackupFileOpener { uri ->
            if (uri != null) {
                isRestoring = true
                scope.launch {
                    // Read backup from selected file
                    val backupJson = com.davy.ui.util.readBackupFromUri(context, uri)
                    if (backupJson != null) {
                        val result = onRestoreBackup(backupJson)
                        when (result) {
                            is com.davy.domain.manager.BackupRestoreManager.RestoreResult.Success -> {
                                val filename = com.davy.ui.util.getFileNameFromUri(context, uri)
                                val parts = mutableListOf<String>()
                                
                                if (result.accountsRestored > 0) {
                                    parts.add("${result.accountsRestored} account${if (result.accountsRestored != 1) "s" else ""}")
                                }
                                if (result.settingsRestored) {
                                    parts.add("app settings")
                                }
                                
                                restoreResult = if (parts.isNotEmpty()) {
                                    "Restored from $filename:\n${parts.joinToString(" and ")}"
                                } else {
                                    "Restored from $filename (no changes needed)"
                                }
                                restoreError = null
                            }
                            is com.davy.domain.manager.BackupRestoreManager.RestoreResult.Error -> {
                                restoreError = result.message
                                restoreResult = null
                            }
                        }
                    } else {
                        restoreError = "Failed to read backup file"
                        restoreResult = null
                    }
                    isRestoring = false
                }
            } else {
                // User cancelled
                showRestoreDialog = false
            }
        }
        
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.restore_settings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isRestoring -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(stringResource(id = com.davy.R.string.restoring_settings))
                            }
                        }
                        restoreError != null -> {
                            Text(
                                text = stringResource(id = com.davy.R.string.error) + ": " + restoreError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        restoreResult != null -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Animated success checkmark
                                com.davy.ui.components.AnimatedSuccessCheck(
                                    size = 64.dp,
                                    strokeWidth = 5.dp
                                )
                                
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(id = com.davy.R.string.settings_restored_successfully),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = Color(0xFF4CAF50)  // Green
                                    )
                                    Text(
                                        text = restoreResult!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(id = com.davy.R.string.remember_reenter_passwords),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(id = com.davy.R.string.select_backup_file_to_restore_account))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = com.davy.R.string.choose_backup_file_from_device),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = com.davy.R.string.passwords_not_stored_in_backups),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (restoreResult == null && !isRestoring) {
                    TextButton(
                        onClick = {
                            // Launch file picker for JSON files
                            backupFileOpener.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    ) {
                        Text(stringResource(id = com.davy.R.string.choose_file))
                    }
                } else {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(stringResource(id = com.davy.R.string.close))
                    }
                }
            },
            dismissButton = {
                if (restoreResult == null && !isRestoring) {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(stringResource(id = com.davy.R.string.cancel))
                    }
                }
            }
        )
    }
    
    // Certificate selection dialog
    if (showCertificateDialog) {
        val dialogContext = LocalContext.current
        
        AlertDialog(
            onDismissRequest = { showCertificateDialog = false },
            title = { Text(stringResource(id = com.davy.R.string.client_certificate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(id = com.davy.R.string.select_client_certificate_for_auth))
                    
                    // Option 1: Choose from Android KeyChain
                    OutlinedButton(
                        onClick = {
                            val activity = dialogContext as Activity
                            showCertificateDialog = false
                            KeyChain.choosePrivateKeyAlias(
                                activity,
                                { alias ->
                                    // Update certificate path with selected alias
                                    if (alias != null) {
                                        certificatePath = alias
                                    } else {
                                        // Show snackbar when no certificate selected (or none installed)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = context.getString(com.davy.R.string.no_certificate_selected),
                                                actionLabel = context.getString(com.davy.R.string.install_certificate),
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
                                certificatePath.ifBlank { null } // alias to preselect
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(id = com.davy.R.string.choose_from_system_certificates))
                    }
                    
                    HorizontalDivider()
                    
                    // Option 2: Manual entry
                    Text(
                        stringResource(id = com.davy.R.string.enter_certificate_alias_manually),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = certificatePath,
                        onValueChange = { certificatePath = it },
                        singleLine = true,
                        label = { Text(stringResource(id = com.davy.R.string.certificate_alias)) },
                        placeholder = { Text(stringResource(id = com.davy.R.string.enter_certificate_alias)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCertificateDialog = false }) {
                    Text(stringResource(id = com.davy.R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    certificatePath = ""
                    showCertificateDialog = false
                }) {
                    Text(stringResource(id = com.davy.R.string.remove))
                }
            }
        )
    }
        
        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Button state for animated transitions
 */
private enum class ButtonState {
    Normal,
    Loading,
    Success,
    Failure
}

// --- Helpers for interval and event age labels ---
@Composable
private fun intervalLabel(intervalMinutes: Int): String {
    return when (intervalMinutes) {
        -1 -> stringResource(id = com.davy.R.string.manual)
        in 1..59 -> pluralStringResource(
            id = com.davy.R.plurals.duration_minutes,
            count = intervalMinutes,
            intervalMinutes
        )
        in 60..1439 -> {
            val hours = intervalMinutes / 60
            pluralStringResource(id = com.davy.R.plurals.duration_hours, count = hours, hours)
        }
        else -> {
            val days = intervalMinutes / 1440
            if (days % 365 == 0) {
                val years = days / 365
                pluralStringResource(id = com.davy.R.plurals.duration_years, count = years, years)
            } else {
                pluralStringResource(id = com.davy.R.plurals.duration_days, count = days, days)
            }
        }
    }
}

private val syncIntervals = listOf(-1, 15, 30, 60, 120, 240, 720, 1440)

@Composable
private fun eventAgeLabel(days: Int): String {
    return when (days) {
        -1 -> stringResource(id = com.davy.R.string.all_events)
        365 -> pluralStringResource(id = com.davy.R.plurals.duration_years, count = 1, 1)
        730 -> pluralStringResource(id = com.davy.R.plurals.duration_years, count = 2, 2)
        else -> pluralStringResource(id = com.davy.R.plurals.duration_days, count = days, days)
    }
}
