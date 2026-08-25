package app.terminalssh.secure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.RemoteEntry
import app.terminalssh.secure.sftp.SftpController
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.vm.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * SFTP tab. Rides the currently selected terminal session rather than opening its own
 * connection, so browsing files never means authenticating a second time.
 */
@Composable
fun FilesScreen(viewModel: AppViewModel, onGoToHosts: () -> Unit) {
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    val session = sessions.firstOrNull { it.state.value.isLive } ?: sessions.firstOrNull()

    if (session == null) {
        NoSession(onGoToHosts)
        return
    }

    val context = LocalContext.current
    // Tied to this session: switching sessions rebuilds the controller and its queue.
    val controller = remember(session.id) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        SftpController(session, context.contentResolver, scope) to scope
    }
    val sftp = controller.first

    DisposableEffect(session.id) {
        sftp.openHome()
        onDispose {
            sftp.close()
            controller.second.cancel()
        }
    }

    val browser by sftp.browser.collectAsStateWithLifecycle()
    val transfers by sftp.queue.transfers.collectAsStateWithLifecycle()
    var pendingDownload by remember { mutableStateOf<RemoteEntry?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) sftp.enqueueDownload(entry, uri)
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sftp.enqueueUpload(uri, viewModel.displayNameFor(uri), browser.path)
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TransferStrip(
            transfers = transfers,
            onPause = sftp::pause,
            onResume = sftp::resume,
            onCancel = sftp::cancel,
            onClearFinished = sftp::clearFinished,
        )
        SftpBrowser(
            state = browser,
            onNavigate = sftp::navigate,
            onUp = sftp::navigateUp,
            onRefresh = sftp::refresh,
            onDownload = { entry ->
                pendingDownload = entry
                // SAF picks the destination, so the app never needs storage permission
                // and the user stays in control of where their files land.
                saveLauncher.launch(app.terminalssh.secure.sftp.RemotePath.sanitizeDownloadName(entry.name))
            },
            onUpload = { openLauncher.launch(arrayOf("*/*")) },
        )
    }
}

@Composable
private fun NoSession(onGoToHosts: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.sftp_no_session),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGoToHosts, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.tab_hosts))
            }
        }
    }
}
