package com.davy.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Animated success checkmark with green circle
 * @param modifier Modifier for layout
 * @param size Size of the checkmark in dp (default 80dp)
 * @param strokeWidth Width of the drawn lines (default 6dp)
 * @param color Color of the checkmark (default green)
 * @param onAnimationEnd Callback when animation completes
 */
@Composable
fun AnimatedSuccessCheck(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 6.dp,
    color: Color = Color(0xFF4CAF50),  // Material Green 500
    onAnimationEnd: (() -> Unit)? = null
) {
    var animationPlayed by remember { mutableStateOf(false) }
    
    // Circle animation (0 to 1)
    val circleAnimation = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        ),
        label = "circle_animation"
    )
    
    // Checkmark animation (0 to 1) - starts after circle animation begins
    val checkAnimation = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = 300,  // Delay to start after circle
            easing = FastOutSlowInEasing
        ),
        label = "check_animation",
        finishedListener = { 
            onAnimationEnd?.invoke()
        }
    )
    
    // Start animation on first composition
    LaunchedEffect(Unit) {
        animationPlayed = true
    }
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val strokeWidthPx = strokeWidth.toPx()
            
            // Draw animated circle
            drawCircle(
                color = color,
                radius = (canvasSize / 2f - strokeWidthPx / 2) * circleAnimation.value,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )
            
            // Draw animated checkmark
            if (circleAnimation.value > 0.5f) {  // Start drawing check after circle is halfway
                val checkPath = Path().apply {
                    val checkStartX = canvasSize * 0.25f
                    val checkStartY = canvasSize * 0.5f
                    val checkMidX = canvasSize * 0.42f
                    val checkMidY = canvasSize * 0.68f
                    val checkEndX = canvasSize * 0.75f
                    val checkEndY = canvasSize * 0.3f
                    
                    moveTo(checkStartX, checkStartY)
                    lineTo(checkMidX, checkMidY)
                    lineTo(checkEndX, checkEndY)
                }
                
                // Measure total path length
                val pathMeasure = PathMeasure()
                pathMeasure.setPath(checkPath, false)
                val pathLength = pathMeasure.length
                
                // Draw only the animated portion
                drawPath(
                    path = checkPath,
                    color = color,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(pathLength, pathLength),
                            phase = pathLength * (1f - checkAnimation.value)
                        )
                    )
                )
            }
        }
    }
}
