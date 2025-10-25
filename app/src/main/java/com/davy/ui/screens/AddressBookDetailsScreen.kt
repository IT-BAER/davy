package com.davy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davy.domain.model.AddressBook
import com.davy.ui.viewmodel.AddressBookDetailsViewModel
import com.davy.ui.viewmodel.AddressBookDetailsUiState

/**
 * Full-page address book details and settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookDetailsScreen(
    addressBookId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AddressBookDetailsViewModel = hiltViewModel()
) {
    LaunchedEffect(addressBookId) {
        viewModel.loadAddressBook(addressBookId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = com.davy.R.string.address_book_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = com.davy.R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is AddressBookDetailsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AddressBookDetailsUiState.Error -> {
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

            is AddressBookDetailsUiState.Success -> {
                AddressBookDetailsContent(
                    addressBook = state.addressBook,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding),
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun AddressBookDetailsContent(
    addressBook: AddressBook,
    viewModel: AddressBookDetailsViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    var wifiOnlySync by remember { mutableStateOf(addressBook.wifiOnlySync) }
    var syncIntervalMinutes by remember { mutableStateOf(addressBook.syncIntervalMinutes) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var forceReadOnly by remember { mutableStateOf(addressBook.forceReadOnly) }

    // Apply changes immediately
    LaunchedEffect(wifiOnlySync) {
        if (wifiOnlySync != addressBook.wifiOnlySync) {
            viewModel.updateSettings(addressBook, wifiOnlySync, syncIntervalMinutes)
        }
    }
    
    LaunchedEffect(syncIntervalMinutes) {
        if (syncIntervalMinutes != addressBook.syncIntervalMinutes) {
            viewModel.updateSettings(addressBook, wifiOnlySync, syncIntervalMinutes)
        }
    }
    LaunchedEffect(forceReadOnly) {
        if (forceReadOnly != addressBook.forceReadOnly) {
            viewModel.updateReadOnly(addressBook, forceReadOnly)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Address Book header with name
        Text(
            text = addressBook.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Address Book Information
        Text(
            text = stringResource(id = com.davy.R.string.address_book_information),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        InfoRow(stringResource(id = com.davy.R.string.url), addressBook.url)
        if (addressBook.owner != null) {
            val ownerName = addressBook.owner
                .substringBeforeLast("/")
                .substringAfterLast("/")
                .takeIf { it.isNotEmpty() } ?: stringResource(id = com.davy.R.string.unknown)
            InfoRow(stringResource(id = com.davy.R.string.owner), ownerName)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Sync Settings
        Text(
            text = stringResource(id = com.davy.R.string.settings_sync_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        // Sync Enabled toggle
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.toggleSync(addressBook, !addressBook.syncEnabled) }
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
                        text = stringResource(id = com.davy.R.string.sync_enabled),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (addressBook.syncEnabled) stringResource(id = com.davy.R.string.automatic_sync_enabled) else stringResource(id = com.davy.R.string.sync_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = addressBook.syncEnabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }
        }

        // WiFi-only sync toggle
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
                        text = stringResource(id = com.davy.R.string.sync_on_wifi_only),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (wifiOnlySync) stringResource(id = com.davy.R.string.wifi_only_sync_on) else stringResource(id = com.davy.R.string.wifi_only_sync_off),
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

        // Read-Only toggle (parity with Calendar)
        val isServerReadOnly = !addressBook.privWriteContent
        val canChangeReadOnly = addressBook.privWriteContent
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
                        text = stringResource(id = com.davy.R.string.read_only),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isServerReadOnly) {
                            stringResource(id = com.davy.R.string.server_enforced_cannot_write)
                        } else if (forceReadOnly) {
                            stringResource(id = com.davy.R.string.changes_prevented)
                        } else {
                            stringResource(id = com.davy.R.string.changes_allowed)
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Sync Now button
        if (addressBook.syncEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete button on the left
                val deleteEnabled = !addressBook.isReadOnly()
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = deleteEnabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = com.davy.R.string.delete_address_book))
                }
                
                // Sync Now button on the right
                OutlinedButton(
                    onClick = { viewModel.syncNow(addressBook) },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = stringResource(id = com.davy.R.string.sync_now),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = com.davy.R.string.sync_now))
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(id = com.davy.R.string.delete_address_book_question)) },
            text = {
                Column {
                    Text(stringResource(id = com.davy.R.string.delete_address_book_confirmation_named, addressBook.displayName))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = com.davy.R.string.delete_address_book_warning),
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
                            viewModel.deleteAddressBook(addressBook)
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
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(id = com.davy.R.string.cancel)) }
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
