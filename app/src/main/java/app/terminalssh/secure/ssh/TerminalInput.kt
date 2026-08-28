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

/** One-shot modifier state shared by termlib's IME, physical-key, and toolbar paths. */
class TerminalInputController : ModifierManager {
    private val lock = Any()
    private val _modifiers = MutableStateFlow(TerminalModifiers())
    val modifiers: StateFlow<TerminalModifiers> = _modifiers.asStateFlow()

    override fun isCtrlActive(): Boolean = _modifiers.value.ctrl
    override fun isAltActive(): Boolean = _modifiers.value.alt
    override fun isShiftActive(): Boolean = _modifiers.value.shift

    fun toggle(modifier: TerminalModifier) {
        synchronized(lock) {
            val current = _modifiers.value
            _modifiers.value = when (modifier) {
                TerminalModifier.CTRL -> current.copy(ctrl = !current.ctrl)
                TerminalModifier.ALT -> current.copy(alt = !current.alt)
                TerminalModifier.SHIFT -> current.copy(shift = !current.shift)
            }
        }
    }

    override fun clearTransients() {
        synchronized(lock) { _modifiers.value = TerminalModifiers() }
    }

    fun modifierMask(): Int =
        (if (isShiftActive()) VTERM_MOD_SHIFT else 0) or
            (if (isAltActive()) VTERM_MOD_ALT else 0) or
            (if (isCtrlActive()) VTERM_MOD_CTRL else 0)

    companion object {
        const val VTERM_MOD_SHIFT = 1
        const val VTERM_MOD_ALT = 2
        const val VTERM_MOD_CTRL = 4
    }
}
