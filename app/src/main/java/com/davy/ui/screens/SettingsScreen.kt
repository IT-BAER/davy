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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            SettingsHeader(text = "Synchronization")
            
            SwitchSettingItem(
                icon = Icons.Default.Sync,
                title = "Auto-sync",
                subtitle = "Automatically sync calendars and contacts",
                checked = autoSyncEnabled,
                onCheckedChange = viewModel::updateAutoSync
            )
            
            SwitchSettingItem(
                icon = Icons.Default.Wifi,
                title = "WiFi only",
                subtitle = "Only sync when connected to WiFI",
                checked = syncOnWifiOnly,
                onCheckedChange = viewModel::updateSyncOnWifiOnly
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Battery
            SettingsHeader(text = "Battery")
            
            ClickableSettingItemWithStatus(
                icon = if (isBatteryOptimizationDisabled) Icons.Default.BatteryFull else Icons.Default.BatterySaver,
                title = "Battery optimization",
                subtitle = "Configure battery restrictions for reliable sync",
                status = if (isBatteryOptimizationDisabled) "Unrestricted" else "Optimized",
                isStatusGood = isBatteryOptimizationDisabled,
                onClick = {
                    // Launch battery optimization settings using ActivityResultLauncher
                    batteryOptimizationLauncher.launch(context.packageName)
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Permissions
            SettingsHeader(text = "Permissions")
            
            ClickableSettingItem(
                icon = Icons.Default.Security,
                title = "View permissions",
                subtitle = "Check granted permissions and manage them",
                onClick = { showPermissionsDialog = true }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Appearance
            SettingsHeader(text = "Appearance")
            
            SwitchSettingItem(
                icon = Icons.Default.DarkMode,
                title = "Dark mode",
                subtitle = "Use dark theme",
                checked = isDarkMode,
                onCheckedChange = viewModel::toggleDarkMode
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Backup & Restore
            SettingsHeader(text = "Backup & Restore")
            
            ClickableSettingItem(
                icon = Icons.Default.Save,
                title = "Backup all settings",
                subtitle = "Create a backup of all accounts and app settings",
                onClick = { showBackupDialog = true }
            )
            
            ClickableSettingItem(
                icon = Icons.Default.Upload,
                title = "Restore from backup",
                subtitle = "Restore accounts and settings from a backup file",
                onClick = { showRestoreDialog = true }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Developer
            SettingsHeader(text = "Developer")
            
            SwitchSettingItem(
                icon = Icons.Default.BugReport,
                title = "Debug logging",
                subtitle = "Enable detailed logging for troubleshooting (excludes passwords)",
                checked = debugLoggingEnabled,
                onCheckedChange = viewModel::updateDebugLogging
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // About
            SettingsHeader(text = "About")
            
            ClickableSettingItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0",
                onClick = { /* Show version details */ }
            )
            
            ClickableSettingItem(
                icon = Icons.Default.Description,
                title = "License",
                subtitle = "Apache 2.0 - Open source software",
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
            title = { Text("App Permissions") },
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
                    Text("Manage")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Close")
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
            title = { Text("Backup All Settings") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    when {
                        isBackingUp -> {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("Creating backup...")
                            }
                        }
                        backupError != null -> {
                            Text(
                                text = "Error: $backupError",
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
                                        text = "Full Backup Created Successfully!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = Color(0xFF4CAF50)  // Green
                                    )
                                    Text(
                                        text = "Saved as: $backupResult",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "⚠ Passwords are NOT included in backups for security reasons.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                        else -> {
                            Text("Create a full backup of all accounts, app settings, and configurations.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You will choose where to save the backup file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠ Passwords will NOT be backed up for security reasons.",
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
                        Text("Choose Location")
                    }
                } else {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (backupResult == null && !isBackingUp) {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("Cancel")
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
            title = { Text("Restore Settings") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    when {
                        isRestoring -> {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("Restoring settings...")
                            }
                        }
                        restoreError != null -> {
                            Text(
                                text = "Error: $restoreError",
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
                                        text = "Settings Restored Successfully!",
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
                                        text = "⚠ Remember to re-enter passwords for your accounts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                        else -> {
                            Text("Select a backup file to restore all accounts, app settings, and configurations.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You will choose a backup file from your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠ Passwords are not stored in backups and must be re-entered.",
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
                        Text("Choose File")
                    }
                } else {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (restoreResult == null && !isRestoring) {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text("Cancel")
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
