package com.davy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davy.R
import com.davy.domain.model.Account
import com.davy.ui.LocalSyncManager
import com.davy.ui.viewmodel.AccountListUiState
import com.davy.ui.viewmodel.AccountListViewModel
import kotlinx.coroutines.launch

/**
 * Screen for managing accounts.
 * Shows list of accounts with delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun AccountListScreen(
    onNavigateBack: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onAccountClick: (Long) -> Unit = {},
    onAccountLongClick: (Long) -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGuides: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    viewModel: AccountListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    // Use injected SyncManager from CompositionLocal (performance optimization)
    val syncManager = LocalSyncManager.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    // LazyColumn state for pull-to-refresh
    val listState = rememberLazyListState()

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                // Sync all enabled items for all accounts
                syncManager.syncAllNow()
                viewModel.syncAll()
                isRefreshing = false
            }
        }
    )

    // Derived state to prevent unnecessary recompositions
    val shouldShowIllustration = remember(uiState) {
        derivedStateOf { uiState !is AccountListUiState.Loading }
    }.value

    // Rotation animation for sync icon
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "sync_rotation"
    )
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,  // Enable swipe gesture to open drawer
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    onCloseDrawer = { 
                        scope.launch { drawerState.close() }
                    },
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToGuides = onNavigateToGuides,
                    onNavigateToFaq = onNavigateToFaq
                )
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.account_list_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(id = R.string.content_description_menu)
                            )
                        }
                    },
                    actions = {
                        // Sync All button
                        IconButton(
                            onClick = { 
                                syncManager.syncAllNow()
                                viewModel.syncAll()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = stringResource(id = R.string.content_description_sync),
                                modifier = if (isSyncing) {
                                    Modifier.rotate(rotation)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            when (val state = uiState) {
                is AccountListUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is AccountListUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.no_accounts),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.no_accounts_description),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is AccountListUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.accounts,
                            key = { it.id },
                            contentType = { "account" }
                        ) { account ->
                            key(account.id) {
                                AccountItemCard(
                                    account = account,
                                    allAccounts = state.accounts,
                                    onClick = { onAccountClick(account.id) },
                                    onLongClick = { onAccountLongClick(account.id) }
                                )
                            }
                        }
                    }
                }
                is AccountListUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.error) + ": ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(id = R.string.retry))
                        }
                    }
                }
            }
            
            // Pull-to-refresh indicator
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // Background illustration at bottom (visible in all states except Loading)
            if (shouldShowIllustration) {
                Image(
                    painter = painterResource(id = com.davy.R.drawable.home_illustration),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.75f)
                        .alpha(0.35f),
                    contentScale = ContentScale.FillWidth
                )
            }
            
            // Floating Action Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 84.dp)
            ) {
                val isEmpty = uiState is AccountListUiState.Empty
                val haptics = LocalHapticFeedback.current
                
                if (isEmpty) {
                    // Extended FAB for empty state
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddAccount()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.content_description_add),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        text = { 
                            Text(
                                stringResource(id = R.string.add_account),
                                style = MaterialTheme.typography.labelLarge
                            ) 
                        },
                        containerColor = Color(0xFF2E7D32),  // Material Green 800 - darker green
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp
                        )
                    )
                } else {
                    // Standard FAB with slightly larger icon
                    FloatingActionButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddAccount()
                        },
                        containerColor = Color(0xFF2E7D32),  // Material Green 800 - darker green
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(id = R.string.content_description_add),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(stringResource(id = R.string.delete_account_question)) },
            text = { Text(stringResource(id = R.string.delete_account_confirmation_named, account.accountName)) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete icon button on the left
                    IconButton(
                        onClick = {
                            viewModel.removeAccount(account)
                            accountToDelete = null
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.content_description_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Cancel button on the right
                    TextButton(onClick = { accountToDelete = null }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            },
            dismissButton = null
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountItemCard(
    account: Account,
    allAccounts: List<Account>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Generate distinct color based on account position in sorted list - ensures color reuse
    val accountColor = remember(account.id, allAccounts.size) { 
        getAccountColorByPosition(account.id, allAccounts) 
    }
    
    // PERFORMANCE: Add haptic feedback for native feel
    val haptics = LocalHapticFeedback.current
    
    // PERFORMANCE: Memoize click handlers to prevent lambda recreation
    val onClickWithHaptic = remember(onClick) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    }
    
    val onLongClickWithHaptic = remember(onLongClick) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, accountColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .combinedClickable(
                        onClick = onClickWithHaptic,
                        onLongClick = onLongClickWithHaptic
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Account image on the left edge - full height, with left padding equal to border width
                Icon(
                    painter = painterResource(id = R.drawable.ic_account_card),
                    contentDescription = null,
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .padding(start = 1.dp),
                    tint = accountColor.copy(alpha = 0.35f)
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = account.accountName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = account.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (account.calendarEnabled) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.calendar),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (account.contactsEnabled) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.contacts),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (account.tasksEnabled) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.tasks),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generate a distinct color for an account based on its position in the account list.
 * This ensures colors are reused when accounts are deleted - the first account gets the first color,
 * second account gets the second color, etc., regardless of their IDs.
 * 
 * Uses a predefined palette of vibrant colors.
 */
