package com.davy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wizard for creating new calendars with templates.
 * Provides guided setup for common calendar types.
 * 
 * See reference implementation: CreateCalendarActivity
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionCreationWizard(
    @Suppress("UNUSED_PARAMETER") accountId: Long,
    onDismiss: () -> Unit,
    onCreateCalendar: (displayName: String, color: Int, description: String?, supportsEvents: Boolean, supportsTasks: Boolean, supportsJournal: Boolean) -> Unit,
    onCreateAddressBook: (displayName: String, description: String?) -> Unit
) {
    var collectionType by remember { mutableStateOf(CollectionType.CALENDAR) }
    var displayName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color(0xFF2196F3)) }  // Material Blue
    var supportsEvents by remember { mutableStateOf(true) }
    var supportsTasks by remember { mutableStateOf(false) }
    var supportsJournal by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Collection",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Collection Type Selector
                Text(
                    text = "Collection Type",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = collectionType == CollectionType.CALENDAR,
                        onClick = { collectionType = CollectionType.CALENDAR },
                        label = { Text("Calendar") },
                        leadingIcon = { Icon(Icons.Default.DateRange, null) }
                    )
                    
                    FilterChip(
                        selected = collectionType == CollectionType.ADDRESS_BOOK,
                        onClick = { collectionType = CollectionType.ADDRESS_BOOK },
                        label = { Text("Address Book") },
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )
                }
                
                // Display Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Description (optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                if (collectionType == CollectionType.CALENDAR) {
                    // Calendar-specific options
                    
                    // Color Picker (simplified)
                    Text(
                        text = "Calendar Color",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CalendarColorOption(
                            color = Color(0xFF2196F3),  // Blue
                            selected = selectedColor == Color(0xFF2196F3),
                            onClick = { selectedColor = Color(0xFF2196F3) }
                        )
                        CalendarColorOption(
                            color = Color(0xFFF44336),  // Red
                            selected = selectedColor == Color(0xFFF44336),
                            onClick = { selectedColor = Color(0xFFF44336) }
                        )
                        CalendarColorOption(
                            color = Color(0xFF4CAF50),  // Green
                            selected = selectedColor == Color(0xFF4CAF50),
                            onClick = { selectedColor = Color(0xFF4CAF50) }
                        )
                        CalendarColorOption(
                            color = Color(0xFFFF9800),  // Orange
                            selected = selectedColor == Color(0xFFFF9800),
                            onClick = { selectedColor = Color(0xFFFF9800) }
                        )
                        CalendarColorOption(
                            color = Color(0xFF9C27B0),  // Purple
                            selected = selectedColor == Color(0xFF9C27B0),
                            onClick = { selectedColor = Color(0xFF9C27B0) }
                        )
                    }
                    
                    // Component Support
                    Text(
                        text = "Supported Components",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = supportsEvents,
                            onCheckedChange = { supportsEvents = it }
                        )
                        Text("Events (VEVENT)")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = supportsTasks,
                            onCheckedChange = { supportsTasks = it }
                        )
                        Text("Tasks (VTODO)")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = supportsJournal,
                            onCheckedChange = { supportsJournal = it }
                        )
                        Text("Journal (VJOURNAL)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        when (collectionType) {
                            CollectionType.CALENDAR -> {
                                onCreateCalendar(
                                    displayName,
                                    selectedColor.hashCode(),
                                    description.ifBlank { null },
                                    supportsEvents,
                                    supportsTasks,
                                    supportsJournal
                                )
                            }
                            CollectionType.ADDRESS_BOOK -> {
                                onCreateAddressBook(
                                    displayName,
                                    description.ifBlank { null }
                                )
                            }
                        }
                        onDismiss()
                    }
                },
                enabled = displayName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CalendarColorOption(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = color,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.size(48.dp)
    ) {
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

private enum class CollectionType {
    CALENDAR,
    ADDRESS_BOOK
}
