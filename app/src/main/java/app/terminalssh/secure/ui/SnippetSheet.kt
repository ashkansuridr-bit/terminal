package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.model.SnippetEntry
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetSheet(
    snippets: List<SnippetEntry>,
    onDismiss: () -> Unit,
    onSave: (String, CharArray) -> Unit,
    onInsert: (SnippetEntry) -> Unit,
    onDelete: (SnippetEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.snippets_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.snippets_private),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Turquoise)
            }

            if (adding) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(18.dp),
                        )
                        .border(1.dp, Stroke, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text(stringResource(R.string.snippets_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it.take(4096) },
                        label = { Text(stringResource(R.string.snippets_command)) },
                        minLines = 2,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            onClick = {
                                command = ""
                                name = ""
                                adding = false
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = command.isNotBlank(),
                            onClick = {
                                onSave(name, command.toCharArray())
                                command = ""
                                name = ""
                                adding = false
                            },
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.snippets_add))
                }
            }

            if (snippets.isEmpty() && !adding) {
                Text(
                    stringResource(R.string.snippets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            snippets.forEach { entry ->
                val deleteDescription = stringResource(R.string.snippet_delete, entry.name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp),
                        )
                        .border(1.dp, Stroke, RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.snippets_insert_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    TextButton(onClick = { onInsert(entry) }) {
                        Text(stringResource(R.string.snippets_insert))
                    }
                    IconButton(
                        onClick = { onDelete(entry) },
                        modifier = Modifier.semantics { contentDescription = deleteDescription },
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
