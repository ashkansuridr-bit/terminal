package app.terminalssh.secure.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

/**
 * One motion vocabulary for the whole app.
 *
 * Everything that moves is either responding to a touch or reporting real progress. A
 * terminal is a tool people use while tired and in a hurry; decorative motion in the way
 * of that is a cost, not a delight.
 */
object Motion {

    /** Standard Material easing: quick to leave, gentle to arrive. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things leaving the screen, where nobody is waiting on the result. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val QUICK_MS = 140
    const val NORMAL_MS = 260
    const val SLOW_MS = 420

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK_MS, easing = Standard)
    fun <T> normal(): FiniteAnimationSpec<T> = tween(NORMAL_MS, easing = Standard)

    /**
     * Touch feedback. A spring rather than a duration, because a press that is
     * interrupted mid-flight should redirect from where it is rather than snapping.
     */
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = 900f,
    )

    /** Content settling into place — list items, sheets, expanding rows. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f,
    )
}

/**
 * A shimmer sweep for content that is loading and whose shape is already known.
 *
 * Preferred over a spinner for the file browser: the rows are already laid out, so the
 * page does not jump when real data replaces the placeholder.
 */
@Composable
fun rememberShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-sweep",
    )
}

/**
 * Draws a moving highlight across whatever it modifies.
 *
 * @param progress 0f..1f sweep position, normally from [rememberShimmerProgress].
 */
fun Modifier.shimmer(progress: Float, highlight: Color): Modifier = composed {
    drawWithContent {
        drawContent()
        val sweepWidth = size.width * 0.4f
        val start = -sweepWidth + (size.width + sweepWidth * 2) * progress
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + sweepWidth, size.height),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }
}
