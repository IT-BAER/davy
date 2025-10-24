package com.davy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.repository.ContactRepository
import com.davy.domain.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(contactId) {
        viewModel.loadContact(contactId)
    }
    
    LaunchedEffect(uiState.contactDeleted) {
        if (uiState.contactDeleted) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Edit */ }) {
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
                    Text(
                        text = uiState.errorMessage ?: "Error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.contact != null -> {
                ContactDetailContent(
                    contact = uiState.contact!!,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
        
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Contact") },
                text = { Text("Are you sure you want to delete this contact?") },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Delete icon button on the left
                        IconButton(
                            onClick = {
                                viewModel.deleteContact()
                                showDeleteDialog = false
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
    }
}

@Composable
private fun ContactDetailContent(
    contact: Contact,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar and name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.getInitials(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            contact.organization?.let { org ->
                Text(
                    text = org,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider()
        
        // Phone numbers
        if (contact.hasPhoneNumbers()) {
            ContactSection(title = "Phone Numbers", icon = Icons.Default.Phone) {
                contact.phoneNumbers.forEach { phone ->
                    ContactInfoRow(
                        label = phone.type.name,
                        value = phone.number
                    )
                }
            }
        }
        
        // Emails
        if (contact.hasEmails()) {
            ContactSection(title = "Emails", icon = Icons.Default.Email) {
                contact.emails.forEach { email ->
                    ContactInfoRow(
                        label = email.type.name,
                        value = email.email
                    )
                }
            }
        }
        
        // Addresses
        if (contact.hasPostalAddresses()) {
            ContactSection(title = "Addresses", icon = Icons.Default.Place) {
                contact.postalAddresses.forEach { address ->
                    ContactInfoRow(
                        label = address.type.name,
                        value = address.format()
                    )
                }
            }
        }
        
        // Websites
        if (contact.websites.isNotEmpty()) {
            ContactSection(title = "Websites", icon = Icons.Default.Language) {
                contact.websites.forEach { website ->
                    ContactInfoRow(
                        label = "Website",
                        value = website
                    )
                }
            }
        }
        
        // Organization details
        if (contact.organization != null || contact.jobTitle != null) {
            ContactSection(title = "Organization", icon = Icons.Default.Business) {
                contact.organization?.let {
                    ContactInfoRow("Company", it)
                }
                contact.organizationUnit?.let {
                    ContactInfoRow("Department", it)
                }
                contact.jobTitle?.let {
                    ContactInfoRow("Job Title", it)
                }
            }
        }
        
        // Personal info
        if (contact.birthday != null || contact.anniversary != null) {
            ContactSection(title = "Important Dates", icon = Icons.Default.Cake) {
                contact.birthday?.let {
                    ContactInfoRow("Birthday", it)
                }
                contact.anniversary?.let {
                    ContactInfoRow("Anniversary", it)
                }
            }
        }
        
        // Notes
        contact.note?.let { note ->
            ContactSection(title = "Notes", icon = Icons.AutoMirrored.Filled.Note) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Technical details
        ContactSection(title = "Technical Details", icon = Icons.Default.Info) {
            ContactInfoRow("UID", contact.uid)
            contact.contactUrl?.let {
                ContactInfoRow("URL", it)
            }
            contact.etag?.let {
                ContactInfoRow("ETag", it)
            }
        }
    }
}

@Composable
private fun ContactSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun ContactInfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

data class ContactDetailUiState(
    val contact: Contact? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val contactDeleted: Boolean = false
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()
    
    fun loadContact(contactId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val contact = contactRepository.getById(contactId)
                if (contact != null) {
                    _uiState.value = _uiState.value.copy(
                        contact = contact,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Contact not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load contact"
                )
            }
        }
    }
    
    fun deleteContact() {
        viewModelScope.launch {
            try {
                _uiState.value.contact?.let { contact ->
                    contactRepository.delete(contact.id)
                    _uiState.value = _uiState.value.copy(contactDeleted = true)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to delete contact"
                )
            }
        }
    }
}
