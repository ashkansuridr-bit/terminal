package app.terminalssh.secure.ui

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ui.theme.Amber
import app.terminalssh.secure.ui.theme.Cyan
import app.terminalssh.secure.ui.theme.Danger
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel
import kotlinx.coroutines.launch
import org.connectbot.terminal.Terminal

@Composable
fun TerminalScreen(viewModel: AppViewModel, onGoToHosts: () -> Unit) {
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    val activeId by viewModel.sessions.activeId.collectAsStateWithLifecycle()
    val active = sessions.firstOrNull { it.id == activeId }

    if (active == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.hosts_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.hosts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.tab_hosts),
                color = Turquoise,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onGoToHosts)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        return
    }

    var snippetsOpen by remember(active.id) { mutableStateOf(false) }
    val terminalFocusRequester = remember(active.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    val keyboardScope = rememberCoroutineScope()

    val showKeyboard = {
        keyboardScope.launch {
            terminalFocusRequester.requestFocus()
            // Terminal is a custom editor. Let focus publish its input connection before
            // asking the IME to attach, otherwise rapid dismiss/reopen taps can be ignored.
            withFrameNanos { }
            // The actual input connection belongs to termlib's embedded Android View, not
            // the surrounding Compose focus node. Target it directly when available.
            val imeView = rootView.findTerminalInputView()
            if (imeView != null) {
                imeView.requestFocus()
                val inputMethodManager = imeView.context
                    .getSystemService(InputMethodManager::class.java)
                inputMethodManager.showSoftInput(imeView, InputMethodManager.SHOW_IMPLICIT)
            } else {
                keyboardController?.show()
            }
        }
        Unit
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        SessionTabs(
            sessions = sessions,
            activeId = activeId,
            onSelect = viewModel.sessions::select,
            onClose = viewModel::closeSession,
        )
        StatusBar(active)
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Terminal(
                terminalEmulator = active.emulator,
                keyboardEnabled = true,
                showSoftKeyboard = true,
                focusRequester = terminalFocusRequester,
                onPasteRequest = { active.requestPaste() },
            )
        }
        KeyToolbar(
            active,
            onShowKeyboard = showKeyboard,
            onSnippets = { snippetsOpen = true },
        )
        PasteAndHostKeyDialogs(viewModel, active)
        if (snippetsOpen) {
            val snippets by viewModel.snippets.collectAsStateWithLifecycle()
            SnippetSheet(
                snippets = snippets,
                onDismiss = { snippetsOpen = false },
                onSave = viewModel::saveSnippet,
                onInsert = { entry ->
                    viewModel.insertSnippet(entry, active)
                    snippetsOpen = false
                },
                onDelete = viewModel::deleteSnippet,
            )
        }
    }
}

private fun View.findTerminalInputView(): View? {
    // ImeInputView is an internal Kotlin type in termlib, so identify the embedded text
    // editor without reflecting into its implementation.
    if (javaClass.name == TERMINAL_IME_VIEW_CLASS) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findTerminalInputView()?.let { return it }
    }
    return null
}

private const val TERMINAL_IME_VIEW_CLASS = "org.connectbot.terminal.ImeInputView"

