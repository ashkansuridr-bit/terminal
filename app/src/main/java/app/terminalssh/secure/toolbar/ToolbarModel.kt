package app.terminalssh.secure.toolbar

/**
 * The toolbar's domain model.
 *
 * The old toolbar was a hardcoded list of buttons buried in `TerminalScreen`. Everything a
 * user might want to change — which keys exist, what they do, how they look — lived in
 * Kotlin source. This models the bar as data: an ordered layout of keys, each key carrying
 * a typed action and its own gestures and appearance, grouped into named profiles and
 * rendered through a theme.
 *
 * Nothing here depends on Android or on the terminal library, so all of the ordering,
 * validation and serialisation rules are testable on a plain JVM. The mapping from an
 * action to a real terminal byte happens in the dispatcher, not in the model.
 */

/** The three latching modifiers, mirroring `TerminalModifier` without importing it. */
enum class ToolbarModifier { CTRL, ALT, SHIFT }

/**
 * Every special key the bar can send.
 *
 * These are named after what the user presses, not after the library constant, so the
 * dispatcher is the single place that has to know termlib exists.
 */
enum class ToolbarSpecialKey {
    ESCAPE, TAB, BACK_TAB, ENTER, BACKSPACE,
    HOME, END, PAGE_UP, PAGE_DOWN,
    UP, DOWN, LEFT, RIGHT,
    INSERT, DELETE,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}

/** A gesture that can be bound to a key, in addition to its primary tap action. */
enum class ToolbarGesture { DOUBLE_TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT }

/** How wide a key renders relative to the standard 48dp target. */
enum class KeyWidth { NARROW, NORMAL, WIDE }

/** A small semantic style bucket the renderer maps onto the active theme. */
enum class KeyStyle { DEFAULT, ACCENT, DANGER, MUTED, OUTLINE }

/**
 * A typed toolbar action.
 *
 * Split into two families on purpose: [TerminalInput] actions produce bytes that go to the
 * SSH server, [UiControl] actions only move around the app. Keeping them apart means a
 * mis-mapped button can never silently send something to the server, and the dispatcher
 * can be validated exhaustively.
 */
sealed interface ToolbarAction {
    /** Goes to the terminal emulator / SSH channel. */
    sealed interface TerminalInput : ToolbarAction

    /** Application/UI only; never writes to the session. */
    sealed interface UiControl : ToolbarAction
}

data class SpecialKeyAction(val key: ToolbarSpecialKey) : ToolbarAction.TerminalInput

/** A Ctrl+letter control code, e.g. `C` for Ctrl+C. Only A–Z (and `@`–`_`) are valid. */
data class ControlAction(val letter: Char) : ToolbarAction.TerminalInput

/** Multi-character insertion — treated as text, never as one terminal character. */
data class TextAction(val text: String) : ToolbarAction.TerminalInput

/** Latches or locks a modifier. */
data class ModifierAction(val modifier: ToolbarModifier, val lock: Boolean = false) : ToolbarAction.TerminalInput

data class SnippetAction(val snippetId: String) : ToolbarAction.TerminalInput

data class MacroAction(val macroId: String) : ToolbarAction.TerminalInput

data object PasteAction : ToolbarAction.TerminalInput

data object ReconnectAction : ToolbarAction.TerminalInput

data object OpenKeyboardAction : ToolbarAction.UiControl

data object HideKeyboardAction : ToolbarAction.UiControl

data object ToggleComposeAction : ToolbarAction.UiControl

data object OpenSnippetsAction : ToolbarAction.UiControl

data object OpenAgentsAction : ToolbarAction.UiControl

data object OpenPortForwardAction : ToolbarAction.UiControl

data object OpenSettingsAction : ToolbarAction.UiControl

data object OpenToolbarEditorAction : ToolbarAction.UiControl

data class ChangeFontSizeAction(val delta: Int) : ToolbarAction.UiControl

data class SwitchProfileAction(val profileId: String) : ToolbarAction.UiControl

/**
 * One key on the bar.
 *
 * [id] is stable across renames so a gesture binding or a saved layout keeps pointing at
 * the same logical key. [gestures] holds only the *extra* bindings; the primary action is
 * always [action] and is what a plain tap does.
 */
data class ToolKeySpec(
    val id: String,
    val label: String,
    val action: ToolbarAction,
    val contentDescription: String? = null,
    val width: KeyWidth = KeyWidth.NORMAL,
    val style: KeyStyle = KeyStyle.DEFAULT,
    val gestures: Map<ToolbarGesture, ToolbarAction> = emptyMap(),
    val repeatWhileHeld: Boolean = false,
    val enabled: Boolean = true,
    val pinned: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "key id must not be blank" }
        require(label.isNotEmpty()) { "key $id needs a visible label" }
        require(label.length <= MAX_LABEL) { "key $id label is too long" }
    }

    companion object {
        const val MAX_LABEL = 24
    }
}

