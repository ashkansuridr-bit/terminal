package app.terminalssh.secure.ui.toolbar

import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ssh.TerminalModifier
import app.terminalssh.secure.toolbar.ChangeFontSizeAction
import app.terminalssh.secure.toolbar.ControlAction
import app.terminalssh.secure.toolbar.HideKeyboardAction
import app.terminalssh.secure.toolbar.MacroAction
import app.terminalssh.secure.toolbar.MacroEngine
import app.terminalssh.secure.toolbar.MacroRunner
import app.terminalssh.secure.toolbar.MacroSink
import app.terminalssh.secure.toolbar.ModifierAction
import app.terminalssh.secure.toolbar.OpenAgentsAction
import app.terminalssh.secure.toolbar.OpenKeyboardAction
import app.terminalssh.secure.toolbar.OpenPortForwardAction
import app.terminalssh.secure.toolbar.OpenSettingsAction
import app.terminalssh.secure.toolbar.OpenSnippetsAction
import app.terminalssh.secure.toolbar.OpenToolbarEditorAction
import app.terminalssh.secure.toolbar.PasteAction
import app.terminalssh.secure.toolbar.ReconnectAction
import app.terminalssh.secure.toolbar.SnippetAction
import app.terminalssh.secure.toolbar.SpecialKeyAction
import app.terminalssh.secure.toolbar.SwitchProfileAction
import app.terminalssh.secure.toolbar.TextAction
import app.terminalssh.secure.toolbar.ToggleComposeAction
import app.terminalssh.secure.toolbar.ToolbarAction
import app.terminalssh.secure.toolbar.ToolbarModifier
import app.terminalssh.secure.toolbar.ToolbarRepository
import app.terminalssh.secure.toolbar.ToolbarSpecialKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything a toolbar action can reach outside the toolbar itself.
 *
 * The renderer knows only about [ToolbarAction]; this interface is the seam where an action
 * becomes a session byte or a screen change. Keeping it an interface means the dispatch
 * rules can be read in one file, and the terminal screen supplies the concrete behaviour.
 */
interface ToolbarHost {
    val session: SshSession
    fun requestKeyboard()
    fun dismissKeyboard()
    fun toggleCompose()
    fun openSnippets()
    fun openAgents()
    fun openPortForward()
    fun openSettings()
    fun openToolbarEditor()
    fun insertSnippet(snippetId: String)
    fun changeFontSize(delta: Int)
    fun switchProfile(profileId: String)
    fun returnFocusToTerminal()
}

/**
 * Turns a typed [ToolbarAction] into a real effect.
 *
 * Terminal-input actions go through the session's existing dispatch methods, so they share
 * exactly the modifier handling, buffering and write-failure reporting as the rest of the
 * app. UI actions call the host. Macros run on a coroutine so delays do not block the main
 * thread, and they stop if the session has closed.
 */
class ToolbarDispatcher(
    private val host: ToolbarHost,
    private val repository: ToolbarRepository,
    private val scope: CoroutineScope,
) {

    fun dispatch(action: ToolbarAction) {
        when (action) {
            is SpecialKeyAction -> dispatchSpecial(action.key)
            is ControlAction -> {
                host.session.pressControl(action.letter)
                host.returnFocusToTerminal()
            }
            is TextAction -> dispatchText(action.text)
            is ModifierAction -> dispatchModifier(action)
            is SnippetAction -> host.insertSnippet(action.snippetId)
            is MacroAction -> runMacro(action.macroId)
            PasteAction -> host.session.requestPaste()
            ReconnectAction -> host.session.connect()
            OpenKeyboardAction -> host.requestKeyboard()
            HideKeyboardAction -> host.dismissKeyboard()
            ToggleComposeAction -> host.toggleCompose()
            OpenSnippetsAction -> host.openSnippets()
            OpenAgentsAction -> host.openAgents()
            OpenPortForwardAction -> host.openPortForward()
            OpenSettingsAction -> host.openSettings()
            OpenToolbarEditorAction -> host.openToolbarEditor()
            is ChangeFontSizeAction -> host.changeFontSize(action.delta)
            is SwitchProfileAction -> host.switchProfile(action.profileId)
        }
    }

    private fun dispatchSpecial(key: ToolbarSpecialKey) {
        if (key == ToolbarSpecialKey.BACK_TAB) {
            host.session.pressBackTab()
        } else {
            key.vTermKey()?.let { host.session.pressVTermKey(it) }
        }
        host.returnFocusToTerminal()
    }

    private fun dispatchText(text: String) {
        if (text.length == 1) {
            host.session.typeToolbarCharacter(text.single())
        } else {
            // Multi-character actions are insertion, not a single terminal character.
            host.session.send(text)
        }
        host.returnFocusToTerminal()
    }

    private fun dispatchModifier(action: ModifierAction) {
        val modifier = action.modifier.toTerminalModifier()
        val controller = host.session.terminalInput
        if (action.lock && !controller.isLocked(modifier)) {
            controller.lockModifier(modifier)
        } else {
            // A tap on an already-locked modifier releases it.
            controller.toggle(modifier)
        }
    }

    private fun runMacro(macroId: String) {
        val macro = repository.macroById(macroId) ?: return
        if (!MacroEngine.validate(macro).isValid) return
        if (host.session.state.value !is SshSessionState.Connected) return
        val channelEpoch = host.session.activeChannelEpoch
        val ops = MacroEngine.expand(macro)
        fun dispatchOnCapturedChannel(action: () -> Unit) {
            host.session.runIfConnectedAtEpoch(channelEpoch, action)
        }
        val sink = object : MacroSink {
            override fun sendText(text: String) {
                dispatchOnCapturedChannel { host.session.send(text) }
            }

            override fun sendSpecial(key: ToolbarSpecialKey) = dispatchOnCapturedChannel {
                dispatchSpecial(key)
            }

            override fun sendControl(letter: Char) {
                dispatchOnCapturedChannel { host.session.pressControl(letter) }
            }

            override fun setModifier(modifier: ToolbarModifier, lock: Boolean) {
                dispatchOnCapturedChannel { dispatchModifier(ModifierAction(modifier, lock)) }
            }
        }
        scope.launch {
            // Do not let delayed operations migrate onto a replacement shell after
            // automatic reconnect. Epoch comparison catches even a fast reconnect
            // whose transient state was not observed by this coroutine.
            MacroRunner(sink).execute(ops, cancelled = {
                host.session.state.value !is SshSessionState.Connected ||
                    host.session.activeChannelEpoch != channelEpoch
            })
        }
    }
}

internal fun ToolbarModifier.toTerminalModifier(): TerminalModifier = when (this) {
    ToolbarModifier.CTRL -> TerminalModifier.CTRL
    ToolbarModifier.ALT -> TerminalModifier.ALT
    ToolbarModifier.SHIFT -> TerminalModifier.SHIFT
}
