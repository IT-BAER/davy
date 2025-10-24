package com.davy.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Dialog that guides users to exclude the app from battery optimization.
 * This is necessary for reliable background sync on Android 6.0+.
 * 
 * See reference implementation: BatteryOptimizationsScreen
 */
@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageName = context.packageName
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Battery Optimization",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "For reliable background sync, DAVy needs to be excluded from battery optimization.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Without this exemption:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                Text(
                    text = "• Sync may be delayed or stopped\n" +
                           "• Calendar events may not update\n" +
                           "• Contact changes may not sync",
                    style = MaterialTheme.typography.bodySmall
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Text(
                        text = "Tap \"Open Settings\" to disable battery optimization for DAVy.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Button(
                    onClick = {
                        val intent = Intent().apply {
                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = Uri.parse("package:$packageName")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to general battery optimization settings
                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(fallbackIntent)
                        }
                        onDismiss()
                    }
                ) {
                    Text("Open Settings")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

/**
 * Card component to show battery optimization warning in settings.
 */
@Composable
fun BatteryOptimizationCard(
    isOptimized: Boolean,
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOptimized) return  // Don't show if already exempted
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Battery Optimization Active",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = "Sync may be unreliable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            
            TextButton(onClick = onOpenDialog) {
                Text("Fix")
            }
        }
    }
}
