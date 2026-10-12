package app.terminalssh.secure.toolbar

/**
 * The catalogue a user picks new keys from.
 *
 * Grouping is by what the key is *for*, because that is how someone looks for it ("I need the
 * pipe character" or "I need Ctrl+C"), not by how it is encoded. Every entry produces a real
 * [ToolKeySpec] with a real [ToolbarAction]; there are no decorative or disabled entries, and
 * nothing here is app-specific UI chrome beyond the handful of navigation shortcuts that the
 * bar already offered.
 */
object ToolbarKeyPalette {

    data class Entry(
        val label: String,
        val description: String,
        val group: Group,
        val action: ToolbarAction,
        val width: KeyWidth = KeyWidth.NORMAL,
        val style: KeyStyle = KeyStyle.DEFAULT,
        val repeatWhileHeld: Boolean = false,
        /** A hint for the generated id; the editor makes it unique. */
        val idHint: String,
    )

    enum class Group(val displayName: String) {
        MODIFIERS("Modifiers"),
        NAVIGATION("Navigation"),
        EDITING("Editing"),
        CONTROL("Control codes"),
        SYMBOLS("Symbols"),
        TYPING("Common typing"),
        APP("App"),
    }

    private fun special(
        key: ToolbarSpecialKey,
        label: String,
        group: Group,
        width: KeyWidth = KeyWidth.NORMAL,
        repeatWhileHeld: Boolean = false,
    ) = Entry(
        label = label,
        description = key.name.lowercase().replace('_', ' '),
        group = group,
        action = SpecialKeyAction(key),
        width = width,
        repeatWhileHeld = repeatWhileHeld,
        idHint = "k_" + key.name.lowercase(),
    )

    private fun control(letter: Char, group: Group = Group.CONTROL) = Entry(
        label = "^${letter.uppercaseChar()}",
        description = "Control+${letter.uppercaseChar()}",
        group = group,
        action = ControlAction(letter),
        idHint = "ctrl_" + letter.lowercaseChar(),
    )

    private fun symbol(char: Char) = Entry(
        label = char.toString(),
        description = "Type $char",
        group = Group.SYMBOLS,
        action = TextAction(char.toString()),
        idHint = "sym_" + char.code,
    )

