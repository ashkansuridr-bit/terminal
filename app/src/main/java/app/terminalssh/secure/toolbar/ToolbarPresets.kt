package app.terminalssh.secure.toolbar

/**
 * The shipped themes, profiles and safe starter macros.
 *
 * These are the defaults a first-run user sees, and the templates a "Vim" or "tmux" user
 * can switch to in one tap. They are plain data so they can be exported, diffed and
 * tested. Nothing here is destructive: the starter macros are read-only commands.
 */
object ToolbarThemes {

    val materialDark = theme(
        id = "material_dark", name = "Material Dark",
        keyBg = 0xFF2A2F38, keyLabel = 0xFFE6EAF2, activeBg = 0xFF3DDC97, activeLabel = 0xFF04231A,
        border = 0xFF3A414D, surface = 0xFF1E222A,
    )

    val materialLight = theme(
        id = "material_light", name = "Material Light",
        keyBg = 0xFFEFF1F5, keyLabel = 0xFF1B1F27, activeBg = 0xFF0E8F6B, activeLabel = 0xFFF3FFF9,
        border = 0xFFD3D8E0, surface = 0xFFF7F8FA,
    )

    val amoled = theme(
        id = "amoled", name = "AMOLED",
        keyBg = 0xFF0B0B0B, keyLabel = 0xFFEDEDED, activeBg = 0xFF00E5A0, activeLabel = 0xFF001A12,
        border = 0xFF1C1C1C, surface = 0xFF000000,
    )

    val midnight = theme(
        id = "midnight", name = "Midnight",
        keyBg = 0xFF1B2B4B, keyLabel = 0xFFD6E1F5, activeBg = 0xFF4D8DFF, activeLabel = 0xFF04122B,
        border = 0xFF2C3E63, surface = 0xFF0E1830,
    )

    val tokyoNight = theme(
        id = "tokyo_night", name = "Tokyo Night",
        keyBg = 0xFF24283B, keyLabel = 0xFFC0CAF5, activeBg = 0xFF7AA2F7, activeLabel = 0xFF16161E,
        border = 0xFF3B4261, surface = 0xFF1A1B26,
    )

    val nord = theme(
        id = "nord", name = "Nord",
        keyBg = 0xFF3B4252, keyLabel = 0xFFECEFF4, activeBg = 0xFF88C0D0, activeLabel = 0xFF2E3440,
        border = 0xFF4C566A, surface = 0xFF2E3440,
    )

    val dracula = theme(
        id = "dracula", name = "Dracula",
        keyBg = 0xFF44475A, keyLabel = 0xFFF8F8F2, activeBg = 0xFFBD93F9, activeLabel = 0xFF191A21,
        border = 0xFF565A73, surface = 0xFF282A36,
    )

    val solarized = theme(
        id = "solarized", name = "Solarized",
        keyBg = 0xFF073642, keyLabel = 0xFF93A1A1, activeBg = 0xFF268BD2, activeLabel = 0xFF002B36,
        border = 0xFF0A4B5A, surface = 0xFF002B36,
    )

    val gruvbox = theme(
        id = "gruvbox", name = "Gruvbox",
        keyBg = 0xFF3C3836, keyLabel = 0xFFEBDBB2, activeBg = 0xFFB8BB26, activeLabel = 0xFF282828,
        border = 0xFF504945, surface = 0xFF282828,
    )

    val minimalMono = theme(
        id = "minimal_mono", name = "Minimal Mono",
        keyBg = 0xFF1A1A1A, keyLabel = 0xFFCCCCCC, activeBg = 0xFFFFFFFF, activeLabel = 0xFF111111,
        border = 0xFF333333, surface = 0xFF0D0D0D, monochrome = true, borderWidthDp = 0, cornerRadiusDp = 8,
    )

    val all: List<ToolbarTheme> = listOf(
        materialDark, materialLight, amoled, midnight, tokyoNight,
        nord, dracula, solarized, gruvbox, minimalMono,
    )

    val default: ToolbarTheme = materialDark

    fun byId(id: String?): ToolbarTheme = all.firstOrNull { it.id == id } ?: default
}

private fun theme(
    id: String,
    name: String,
    keyBg: Long,
    keyLabel: Long,
    activeBg: Long,
    activeLabel: Long,
    border: Long,
    surface: Long,
    monochrome: Boolean = false,
    borderWidthDp: Int = 1,
    cornerRadiusDp: Int = 10,
): ToolbarTheme = ToolbarTheme(
    id = id, name = name,
    keyBackground = keyBg, keyLabel = keyLabel,
    activeBackground = activeBg, activeLabel = activeLabel,
    borderColor = border, keySurface = surface,
    borderWidthDp = borderWidthDp, cornerRadiusDp = cornerRadiusDp,
    monochrome = monochrome,
)

