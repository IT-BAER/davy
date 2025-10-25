package com.davy.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import com.davy.sync.SyncManager
import com.davy.ui.navigation.NavGraph
import com.davy.ui.permissions.PermissionRequestScreen
import com.davy.ui.permissions.hasAllPermissions
import com.davy.ui.theme.DavyTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Main activity for DAVy application.
 * 
 * Single activity architecture using Jetpack Compose and Navigation.
 * 
 * Entry point: Checks permissions first, then displays splash/onboarding 
 * or main sync screen based on account status.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var syncManager: SyncManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen but don't block with keepOnScreenCondition
        // Let the splash naturally dismiss when first frame is ready
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display (full height to screen edges)
        // This makes the app draw behind system bars (status bar and navigation bar)
        enableEdgeToEdge()
        
        Timber.d("MainActivity onCreate")
        
        // Handle app shortcut actions
        handleShortcutIntent(intent)
        
        // Extract navigation target from intent (e.g., from notifications)
        val navigateTo = intent.getStringExtra("navigate_to")
        
        // PERFORMANCE: Set content FIRST to show UI immediately
        setContent {
            // Provide SyncManager through CompositionLocal to avoid
            // creating it in remember blocks (performance issue)
            CompositionLocalProvider(LocalSyncManager provides syncManager) {
                DavyApp(startDestination = navigateTo)
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }
    
    private fun handleShortcutIntent(intent: android.content.Intent?) {
        when (intent?.action) {
            "com.davy.ACTION_SYNC_ALL" -> {
                Timber.d("App shortcut: Sync All triggered")
                syncManager.syncAllNow()
            }
        }
    }
}

/**
 * Main composable for DAVy app.
 * 
 * Sets up theme, navigation, and main UI structure.
 * Handles permission requests before showing main app.
 * 
 * @param startDestination Optional deep link destination (e.g., from notifications)
 */
@Composable
fun DavyApp(startDestination: String? = null) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(hasAllPermissions(context)) }
    
    // Observe theme preference reactively
    val prefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var themeModeState by remember { 
        mutableStateOf(prefs.getInt("preferred_theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) 
    }
    
    // Listen to preference changes
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "preferred_theme") {
                themeModeState = prefs.getInt("preferred_theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    val isDark = when (themeModeState) {
        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isSystemInDarkTheme()
    }
    
    DavyTheme(darkTheme = isDark) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            if (hasPermissions) {
                // Permissions granted - show main app
                val navController = rememberNavController()
                
                // Navigate to deep link destination if provided
                LaunchedEffect(startDestination) {
                    if (startDestination != null) {
                        navController.navigate(startDestination)
                    }
                }
                
                // Use Scaffold with contentWindowInsets set to WindowInsets(0)
                // This prevents automatic padding and lets content extend to edges
                // Individual screens can apply their own insets as needed
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { paddingValues ->
                    NavGraph(
                        navController = navController,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            } else {
                // Permissions not granted - show permission request screen
                PermissionRequestScreen(
                    onPermissionsGranted = {
                        hasPermissions = true
                        Timber.d("All permissions granted")
                    }
                )
            }
        }
    }
}
