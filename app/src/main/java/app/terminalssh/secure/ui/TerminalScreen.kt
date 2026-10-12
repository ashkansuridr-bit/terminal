package app.terminalssh.secure.ui

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.requiredSizeIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.terminalssh.secure.R
import app.terminalssh.secure.settings.SettingsRegistry
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ssh.TerminalKey
import app.terminalssh.secure.ssh.TerminalModifier
import app.terminalssh.secure.toolbar.ToolbarGesture
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.TerminalPalettes
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.ui.toolbar.StudioToolbar
import app.terminalssh.secure.ui.toolbar.ToolbarDispatcher
import app.terminalssh.secure.ui.toolbar.ToolbarEditorScreen
import app.terminalssh.secure.ui.toolbar.ToolbarHost
import app.terminalssh.secure.vm.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.terminal.Terminal

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(viewModel: AppViewModel, onGoToHosts: () -> Unit, onGoToSettings: () -> Unit) {
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
    var toolbarEditorOpen by remember { mutableStateOf(false) }
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
            // termlib attaches the FocusRequester only once its embedded editor view is
            // composed, so a tap that lands early can find it uninitialized and throw,
            // aborting this coroutine before view focus is requested. Tolerate that and
            // drive the real editor view directly below.
            runCatching { terminalFocusRequester.requestFocus() }
            // Give WindowManager a frame to publish the input connection, then make
            // sure termlib's editor actually holds view focus (a single request can
            // be dropped while the window is still attaching on slow devices).
            withFrameNanos { }
            rootView.focusTerminalInputView()
        }
        Unit
    }

    val showKeyboard = {
        keyboardScope.launch {
            runCatching { terminalFocusRequester.requestFocus() }
            // Terminal is a custom editor. Let focus publish its input connection before
            // asking the IME to attach, otherwise rapid dismiss/reopen taps can be ignored.
            withFrameNanos { }
            // The actual input connection belongs to termlib's embedded Android View, not
            // the surrounding Compose focus node. Target it directly when available,
            // retrying the focus request across frames if the window is still attaching.
            val editorFocused = rootView.focusTerminalInputView()
            val imeView = rootView.findTerminalInputView()
            if (editorFocused && imeView != null) {
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
    val imeVisible = WindowInsets.isImeVisible
    // A phone in landscape with the keyboard up can leave only a sliver of height. The
    // session tabs and connection strip are chrome the terminal can spare while typing;
    // yielding them keeps the fixed-height key toolbar inside the ime-padded column
    // instead of overflowing it and sliding under the keyboard.
    val compactForIme = imeVisible &&
        LocalConfiguration.current.screenHeightDp <= COMPACT_IME_HEIGHT_DP
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .then(if (imeVisible) Modifier else Modifier.navigationBarsPadding()),
    ) {
        if (!compactForIme) {
            SessionTabs(
                sessions = sessions,
                activeId = activeId,
                onSelect = viewModel.sessions::select,
                onClose = viewModel::closeSession,
            )
            StatusBar(active)
        }
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
        val toolbarState by viewModel.toolbar.state.collectAsStateWithLifecycle()
        val toolbarProfile = remember(toolbarState, active.profile.id) {
            viewModel.toolbar.effectiveProfile(active.profile.id)
        }
        val toolbarTheme = remember(toolbarState, toolbarProfile) {
            viewModel.toolbar.effectiveTheme(toolbarProfile)
        }
        val toolbarModifiers by active.terminalInput.modifiers.collectAsStateWithLifecycle()
        val toolbarLocked by active.terminalInput.locked.collectAsStateWithLifecycle()

        // The host is the seam between a typed toolbar action and the real app. It is built
        // fresh each recomposition so its lambdas always close over current state rather than
        // a stale snapshot captured on the first composition.
        val toolbarHost = object : ToolbarHost {
            override val session get() = active
            override fun requestKeyboard() = showKeyboard()
            override fun dismissKeyboard() { keyboardController?.hide() }
            override fun toggleCompose() { composeOpen = !composeOpen }
            override fun openSnippets() { snippetsOpen = true }
            override fun openAgents() { agentSheetOpen = true }
            override fun openPortForward() { portForwardOpen = true }
            override fun openSettings() = onGoToSettings()
            override fun openToolbarEditor() { toolbarEditorOpen = true }
            override fun insertSnippet(snippetId: String) {
                viewModel.snippets.value.firstOrNull { it.id == snippetId }
                    ?.let { viewModel.insertSnippet(it, active) }
            }
            override fun changeFontSize(delta: Int) {
                settingsStore.set(
                    SettingsRegistry.fontSize,
                    settingsStore.get(SettingsRegistry.fontSize) + delta,
                )
            }
            override fun switchProfile(profileId: String) { viewModel.toolbar.setActiveProfile(profileId) }
            override fun returnFocusToTerminal() = focusTerminal()
        }
        val toolbarDispatcher = ToolbarDispatcher(toolbarHost, viewModel.toolbar, scope)

        StudioToolbar(
            profile = toolbarProfile,
            theme = toolbarTheme,
            modifiers = toolbarModifiers,
            locked = toolbarLocked,
            hapticsEnabled = hapticKeys,
            onKeyAction = { key, gesture ->
                val action = if (gesture == null) key.action else key.gestures[gesture]
                if (action != null) toolbarDispatcher.dispatch(action)
            },
            onShowKeyboard = showKeyboard,
            onOpenEditor = { toolbarEditorOpen = true },
        )

        if (toolbarEditorOpen) {
            Dialog(
                onDismissRequest = { toolbarEditorOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                ToolbarEditorScreen(
                    repository = viewModel.toolbar,
                    onDismiss = { toolbarEditorOpen = false },
                )
            }
        }
        PasteAndHostKeyDialogs(viewModel, active)
        if (portForwardOpen) {
            val forwards by active.portForwards.collectAsStateWithLifecycle()
            PortForwardDialog(
                existingForwards = forwards,
                onAdd = { fwd ->
                    if (fwd.isLocal) active.addLocalForward(fwd.bindPort, fwd.host, fwd.port)
                    else active.addRemoteForward(fwd.bindPort, fwd.host, fwd.port)
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

/**
 * Requests focus on the embedded termlib editor, retrying across frames so a slow
 * window attach cannot silently drop the request (the window keeps focus while the
 * view tree is still settling). Returns whether the editor holds view focus.
 */
private suspend fun View.focusTerminalInputView(retries: Int = 6): Boolean {
    repeat(retries) { attempt ->
        val imeView = findTerminalInputView() ?: return false
        // termlib gives its invisible editor a 1px frame so it can hold focus. Right
        // after a BACK-dismissed keyboard that frame can collapse to zero height, and a
        // zero-sized view is not allowed to take focus — which deadlocks the show-
        // keyboard action (the editor can never regain focus, so the IME never returns).
        // Restore a 1px frame before requesting focus.
        if (imeView.width <= 0 || imeView.height <= 0) {
            imeView.layout(
                imeView.left,
                imeView.top,
                imeView.left + 1,
                imeView.top + 1,
            )
        }
        imeView.requestFocus()
        if (imeView.isFocused) return true
        if (attempt < retries - 1) delay(16)
    }
    return false
}

private const val TERMINAL_IME_VIEW_CLASS = "org.connectbot.terminal.ImeInputView"

// Window height (dp) at or below which the terminal hides its session tabs and status
// strip while the soft keyboard is up, so the key toolbar always clears the keyboard.
private const val COMPACT_IME_HEIGHT_DP = 480

@Composable
private fun SessionTabs(
    sessions: List<SshSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
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
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                StatusDot(state)
                Spacer(Modifier.width(8.dp))
                // One accessible element per tab: this box defines the whole title area as a
                // single node carrying the text together with selection state and the
                // reuse action (the inner label Text stays purely visual), matching the
                // settings-row pattern. The close action remains its own 48dp target
                // ("Close session …") next to it.
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(session.id) },
                        )
                        .clearAndSetSemantics {
                            // One node per tab: the announced name is the description,
                            // and selected state rides on the same node. Putting the
                            // title in `text` semantics instead split a bare text child
                            // off the control and (like the toggle keys above) hid the
                            // description from the accessibility tree.
                            this.contentDescription = session.title
                            this.selected = selected
                            role = Role.Tab
                            onClick {
                                onSelect(session.id)
                                true
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    Text(session.title, style = MaterialTheme.typography.labelLarge)
                }
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
