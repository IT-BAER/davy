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
 * Terms of Service screen displaying the app's terms of service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.terms_of_service)) },
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
                text = stringResource(id = R.string.tos_effective_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Acceptance of Terms
            TermsSection(
                title = stringResource(id = R.string.tos_acceptance_title),
                content = stringResource(id = R.string.tos_acceptance_content)
            )
            
            // Description of Service
            TermsSection(
                title = stringResource(id = R.string.tos_service_title),
                content = stringResource(id = R.string.tos_service_content)
            )
            
            // User Responsibilities
            TermsSection(
                title = stringResource(id = R.string.tos_responsibilities_title),
                content = stringResource(id = R.string.tos_responsibilities_content)
            )
            
            // Account Security
            TermsSection(
                title = stringResource(id = R.string.tos_security_title),
                content = stringResource(id = R.string.tos_security_content)
            )
            
            // Intellectual Property
            TermsSection(
                title = stringResource(id = R.string.tos_ip_title),
                content = stringResource(id = R.string.tos_ip_content)
            )
            
            // Disclaimer of Warranties
            TermsSection(
                title = stringResource(id = R.string.tos_disclaimer_title),
                content = stringResource(id = R.string.tos_disclaimer_content)
            )
            
            // Limitation of Liability
            TermsSection(
                title = stringResource(id = R.string.tos_liability_title),
                content = stringResource(id = R.string.tos_liability_content)
            )
            
            // Modifications to Terms
            TermsSection(
                title = stringResource(id = R.string.tos_modifications_title),
                content = stringResource(id = R.string.tos_modifications_content)
            )
            
            // Termination
            TermsSection(
                title = stringResource(id = R.string.tos_termination_title),
                content = stringResource(id = R.string.tos_termination_content)
            )
            
            // Contact Information
            TermsSection(
                title = stringResource(id = R.string.tos_contact_title),
                content = stringResource(id = R.string.tos_contact_content)
            )
            
            // Last Updated
            Text(
                text = stringResource(id = R.string.tos_last_updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TermsSection(
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
