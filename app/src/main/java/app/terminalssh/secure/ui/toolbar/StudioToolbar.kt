package app.terminalssh.secure.ui.toolbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.TerminalModifier
import app.terminalssh.secure.ssh.TerminalModifiers
import app.terminalssh.secure.toolbar.KeyStyle
import app.terminalssh.secure.toolbar.KeyWidth
import app.terminalssh.secure.toolbar.ModifierAction
import app.terminalssh.secure.toolbar.ToolKeySpec
import app.terminalssh.secure.toolbar.ToolbarGesture
import app.terminalssh.secure.toolbar.ToolbarMode
import app.terminalssh.secure.toolbar.ToolbarProfile
import app.terminalssh.secure.toolbar.ToolbarTheme
import kotlinx.coroutines.delay

/**
 * Renders the active [profile] through [theme] and reports gestures back as actions.
 *
 * The bar itself holds no state: no key list, no colours, no behaviour. Everything visible
 * comes from the profile, which is why the same composable serves every installed profile
 * and every user edit without a branch anywhere.
 *
 * The leading keyboard key is always present and is not part of the profile — on a phone it
 * is the way back to the soft keyboard, and a layout that could delete it could strand the
 * user. Its long press opens the customization screen, so the editor is reachable from the
 * bar even when the user has removed every other key.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StudioToolbar(
    profile: ToolbarProfile,
    theme: ToolbarTheme,
    modifiers: TerminalModifiers,
    locked: Set<TerminalModifier>,
    hapticsEnabled: Boolean,
    onKeyAction: (ToolKeySpec, ToolbarGesture?) -> Unit,
    onShowKeyboard: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = Color(theme.keySurface)
    BoxWithConstraints(modifier.fillMaxWidth().background(surface)) {
        val twoRowsPossible = maxWidth >= 600.dp && !WindowInsets.isImeVisible
        val mode = profile.layout.mode
        val rows = profile.layout.rows

        // MULTI_PAGE shows one row at a time so a big layout stays a fixed height; every
        // other mode is a single flattened strip (or two strips where there is width).
        if (mode == ToolbarMode.MULTI_PAGE && rows.size > 1) {
            var page by rememberSaveable(profile.id) { mutableIntStateOf(0) }
            val safePage = page.coerceIn(0, rows.lastIndex)
            val barHeight = barHeight(theme, twoRows = false)
            Row(
                Modifier.fillMaxWidth().height(barHeight).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyboardKey(theme, hapticsEnabled, onShowKeyboard, onOpenEditor)
                PageButton("‹", theme, enabled = safePage > 0) { page = safePage - 1 }
                KeyStrip(
                    keys = rows[safePage],
                    theme = theme,
                    modifiers = modifiers,
                    locked = locked,
                    hapticsEnabled = hapticsEnabled,
                    onKeyAction = onKeyAction,
                    modifier = Modifier.weight(1f),
                )
                PageButton("›", theme, enabled = safePage < rows.lastIndex) { page = safePage + 1 }
            }
            return@BoxWithConstraints
        }

        val twoRows = twoRowsPossible &&
            rows.size >= 2 &&
            (mode == ToolbarMode.DOUBLE_ROW || mode == ToolbarMode.EXPANDED)
        val barHeight = barHeight(theme, twoRows)

        Row(
            Modifier.fillMaxWidth().height(barHeight).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyboardKey(theme, hapticsEnabled, onShowKeyboard, onOpenEditor)

            if (twoRows) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(theme.verticalGapDp.dp),
                ) {
                    KeyStrip(rows[0], theme, modifiers, locked, hapticsEnabled, onKeyAction)
                    KeyStrip(rows[1], theme, modifiers, locked, hapticsEnabled, onKeyAction)
                }
            } else {
                // One scrollable strip. Flattening rather than truncating means a row that
                // does not fit is scrolled to, never silently dropped.
                KeyStrip(
                    keys = rows.flatten(),
                    theme = theme,
                    modifiers = modifiers,
                    locked = locked,
                    hapticsEnabled = hapticsEnabled,
                    onKeyAction = onKeyAction,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun barHeight(theme: ToolbarTheme, twoRows: Boolean): Dp = when {
    theme.toolbarHeightDp > 0 && twoRows -> theme.toolbarHeightDp.dp
    theme.toolbarHeightDp > 0 -> theme.toolbarHeightDp.dp
    twoRows -> 120.dp
    else -> 64.dp
}

@Composable
private fun KeyStrip(
    keys: List<ToolKeySpec>,
    theme: ToolbarTheme,
    modifiers: TerminalModifiers,
    locked: Set<TerminalModifier>,
    hapticsEnabled: Boolean,
    onKeyAction: (ToolKeySpec, ToolbarGesture?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(theme.horizontalGapDp.dp),
    ) {
        items(keys, key = { it.id }) { key ->
            val active = key.isActive(modifiers)
            val isLocked = key.isLocked(locked)
            StudioKey(
                label = key.label,
                description = key.announce(active, isLocked),
                theme = theme,
                style = key.style,
                width = key.width,
                enabled = key.enabled,
                active = active,
                locked = isLocked,
                repeatWhileHeld = key.repeatWhileHeld,
                gestures = key.gestures.keys,
                checkable = key.action is ModifierAction,
                hapticsEnabled = hapticsEnabled,
                onPrimary = { onKeyAction(key, null) },
                onGesture = { gesture -> onKeyAction(key, gesture) },
            )
        }
    }
}

/**
 * A key press is reported here exactly once; the caller decides what the action means.
 *
 * Repeat is driven off the press interaction rather than a raw pointer loop so it shares the
 * same press/release the ripple and scale already track, and so releasing the key cancels
 * the coroutine that repeats it.
 */
