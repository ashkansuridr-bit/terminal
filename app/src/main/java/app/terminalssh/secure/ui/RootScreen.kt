package app.terminalssh.secure.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ui.theme.Cyan
import app.terminalssh.secure.ui.theme.Ink
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel

enum class Tab { HOSTS, TERMINAL, FILES, KEYS, SETTINGS }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RootScreen(viewModel: AppViewModel, launchHostId: String? = null) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOSTS) }
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    // Survives recomposition so a shortcut cannot re-open the session on every recompose.
    var consumedLaunchId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = tab != Tab.HOSTS) {
        tab = Tab.HOSTS
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    val openTerminal: (HostProfile, CharArray?) -> Unit = { profile, password ->
        viewModel.openSession(profile, password)
        tab = Tab.TERMINAL
    }

    // A launcher shortcut names a host by id. Only hosts with a stored credential can
    // connect unattended; anything else just lands the user on the host list, where the
    // normal password prompt happens.
    LaunchedEffect(launchHostId, hosts) {
        val id = launchHostId ?: return@LaunchedEffect
        if (consumedLaunchId == id) return@LaunchedEffect
        val profile = hosts.firstOrNull { it.id == id } ?: return@LaunchedEffect
        consumedLaunchId = id
        if (viewModel.hasStoredSecret(profile)) openTerminal(profile, null)
    }

    // While the soft keyboard is up there is no room to spare, and the nav dock would
    // otherwise sit as dead space between the key toolbar and the keyboard — exactly
    // where the user is looking. It slides away instead of vanishing so the tab bar does
    // not appear to teleport when the keyboard closes.
    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        // The terminal manages its own IME inset so the toolbar can sit flush against
        // the keyboard; letting the Scaffold consume it too would double-count it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        TabItem(tab, Tab.HOSTS, Icons.Outlined.Dns, stringResource(R.string.tab_hosts)) { tab = it }
                        TabItem(
                            tab,
                            Tab.TERMINAL,
                            Icons.Outlined.Terminal,
                            stringResource(R.string.tab_terminal),
                            sessions.size,
                        ) { tab = it }
                        TabItem(tab, Tab.FILES, Icons.Outlined.Folder, stringResource(R.string.tab_files)) { tab = it }
                        TabItem(tab, Tab.KEYS, Icons.Outlined.VpnKey, stringResource(R.string.tab_keys)) { tab = it }
                        TabItem(tab, Tab.SETTINGS, Icons.Outlined.Settings, stringResource(R.string.tab_settings)) { tab = it }
                    }
                }
            }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Turquoise.copy(alpha = 0.055f),
                            Cyan.copy(alpha = 0.018f),
                            Ink,
                            Ink,
                        )
                    )
                )
                .padding(padding)
        ) {
            Crossfade(targetState = tab, label = "root-tab") { target ->
                when (target) {
                    Tab.HOSTS -> HostsScreen(viewModel, onConnect = openTerminal)
                    Tab.TERMINAL -> TerminalScreen(viewModel, onGoToHosts = { tab = Tab.HOSTS })
                    Tab.FILES -> FilesScreen(viewModel, onGoToHosts = { tab = Tab.HOSTS })
                    Tab.KEYS -> KeysScreen(viewModel)
                    Tab.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    current: Tab,
    target: Tab,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: Int = 0,
    onSelect: (Tab) -> Unit,
) {
    NavigationBarItem(
        selected = current == target,
        onClick = { onSelect(target) },
        icon = {
            if (badge > 0) {
                BadgedBox(badge = { Badge { Text("$badge") } }) {
                    Icon(icon, contentDescription = null)
                }
            } else {
                Icon(icon, contentDescription = null)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Turquoise,
            selectedTextColor = Turquoise,
            indicatorColor = Turquoise.copy(alpha = 0.12f),
        ),
    )
}
