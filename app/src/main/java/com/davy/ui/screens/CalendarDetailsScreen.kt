package com.davy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davy.domain.model.Calendar
import com.davy.ui.viewmodel.CalendarDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*
import timber.log.Timber

/**
 * Full-page calendar details and settings screen.
 * Provides all calendar management options in a scrollable view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDetailsScreen(
    calendarId: Long,
    onNavigateBack: () -> Unit,
    viewModel: CalendarDetailsViewModel = hiltViewModel()
) {
    // Load calendar data
    LaunchedEffect(calendarId) {
        viewModel.loadCalendar(calendarId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar Settings") },
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
    ) { padding ->
        when (val state = uiState) {
            is CalendarDetailsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is CalendarDetailsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is CalendarDetailsUiState.Success -> {
                CalendarDetailsContent(
                    calendar = state.calendar,
                    modifier = Modifier.padding(padding),
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun CalendarDetailsContent(
    calendar: Calendar,
    modifier: Modifier = Modifier,
    viewModel: CalendarDetailsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Local state for settings (synced with calendar state)
    var wifiOnlySync by remember(calendar) { mutableStateOf(calendar.wifiOnlySync) }
    var forceReadOnly by remember(calendar) { mutableStateOf(calendar.forceReadOnly) }
    
    // Apply changes immediately
    LaunchedEffect(wifiOnlySync) {
        if (wifiOnlySync != calendar.wifiOnlySync) {
            viewModel.updateSettings(calendar, wifiOnlySync, forceReadOnly)
        }
    }
    
    LaunchedEffect(forceReadOnly) {
        if (forceReadOnly != calendar.forceReadOnly) {
            viewModel.updateSettings(calendar, wifiOnlySync, forceReadOnly)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Calendar header with color and name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator (clickable to change color)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(calendar.color),
                        shape = CircleShape
                    )
                    .clickable { showColorPicker = true }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                if (calendar.lastSyncedAt != null) {
                    Text(
                        text = "Last synced: ${formatDate(calendar.lastSyncedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Never synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Calendar information section
        Text(
            text = "Calendar Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        InfoRow("URL", calendar.calendarUrl)
        if (calendar.description != null) {
            InfoRow("Description", calendar.description)
        }
        if (calendar.timezone != null) {
            InfoRow("Timezone", calendar.timezone)
        }
        if (calendar.syncToken != null) {
            InfoRow("Sync Token", calendar.syncToken)
        }
        InfoRow("Permissions", buildString {
            if (calendar.privWriteContent) append("Write ")
            if (calendar.privUnbind) append("Delete")
            if (calendar.forceReadOnly) append(" (Read-Only Forced)")
        })
        if (calendar.source != null) {
            InfoRow("Source", calendar.source)
        }
        if (calendar.owner != null) {
            val ownerName = calendar.owner
                .substringBeforeLast("/")
                .substringAfterLast("/")
                .takeIf { it.isNotEmpty() } ?: "Unknown"
            InfoRow("Owner", ownerName)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Sync settings section
        Text(
            text = "Sync Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        // Sync toggle as OutlinedCard
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { 
                Timber.d("🔴 CARD CLICKED: %s -> toggling sync to %s", calendar.displayName, !calendar.syncEnabled)
                viewModel.toggleSync(calendar, !calendar.syncEnabled) 
            }
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
                        text = "Sync Enabled",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (calendar.syncEnabled) "Automatic sync enabled" else "Sync disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = calendar.syncEnabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }
        }

        // WiFi-only sync toggle as OutlinedCard
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { wifiOnlySync = !wifiOnlySync }
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
                        text = "WiFi-only Sync",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (wifiOnlySync) "Sync only on WiFi" else "Sync on any network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = wifiOnlySync,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2E7D32),
                        checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                    )
                )
            }
        }

        // Read-Only toggle as OutlinedCard
        val isServerReadOnly = !calendar.privWriteContent
        val canChangeReadOnly = calendar.privWriteContent
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (canChangeReadOnly) forceReadOnly = !forceReadOnly }
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
                        text = "Read-Only",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isServerReadOnly) {
                            "Server enforced (cannot write)"
                        } else if (forceReadOnly) {
                            "Changes prevented"
                        } else {
                            "Changes allowed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isServerReadOnly || forceReadOnly,
                    onCheckedChange = null,
                    enabled = canChangeReadOnly,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.error,
                        checkedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        disabledCheckedThumbColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        disabledCheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Sync Now button (moved inside Sync Settings section)
        if (calendar.syncEnabled) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete button on the left
                val deleteEnabled = calendar.canDelete()
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = deleteEnabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete calendar")
                }
                
                // Sync Now button on the right
                OutlinedButton(
                    onClick = { viewModel.syncNow(calendar) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync now",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }
        }

        // Status chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (calendar.isSyncedWithAndroid()) {
                StatusChip("Synced with Android")
            }
            if (calendar.supportsVTODO) {
                StatusChip("Supports Tasks")
            }
            if (calendar.supportsVJOURNAL) {
                StatusChip("Supports Journal")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }

    // Color picker dialog
    if (showColorPicker) {
        com.davy.ui.components.ColorPickerDialog(
            currentColor = Color(calendar.color),
            onColorSelected = { newColor ->
                viewModel.changeColor(calendar, newColor.hashCode())
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Calendar?") },
            text = {
                Column {
                    Text("Are you sure you want to delete \"${calendar.displayName}\"?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will delete the calendar from both the server and your device. All events in this calendar will be permanently removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.deleteCalendar(calendar)
                            showDeleteConfirmation = false
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

sealed class CalendarDetailsUiState {
    object Loading : CalendarDetailsUiState()
    data class Success(val calendar: Calendar) : CalendarDetailsUiState()
    data class Error(val message: String) : CalendarDetailsUiState()
}
