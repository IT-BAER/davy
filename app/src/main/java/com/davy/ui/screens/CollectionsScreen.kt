package com.davy.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davy.ui.LocalSyncManager
import kotlinx.coroutines.launch

/**
 * Main Collections Screen with swipeable tabs.
 * 
 * Displays Calendars, Contacts, Tasks, and Address Books in separate tabs.
 * Follows reference implementation architecture with HorizontalPager for swipe navigation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun CollectionsScreen(
    onNavigateBack: () -> Unit = {},
    onCalendarClick: (Long) -> Unit = {},
    onContactClick: (Long) -> Unit = {},
    onTaskClick: (Long) -> Unit = {},
    onAddressBookClick: (Long) -> Unit = {}
) {
    // Use injected SyncManager from CompositionLocal (performance optimization)
    val syncManager = LocalSyncManager.current
    val scope = rememberCoroutineScope()
    
    // Pager state for 4 tabs
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 4 }
    )
    
    // Tab titles
    val tabs = listOf("Calendars", "Contacts", "Tasks", "Address Books")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DAVy - Collections") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { syncManager.syncAllNow() }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync all"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }
            
            // Horizontal pager for swipeable content - Preload all tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 3,  // Preload all tabs (4 total pages)
                key = { it }  // Stable key for composition
            ) { page ->
                when (page) {
                    0 -> CalendarListScreen(
                        onNavigateBack = { /* handled by main back */ }
                    )
                    1 -> ContactListScreen(
                        onNavigateBack = { /* handled by main back */ },
                        onContactClick = onContactClick
                    )
                    2 -> TaskListScreen(
                        onNavigateBack = { /* handled by main back */ },
                        onTaskListClick = onTaskClick
                    )
                    3 -> AddressBookListScreen(
                        onNavigateBack = { /* handled by main back */ }
                    )
                }
            }
        }
    }
}
