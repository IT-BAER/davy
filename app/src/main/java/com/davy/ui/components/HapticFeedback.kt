package com.davy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * Modifier that adds clickable behavior with haptic feedback for native feel.
 * 
 * Following Android best practices:
 * - Haptic feedback on tap (TextClick type for light touch)
 * - Ripple effect for visual feedback
 * - Proper accessibility semantics
 * 
 * Reference: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press
 * 
 * @param enabled Whether the element is clickable
 * @param role Semantic role for accessibility
 * @param onClick Callback when element is clicked
 */
@Composable
fun Modifier.clickableWithFeedback(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    
    return this.clickable(
        enabled = enabled,
        role = role,
        interactionSource = interactionSource,
        indication = rememberRipple(),
        onClick = {
            // Perform haptic feedback before action
            // TextClick is lighter than LongPress - better for frequent interactions
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
}

/**
 * Modifier for long-press with stronger haptic feedback.
 * Use for context menus, selection, or important actions.
 * 
 * @param onLongClick Callback when element is long-pressed
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.longClickableWithFeedback(
    onLongClick: () -> Unit
): Modifier {
    val haptics = LocalHapticFeedback.current
    
    return this.combinedClickable(
        indication = rememberRipple(),
        interactionSource = remember { MutableInteractionSource() },
        onClick = { },
        onLongClick = {
            // Use LongPress haptic for long-press actions
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    )
}
