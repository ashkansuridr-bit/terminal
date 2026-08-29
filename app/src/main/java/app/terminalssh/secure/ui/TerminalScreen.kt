package app.terminalssh.secure.ui

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.settings.SettingsRegistry
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ssh.TerminalKey
import app.terminalssh.secure.ssh.TerminalModifier
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.TerminalPalettes
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel
import kotlinx.coroutines.launch
import org.connectbot.terminal.Terminal

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(viewModel: AppViewModel, onGoToHosts: () -> Unit) {
    val settingsRevision by viewModel.settingsStore.revision.collectAsStateWithLifecycle()
    val settingsStore = viewModel.settingsStore
    val palette = remember(settingsRevision) {
        val id = settingsStore.get(SettingsRegistry.theme)
        TerminalPalettes.firstOrNull { it.id == id }
            ?: TerminalPalettes.first { it.id == SettingsRegistry.theme.default }
    }
    val fontSize = settingsStore.get(SettingsRegistry.fontSize)
    val hapticKeys = settingsStore.get(SettingsRegistry.hapticKeys)
    val keepScreenOn = settingsStore.get(SettingsRegistry.keepScreenOn)
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    val activeId by viewModel.sessions.activeId.collectAsStateWithLifecycle()
    val active = sessions.firstOrNull { it.id == activeId }
    var localTerminalOpen by rememberSaveable { mutableStateOf(false) }

    if (active == null && localTerminalOpen) {
        BackHandler { localTerminalOpen = false }
        LocalTerminalScreen(onClose = { localTerminalOpen = false })
        return
    }

    if (active == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.hosts_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.hosts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.tab_hosts),
                color = Turquoise,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onGoToHosts)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.local_terminal),
                color = Turquoise,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { localTerminalOpen = true }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        return
    }

    var snippetsOpen by remember(active.id) { mutableStateOf(false) }
    var agentSheetOpen by remember(active.id) { mutableStateOf(false) }
    var composeOpen by remember(active.id) { mutableStateOf(false) }
    var portForwardOpen by remember(active.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Hoisted above the bar so an unsent prompt survives the bar closing, a reconnect,
    // or a rotation. Losing a long prompt to a passing tunnel is the other half of the
    // problem this bar solves.
    var composeDraft by rememberSaveable(active.id) { mutableStateOf("") }
    val terminalFocusRequester = remember(active.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    val keyboardScope = rememberCoroutineScope()

    DisposableEffect(rootView, active.id, keepScreenOn) {
        val previous = rootView.keepScreenOn
        rootView.keepScreenOn = keepScreenOn
        onDispose { rootView.keepScreenOn = previous }
    }

    DisposableEffect(active.id) {
        onDispose { active.terminalInput.clearTransients() }
    }

    val focusTerminal = {
        keyboardScope.launch {
            terminalFocusRequester.requestFocus()
            withFrameNanos { }
            rootView.findTerminalInputView()?.requestFocus()
        }
        Unit
    }

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

    // imePadding lifts the toolbar to sit directly on the keyboard; navigationBarsPadding
    // only applies when the keyboard is down, since the IME already covers the nav bar.
    // Applying both unconditionally is what leaves a dead strip under the toolbar.
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .then(if (WindowInsets.isImeVisible) Modifier else Modifier.navigationBarsPadding()),
    ) {
        SessionTabs(
            sessions = sessions,
            activeId = activeId,
            onSelect = viewModel.sessions::select,
            onClose = viewModel::closeSession,
        )
        StatusBar(active)
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            key(active.id, fontSize) {
                Terminal(
                    terminalEmulator = active.emulator,
                    initialFontSize = fontSize.sp,
                    backgroundColor = palette.background,
                    foregroundColor = palette.foreground,
                    keyboardEnabled = true,
                    showSoftKeyboard = true,
                    focusRequester = terminalFocusRequester,
                    modifierManager = active.terminalInput,
                    onPasteRequest = { active.requestPaste() },
                )
            }
        }
        if (composeOpen) {
            ComposeBar(
                draft = composeDraft,
                onDraftChange = { composeDraft = it },
                onSend = { text ->
                    // Sent as one payload with a single trailing newline, so a multi-line
                    // prompt reaches the agent as one submission rather than as N commands.
                    active.send(text.trimEnd() + "\n")
                    composeDraft = ""
                },
                onDismiss = { composeOpen = false },
            )
        }
        CompositionLocalProvider(LocalTerminalKeyHaptics provides hapticKeys) {
            KeyToolbar(
                active,
                onShowKeyboard = showKeyboard,
                onSnippets = { snippetsOpen = true },
                onAgents = { agentSheetOpen = true },
                onCompose = { composeOpen = !composeOpen },
                onPortForward = { portForwardOpen = true },
                composeActive = composeOpen,
                onTerminalFocus = focusTerminal,
            )
        }
        PasteAndHostKeyDialogs(viewModel, active)
        if (portForwardOpen) {
            val forwards by active.portForwards.collectAsStateWithLifecycle()
            PortForwardDialog(
                existingForwards = forwards,
                onAdd = { fwd ->
                    scope.launch {
                        try {
                            if (fwd.isLocal) active.addLocalForward(fwd.bindPort, fwd.host, fwd.port)
                            else active.addRemoteForward(fwd.bindPort, fwd.host, fwd.port)
                        } catch (_: Exception) {}
                    }
                    portForwardOpen = false
                },
                onRemove = { fwd ->
                    if (fwd.isLocal) active.removeLocalForward(fwd.bindPort)
                    else active.removeRemoteForward(fwd.bindPort)
                },
                onDismiss = { portForwardOpen = false },
            )
        }
        if (agentSheetOpen) {
            AgentInstallSheet(
                onDismiss = { agentSheetOpen = false },
                hasKey = { agent -> viewModel.hasAgentKey(agent, active.profile.id) },
                onSaveKey = { agent, hostScoped, key ->
                    viewModel.saveAgentKey(agent, active.profile.id.takeIf { hostScoped }, key)
                },
                onInjectKey = { agent -> viewModel.injectAgentKey(agent, active) },
                onRunScript = { script ->
                    agentSheetOpen = false
                    // Sent as terminal input rather than executed out of band, so the
                    // user watches it run in the session they are already looking at.
                    active.send(script + "\n")
                },
            )
        }
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
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
    // Shared with the host list rather than restated here: the two screens had already
    // drifted apart on what colour a connecting session is.
    val color by animateColorAsState(state.status().color, label = "status")

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

    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
 * Ctrl, Alt, and Shift latch for exactly one following keystroke, like a real terminal.
 */
@Composable
private fun KeyToolbar(
    session: SshSession,
    onShowKeyboard: () -> Unit,
    onSnippets: () -> Unit,
    onAgents: () -> Unit,
    onCompose: () -> Unit,
    onPortForward: () -> Unit,
    composeActive: Boolean,
    onTerminalFocus: () -> Unit,
) {
    val modifiers by session.terminalInput.modifiers.collectAsStateWithLifecycle()

    fun press(key: TerminalKey) {
        session.pressTerminalKey(key)
        onTerminalFocus()
    }

    fun type(text: String) {
        check(text.length == 1)
        session.typeToolbarCharacter(text.single())
        onTerminalFocus()
    }

    // Keys are ordered by how often they are actually reached for, because on a narrow
    // phone everything past the first handful costs a scroll. Arrows stay adjacent so the
    // cluster is findable by shape rather than by reading each label.
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        // A 600dp-wide window (large phone landscape, tablet, unfolded foldable) has room
        // for two rows, which removes the scroll entirely on those devices.
        val twoRows = maxWidth >= 600.dp

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolKey(
                label = "⌨",
                contentDescription = stringResource(R.string.show_keyboard),
                onClick = onShowKeyboard,
            )

            val primary: @Composable () -> Unit = {
                ToolKey("Ctrl", active = modifiers.ctrl, toggle = true) {
                    session.terminalInput.toggle(TerminalModifier.CTRL); onTerminalFocus()
                }
                ToolKey("Alt", active = modifiers.alt, toggle = true) {
                    session.terminalInput.toggle(TerminalModifier.ALT); onTerminalFocus()
                }
                ToolKey("Shift", active = modifiers.shift, toggle = true) {
                    session.terminalInput.toggle(TerminalModifier.SHIFT); onTerminalFocus()
                }
                ToolKey("Esc") { press(TerminalKey.ESCAPE) }
                ToolKey("Tab") { press(TerminalKey.TAB) }
                ToolKey(stringResource(R.string.snippets_short)) { onSnippets() }
                ToolKey(stringResource(R.string.agent_short)) { onAgents() }
                ToolKey("⚡") { onPortForward() }
                ToolKey(
                    stringResource(R.string.compose_short),
                    active = composeActive,
                    toggle = true,
                ) { onCompose() }
                ToolKey("^C", contentDescription = stringResource(R.string.terminal_key_interrupt)) {
                    session.pressControl('C'); onTerminalFocus()
                }
                ToolKey("^D", contentDescription = stringResource(R.string.terminal_key_eof)) {
                    session.pressControl('D'); onTerminalFocus()
                }
                ToolKey("^L", contentDescription = stringResource(R.string.terminal_key_clear)) {
                    session.pressControl('L'); onTerminalFocus()
                }
                ToolKey("↑", contentDescription = stringResource(R.string.terminal_key_up)) { press(TerminalKey.UP) }
                ToolKey("↓", contentDescription = stringResource(R.string.terminal_key_down)) { press(TerminalKey.DOWN) }
                ToolKey("←", contentDescription = stringResource(R.string.terminal_key_left)) { press(TerminalKey.LEFT) }
                ToolKey("→", contentDescription = stringResource(R.string.terminal_key_right)) { press(TerminalKey.RIGHT) }
            }

            val secondary: @Composable () -> Unit = {
                ToolKey("|") { type("|") }
                ToolKey("/") { type("/") }
                ToolKey("-") { type("-") }
                ToolKey("~") { type("~") }
                ToolKey("Home") { press(TerminalKey.HOME) }
                ToolKey("End") { press(TerminalKey.END) }
                ToolKey("PgUp") { press(TerminalKey.PAGE_UP) }
                ToolKey("PgDn") { press(TerminalKey.PAGE_DOWN) }
            }

            if (twoRows) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { primary() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { secondary() }
                }
            } else {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    val hapticsEnabled = LocalTerminalKeyHaptics.current
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val isPressed by interactions.collectIsPressedAsState()
    // A physical key gives travel; a glass one has to give something back instead.
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, Motion.press(), label = "key-press")

    val press: () -> Unit = {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }

    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private val LocalTerminalKeyHaptics = staticCompositionLocalOf { true }
