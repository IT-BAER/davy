package com.davy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davy.domain.model.Calendar
import com.davy.ui.viewmodel.CalendarListUiState
import com.davy.ui.viewmodel.CalendarListViewModel
import java.text.SimpleDateFormat
import java.util.*
import timber.log.Timber

/**
 * Screen for viewing calendars.
 * Shows list of calendars for the selected account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit = {},
    viewModel: CalendarListViewModel = hiltViewModel()
) {
    Timber.d("CalendarListScreen COMPOSING...")
    
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isBusy = isSyncing
    
    Timber.d("UI State collected: %s", uiState)
    
    val pullToRefreshState = rememberPullToRefreshState()
    var showSyncMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(pullToRefreshState.isRefreshing, isBusy) {
        if (pullToRefreshState.isRefreshing) {
            if (!isBusy) {
                viewModel.refresh()
                // Simulate refresh completion after a delay
                kotlinx.coroutines.delay(1000)
            }
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = com.davy.R.string.calendars)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = com.davy.R.string.back)
                        )
                    }
                },
                actions = {
                    // Sync menu
                    IconButton(onClick = { showSyncMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(id = com.davy.R.string.sync)
                        )
                    }
                    DropdownMenu(
                        expanded = showSyncMenu,
                        onDismissRequest = { showSyncMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = com.davy.R.string.sync_calendars_only)) },
                            onClick = {
                                showSyncMenu = false
                                viewModel.syncCalendarsOnly()
                            },
                            enabled = !isBusy,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    
                    // Refresh button
                    IconButton(onClick = viewModel::refresh, enabled = !isBusy) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = com.davy.R.string.refresh)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            when (val state = uiState) {
                is CalendarListUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is CalendarListUiState.NoAccount -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = com.davy.R.string.no_account_configured),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = com.davy.R.string.please_add_account_first),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is CalendarListUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = com.davy.R.string.no_calendars_found),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = com.davy.R.string.sync_with_server_to_fetch_calendars),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is CalendarListUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.calendars) { calendar ->
                            CalendarItemCard(
                                calendar = calendar,
                                onClick = { cal ->
                                    onNavigateToDetails(cal.id)
                                },
                                onSyncToggle = { cal, enabled ->
                                    viewModel.toggleSync(cal, enabled)
                                },
                                onVisibilityToggle = { cal, visible ->
                                    viewModel.toggleVisibility(cal, visible)
                                },
                                onSyncNow = { cal ->
                                    viewModel.syncNow(cal)
                                },
                                onResetSyncAndSync = { cal ->
                                    viewModel.resetSyncAndSync(cal)
                                },
                                onColorChange = { cal, color ->
                                    viewModel.changeColor(cal, color)
                                },
                                isBusy = isBusy
                            )
                        }
                    }
                }
                is CalendarListUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = com.davy.R.string.error) + ": ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(id = com.davy.R.string.retry))
                        }
                    }
                }
            }
            
            // Pull-to-refresh container
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun CalendarItemCard(
    calendar: Calendar,
    onClick: (Calendar) -> Unit = { },
    onSyncToggle: (Calendar, Boolean) -> Unit = { _, _ -> },
    onVisibilityToggle: (Calendar, Boolean) -> Unit = { _, _ -> },
    onSyncNow: (Calendar) -> Unit = { },
    onResetSyncAndSync: (Calendar) -> Unit = { },
    onColorChange: (Calendar, Int) -> Unit = { _, _ -> },
    isBusy: Boolean = false
) {
    var showColorPicker by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(calendar) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator (clickable to change color)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(calendar.color),
                            shape = CircleShape
                        )
                        .clickable { showColorPicker = true }
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = calendar.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (calendar.description != null) {
                        Text(
                            text = calendar.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (calendar.lastSyncedAt != null) {
                        Text(
                            text = stringResource(id = com.davy.R.string.last_sync, formatDate(calendar.lastSyncedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sync and visibility toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sync toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = com.davy.R.string.sync),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = calendar.syncEnabled,
                        onCheckedChange = { enabled ->
                            Timber.d("SWITCH TOGGLED: %s -> enabled=%s", calendar.displayName, enabled)
                            onSyncToggle(calendar, enabled)
                        },
                        enabled = !isBusy,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
                
                // Visibility toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = com.davy.R.string.visible),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = calendar.visible,
                        onCheckedChange = { visible ->
                            onVisibilityToggle(calendar, visible)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            // Sync Now button
            if (calendar.syncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSyncNow(calendar) },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = stringResource(id = com.davy.R.string.sync_now),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = com.davy.R.string.sync_now))
                }
                
                // Reset & Full Sync button (for debugging sync issues)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onResetSyncAndSync(calendar) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = com.davy.R.string.reset_full_sync))
                }
            }
            
            // Status chips
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (calendar.isSyncedWithAndroid()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            stringResource(id = com.davy.R.string.synced_with_android),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
    
    // Color picker dialog
    if (showColorPicker) {
        com.davy.ui.components.ColorPickerDialog(
            currentColor = Color(calendar.color),
            onColorSelected = { newColor ->
                onColorChange(calendar, newColor.toArgb())
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

