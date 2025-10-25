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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davy.R
import com.davy.BuildConfig

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
                title = { Text(stringResource(id = R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.content_description_back)
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
                contentDescription = stringResource(id = R.string.content_description_app_logo),
                modifier = Modifier.size(80.dp)
            )
            
            // App name
            Text(
                stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineLarge
            )
            
            // Version
            Text(
                stringResource(id = R.string.version, BuildConfig.VERSION_NAME),
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
                    stringResource(id = R.string.description),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(id = R.string.app_description),
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
                    stringResource(id = R.string.technology_stack),
                    style = MaterialTheme.typography.titleLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TechStackItem(stringResource(id = R.string.tech_kotlin_compose))
                    TechStackItem(stringResource(id = R.string.tech_material3))
                    TechStackItem(stringResource(id = R.string.tech_hilt))
                    TechStackItem(stringResource(id = R.string.tech_room))
                    TechStackItem(stringResource(id = R.string.tech_coroutines))
                    TechStackItem(stringResource(id = R.string.tech_okhttp))
                    TechStackItem(stringResource(id = R.string.tech_sardine))
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Dependencies
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(id = R.string.key_dependencies),
                    style = MaterialTheme.typography.titleLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TechStackItem(stringResource(id = R.string.tech_compose))
                    TechStackItem(stringResource(id = R.string.tech_timber))
                    TechStackItem(stringResource(id = R.string.tech_ical4j))
                    TechStackItem(stringResource(id = R.string.tech_ezvcard))
                    TechStackItem(stringResource(id = R.string.tech_workmanager))
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // License
            Text(
                stringResource(id = R.string.license),
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