/**
 * The starter macros. All read-only: they list state, they do not change it.
 */
object ToolbarMacros {

    private fun run(command: String, id: String, name: String) = MacroDef(
        id = id, name = name, builtIn = true,
        description = "Run `$command`",
        steps = listOf(MacroStep.Text(command), MacroStep.Special(ToolbarSpecialKey.ENTER)),
    )

    val listFiles = run("ls -la", "macro.ls", "List files")
    val diskUsage = run("df -h", "macro.df", "Disk usage")
    val gitStatus = run("git status", "macro.git_status", "Git status")
    val dockerPs = run("docker ps", "macro.docker_ps", "Docker ps")
    val whoAmI = run("id", "macro.whoami", "Who am I")

    val clearScreen = MacroDef(
        id = "macro.clear", name = "Clear screen", builtIn = true,
        description = "Send Ctrl+L",
        steps = listOf(MacroStep.Controls('L')),
    )

    val all: List<MacroDef> = listOf(listFiles, diskUsage, gitStatus, dockerPs, whoAmI, clearScreen)

    fun byId(id: String?): MacroDef? = all.firstOrNull { it.id == id }
}

/**
 * The shipped profiles. A first-run user gets [default]; the rest are one tap away.
 */
object ToolbarProfiles {

    // --- reusable keys ---

    private fun ctrl(g: Map<ToolbarGesture, ToolbarAction> = emptyMap()) = ToolKeySpec(
        id = "ctrl", label = "Ctrl", contentDescription = "Control",
        action = ModifierAction(ToolbarModifier.CTRL), style = KeyStyle.ACCENT, gestures = g,
    )

    private fun alt() = ToolKeySpec(
        id = "alt", label = "Alt", contentDescription = "Alt",
        action = ModifierAction(ToolbarModifier.ALT), style = KeyStyle.ACCENT,
    )

    private fun shift() = ToolKeySpec(
        id = "shift", label = "Shift", contentDescription = "Shift",
        action = ModifierAction(ToolbarModifier.SHIFT), style = KeyStyle.ACCENT,
    )

    private fun special(key: ToolbarSpecialKey, label: String, width: KeyWidth = KeyWidth.NORMAL) =
        ToolKeySpec(id = key.name.lowercase(), label = label, action = SpecialKeyAction(key), width = width)

    private val esc = special(ToolbarSpecialKey.ESCAPE, "Esc")
    private val tab = special(ToolbarSpecialKey.TAB, "Tab")
    private val up = special(ToolbarSpecialKey.UP, "↑", KeyWidth.NARROW)
    private val down = special(ToolbarSpecialKey.DOWN, "↓", KeyWidth.NARROW)
    private val left = special(ToolbarSpecialKey.LEFT, "←", KeyWidth.NARROW)
    private val right = special(ToolbarSpecialKey.RIGHT, "→", KeyWidth.NARROW)
    private val ctrlC = ToolKeySpec("cc", "^C", ControlAction('C'), contentDescription = "Interrupt", style = KeyStyle.DANGER)
    private val ctrlD = ToolKeySpec("cd", "^D", ControlAction('D'), contentDescription = "End of file")
    private val snippets = ToolKeySpec(
        id = "snippets",
        label = "Snip",
        contentDescription = "Open command snippets",
        action = OpenSnippetsAction,
        style = KeyStyle.ACCENT,
    )

    // --- profiles ---

