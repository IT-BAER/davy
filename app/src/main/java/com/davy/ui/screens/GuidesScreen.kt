package com.davy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidesScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Guides") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(16.dp)
        ) {
            // Getting Started
            Text(
                text = "Getting Started",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. Add an account by tapping the + button\n" +
                        "2. Enter your CalDAV/CardDAV server URL\n" +
                        "3. Provide your username and password\n" +
                        "4. DAVy will discover available calendars and contacts",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            // Managing Calendars
            Text(
                text = "Managing Calendars",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Tap on an account to view calendars\n" +
                        "• Use 'Get Lists' to refresh available calendars\n" +
                        "• Sync individual calendars or use 'Sync All'\n" +
                        "• Create new calendars with the + button\n" +
                        "• Long-press calendars to delete them",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            // Contacts
            Text(
                text = "Managing Contacts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Switch to Contacts tab to view address books\n" +
                        "• Contacts sync automatically based on settings\n" +
                        "• All synced contacts appear in Android Contacts",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            // Account Settings
            Text(
                text = "Account Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Tap the settings icon on account details\n" +
                        "• Configure authentication credentials\n" +
                        "• Enable WiFi-only sync\n" +
                        "• Set calendar color preferences\n" +
                        "• Choose contact group method\n" +
                        "• Skip old calendar events",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sync
            Text(
                text = "Syncing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• 'Sync All' syncs calendars, contacts, and tasks\n" +
                        "• Individual sync buttons for each resource type\n" +
                        "• Auto-sync can be enabled in Android Settings\n" +
                        "• WiFi-only option available in account settings",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
