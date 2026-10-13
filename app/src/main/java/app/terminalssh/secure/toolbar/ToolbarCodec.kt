package app.terminalssh.secure.toolbar

/**
 * Serialisation for the whole toolbar configuration.
 *
 * The format is versioned and defensive: unknown fields are ignored, unknown *action*
 * types are dropped with the key that carried them (a newer build's key degrades out of an
 * older build rather than breaking it), out-of-range values are rejected, and a document
 * that fails to parse returns null so the caller can fall back to a known-good default
 * without destroying the user's file.
 *
 * Contains preferences only. Configurations are exported to share layouts, so no field
 * may ever hold a credential.
 */
data class ToolbarConfigState(
    val activeProfileId: String = ToolbarProfiles.default.id,
    val activeThemeId: String = ToolbarThemes.default.id,
    val modifierLockEnabled: Boolean = true,
    val customProfiles: List<ToolbarProfile> = emptyList(),
    val customThemes: List<ToolbarTheme> = emptyList(),
    val customMacros: List<MacroDef> = emptyList(),
    /** host profile id -> toolbar profile id. */
    val hostOverrides: Map<String, String> = emptyMap(),
) {
    fun profileById(id: String?): ToolbarProfile? =
        customProfiles.firstOrNull { it.id == id } ?: ToolbarProfiles.all.firstOrNull { it.id == id }

    fun themeById(id: String?): ToolbarTheme? =
        customThemes.firstOrNull { it.id == id } ?: ToolbarThemes.all.firstOrNull { it.id == id }

    fun macroById(id: String?): MacroDef? =
        customMacros.firstOrNull { it.id == id } ?: ToolbarMacros.all.firstOrNull { it.id == id }

    /** The profile actually in effect for [hostId], honouring a host override when present. */
    fun effectiveProfileId(hostId: String?): String {
        val override = hostId?.let { hostOverrides[it] }
        return if (override != null && profileById(override) != null) override else activeProfileId
    }

    companion object {
        fun default(): ToolbarConfigState = ToolbarConfigState()
    }
}

/** The outcome of reading a configuration document. */
sealed interface ToolbarDecode {
    data class Ok(
        val state: ToolbarConfigState,
        /** Number of toolbar key actions this reader could not decode and therefore omitted. */
        val omittedActions: Int = 0,
    ) : ToolbarDecode
    data class Failed(val reason: String) : ToolbarDecode
}

object ToolbarCodec {

    const val SCHEMA_VERSION = 1

    fun encode(state: ToolbarConfigState): String {
        val root = Json.obj(
            "schema" to Json.num(SCHEMA_VERSION),
            "activeProfile" to Json.str(state.activeProfileId),
            "activeTheme" to Json.str(state.activeThemeId),
            "modifierLock" to Json.bool(state.modifierLockEnabled),
            "customProfiles" to Json.arr(state.customProfiles.map(::encodeProfile)),
            "customThemes" to Json.arr(state.customThemes.map(::encodeTheme)),
            "customMacros" to Json.arr(state.customMacros.map(::encodeMacro)),
            "hostOverrides" to JsonValue.Obj(
                state.hostOverrides.mapValues { Json.str(it.value) },
            ),
        )
        return root.encode()
    }

    fun decode(text: String): ToolbarDecode {
        val parsed = Json.parse(text) ?: return ToolbarDecode.Failed("not valid JSON")
        val root = parsed as? JsonValue.Obj ?: return ToolbarDecode.Failed("root is not an object")
        val version = root.int("schema") ?: return ToolbarDecode.Failed("missing schema version")
        if (version < 1) return ToolbarDecode.Failed("unknown schema version $version")
        if (version > SCHEMA_VERSION) {
            // A config from a newer build: read what we understand instead of failing outright.
            // Unknown keys are dropped by the per-key decoder below.
        }
        return try {
            val state = ToolbarConfigState(
                activeProfileId = root.str("activeProfile") ?: ToolbarProfiles.default.id,
                activeThemeId = root.str("activeTheme") ?: ToolbarThemes.default.id,
                modifierLockEnabled = root.bool("modifierLock") ?: true,
                customProfiles = root.arr("customProfiles").mapNotNull { it.decodeProfile() },
                customThemes = root.arr("customThemes").mapNotNull { it.decodeTheme() },
                customMacros = root.arr("customMacros").mapNotNull { it.decodeMacro() },
                hostOverrides = root.obj("hostOverrides")?.entries
                    ?.mapNotNull { (k, v) -> (v as? JsonValue.Str)?.let { k to it.value } }
                    ?.toMap()
                    .orEmpty(),
            )
            ToolbarDecode.Ok(state, countOmittedActions(parsed))
        } catch (invalid: IllegalArgumentException) {
            ToolbarDecode.Failed(invalid.message ?: "invalid configuration")
        } catch (bad: IndexOutOfBoundsException) {
            ToolbarDecode.Failed("truncated configuration")
        }
    }

