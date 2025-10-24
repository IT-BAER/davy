package com.davy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.repository.CalendarEventRepository
import com.davy.domain.model.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventScreen(
    eventId: Long,
    onNavigateBack: () -> Unit,
    viewModel: CalendarEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = uiState.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            uiState.event != null -> {
                val event = uiState.event!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    
                    // Date and Time
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Date & Time",
                                style = MaterialTheme.typography.titleMedium
                            )
                            HorizontalDivider()
                            EventDetailRow("Start", formatDateTime(event.dtStart, event.allDay))
                            EventDetailRow("End", formatDateTime(event.dtEnd, event.allDay))
                            if (event.allDay) {
                                EventDetailRow("Type", "All Day Event")
                            }
                            event.timezone?.let {
                                EventDetailRow("Timezone", it)
                            }
                        }
                    }
                    
                    // Description
                    event.description?.let { desc ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    // Location
                    event.location?.let { loc ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Location",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                Text(
                                    text = loc,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    // Attendees
                    if (event.attendees.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Attendees (${event.attendees.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                event.attendees.forEach { attendee ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = attendee.name ?: attendee.email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = attendee.status.name.replace("_", " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Reminders
                    if (event.reminders.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Reminders",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                event.reminders.forEach { reminder ->
                                    Text(
                                        text = "${reminder.minutes} minutes before (${reminder.method})",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    
                    // Recurrence
                    if (event.isRecurring()) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Recurrence",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                event.rrule?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    
                    // Status and Availability
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.titleMedium
                            )
                            HorizontalDivider()
                            event.status?.let {
                                EventDetailRow("Status", it.name)
                            }
                            event.availability?.let {
                                EventDetailRow("Availability", it.name.replace("_", " "))
                            }
                            event.organizer?.let {
                                EventDetailRow("Organizer", it)
                            }
                            EventDetailRow("UID", event.uid)
                        }
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to delete this event?") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete icon button on the left
                    IconButton(
                        onClick = {
                            viewModel.deleteEvent()
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Cancel button on the right
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = null
        )
    }
    
    // Edit dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Event") },
            text = { Text("Event editing will be implemented in a future update.") },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Navigate back on deletion
    LaunchedEffect(uiState.eventDeleted) {
        if (uiState.eventDeleted) {
            onNavigateBack()
        }
    }
}

@Composable
private fun EventDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatDateTime(timestamp: Long, allDay: Boolean): String {
    val format = if (allDay) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    }
    return format.format(Date(timestamp))
}

data class CalendarEventUiState(
    val event: CalendarEvent? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val eventDeleted: Boolean = false
)

@HiltViewModel
class CalendarEventViewModel @Inject constructor(
    private val calendarEventRepository: CalendarEventRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CalendarEventUiState())
    val uiState: StateFlow<CalendarEventUiState> = _uiState.asStateFlow()
    
    fun loadEvent(eventId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val event = calendarEventRepository.getById(eventId)
                if (event != null) {
                    _uiState.value = _uiState.value.copy(
                        event = event,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Event not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load event: ${e.message}"
                )
            }
        }
    }
    
    fun deleteEvent() {
        viewModelScope.launch {
            try {
                _uiState.value.event?.let { event ->
                    calendarEventRepository.delete(event)
                    _uiState.value = _uiState.value.copy(eventDeleted = true)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete event: ${e.message}"
                )
            }
        }
    }
}
