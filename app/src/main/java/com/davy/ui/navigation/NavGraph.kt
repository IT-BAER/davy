package com.davy.ui.navigation

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.davy.ui.screens.AboutScreen
import com.davy.ui.screens.AccountDetailScreen
import com.davy.ui.screens.AccountListScreen
import com.davy.ui.screens.AddAccountScreen
import com.davy.ui.screens.AddressBookDetailsScreen
import com.davy.ui.screens.AddressBookListScreen
import com.davy.ui.screens.CalendarDetailsScreen
import com.davy.ui.screens.CalendarEventScreen
import com.davy.ui.screens.CalendarListScreen
import com.davy.ui.screens.CollectionsScreen
import com.davy.ui.screens.ContactDetailScreen
import com.davy.ui.screens.ContactListScreen
import com.davy.ui.screens.TaskListScreen
import com.davy.ui.screens.TaskScreen
import com.davy.ui.LocalSyncManager
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Navigation graph for DAVy app.
 * Phase 1: Account Management enabled.
 * Phase 2: Calendar Sync enabled.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // Optimized spring animations for 60fps native feel
    // Using higher stiffness (700) instead of MediumLow (400) for snappy response
    // Damping ratio of 0.85f provides natural bounce without feeling sluggish
    val springSpec = spring<IntOffset>(
        dampingRatio = 0.85f,  // Slightly bouncy for natural, responsive feel
        stiffness = 700f        // Higher stiffness for faster, snappier animations (60fps)
    )
    
    // Fast fade for smooth transitions without delay
    val fadeSpec = tween<Float>(
        durationMillis = 150,  // Slightly longer for smoother fade
        easing = LinearOutSlowInEasing
    )
    
    val enterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = springSpec
    ) + fadeIn(animationSpec = fadeSpec)
    
    val exitTransition = slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = springSpec
    ) + fadeOut(animationSpec = fadeSpec)
    
    val popEnterTransition = slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = springSpec
    ) + fadeIn(animationSpec = fadeSpec)
    
    val popExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = springSpec
    ) + fadeOut(animationSpec = fadeSpec)
    
    NavHost(
        navController = navController,
        startDestination = "accounts",
        modifier = modifier,
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition }
    ) {
        // Home screen with navigation options
        composable("home") {
            // Use injected SyncManager from CompositionLocal (performance optimization)
            val syncManager = LocalSyncManager.current
            
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        // App Icon/Logo (placeholder with styled text)
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "D",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "DAVy",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CalDAV & CardDAV Sync",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Compatible with Nextcloud, ownCloud, and more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        // Navigation buttons
                        FilledTonalButton(
                            onClick = { navController.navigate("accounts") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Manage Accounts")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FilledTonalButton(
                            onClick = { navController.navigate("calendars") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("View Calendars (Standalone)")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FilledTonalButton(
                            onClick = { navController.navigate("contacts") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("View Contacts")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FilledTonalButton(
                            onClick = { navController.navigate("tasks") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("View Tasks")
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { syncManager.syncAllNow() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Sync All Accounts")
                        }
                    }
                    
                    // Version info at bottom
                    Text(
                        text = "Built with: Kotlin 1.9.24, Compose, Hilt, KSP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
            }
        }
        
        // Account list screen (main screen)
        composable("accounts") {
            AccountListScreen(
                onAddAccount = { 
                    navController.navigate("add_account")
                },
                onAccountClick = { accountId ->
                    navController.navigate("account/$accountId")
                },
                onAccountLongClick = { accountId ->
                    navController.navigate("account/$accountId/settings")
                },
                onNavigateToAbout = {
                    navController.navigate("about")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToGuides = {
                    navController.navigate("guides")
                },
                onNavigateToFaq = {
                    navController.navigate("faq")
                }
            )
        }
        
        // Account detail screen
        composable(
            route = "account/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L
            AccountDetailScreen(
                accountId = accountId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { accId ->
                    navController.navigate("account/$accId/settings")
                },
                onNavigateToCalendarDetails = { calendarId ->
                    navController.navigate("calendar/$calendarId")
                },
                onNavigateToAddressBookDetails = { addressBookId ->
                    navController.navigate("addressbook/$addressBookId")
                }
            )
        }
        
        // Account settings screen
        composable(
            route = "account/{accountId}/settings",
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L
            com.davy.ui.screens.AccountSettingsScreen(
                accountId = accountId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Add account screen
        composable("add_account") {
            AddAccountScreen(
                onNavigateBack = { navController.popBackStack() },
                onAccountCreated = { 
                    navController.popBackStack()
                }
            )
        }
        
        // Calendar list screen (standalone)
        composable("calendars") {
            CalendarListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { calendarId ->
                    navController.navigate("calendar/$calendarId")
                }
            )
        }
        
        // Calendar detail screen
        composable(
            route = "calendar/{calendarId}",
            arguments = listOf(navArgument("calendarId") { type = NavType.LongType })
        ) { backStackEntry ->
            val calendarId = backStackEntry.arguments?.getLong("calendarId") ?: 0L
            CalendarDetailsScreen(
                calendarId = calendarId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Calendar event detail screen
        composable(
            route = "event/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId") ?: 0L
            CalendarEventScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Contact list screen
        composable("contacts") {
            ContactListScreen(
                onNavigateBack = { navController.popBackStack() },
                onContactClick = { contactId ->
                    navController.navigate("contact/$contactId")
                }
            )
        }
        
        // Contact detail screen
        composable(
            route = "contact/{contactId}",
            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: 0L
            ContactDetailScreen(
                contactId = contactId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Address book detail screen
        composable(
            route = "addressbook/{addressBookId}",
            arguments = listOf(navArgument("addressBookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val addressBookId = backStackEntry.arguments?.getLong("addressBookId") ?: 0L
            AddressBookDetailsScreen(
                addressBookId = addressBookId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Task lists screen
        composable("tasks") {
            TaskListScreen(
                onNavigateBack = { navController.popBackStack() },
                onTaskListClick = { taskListId ->
                    navController.navigate("tasklist/$taskListId")
                }
            )
        }
        
        // Task screen (for a specific task list)
        composable(
            route = "tasklist/{taskListId}",
            arguments = listOf(navArgument("taskListId") { type = NavType.LongType })
        ) { backStackEntry ->
            val taskListId = backStackEntry.arguments?.getLong("taskListId") ?: 0L
            TaskScreen(
                taskListId = taskListId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("about") {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("guides") {
            com.davy.ui.screens.GuidesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("faq") {
            com.davy.ui.screens.FaqScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("settings") {
            com.davy.ui.screens.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
