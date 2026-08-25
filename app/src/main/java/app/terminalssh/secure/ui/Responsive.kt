package app.terminalssh.secure.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width bands the layout actually changes at.
 *
 * These are the Material window-size-class breakpoints. They are used instead of a device
 * check because "phone" is not a size: a folded Fold is 320dp wide, an unfolded one is
 * 674dp, and both are the same phone in the same hand a second apart.
 */
enum class WidthClass {
    /** Under 600dp — every phone in portrait, and small phones in landscape. */
    COMPACT,

    /** 600–839dp — large phone landscape, small tablet, unfolded foldable. */
    MEDIUM,

    /** 840dp and up — tablets and desktop-sized windows. */
    EXPANDED,
    ;

    val isCompact: Boolean get() = this == COMPACT
    val isAtLeastMedium: Boolean get() = this != COMPACT
}

/** Height bands, which matter for whether a keyboard leaves room for content. */
enum class HeightClass {
    /** Under 480dp — almost always a phone in landscape with the keyboard up. */
    COMPACT,
    MEDIUM,
    EXPANDED,
}

data class WindowSize(val width: WidthClass, val height: HeightClass) {
    /**
     * True when vertical space is the scarce resource: landscape phones, and any window
     * short enough that a soft keyboard would leave almost nothing visible. Screens use
     * this to drop decorative headers rather than shrink the content people came for.
     */
    val prefersDenseVertical: Boolean get() = height == HeightClass.COMPACT
}

@Composable
@ReadOnlyComposable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    return WindowSize(
        width = widthClassFor(configuration.screenWidthDp.dp),
        height = heightClassFor(configuration.screenHeightDp.dp),
    )
}

fun widthClassFor(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.COMPACT
    width < 840.dp -> WidthClass.MEDIUM
    else -> WidthClass.EXPANDED
}

fun heightClassFor(height: Dp): HeightClass = when {
    height < 480.dp -> HeightClass.COMPACT
    height < 900.dp -> HeightClass.MEDIUM
    else -> HeightClass.EXPANDED
}

/**
 * Horizontal page margin for the width band.
 *
 * A fixed 20dp margin that reads well on a 360dp phone leaves an 840dp tablet with a
 * single line of text stretched edge to edge, which is unreadable for a different reason.
 */
fun WidthClass.pageMargin(): Dp = when (this) {
    WidthClass.COMPACT -> 24.dp
    WidthClass.MEDIUM -> 32.dp
    WidthClass.EXPANDED -> 48.dp
}

/**
 * Maximum width for a column of running text or form fields. Beyond roughly this, the
 * eye loses the start of the next line.
 */
fun WidthClass.contentMaxWidth(): Dp = when (this) {
    WidthClass.COMPACT -> Dp.Unspecified
    WidthClass.MEDIUM -> 600.dp
    WidthClass.EXPANDED -> 720.dp
}
