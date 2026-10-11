package app.terminalssh.secure.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.PreviewContent

/**
 * In-app text editor for small remote files (#36).
 * Downloads the file content, shows it in an editable field, and uploads on save.
 * Falls back to external editor for files > 512KB.
 */
@Composable
fun TextEditorDialog(
    fileName: String,
    isLoading: Boolean,
    content: String,
    errorMessage: String? = null,
    isReadOnly: Boolean = false,
    onContentChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_edit_file)) },
        text = {
            if (isLoading) {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Column {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = app.terminalssh.secure.ui.theme.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (errorMessage != null) {
                        Text(
                            errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    SelectionContainer {
                        OutlinedTextField(
                            value = content,
                            onValueChange = if (isReadOnly) {{}} else onContentChange,
                            readOnly = isReadOnly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .verticalScroll(rememberScrollState()),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = false,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isLoading && !isReadOnly) {
                TextButton(onClick = onSave) {
                    Text(stringResource(R.string.sftp_save_upload))
                }
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (!isLoading && isReadOnly && onDownload != null) {
                    TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/**
 * Image preview dialog for remote files (#38).
 * Shows the image from the downloaded staging file.
 */
@Composable
fun ImagePreviewDialog(
    fileName: String,
    isLoading: Boolean,
    imageBytes: ByteArray?,
    onDismiss: () -> Unit,
) {
    // Sampled decode: a modest file can still be a huge bitmap. See BoundedImage.
    val bitmap = remember(imageBytes) {
        imageBytes?.let { app.terminalssh.secure.sftp.BoundedImage.decodeSampled(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_preview_image)) },
        text = {
            Column {
                Text(
                    fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = app.terminalssh.secure.ui.theme.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxWidth().padding(32.dp))
                } else if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = fileName,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(stringResource(R.string.sftp_preview))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

/**
 * Read-only preview dialog for remote files (#39 / F026).
 *
 * Renders the bounded sample as text, or as a hex dump when the bytes are not text. A binary
 * decoded as text is a wall of replacement characters that hides whether the read itself was
 * sound, so the view is chosen from the bytes rather than assumed.
 */
@Composable
fun FilePreviewDialog(
    fileName: String,
    isLoading: Boolean,
    view: PreviewContent.View?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_preview_text)) },
        text = {
            Column {
                Text(
                    fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = app.terminalssh.secure.ui.theme.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxWidth().padding(32.dp))
                } else {
                    when (view) {
                        is PreviewContent.View.Hex -> {
                            Text(
                                stringResource(R.string.sftp_preview_binary),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    view.lines.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                        is PreviewContent.View.Text -> {
                            SelectionContainer {
                                Text(
                                    view.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                        null -> Text(stringResource(R.string.sftp_preview))
                    }
                    if (view?.truncated == true) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.sftp_preview_truncated),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}
