package com.davy.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davy.data.repository.AccountRepository
import com.davy.data.repository.AddressBookRepository
import com.davy.domain.model.AddressBook
import com.davy.domain.usecase.GetAddressBooksUseCase
import com.davy.domain.usecase.GetAllAccountsUseCase
import com.davy.domain.usecase.UpdateAddressBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

private const val TAG = "AddressBookListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookListScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddressBookListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isBusy = isSyncing
    var showSyncMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = com.davy.R.string.address_books)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(id = com.davy.R.string.back))
                    }
                },
                actions = {
                    // Sync menu
                    IconButton(onClick = { showSyncMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(id = com.davy.R.string.sync)
                        )
                    }
                    DropdownMenu(
                        expanded = showSyncMenu,
                        onDismissRequest = { showSyncMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = com.davy.R.string.sync_contacts_only)) },
                            onClick = {
                                showSyncMenu = false
                                viewModel.syncContactsOnly()
                            },
                            enabled = !isBusy,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    
                    // Refresh button
                    IconButton(onClick = viewModel::refresh, enabled = !isBusy) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = com.davy.R.string.refresh)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isBusy,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.addressBooks.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = com.davy.R.string.no_address_books),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(id = com.davy.R.string.add_account_to_sync_address_books),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.addressBooks, key = { it.id }, contentType = { "addressbook" }) { addressBook ->
                            AddressBookItemCard(
                                addressBook = addressBook,
                                onSyncToggle = { enabled ->
                                    viewModel.toggleSync(addressBook, enabled)
                                },
                                onVisibilityToggle = { visible ->
                                    viewModel.toggleVisibility(addressBook, visible)
                                },
                                onSyncNow = {
                                    viewModel.syncNow(addressBook)
                                },
                                isBusy = isBusy
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressBookItemCard(
    addressBook: AddressBook,
    onSyncToggle: (Boolean) -> Unit,
    onVisibilityToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    isBusy: Boolean = false
) {
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
                // Color indicator
                Surface(
                    modifier = Modifier.size(width = 4.dp, height = 40.dp),
                    color = Color(addressBook.color),
                    shape = MaterialTheme.shapes.small
                ) {}
                
                // Address book info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = addressBook.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    addressBook.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                        text = stringResource(id = com.davy.R.string.sync_with_server),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(id = com.davy.R.string.enable_bidirectional_sync),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = addressBook.syncEnabled,
                    onCheckedChange = onSyncToggle,
                    enabled = !isBusy,
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
                        text = stringResource(id = com.davy.R.string.visible_in_app),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(id = com.davy.R.string.show_in_contacts_list),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = addressBook.visible,
                    onCheckedChange = onVisibilityToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2E7D32),
                        checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                    )
                )
            }
            
            // Sync Now button
            if (addressBook.syncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = stringResource(id = com.davy.R.string.sync_now),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = com.davy.R.string.sync_now))
                }
            }
            
            // Status chips
            if (addressBook.androidAccountName != null) {
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
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(id = com.davy.R.string.synced_with_android),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

data class AddressBookListUiState(
    val addressBooks: List<AddressBook> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedAccountId: Long? = null
)

@HiltViewModel
class AddressBookListViewModel @Inject constructor(
    private val getAddressBooksUseCase: GetAddressBooksUseCase,
    private val getAllAccountsUseCase: GetAllAccountsUseCase,
    private val updateAddressBookUseCase: UpdateAddressBookUseCase,
    private val syncManager: com.davy.sync.SyncManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddressBookListUiState(isLoading = true))
    val uiState: StateFlow<AddressBookListUiState> = _uiState.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    init {
        loadAddressBooks()
        observeSelectedAccountSync()
    }
    
    private fun loadAddressBooks() {
        viewModelScope.launch {
            try {
                getAllAccountsUseCase().collect { accounts ->
                    val allAddressBooks = mutableListOf<AddressBook>()
                    var firstAccountId: Long? = null
                    
                    for (account in accounts) {
                        if (firstAccountId == null) firstAccountId = account.id
                        val addressBooks = getAddressBooksUseCase(account.id)
                        allAddressBooks.addAll(addressBooks)
                    }
                    
                    _uiState.value = AddressBookListUiState(
                        addressBooks = allAddressBooks,
                        isLoading = false,
                        selectedAccountId = firstAccountId
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error loading address books")
                _uiState.value = AddressBookListUiState(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    /**
     * Sync contacts only (CardDAV service) for the current account.
     * Triggers Android SyncAdapter framework with MANUAL flag to ensure proper sync execution.
     */
    fun syncContactsOnly() {
        viewModelScope.launch {
            try {
                val accountId = _uiState.value.selectedAccountId
                if (accountId != null) {
                    // FIXED: Trigger Android SyncAdapter with MANUAL flag instead of using SyncWorker
                    // The SyncWorker bypasses ContactsSyncAdapter which has the actual sync logic
                    syncManager.requestContactsSyncThroughAdapter(accountId)
                    Timber.tag(TAG).d("Manual contacts sync requested through Android SyncAdapter for account: %s", accountId)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync contacts")
            }
        }
    }
    
    fun toggleSync(addressBook: AddressBook, enabled: Boolean) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Toggling sync for %s: %s", addressBook.displayName, enabled)
                val updated = addressBook.copy(
                    syncEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                updateAddressBookUseCase(updated)
                Timber.tag(TAG).d("Successfully toggled sync for %s", addressBook.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error toggling sync for %s", addressBook.displayName)
            }
        }
    }
    
    fun toggleVisibility(addressBook: AddressBook, visible: Boolean) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Toggling visibility for %s: %s", addressBook.displayName, visible)
                val updated = addressBook.copy(
                    visible = visible,
                    updatedAt = System.currentTimeMillis()
                )
                updateAddressBookUseCase(updated)
                Timber.tag(TAG).d("Successfully toggled visibility for %s", addressBook.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error toggling visibility for %s", addressBook.displayName)
            }
        }
    }
    
    fun syncNow(addressBook: AddressBook) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Starting manual sync for %s", addressBook.displayName)
                syncManager.syncAddressBook(addressBook.accountId, addressBook.id)
                Timber.tag(TAG).d("Manual sync triggered for %s", addressBook.displayName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync %s", addressBook.displayName)
            }
        }
    }
    
    fun syncAccount(accountId: Long) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).d("Syncing all address books for account %s", accountId)
                syncManager.syncNow(accountId, com.davy.sync.SyncManager.SYNC_TYPE_CONTACTS)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync address books for account %s", accountId)
            }
        }
    }
    
    fun refresh() {
        loadAddressBooks()
    }

    private fun observeSelectedAccountSync() {
        viewModelScope.launch {
            uiState
                .map { it.selectedAccountId }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id != null) syncManager.observeSyncState(id) else flowOf(false) }
                .collect { syncing -> _isSyncing.value = syncing }
        }
    }
}

