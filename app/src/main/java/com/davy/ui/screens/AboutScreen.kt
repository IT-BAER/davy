package com.davy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davy.R

/**
 * Full screen about page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo - same as main menu header
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "DAVy Logo",
                modifier = Modifier.size(80.dp)
            )
            
            // App name
            Text(
                "DAVy",
                style = MaterialTheme.typography.headlineLarge
            )
            
            // Version
            Text(
                "Version 1.0.0",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Description
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Description",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "DAVy is a modern CalDAV, CardDAV, and WebCal synchronization app for Android. " +
                    "Keep your calendars, contacts, and tasks in sync across all your devices.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Tech Stack
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Technology Stack",
                    style = MaterialTheme.typography.titleLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TechStackItem("• Kotlin & Jetpack Compose")
                    TechStackItem("• Material 3 Design")
                    TechStackItem("• Hilt Dependency Injection")
                    TechStackItem("• Room Database")
                    TechStackItem("• Coroutines & Flow")
                    TechStackItem("• OkHttp & Retrofit")
                    TechStackItem("• Sardine WebDAV")
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Dependencies
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Key Dependencies",
                    style = MaterialTheme.typography.titleLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TechStackItem("• AndroidX Compose")
                    TechStackItem("• Timber Logging")
                    TechStackItem("• iCal4j (RFC 5545)")
                    TechStackItem("• ez-vcard")
                    TechStackItem("• WorkManager")
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // License
            Text(
                "License: Apache 2.0",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TechStackItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}