@Composable
private fun StudioKey(
    label: String,
    description: String,
    theme: ToolbarTheme,
    style: KeyStyle,
    width: KeyWidth,
    enabled: Boolean,
    active: Boolean,
    locked: Boolean,
    repeatWhileHeld: Boolean,
    gestures: Set<ToolbarGesture>,
    checkable: Boolean = false,
    hapticsEnabled: Boolean,
    onPrimary: () -> Unit,
    onGesture: (ToolbarGesture) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val isPressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "studio-key-press")
    var repeatedWhileHeld by remember { mutableStateOf(false) }

    val fire: () -> Unit = {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onPrimary()
    }

    if (enabled && repeatWhileHeld) {
        // Hold to repeat: a short guard delay keeps a deliberate single tap from
        // emitting two keystrokes, then it fires until the key is released.
        androidx.compose.runtime.LaunchedEffect(isPressed) {
            if (!isPressed) {
                repeatedWhileHeld = false
            } else {
                repeatedWhileHeld = false
                delay(REPEAT_DELAY_MS)
                while (isPressed) {
                    repeatedWhileHeld = true
                    onPrimary()
                    delay(REPEAT_INTERVAL_MS)
                }
            }
        }
    }

    val shape = RoundedCornerShape(theme.cornerRadiusDp.dp)
    val background = keyBackground(theme, style, active, locked, enabled)
    val labelColor = keyLabel(theme, style, active, locked, enabled)
    val minWidth = width.minWidth(theme)

    val swipe = remember(gestures) { gestures.any { it in SWIPE_GESTURES } }
    var gestureModifier: Modifier = Modifier
    if (swipe && enabled) {
        gestureModifier = gestureModifier.pointerInput(label, gestures) {
            var dx = 0f
            var dy = 0f
            var dragged = false
            val threshold = 24.dp.toPx()
            detectDragGestures(
                onDragStart = { dx = 0f; dy = 0f; dragged = false },
                onDrag = { change, amount ->
                    change.consume()
                    dx += amount.x
                    dy += amount.y
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > threshold) dragged = true
                },
                onDragEnd = {
                    if (dragged) {
                        val gesture = dominantSwipe(dx, dy)
                        if (gesture != null && gesture in gestures) onGesture(gesture)
                    }
                    dx = 0f
                    dy = 0f
                    dragged = false
                },
                onDragCancel = { dx = 0f; dy = 0f; dragged = false },
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = minWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .then(
                if (theme.borderWidthDp > 0 && (style == KeyStyle.OUTLINE || theme.borderWidthDp > 1)) {
                    Modifier.border(theme.borderWidthDp.dp, Color(theme.borderColor), shape)
                } else {
                    Modifier
                },
            )
            .then(gestureModifier)
            .combinedClickable(
                enabled = enabled,
                role = if (checkable) Role.Checkbox else Role.Button,
                onLongClick = if (ToolbarGesture.LONG_PRESS in gestures) {
                    { if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onGesture(ToolbarGesture.LONG_PRESS) }
                } else {
                    null
                },
                onDoubleClick = if (ToolbarGesture.DOUBLE_TAP in gestures) {
                    { onGesture(ToolbarGesture.DOUBLE_TAP) }
                } else {
                    null
                },
                interactionSource = interactions,
                indication = null,
                // combinedClickable still delivers the release click after a long hold.
                // Once repeat has emitted any keys, suppress that trailing click so a held
                // Backspace/Delete does not erase one extra character on release.
                onClick = {
                    if (!repeatWhileHeld || !repeatedWhileHeld) fire()
                },
            )
            .semantics(mergeDescendants = checkable) {
                contentDescription = description
                if (checkable) {
                    toggleableState = if (active) ToggleableState.On else ToggleableState.Off
                }
            }
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = labelColor, fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KeyboardKey(
    theme: ToolbarTheme,
    hapticsEnabled: Boolean,
    onShowKeyboard: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    StudioKey(
        label = "⌨",
        description = stringResource(R.string.show_keyboard),
        theme = theme,
        style = KeyStyle.DEFAULT,
        width = KeyWidth.NORMAL,
        enabled = true,
        active = false,
        locked = false,
        repeatWhileHeld = false,
        // Long press on the keyboard key is the always-present doorway into the editor.
        gestures = setOf(ToolbarGesture.LONG_PRESS),
        hapticsEnabled = hapticsEnabled,
        onPrimary = onShowKeyboard,
        onGesture = { onOpenEditor() },
    )
}

@Composable
private fun PageButton(label: String, theme: ToolbarTheme, enabled: Boolean, onClick: () -> Unit) {
    val color = Color(theme.keyLabel)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 36.dp)
            .clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
            .background(if (enabled) Color(theme.keyBackground) else Color(theme.keySurface))
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(color = color, fontSize = 18.sp))
    }
}

