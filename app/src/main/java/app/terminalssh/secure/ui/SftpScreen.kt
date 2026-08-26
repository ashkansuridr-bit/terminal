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
) {
    var newFolderPrompt by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var detailsTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var moveTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var copyTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleteSelectedPrompt by remember { mutableStateOf(false) }
    var chmodTarget by remember { mutableStateOf<RemoteEntry?>(null) }

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
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.entries, key = { it.path }) { entry ->
                    EntryRow(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = entry.path in selected,
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
        IconButton(onClick = onUpload) {
            Icon(Icons.Outlined.Upload, stringResource(R.string.sftp_upload), tint = Turquoise)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, stringResource(R.string.sftp_refresh), tint = TextSecondary)
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
                Text(
                    ltr(entry.name),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                Text(
                    if (entry.isDirectory) entry.permissions else "${FileSize.format(entry.sizeBytes)} · ${entry.permissions}",
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_properties)) },
                onClick = { menuOpen = false; onDetailsRequest() },
            )
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
