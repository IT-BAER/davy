package com.davy.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RuleFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davy.R
import com.davy.domain.model.Account
import com.davy.domain.model.AddressBook
import com.davy.domain.model.Calendar
import com.davy.domain.model.WebCalSubscription
import com.davy.sync.SyncManager
import com.davy.ui.LocalSyncManager
import com.davy.ui.components.ColorPickerDialog
import com.davy.ui.viewmodels.AccountDetailViewModel
import com.davy.util.BatteryOptimizationUtils
import com.davy.ui.theme.DavyOrange
import com.davy.ui.theme.DavyBlue
import kotlinx.coroutines.launch

private enum class AccountDetailTabType { CalDav, CardDav, WebCal }

private data class AccountDetailTabDescriptor(
    val type: AccountDetailTabType,
    val title: String,
    val icon: ImageVector
)

private val accountDetailTabs = listOf(
    AccountDetailTabDescriptor(
        type = AccountDetailTabType.CalDav,
        title = "CalDAV",
        icon = Icons.Default.Event // Calendar events icon
    ),
    AccountDetailTabDescriptor(
        type = AccountDetailTabType.CardDav,
        title = "CardDAV",
        icon = Icons.Default.Contacts // Contacts icon
    ),
    AccountDetailTabDescriptor(
        type = AccountDetailTabType.WebCal,
        title = "WebCal",
        icon = Icons.Default.CalendarMonth // Web calendar subscription icon
    )
)

@Composable
private fun rememberGradientBorderBrush(baseColor: Color): Brush {
    val fallbackColor = MaterialTheme.colorScheme.outline
    val resolvedColor = if (baseColor.alpha == 0f) fallbackColor else baseColor
    return remember(resolvedColor) {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to resolvedColor.copy(alpha = 0.9f),
                0.55f to resolvedColor.copy(alpha = 0.3f), // Reduced from 0.55f to 0.3f
                1f to resolvedColor.copy(alpha = 0.1f)  // Reduced from 0.2f to 0.1f
            )
        )
    }
}

/**
 * Custom border modifier with thicker left border and rounded corners.
 * Uses a path-based approach to draw a rounded rectangle outline with varying border widths.
 * Matches the card's corner radius for seamless integration using UX/UI best practices.
 */
private fun Modifier.customCardBorder(
    brush: Brush,
    shape: androidx.compose.ui.graphics.Shape,
    leftWidth: androidx.compose.ui.unit.Dp = 2.dp,
    otherWidth: androidx.compose.ui.unit.Dp = 1.dp
): Modifier = this.then(
    drawWithCache {
        val leftPx = leftWidth.toPx()
        val otherPx = otherWidth.toPx()
        
        // Extract corner radius from the shape
        val outline = shape.createOutline(size, layoutDirection, this)
        val radiusPx = if (outline is androidx.compose.ui.graphics.Outline.Rounded) {
            outline.roundRect.topLeftCornerRadius.x
        } else {
            0f
        }
        
        onDrawWithContent {
            // Draw the card content first
            drawContent()
            
            // For proper border alignment with card edges, inset by half the border width
            // This ensures the border sits exactly on the card edge
            val insetLeft = leftPx / 2f
            val insetOther = otherPx / 2f
            
            // Adjust corner radius to account for the inset while maintaining visual consistency
            val adjustedRadius = (radiusPx - insetOther).coerceAtLeast(0f)
            
            // Draw the full outline with left border width
            val fullPath = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = insetLeft,
                            top = insetOther,
                            right = size.width - insetOther,
                            bottom = size.height - insetOther
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(adjustedRadius, adjustedRadius)
                    )
                )
            }
            
            // Draw the complete border outline
            drawPath(
                path = fullPath,
                brush = brush,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = leftPx
                )
            )
            
            // Cover the thicker parts on top, right, and bottom with the card background color
            // to create the asymmetric border effect
            if (leftPx > otherPx) {
                val coverWidth = leftPx - otherPx
                val coverInset = otherPx / 2f
                
                // Create paths to cover the excess border width on top, right, and bottom
                val coverPath = androidx.compose.ui.graphics.Path().apply {
                    // Top edge cover
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left = adjustedRadius + coverInset,
                            top = coverInset,
                            right = size.width - adjustedRadius - coverInset,
                            bottom = coverInset + coverWidth
                        )
                    )
                    
                    // Right edge cover
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left = size.width - coverInset - coverWidth,
                            top = adjustedRadius + coverInset,
                            right = size.width - coverInset,
                            bottom = size.height - adjustedRadius - coverInset
                        )
                    )
                    
                    // Bottom edge cover
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left = adjustedRadius + coverInset,
                            top = size.height - coverInset - coverWidth,
                            right = size.width - adjustedRadius - coverInset,
                            bottom = size.height - coverInset
                        )
                    )
                }
                
                // Draw the cover rectangles with the card's background color
                drawPath(
                    path = coverPath,
                    color = androidx.compose.ui.graphics.Color.Transparent
                )
            }
        }
    }
)

@Composable
private fun rememberUnifiedBackgroundBrush(currentPage: Int, pageOffsetFraction: Float): Brush {
    val colorScheme = MaterialTheme.colorScheme
    
    return remember(currentPage, pageOffsetFraction, colorScheme) {
        // Define color stops for each tab: CalDAV (primary), CardDAV (secondary), WebCal (tertiary)
        val tabColors = listOf(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary
        )
        
        // Calculate interpolated base color based on page position
        val effectivePage = (currentPage + pageOffsetFraction).coerceIn(0f, tabColors.size - 1f)
        val leftIndex = effectivePage.toInt().coerceIn(0, tabColors.size - 2)
        val rightIndex = (leftIndex + 1).coerceIn(0, tabColors.size - 1)
        val fraction = effectivePage - leftIndex
        
        val baseColor = lerp(tabColors[leftIndex], tabColors[rightIndex], fraction)
        
        // Create subtle gradient with interpolated base color
        val tint = lerp(baseColor, colorScheme.surfaceTint, 0.1f)
        val highlight = lerp(tint, Color.White, 0.5f).copy(alpha = 0.08f)
        val mid = lerp(tint, colorScheme.surfaceVariant, 0.3f).copy(alpha = 0.05f)
        val lowMid = lerp(tint, colorScheme.surface, 0.5f).copy(alpha = 0.02f)
        
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to highlight,
                0.3f to mid,
                0.7f to lowMid,
                1f to colorScheme.surface
            )
        )
    }
}

@Composable
private fun rememberSyncRotation(isActive: Boolean): Float {
    // When sync stops, immediately return to 0
    if (!isActive) return 0f
    
    // When syncing, use infinite rotation animation
    val rotation by rememberInfiniteTransition(label = "syncRotation")
        .animateFloat(
            initialValue = 0f,
            targetValue = -360f, // Counter-clockwise rotation
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing)
            ),
            label = "syncRotationValue"
        )
    return rotation
}

