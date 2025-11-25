package com.davy.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davy.BuildConfig
import com.davy.R

private const val PREFS_NAME = "whats_new_prefs"
private const val KEY_LAST_SHOWN_VERSION = "last_shown_version"

/**
 * Check if What's New dialog should be shown (new version installed)
 */
fun shouldShowWhatsNew(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lastShownVersion = prefs.getString(KEY_LAST_SHOWN_VERSION, null)
    val currentVersion = BuildConfig.VERSION_NAME
    
    // Show if version changed (not on first install - that shows onboarding)
    return lastShownVersion != null && lastShownVersion != currentVersion
}

/**
 * Mark What's New as shown for current version
 */
fun markWhatsNewShown(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_LAST_SHOWN_VERSION, BuildConfig.VERSION_NAME).apply()
}

/**
 * Initialize the version tracking (call on first app launch after onboarding)
 */
fun initializeVersionTracking(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getString(KEY_LAST_SHOWN_VERSION, null) == null) {
        prefs.edit().putString(KEY_LAST_SHOWN_VERSION, BuildConfig.VERSION_NAME).apply()
    }
}

/**
 * Reset the last shown version to force showing What's New dialog (for testing)
 */
fun resetWhatsNewVersion(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Set to a fake old version so the dialog shows
    prefs.edit().putString(KEY_LAST_SHOWN_VERSION, "0.0.0").apply()
}

/**
 * Get changelog content for a specific version
 */
@Composable
fun getChangelogForVersion(version: String): String {
    // Map version to changelog string resource
    return when {
        version.startsWith("1.0.1") -> stringResource(id = R.string.whats_new_v1_0_1)
        version.startsWith("1.0.0") -> stringResource(id = R.string.whats_new_v1_0_0)
        // Add more versions as needed:
        // version.startsWith("1.1") -> stringResource(id = R.string.whats_new_v1_1_0)
        else -> stringResource(id = R.string.whats_new_v1_0_1) // Default to latest
    }
}

/**
 * What's New dialog shown after app updates
 */
@Composable
fun WhatsNewDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    val changelog = getChangelogForVersion(versionName)
    
    AlertDialog(
        onDismissRequest = {
            markWhatsNewShown(context)
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(id = R.string.whats_new_title, versionName),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = changelog,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    markWhatsNewShown(context)
                    onDismiss()
                }
            ) {
                Text(stringResource(id = R.string.whats_new_dismiss))
            }
        }
    )
}
