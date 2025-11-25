package com.davy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davy.R

/**
 * Privacy Policy screen displaying the app's privacy policy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.privacy_policy)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Effective Date
            Text(
                text = stringResource(id = R.string.privacy_effective_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Introduction
            PolicySection(
                title = stringResource(id = R.string.privacy_introduction_title),
                content = stringResource(id = R.string.privacy_introduction_content)
            )
            
            // Information We Collect
            PolicySection(
                title = stringResource(id = R.string.privacy_info_collect_title),
                content = stringResource(id = R.string.privacy_info_collect_content)
            )
            
            // How We Use Information
            PolicySection(
                title = stringResource(id = R.string.privacy_how_use_title),
                content = stringResource(id = R.string.privacy_how_use_content)
            )
            
            // Data Storage and Security
            PolicySection(
                title = stringResource(id = R.string.privacy_data_storage_title),
                content = stringResource(id = R.string.privacy_data_storage_content)
            )
            
            // Data Sharing
            PolicySection(
                title = stringResource(id = R.string.privacy_data_sharing_title),
                content = stringResource(id = R.string.privacy_data_sharing_content)
            )
            
            // Your Rights
            PolicySection(
                title = stringResource(id = R.string.privacy_your_rights_title),
                content = stringResource(id = R.string.privacy_your_rights_content)
            )
            
            // Permissions
            PolicySection(
                title = stringResource(id = R.string.privacy_permissions_title),
                content = stringResource(id = R.string.privacy_permissions_content)
            )
            
            // Third-Party Services
            PolicySection(
                title = stringResource(id = R.string.privacy_third_party_title),
                content = stringResource(id = R.string.privacy_third_party_content)
            )
            
            // Children's Privacy
            PolicySection(
                title = stringResource(id = R.string.privacy_children_title),
                content = stringResource(id = R.string.privacy_children_content)
            )
            
            // Contact Information
            PolicySection(
                title = stringResource(id = R.string.privacy_contact_title),
                content = stringResource(id = R.string.privacy_contact_content)
            )
            
            // Last Updated
            Text(
                text = stringResource(id = R.string.privacy_last_updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
