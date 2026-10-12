package app.terminalssh.secure.ui.toolbar

import app.terminalssh.secure.toolbar.ToolbarSpecialKey
import org.connectbot.terminal.VTermKey

/**
 * The one place that knows how a toolbar key becomes a termlib key constant.
 *
 * BackTab is deliberately null: it is not a key of its own in the terminal library, it is
 * Shift+Tab, so the session dispatches it as a modifier combination instead.
 */
fun ToolbarSpecialKey.vTermKey(): Int? = when (this) {
    ToolbarSpecialKey.ESCAPE -> VTermKey.ESCAPE
    ToolbarSpecialKey.TAB -> VTermKey.TAB
    ToolbarSpecialKey.BACK_TAB -> null
    ToolbarSpecialKey.ENTER -> VTermKey.ENTER
    ToolbarSpecialKey.BACKSPACE -> VTermKey.BACKSPACE
    ToolbarSpecialKey.HOME -> VTermKey.HOME
    ToolbarSpecialKey.END -> VTermKey.END
    ToolbarSpecialKey.PAGE_UP -> VTermKey.PAGEUP
    ToolbarSpecialKey.PAGE_DOWN -> VTermKey.PAGEDOWN
    ToolbarSpecialKey.UP -> VTermKey.UP
    ToolbarSpecialKey.DOWN -> VTermKey.DOWN
    ToolbarSpecialKey.LEFT -> VTermKey.LEFT
    ToolbarSpecialKey.RIGHT -> VTermKey.RIGHT
    ToolbarSpecialKey.INSERT -> VTermKey.INS
    ToolbarSpecialKey.DELETE -> VTermKey.DEL
    ToolbarSpecialKey.F1 -> VTermKey.FUNCTION_1
    ToolbarSpecialKey.F2 -> VTermKey.FUNCTION_2
    ToolbarSpecialKey.F3 -> VTermKey.FUNCTION_3
    ToolbarSpecialKey.F4 -> VTermKey.FUNCTION_4
    ToolbarSpecialKey.F5 -> VTermKey.FUNCTION_5
    ToolbarSpecialKey.F6 -> VTermKey.FUNCTION_6
    ToolbarSpecialKey.F7 -> VTermKey.FUNCTION_7
    ToolbarSpecialKey.F8 -> VTermKey.FUNCTION_8
    ToolbarSpecialKey.F9 -> VTermKey.FUNCTION_9
    ToolbarSpecialKey.F10 -> VTermKey.FUNCTION_10
    ToolbarSpecialKey.F11 -> VTermKey.FUNCTION_11
    ToolbarSpecialKey.F12 -> VTermKey.FUNCTION_12
}