// --- helpers ---

private val SWIPE_GESTURES = setOf(
    ToolbarGesture.SWIPE_UP,
    ToolbarGesture.SWIPE_DOWN,
    ToolbarGesture.SWIPE_LEFT,
    ToolbarGesture.SWIPE_RIGHT,
)

private const val REPEAT_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 90L

private fun dominantSwipe(dx: Float, dy: Float): ToolbarGesture? = when {
    kotlin.math.abs(dx) >= kotlin.math.abs(dy) && dx > 0 -> ToolbarGesture.SWIPE_RIGHT
    kotlin.math.abs(dx) >= kotlin.math.abs(dy) && dx < 0 -> ToolbarGesture.SWIPE_LEFT
    dy > 0 -> ToolbarGesture.SWIPE_DOWN
    dy < 0 -> ToolbarGesture.SWIPE_UP
    else -> null
}

private fun KeyWidth.minWidth(theme: ToolbarTheme): Dp = when (this) {
    KeyWidth.NARROW -> (theme.minKeyWidthDp - 8).coerceAtLeast(40).dp
    KeyWidth.NORMAL -> theme.minKeyWidthDp.dp
    KeyWidth.WIDE -> (theme.minKeyWidthDp + 24).dp
}

private fun keyBackground(
    theme: ToolbarTheme,
    style: KeyStyle,
    active: Boolean,
    locked: Boolean,
    enabled: Boolean,
): Color {
    if (!enabled) return Color(theme.keySurface)
    if (active || locked) return Color(theme.activeBackground)
    return when (style) {
        KeyStyle.ACCENT -> Color(theme.activeBackground).copy(alpha = 0.30f)
        KeyStyle.DANGER -> Color(0xFF7F1D1D)
        KeyStyle.MUTED -> Color(theme.keyBackground).copy(alpha = 0.55f)
        KeyStyle.OUTLINE, KeyStyle.DEFAULT -> Color(theme.keyBackground)
    }
}

private fun keyLabel(
    theme: ToolbarTheme,
    style: KeyStyle,
    active: Boolean,
    locked: Boolean,
    enabled: Boolean,
): Color {
    if (!enabled) return Color(theme.keyLabel).copy(alpha = 0.35f)
    if (active || locked) return Color(theme.activeLabel)
    return when (style) {
        KeyStyle.MUTED -> Color(theme.keyLabel).copy(alpha = 0.6f)
        else -> Color(theme.keyLabel)
    }
}

/** Whether the key's action is a modifier that is currently latched on. */
private fun ToolKeySpec.isActive(modifiers: TerminalModifiers): Boolean {
    val modifier = (action as? ModifierAction)?.modifier ?: return false
    return when (modifier) {
        app.terminalssh.secure.toolbar.ToolbarModifier.CTRL -> modifiers.ctrl
        app.terminalssh.secure.toolbar.ToolbarModifier.ALT -> modifiers.alt
        app.terminalssh.secure.toolbar.ToolbarModifier.SHIFT -> modifiers.shift
    }
}

private fun ToolKeySpec.isLocked(locked: Set<TerminalModifier>): Boolean {
    val modifier = (action as? ModifierAction)?.modifier ?: return false
    val terminal = when (modifier) {
        app.terminalssh.secure.toolbar.ToolbarModifier.CTRL -> TerminalModifier.CTRL
        app.terminalssh.secure.toolbar.ToolbarModifier.ALT -> TerminalModifier.ALT
        app.terminalssh.secure.toolbar.ToolbarModifier.SHIFT -> TerminalModifier.SHIFT
    }
    return terminal in locked
}

@Composable
private fun ToolKeySpec.announce(active: Boolean, locked: Boolean): String {
    // Keep shipped accessibility names useful in each app locale. User-edited descriptions
    // remain verbatim; only the built-in default names map to localized resources.
    val base = when {
        id == "ctrl" && contentDescription == "Control" -> stringResource(R.string.tcs_control_modifier)
        id == "cc" && contentDescription == "Interrupt" -> stringResource(R.string.terminal_key_interrupt)
        id == "cd" && contentDescription == "End of file" -> stringResource(R.string.terminal_key_eof)
        else -> contentDescription ?: label
    }
    val suffix = when {
        locked -> stringResource(R.string.tcs_locked_suffix)
        active -> stringResource(R.string.tcs_active_suffix)
        else -> null
    }
    return if (suffix != null) "$base ($suffix)" else base
}
