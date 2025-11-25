package com.davy.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davy.R
import kotlinx.coroutines.launch

private const val PREFS_NAME = "app_settings"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

/**
 * Data class representing a single onboarding page.
 */
data class OnboardingPage(
    val icon: ImageVector,
    val titleResId: Int,
    val descriptionResId: Int
)

/**
 * Onboarding screen shown to first-time users.
 * Displays a series of pages explaining the app's features.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Default.CalendarMonth,
                titleResId = R.string.onboarding_welcome_title,
                descriptionResId = R.string.onboarding_welcome_description
            ),
            OnboardingPage(
                icon = Icons.Default.AccountCircle,
                titleResId = R.string.onboarding_add_account_title,
                descriptionResId = R.string.onboarding_add_account_description
            ),
            OnboardingPage(
                icon = Icons.Default.Sync,
                titleResId = R.string.onboarding_sync_title,
                descriptionResId = R.string.onboarding_sync_description
            ),
            OnboardingPage(
                icon = Icons.Default.Event,
                titleResId = R.string.onboarding_calendar_title,
                descriptionResId = R.string.onboarding_calendar_description
            ),
            OnboardingPage(
                icon = Icons.Default.Contacts,
                titleResId = R.string.onboarding_contacts_title,
                descriptionResId = R.string.onboarding_contacts_description
            )
        )
    }
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button - more prominent, positioned with top padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, end = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (!isLastPage) {
                    FilledTonalButton(
                        onClick = {
                            completeOnboarding(context)
                            onComplete()
                        }
                    ) {
                        Text(stringResource(id = R.string.onboarding_skip))
                    }
                } else {
                    // Maintain layout space when on last page
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Page indicators
            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { index ->
                    PageIndicator(isSelected = index == pagerState.currentPage)
                }
            }
            
            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Text(stringResource(id = R.string.onboarding_back))
                    }
                } else {
                    Spacer(modifier = Modifier.width(100.dp))
                }
                
                // Next/Get Started button
                Button(
                    onClick = {
                        if (isLastPage) {
                            completeOnboarding(context)
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (isLastPage) {
                            stringResource(id = R.string.onboarding_get_started)
                        } else {
                            stringResource(id = R.string.onboarding_next)
                        }
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Title
        Text(
            text = stringResource(id = page.titleResId),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            text = stringResource(id = page.descriptionResId),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun PageIndicator(isSelected: Boolean) {
    val width by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 8.dp,
        label = "indicator_width"
    )
    
    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
    )
}

/**
 * Mark onboarding as complete in SharedPreferences.
 */
private fun completeOnboarding(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ONBOARDING_COMPLETE, true)
        .apply()
}

/**
 * Check if onboarding has been completed.
 */
fun isOnboardingComplete(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDING_COMPLETE, false)
}

/**
 * Reset onboarding state (for testing/replay).
 */
fun resetOnboarding(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ONBOARDING_COMPLETE, false)
        .apply()
}
