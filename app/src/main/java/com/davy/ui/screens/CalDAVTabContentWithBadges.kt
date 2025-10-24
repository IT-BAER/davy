package com.davy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davy.ui.viewmodel.CalendarListViewModel
import com.davy.ui.viewmodel.CalendarListUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unified CalDAV collection display.
 * Shows all CalDAV collections (calendars and task lists) together with type badges.
 * Following reference implementation pattern where each collection shows its supported component types.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CalDAVTabContentWithBadges(
    accountId: Long,
    calendarViewModel: CalendarListViewModel = hiltViewModel(),
    taskViewModel: TaskListViewModel = hiltViewModel()
) {
    val calendarState by calendarViewModel.uiState.collectAsState()
    val taskState by taskViewModel.uiState.collectAsState()
    
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                // Trigger sync for all CalDAV collections
                calendarViewModel.syncAccount(accountId)
                taskViewModel.syncAccount(accountId)
                isRefreshing = false
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // Calendars (with VEVENT support)
        when (calendarState) {
            is CalendarListUiState.Success -> {
                val calendars = (calendarState as CalendarListUiState.Success).calendars
                items(calendars, key = { "cal_${it.id}" }, contentType = { "calendar" }) { calendar ->
                    CalDAVCollectionCard(
                        displayName = calendar.displayName,
                        color = Color(calendar.color),
                        description = calendar.description,
                        lastSynced = calendar.lastSyncedAt,
                        syncEnabled = calendar.syncEnabled,
                        visible = calendar.visible,
                        componentTypes = listOf("Calendar"), // VEVENT
                        isReadOnly = false, // TODO: Add read-only detection
                        onSyncToggle = { enabled ->
                            calendarViewModel.toggleSync(calendar, enabled)
                        },
                        onVisibilityToggle = { visible ->
                            calendarViewModel.toggleVisibility(calendar, visible)
                        },
                        onSyncNow = {
                            calendarViewModel.syncNow(calendar)
                        },
                        onColorChange = { newColor ->
                            calendarViewModel.changeColor(calendar, newColor.toArgb())
                        }
                    )
                }
            }
            else -> {}
        }

        // Task lists (with VTODO support)
        if (taskState.taskLists.isNotEmpty()) {
            items(taskState.taskLists, key = { "task_${it.id}" }) { taskList ->
                val defaultColor = MaterialTheme.colorScheme.primary
                val color = remember(taskList.color) {
                    taskList.color?.let {
                        try {
                            Color(android.graphics.Color.parseColor(it))
                        } catch (e: Exception) {
                            null
                        }
                    }
                } ?: defaultColor
                
                CalDAVCollectionCard(
                    displayName = taskList.displayName,
                    color = color,
                    description = null,
                    lastSynced = taskList.lastSynced,
                    syncEnabled = taskList.syncEnabled,
                    visible = taskList.visible,
                    componentTypes = listOf("Tasks"), // VTODO
                    isReadOnly = false, // TODO: Add read-only detection
                    onSyncToggle = { enabled ->
                        taskViewModel.toggleSync(taskList, enabled)
                    },
                    onVisibilityToggle = { visible ->
                        taskViewModel.toggleVisibility(taskList, visible)
                    },
                    onSyncNow = {
                        taskViewModel.syncNow(taskList)
                    },
                    onColorChange = { newColor ->
                        taskViewModel.changeColor(taskList, newColor.toArgb())
                    }
                )
            }
        }
        }
        
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Unified CalDAV collection card with component type badges.
 * Displays a single CalDAV collection with badges showing supported types.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun CalDAVCollectionCard(
    displayName: String,
    color: Color,
    description: String?,
    lastSynced: Long?,
    syncEnabled: Boolean,
    visible: Boolean,
    componentTypes: List<String>,
    isReadOnly: Boolean,
    onSyncToggle: (Boolean) -> Unit,
    onVisibilityToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onColorChange: (Color) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Color + Name + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = color, shape = CircleShape)
                        .clickable { showColorPicker = true }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Name and badges
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Component type badges
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        componentTypes.forEach { type ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(0.dp)
                            ) {
                                Text(
                                    type,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        if (isReadOnly) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.padding(0.dp)
                            ) {
                                Text(
                                    "Read-only",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
            
            if (lastSynced != null) {
                Text(
                    text = "Last synced: ${formatDate(lastSynced)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 52.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Single sync toggle (visibility removed as per reference implementation pattern)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = onSyncToggle
                )
            }
            
            // Sync Now button
            if (syncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth()
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
    }
    
    // Color picker dialog
    if (showColorPicker) {
        com.davy.ui.components.ColorPickerDialog(
            currentColor = color,
            onColorSelected = onColorChange,
            onDismiss = { showColorPicker = false }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