@Composable
private fun SessionTabs(
    sessions: List<SshSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sessions.forEach { session ->
            val selected = session.id == activeId
            val state by session.state.collectAsStateWithLifecycle()
            val closeDescription = stringResource(R.string.close_session, session.title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                    )
                    .border(1.dp, if (selected) Turquoise.copy(alpha = 0.4f) else Stroke, RoundedCornerShape(12.dp))
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(session.id) },
                    )
                    .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            ) {
                StatusDot(state)
                Spacer(Modifier.width(8.dp))
                Text(session.title, style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = closeDescription,
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onClose(session.id) }
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(state: SshSessionState) {
    val target = when (state) {
        is SshSessionState.Connected -> Turquoise
        is SshSessionState.Connecting, is SshSessionState.Reconnecting -> Cyan
        is SshSessionState.AwaitingHostKeyApproval -> Amber
        is SshSessionState.Failed -> Danger
        else -> TextSecondary
    }
    val color by animateColorAsState(target, label = "status")

    // While a connection is being established the dot pulses, so "working" is readable
    // at a glance without occupying any more space than the idle indicator. Everything
    // else is a steady dot: motion here would mean nothing and cost battery.
    val busy = state.isBusy
    val transition = rememberInfiniteTransition(label = "status-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (busy) 1.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "status-pulse-scale",
    )

    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        if (busy) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = (1.6f - pulse).coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun StatusBar(session: SshSession) {
    val state by session.state.collectAsStateWithLifecycle()
    val text = when (val s = state) {
        SshSessionState.Idle -> stringResource(R.string.state_idle)
        SshSessionState.Connecting -> stringResource(R.string.state_connecting)
        is SshSessionState.AwaitingHostKeyApproval -> stringResource(R.string.state_verifying)
        SshSessionState.Connected -> stringResource(R.string.state_connected)
        is SshSessionState.Reconnecting -> stringResource(R.string.state_reconnecting) + " ${s.attempt}/${s.max}"
        is SshSessionState.Failed -> stringResource(s.kind.stringRes)
        SshSessionState.Closed -> stringResource(R.string.state_disconnected)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        StatusDot(state)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            ltr(session.profile.subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * The single most important mobile-SSH affordance: keys a soft keyboard does not have.
 * Ctrl and Alt latch for exactly one following keystroke, like a real terminal.
 */
@Composable
private fun KeyToolbar(
    session: SshSession,
    onShowKeyboard: () -> Unit,
    onSnippets: () -> Unit,
) {
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }

    fun sendText(text: String) {
        when {
            ctrl && text.length == 1 -> {
                val code = text.uppercase()[0].code - 64
                if (code in 1..31) session.send(byteArrayOf(code.toByte()))
                ctrl = false
            }
            alt && text.length == 1 -> {
                session.send(byteArrayOf(0x1B) + text.encodeToByteArray())
                alt = false
            }
            else -> session.send(text)
        }
    }

    // Keys are ordered by how often they are actually reached for, because on a narrow
    // phone everything past the first handful costs a scroll. Arrows stay adjacent so the
    // cluster is findable by shape rather than by reading each label.
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        // A 600dp-wide window (large phone landscape, tablet, unfolded foldable) has room
        // for two rows, which removes the scroll entirely on those devices.
        val twoRows = maxWidth >= 600.dp

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolKey(
                label = "⌨",
                contentDescription = stringResource(R.string.show_keyboard),
                onClick = onShowKeyboard,
            )

            val primary: @Composable () -> Unit = {
                ToolKey(stringResource(R.string.snippets_short)) { onSnippets() }
                ToolKey("Esc") { session.send(byteArrayOf(0x1B)) }
                ToolKey("Tab") { session.send(byteArrayOf(0x09)) }
                ToolKey("Ctrl", active = ctrl, toggle = true) { ctrl = !ctrl; alt = false }
                ToolKey("Alt", active = alt, toggle = true) { alt = !alt; ctrl = false }
                ToolKey("^C", contentDescription = stringResource(R.string.terminal_key_interrupt)) {
                    session.send(byteArrayOf(0x03)); ctrl = false
                }
                ToolKey("^D", contentDescription = stringResource(R.string.terminal_key_eof)) {
                    session.send(byteArrayOf(0x04)); ctrl = false
                }
                ToolKey("^L", contentDescription = stringResource(R.string.terminal_key_clear)) {
                    session.send(byteArrayOf(0x0C)); ctrl = false
                }
                ToolKey("↑", contentDescription = stringResource(R.string.terminal_key_up)) { session.send("\u001B[A") }
                ToolKey("↓", contentDescription = stringResource(R.string.terminal_key_down)) { session.send("\u001B[B") }
                ToolKey("←", contentDescription = stringResource(R.string.terminal_key_left)) { session.send("\u001B[D") }
                ToolKey("→", contentDescription = stringResource(R.string.terminal_key_right)) { session.send("\u001B[C") }
            }

            val secondary: @Composable () -> Unit = {
                ToolKey("|") { sendText("|") }
                ToolKey("/") { sendText("/") }
                ToolKey("-") { sendText("-") }
                ToolKey("~") { sendText("~") }
                ToolKey("Home") { session.send("\u001B[H") }
                ToolKey("End") { session.send("\u001B[F") }
                ToolKey("PgUp") { session.send("\u001B[5~") }
                ToolKey("PgDn") { session.send("\u001B[6~") }
            }

            if (twoRows) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { primary() }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { secondary() }
                }
            } else {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    primary()
                    secondary()
                }
            }
        }
    }
}

@Composable
private fun ToolKey(
    label: String,
    active: Boolean = false,
    toggle: Boolean = false,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val isPressed by interactions.collectIsPressedAsState()
    // A physical key gives travel; a glass one has to give something back instead.
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, Motion.press(), label = "key-press")

    val press: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }

    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Turquoise else MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (contentDescription != null) Modifier.semantics {
                    this.contentDescription = contentDescription
                } else Modifier,
            )
            .then(
                if (toggle) Modifier.toggleable(
                    value = active,
                    interactionSource = interactions,
                    indication = null,
                    role = Role.Button,
                    onValueChange = { press() },
                ) else Modifier.clickable(
                    interactionSource = interactions,
                    indication = null,
                    onClick = press,
                ),
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}