    val entries: List<Entry> = buildList {
        // Modifiers.
        add(Entry("Ctrl", "Control modifier", Group.MODIFIERS, ModifierAction(ToolbarModifier.CTRL), style = KeyStyle.ACCENT, idHint = "mod_ctrl"))
        add(Entry("Alt", "Alt modifier", Group.MODIFIERS, ModifierAction(ToolbarModifier.ALT), style = KeyStyle.ACCENT, idHint = "mod_alt"))
        add(Entry("Shift", "Shift modifier", Group.MODIFIERS, ModifierAction(ToolbarModifier.SHIFT), style = KeyStyle.ACCENT, idHint = "mod_shift"))
        add(Entry("Ctrl🔒", "Lock Control until tapped again", Group.MODIFIERS, ModifierAction(ToolbarModifier.CTRL, lock = true), style = KeyStyle.OUTLINE, idHint = "mod_ctrl_lock"))

        // Navigation.
        add(special(ToolbarSpecialKey.UP, "↑", Group.NAVIGATION, repeatWhileHeld = true))
        add(special(ToolbarSpecialKey.DOWN, "↓", Group.NAVIGATION, repeatWhileHeld = true))
        add(special(ToolbarSpecialKey.LEFT, "←", Group.NAVIGATION, repeatWhileHeld = true))
        add(special(ToolbarSpecialKey.RIGHT, "→", Group.NAVIGATION, repeatWhileHeld = true))
        add(special(ToolbarSpecialKey.HOME, "Home", Group.NAVIGATION))
        add(special(ToolbarSpecialKey.END, "End", Group.NAVIGATION))
        add(special(ToolbarSpecialKey.PAGE_UP, "PgUp", Group.NAVIGATION))
        add(special(ToolbarSpecialKey.PAGE_DOWN, "PgDn", Group.NAVIGATION))

        // Editing / terminal keys.
        add(special(ToolbarSpecialKey.ESCAPE, "Esc", Group.EDITING))
        add(special(ToolbarSpecialKey.TAB, "Tab", Group.EDITING))
        add(special(ToolbarSpecialKey.BACK_TAB, "⇤", Group.EDITING, width = KeyWidth.NARROW))
        add(special(ToolbarSpecialKey.ENTER, "⏎", Group.EDITING))
        add(special(ToolbarSpecialKey.BACKSPACE, "⌫", Group.EDITING, repeatWhileHeld = true))
        add(special(ToolbarSpecialKey.INSERT, "Ins", Group.EDITING))
        add(special(ToolbarSpecialKey.DELETE, "Del", Group.EDITING, repeatWhileHeld = true))
        add(special(ToolbarSpecialKey.F1, "F1", Group.EDITING))
        add(special(ToolbarSpecialKey.F2, "F2", Group.EDITING))
        add(special(ToolbarSpecialKey.F3, "F3", Group.EDITING))
        add(special(ToolbarSpecialKey.F4, "F4", Group.EDITING))
        add(special(ToolbarSpecialKey.F5, "F5", Group.EDITING))
        add(special(ToolbarSpecialKey.F6, "F6", Group.EDITING))
        add(special(ToolbarSpecialKey.F7, "F7", Group.EDITING))
        add(special(ToolbarSpecialKey.F8, "F8", Group.EDITING))
        add(special(ToolbarSpecialKey.F9, "F9", Group.EDITING))
        add(special(ToolbarSpecialKey.F10, "F10", Group.EDITING))
        add(special(ToolbarSpecialKey.F11, "F11", Group.EDITING))
        add(special(ToolbarSpecialKey.F12, "F12", Group.EDITING))

        // Control codes.
        listOf('C', 'D', 'L', 'Z', 'A', 'E', 'U', 'K', 'W', 'R', 'O', 'X').forEach { add(control(it)) }

        // Symbols.
        listOf('|', '/', '-', '~', '`', '$', '&', '*', '?', ':', '!', '=', '>', '<', '(', ')', '[', ']', '{', '}', '"', '\'', '@', '#', '%', '^', '+', '.', ',', ';').forEach { add(symbol(it)) }

        // Common typing.
        add(Entry("sudo ", "Insert sudo", Group.TYPING, TextAction("sudo "), width = KeyWidth.WIDE, idHint = "text_sudo"))
        add(Entry("cd ", "Insert cd", Group.TYPING, TextAction("cd "), idHint = "text_cd"))
        add(Entry("ls -la", "List files in detail", Group.TYPING, TextAction("ls -la"), width = KeyWidth.WIDE, idHint = "text_ls"))
        add(Entry("git status", "Run git status", Group.TYPING, TextAction("git status"), width = KeyWidth.WIDE, idHint = "text_git_status"))

        // App actions.
        add(Entry("📋", "Paste from clipboard", Group.APP, PasteAction, idHint = "ui_paste"))
        add(Entry("✂", "Snippets", Group.APP, OpenSnippetsAction, idHint = "ui_snippets"))
        add(Entry("🤖", "Agents", Group.APP, OpenAgentsAction, idHint = "ui_agents"))
        add(Entry("⚡", "Port forwarding", Group.APP, OpenPortForwardAction, idHint = "ui_port_forward"))
        add(Entry("A+", "Bigger font", Group.APP, ChangeFontSizeAction(1), width = KeyWidth.NARROW, idHint = "ui_font_up"))
        add(Entry("A-", "Smaller font", Group.APP, ChangeFontSizeAction(-1), width = KeyWidth.NARROW, idHint = "ui_font_down"))
        add(Entry("⚙", "Settings", Group.APP, OpenSettingsAction, idHint = "ui_settings"))
        add(Entry("Toolbar", "Edit key bar", Group.APP, OpenToolbarEditorAction, width = KeyWidth.WIDE, idHint = "ui_editor"))
        add(Entry("Reconnect", "Reconnect session", Group.APP, ReconnectAction, width = KeyWidth.WIDE, style = KeyStyle.DANGER, idHint = "ui_reconnect"))
    }

    /** Builds a concrete key from a palette entry, resolving a collision-free id in [layout]. */
    fun materialise(entry: Entry, layout: ToolbarLayout): ToolKeySpec = ToolKeySpec(
        id = ToolbarEditor.uniqueId(layout, entry.idHint),
        label = entry.label,
        action = entry.action,
        contentDescription = entry.description,
        width = entry.width,
        style = entry.style,
        repeatWhileHeld = entry.repeatWhileHeld,
    )
}
