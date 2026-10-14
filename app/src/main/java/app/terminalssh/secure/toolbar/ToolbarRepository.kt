package app.terminalssh.secure.toolbar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What an import will change, shown to the user before anything is applied. */
data class ToolbarImportPreview(
    val profiles: Int,
    val themes: Int,
    val macros: Int,
    val activeProfile: String,
    val warnings: List<String>,
    val omittedActions: Int = 0,
    /** Imported macro definitions that contain an Enter step and can submit a command. */
    val enterMacros: Int = 0,
)

/** Parsed, immutable configuration the user can review before an import replaces the current one. */
class ToolbarImportCandidate internal constructor(
    internal val state: ToolbarConfigState,
    val preview: ToolbarImportPreview,
)

/**
 * The single owner of toolbar configuration.
 *
 * Reads the whole configuration once, keeps it as one immutable value in a [StateFlow] so
 * Compose simply observes it, and writes it back atomically after every change. A document
 * that fails to parse does not throw and does not erase the user's file: the repository
 * falls back to a known-good default, keeps the raw text for recovery, and reports the
 * fallback through [recoveryNotice].
 */
class ToolbarRepository(
    private val prefs: ToolbarPrefs,
    private val builtInProfiles: List<ToolbarProfile> = ToolbarProfiles.all,
    private val builtInThemes: List<ToolbarTheme> = ToolbarThemes.all,
    private val builtInMacros: List<MacroDef> = ToolbarMacros.all,
) {

    private val _state: MutableStateFlow<ToolbarConfigState>
    val state: StateFlow<ToolbarConfigState>

    /** Non-null when the stored configuration could not be read and a default was used. */
    var recoveryNotice: String? = null
        private set

    /** The unreadable document, kept so a future build (or an export) can recover it. */
    var recoverableRaw: String? = null
        private set

    init {
        val loaded = load()
        _state = MutableStateFlow(loaded)
        state = _state.asStateFlow()
    }

    private fun load(): ToolbarConfigState {
        val raw = prefs.get(KEY_CONFIG) ?: return ToolbarConfigState.default()
        return when (val decoded = ToolbarCodec.decode(raw)) {
            is ToolbarDecode.Ok -> decoded.state
            is ToolbarDecode.Failed -> {
                recoveryNotice = "Saved toolbar settings could not be read (${decoded.reason}); using defaults."
                recoverableRaw = raw
                ToolbarConfigState.default()
            }
        }
    }

    // --- lookups ---

    fun profileById(id: String?): ToolbarProfile? =
        _state.value.customProfiles.firstOrNull { it.id == id }
            ?: builtInProfiles.firstOrNull { it.id == id }

    fun themeById(id: String?): ToolbarTheme? =
        _state.value.customThemes.firstOrNull { it.id == id }
            ?: builtInThemes.firstOrNull { it.id == id }

    fun macroById(id: String?): MacroDef? =
        _state.value.customMacros.firstOrNull { it.id == id }
            ?: builtInMacros.firstOrNull { it.id == id }

    val allProfiles: List<ToolbarProfile>
        get() = _state.value.customProfiles + builtInProfiles

    val allThemes: List<ToolbarTheme>
        get() = _state.value.customThemes + builtInThemes

    val allMacros: List<MacroDef>
        get() = _state.value.customMacros + builtInMacros

    /** The profile in effect for a host, honouring per-host overrides. */
    fun effectiveProfile(hostId: String?): ToolbarProfile {
        val id = _state.value.effectiveProfileId(hostId)
        return profileById(id) ?: builtInProfiles.first()
    }

    fun effectiveTheme(profile: ToolbarProfile): ToolbarTheme =
        themeById(profile.themeId) ?: builtInThemes.first()

    // --- mutations ---

    fun setActiveProfile(id: String) {
        if (profileById(id) == null) return
        update { it.copy(activeProfileId = id) }
    }

    fun setActiveTheme(id: String) {
        if (themeById(id) == null) return
        update { it.copy(activeThemeId = id) }
    }

    fun setModifierLockEnabled(enabled: Boolean) = update { it.copy(modifierLockEnabled = enabled) }

    /** Saves a profile as a user profile, shadowing a built-in with the same id. */
    fun saveProfile(profile: ToolbarProfile) = update { state ->
        val without = state.customProfiles.filterNot { it.id == profile.id }
        state.copy(customProfiles = without + profile)
    }

    /** Removes a user profile. Built-ins always remain available. */
    fun deleteProfile(id: String) = update { state ->
        val remaining = state.customProfiles.filterNot { it.id == id }
        val active = if (state.activeProfileId == id && !profileExists(remaining, id)) {
            ToolbarProfiles.default.id
        } else {
            state.activeProfileId
        }
        // Drop only overrides that no longer resolve to any profile (custom or built-in).
        val overrides = state.hostOverrides.filterValues { target ->
            remaining.any { it.id == target } || builtInProfiles.any { it.id == target }
        }
        state.copy(customProfiles = remaining, activeProfileId = active, hostOverrides = overrides)
    }

    /** Restores a built-in profile by discarding the user's shadowing copy. */
    fun resetProfileToBuiltIn(id: String) {
        if (builtInProfiles.none { it.id == id }) return
        deleteProfile(id)
    }

    fun duplicateProfile(id: String, copySuffix: String = "copy"): ToolbarProfile? {
        val source = profileById(id) ?: return null
        val newId = uniqueProfileId("${source.id}_copy")
        val copy = source.copy(
            id = newId,
            name = "${source.name} ${copySuffix.trim().ifBlank { "copy" }}".take(40),
            builtIn = false,
        )
        saveProfile(copy)
        return copy
    }

    fun saveTheme(theme: ToolbarTheme) = update { state ->
        state.copy(customThemes = state.customThemes.filterNot { it.id == theme.id } + theme)
    }

    fun saveMacro(macro: MacroDef) = update { state ->
        state.copy(customMacros = state.customMacros.filterNot { it.id == macro.id } + macro)
    }

    fun deleteMacro(id: String) = update { state ->
        state.copy(
            customMacros = state.customMacros.filterNot { it.id == id },
            customProfiles = state.customProfiles.map { profile ->
                profile.copy(macroIds = profile.macroIds.filterNot { it == id })
            },
        )
    }

    fun setHostOverride(hostId: String, profileId: String?) = update { state ->
        val overrides = state.hostOverrides.toMutableMap()
        if (profileId == null) overrides.remove(hostId) else overrides[hostId] = profileId
        state.copy(hostOverrides = overrides)
    }

    // --- import / export ---

    fun export(): String = ToolbarCodec.encode(_state.value)

    fun prepareImport(text: String): ToolbarImportCandidate? {
        if (text.length > MAX_IMPORT_BYTES) return null
        val decoded = ToolbarCodec.decode(text)
        if (decoded !is ToolbarDecode.Ok) return null
        val state = decoded.state
        if (state.customProfiles.size > MAX_IMPORTED_PROFILES ||
            state.customThemes.size > MAX_IMPORTED_THEMES ||
            state.customMacros.size > MAX_IMPORTED_MACROS ||
            state.hostOverrides.size > MAX_IMPORTED_HOST_OVERRIDES
        ) return null
        if (state.customProfiles.any { profile ->
                profile.layout.rows.size > ToolbarLayout.MAX_ROWS ||
                    profile.layout.keys.size > ToolbarLayout.MAX_KEYS ||
                    profile.optionalKeys.size > ToolbarLayout.MAX_KEYS ||
                    profile.macroIds.size > MAX_IMPORTED_MACROS
            }
        ) return null
        if (state.customMacros.any { MacroEngine.validate(it) !is MacroValidation.Valid }) return null
        return ToolbarImportCandidate(
            state = state,
            preview = ToolbarImportPreview(
                profiles = state.customProfiles.size,
                themes = state.customThemes.size,
                macros = state.customMacros.size,
                activeProfile = state.activeProfileId,
                warnings = ToolbarValidator.validate(state),
                omittedActions = decoded.omittedActions,
                enterMacros = state.customMacros.count { macro ->
                    macro.steps.any { step ->
                        step is MacroStep.Special && step.key == ToolbarSpecialKey.ENTER
                    }
                },
            ),
        )
    }

    fun previewImport(text: String): ToolbarImportPreview? = prepareImport(text)?.preview

    /** Applies the reviewed document wholesale only after a durable write; false means invalid. */
    fun applyImport(text: String): Boolean {
        val candidate = prepareImport(text) ?: return false
        return applyImport(candidate)
    }

    fun applyImport(candidate: ToolbarImportCandidate): Boolean {
        commit(candidate.state, durable = true)
        return true
    }

    /** Restores the shipped defaults and persists immediately. */
    fun resetToDefaults() {
        commit(ToolbarConfigState.default())
        recoveryNotice = null
        recoverableRaw = null
    }

    // --- internals ---

    private fun update(transform: (ToolbarConfigState) -> ToolbarConfigState) {
        commit(transform(_state.value))
    }

    /** Write the candidate before publishing it, so failed storage never leaves phantom state. */
    private fun commit(candidate: ToolbarConfigState, durable: Boolean = false) {
        val safe = sanitize(candidate)
        val encoded = ToolbarCodec.encode(safe)
        if (durable) prefs.putDurably(KEY_CONFIG, encoded) else prefs.put(KEY_CONFIG, encoded)
        recoveryNotice = null
        recoverableRaw = null
        _state.value = safe
    }

    private fun profileExists(
        custom: List<ToolbarProfile>,
        id: String,
    ): Boolean = custom.any { it.id == id } || builtInProfiles.any { it.id == id }

    private fun uniqueProfileId(base: String): String {
        val existing = allProfiles.map { it.id }.toSet()
        if (base !in existing) return base
        var n = 2
        while ("$base$n" in existing) n++
        return "$base$n"
    }

    /**
     * Rejects a state that cannot be rendered: an active profile that does not exist is
     * reset to the built-in default rather than left dangling. Other references (missing
     * macros, missing themes) are tolerated and surface as render-time fallbacks.
     */
    private fun sanitize(state: ToolbarConfigState): ToolbarConfigState {
        val knownProfile = state.customProfiles.any { it.id == state.activeProfileId } ||
            builtInProfiles.any { it.id == state.activeProfileId }
        return if (knownProfile) state else state.copy(activeProfileId = ToolbarProfiles.default.id)
    }

    companion object {
        const val KEY_CONFIG = "tcs_config_v1"
        const val MAX_IMPORT_BYTES = 1_048_576
        const val MAX_IMPORTED_PROFILES = 128
        const val MAX_IMPORTED_THEMES = 128
        const val MAX_IMPORTED_MACROS = 128
        const val MAX_IMPORTED_HOST_OVERRIDES = 1_024
    }
}
