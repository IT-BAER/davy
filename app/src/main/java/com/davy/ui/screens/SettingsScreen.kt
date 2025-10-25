package com.davy.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import com.davy.ui.components.clickableWithFeedback
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.davy.BuildConfig
import com.davy.R
import com.davy.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ActivityResultContract for requesting battery optimization exemption.
 */
@SuppressLint("BatteryLife")
object IgnoreBatteryOptimizationsContract : ActivityResultContract<String, Unit?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:$input".toUri()
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Unit? {
        return null
    }
}

/**
 * Settings screen with app configuration options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    backupRestoreViewModel: com.davy.ui.viewmodel.BackupRestoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncOnWifiOnly by viewModel.syncOnWifiOnly.collectAsState()
    val debugLoggingEnabled by viewModel.debugLoggingEnabled.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isBatteryOptimizationDisabled by viewModel.isBatteryOptimizationDisabled.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    
    // Battery optimization launcher
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        IgnoreBatteryOptimizationsContract
    ) {
        // Refresh state after returning from settings
        viewModel.refreshBatteryOptimizationState()
    }
    
    // Refresh battery optimization state and permissions when screen is visible
    LaunchedEffect(Unit) {
        viewModel.refreshBatteryOptimizationState()
        viewModel.refreshPermissions()
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings)) },
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
        ) {
            // Sync Settings
            SettingsHeader(text = stringResource(id = R.string.settings_sync_title))
            
            SwitchSettingItem(
                icon = Icons.Default.Sync,
                title = stringResource(id = R.string.auto_sync),
                subtitle = stringResource(id = R.string.auto_sync_subtitle),
                checked = autoSyncEnabled,
                onCheckedChange = viewModel::updateAutoSync
            )
            
            SwitchSettingItem(
                icon = Icons.Default.Wifi,
                title = stringResource(id = R.string.sync_on_wifi_only),
                subtitle = stringResource(id = R.string.sync_on_wifi_only_subtitle),
                checked = syncOnWifiOnly,
                onCheckedChange = viewModel::updateSyncOnWifiOnly
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Battery
            SettingsHeader(text = stringResource(id = R.string.battery))
            
            ClickableSettingItemWithStatus(
                icon = if (isBatteryOptimizationDisabled) Icons.Default.BatteryFull else Icons.Default.BatterySaver,
                title = stringResource(id = R.string.battery_optimization),
                subtitle = stringResource(id = R.string.battery_optimization_description),
                status = if (isBatteryOptimizationDisabled) stringResource(id = R.string.battery_optimization_disabled) else stringResource(id = R.string.battery_optimization_enabled),
                isStatusGood = isBatteryOptimizationDisabled,
                onClick = {
                    // Launch battery optimization settings using ActivityResultLauncher
                    batteryOptimizationLauncher.launch(context.packageName)
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Permissions
            SettingsHeader(text = stringResource(id = R.string.settings_permissions_title))
            
            ClickableSettingItem(
                icon = Icons.Default.Security,
                title = stringResource(id = R.string.view_permissions),
                subtitle = stringResource(id = R.string.view_permissions_subtitle),
                onClick = { showPermissionsDialog = true }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Appearance
            SettingsHeader(text = stringResource(id = R.string.settings_appearance_title))
            
            SwitchSettingItem(
                icon = Icons.Default.DarkMode,
                title = stringResource(id = R.string.dark_mode),
                subtitle = stringResource(id = R.string.dark_mode_subtitle),
                checked = isDarkMode,
                onCheckedChange = viewModel::toggleDarkMode
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Backup & Restore
            SettingsHeader(text = stringResource(id = R.string.settings_backup_title))
            
            ClickableSettingItem(
                icon = Icons.Default.Save,
                title = stringResource(id = R.string.backup_all_settings),
                subtitle = stringResource(id = R.string.backup_all_settings_subtitle),
                onClick = { showBackupDialog = true }
            )
            
            ClickableSettingItem(
                icon = Icons.Default.Upload,
                title = stringResource(id = R.string.restore_from_backup),
                subtitle = stringResource(id = R.string.restore_from_backup_subtitle),
                onClick = { showRestoreDialog = true }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Developer
            SettingsHeader(text = stringResource(id = R.string.developer))
            
            SwitchSettingItem(
                icon = Icons.Default.BugReport,
                title = stringResource(id = R.string.debug_logging),
                subtitle = stringResource(id = R.string.debug_logging_subtitle),
                checked = debugLoggingEnabled,
                onCheckedChange = viewModel::updateDebugLogging
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // About
            SettingsHeader(text = stringResource(id = R.string.about))
            
            ClickableSettingItem(
                icon = Icons.Default.Info,
                title = stringResource(id = R.string.version_title),
                subtitle = stringResource(id = R.string.version, BuildConfig.VERSION_NAME),
                onClick = { /* Show version details */ }
            )
            
            ClickableSettingItem(
                icon = Icons.Default.Description,
                title = stringResource(id = R.string.license_title),
                subtitle = stringResource(id = R.string.license_description),
                onClick = { 
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://www.apache.org/licenses/LICENSE-2.0")
                        try {
                            context.startActivity(this)
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                }
            )
        }
    }
    
    // Permissions dialog
    if (showPermissionsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text(stringResource(id = R.string.app_permissions)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    permissions.forEach { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (permission.isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (permission.isGranted) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = permission.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = if (permission.isGranted) "✓" else "✗",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (permission.isGranted) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionsDialog = false
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            // Refresh after returning
                            MainScope().launch {
                                delay(500)
                                viewModel.refreshPermissions()
                            }
                        } catch (e: Exception) {
                            // Handle error silently
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.manage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }
    
    // Backup Dialog with file picker
    if (showBackupDialog) {
        var backupResult by remember { mutableStateOf<String?>(null) }
        var backupError by remember { mutableStateOf<String?>(null) }
        var isBackingUp by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        
        // File saver launcher
        val backupFileSaver = com.davy.ui.util.rememberBackupFileSaver { uri ->
            if (uri != null) {
                isBackingUp = true
                scope.launch {
                    val result = backupRestoreViewModel.createFullBackup()
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
                                backupError = context.getString(R.string.failed_to_write_backup_file)
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
            title = { Text(stringResource(id = R.string.backup_all_settings)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    when {
                        isBackingUp -> {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(stringResource(id = R.string.creating_backup))
                            }
                        }
                        backupError != null -> {
                            Text(
                                text = stringResource(id = R.string.error) + ": " + backupError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        backupResult != null -> {
                            Column(
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                // Animated success checkmark
                                com.davy.ui.components.AnimatedSuccessCheck(
                                    size = 64.dp,
                                    strokeWidth = 5.dp
                                )
                                
                                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.full_backup_created_successfully),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = Color(0xFF4CAF50)  // Green
                                    )
                                    Text(
                                        text = stringResource(id = R.string.saved_as) + " " + backupResult!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(id = R.string.passwords_not_included_backup),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(id = R.string.create_full_backup_description))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = R.string.choose_where_to_save_backup),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.passwords_not_backed_up_note),
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
                            backupFileSaver.launch("davy_full_backup_$timestamp.json")
                        }
                    ) {
                        Text(stringResource(id = R.string.choose_location))
                    }
                } else {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            },
            dismissButton = {
                if (backupResult == null && !isBackingUp) {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text(stringResource(id = R.string.cancel))
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
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        
        // File opener launcher
        val backupFileOpener = com.davy.ui.util.rememberBackupFileOpener { uri ->
            if (uri != null) {
                isRestoring = true
                scope.launch {
                    // Read backup from selected file
                    val backupJson = com.davy.ui.util.readBackupFromUri(context, uri)
                    if (backupJson != null) {
                        val result = backupRestoreViewModel.restoreBackup(backupJson, overwriteExisting = true)
                        when (result) {
                            is com.davy.domain.manager.BackupRestoreManager.RestoreResult.Success -> {
                                val filename = com.davy.ui.util.getFileNameFromUri(context, uri)
                                val parts = mutableListOf<String>()

                                if (result.accountsRestored > 0) {
                                    parts.add(
                                        context.resources.getQuantityString(
                                            R.plurals.accounts_restored,
                                            result.accountsRestored,
                                            result.accountsRestored
                                        )
                                    )
                                }
                                if (result.settingsRestored) {
                                    parts.add(context.getString(R.string.app_settings))
                                }

                                val joiner = context.getString(R.string.and_joiner)
                                restoreResult = if (parts.isNotEmpty()) {
                                    context.getString(
                                        R.string.restored_from_filename_with_details,
                                        filename,
                                        parts.joinToString(joiner)
                                    )
                                } else {
                                    context.getString(R.string.restored_from_filename_no_changes, filename)
                                }
                                restoreError = null
                            }
                            is com.davy.domain.manager.BackupRestoreManager.RestoreResult.Error -> {
                                restoreError = result.message
                                restoreResult = null
                            }
                        }
                    } else {
                        restoreError = context.getString(R.string.failed_to_read_backup_file)
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
            title = { Text(stringResource(id = R.string.restore_settings)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    when {
                        isRestoring -> {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(stringResource(id = R.string.restoring_settings))
                            }
                        }
                        restoreError != null -> {
                            Text(
                                text = stringResource(id = R.string.error) + ": " + restoreError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        restoreResult != null -> {
                            Column(
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                // Animated success checkmark
                                com.davy.ui.components.AnimatedSuccessCheck(
                                    size = 64.dp,
                                    strokeWidth = 5.dp
                                )
                                
                                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.settings_restored_successfully),
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
                                        text = stringResource(id = R.string.remember_reenter_passwords),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(id = R.string.select_backup_file_to_restore))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = R.string.choose_backup_file_from_device),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.passwords_not_stored_in_backups),
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
                        Text(stringResource(id = R.string.choose_file))
                    }
                } else {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            },
            dismissButton = {
                if (restoreResult == null && !isRestoring) {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SwitchSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
    )
}

@Composable
private fun ClickableSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickableWithFeedback(onClick = onClick)
    )
}

@Composable
private fun ClickableSettingItemWithStatus(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    status: String,
    isStatusGood: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (isStatusGood) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
        },
        modifier = Modifier.clickableWithFeedback(onClick = onClick)
    )
}
