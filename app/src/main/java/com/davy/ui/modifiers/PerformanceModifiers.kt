package com.davy.ui.modifiers

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Performance-optimized modifiers following Compose best practices.
 * 
 * Key principle: Use LAMBDA versions of modifiers when reading frequently-changing state.
 * This defers state reads to layout/draw phases, skipping composition/recomposition.
 * 
 * Reference: https://developer.android.com/develop/ui/compose/performance/bestpractices#defer-reads
 */

/**
 * Offset modifier with lambda-based state reading.
 * 
 * **Best Practice**: Use this instead of `Modifier.offset(x: Dp, y: Dp)` when offset
 * is based on frequently-changing state (e.g., scroll position, animation values).
 * 
 * **Why**: Reads state in LAYOUT phase instead of COMPOSITION phase, allowing
 * Compose to skip recomposition entirely when only layout changes.
 * 
 * **Example Use Case**: Parallax scrolling, collapsing toolbars, scroll-based animations
 * 
 * ```kotlin
 * val scrollState = rememberScrollState()
 * 
 * // ❌ BAD: Triggers recomposition on every scroll pixel
 * val offset = with(LocalDensity.current) { scrollState.value.toDp() }
 * Modifier.offset(y = offset)
 * 
 * // ✅ GOOD: Skips recomposition, only re-layouts
 * Modifier.offsetLambda { IntOffset(0, scrollState.value / 2) }
 * ```
 */
@Stable
fun Modifier.offsetLambda(
    offset: () -> IntOffset
): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        val currentOffset = offset()
        placeable.place(currentOffset)
    }
}

/**
 * Draw background with lambda-based color reading.
 * 
 * **Best Practice**: Use this instead of `Modifier.background(color)` when color
 * is rapidly animating or changing frequently.
 * 
 * **Why**: Reads color in DRAW phase instead of COMPOSITION phase, allowing
 * Compose to skip both composition AND layout phases.
 * 
 * **Example Use Case**: Color animations, theme transitions, pulsing effects
 * 
 * ```kotlin
 * val color by animateColorBetween(Color.Cyan, Color.Magenta)
 * 
 * // ❌ BAD: Triggers full recomposition on every frame
 * Modifier.background(color)
 * 
 * // ✅ GOOD: Skips to draw phase only
 * Modifier.drawBackgroundLambda { drawRect(color) }
 * ```
 */
@Stable
fun Modifier.drawBackgroundLambda(
    draw: DrawScope.() -> Unit
): Modifier = this.drawBehind(draw)

/**
 * Graphics layer with lambda-based transformation reading.
 * 
 * **Best Practice**: Use lambda versions of graphicsLayer properties for
 * frequently-changing transformations.
 * 
 * **Why**: Defers reading transformation values to DRAW phase, avoiding
 * expensive recomposition for pure visual changes.
 * 
 * **Example Use Case**: Scroll-based alpha fading, rotation animations, scale effects
 * 
 * ```kotlin
 * val scrollState = rememberScrollState()
 * 
 * // ❌ BAD: Recomposes on every scroll pixel
 * val alpha = (1f - (scrollState.value / 500f)).coerceIn(0f, 1f)
 * Modifier.graphicsLayer(alpha = alpha)
 * 
 * // ✅ GOOD: Use graphicsLayer with lambda block
 * Modifier.graphicsLayer {
 *     alpha = (1f - (scrollState.value / 500f)).coerceIn(0f, 1f)
 * }
 * ```
 */
// Note: graphicsLayer already has lambda block support built-in
// This is just a reference example of the pattern

/**
 * Helper: Create a scroll-based alpha fade modifier.
 * Demonstrates deferred state reading for performance.
 */
@Stable
fun Modifier.scrollFadeOut(
    scrollOffset: () -> Int,
    fadeDistance: Float = 500f
): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeWithLayer(0, 0) {
            // Read scroll state HERE (layout phase) not in composition
            val offset = scrollOffset()
            alpha = (1f - (offset / fadeDistance)).coerceIn(0f, 1f)
        }
    }
}

/**
 * Helper: Create a parallax offset modifier.
 * Demonstrates deferred state reading for scroll-based effects.
 */
@Stable
fun Modifier.parallaxOffset(
    scrollOffset: () -> Int,
    ratio: Float = 0.5f
): Modifier = this.offsetLambda {
    // Read scroll state HERE (layout phase) not in composition
    val offset = scrollOffset()
    IntOffset(0, (offset * ratio).roundToInt())
}

/**
 * Performance Notes:
 * 
 * Compose has 3 phases:
 * 1. COMPOSITION - Build UI tree, expensive
 * 2. LAYOUT - Measure and position, medium cost
 * 3. DRAW - Render to canvas, cheapest
 * 
 * Lambda-based modifiers defer state reads to later phases:
 * - offset { } → reads in LAYOUT (skips composition)
 * - drawBehind { } → reads in DRAW (skips composition + layout)
 * - graphicsLayer { } → reads in DRAW (skips composition + layout)
 * 
 * This is CRITICAL for smooth 60fps animations and scrolling.
 * 
 * Rule of thumb:
 * - If state changes more than once per second → use lambda version
 * - Especially for: scroll, animations, gestures, timers
 */