/**
 * AccountDetailScreen optimized for responsiveness.
 * 
 * PERFORMANCE OPTIMIZATIONS APPLIED:
 * 1. collectAsStateWithLifecycle() - lifecycle-aware state collection (stops when backgrounded)
 * 2. HorizontalPager beyondBoundsPageCount = 1 (pre-compose adjacent pages for smooth swiping)
 * 3. derivedStateOf for computed values (skip unnecessary recompositions)
 * 4. @Immutable domain models (Compose can skip unchanged state)
 * 5. Lambda modifiers for frequently changing values (defer state reads)
 * 6. CompositionLocal for DI (no remember blocks)
 * 
 * Reference: https://developer.android.com/develop/ui/compose/performance/bestpractices
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (Long) -> Unit = {},
    onNavigateToCalendarDetails: (Long) -> Unit = {},
    onNavigateToAddressBookDetails: (Long) -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Exit batch selection when system back is pressed (including back gesture)
    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = isBatchMode) {
        // Toggle off batch mode (also clears selections in ViewModel)
        viewModel.toggleBatchSelectionMode()
    }

    LaunchedEffect(uiState.accountDeleted) {
        if (uiState.accountDeleted) {
            viewModel.onAccountDeletionHandled()
            onNavigateBack()
        }
    }

    val calDavListState = rememberLazyListState()
    val cardDavListState = rememberLazyListState()
    val webCalListState = rememberLazyListState()

    val pagerState = rememberPagerState(pageCount = { accountDetailTabs.size })
    
    // Determine current tab item count for scroll behavior
    val currentTabItemCount = when (pagerState.currentPage) {
        0 -> uiState.calendars.size // CalDAV tab
        1 -> uiState.addressBooks.size // CardDAV tab
        2 -> uiState.webCalSubscriptions.size // WebCal tab
        else -> 0
    }
    
    // Use pinned scroll behavior (no collapse) when there are fewer than 5 items
    // Use enterAlways scroll behavior (with collapse) when there are 5 or more items
    val scrollBehavior = if (currentTabItemCount < 5) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }
    
    val scope = rememberCoroutineScope()
    val syncManager = LocalSyncManager.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val batteryOptimizationUtils = remember { BatteryOptimizationUtils(context) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showCreateCalendarDialog by remember { mutableStateOf(false) }
    var showCreateAddressBookDialog by remember { mutableStateOf(false) }
    var showAddWebCalDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedCalendarsDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedAddressBooksDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedWebCalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }

    val currentTab = accountDetailTabs[pagerState.currentPage]
    val currentScrollState = when (currentTab.type) {
        AccountDetailTabType.CalDav -> calDavListState
        AccountDetailTabType.CardDav -> cardDavListState
        AccountDetailTabType.WebCal -> webCalListState
    }
    val showTabIcons = !currentScrollState.canScrollBackward

    val hasIndividualSyncInFlight = uiState.syncingCalendarIds.isNotEmpty() ||
        uiState.syncingAddressBookIds.isNotEmpty()
    val isGlobalOperationRunning = uiState.isDoingFullSync || uiState.isRefreshingCollections
    val isBusy = uiState.isSyncing || uiState.isRefreshingCollections || hasIndividualSyncInFlight

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(uiState.account?.accountName ?: stringResource(id = R.string.account)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.content_description_back))
                    }
                },
                actions = {
                    // Batch selection mode toggle button
                    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
                    val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsStateWithLifecycle()
                    val selectedAddressBookIds by viewModel.selectedAddressBookIds.collectAsStateWithLifecycle()
                    val selectedWebCalIds by viewModel.selectedWebCalIds.collectAsStateWithLifecycle()
                    IconButton(
                        onClick = { viewModel.toggleBatchSelectionMode() }
                    ) {
                        Icon(
                            imageVector = if (isBatchMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                            contentDescription = if (isBatchMode) stringResource(id = R.string.exit_batch_selection) else stringResource(id = R.string.batch_selection),
                            tint = if (isBatchMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    
                    IconButton(onClick = { onNavigateToSettings(accountId) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(id = R.string.content_description_settings))
                    }
                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.content_description_menu))
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false }
                        ) {
                            when (currentTab.type) {
                                AccountDetailTabType.CalDav -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.sync_calendars_only)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            if (!isBusy) viewModel.syncAllCalendars()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Sync, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.create_calendar)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showCreateCalendarDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.delete_selected)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            if (isBatchMode && selectedCalendarIds.isNotEmpty() && !isBusy) {
                                                showDeleteSelectedCalendarsDialog = true
                                            }
                                        },
                                        enabled = isBatchMode && selectedCalendarIds.isNotEmpty() && !isBusy,
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        }
                                    )
                                }

                                AccountDetailTabType.CardDav -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.sync_contacts_only)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            if (!isBusy) viewModel.syncAllAddressBooks()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Sync, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.create_address_book)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showCreateAddressBookDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.delete_selected)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            if (isBatchMode && selectedAddressBookIds.isNotEmpty() && !isBusy) {
                                                showDeleteSelectedAddressBooksDialog = true
                                            }
                                        },
                                        enabled = isBatchMode && selectedAddressBookIds.isNotEmpty() && !isBusy,
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        }
                                    )
                                }

                                AccountDetailTabType.WebCal -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.sync_webcal_subscriptions)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            if (!isBusy) viewModel.syncWebCalSubscriptions()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Sync, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.add_webcal_subscription)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showAddWebCalDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.delete_selected)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            if (isBatchMode && selectedWebCalIds.isNotEmpty() && !isBusy) {
                                                showDeleteSelectedWebCalDialog = true
                                            }
                                        },
                                        enabled = isBatchMode && selectedWebCalIds.isNotEmpty() && !isBusy,
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
            val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsStateWithLifecycle()
            val selectedAddressBookIds by viewModel.selectedAddressBookIds.collectAsStateWithLifecycle()
            val selectedWebCalIds by viewModel.selectedWebCalIds.collectAsStateWithLifecycle()
            val totalSelected = selectedCalendarIds.size + selectedAddressBookIds.size + selectedWebCalIds.size
            
            Column(
                modifier = Modifier.padding(bottom = 60.dp, end = 24.dp),  // Lowered position
                horizontalAlignment = Alignment.End
            ) {
                // Batch sync button (shows in batch selection mode)
                if (isBatchMode && totalSelected > 0) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(id = R.string.sync_selected_with_count, totalSelected)) },
                        icon = {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = stringResource(id = R.string.content_description_sync_selected)
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = {
                            if (!isBusy) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.syncSelectedCollections()
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Sync Now button (first/top position) - hidden in batch mode
                if (!isBatchMode) {
                    val syncContainer = if (isBusy) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        DavyBlue
                    }
                    val syncContent = if (isBusy) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        Color.White
                    }
                    val isRefreshing = uiState.isRefreshingCollections
                    val isGlobalSyncing = isGlobalOperationRunning && !isRefreshing
                    val globalSyncRotation = rememberSyncRotation(isGlobalSyncing)
                    ExtendedFloatingActionButton(
                        modifier = Modifier.widthIn(min = 140.dp),
                        text = { Text(stringResource(id = R.string.sync_now)) },
                        icon = {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = stringResource(id = R.string.content_description_sync),
                                modifier = Modifier.rotate(if (isGlobalSyncing) globalSyncRotation else 0f)
                            )
                        },
                        containerColor = syncContainer,
                        contentColor = syncContent,
                        onClick = {
                            if (!isBusy) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    // Sync ALL resource types (calendars, contacts, tasks) in parallel
                                    syncManager.syncNow(accountId, com.davy.sync.SyncManager.SYNC_TYPE_ALL)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Get Lists button (second/bottom position) - always visible
                ExtendedFloatingActionButton(
                    modifier = Modifier.widthIn(min = 140.dp),
                    text = { Text(stringResource(id = R.string.sync_with_server)) },
                    icon = { 
                        if (uiState.isRefreshingCollections) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = if (isBusy) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.White
                            )
                        } else {
                            Icon(
                                Icons.Outlined.RuleFolder, 
                                contentDescription = null
                            )
                        }
                    },
                    containerColor = if (isBusy) MaterialTheme.colorScheme.surfaceVariant else DavyOrange,
                    contentColor = if (isBusy) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.White,
                    onClick = {
                        if (!isBusy) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.refreshCollections()
                        }
                    }
                )
            }
        }
    ) { padding ->
        // Unified gradient background that smoothly transitions across tabs
        val unifiedBackgroundBrush = rememberUnifiedBackgroundBrush(
            currentPage = pagerState.currentPage,
            pageOffsetFraction = pagerState.currentPageOffsetFraction
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(unifiedBackgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                accountDetailTabs.forEachIndexed { index, tab ->
                    val tabTitle = when (tab.type) {
                        AccountDetailTabType.CalDav -> stringResource(id = R.string.caldav)
                        AccountDetailTabType.CardDav -> stringResource(id = R.string.carddav)
                        AccountDetailTabType.WebCal -> stringResource(id = R.string.webcal)
                    }
                    val iconContent: (@Composable () -> Unit)? = if (showTabIcons) {
                        { 
                            Icon(
                                tab.icon, 
                                contentDescription = tabTitle
                            )
                        }
                    } else {
                        null
                    }
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = {
                            scope.launch {
                                pagerState.scrollToPage(index)
                            }
                        },
                        icon = iconContent,
                        text = { Text(tabTitle) }
                    )
                }
            }

            if (uiState.isDoingFullSync) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Battery optimization warning
            if (batteryOptimizationUtils.isOptimized()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBatteryOptimizationDialog = true }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.battery_optimization_active),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(id = R.string.sync_may_be_unreliable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(id = R.string.content_description_warning),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1, // Pre-compose adjacent pages for smooth swiping
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(pages = 1)
                ),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (accountDetailTabs[page].type) {
                    AccountDetailTabType.CalDav -> CalDAVTabContent(
                        calendars = uiState.calendars,
                        viewModel = viewModel,
                        accountUsername = uiState.account?.username,
                        listState = calDavListState,
                        onNavigateToCalendarDetails = onNavigateToCalendarDetails,
                        isBusy = isBusy
                    )

                    AccountDetailTabType.CardDav -> CardDAVTabContent(
                        addressBooks = uiState.addressBooks,
                        viewModel = viewModel,
                        accountUsername = uiState.account?.username,
                        listState = cardDavListState,
                        onNavigateToAddressBookDetails = onNavigateToAddressBookDetails,
                        isBusy = isBusy
                    )

                    AccountDetailTabType.WebCal -> WebCalTabContent(
                        webCalSubscriptions = uiState.webCalSubscriptions,
                        viewModel = viewModel,
                        listState = webCalListState,
                        isBusy = isBusy,
                        showAddWebCalDialog = showAddWebCalDialog,
                        onShowAddWebCalDialog = { showAddWebCalDialog = it }
                    )
                }
            }
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog && uiState.account != null) {
        AccountSettingsDialog(
            account = uiState.account!!,
            onDismiss = { showSettingsDialog = false },
            onSave = { updatedAccount: Account, newPassword: String? ->
                viewModel.updateAccount(updatedAccount, newPassword)
                viewModel.updateSyncConfiguration(updatedAccount.id)
                showSettingsDialog = false
            }
        )
    }
    
    // Battery Optimization Dialog
    if (showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showBatteryOptimizationDialog = false }
        )
    }
    
    // Create Calendar Dialog
    if (showCreateCalendarDialog) {
        CreateCalendarDialog(
            onDismiss = { showCreateCalendarDialog = false },
            onCreate = { name: String, color: Int ->
                viewModel.createCalendar(name, color)
                showCreateCalendarDialog = false
            }
        )
    }
    
    // Create Address Book Dialog
    if (showCreateAddressBookDialog) {
        CreateAddressBookDialog(
            onDismiss = { showCreateAddressBookDialog = false },
            onCreate = { name: String ->
                viewModel.createAddressBook(name)
                showCreateAddressBookDialog = false
            }
        )
    }

    // Delete selected calendars confirmation
    if (showDeleteSelectedCalendarsDialog) {
        val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showDeleteSelectedCalendarsDialog = false },
            title = { Text(stringResource(id = R.string.delete_selected_calendars_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.delete_selected_calendars_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.selected_count, selectedCalendarIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCalendars(selectedCalendarIds)
                        viewModel.clearBatchSelections()
                        showDeleteSelectedCalendarsDialog = false
                    }
                ) { Text(stringResource(id = R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedCalendarsDialog = false }) { Text(stringResource(id = R.string.cancel)) }
            }
        )
    }

    // Delete selected address books confirmation
    if (showDeleteSelectedAddressBooksDialog) {
        val selectedAddressBookIds by viewModel.selectedAddressBookIds.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showDeleteSelectedAddressBooksDialog = false },
            title = { Text(stringResource(id = R.string.delete_selected_address_books_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.delete_selected_address_books_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.selected_count, selectedAddressBookIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAddressBooks(selectedAddressBookIds)
                        viewModel.clearBatchSelections()
                        showDeleteSelectedAddressBooksDialog = false
                    }
                ) { Text(stringResource(id = R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedAddressBooksDialog = false }) { Text(stringResource(id = R.string.cancel)) }
            }
        )
    }

    // Delete selected WebCal subscriptions confirmation
    if (showDeleteSelectedWebCalDialog) {
        val selectedWebCalIds by viewModel.selectedWebCalIds.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showDeleteSelectedWebCalDialog = false },
            title = { Text(stringResource(id = R.string.delete_selected_webcal_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.delete_selected_webcal_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.selected_count, selectedWebCalIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWebCalSubscriptions(selectedWebCalIds)
                        viewModel.clearBatchSelections()
                        showDeleteSelectedWebCalDialog = false
                    }
                ) { Text(stringResource(id = R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedWebCalDialog = false }) { Text(stringResource(id = R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun CalDAVTabContent(
    calendars: List<Calendar>,
    viewModel: AccountDetailViewModel,
    accountUsername: String? = null,
    listState: LazyListState,
    onNavigateToCalendarDetails: (Long) -> Unit = {},
    isBusy: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val canScroll by remember {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    
    var selectedCalendar by remember { mutableStateOf<Calendar?>(null) }
    var showCalendarSettingsDialog by remember { mutableStateOf(false) }
    
    // PERFORMANCE: Remember lambdas to avoid recomposition
    val onCalendarClick = remember(onNavigateToCalendarDetails) {
        { calendar: Calendar ->
            onNavigateToCalendarDetails(calendar.id)
        }
    }
    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
    val onCalendarLongPress = remember(isBatchMode) {
        { calendar: Calendar ->
            if (!isBatchMode) {
                viewModel.toggleBatchSelectionMode()
                viewModel.toggleCalendarSelection(calendar.id)
            } else {
                viewModel.toggleCalendarSelection(calendar.id)
            }
        }
    }
    
    // Pull-to-sync functionality
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                if (!isBusy) {
                    isRefreshing = true
                    viewModel.syncAllCalendars()
                    isRefreshing = false
                }
            }
        }
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (calendars.isEmpty()) {
            Text(
                text = stringResource(id = R.string.no_calendars_found) + " " + stringResource(id = R.string.sync_with_server_to_fetch_calendars),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 200.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = calendars,
                    key = { calendar -> calendar.id },
                    contentType = { "calendar" }
                ) { calendar ->
                    // PERFORMANCE: Remove ALL animations from list items - follow reference implementation pattern
                    // reference implementation uses plain items with no graphicsLayer, no animateItemPlacement
                    // These "optimizations" actually cause lag during swipes!
                    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
                    val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsStateWithLifecycle()
                    CalendarCard(
                        calendar = calendar,
                        accountUsername = accountUsername,
                        onClick = {
                            if (isBatchMode) {
                                viewModel.toggleCalendarSelection(calendar.id)
                            } else {
                                onCalendarClick(calendar)
                            }
                        },
                        onLongPress = onCalendarLongPress,
                        onToggleSync = { calendarId -> viewModel.toggleCalendarSync(calendarId) },
                        isBatchMode = isBatchMode,
                        isSelected = selectedCalendarIds.contains(calendar.id),
                        isBusy = isBusy
                    )
                }
            }
        }
        
        // Pull-to-refresh indicator
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    // Calendar settings dialog
    if (showCalendarSettingsDialog && selectedCalendar != null) {
        CalendarSettingsDialog(
            calendar = selectedCalendar!!,
            onDismiss = { showCalendarSettingsDialog = false },
            onToggleSync = { calendar ->
                viewModel.toggleCalendarSync(calendar.id)
                showCalendarSettingsDialog = false
            },
            onColorChange = { calendar, color ->
                viewModel.updateCalendarColor(calendar.id, color)
            },
            onDelete = { calendar ->
                viewModel.deleteCalendar(calendar)
                showCalendarSettingsDialog = false
            },
            onRename = { calendar, newName ->
                viewModel.renameCalendar(calendar.id, newName)
            },
            onUpdateSettings = { calendar, wifiOnly, readOnly, interval ->
                viewModel.updateCalendarSettings(calendar.id, wifiOnly, readOnly, interval)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CalendarCard(
    calendar: Calendar,
    accountUsername: String? = null,
    onClick: (Calendar) -> Unit = {},
    onLongPress: (Calendar) -> Unit = {},
    onToggleSync: (Long) -> Unit = {},
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    isBusy: Boolean = false
) {
    // PERFORMANCE: Convert color OUTSIDE of remember - Color() is cheap, remember overhead is expensive
    // See: https://developer.android.com/develop/ui/compose/performance/bestpractices
    val calendarColor = androidx.compose.ui.graphics.Color(calendar.color)
    val haptics = LocalHapticFeedback.current
    
    // PERFORMANCE: Use ElevatedCard with surfaceContainer (reference implementation pattern) instead of Card with surfaceVariant
    // ElevatedCard provides subtle shadow for better depth perception and native feel
    val cardShape = MaterialTheme.shapes.medium
    val gradientBorder = rememberGradientBorderBrush(calendarColor)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .customCardBorder(brush = gradientBorder, shape = cardShape, leftWidth = 2.dp, otherWidth = 1.dp),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick(calendar) },
                    onLongClick = { onLongPress(calendar); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox (batch mode)
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null // Click handled by card
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = calendarColor,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Property tags (wrapped across lines when space runs out)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Events tag - all calendars support events
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            stringResource(id = R.string.events),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    // Tasks tag if calendar supports VTODO
                    if (calendar.supportsVTODO) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.tasks),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // Journal tag if calendar supports VJOURNAL
                    if (calendar.supportsVJOURNAL) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.journal),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // Read-only tag ONLY when calendar is actually read-only (server/user policy)
                    // Do NOT mark as read-only when sync is simply disabled
                    if (calendar.isReadOnly()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(0.dp).alpha(0.7f)
                        ) {
                            Text(
                                stringResource(id = R.string.read_only),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    // Show "Shared" tag when calendar has an owner (shared by another user)
                    // Only show if owner is different from current account username
                    // Extract displayable owner name from URL (e.g., "/principals/user/" -> "user")
                    if (calendar.owner != null && accountUsername != null) {
                        val ownerName = calendar.owner
                            .substringBeforeLast("/")
                            .substringAfterLast("/")
                            .takeIf { it.isNotEmpty() } ?: stringResource(id = R.string.unknown)
                        
                        // Only show "Shared" tag if owner is different from current user
                        // Owner details will be shown in calendar settings dialog when tapping the card
                        if (ownerName != accountUsername && !calendar.owner.contains("/$accountUsername/", ignoreCase = true)) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(0.dp).alpha(0.7f)
                            ) {
                                Text(
                                    stringResource(id = R.string.shared),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    
                    // TODO: Add "Tasks" tag when calendar supports VTODO (requires supportedComponents from server)
                    // TODO: Fetch privWriteContent from CalDAV PROPFIND to accurately determine read-only status
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Display last sync time
                Text(
                    text = if (calendar.lastSyncedAt != null) {
                        stringResource(id = R.string.last_sync, formatRelativeTime(calendar.lastSyncedAt))
                    } else {
                        stringResource(id = R.string.never_synced)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Sync toggle
            Switch(
                checked = calendar.syncEnabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSync(calendar.id)
                },
                enabled = !isBusy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun CalendarSettingsDialog(
    calendar: Calendar,
    onDismiss: () -> Unit,
    onToggleSync: (Calendar) -> Unit,
    onColorChange: (Calendar, Int) -> Unit,
    onDelete: (Calendar) -> Unit = {},
    onRename: (Calendar, String) -> Unit = { _, _ -> },
    onUpdateSettings: (Calendar, Boolean, Boolean, Int?) -> Unit = { _, _, _, _ -> }
) {
    var isSyncEnabled by remember { mutableStateOf(calendar.syncEnabled) }
    var selectedColor by remember { mutableStateOf(calendar.color) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    var wifiOnlySync by remember { mutableStateOf(calendar.wifiOnlySync) }
    var forceReadOnly by remember { mutableStateOf(calendar.forceReadOnly) }
    var syncIntervalMinutes by remember { mutableStateOf(calendar.syncIntervalMinutes) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = calendar.calendarUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calendar Information Section
                Text(
                    text = stringResource(id = R.string.calendar_information),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                Text(
                    text = stringResource(id = R.string.owner) + ": " + (if (calendar.owner != null) {
                        calendar.owner
                            .substringBeforeLast("/")
                            .substringAfterLast("/")
                            .takeIf { it.isNotEmpty() } ?: stringResource(id = R.string.unknown)
                    } else stringResource(id = R.string.none)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = if (calendar.lastSyncedAt != null) {
                        stringResource(id = R.string.last_sync, formatRelativeTime(calendar.lastSyncedAt))
                    } else {
                        stringResource(id = R.string.never_synced)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Sync Settings Section
                Text(
                    text = stringResource(id = R.string.settings_sync_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                // Synchronization toggle card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { isSyncEnabled = !isSyncEnabled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.synchronization),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isSyncEnabled) stringResource(id = R.string.automatic_sync_enabled) else stringResource(id = R.string.sync_disabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSyncEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF2E7D32),
                                checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Sync interval selector card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showIntervalPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.sync_interval),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = syncIntervalLabel(syncIntervalMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // WiFi-only sync toggle card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { wifiOnlySync = !wifiOnlySync }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.wifi_only_sync),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (wifiOnlySync) stringResource(id = R.string.wifi_only_sync_on) else stringResource(id = R.string.wifi_only_sync_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wifiOnlySync,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF2E7D32),
                                checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Read-Only toggle card (auto-enabled if server-side read-only)
                val isServerReadOnly = !calendar.privWriteContent
                val canChangeReadOnly = calendar.privWriteContent // Can only toggle if server allows writes
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (canChangeReadOnly) forceReadOnly = !forceReadOnly }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.read_only),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isServerReadOnly) {
                                    stringResource(id = R.string.server_enforced_cannot_write)
                                } else if (forceReadOnly) {
                                    stringResource(id = R.string.changes_prevented)
                                } else {
                                    stringResource(id = R.string.changes_allowed)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isServerReadOnly || forceReadOnly,
                            onCheckedChange = null,
                            enabled = canChangeReadOnly,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.error,
                                checkedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                disabledCheckedThumbColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                disabledCheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Appearance Section
                Text(
                    text = stringResource(id = R.string.settings_appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                // Color picker card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showColorPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.calendar_color),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(id = R.string.tap_to_choose_color),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(selectedColor), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Rename button card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showRenameDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.rename_calendar),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = calendar.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete icon button on the left
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = calendar.canDelete(),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete_calendar))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Cancel button
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    
                    // Save button
                    TextButton(
                        onClick = {
                            if (isSyncEnabled != calendar.syncEnabled) {
                                onToggleSync(calendar)
                            }
                            if (wifiOnlySync != calendar.wifiOnlySync || 
                                forceReadOnly != calendar.forceReadOnly ||
                                syncIntervalMinutes != calendar.syncIntervalMinutes) {
                                onUpdateSettings(calendar, wifiOnlySync, forceReadOnly, syncIntervalMinutes)
                            }
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(id = R.string.save))
                    }
                }
            }
        },
        dismissButton = null
    )
    
    // Rename dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(calendar.displayName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(id = R.string.rename_calendar)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(id = R.string.calendar_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != calendar.displayName) {
                            onRename(calendar, newName)
                        }
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(id = R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
    
    // Sync interval picker dialog
    if (showIntervalPicker) {
        AlertDialog(
            onDismissRequest = { showIntervalPicker = false },
            title = { Text(stringResource(id = R.string.sync_interval)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, 15, 30, 60, 240, 1440).forEach { minutes ->
                        val label = if (minutes == null) {
                            stringResource(id = R.string.use_account_default)
                        } else {
                            val m = minutes
                            when {
                                m == 60 -> stringResource(id = R.string.every_hour)
                                m == 1440 -> stringResource(id = R.string.once_a_day)
                                m < 60 -> stringResource(
                                    id = R.string.every_duration,
                                    pluralStringResource(id = R.plurals.duration_minutes, count = m, m)
                                )
                                m % 60 == 0 && m < 1440 -> {
                                    val hours = m / 60
                                    stringResource(
                                        id = R.string.every_duration,
                                        pluralStringResource(id = R.plurals.duration_hours, count = hours, hours)
                                    )
                                }
                                else -> {
                                    val days = m / 1440
                                    stringResource(
                                        id = R.string.every_duration,
                                        pluralStringResource(id = R.plurals.duration_days, count = days, days)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncIntervalMinutes = minutes
                                    showIntervalPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = syncIntervalMinutes == minutes,
                                onClick = {
                                    syncIntervalMinutes = minutes
                                    showIntervalPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalPicker = false }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }
    
    // Color picker dialog
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = Color(selectedColor),
            onColorSelected = { newColor ->
                selectedColor = newColor.toArgb()
                onColorChange(calendar, selectedColor)
            },
            onDismiss = { showColorPicker = false }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(id = R.string.delete_calendar_question)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.delete_calendar_confirmation_named, calendar.displayName))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.delete_calendar_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete icon button on the left
                    IconButton(
                        onClick = {
                            onDelete(calendar)
                            showDeleteConfirmation = false
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Cancel button on the right
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun WebCalSubscriptionCard(
    subscription: WebCalSubscription,
    onToggleSync: (Long) -> Unit,
    onClick: () -> Unit,
    onLongPress: (WebCalSubscription) -> Unit = {},
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    isBusy: Boolean = false
) {
    // PERFORMANCE: Convert color OUTSIDE of remember - Color() is cheap, remember overhead is expensive
    val iconColor = androidx.compose.ui.graphics.Color(subscription.color)
    val haptics = LocalHapticFeedback.current
    
    // PERFORMANCE: Use ElevatedCard (reference implementation pattern)
    val cardShape = MaterialTheme.shapes.medium
    val gradientBorder = rememberGradientBorderBrush(iconColor)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .customCardBorder(brush = gradientBorder, shape = cardShape, leftWidth = 2.dp, otherWidth = 1.dp),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongPress(subscription); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox (batch mode)
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null // Click handled by card
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Property tags (wrap to next line when needed)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // WebCal subscription tag
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            stringResource(id = R.string.webcal),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    // Read-only tag - webcal subscriptions are always read-only
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(0.dp).alpha(0.7f)
                    ) {
                        Text(
                            stringResource(id = R.string.read_only),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Display last sync time
                Text(
                    text = if (subscription.lastSyncedAt != null) {
                        stringResource(id = R.string.last_sync, formatRelativeTime(subscription.lastSyncedAt))
                    } else {
                        stringResource(id = R.string.never_synced)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Sync toggle
            Switch(
                checked = subscription.syncEnabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSync(subscription.id)
                },
                enabled = !isBusy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CardDAVTabContent(
    addressBooks: List<AddressBook>,
    viewModel: AccountDetailViewModel,
    accountUsername: String? = null,
    listState: LazyListState,
    onNavigateToAddressBookDetails: (Long) -> Unit = {},
    isBusy: Boolean = false
) {
    val scope = rememberCoroutineScope()
    // REMOVED duplicate collectAsState() - now received as parameter
    val canScroll by remember {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    
    // Pull-to-sync functionality
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                if (!isBusy) {
                    isRefreshing = true
                    viewModel.syncAllAddressBooks()
                    isRefreshing = false
                }
            }
        }
    )
    
    // PERFORMANCE: Remember lambdas to prevent recreating them on every recomposition
    val onAddressBookClick = remember(onNavigateToAddressBookDetails) {
        { ab: AddressBook ->
            onNavigateToAddressBookDetails(ab.id)
        }
    }
    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
    val onAddressBookLongPress = remember(isBatchMode) {
        { ab: AddressBook ->
            if (!isBatchMode) {
                viewModel.toggleBatchSelectionMode()
                viewModel.toggleAddressBookSelection(ab.id)
            } else {
                viewModel.toggleAddressBookSelection(ab.id)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (addressBooks.isEmpty()) {
            Text(
                text = stringResource(id = R.string.no_address_books) + " " + stringResource(id = R.string.sync_with_server_to_fetch_address_books),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 200.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = addressBooks,
                    key = { addressBook -> addressBook.id },
                    contentType = { "addressbook" }
                ) { addressBook ->
                    // PERFORMANCE: Remove ALL animations - follow reference implementation pattern
                    // reference implementation uses plain items with no wrapper modifiers
                    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
                    val selectedAddressBookIds by viewModel.selectedAddressBookIds.collectAsStateWithLifecycle()
                    AddressBookCard(
                        addressBook = addressBook,
                        accountUsername = accountUsername,
                        onClick = {
                            if (isBatchMode) {
                                viewModel.toggleAddressBookSelection(addressBook.id)
                            } else {
                                onAddressBookClick(addressBook)
                            }
                        },
                        onLongPress = onAddressBookLongPress,
                        onToggleSync = { addressBookId -> viewModel.toggleAddressBookSync(addressBookId) },
                        isBatchMode = isBatchMode,
                        isSelected = selectedAddressBookIds.contains(addressBook.id),
                        isBusy = isBusy
                    )
                }
            }
        }
        
        // Pull-to-refresh indicator
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
private fun AddressBookCard(
    addressBook: AddressBook,
    accountUsername: String? = null,
    onClick: (AddressBook) -> Unit = {},
    onLongPress: (AddressBook) -> Unit = {},
    onToggleSync: (Long) -> Unit = {},
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    isBusy: Boolean = false
) {
    // Use secondary color palette for address book cards
    val addressBookColor = MaterialTheme.colorScheme.secondary
    val iconColor = addressBookColor
    val haptics = LocalHapticFeedback.current
    
    // PERFORMANCE: Use ElevatedCard with surfaceContainer (reference implementation pattern)
    val cardShape = MaterialTheme.shapes.medium
    val gradientBorder = rememberGradientBorderBrush(iconColor)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .customCardBorder(brush = gradientBorder, shape = cardShape, leftWidth = 2.dp, otherWidth = 1.dp),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick(addressBook) },
                    onLongClick = { onLongPress(addressBook); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox (batch mode)
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null // Click handled by card
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Icon(
                Icons.Default.Contacts,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = addressBook.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Property tags (wrap to next line when needed)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Show "Contacts" tag for a consistent address book experience
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            stringResource(id = R.string.contacts),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    // Show "Read-only" tag when the server doesn't allow writes
                    // readOnly correlates with CardDAV privWriteContent support
                    // System address books like "Accounts" and "Recently contacted" return false
                    if (addressBook.isReadOnly()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(0.dp).alpha(0.7f)
                        ) {
                            Text(
                                stringResource(id = R.string.read_only),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    // Show "Shared" tag when addressbook has an owner (shared by another user)
                    // Only show if owner is different from current account username
                    // Extract displayable owner name from URL (e.g., "/principals/user/" -> "user")
                    if (addressBook.owner != null && accountUsername != null) {
                        val ownerName = addressBook.owner
                            .substringBeforeLast("/")
                            .substringAfterLast("/")
                            .takeIf { it.isNotEmpty() } ?: stringResource(id = R.string.unknown)
                        
                        // Only show "Shared" tag if owner is different from current user
                        // Owner details will be shown in addressbook settings dialog when tapping the card
                        if (ownerName != accountUsername && !addressBook.owner.contains("/$accountUsername/", ignoreCase = true)) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(0.dp).alpha(0.7f)
                            ) {
                                Text(
                                    stringResource(id = R.string.shared),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    
                    // Future enhancements from reference implementation research:
                    // - Show owner displayname in settings dialog (requires principal lookup/repository)
                    // - "Personal" distinction when showOnlyPersonal filter is active
                    // - forceReadOnly user preference (user-disabled writes)
                    // - Different read-only states: by server, by user, by policy
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Display last sync time
                Text(
                    text = if (addressBook.ctag != null) {
                        stringResource(id = R.string.last_sync, formatRelativeTime(addressBook.updatedAt))
                    } else {
                        stringResource(id = R.string.never_synced)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Sync toggle
            Switch(
                checked = addressBook.syncEnabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSync(addressBook.id)
                },
                enabled = !isBusy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun AddressBookSettingsDialog(
    addressBook: AddressBook,
    onDismiss: () -> Unit,
    onSync: (AddressBook) -> Unit = {},
    onToggleSync: (AddressBook) -> Unit,
    onRename: (AddressBook, String) -> Unit = { _, _ -> },
    onUpdateSettings: (AddressBook, Boolean, Int?) -> Unit = { _, _, _ -> },
    onDelete: (AddressBook) -> Unit = {},
    isSyncing: Boolean = false
) {
    var isSyncEnabled by remember { mutableStateOf(addressBook.syncEnabled) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var wifiOnlySync by remember { mutableStateOf(addressBook.wifiOnlySync) }
    var syncIntervalMinutes by remember { mutableStateOf(addressBook.syncIntervalMinutes) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = addressBook.displayName,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = addressBook.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Synchronization toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Synchronization",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Enable automatic sync for this address book",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSyncEnabled,
                        onCheckedChange = { 
                            isSyncEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
                
                HorizontalDivider()
                
                // WiFi-only sync toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WiFi-only Sync",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Restrict sync to WiFi networks only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wifiOnlySync,
                        onCheckedChange = { wifiOnlySync = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
                
                HorizontalDivider()
                
                // Sync interval selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIntervalPicker = true }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Sync Interval",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (syncIntervalMinutes) {
                            null -> "Use account default"
                            15 -> "Every 15 minutes"
                            30 -> "Every 30 minutes"
                            60 -> "Every hour"
                            240 -> "Every 4 hours"
                            1440 -> "Once a day"
                            else -> "$syncIntervalMinutes minutes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                HorizontalDivider()
                
                // Rename button
                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.rename_address_book))
                }
                
                HorizontalDivider()
                
                // Owner info (extracted from addressBook.owner field)
                Column {
                    Text(
                        text = stringResource(id = R.string.owner),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (addressBook.owner != null) {
                            // Extract displayable owner name from URL (e.g., "/principals/user/" -> "user")
                            addressBook.owner
                                .substringBeforeLast("/")
                                .substringAfterLast("/")
                                .takeIf { it.isNotEmpty() } ?: stringResource(id = R.string.unknown)
                        } else {
                            stringResource(id = R.string.none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = if (addressBook.owner == null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    )
                }
                
                HorizontalDivider()
                
                // Last sync time with sync button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.last_sync, ""),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRelativeTime(addressBook.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Sync button
                    val rotation = rememberSyncRotation(isSyncing)
                    val haptics = LocalHapticFeedback.current
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSync(addressBook)
                        },
                        enabled = !isSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(id = R.string.sync_now),
                            modifier = Modifier.rotate(if (isSyncing) rotation else 0f),
                            tint = if (isSyncing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete icon button on the left
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = !addressBook.isReadOnly(),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete_address_book))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            if (isSyncEnabled != addressBook.syncEnabled) {
                                onToggleSync(addressBook)
                            }
                            if (wifiOnlySync != addressBook.wifiOnlySync ||
                                syncIntervalMinutes != addressBook.syncIntervalMinutes) {
                                onUpdateSettings(addressBook, wifiOnlySync, syncIntervalMinutes)
                            }
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(id = R.string.save))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
    
    // Rename dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(addressBook.displayName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(id = R.string.rename_address_book)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(id = R.string.address_book_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != addressBook.displayName) {
                            onRename(addressBook, newName)
                        }
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(id = R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
    
    // Sync interval picker dialog
    if (showIntervalPicker) {
        AlertDialog(
            onDismissRequest = { showIntervalPicker = false },
            title = { Text(stringResource(id = R.string.sync_interval)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        null to "Use account default",
                        15 to "Every 15 minutes",
                        30 to "Every 30 minutes",
                        60 to "Every hour",
                        240 to "Every 4 hours",
                        1440 to "Once a day"
                    ).forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncIntervalMinutes = minutes
                                    showIntervalPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = syncIntervalMinutes == minutes,
                                onClick = {
                                    syncIntervalMinutes = minutes
                                    showIntervalPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalPicker = false }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(id = R.string.delete_address_book_question)) },
            text = {
                Column {
                    Text("Are you sure you want to delete \"${addressBook.displayName}\"?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will delete the address book from both the server and your device. All contacts in this address book will be permanently removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onDelete(addressBook)
                            showDeleteConfirmation = false
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(id = R.string.cancel)) }
                }
            },
            dismissButton = null
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WebCalTabContent(
    webCalSubscriptions: List<WebCalSubscription>,
    viewModel: AccountDetailViewModel,
    listState: LazyListState,
    isBusy: Boolean = false,
    showAddWebCalDialog: Boolean,
    onShowAddWebCalDialog: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    
    // Pull-to-refresh functionality
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                if (!isBusy) {
                    isRefreshing = true
                    viewModel.syncWebCalSubscriptions()
                    isRefreshing = false
                }
            }
        }
    )
    
    // PERFORMANCE: Remember lambdas to prevent recreating them on every recomposition
    val onToggleSync = remember(viewModel) {
        { subscriptionId: Long ->
            viewModel.toggleWebCalSync(subscriptionId)
        }
    }
    val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
    val onWebCalLongPress = remember(isBatchMode) {
        { sub: WebCalSubscription ->
            if (!isBatchMode) {
                viewModel.toggleBatchSelectionMode()
                viewModel.toggleWebCalSelection(sub.id)
            } else {
                viewModel.toggleWebCalSelection(sub.id)
            }
        }
    }
    
    // Wrap the content in a Box with pullRefresh modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
    if (webCalSubscriptions.isEmpty()) {
        // Show empty state with explanation
        // PERFORMANCE FIX: Don't use Box(fillMaxSize) - it blocks touch events for HorizontalPager
        // Use Column with fillMaxSize instead to allow swipe gestures to pass through
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Background illustration (using calendar illustration for consistency)
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(id = R.string.webcal_subscriptions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(id = R.string.subscribe_to_read_only_feeds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    FilledTonalButton(
                        onClick = { onShowAddWebCalDialog(true) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.subscribe_to_webcal))
                    }
                }
            }
        }
    } else {
        // Show list of subscriptions
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 200.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            items(
                items = webCalSubscriptions,
                key = { subscription -> subscription.id },
                contentType = { "webcal" }
            ) { subscription ->
                // PERFORMANCE: Remove ALL animations - follow reference implementation pattern
                // No wrapper Box, no graphicsLayer - keep it simple
                val isBatchMode by viewModel.isBatchSelectionMode.collectAsStateWithLifecycle()
                val selectedWebCalIds by viewModel.selectedWebCalIds.collectAsStateWithLifecycle()
                WebCalSubscriptionCard(
                    subscription = subscription,
                    onToggleSync = onToggleSync,
                    onClick = {
                        if (isBatchMode) {
                            viewModel.toggleWebCalSelection(subscription.id)
                        } else {
                            /* TODO: Open subscription settings */
                        }
                    },
                    onLongPress = onWebCalLongPress,
                    isBatchMode = isBatchMode,
                    isSelected = selectedWebCalIds.contains(subscription.id),
                    isBusy = isBusy
                )
            }
        }
    }
        
        // Pull-to-refresh indicator
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    if (showAddWebCalDialog) {
        WebCalUrlInputDialog(
            onDismiss = { onShowAddWebCalDialog(false) },
            onSubmit = { url, displayName ->
                // Add WebCal subscription locally
                viewModel.addWebCalSubscription(url, displayName)
                onShowAddWebCalDialog(false)
            }
        )
    }
}

