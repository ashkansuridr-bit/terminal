package app.terminalssh.secure.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.VTermKey

data class TerminalModifiers(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)

enum class TerminalModifier { CTRL, ALT, SHIFT }

enum class TerminalKey(val vTermKey: Int) {
    ESCAPE(VTermKey.ESCAPE),
    TAB(VTermKey.TAB),
    UP(VTermKey.UP),
    DOWN(VTermKey.DOWN),
    LEFT(VTermKey.LEFT),
    RIGHT(VTermKey.RIGHT),
    HOME(VTermKey.HOME),
    END(VTermKey.END),
    PAGE_UP(VTermKey.PAGEUP),
    PAGE_DOWN(VTermKey.PAGEDOWN),
}

/**
 * Modifier state shared by termlib's IME, physical-key and toolbar paths.
 *
 * Two behaviours share this one object, in the order a terminal user expects:
 * a single tap latches a modifier for exactly the next keystroke, and a double tap
 * *locks* it so it survives keystrokes until the user taps it again. Locked modifiers
 * live in their own set and are re-applied in [clearTransients], which is why a locked
 * Ctrl does not silently let go after the first byte.
 *
 * [reset] exists so a disconnect, reconnect or session teardown cannot leave a stuck
 * modifier behind — lock is a convenience, not a trap.
 */
class TerminalInputController : ModifierManager {
    private val lock = Any()
    private val _modifiers = MutableStateFlow(TerminalModifiers())
    val modifiers: StateFlow<TerminalModifiers> = _modifiers.asStateFlow()

    private val _locked = MutableStateFlow(emptySet<TerminalModifier>())

    /** Modifiers held down across keystrokes until explicitly unlocked. */
    val locked: StateFlow<Set<TerminalModifier>> = _locked.asStateFlow()

    override fun isCtrlActive(): Boolean = _modifiers.value.ctrl
    override fun isAltActive(): Boolean = _modifiers.value.alt
    override fun isShiftActive(): Boolean = _modifiers.value.shift

    fun isLocked(modifier: TerminalModifier): Boolean = modifier in _locked.value

    /**
     * One tap. If the modifier is locked, a tap unlocks it — the same gesture that locked
     * it releases it, which is the least surprising way to get out of a lock.
     */
    fun toggle(modifier: TerminalModifier) {
        synchronized(lock) {
            if (modifier in _locked.value) {
                unlockLocked(modifier)
            } else {
                apply(modifier, !isActive(modifier))
            }
        }
    }

    /** Double tap: hold the modifier on across keystrokes. */
    fun lockModifier(modifier: TerminalModifier) {
        synchronized(lock) {
            _locked.value = _locked.value + modifier
            apply(modifier, true)
        }
    }

    fun unlockModifier(modifier: TerminalModifier) {
        synchronized(lock) { unlockLocked(modifier) }
    }

    private fun unlockLocked(modifier: TerminalModifier) {
        _locked.value = _locked.value - modifier
        apply(modifier, false)
    }

    /**
     * Consumes one-shot modifiers, re-applying any that are locked. This is called by the
     * terminal library after every keystroke and by the toolbar after every dispatch, so
     * it is the single place that decides what "release after use" means.
     */
    override fun clearTransients() {
        synchronized(lock) {
            val held = _locked.value
            _modifiers.value = TerminalModifiers(
                ctrl = TerminalModifier.CTRL in held,
                alt = TerminalModifier.ALT in held,
                shift = TerminalModifier.SHIFT in held,
            )
        }
    }

    /** Drops both one-shot and locked state. Used on disconnect so nothing stays stuck. */
    fun reset() {
        synchronized(lock) {
            _locked.value = emptySet()
            _modifiers.value = TerminalModifiers()
        }
    }

    fun modifierMask(): Int =
        (if (isShiftActive()) VTERM_MOD_SHIFT else 0) or
            (if (isAltActive()) VTERM_MOD_ALT else 0) or
            (if (isCtrlActive()) VTERM_MOD_CTRL else 0)

    private fun isActive(modifier: TerminalModifier): Boolean = when (modifier) {
        TerminalModifier.CTRL -> _modifiers.value.ctrl
        TerminalModifier.ALT -> _modifiers.value.alt
        TerminalModifier.SHIFT -> _modifiers.value.shift
    }

    private fun apply(modifier: TerminalModifier, on: Boolean) {
        val current = _modifiers.value
        _modifiers.value = when (modifier) {
            TerminalModifier.CTRL -> current.copy(ctrl = on)
            TerminalModifier.ALT -> current.copy(alt = on)
            TerminalModifier.SHIFT -> current.copy(shift = on)
        }
    }

    companion object {
        const val VTERM_MOD_SHIFT = 1
        const val VTERM_MOD_ALT = 2
        const val VTERM_MOD_CTRL = 4
    }
}