private fun getAccountColorByPosition(accountId: Long, allAccounts: List<Account>): Color {
    val colorPalette = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFF44336), // Red
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFFEB3B), // Yellow
        Color(0xFFE91E63), // Pink
        Color(0xFF3F51B5), // Indigo
        Color(0xFF009688)  // Teal
    )
    
    // Sort accounts by ID to ensure stable color assignment
    val sortedAccounts = allAccounts.sortedBy { it.id }
    val accountIndex = sortedAccounts.indexOfFirst { it.id == accountId }
    
    // If account not found (shouldn't happen), fallback to ID-based color
    if (accountIndex == -1) {
        return colorPalette[(accountId % colorPalette.size).toInt()]
    }
    
    // Assign color based on position in sorted list - ensures color reuse
    return colorPalette[accountIndex % colorPalette.size]
}

/**
 * Navigation drawer content with menu options.
 * Based on reference implementation drawer pattern.
 */
@Composable
private fun DrawerContent(
    onCloseDrawer: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGuides: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {}
) {
    var helpExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with app branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Image(
                    painter = painterResource(id = com.davy.R.drawable.ic_app_logo),
                    contentDescription = stringResource(id = R.string.content_description_app_logo),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Black
                    )
                    Text(
                        text = stringResource(id = R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Settings
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(id = R.string.settings)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                onNavigateToSettings()
            },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Help (expandable)
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(id = R.string.help)) },
            selected = false,
            onClick = { helpExpanded = !helpExpanded },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Help submenu items
        if (helpExpanded) {
            // Guides
            NavigationDrawerItem(
                icon = { Spacer(modifier = Modifier.width(24.dp)) },
                label = { Text(stringResource(id = R.string.guides), style = MaterialTheme.typography.bodyMedium) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToGuides()
                },
                modifier = Modifier.padding(start = 36.dp, end = 12.dp)
            )
            
            // FAQ
            NavigationDrawerItem(
                icon = { Spacer(modifier = Modifier.width(24.dp)) },
                label = { Text(stringResource(id = R.string.faq), style = MaterialTheme.typography.bodyMedium) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToFaq()
                },
                modifier = Modifier.padding(start = 36.dp, end = 12.dp)
            )
        }
        
        // About
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(id = R.string.about)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                onNavigateToAbout()
            },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Contact
        val context = LocalContext.current
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(id = R.string.contact)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:admin@it-baer.net")
                }
                try {
                    val chooser = Intent.createChooser(intent, context.getString(R.string.send_email))
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    // Handle error silently
                }
            },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // License information
        NavigationDrawerItem(
            icon = null,
            label = { 
                Column {
                    Text(
                        text = stringResource(id = R.string.license_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.license_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            selected = false,
            onClick = {
                onCloseDrawer()
                showLicenseInfo(context)
            },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Show license information.
 */
private fun showLicenseInfo(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://www.apache.org/licenses/LICENSE-2.0")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error silently
    }
}
