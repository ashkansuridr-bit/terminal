package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.RemoteEntry
import app.terminalssh.secure.sftp.RemotePath
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * A minimal directory-only browser inside a dialog, for picking a "move to"/"copy to"
 * destination. Keeps its own navigation state entirely separate from the main
 * [app.terminalssh.secure.sftp.SftpController.browser] so opening it never disturbs what
 * the user was actually looking at underneath.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    startPath: String,
    listDirectories: suspend (String) -> List<RemoteEntry>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(startPath) }
    var directories by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }

    LaunchedEffect(path) {
        directories = listDirectories(path)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { path = RemotePath.parent(path) },
                        enabled = path != RemotePath.ROOT,
                    ) {
                        Icon(Icons.Outlined.ArrowUpward, null)
                    }
                    LazyRow(Modifier.weight(1f)) {
                        items(RemotePath.breadcrumbs(path), key = { it.second }) { (name, target) ->
                            Text(
                                text = if (name == "/") "/" else "$name  ›",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (target == path) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = target != path) { path = target }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(directories, key = { it.path }) { dir ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { path = dir.path }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, null, tint = Turquoise, modifier = Modifier.size(20.dp))
                            Box(Modifier.padding(start = 12.dp)) {
                                Text(ltr(dir.name), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(path) }) {
                Text(stringResource(R.string.sftp_move_here))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
