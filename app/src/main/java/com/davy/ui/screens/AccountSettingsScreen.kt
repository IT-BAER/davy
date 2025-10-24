package com.davy.ui.screens

import android.app.Activity
import android.security.KeyChain
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davy.ui.viewmodels.AccountDetailViewModel
import com.davy.ui.util.rememberDebounced
import kotlinx.coroutines.Dispatchers
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
                title = { Text("Account Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                }
            )
        }
    }
}

@Composable
private fun AccountSettingsContent(
    account: com.davy.domain.model.Account,
    modifier: Modifier = Modifier,
    onUpdateAccount: (com.davy.domain.model.Account, String?) -> Unit,
    onRenameAccount: (String) -> Unit,
    onUpdateSyncConfiguration: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDeleteOldEvents: (Int) -> Unit
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
    
    // Debounce user input to prevent UI thread blocking on every keystroke
    val debouncedUsername = rememberDebounced(username, delayMillis = 500L)
    val debouncedCertificatePath = rememberDebounced(certificatePath, delayMillis = 500L)
    val debouncedPassword = rememberDebounced(password, delayMillis = 500L)
    
    // Save username changes with debouncing
    LaunchedEffect(debouncedUsername) {
        if (debouncedUsername != account.username) {
            withContext(Dispatchers.IO) {
                val updatedAccount = account.copy(username = debouncedUsername)
                onUpdateAccount(updatedAccount, null)
            }
        }
    }
    
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
    
    // Save password changes with debouncing (only when not empty)
    LaunchedEffect(debouncedPassword) {
        if (debouncedPassword.isNotBlank()) {
            withContext(Dispatchers.IO) {
                onUpdateAccount(account, debouncedPassword)
            }
        }
    }
    
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
    var showExportDialog by remember { mutableStateOf(false) }
    
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
            text = "Account Information",
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
                contentDescription = "Rename account",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = "Server: ${account.serverUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Authentication Section
        Text(
            text = "Authentication",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (leave empty to keep current)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )
        
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
                        text = "Client certificate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (certificatePath.isNotBlank()) certificatePath else "None",
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
            text = "Sync Settings",
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
                    text = "Sync only on WiFi",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Disable mobile data sync",
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
                        text = "Calendar sync interval",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = syncIntervalOptions.find { it.first == calendarSyncInterval }?.second ?: "Unknown",
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
                        text = "Contact sync interval",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = syncIntervalOptions.find { it.first == contactSyncInterval }?.second ?: "Unknown",
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
                        text = "WebCal sync interval",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = syncIntervalOptions.find { it.first == webCalSyncInterval }?.second ?: "Unknown",
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
            text = "CalDAV Settings",
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
                        text = "Skip events older than",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = eventAgeDays.find { it.first == skipEventsDays }?.second ?: "All events",
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
                    text = "Manage calendar colors",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Use server-side calendar colors",
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
                    text = "Event colors",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Use colors from individual events",
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
            text = "CardDAV Settings",
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
                        text = "Contact Group Method",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = contactGroupMethod,
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
            text = "Account Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Export Settings
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showExportDialog = true }
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
                        text = "Export Settings",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Export account configuration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        text = "Delete Account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Permanently remove this account",
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
            title = { Text("Contact Group Method") },
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
                            Text(method)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupMethodDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Event Age Dialog
    if (showEventAgeDialog) {
        AlertDialog(
            onDismissRequest = { showEventAgeDialog = false },
            title = { Text("Skip events older than") },
            text = {
                Column {
                    eventAgeDays.forEach { (days, label) ->
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
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEventAgeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Calendar Sync Interval Dialog
    if (showCalendarIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarIntervalDialog = false },
            title = { Text("Calendar sync interval") },
            text = {
                Column {
                    syncIntervalOptions.forEach { (interval, label) ->
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
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Contact Sync Interval Dialog
    if (showContactIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showContactIntervalDialog = false },
            title = { Text("Contact sync interval") },
            text = {
                Column {
                    syncIntervalOptions.forEach { (interval, label) ->
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
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContactIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // WebCal Sync Interval Dialog
    if (showWebCalIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showWebCalIntervalDialog = false },
            title = { Text("WebCal sync interval") },
            text = {
                Column {
                    syncIntervalOptions.forEach { (interval, label) ->
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
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWebCalIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Rename Account Dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(account.accountName) }
        
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Account") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Account Name") },
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
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Account Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete '${account.accountName}'? This will remove all calendars, contacts, and tasks associated with this account.") },
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
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Cancel button on the right
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = null
        )
    }
    
    // Export Settings Dialog
    if (showExportDialog) {
        var exportResult by remember { mutableStateOf<String?>(null) }
        var exportError by remember { mutableStateOf<String?>(null) }
        var isExporting by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Settings") },
            text = {
                Column {
                    when {
                        isExporting -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("Exporting settings...")
                            }
                        }
                        exportError != null -> {
                            Text(
                                text = "Error: $exportError",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        exportResult != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Settings exported successfully!")
                                Text(
                                    text = "File: $exportResult",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            Text("Export feature not yet implemented")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(if (isExporting) "Cancel" else "Close")
                }
            }
        )
    }
    
    // Certificate selection dialog
    if (showCertificateDialog) {
        val dialogContext = LocalContext.current
        
        AlertDialog(
            onDismissRequest = { showCertificateDialog = false },
            title = { Text("Client Certificate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a client certificate for authentication:")
                    
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
                                                message = "No certificate selected",
                                                actionLabel = "Install Certificate",
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
                        Text("Choose from System Certificates")
                    }
                    
                    HorizontalDivider()
                    
                    // Option 2: Manual entry
                    Text(
                        "Or enter certificate alias manually:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = certificatePath,
                        onValueChange = { certificatePath = it },
                        singleLine = true,
                        label = { Text("Certificate alias") },
                        placeholder = { Text("Enter certificate alias") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCertificateDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    certificatePath = ""
                    showCertificateDialog = false
                }) {
                    Text("Remove")
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