    /** Counts undecodable key actions so import can warn before replacing the user's config. */
    private fun countOmittedActions(value: JsonValue): Int = when (value) {
        is JsonValue.Obj -> value.entries.entries.fold(0) { count, (name, child) ->
            val omittedHere = if (name == "action" && child.decodeAction() == null) 1 else 0
            count + omittedHere + countOmittedActions(child)
        }
        is JsonValue.Arr -> value.items.fold(0) { count, child -> count + countOmittedActions(child) }
        else -> 0
    }

    // --- profiles ---

    private fun encodeProfile(profile: ToolbarProfile): JsonValue = Json.obj(
        "id" to Json.str(profile.id),
        "name" to Json.str(profile.name),
        "description" to Json.str(profile.description),
        "icon" to Json.str(profile.iconToken),
        "theme" to Json.str(profile.themeId),
        "builtIn" to Json.bool(profile.builtIn),
        "macroIds" to Json.arr(profile.macroIds.map { Json.str(it) }),
        "rows" to Json.arr(profile.layout.rows.map { row -> Json.arr(row.map(::encodeKey)) }),
        "optionalKeys" to Json.arr(profile.optionalKeys.map(::encodeKey)),
        "mode" to Json.str(profile.layout.mode.name),
    )

    private fun JsonValue.decodeProfile(): ToolbarProfile? {
        val obj = this as? JsonValue.Obj ?: return null
        val id = obj.str("id") ?: return null
        val rows = obj.arr("rows").mapNotNull { row ->
            (row as? JsonValue.Arr)?.let { r -> r.items.mapNotNull { it.decodeKey() }.takeIf { it.isNotEmpty() } }
        }.takeIf { it.isNotEmpty() } ?: return null
        val mode = obj.str("mode")?.let { name ->
            ToolbarMode.entries.firstOrNull { it.name == name }
        } ?: ToolbarMode.SINGLE_ROW
        return ToolbarProfile(
            id = id,
            name = obj.str("name") ?: id,
            description = obj.str("description").orEmpty(),
            iconToken = obj.str("icon").orEmpty(),
            themeId = obj.str("theme") ?: ToolbarThemes.default.id,
            layout = ToolbarLayout(rows = rows, mode = mode),
            macroIds = obj.arr("macroIds").mapNotNull { (it as? JsonValue.Str)?.value },
            optionalKeys = obj.arr("optionalKeys").mapNotNull { it.decodeKey() },
            builtIn = obj.bool("builtIn") ?: false,
        )
    }

    // --- keys ---

    private fun encodeKey(key: ToolKeySpec): JsonValue = Json.obj(
        "id" to Json.str(key.id),
        "label" to Json.str(key.label),
        "desc" to Json.str(key.contentDescription.orEmpty()),
        "width" to Json.str(key.width.name),
        "style" to Json.str(key.style.name),
        "repeat" to Json.bool(key.repeatWhileHeld),
        "enabled" to Json.bool(key.enabled),
        "pinned" to Json.bool(key.pinned),
        "action" to encodeAction(key.action),
        "gestures" to Json.arr(
            key.gestures.map { (gesture, action) ->
                Json.obj("gesture" to Json.str(gesture.name), "action" to encodeAction(action))
            },
        ),
    )

