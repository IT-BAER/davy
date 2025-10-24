package com.davy.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.TaskListRepository
import com.davy.domain.model.TaskList
import com.davy.domain.usecase.GetAllAccountsUseCase
import com.davy.domain.usecase.GetTaskListsUseCase
import com.davy.domain.usecase.UpdateTaskListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

private const val TAG = "TaskListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun TaskListScreen(
    onNavigateBack: () -> Unit,
    onTaskListClick: (Long) -> Unit = {},
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Lists") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            uiState.taskLists.isEmpty() -> {
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
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No task lists",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add an account to sync tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.taskLists, key = { it.id }, contentType = { "tasklist" }) { taskList ->
                        TaskListItemCard(
                            taskList = taskList,
                            onSyncToggle = { enabled ->
                                viewModel.toggleSync(taskList, enabled)
                            },
                            onVisibilityToggle = { visible ->
                                viewModel.toggleVisibility(taskList, visible)
                            },
                            onSyncNow = {
                                viewModel.syncNow(taskList)
                            },
                            onColorChange = { tl, color ->
                                viewModel.changeColor(tl, color)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskListItemCard(
    taskList: TaskList,
    onSyncToggle: (Boolean) -> Unit,
    onVisibilityToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onColorChange: (TaskList, Int) -> Unit = { _, _ -> }
) {
    var showColorPicker by remember { mutableStateOf(false) }
    
    val defaultColor = MaterialTheme.colorScheme.primary
    val color = remember(taskList.color) {
        taskList.color?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (e: Exception) {
                null
            }
        }
    } ?: defaultColor
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Color indicator and name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator (clickable)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = color, shape = CircleShape)
                        .clickable { showColorPicker = true }
                )
                
                // Task list info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = taskList.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Sync toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync with server",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Enable bidirectional synchronization",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = taskList.syncEnabled,
                    onCheckedChange = onSyncToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2E7D32),
                        checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Visibility toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Visible in app",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Show in tasks list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = taskList.visible,
                    onCheckedChange = onVisibilityToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2E7D32),
                        checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                    )
                )
            }
            
            // Sync Now button
            if (taskList.syncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync now",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }
            
            // Status chips
            if (taskList.isSynced()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        // Color picker dialog
        if (showColorPicker) {
            com.davy.ui.components.ColorPickerDialog(
                currentColor = color,
                onColorSelected = { newColor ->
                    onColorChange(taskList, android.graphics.Color.argb(
                        (newColor.alpha * 255).toInt(),
                        (newColor.red * 255).toInt(),
                        (newColor.green * 255).toInt(),
                        (newColor.blue * 255).toInt()
                    ))
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }
    }
}

data class TaskListUiState(
    val taskLists: List<TaskList> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val getTaskListsUseCase: GetTaskListsUseCase,
    private val getAllAccountsUseCase: GetAllAccountsUseCase,
    private val updateTaskListUseCase: UpdateTaskListUseCase,
    private val syncManager: com.davy.sync.SyncManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TaskListUiState(isLoading = true))
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()
    
    init {
        loadTaskLists()
    }
    
    private fun loadTaskLists() {
        viewModelScope.launch {
            try {
                getAllAccountsUseCase().collect { accounts ->
                    val allTaskLists = mutableListOf<TaskList>()
                    
                    for (account in accounts) {
                        val taskLists = getTaskListsUseCase(account.id)
                        allTaskLists.addAll(taskLists)
                    }
                    
                    _uiState.value = TaskListUiState(
                        taskLists = allTaskLists,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error loading task lists")
                _uiState.value = TaskListUiState(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun toggleSync(taskList: TaskList, enabled: Boolean) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Toggling sync for %s: %s", taskList.displayName, enabled)
                val updated = taskList.copy(
                    syncEnabled = enabled
                )
                updateTaskListUseCase(updated)
                Timber.tag(TAG).d("Successfully toggled sync for %s", taskList.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error toggling sync for %s", taskList.displayName)
            }
        }
    }
    
    fun toggleVisibility(taskList: TaskList, visible: Boolean) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Toggling visibility for %s: %s", taskList.displayName, visible)
                val updated = taskList.copy(
                    visible = visible
                )
                updateTaskListUseCase(updated)
                Timber.tag(TAG).d("Successfully toggled visibility for %s", taskList.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error toggling visibility for %s", taskList.displayName)
            }
        }
    }
    
    fun syncNow(taskList: TaskList) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Starting manual sync for %s", taskList.displayName)
                // TODO: Implement manual sync trigger
                // This will be connected to the sync worker
                Timber.tag(TAG).d("Manual sync triggered for %s", taskList.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync %s", taskList.displayName)
            }
        }
    }
    
    fun changeColor(taskList: TaskList, color: Int) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Changing color for %s", taskList.displayName)
                val colorString = String.format("#%08X", color)
                val updated = taskList.copy(
                    color = colorString
                )
                updateTaskListUseCase(updated)
                Timber.tag(TAG).d("Successfully changed color for %s", taskList.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to change color for %s", taskList.displayName)
            }
        }
    }
    
    fun syncAccount(accountId: Long) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Syncing all task lists for account %s", accountId)
                syncManager.syncNow(accountId, com.davy.sync.SyncManager.SYNC_TYPE_TASKS)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync task lists for account %s", accountId)
            }
        }
    }
    
    fun refresh() {
        loadTaskLists()
    }
}

