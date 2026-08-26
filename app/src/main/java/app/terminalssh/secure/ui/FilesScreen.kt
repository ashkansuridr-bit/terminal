package app.terminalssh.secure.ui

import android.provider.DocumentsContract
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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

    // Cached per session in the ViewModel, not tied to this composable: switching tabs
    // must not tear the controller down mid-transfer. It's only closed when the session
    // itself closes (AppViewModel.closeSession).
    val sftp = remember(session.id) { viewModel.sftpControllerFor(session) }

    DisposableEffect(session.id) {
        if (!sftp.hasOpened) sftp.openHome()
        onDispose { }
    }

    val browser by sftp.browser.collectAsStateWithLifecycle()
    val transfers by sftp.queue.transfers.collectAsStateWithLifecycle()
    val transferHistory by sftp.queue.history.collectAsStateWithLifecycle()
    val uploadConflict by sftp.uploadConflict.collectAsStateWithLifecycle()
    var pendingDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    var pendingBatchDownload by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var pendingFolderDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) sftp.enqueueDownload(entry, uri)
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri -> sftp.enqueueUpload(uri, viewModel.displayNameFor(uri), browser.path) }
    }

    // One destination folder for the whole batch, picked once via SAF, rather than a
    // CreateDocument round-trip per file.
    val batchDownloadTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val entries = pendingBatchDownload
        pendingBatchDownload = emptyList()
        if (treeUri != null && entries.isNotEmpty()) {
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            entries.forEach { entry ->
                val name = app.terminalssh.secure.sftp.RemotePath.sanitizeDownloadName(entry.name)
                val destination = runCatching {
                    DocumentsContract.createDocument(context.contentResolver, parentDocUri, "application/octet-stream", name)
                }.getOrNull()
                if (destination != null) sftp.enqueueDownload(entry, destination)
            }
        }
    }

    // Folder download: picks a destination tree, then recursively downloads the folder
    val folderDownloadTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val entry = pendingFolderDownload
        pendingFolderDownload = null
        if (treeUri != null && entry != null) {
            sftp.downloadFolder(entry.path, treeUri, entry.name)
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TransferStrip(
            transfers = transfers,
            history = transferHistory,
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
            onCreateFolder = sftp::createDirectory,
            onRename = sftp::rename,
            onDelete = sftp::delete,
            onBatchDelete = sftp::deleteAll,
            onDownloadSelected = { entries ->
                pendingBatchDownload = entries
                batchDownloadTreeLauncher.launch(null)
            },
            fetchSymlinkTarget = sftp::symlinkTarget,
            onMoveTo = sftp::moveTo,
            onCopyTo = sftp::copyTo,
            listDirectories = sftp::listDirectories,
            onChmod = { entry, mode -> sftp.chmod(entry, mode) },
            onChmodRecursive = { path, mode -> sftp.chmodRecursive(path, mode) },
            onDownloadFolder = { entry ->
                pendingFolderDownload = entry
                folderDownloadTreeLauncher.launch(null)
            },
            onEditFile = { entry ->
                // Edit file: the dialog handles download/upload lifecycle
            },
            onPreviewFile = { entry ->
                // Preview file: the dialog handles download lifecycle
            },
            fetchFileText = sftp::downloadFileText,
            fetchFileTextForEdit = sftp::downloadFileTextForEdit,
            fetchFileBytes = sftp::downloadFileBytes,
            onUploadEditedText = { path, text -> sftp.uploadFileText(path, text) },
            onUploadEditedTextChecked = sftp::uploadFileTextChecked,
            onCompressSelected = { entries ->
                scope.launch {
                    try {
                        val remotePath = sftp.compressSelection(entries, sftp.browser.value.path)
                        sftp.refresh()
                    } catch (_: Exception) {}
                }
            },
        )
    }

    uploadConflict?.let { conflict ->
        UploadConflictDialog(conflict = conflict, onResolve = sftp::resolveConflict)
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
