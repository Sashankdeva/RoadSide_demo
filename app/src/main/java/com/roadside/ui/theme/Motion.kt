package com.roadside.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * The RoadSide motion specification.
 *
 * Motion here is confirmation, never decoration: it tells the rider that a control was
 * registered, that the device is listening, that analysis is running, and that a result has
 * arrived. Durations stay short (150–420 ms) so nothing delays a diagnosis, and every
 * transition is interruptible.
 */
object Motion {

    /** Automotive standard easing — quick to leave, settled on arrival. */
    val standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    const val PRESS_MS = 90
    const val FAST_MS = 180
    const val SCREEN_MS = 320
    const val REVEAL_MS = 380

    /** Physical settle for things that appear rather than move. */
    fun <T> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * State-to-state transition inside one screen — idle → recording → analysing → result,
     * or camera → evidence. The new state rises slightly as it fades in, which reads as the
     * instrument settling rather than the page changing.
     */
    fun stateTransition(): ContentTransform =
        (fadeIn(tween(FAST_MS)) + slideInVertically(Motion.settle()) { it / 6 }) togetherWith
            fadeOut(tween(FAST_MS / 2))

    /** Evidence and results: a short rise and scale, staggered by [index]. */
    fun revealTransform(index: Int = 0): ContentTransform {
        val delay = index * 60
        return (fadeIn(tween(REVEAL_MS, delayMillis = delay)) +
            slideInVertically(tween(REVEAL_MS, delayMillis = delay, easing = decelerate)) { it / 3 } +
            scaleIn(tween(REVEAL_MS, delayMillis = delay, easing = decelerate), initialScale = 0.96f)
            ) togetherWith fadeOut(tween(FAST_MS / 2))
    }
}

/**
 * Screen-to-screen transition. Forward navigation carries content in from the right, back
 * sends it the other way, so the flow keeps a sense of direction.
 */
fun AnimatedContentTransitionScope<*>.screenTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return (slideIntoContainer(dir, tween(Motion.SCREEN_MS, easing = Motion.standard)) +
        fadeIn(tween(Motion.SCREEN_MS / 2))) togetherWith
        (slideOutOfContainer(dir, tween(Motion.SCREEN_MS, easing = Motion.standard)) +
            fadeOut(tween(Motion.SCREEN_MS / 2)))
}

/**
 * Press feedback for any tactile control: the surface yields to the finger and springs back.
 *
 * Returns the scale so callers can also drive a colour or elevation change from the same
 * interaction source.
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
}

/** Applies [rememberPressScale] to a modifier chain. */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f
): Modifier = composed {
    val scale by rememberPressScale(interactionSource, pressedScale)
    scale(scale)
}

@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
