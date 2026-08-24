package app.terminalssh.secure.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    Column(Modifier.fillMaxSize()) {
        PathBar(state.path, onNavigate = onNavigate, onUp = onUp, onRefresh = onRefresh, onUpload = onUpload)

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
                        .padding(horizontal = 20.dp, vertical = 10.dp),
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
                        onClick = {
                            if (entry.isDirectory) onNavigate(entry.path) else onDownload(entry)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Breadcrumbs that scroll horizontally. A deep path on a narrow phone would otherwise
 * either truncate the part the user needs or wrap into several lines.
 */
@Composable
private fun PathBar(
    path: String,
    onNavigate: (String) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
) {
    val crumbs = app.terminalssh.secure.sftp.RemotePath.breadcrumbs(path)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isCurrent) { onNavigate(target) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
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
}

@Composable
private fun EntryRow(entry: RemoteEntry, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, Motion.press(), label = "row-press")
    val background by animateColorAsState(
        if (pressed) Turquoise.copy(alpha = 0.08f) else Color.Transparent,
        Motion.quick(),
        label = "row-bg",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(
                onClick = {
                    pressed = false
                    onClick()
                },
            )
            // A 56dp row clears the 48dp minimum touch target with room for a mis-tap.
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                entry.isSymlink -> Icons.Outlined.Link
                entry.isDirectory -> Icons.Outlined.Folder
                else -> Icons.Outlined.Description
            },
            contentDescription = null,
            tint = if (entry.isDirectory) Turquoise else TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
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
}

@Composable
private fun EmptyDirectory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Folder,
                null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.sftp_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}
