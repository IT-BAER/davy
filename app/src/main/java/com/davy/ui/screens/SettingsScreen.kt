package com.davy.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncOnWifiOnly by viewModel.syncOnWifiOnly.collectAsState()
    val debugLoggingEnabled by viewModel.debugLoggingEnabled.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isBatteryOptimizationDisabled by viewModel.isBatteryOptimizationDisabled.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    
    var showPermissionsDialog by remember { mutableStateOf(false) }
    
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
