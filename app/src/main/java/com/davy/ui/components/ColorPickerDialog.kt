package com.davy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Color picker dialog for calendars and task lists.
 *
 * Provides a grid of predefined colors matching reference implementation's color palette.
 */
@Composable
fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Choose Color",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(calendarColors) { color ->
                        ColorItem(
                            color = color,
                            isSelected = color == currentColor,
                            onClick = {
                                onColorSelected(color)
                                onDismiss()
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ColorItem(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color, CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                }
            )
            .clickableWithFeedback(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // Checkmark or indicator
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.9f)
            ) {
                // Could add a checkmark icon here
            }
        }
    }
}

/**
 * Predefined calendar colors matching reference implementation palette.
 */
private val calendarColors = listOf(
    // Red shades
    Color(0xFFD32F2F),
    Color(0xFFE57373),
    Color(0xFFF44336),
    
    // Pink shades
    Color(0xFFC2185B),
    Color(0xFFE91E63),
    Color(0xFFF06292),
    
    // Purple shades
    Color(0xFF7B1FA2),
    Color(0xFF9C27B0),
    Color(0xFFBA68C8),
    
    // Deep Purple
    Color(0xFF512DA8),
    Color(0xFF673AB7),
    Color(0xFF9575CD),
    
    // Indigo
    Color(0xFF303F9F),
    Color(0xFF3F51B5),
    Color(0xFF7986CB),
    
    // Blue shades
    Color(0xFF1976D2),
    Color(0xFF2196F3),
    Color(0xFF64B5F6),
    
    // Light Blue
    Color(0xFF0288D1),
    Color(0xFF03A9F4),
    Color(0xFF4FC3F7),
    
    // Cyan
    Color(0xFF0097A7),
    Color(0xFF00BCD4),
    Color(0xFF4DD0E1),
    
    // Teal
    Color(0xFF00796B),
    Color(0xFF009688),
    Color(0xFF4DB6AC),
    
    // Green shades
    Color(0xFF388E3C),
    Color(0xFF4CAF50),
    Color(0xFF81C784),
    
    // Light Green
    Color(0xFF689F38),
    Color(0xFF8BC34A),
    Color(0xFFAED581),
    
    // Lime
    Color(0xFFAFB42B),
    Color(0xFFCDDC39),
    Color(0xFFDCE775),
    
    // Yellow
    Color(0xFFFBC02D),
    Color(0xFFFFEB3B),
    Color(0xFFFFF176),
    
    // Amber
    Color(0xFFFFA000),
    Color(0xFFFFC107),
    Color(0xFFFFD54F),
    
    // Orange
    Color(0xFFF57C00),
    Color(0xFFFF9800),
    Color(0xFFFFB74D),
    
    // Deep Orange
    Color(0xFFE64A19),
    Color(0xFFFF5722),
    Color(0xFFFF8A65),
    
    // Brown
    Color(0xFF5D4037),
    Color(0xFF795548),
    Color(0xFFA1887F),
    
    // Grey
    Color(0xFF616161),
    Color(0xFF757575),
    Color(0xFF9E9E9E),
    
    // Blue Grey
    Color(0xFF455A64),
    Color(0xFF607D8B),
    Color(0xFF90A4AE)
)
