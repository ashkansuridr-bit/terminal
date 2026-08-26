package app.terminalssh.secure.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.RemoteEntry
import app.terminalssh.secure.sftp.RemotePath
import app.terminalssh.secure.sftp.SftpController
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import kotlinx.coroutines.launch

/**
 * Remote file browser.
 *
 * Layout is intentionally list-first rather than a grid: on a phone, filenames are the
 * information that matters and truncating them into a grid cell defeats the point.
 */
@Composable
fun SftpBrowser(
    state: SftpController.BrowserState,
    onNavigate: (String) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (RemoteEntry) -> Unit,
    onUpload: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (RemoteEntry, String) -> Unit,
    onDelete: (RemoteEntry) -> Unit,
    onBatchDelete: (List<RemoteEntry>) -> Unit,
    onDownloadSelected: (List<RemoteEntry>) -> Unit,
    fetchSymlinkTarget: suspend (String) -> String?,
    onMoveTo: (RemoteEntry, String) -> Unit,
    onCopyTo: (RemoteEntry, String) -> Unit,
    listDirectories: suspend (String) -> List<RemoteEntry>,
    onChmod: (RemoteEntry, Int) -> Unit = { _, _ -> },
    onChmodRecursive: (String, Int) -> Unit = { _, _ -> },
    onDownloadFolder: (RemoteEntry) -> Unit = {},
    onEditFile: (RemoteEntry) -> Unit = {},
    onPreviewFile: (RemoteEntry) -> Unit = {},
    fetchFileText: (suspend (String) -> Result<String>)? = null,
    fetchFileTextForEdit: (suspend (String) -> Result<Pair<String, Long>>)? = null,
    fetchFileBytes: (suspend (String) -> Result<ByteArray>)? = null,
    onUploadEditedText: ((String, String) -> Unit)? = null,
    onUploadEditedTextChecked: (suspend (String, String) -> Result<Boolean>)? = null,
    onCompressSelected: (List<RemoteEntry>) -> Unit = {},
    onSyncToRemote: ((RemoteEntry) -> Unit)? = null,
    onComputeSync: ((RemoteEntry) -> Unit)? = null,
    onExecuteSync: ((RemoteEntry, List<SftpController.SyncAction>, Boolean) -> Unit)? = null,
    onToggleBookmark: ((String) -> Unit)? = null,
    isBookmarked: ((String) -> Boolean)? = null,
    onComputeFolderSize: ((String) -> Unit)? = null,
    folderSizes: Map<String, Long> = emptyMap(),
    onSaveSyncPreset: ((SftpController.SyncPreset) -> Unit)? = null,
    syncPresets: List<SftpController.SyncPreset> = emptyList(),
    onLoadSyncPreset: ((SftpController.SyncPreset) -> Unit)? = null,
) {
    var newFolderPrompt by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var detailsTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var moveTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var copyTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var downloadFolderTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var editTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var previewTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var syncTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var syncPlan by remember { mutableStateOf<List<SftpController.SyncAction>?>(null) }
    var syncPlanDir by remember { mutableStateOf<RemoteEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleteSelectedPrompt by remember { mutableStateOf(false) }
    var chmodTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // A directory change invalidates any selection made in the previous one.
    LaunchedEffect(state.path) {
        selectionMode = false
        selected = emptySet()
    }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            // Directories aren't included yet — recursive folder download is a separate,
            // larger feature; only files in the current selection are downloadable here.
            val downloadableCount = state.entries.count { it.path in selected && !it.isDirectory }
            SelectionBar(
                count = selected.size,
                downloadEnabled = downloadableCount > 0,
                onDownloadSelected = {
                    onDownloadSelected(state.entries.filter { it.path in selected && !it.isDirectory })
                },
                onDeleteSelected = { deleteSelectedPrompt = true },
                onCompressSelected = {
                    val entriesToCompress = state.entries.filter { it.path in selected }
                    if (entriesToCompress.isNotEmpty()) {
                        onCompressSelected(entriesToCompress)
                        selectionMode = false; selected = emptySet()
                    }
                },
                onClose = { selectionMode = false; selected = emptySet() },
            )
        } else {
            PathBar(
                path = state.path,
                onNavigate = onNavigate,
                onUp = onUp,
                onRefresh = onRefresh,
                onUpload = onUpload,
                onNewFolder = { newFolderPrompt = true },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isBookmarked = isBookmarked?.invoke(state.path) == true,
                onToggleBookmark = if (onToggleBookmark != null) {{ onToggleBookmark(state.path) }} else null,
            )
        }

        // Reserves its own height so the list below never jumps when loading starts.
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = state.loading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = Turquoise,
                    trackColor = Stroke,
                )
            }
        }

        AnimatedVisibility(
            visible = state.errorKind != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.errorKind?.let { kind ->
                Text(
                    stringResource(kind.stringRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }

        if (state.entries.isEmpty() && !state.loading) {
            EmptyDirectory()
        } else {
            // Filter entries by search query (#35 search)
            val filteredEntries = if (searchQuery.isBlank()) state.entries
            else state.entries.filter { it.name.contains(searchQuery, ignoreCase = true) }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filteredEntries, key = { it.path }) { entry ->
                    EntryRow(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = entry.path in selected,
                        isBookmarked = isBookmarked?.invoke(entry.path) == true,
                        folderSize = folderSizes[entry.path],
                        onClick = {
                            when {
                                selectionMode -> selected = selected.toggle(entry.path)
                                entry.isDirectory -> onNavigate(entry.path)
                                else -> onDownload(entry)
                            }
                        },
                        onToggleSelect = { selected = selected.toggle(entry.path) },
                        onSelectRequest = {
                            selectionMode = true
                            selected = setOf(entry.path)
                        },
                        onRenameRequest = { renameTarget = entry },
                        onDeleteRequest = { deleteTarget = entry },
                        onDetailsRequest = { detailsTarget = entry },
                    onMoveRequest = { moveTarget = entry },
                    onCopyRequest = { copyTarget = entry },
                    onChmodRequest = { chmodTarget = entry },
                    onDownloadFolderRequest = if (entry.isDirectory) {{ downloadFolderTarget = entry }} else null,
                    onEditRequest = if (!entry.isDirectory) {{ editTarget = entry }} else null,
                    onPreviewRequest = if (!entry.isDirectory) {{ previewTarget = entry }} else null,
                    onSyncRequest = if (entry.isDirectory) {{ syncTarget = entry }} else null,
                    onToggleBookmark = if (onToggleBookmark != null) {{ onToggleBookmark(entry.path) }} else null,
                    onComputeSize = if (entry.isDirectory && onComputeFolderSize != null) {{ onComputeFolderSize(entry.path) }} else null,
                )
                }
            }
        }
    }

    moveTarget?.let { entry ->
        FolderPickerDialog(
            title = stringResource(R.string.sftp_move_to),
            startPath = RemotePath.parent(entry.path),
            listDirectories = listDirectories,
            onPick = { destination -> onMoveTo(entry, destination); moveTarget = null },
            onDismiss = { moveTarget = null },
        )
    }

    copyTarget?.let { entry ->
        FolderPickerDialog(
            title = stringResource(R.string.sftp_copy_to),
            startPath = RemotePath.parent(entry.path),
            listDirectories = listDirectories,
            onPick = { destination -> onCopyTo(entry, destination); copyTarget = null },
            onDismiss = { copyTarget = null },
        )
    }

    detailsTarget?.let { entry ->
        EntryDetailsDialog(
            entry = entry,
            fetchSymlinkTarget = fetchSymlinkTarget,
            onChmodRequest = { chmodTarget = entry },
            onDismiss = { detailsTarget = null },
        )
    }

    chmodTarget?.let { entry ->
        ChmodDialog(
            currentPermissions = entry.permissions,
            fileName = entry.name,
            onConfirm = { mode -> onChmod(entry, mode); chmodTarget = null },
            onDismiss = { chmodTarget = null },
        )
    }

    downloadFolderTarget?.let { entry ->
        DownloadFolderConfirmDialog(
            entry = entry,
            onConfirm = { onDownloadFolder(entry); downloadFolderTarget = null },
            onDismiss = { downloadFolderTarget = null },
        )
    }

    syncTarget?.let { entry ->
        onComputeSync?.invoke(entry)
        syncTarget = null
    }

    syncPlan?.let { actions ->
        val dir = syncPlanDir
        if (dir != null) {
            SyncConfirmDialog(
                actions = actions,
                onConfirm = { deleteRemote ->
                    onExecuteSync?.invoke(dir, actions, deleteRemote)
                    syncPlan = null; syncPlanDir = null
                },
                onDismiss = { syncPlan = null; syncPlanDir = null },
            )
        }
    }

    editTarget?.let { entry ->
        RemoteTextEditor(
            entry = entry,
            fetchFileText = fetchFileText,
            fetchFileTextForEdit = fetchFileTextForEdit,
            onUpload = { text -> onUploadEditedText?.invoke(entry.path, text); editTarget = null },
            onUploadChecked = onUploadEditedTextChecked,
            onDismiss = { editTarget = null },
        )
    }

    previewTarget?.let { entry ->
        val isImage = FileTypeIcons.mimeFor(entry.name).startsWith("image/")
        if (isImage) {
            RemoteImagePreview(
                entry = entry,
                fetchFileBytes = fetchFileBytes,
                onDismiss = { previewTarget = null },
            )
        } else {
            RemoteTextPreview(
                entry = entry,
                fetchFileText = fetchFileText,
                onDismiss = { previewTarget = null },
            )
        }
    }

    if (deleteSelectedPrompt) {
        val targets = state.entries.filter { it.path in selected }
        AlertDialog(
            onDismissRequest = { deleteSelectedPrompt = false },
            title = { Text(stringResource(R.string.sftp_delete_confirm_title)) },
            text = { Text(stringResource(R.string.sftp_delete_selected_confirm_body, targets.size)) },
            confirmButton = {
                TextButton(onClick = {
                    onBatchDelete(targets)
                    deleteSelectedPrompt = false
                    selectionMode = false
                    selected = emptySet()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectedPrompt = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (newFolderPrompt) {
        NameInputDialog(
            title = stringResource(R.string.sftp_new_folder_title),
            initialValue = "",
            onConfirm = { name ->
                onCreateFolder(name)
                newFolderPrompt = false
            },
            onDismiss = { newFolderPrompt = false },
        )
    }

    renameTarget?.let { entry ->
        NameInputDialog(
            title = stringResource(R.string.sftp_conflict_rename),
            initialValue = entry.name,
            onConfirm = { name ->
                onRename(entry, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.sftp_delete_confirm_title)) },
            text = { Text(stringResource(R.string.sftp_delete_confirm_body, entry.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** A single-field name prompt shared by "new folder" and "rename". */
@Composable
private fun NameInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Breadcrumbs that scroll horizontally. A deep path on a narrow phone would otherwise
 * either truncate the part the user needs or wrap into several lines.
 */
private fun Set<String>.toggle(item: String): Set<String> = if (item in this) this - item else this + item

/** Replaces [PathBar] while one or more entries are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    downloadEnabled: Boolean,
    onDownloadSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, stringResource(R.string.cancel), tint = TextSecondary)
        }
        Text(
            stringResource(R.string.sftp_selected_count, count),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDownloadSelected, enabled = downloadEnabled) {
            Icon(Icons.Outlined.Download, stringResource(R.string.sftp_download_selected), tint = Turquoise)
        }
        IconButton(onClick = onCompressSelected, enabled = count > 0) {
            Icon(Icons.Outlined.Description, stringResource(R.string.sftp_compress_selection), tint = Turquoise)
        }
        IconButton(onClick = onDeleteSelected, enabled = count > 0) {
            Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PathBar(
    path: String,
    onNavigate: (String) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
) {
    val crumbs = app.terminalssh.secure.sftp.RemotePath.breadcrumbs(path)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onUp,
            enabled = path != app.terminalssh.secure.sftp.RemotePath.ROOT,
            modifier = Modifier.semantics {
                contentDescription = "Go to parent directory"
            },
        ) {
            Icon(Icons.Outlined.ArrowUpward, null, tint = TextSecondary)
        }

        LazyRow(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(crumbs, key = { it.second }) { (name, target) ->
                val isCurrent = target == path
                Text(
                    text = if (name == "/") "/" else "$name  ›",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isCurrent) { onNavigate(target) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }

        IconButton(onClick = onNewFolder) {
            Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.sftp_new_folder_title), tint = TextSecondary)
        }
        if (onToggleBookmark != null) {
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (isBookmarked) Icons.Outlined.Star else Icons.Outlined.Star,
                    stringResource(R.string.sftp_bookmark),
                    tint = if (isBookmarked) MaterialTheme.colorScheme.tertiary else TextSecondary,
                )
            }
        }
        IconButton(onClick = onUpload) {
            Icon(Icons.Outlined.Upload, stringResource(R.string.sftp_upload), tint = Turquoise)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, stringResource(R.string.sftp_refresh), tint = TextSecondary)
        }
    }
    // Search bar
    if (onSearchQueryChange !== {}) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.sftp_search_hint), style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                textStyle = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: RemoteEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onSelectRequest: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDetailsRequest: () -> Unit,
    onMoveRequest: () -> Unit,
    onCopyRequest: () -> Unit,
    onChmodRequest: () -> Unit = {},
    onDownloadFolderRequest: (() -> Unit)? = null,
    onEditRequest: (() -> Unit)? = null,
    onPreviewRequest: (() -> Unit)? = null,
    onSyncRequest: (() -> Unit)? = null,
    isBookmarked: Boolean = false,
    folderSize: Long? = null,
    onToggleBookmark: (() -> Unit)? = null,
    onComputeSize: (() -> Unit)? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, Motion.press(), label = "row-press")
    val background by animateColorAsState(
        when {
            selected -> Turquoise.copy(alpha = 0.12f)
            pressed -> Turquoise.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        Motion.quick(),
        label = "row-bg",
    )

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                // Outside selection mode, long-press opens a per-item menu (rename,
                // delete, or start selecting); inside it, tapping already toggles, so a
                // long-press has nothing extra to offer.
                .combinedClickable(
                    onClick = {
                        pressed = false
                        onClick()
                    },
                    onLongClick = if (selectionMode) null else ({ menuOpen = true }),
                )
                // A 56dp row clears the 48dp minimum touch target with room for a mis-tap.
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            } else {
                Icon(
                    imageVector = when {
                        entry.isSymlink -> Icons.Outlined.Link
                        entry.isDirectory -> Icons.Outlined.Folder
                        else -> FileTypeIcons.forExtension(entry.name)
                    },
                    contentDescription = null,
                    tint = when {
                        entry.isDirectory -> Turquoise
                        entry.isSymlink -> TextSecondary.copy(alpha = 0.8f)
                        else -> TextSecondary
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ltr(entry.name),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (isBookmarked) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = stringResource(R.string.sftp_bookmark),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val sizeText = when {
                    entry.isDirectory && folderSize != null -> FileSize.format(folderSize)
                    entry.isDirectory -> ""
                    else -> FileSize.format(entry.sizeBytes)
                }
                val permText = entry.permissions
                val subtitle = listOfNotNull(
                    sizeText.takeIf { it.isNotEmpty() },
                    permText.takeIf { it.isNotEmpty() },
                ).joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_select)) },
                onClick = { menuOpen = false; onSelectRequest() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_conflict_rename)) },
                onClick = { menuOpen = false; onRenameRequest() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_move_to)) },
                onClick = { menuOpen = false; onMoveRequest() },
            )
            if (!entry.isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_copy_to)) },
                    onClick = { menuOpen = false; onCopyRequest() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = { menuOpen = false; onDeleteRequest() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_chmod)) },
                onClick = { menuOpen = false; onChmodRequest() },
            )
            if (onDownloadFolderRequest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_recursive_folder_download)) },
                    onClick = { menuOpen = false; onDownloadFolderRequest() },
                )
            }
            if (onSyncRequest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_sync_to_remote)) },
                    onClick = { menuOpen = false; onSyncRequest() },
                )
            }
            if (onEditRequest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_edit_file)) },
                    onClick = { menuOpen = false; onEditRequest() },
                )
            }
            if (onPreviewRequest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_preview)) },
                    onClick = { menuOpen = false; onPreviewRequest() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_properties)) },
                onClick = { menuOpen = false; onDetailsRequest() },
            )
            if (onToggleBookmark != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (isBookmarked) R.string.sftp_unbookmark else R.string.sftp_bookmark)) },
                    onClick = { menuOpen = false; onToggleBookmark() },
                )
            }
            if (onComputeSize != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_compute_size)) },
                    onClick = { menuOpen = false; onComputeSize() },
                )
            }
        }
    }
}

@Composable
private fun EmptyDirectory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Folder,
                null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.sftp_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

/**
 * Confirmation dialog for recursive folder download. Simple — just the name —
 * because the file count is discovered asynchronously and the actual I/O is
 * handled by the queue. Matches the app's "show consequences before action" principle.
 */
@Composable
private fun DownloadFolderConfirmDialog(
    entry: RemoteEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_recursive_folder_download)) },
        text = { Text(stringResource(R.string.sftp_recursive_download_confirm, entry.name, 0)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.download)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RemoteTextEditor(
    entry: RemoteEntry,
    fetchFileText: (suspend (String) -> Result<String>)?,
    fetchFileTextForEdit: (suspend (String) -> Result<Pair<String, Long>>)? = null,
    onUpload: (String) -> Unit,
    onUploadChecked: (suspend (String, String) -> Result<Boolean>)? = null,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var concurrentEditWarning by remember { mutableStateOf(false) }
    var hasConcurrentEdit by remember { mutableStateOf(false) }

    LaunchedEffect(entry.path) {
        loading = true
        error = null
        if (fetchFileTextForEdit != null) {
            fetchFileTextForEdit(entry.path)
                .onSuccess { (text, _) -> content = text; loading = false }
                .onFailure { e -> error = e.message; loading = false }
        } else {
            fetchFileText?.invoke(entry.path)
                ?.onSuccess { text -> content = text; loading = false }
                ?.onFailure { e -> error = e.message; loading = false }
        }
    }

    val scope = rememberCoroutineScope()

    if (concurrentEditWarning) {
        AlertDialog(
            onDismissRequest = { concurrentEditWarning = false },
            title = { Text(stringResource(R.string.sftp_concurrent_edit_title)) },
            text = { Text(stringResource(R.string.sftp_concurrent_edit_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    concurrentEditWarning = false
                    onUpload(content)
                }) {
                    Text(stringResource(R.string.sftp_force_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { concurrentEditWarning = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    } else {
        TextEditorDialog(
            fileName = entry.name,
            isLoading = loading,
            content = content,
            onContentChange = { content = it },
            onSave = {
                if (onUploadChecked != null) {
                    scope.launch {
                        onUploadChecked(entry.path, content)
                            .onSuccess { modifiedExternally ->
                                if (modifiedExternally) {
                                    concurrentEditWarning = true
                                } else {
                                    onUpload(content)
                                }
                            }
                            .onFailure { onUpload(content) }
                    }
                } else {
                    onUpload(content)
                }
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun RemoteTextPreview(
    entry: RemoteEntry,
    fetchFileText: (suspend (String) -> Result<String>)?,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf("") }

    LaunchedEffect(entry.path) {
        loading = true
        val result = fetchFileText?.invoke(entry.path)
        result?.onSuccess { text -> content = text }
        loading = false
    }

    TextPreviewDialog(
        fileName = entry.name,
        isLoading = loading,
        content = content,
        onDismiss = onDismiss,
    )
}

@Composable
private fun RemoteImagePreview(
    entry: RemoteEntry,
    fetchFileBytes: (suspend (String) -> Result<ByteArray>)?,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(entry.path) {
        loading = true
        // Check thumbnail cache first
        val cached = app.terminalssh.secure.sftp.ThumbnailCache.get(entry.path)
        if (cached != null) {
            imageBytes = java.io.ByteArrayOutputStream().also { cached.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, it) }.toByteArray()
            loading = false
        } else {
            val result = fetchFileBytes?.invoke(entry.path)
            result?.onSuccess { bytes ->
                imageBytes = bytes
                // Cache the thumbnail
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) app.terminalssh.secure.sftp.ThumbnailCache.put(entry.path, bmp)
            }
            loading = false
        }
    }

    ImagePreviewDialog(
        fileName = entry.name,
        isLoading = loading,
        imageBytes = imageBytes,
        onDismiss = onDismiss,
    )
}