    private fun JsonValue.decodeKey(): ToolKeySpec? {
        val obj = this as? JsonValue.Obj ?: return null
        val id = obj.str("id") ?: return null
        val action = obj.obj("action")?.decodeAction() ?: return null
        return try {
            ToolKeySpec(
                id = id,
                label = obj.str("label") ?: id,
                action = action,
                contentDescription = obj.str("desc")?.takeIf { it.isNotBlank() },
                width = obj.str("width")?.let { n -> KeyWidth.entries.firstOrNull { it.name == n } } ?: KeyWidth.NORMAL,
                style = obj.str("style")?.let { n -> KeyStyle.entries.firstOrNull { it.name == n } } ?: KeyStyle.DEFAULT,
                gestures = obj.arr("gestures").mapNotNull { g ->
                    val go = g as? JsonValue.Obj ?: return@mapNotNull null
                    val gesture = go.str("gesture")?.let { name ->
                        ToolbarGesture.entries.firstOrNull { it.name == name }
                    } ?: return@mapNotNull null
                    val ga = go.obj("action")?.decodeAction() ?: return@mapNotNull null
                    gesture to ga
                }.toMap(),
                repeatWhileHeld = obj.bool("repeat") ?: false,
                enabled = obj.bool("enabled") ?: true,
                pinned = obj.bool("pinned") ?: false,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // --- actions ---

    private fun encodeAction(action: ToolbarAction): JsonValue = when (action) {
        is SpecialKeyAction -> Json.obj("type" to Json.str("special"), "key" to Json.str(action.key.name))
        is ControlAction -> Json.obj("type" to Json.str("control"), "letter" to Json.str(action.letter.toString()))
        is TextAction -> Json.obj("type" to Json.str("text"), "text" to Json.str(action.text))
        is ModifierAction -> Json.obj(
            "type" to Json.str("modifier"),
            "modifier" to Json.str(action.modifier.name),
            "lock" to Json.bool(action.lock),
        )
        is SnippetAction -> Json.obj("type" to Json.str("snippet"), "id" to Json.str(action.snippetId))
        is MacroAction -> Json.obj("type" to Json.str("macro"), "id" to Json.str(action.macroId))
        PasteAction -> Json.obj("type" to Json.str("paste"))
        ReconnectAction -> Json.obj("type" to Json.str("reconnect"))
        OpenKeyboardAction -> Json.obj("type" to Json.str("open_keyboard"))
        HideKeyboardAction -> Json.obj("type" to Json.str("hide_keyboard"))
        ToggleComposeAction -> Json.obj("type" to Json.str("toggle_compose"))
        OpenSnippetsAction -> Json.obj("type" to Json.str("open_snippets"))
        OpenAgentsAction -> Json.obj("type" to Json.str("open_agents"))
        OpenPortForwardAction -> Json.obj("type" to Json.str("open_port_forward"))
        OpenSettingsAction -> Json.obj("type" to Json.str("open_settings"))
        OpenToolbarEditorAction -> Json.obj("type" to Json.str("open_toolbar_editor"))
        is ChangeFontSizeAction -> Json.obj("type" to Json.str("font_size"), "delta" to Json.num(action.delta))
        is SwitchProfileAction -> Json.obj("type" to Json.str("switch_profile"), "id" to Json.str(action.profileId))
    }

    /** Returns null for an unknown action type so the caller drops the whole key. */
    private fun JsonValue.decodeAction(): ToolbarAction? {
        val obj = this as? JsonValue.Obj ?: return null
        return when (obj.str("type")) {
            "special" -> obj.str("key")?.let { name ->
                ToolbarSpecialKey.entries.firstOrNull { it.name == name }
            }?.let(::SpecialKeyAction)

            "control" -> obj.str("letter")?.firstOrNull()?.let(::ControlAction)

            "text" -> obj.str("text")?.let(::TextAction)

            "modifier" -> obj.str("modifier")?.let { name ->
                ToolbarModifier.entries.firstOrNull { it.name == name }
            }?.let { ModifierAction(it, obj.bool("lock") ?: false) }

            "snippet" -> obj.str("id")?.let(::SnippetAction)
            "macro" -> obj.str("id")?.let(::MacroAction)
            "paste" -> PasteAction
            "reconnect" -> ReconnectAction
            "open_keyboard" -> OpenKeyboardAction
            "hide_keyboard" -> HideKeyboardAction
            "toggle_compose" -> ToggleComposeAction
            "open_snippets" -> OpenSnippetsAction
            "open_agents" -> OpenAgentsAction
            "open_port_forward" -> OpenPortForwardAction
            "open_settings" -> OpenSettingsAction
            "open_toolbar_editor" -> OpenToolbarEditorAction
            "font_size" -> obj.int("delta")?.let(::ChangeFontSizeAction)
            "switch_profile" -> obj.str("id")?.let(::SwitchProfileAction)
            else -> null
        }
    }

    // --- themes ---

    private fun encodeTheme(theme: ToolbarTheme): JsonValue = Json.obj(
        "id" to Json.str(theme.id),
        "name" to Json.str(theme.name),
        "keyBackground" to Json.num(theme.keyBackground),
        "keyLabel" to Json.num(theme.keyLabel),
        "activeBackground" to Json.num(theme.activeBackground),
        "activeLabel" to Json.num(theme.activeLabel),
        "borderColor" to Json.num(theme.borderColor),
        "keySurface" to Json.num(theme.keySurface),
        "borderWidth" to Json.num(theme.borderWidthDp),
        "cornerRadius" to Json.num(theme.cornerRadiusDp),
        "horizontalGap" to Json.num(theme.horizontalGapDp),
        "verticalGap" to Json.num(theme.verticalGapDp),
        "toolbarHeight" to Json.num(theme.toolbarHeightDp),
        "minKeyWidth" to Json.num(theme.minKeyWidthDp),
        "monochrome" to Json.bool(theme.monochrome),
    )

    private fun JsonValue.decodeTheme(): ToolbarTheme? {
        val obj = this as? JsonValue.Obj ?: return null
        val id = obj.str("id") ?: return null
        return try {
            ToolbarTheme(
                id = id,
                name = obj.str("name") ?: id,
                keyBackground = obj.long("keyBackground") ?: 0xFF2A2F38,
                keyLabel = obj.long("keyLabel") ?: 0xFFE6EAF2,
                activeBackground = obj.long("activeBackground") ?: 0xFF3DDC97,
                activeLabel = obj.long("activeLabel") ?: 0xFF04231A,
                borderColor = obj.long("borderColor") ?: 0xFF3A414D,
                keySurface = obj.long("keySurface") ?: 0xFF1E222A,
                borderWidthDp = obj.int("borderWidth") ?: 1,
                cornerRadiusDp = obj.int("cornerRadius") ?: 10,
                horizontalGapDp = obj.int("horizontalGap") ?: 6,
                verticalGapDp = obj.int("verticalGap") ?: 6,
                toolbarHeightDp = obj.int("toolbarHeight") ?: 0,
                minKeyWidthDp = obj.int("minKeyWidth") ?: 48,
                monochrome = obj.bool("monochrome") ?: false,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // --- macros ---

    private fun encodeMacro(macro: MacroDef): JsonValue = Json.obj(
        "id" to Json.str(macro.id),
        "name" to Json.str(macro.name),
        "description" to Json.str(macro.description),
        "builtIn" to Json.bool(macro.builtIn),
        "steps" to Json.arr(macro.steps.map(::encodeStep)),
    )

    private fun encodeStep(step: MacroStep): JsonValue = when (step) {
        is MacroStep.Text -> Json.obj("type" to Json.str("text"), "text" to Json.str(step.text))
        is MacroStep.Special -> Json.obj("type" to Json.str("special"), "key" to Json.str(step.key.name))
        is MacroStep.Controls -> Json.obj("type" to Json.str("control"), "letter" to Json.str(step.letter.toString()))
        is MacroStep.Modifier -> Json.obj(
            "type" to Json.str("modifier"),
            "modifier" to Json.str(step.modifier.name),
            "lock" to Json.bool(step.lock),
        )
        is MacroStep.Delay -> Json.obj("type" to Json.str("delay"), "millis" to Json.num(step.millis))
    }

    private fun JsonValue.decodeMacro(): MacroDef? {
        val obj = this as? JsonValue.Obj ?: return null
        val id = obj.str("id") ?: return null
        val steps = obj.arr("steps").mapNotNull { step ->
            val s = step as? JsonValue.Obj ?: return@mapNotNull null
            when (s.str("type")) {
                "text" -> s.str("text")?.let(MacroStep::Text)
                "special" -> s.str("key")?.let { n -> ToolbarSpecialKey.entries.firstOrNull { it.name == n } }?.let(MacroStep::Special)
                "control" -> s.str("letter")?.firstOrNull()?.let(MacroStep::Controls)
                "modifier" -> s.str("modifier")?.let { n -> ToolbarModifier.entries.firstOrNull { it.name == n } }
                    ?.let { MacroStep.Modifier(it, s.bool("lock") ?: false) }
                "delay" -> s.int("millis")?.let(MacroStep::Delay)
                else -> null
            }
        }
        return try {
            MacroDef(
                id = id,
                name = obj.str("name") ?: id,
                description = obj.str("description").orEmpty(),
                steps = steps,
                builtIn = obj.bool("builtIn") ?: false,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

// --- small typed accessors with defaults ---

private fun JsonValue.Obj.str(key: String): String? = (entries[key] as? JsonValue.Str)?.value

private fun JsonValue.Obj.int(key: String): Int? = (entries[key] as? JsonValue.Num)?.value?.toInt()

private fun JsonValue.Obj.long(key: String): Long? = (entries[key] as? JsonValue.Num)?.value?.toLong()

private fun JsonValue.Obj.bool(key: String): Boolean? = (entries[key] as? JsonValue.Bool)?.value

private fun JsonValue.Obj.arr(key: String): List<JsonValue> = (entries[key] as? JsonValue.Arr)?.items.orEmpty()

private fun JsonValue.Obj.obj(key: String): JsonValue.Obj? = entries[key] as? JsonValue.Obj