@Composable
private fun WebCalCard(subscription: WebCalSubscription) {
    // Icon color for WebCal
    val iconColor = MaterialTheme.colorScheme.primary
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Property tags (matching CardDAV style)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Events tag - WebCal subscriptions are for events
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            stringResource(id = R.string.events),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    // Read-only tag - WebCal subscriptions are always read-only
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            stringResource(id = R.string.read_only),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Display last sync time
                Text(
                    text = if (subscription.lastSyncedAt != null) {
                        stringResource(id = R.string.last_sync, formatRelativeTime(subscription.lastSyncedAt))
                    } else {
                        stringResource(id = R.string.never_synced)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WebCalUrlInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (url: String, displayName: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var url by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Public, contentDescription = null) },
        title = { Text(stringResource(id = R.string.add_webcal_subscription)) },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.webcal_subscription_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        nameError = null
                    },
                    label = { Text(stringResource(id = R.string.display_name)) },
                    placeholder = { Text(stringResource(id = R.string.display_name_placeholder)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = null
                    },
                    label = { Text(stringResource(id = R.string.webcal_url)) },
                    placeholder = { Text(stringResource(id = R.string.webcal_url_placeholder)) },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate display name
                    if (displayName.isBlank()) {
                        nameError = context.getString(R.string.display_name_required)
                        return@TextButton
                    }
                    
                    // Validate URL
                    if (url.isBlank()) {
                        urlError = context.getString(R.string.url_required)
                        return@TextButton
                    }
                    
                    if (!url.startsWith("webcal://") && 
                        !url.startsWith("webcals://") &&
                        !url.startsWith("http://") &&
                        !url.startsWith("https://")) {
                        urlError = context.getString(R.string.invalid_webcal_url)
                        return@TextButton
                    }
                    
                    onSubmit(url, displayName)
                }
            ) {
                Text(stringResource(id = R.string.add_webcal_subscription))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun AccountSettingsDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Account, String?) -> Unit  // Account and optional new password
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("sync_config_${account.id}", android.content.Context.MODE_PRIVATE) }
    
    // Authentication settings
    var username by remember { mutableStateOf(account.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var certificatePath by remember { mutableStateOf(account.certificateFingerprint ?: "") }
    
    // Sync interval options (in minutes, -1 = manual)
    val syncIntervalOptions = listOf(
        -1 to "Manually",
        15 to "15 minutes",
        30 to "30 minutes",
        60 to "1 hour",
        120 to "2 hours",
        240 to "4 hours",
        720 to "12 hours",
        1440 to "1 day"
    )
    
    // Load saved intervals from SharedPreferences
    var calendarSyncInterval by remember { mutableStateOf(prefs.getInt("calendar_sync_interval", 60)) }
    var contactSyncInterval by remember { mutableStateOf(prefs.getInt("contact_sync_interval", 60)) }
    var webCalSyncInterval by remember { mutableStateOf(prefs.getInt("webcal_sync_interval", 60)) }
    var showCalendarIntervalDialog by remember { mutableStateOf(false) }
    var showContactIntervalDialog by remember { mutableStateOf(false) }
    var showWebCalIntervalDialog by remember { mutableStateOf(false) }
    
    var syncOnlyOnWifi by remember { mutableStateOf(prefs.getBoolean("wifi_only", false)) }
    var manageCalendarColors by remember { mutableStateOf(prefs.getBoolean("manage_calendar_colors", true)) }
    var eventColors by remember { mutableStateOf(prefs.getBoolean("event_colors", false)) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(stringResource(id = R.string.account_settings))
                Text(
                    text = account.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Authentication Section
                Text(
                    text = stringResource(id = R.string.authentication),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(id = R.string.username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(id = R.string.leave_empty_keep_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password)
                            )
                        }
                    }
                )
                
                // Client certificate selection
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // TODO: Implement KeyChain certificate selection
                        // android.security.KeyChain.choosePrivateKeyAlias() would be called here
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.client_certificate),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (certificatePath.isNotBlank()) certificatePath else stringResource(id = R.string.none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Account Info Section
                Text(
                    text = stringResource(id = R.string.account_information),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.server_with_value, account.serverUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(id = R.string.username) + ": " + account.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Sync Settings Section
                Text(
                    text = stringResource(id = R.string.settings_sync_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                // Calendar sync interval
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCalendarIntervalDialog = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.calendar_sync_interval),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (calendarSyncInterval == -1) stringResource(id = R.string.manual) else syncIntervalLabel(calendarSyncInterval),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                
                // Contact sync interval
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showContactIntervalDialog = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.contact_sync_interval),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (contactSyncInterval == -1) stringResource(id = R.string.manual) else syncIntervalLabel(contactSyncInterval),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                
                // WebCal sync interval
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWebCalIntervalDialog = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.webcal_sync_interval),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (webCalSyncInterval == -1) stringResource(id = R.string.manual) else syncIntervalLabel(webCalSyncInterval),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // WiFi-only sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.wifi_only_sync),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(id = R.string.disable_mobile_data_sync),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncOnlyOnWifi,
                        onCheckedChange = { syncOnlyOnWifi = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // CalDAV Settings
                Text(
                    text = stringResource(id = R.string.caldav_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.manage_calendar_colors),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(id = R.string.use_server_side_calendar_colors),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = manageCalendarColors,
                        onCheckedChange = { manageCalendarColors = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.event_colors),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(id = R.string.use_colors_from_individual_events),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = eventColors,
                        onCheckedChange = { eventColors = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2E7D32),
                            checkedTrackColor = Color(0xFF2E7D32).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                // Save sync intervals to SharedPreferences
                prefs.edit().apply {
                    putInt("calendar_sync_interval", calendarSyncInterval)
                    putInt("contact_sync_interval", contactSyncInterval)
                    putInt("webcal_sync_interval", webCalSyncInterval)
                    putBoolean("wifi_only", syncOnlyOnWifi)
                    putBoolean("manage_calendar_colors", manageCalendarColors)
                    putBoolean("event_colors", eventColors)
                    apply()
                }
                
                // Update account with new authentication details if changed
                val updatedAccount = account.copy(
                    username = username,
                    certificateFingerprint = certificatePath.ifBlank { null }
                )
                
                // Pass password only if it was changed (not empty)
                val newPassword = password.ifBlank { null }
                
                onSave(updatedAccount, newPassword)
                onDismiss()
            }) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
    
    // Sync interval selection dialogs
    if (showCalendarIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarIntervalDialog = false },
            title = { Text(stringResource(id = R.string.calendar_sync_interval)) },
            text = {
                Column {
                    syncIntervalOptions.forEach { (interval, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    calendarSyncInterval = interval
                                    showCalendarIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = calendarSyncInterval == interval,
                                onClick = {
                                    calendarSyncInterval = interval
                                    showCalendarIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (interval == -1) stringResource(id = R.string.manual) else syncIntervalLabel(interval))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCalendarIntervalDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
    
    if (showContactIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showContactIntervalDialog = false },
            title = { Text(stringResource(id = R.string.contact_sync_interval)) },
            text = {
                Column {
                    syncIntervalOptions.forEach { (interval, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    contactSyncInterval = interval
                                    showContactIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = contactSyncInterval == interval,
                                onClick = {
                                    contactSyncInterval = interval
                                    showContactIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (interval == -1) stringResource(id = R.string.manual) else syncIntervalLabel(interval))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContactIntervalDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
    
    if (showWebCalIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showWebCalIntervalDialog = false },
            title = { Text(stringResource(id = R.string.webcal_sync_interval)) },
            text = {
                Column {
                    syncIntervalOptions.forEach { (interval, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    webCalSyncInterval = interval
                                    showWebCalIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = webCalSyncInterval == interval,
                                onClick = {
                                    webCalSyncInterval = interval
                                    showWebCalIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (interval == -1) stringResource(id = R.string.manual) else syncIntervalLabel(interval))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWebCalIntervalDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun RenameAccountDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.rename_account_title)) },
        text = {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(id = R.string.account_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(newName) },
                enabled = newName.isNotBlank()
            ) {
                Text(stringResource(id = R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun CreateCalendarDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var calendarName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFF2196F3.toInt()) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.create_calendar)) },
        text = {
            Column {
                TextField(
                    value = calendarName,
                    onValueChange = { calendarName = it },
                    label = { Text(stringResource(id = R.string.calendar_name)) },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(id = R.string.calendar_color) + ":", style = MaterialTheme.typography.bodyMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Color preview and picker button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showColorPicker = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(selectedColor), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        text = stringResource(id = R.string.tap_to_choose_color),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(calendarName, selectedColor) },
                enabled = calendarName.isNotBlank()
            ) {
                Text(stringResource(id = R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
    
    // Color picker dialog
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = Color(selectedColor),
            onColorSelected = { selectedColor = it.toArgb() },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
fun CreateAddressBookDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var addressBookName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.create_address_book)) },
        text = {
            Column {
                TextField(
                    value = addressBookName,
                    onValueChange = { addressBookName = it },
                    label = { Text(stringResource(id = R.string.address_book_name)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(addressBookName) },
                enabled = addressBookName.isNotBlank()
            ) {
                Text(stringResource(id = R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

/**
 * Format timestamp to readable date string.
 */
@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> stringResource(id = R.string.just_now)
        diff < 3_600_000 -> {
            val minutes = (diff / 60_000).toInt().coerceAtLeast(1)
            pluralStringResource(id = R.plurals.minutes_ago, count = minutes, minutes)
        }
        diff < 7_200_000 -> stringResource(id = R.string.hour_ago)
        diff < 86_400_000 -> {
            val hours = (diff / 3_600_000).toInt().coerceAtLeast(1)
            pluralStringResource(id = R.plurals.hours_ago, count = hours, hours)
        }
        diff < 172_800_000 -> stringResource(id = R.string.yesterday)
        diff < 604_800_000 -> {
            val days = (diff / 86_400_000).toInt().coerceAtLeast(1)
            pluralStringResource(id = R.plurals.days_ago, count = days, days)
        }
        diff < 2_592_000_000 -> {
            val weeks = (diff / 604_800_000).toInt().coerceAtLeast(1)
            pluralStringResource(id = R.plurals.weeks_ago, count = weeks, weeks)
        }
        else -> {
            val pattern = stringResource(id = R.string.date_format_short)
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
private fun syncIntervalLabel(minutes: Int?): String {
    return when {
        minutes == null -> stringResource(id = R.string.use_account_default)
        minutes < 60 -> pluralStringResource(id = R.plurals.duration_minutes, count = minutes, minutes)
        minutes % 60 == 0 && minutes < 1440 -> {
            val hours = minutes / 60
            pluralStringResource(id = R.plurals.duration_hours, count = hours, hours)
        }
        minutes % 1440 == 0 -> {
            val days = minutes / 1440
            pluralStringResource(id = R.plurals.duration_days, count = days, days)
        }
        else -> pluralStringResource(id = R.plurals.duration_minutes, count = minutes, minutes)
    }
}

