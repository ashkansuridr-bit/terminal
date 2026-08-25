package app.terminalssh.secure.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * Before this existed the codebase used 6, 7, 9, 10, 13, 14, 15, 18, 22, 26, 44 and 50dp
 * alongside the 4-multiples. No single one of those looks wrong on its own, which is
 * exactly why they accumulate — and the result is that nothing on a screen lines up with
 * anything else. A reader cannot say why it looks unfinished, only that it does.
 *
 * A 4dp grid is what Android's own components are built on, so staying on it also means
 * app content aligns with Material's internal padding instead of missing it by a pixel or
 * two.
 *
 * Names describe the role, not the number, so a later decision to loosen the whole app is
 * one edit rather than a hundred.
 */
object Space {
    /** Hairline gaps: between an icon and its label, inside a chip. */
    val xxs: Dp = 2.dp

    /** Tight: stacked lines of the same item, chip-to-chip. */
    val xs: Dp = 4.dp

    /** Default gap between closely related elements. */
    val sm: Dp = 8.dp

    /** Between distinct elements inside one group — the most common value. */
    val md: Dp = 12.dp

    /** Between groups; standard content inset on a phone. */
    val lg: Dp = 16.dp

    /** Section separation. */
    val xl: Dp = 24.dp

    /** Major breaks, empty-state breathing room. */
    val xxl: Dp = 32.dp

    /** Screen-level top and bottom margins on larger windows. */
    val xxxl: Dp = 48.dp
}

/**
 * Fixed sizes that are not spacing: touch targets and control heights.
 *
 * Kept apart from [Space] because they answer a different question. Spacing is a
 * relationship between two things; these are the size of one thing, and the minimum is a
 * platform requirement rather than a taste decision.
 */
object Size {
    /** Android's minimum touch target. Nothing tappable may be smaller. */
    val touchTarget: Dp = 48.dp

    /** Comfortable target for a primary action, which is worth over-sizing. */
    val touchTargetLarge: Dp = 56.dp

    /** Inline icon beside text. */
    val iconSmall: Dp = 16.dp

    /** Standalone icon in a row. */
    val icon: Dp = 24.dp

    /** Avatar or leading badge in a list row. */
    val avatar: Dp = 40.dp

    /** Height of a single-line input or list row. */
    val rowHeight: Dp = 56.dp

    /** Divider and border thickness. */
    val hairline: Dp = 1.dp

    /** The environment band and other emphasis rails. */
    val rail: Dp = 4.dp
}
