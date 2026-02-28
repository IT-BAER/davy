package com.davy.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.davy.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
            
            // Pager content with parallax effects
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    pagerState = pagerState,
                    currentPage = page,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    pagerState: PagerState,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    // Calculate page offset for parallax and scaling effects
    val pageOffset = ((pagerState.currentPage - currentPage) + pagerState.currentPageOffsetFraction).absoluteValue
    
    // Animated values based on page visibility
    val scale by animateFloatAsState(
        targetValue = lerp(0.85f, 1f, 1f - pageOffset.coerceIn(0f, 1f)),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = lerp(0.5f, 1f, 1f - pageOffset.coerceIn(0f, 1f)),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "alpha"
    )
    
    // Staggered entrance animations
    var showIcon by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    
    LaunchedEffect(pagerState.currentPage == currentPage) {
        if (pagerState.currentPage == currentPage) {
            showIcon = false
            showTitle = false
            showDescription = false
            delay(100)
            showIcon = true
            delay(150)
            showTitle = true
            delay(150)
            showDescription = true
        }
    }
    
    // Continuous subtle floating animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val iconFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconFloat"
    )
    
    // Icon pulse animation
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    Column(
        modifier = modifier
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated Icon with floating effect
        AnimatedVisibility(
            visible = showIcon,
            enter = scaleIn(
                initialScale = 0.3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(400)),
            exit = scaleOut() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        translationY = -iconFloat
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
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
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Animated Title with slide up effect
        AnimatedVisibility(
            visible = showTitle,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically() + fadeOut()
        ) {
            Text(
                text = stringResource(id = page.titleResId),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animated Description with fade and slide
        AnimatedVisibility(
            visible = showDescription,
            enter = slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(500)),
            exit = slideOutVertically() + fadeOut()
        ) {
            Text(
                text = stringResource(id = page.descriptionResId),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun PageIndicator(isSelected: Boolean) {
    val width by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicator_width"
    )
    
    val height by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicator_height"
    )
    
    val color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        animationSpec = tween(300),
        label = "indicator_color"
    )
    
    Box(
        modifier = Modifier
            .height(height)
            .width(width)
            .clip(CircleShape)
            .background(color)
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
