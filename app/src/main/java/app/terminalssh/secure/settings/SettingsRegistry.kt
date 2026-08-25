package app.terminalssh.secure.settings

import app.terminalssh.secure.R
import app.terminalssh.secure.model.FuzzyMatch

/**
 * Every setting the app has, in one list.
 *
 * Keys match what earlier versions wrote to SharedPreferences, so an upgrade keeps the
 * user's existing choices rather than silently resetting them.
 */
object SettingsRegistry {

    // ---- appearance ----

    val theme = ChoiceSetting(
        key = "theme",
        default = "persian_neon",
        values = listOf("persian_neon", "oled", "midnight", "solarized", "classic", "amber",
            "dracula", "nord", "gruvbox", "catppuccin", "tokyo_night"),
        titleRes = R.string.settings_theme,
        group = SettingGroup.APPEARANCE,
    )

    val fontSize = IntSetting(
        key = "font_size",
        default = 14, min = 10, max = 24,
        titleRes = R.string.settings_fontsize,
        group = SettingGroup.APPEARANCE,
    )

    val fontFamily = ChoiceSetting(
        key = "font_family",
        default = "monospace",
        values = listOf("monospace", "jetbrains_mono", "fira_code", "source_code_pro"),
        titleRes = R.string.settings_font_family,
        group = SettingGroup.APPEARANCE,
    )

    val lineHeight = IntSetting(
        key = "line_height_percent",
        default = 100, min = 90, max = 160, step = 5,
        titleRes = R.string.settings_line_height,
        summaryRes = R.string.settings_line_height_summary,
        group = SettingGroup.APPEARANCE,
        advanced = true,
    )

    val cursorStyle = ChoiceSetting(
        key = "cursor_style",
        default = "block",
        values = listOf("block", "bar", "underline"),
        titleRes = R.string.settings_cursor_style,
        group = SettingGroup.APPEARANCE,
        advanced = true,
    )

    val cursorBlink = BoolSetting(
        key = "cursor_blink",
        default = true,
        titleRes = R.string.settings_cursor_blink,
        group = SettingGroup.APPEARANCE,
        advanced = true,
    )

    // ---- terminal ----

    val terminalType = TextSetting(
        key = "terminal_type",
        default = "xterm-256color",
        maxLength = 40,
        titleRes = R.string.settings_terminal_type,
        summaryRes = R.string.settings_terminal_type_summary,
        group = SettingGroup.TERMINAL,
        advanced = true,
    )

    val bellBehavior = ChoiceSetting(
        key = "bell_behavior",
        default = "vibrate",
        values = listOf("none", "vibrate", "sound"),
        titleRes = R.string.settings_bell,
        group = SettingGroup.TERMINAL,
    )

    val hapticKeys = BoolSetting(
        key = "haptic_keys",
        default = true,
        titleRes = R.string.settings_haptic_keys,
        summaryRes = R.string.settings_haptic_keys_summary,
        group = SettingGroup.TERMINAL,
    )

    val keepScreenOn = BoolSetting(
        key = "keep_screen_on",
        default = true,
        titleRes = R.string.settings_keep_screen_on,
        summaryRes = R.string.settings_keep_screen_on_summary,
        group = SettingGroup.TERMINAL,
    )

    // ---- behavior ----

    val keepAlive = BoolSetting(
        key = "keepalive",
        default = true,
        titleRes = R.string.settings_keepalive,
        group = SettingGroup.BEHAVIOR,
    )

    val confirmMultilinePaste = BoolSetting(
        key = "paste_confirm",
        default = true,
        titleRes = R.string.settings_paste_confirm,
        group = SettingGroup.BEHAVIOR,
    )

    val confirmCloseRunning = BoolSetting(
        key = "confirm_close_running",
        default = true,
        titleRes = R.string.settings_confirm_close,
        summaryRes = R.string.settings_confirm_close_summary,
        group = SettingGroup.BEHAVIOR,
    )

    val reopenLastSession = BoolSetting(
        key = "reopen_last_session",
        default = false,
        titleRes = R.string.settings_reopen_last,
        group = SettingGroup.BEHAVIOR,
        advanced = true,
    )

    val idleDisconnectMinutes = IntSetting(
        key = "idle_disconnect_minutes",
        default = 0, min = 0, max = 240, step = 5,
        titleRes = R.string.settings_idle_disconnect,
        summaryRes = R.string.settings_idle_disconnect_summary,
        group = SettingGroup.BEHAVIOR,
        advanced = true,
    )

    val meteredKeepAlive = BoolSetting(
        key = "metered_keepalive",
        default = false,
        titleRes = R.string.settings_metered_keepalive,
        summaryRes = R.string.settings_metered_keepalive_summary,
        group = SettingGroup.BEHAVIOR,
        advanced = true,
    )

    // ---- security ----

    val biometricLock = BoolSetting(
        key = "biometric",
        default = false,
        titleRes = R.string.settings_biometric,
        group = SettingGroup.SECURITY,
    )

    val clipboardClearSeconds = IntSetting(
        key = "clipboard_clear_seconds",
        default = 45, min = 0, max = 600, step = 15,
        titleRes = R.string.settings_clipboard_clear,
        group = SettingGroup.SECURITY,
    )

    val maskSecretsInOutput = BoolSetting(
        key = "mask_secrets_output",
        default = true,
        titleRes = R.string.settings_mask_secrets,
        summaryRes = R.string.settings_mask_secrets_summary,
        group = SettingGroup.SECURITY,
    )

    val warnWeakAlgorithms = BoolSetting(
        key = "warn_weak_algorithms",
        default = true,
        titleRes = R.string.settings_warn_weak,
        group = SettingGroup.SECURITY,
        advanced = true,
    )

    /** Every spec, in display order within each group. */
    val all: List<SettingSpec<*>> = listOf(
        theme, fontSize, fontFamily, lineHeight, cursorStyle, cursorBlink,
        terminalType, bellBehavior, hapticKeys, keepScreenOn,
        keepAlive, confirmMultilinePaste, confirmCloseRunning, reopenLastSession,
        idleDisconnectMinutes, meteredKeepAlive,
        biometricLock, clipboardClearSeconds, maskSecretsInOutput, warnWeakAlgorithms,
    )

    init {
        val duplicates = all.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate setting keys: $duplicates" }
    }

    fun byGroup(group: SettingGroup): List<SettingSpec<*>> = all.filter { it.group == group }

    fun byKey(key: String): SettingSpec<*>? = all.firstOrNull { it.key == key }

    /**
     * Settings matching a search query, ranked by relevance.
     *
     * @param resolve turns a string resource into its localized text, so matching happens
     *   against what the user actually sees rather than the English key.
     */
    fun search(query: String, resolve: (Int) -> String): List<SettingSpec<*>> {
        if (query.isBlank()) return emptyList()
        return all
            .map { spec ->
                val titleScore = FuzzyMatch.score(query, resolve(spec.titleRes))
                val summaryScore = spec.summaryRes
                    ?.let { FuzzyMatch.score(query, resolve(it)) }
                    ?: FuzzyMatch.NO_MATCH
                // A title hit outranks a summary hit for the same query.
                spec to maxOf(titleScore, summaryScore / 2)
            }
            .filter { (_, score) -> score > FuzzyMatch.NO_MATCH }
            .sortedByDescending { (_, score) -> score }
            .map { (spec, _) -> spec }
    }
}
