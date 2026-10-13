package app.terminalssh.secure.toolbar

/**
 * Resolving a touch into an action, and rejecting bindings that cannot mean what they say.
 *
 * Gesture handling is where a toolbar most easily sends something the user did not intend:
 * a long press that also fires the tap, a swipe that leaks into the page scroller, a repeat
 * bound to a button that opens a dialog. [resolve] is the single decision point, and
 * [conflicts] surfaces bindings that are ambiguous or impossible before they ship.
 */
object ToolbarGestures {

    /**
     * The action a gesture performs on [key].
     *
     * A plain tap always falls back to the key's own action, so a key is never dead just
     * because a gesture map is empty.
     */
    // There is no TAP entry in the map: the primary action *is* the tap action.
    fun resolve(key: ToolKeySpec, gesture: ToolbarGesture): ToolbarAction? = key.gestures[gesture]

    /** True only when binding this secondary action cannot conflict with the key's tap/repeat behavior. */
    fun canBind(key: ToolKeySpec, gesture: ToolbarGesture, action: ToolbarAction): Boolean {
        if (!key.enabled || action == key.action) return false
        if (gesture == ToolbarGesture.LONG_PRESS && key.repeatWhileHeld) return false
        return conflicts(key.copy(gestures = key.gestures + (gesture to action))).isEmpty()
    }

    /** The action performed by a plain tap or press without any secondary gesture. */
    fun tapAction(key: ToolKeySpec): ToolbarAction = key.action

    /** Whether a key's primary action should repeat while it is held down. */
    fun canRepeat(action: ToolbarAction): Boolean =
        action is ToolbarAction.TerminalInput && action !is MacroAction && action !is PasteAction

    /**
     * Binding problems for one key. Empty means the key is coherent.
     */
    fun conflicts(key: ToolKeySpec): List<String> {
        val problems = mutableListOf<String>()
        if (!key.enabled) return problems
        key.gestures.forEach { (gesture, action) ->
            if (action == key.action) {
                problems.add("${key.id}: ${gesture.name.lowercase()} repeats the primary action")
            }
            if (gesture == ToolbarGesture.LONG_PRESS && key.repeatWhileHeld) {
                problems.add("${key.id}: cannot both repeat while held and bind a long press")
            }
            if (action is ModifierAction && action.lock && gesture == ToolbarGesture.LONG_PRESS &&
                key.action is ModifierAction
            ) {
                problems.add("${key.id}: a long-press lock on a modifier key is ambiguous")
            }
        }
        if (key.repeatWhileHeld && !canRepeat(key.action)) {
            problems.add("${key.id}: this action cannot repeat while held")
        }
        return problems
    }
}

/**
 * Cross-checks a whole configuration for references that would silently do nothing: a
 * profile pointing at a missing theme, a macro id that no longer exists, a host override
 * for a deleted profile.
 */
object ToolbarValidator {

    fun validate(state: ToolbarConfigState): List<String> {
        val problems = mutableListOf<String>()

        val profileIds = state.customProfiles.map { it.id }
        if (profileIds.toSet().size != profileIds.size) problems.add("duplicate custom profile ids")

        val themeIds = state.customThemes.map { it.id }
        if (themeIds.toSet().size != themeIds.size) problems.add("duplicate custom theme ids")

        if (state.profileById(state.activeProfileId) == null) {
            problems.add("active profile '${state.activeProfileId}' does not exist")
        }

        (state.customProfiles + ToolbarProfiles.all).forEach { profile ->
            if (state.themeById(profile.themeId) == null) {
                problems.add("profile '${profile.id}' references missing theme '${profile.themeId}'")
            }
            profile.macroIds.forEach { macroId ->
                if (state.macroById(macroId) == null) {
                    problems.add("profile '${profile.id}' references missing macro '$macroId'")
                }
            }
            profile.layout.keys.forEach { key ->
                problems.addAll(
                    ToolbarGestures.conflicts(key).map { "${profile.id}: $it" },
                )
                val action = key.action
                if (action is MacroAction && state.macroById(action.macroId) == null) {
                    problems.add("profile '${profile.id}' key '${key.id}' uses missing macro '${action.macroId}'")
                }
                if (action is SwitchProfileAction && state.profileById(action.profileId) == null) {
                    problems.add("profile '${profile.id}' key '${key.id}' switches to missing profile '${action.profileId}'")
                }
            }
        }

        state.hostOverrides.forEach { (host, profileId) ->
            if (state.profileById(profileId) == null) {
                problems.add("host override for '$host' points at missing profile '$profileId'")
            }
        }

        return problems
    }
}
