package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.vm.AppViewModel

/** A lightweight dashboard; no polling or automatic reconnect/remote writes. */
@Composable
fun RecoveryDashboard(viewModel: AppViewModel, openHost: (HostProfile) -> Unit, onTerminal: () -> Unit, onFiles: () -> Unit) {
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val recoverable by viewModel.recoverableTransfers.collectAsStateWithLifecycle()
    val loading by viewModel.recoveryLoading.collectAsStateWithLifecycle()
    val cleanupFailures by viewModel.sessionCleanupFailures.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(sessions.size) { viewModel.refreshRecovery() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.recovery_summary, sessions.size, recoverable.size))
            if (cleanupFailures.isNotEmpty()) Text(
                stringResource(R.string.recovery_cleanup_failed, cleanupFailures.size),
                color = MaterialTheme.colorScheme.error,
            )
            Row {
                TextButton(onClick = { expanded = true; viewModel.refreshRecovery() }) {
                    Text(stringResource(R.string.recovery_open))
                }
                TextButton(onClick = viewModel::refreshRecovery, enabled = !loading) { Text(stringResource(R.string.recovery_refresh)) }
            }
        }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.recovery_title)) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.recovery_close)) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) item { CircularProgressIndicator() }
                if (cleanupFailures.isNotEmpty()) item {
                    Text(stringResource(R.string.recovery_cleanup_failed, cleanupFailures.size), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.recovery_cleanup_guidance))
                    TextButton(onClick = { viewModel.refreshRecovery() }, enabled = !loading) { Text(stringResource(R.string.recovery_refresh)) }
                }
                item { Text(stringResource(R.string.recovery_sessions), style = MaterialTheme.typography.titleSmall) }
                if (sessions.isEmpty()) item { Text(stringResource(R.string.recovery_no_sessions)) }
                items(sessions, key = { "session-${it.id}" }) { session ->
                    val state by session.state.collectAsStateWithLifecycle()
                    Column {
                        Text(session.title)
                        Text(ltr(session.profile.subtitle), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(when (state) {
                            SshSessionState.Connected -> R.string.state_connected
                            SshSessionState.Connecting -> R.string.state_connecting
                            is SshSessionState.Reconnecting -> R.string.state_reconnecting
                            is SshSessionState.AwaitingHostKeyApproval -> R.string.state_verifying
                            is SshSessionState.Failed -> R.string.state_failed
                            SshSessionState.Closed -> R.string.state_disconnected
                            SshSessionState.Idle -> R.string.state_idle
                        }))
                        TextButton(onClick = { viewModel.sessions.select(session.id); expanded = false; onTerminal() }) {
                            Text(stringResource(R.string.tab_terminal))
                        }
                    }
                }
                item { Text(stringResource(R.string.recovery_transfers), style = MaterialTheme.typography.titleSmall) }
                if (recoverable.isEmpty()) item { Text(stringResource(R.string.recovery_no_transfers)) }
                items(recoverable, key = { it.file.absolutePath }) { entry ->
                    val host = hosts.firstOrNull { it.id == entry.hostId }
                    Column {
                        Text(host?.displayName ?: stringResource(R.string.recovery_unknown_server))
                        if (entry.server.isNotBlank()) Text(ltr(entry.server), style = MaterialTheme.typography.bodySmall)
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAt)), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(if (entry.identityComplete && host != null) R.string.recovery_paused_notice else R.string.recovery_legacy_notice))
                        if (entry.identityComplete && host != null) Row {
                            TextButton(onClick = {
                                expanded = false
                                val current = sessions.firstOrNull {
                                    it.state.value.isLive && it.profile.id == host.id &&
                                        it.profile.host == host.host && it.profile.port == host.port &&
                                        it.profile.username == host.username && it.profile.jumpHostId == host.jumpHostId
                                }
                                if (current == null) openHost(host)
                                else { viewModel.sessions.select(current.id); onTerminal() }
                            }, enabled = !loading) { Text(stringResource(R.string.recovery_connect)) }
                            TextButton(onClick = { viewModel.restoreTransfers(entry) }, enabled = !loading) { Text(stringResource(R.string.recovery_restore)) }
                        }
                    }
                }
                item { TextButton(onClick = { expanded = false; onFiles() }) { Text(stringResource(R.string.tab_files)) } }
                item { Text(stringResource(R.string.recovery_recent), style = MaterialTheme.typography.titleSmall) }
                items(hosts.filter { it.lastConnectedAt > 0 }.sortedByDescending { it.lastConnectedAt }.take(5), key = { "recent-${it.id}" }) { host ->
                    TextButton(onClick = { expanded = false; openHost(host) }) { Text(host.displayName) }
                }
                item { Text(stringResource(R.string.recovery_shell_notice), style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}
