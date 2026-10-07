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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SFTP tab. Rides the currently selected terminal session rather than opening its own
 * connection, so browsing files never means authenticating a second time.
 */
@Composable
fun FilesScreen(viewModel: AppViewModel, onGoToHosts: () -> Unit) {
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    val activeId by viewModel.sessions.activeId.collectAsStateWithLifecycle()
    val session = sessions.firstOrNull { it.id == activeId }
        ?: sessions.firstOrNull { it.state.value.isLive } ?: sessions.firstOrNull()

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
    val persistenceFailed by sftp.persistenceFailed.collectAsStateWithLifecycle()
    val transfers by sftp.queue.transfers.collectAsStateWithLifecycle()
    val transferHistory by sftp.queue.history.collectAsStateWithLifecycle()
    val uploadConflict by sftp.uploadConflict.collectAsStateWithLifecycle()
    var pendingDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    var pendingBatchDownload by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var pendingFolderDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    var pendingSyncEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var syncPlanTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var syncPlanActions by remember { mutableStateOf<List<SftpController.SyncAction>?>(null) }
    var syncSourceError by remember { mutableStateOf<String?>(null) }
    var syncCacheDir by remember { mutableStateOf<java.io.File?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Resolved at composition: String.format chains need the template, and the coroutine
    // callbacks below are not composable scope, so stringResource is unavailable there.
    val syncSourceErrorText = stringResource(R.string.sftp_sync_source_error)
    val syncFailedTemplate = stringResource(R.string.sftp_sync_failed)
    val compressErrorText = stringResource(R.string.sftp_compress_error)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) {
            // Persist the grant before the URI is written to the queue file, or a resume
            // after process death has a path it is no longer allowed to open.
            app.terminalssh.secure.sftp.SafPermissions.takePersistable(context.contentResolver, uri)
            sftp.enqueueDownload(entry, uri)
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            app.terminalssh.secure.sftp.SafPermissions.takePersistable(context.contentResolver, uri)
            sftp.enqueueUpload(uri, viewModel.displayNameFor(uri), browser.path)
        }
    }

    // One destination folder for the whole batch, picked once via SAF, rather than a
    // CreateDocument round-trip per file.
    val batchDownloadTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val entries = pendingBatchDownload
        pendingBatchDownload = emptyList()
        if (treeUri != null && entries.isNotEmpty()) {
            app.terminalssh.secure.sftp.SafPermissions.takePersistableTree(context.contentResolver, treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            entries.forEach { entry ->
                val name = app.terminalssh.secure.sftp.RemotePath.sanitizeDownloadName(entry.name)
                val destination = runCatching {
                    DocumentsContract.createDocument(context.contentResolver, parentDocUri, "application/octet-stream", name)
                }.getOrNull()
                if (destination != null) {
                    app.terminalssh.secure.sftp.SafPermissions.takePersistable(context.contentResolver, destination)
                    sftp.enqueueDownload(entry, destination)
                }
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
            app.terminalssh.secure.sftp.SafPermissions.takePersistableTree(context.contentResolver, treeUri)
            sftp.downloadFolder(entry.path, treeUri, entry.name)
        }
    }

    // Sync: the user picks a *local* folder, it is mirrored into app-owned scratch space
    // (the engine plans against real java.io.Files), and the preview dialog decides what
    // actually gets uploaded. Failure anywhere in this chain is surfaced, never swallowed.
    val syncSourceTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val entry = pendingSyncEntry
        pendingSyncEntry = null
        if (treeUri != null && entry != null) {
            app.terminalssh.secure.sftp.SafPermissions.takePersistableTree(context.contentResolver, treeUri)
            scope.launch {
                try {
                    val mirror = withContext(Dispatchers.IO) {
                        app.terminalssh.secure.sftp.SyncSourceMirror.mirrorTree(
                            java.io.File(context.cacheDir, "sync-source"),
                            context.contentResolver,
                            treeUri,
                            session.id,
                        )
                    }
                    val actions = sftp.computeSyncPlan(mirror, entry.path, deleteRemote = true)
                    syncCacheDir = mirror
                    syncPlanTarget = entry
                    syncPlanActions = actions
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    syncSourceError = String.format(syncFailedTemplate, failure.message ?: "")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        if (persistenceFailed) {
            Text(
                stringResource(R.string.transfer_persistence_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
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
            countRemoteFiles = { path -> sftp.countRemoteFiles(path) },
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
            onUploadEditedText = { path, text, force -> sftp.uploadFileText(path, text, force) },
            onUploadEditedTextChecked = sftp::checkFileTextConflict,
            onCompressSelected = { entries ->
                scope.launch {
                    try {
                        sftp.compressSelection(entries, sftp.browser.value.path)
                        sftp.refresh()
                    } catch (failure: Exception) {
                        // The user asked for a zip and got a dead tap before; now the real
                        // reason is shown instead of being silently dropped.
                        actionError = failure.message ?: compressErrorText
                    }
                }
            },
            onComputeSync = { entry ->
                pendingSyncEntry = entry
                syncSourceTreeLauncher.launch(null)
            },
            onExecuteSync = { entry, actions, deleteRemote ->
                scope.launch {
                    try {
                        val mirror = syncCacheDir
                        if (mirror == null) {
                            syncSourceError = syncSourceErrorText
                        } else {
                            // The checkbox decides whether DELETE_REMOTE on the plan runs.
                            val approved = if (deleteRemote) actions else actions.filter {
                                it.kind != SftpController.SyncAction.Kind.DELETE_REMOTE
                            }
                            sftp.executeSyncPlan(mirror, entry.path, approved)
                            withContext(Dispatchers.IO) {
                                app.terminalssh.secure.sftp.SyncSourceMirror.wipe(mirror)
                            }
                            syncCacheDir = null
                            sftp.refresh()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        syncSourceError = String.format(syncFailedTemplate, failure.message ?: "")
                    }
                }
            },
            onToggleBookmark = sftp::toggleBookmark,
            isBookmarked = sftp::isBookmarked,
            onComputeFolderSize = sftp::computeFolderSize,
            folderSizes = sftp.folderSizes.collectAsStateWithLifecycle().value,
            syncPlanTarget = syncPlanTarget,
            syncPlanActions = syncPlanActions,
            onSyncPlanDismissed = {
                syncPlanTarget = null
                syncPlanActions = null
            },
            syncSourceError = syncSourceError,
            onSyncSourceErrorDismiss = { syncSourceError = null },
        )
    }

    uploadConflict?.let { conflict ->
        UploadConflictDialog(conflict = conflict, onResolve = sftp::resolveConflict)
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text(stringResource(R.string.sftp_compress_error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { actionError = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
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
