package com.davy.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Context

/**
 * Compose utility functions and extensions for performance optimization.
 * 
 * Following Android best practices:
 * - Minimize recompositions
 * - Use @Stable annotations
 * - Cache expensive operations
 * - Proper state hoisting
 */

/**
 * Remember application context safely.
 * Prevents creating new context references on recomposition.
 */
@Composable
fun rememberApplicationContext(): Context {
    val context = LocalContext.current
    return remember(context) { context.applicationContext }
}

/**
 * Composable marker for items that should skip recomposition when data hasn't changed.
 * Apply to data classes used in Compose.
 */
@Stable
interface StableComposable
