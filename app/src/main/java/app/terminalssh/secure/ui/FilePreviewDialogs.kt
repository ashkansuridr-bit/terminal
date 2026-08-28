package app.terminalssh.secure.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R

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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
    val bitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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
 * Text preview dialog for remote files (#39).
 * Shows the first N KB of a file for quick inspection without downloading.
 */
@Composable
fun TextPreviewDialog(
    fileName: String,
    isLoading: Boolean,
    content: String,
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
                    SelectionContainer {
                        Text(
                            content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .verticalScroll(rememberScrollState()),
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