    val shell = ToolbarProfile(
        id = "shell", name = "Shell", iconToken = "terminal", builtIn = true,
        description = "Everyday shell work: modifiers, arrows, symbols and inline commands.",
        themeId = ToolbarThemes.default.id,
        macroIds = listOf("macro.ls", "macro.df"),
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    // Keep the command-snippet launcher in the compact first viewport.
                    // The CI API26/36 Compose bridge exposes the LazyRow as scrollable,
                    // but injected swipes report success without changing its offset;
                    // a core action must remain reachable without relying on that path.
                    ctrl(), alt(), shift(), snippets, tab, esc,
                    up, down, left, right,
                    ToolKeySpec("pipe", "|", TextAction("|")),
                    ToolKeySpec("slash", "/", TextAction("/")),
                    ToolKeySpec("dash", "-", TextAction("-")),
                    ToolKeySpec("tilde", "~", TextAction("~")),
                    ToolKeySpec("dollar", "$", TextAction("$")),
                ),
                listOf(
                    ctrlC, ctrlD,
                    special(ToolbarSpecialKey.HOME, "Home"),
                    special(ToolbarSpecialKey.END, "End"),
                    special(ToolbarSpecialKey.PAGE_UP, "PgUp"),
                    special(ToolbarSpecialKey.PAGE_DOWN, "PgDn"),
                ),
            ),
            mode = ToolbarMode.DOUBLE_ROW,
        ),
    )

    val vim = ToolbarProfile(
        id = "vim", name = "Vim", iconToken = "edit", builtIn = true,
        description = "Esc-heavy editing with vim command symbols and fast save/quit keys.",
        themeId = ToolbarThemes.nord.id,
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("esc_hi", "Esc", SpecialKeyAction(ToolbarSpecialKey.ESCAPE), style = KeyStyle.ACCENT, width = KeyWidth.WIDE),
                    ToolKeySpec("colon", ":", TextAction(":")),
                    ToolKeySpec("slash", "/", TextAction("/")),
                    ToolKeySpec("semi", ";", TextAction(";")),
                    up, down, left, right,
                    ToolKeySpec("save", "w", TextAction(":w")),
                    ToolKeySpec("quit", "q", TextAction(":q")),
                ),
            ),
            mode = ToolbarMode.SINGLE_ROW,
        ),
    )

    val tmux = ToolbarProfile(
        id = "tmux", name = "tmux", iconToken = "grid", builtIn = true,
        description = "Prefix (Ctrl-b) and pane-splitting keys reachable with one thumb.",
        themeId = ToolbarThemes.gruvbox.id,
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("prefix", "C-b", ControlAction('B'), style = KeyStyle.ACCENT, width = KeyWidth.WIDE),
                    ToolKeySpec("splitv", "|", TextAction("%"), contentDescription = "Split vertical"),
                    ToolKeySpec("splith", "-", TextAction("\""), contentDescription = "Split horizontal"),
                    ToolKeySpec("zoom", "z", TextAction("z"), contentDescription = "Zoom pane"),
                    ToolKeySpec("next", "n", TextAction("n"), contentDescription = "Next window"),
                    up, down, left, right,
                ),
            ),
        ),
    )

    val git = ToolbarProfile(
        id = "git", name = "Git", iconToken = "branch", builtIn = true,
        description = "Branch, status, diff and commit, plus the shell symbols git asks for.",
        themeId = ToolbarThemes.dracula.id,
        macroIds = listOf("macro.git_status"),
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("status", "st", TextAction("git status")),
                    ToolKeySpec("diff", "diff", TextAction("git diff ")),
                    ToolKeySpec("add", "add", TextAction("git add ")),
                    ToolKeySpec("commit", "ci", TextAction("git commit -m \"\""), width = KeyWidth.WIDE),
                    ToolKeySpec("push", "pu", TextAction("git push")),
                    ToolKeySpec("branch", "br", TextAction("git branch")),
                    ToolKeySpec("dash", "-", TextAction("-")),
                    ToolKeySpec("quote", "\"", TextAction("\"")),
                ),
            ),
        ),
    )

    val docker = ToolbarProfile(
        id = "docker", name = "Docker", iconToken = "box", builtIn = true,
        description = "Container and compose commands, with a quick ps/exec bar.",
        themeId = ToolbarThemes.midnight.id,
        macroIds = listOf("macro.docker_ps"),
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("ps", "ps", TextAction("docker ps")),
                    ToolKeySpec("exec", "exec", TextAction("docker exec -it ")),
                    ToolKeySpec("logs", "logs", TextAction("docker logs -f ")),
                    ToolKeySpec("compose", "compose", TextAction("docker compose "), width = KeyWidth.WIDE),
                    ToolKeySpec("stop", "stop", TextAction("docker stop ")),
                    up, down, left, right,
                ),
            ),
        ),
    )

    val python = ToolbarProfile(
        id = "python", name = "Python", iconToken = "code", builtIn = true,
        description = "Indentation, brackets and REPL control for interactive Python.",
        themeId = ToolbarThemes.tokyoNight.id,
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("indent", "⇥", TextAction("    "), contentDescription = "Indent", width = KeyWidth.WIDE),
                    ToolKeySpec("colon", ":", TextAction(":")),
                    ToolKeySpec("paren", "()", TextAction("()")),
                    ToolKeySpec("bracket", "[]", TextAction("[]")),
                    ToolKeySpec("brace", "{}", TextAction("{}")),
                    ctrlC, ctrlD,
                    ToolKeySpec("underscore", "_", TextAction("_")),
                    up, down,
                ),
            ),
        ),
    )

    val sql = ToolbarProfile(
        id = "sql", name = "SQL", iconToken = "database", builtIn = true,
        description = "A query console: keywords, quotes and a semicolon to finish.",
        themeId = ToolbarThemes.solarized.id,
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("select", "SELECT", TextAction("SELECT "), width = KeyWidth.WIDE),
                    ToolKeySpec("from", "FROM", TextAction("FROM ")),
                    ToolKeySpec("where", "WHERE", TextAction("WHERE ")),
                    ToolKeySpec("quote", "'", TextAction("'")),
                    ToolKeySpec("star", "*", TextAction("*")),
                    ToolKeySpec("semi", ";", TextAction(";")),
                    up, down,
                ),
            ),
        ),
    )

    val agent = ToolbarProfile(
        id = "ai", name = "AI Agent", iconToken = "sparkle", builtIn = true,
        description = "Prompt-oriented: type a long prompt, newline without sending, then Enter.",
        themeId = ToolbarThemes.amoled.id,
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ToolKeySpec("compose", "✎", ToggleComposeAction, contentDescription = "Compose prompt", style = KeyStyle.ACCENT, width = KeyWidth.WIDE),
                    ToolKeySpec("newline", "↵", TextAction("\n"), contentDescription = "New line without sending"),
                    ToolKeySpec("enter", "⏎", SpecialKeyAction(ToolbarSpecialKey.ENTER), contentDescription = "Send", style = KeyStyle.ACCENT, width = KeyWidth.WIDE),
                    ctrlC, esc,
                    up, down, left, right,
                ),
            ),
        ),
    )

    val minimal = ToolbarProfile(
        id = "minimal", name = "Minimal", iconToken = "minus", builtIn = true,
        description = "Six essential keys. Nothing else, for maximum terminal area.",
        themeId = ToolbarThemes.minimalMono.id,
        layout = ToolbarLayout(
            rows = listOf(listOf(ctrl(), esc, tab, up, down, ctrlC)),
            mode = ToolbarMode.MINIMAL,
        ),
    )

    val power = ToolbarProfile(
        id = "power", name = "Power User", iconToken = "bolt", builtIn = true,
        description = "Everything: function keys, a paged symbol bank, macros and settings.",
        themeId = ToolbarThemes.tokyoNight.id,
        macroIds = listOf("macro.ls", "macro.df", "macro.git_status", "macro.clear"),
        layout = ToolbarLayout(
            rows = listOf(
                listOf(
                    ctrl(), alt(), shift(), esc, tab,
                    ctrlC, ctrlD, ctrlC.copy(id = "ctrl_z", label = "^Z", action = ControlAction('Z'), contentDescription = "Suspend"),
                    // Keep every visible key actionable. This used to be a decorative
                    // "F…" button backed by empty text, which sent nothing when tapped.
                    special(ToolbarSpecialKey.F1, "F1", KeyWidth.NARROW),
                    up, down, left, right,
                ),
                listOf(
                    ToolKeySpec("pipe", "|", TextAction("|")),
                    ToolKeySpec("slash", "/", TextAction("/")),
                    ToolKeySpec("dash", "-", TextAction("-")),
                    ToolKeySpec("amp", "&", TextAction("&")),
                    ToolKeySpec("dollar", "$", TextAction("$")),
                    ToolKeySpec("hash", "#", TextAction("#")),
                    ToolKeySpec("star", "*", TextAction("*")),
                    ToolKeySpec("tilde", "~", TextAction("~")),
                    ToolKeySpec("backtick", "`", TextAction("`")),
                    ToolKeySpec("paren", "(", TextAction("(")),
                    ToolKeySpec("brace", "{", TextAction("{")),
                    ToolKeySpec("lt", "<", TextAction("<")),
                ),
            ),
            mode = ToolbarMode.EXPANDED,
        ),
    )

    val all: List<ToolbarProfile> = listOf(shell, vim, tmux, git, docker, python, sql, agent, minimal, power)

    val default: ToolbarProfile = shell

    fun byId(id: String?): ToolbarProfile = all.firstOrNull { it.id == id } ?: default
}