/** The supported shape of the bar. */
enum class ToolbarMode {
    /** One scrollable row. */
    SINGLE_ROW,

    /** Two rows when the window is wide enough, otherwise one. */
    DOUBLE_ROW,

    /** Two always-scrollable rows. */
    EXPANDED,

    /** One short row with no optional keys. */
    COMPACT,

    /** A few pinned essentials only. */
    MINIMAL,

    /** One row at a time, paged with ‹ ›. */
    MULTI_PAGE,
}

/**
 * An ordered set of rows of keys.
 *
 * Rows are stored as lists of [ToolKeySpec] rather than as ids into a shared catalog: a
 * profile is the unit of export and import, and a self-contained layout cannot be broken
 * by a key that was deleted in another profile.
 */
data class ToolbarLayout(
    val rows: List<List<ToolKeySpec>>,
    val mode: ToolbarMode = ToolbarMode.SINGLE_ROW,
) {
    init {
        require(rows.isNotEmpty()) { "a layout needs at least one row" }
        require(rows.all { it.isNotEmpty() }) { "no row may be empty" }
        val ids = rows.flatten().map { it.id }
        require(ids.toSet().size == ids.size) { "duplicate key ids in layout" }
    }

    val keys: List<ToolKeySpec> get() = rows.flatten()

    fun keyById(id: String): ToolKeySpec? = rows.flatten().firstOrNull { it.id == id }

    fun redraw(newRows: List<List<ToolKeySpec>>): ToolbarLayout {
        val cleaned = newRows.filter { it.isNotEmpty() }
        return copy(rows = cleaned.ifEmpty { rows })
    }

    companion object {
        const val MAX_ROWS = 4
        const val MAX_KEYS = 48
    }
}

/**
 * A colour/geometry theme for the bar.
 *
 * Colours are `0xAARRGGBB` longs so the same value survives JSON, Compose and the
 * screenshot tests without a conversion table. Names are shown to the user; ids never
 * change.
 */
data class ToolbarTheme(
    val id: String,
    val name: String,
    val keyBackground: Long,
    val keyLabel: Long,
    val activeBackground: Long,
    val activeLabel: Long,
    val borderColor: Long,
    val keySurface: Long,
    val borderWidthDp: Int = 1,
    val cornerRadiusDp: Int = 10,
    val horizontalGapDp: Int = 6,
    val verticalGapDp: Int = 6,
    val toolbarHeightDp: Int = 0,
    val minKeyWidthDp: Int = 48,
    val monochrome: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "theme id must not be blank" }
        require(borderWidthDp in 0..8) { "theme $id border width out of range" }
        require(cornerRadiusDp in 0..32) { "theme $id corner radius out of range" }
        require(horizontalGapDp in 0..24 && verticalGapDp in 0..24) { "theme $id gap out of range" }
        require(minKeyWidthDp in 40..120) { "theme $id min key width out of range" }
    }
}

/** A named, self-contained toolbar: what the user switches between. */
data class ToolbarProfile(
    val id: String,
    val name: String,
    val description: String,
    val iconToken: String,
    val themeId: String,
    val layout: ToolbarLayout,
    val macroIds: List<String> = emptyList(),
    val builtIn: Boolean = false,
    /** Extra keys the bar may show but the layout need not display (e.g. an overflow page). */
    val optionalKeys: List<ToolKeySpec> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "profile id must not be blank" }
        require(name.isNotBlank()) { "profile $id needs a name" }
        require(name.length <= 40) { "profile $id name is too long" }
        require(description.length <= 160) { "profile $id description is too long" }
    }
}

/** One step of a macro. Delays are explicit so timing is part of the plan, not a side effect. */
sealed interface MacroStep {
    data class Text(val text: String) : MacroStep
    data class Special(val key: ToolbarSpecialKey) : MacroStep
    data class Controls(val letter: Char) : MacroStep
    data class Modifier(val modifier: ToolbarModifier, val lock: Boolean = false) : MacroStep
    data class Delay(val millis: Int) : MacroStep
}

/**
 * A reusable, ordered sequence of terminal input.
 *
 * Macros are inert data. They do not capture passwords, they are never auto-executed on
 * import, and the engine refuses to plan one that exceeds its limits.
 */
data class MacroDef(
    val id: String,
    val name: String,
    val description: String = "",
    val steps: List<MacroStep> = emptyList(),
    val builtIn: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "macro id must not be blank" }
        require(name.isNotBlank()) { "macro $id needs a name" }
        require(name.length <= 40) { "macro $id name is too long" }
    }
}
