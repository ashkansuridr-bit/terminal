package app.terminalssh.secure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.security.KeyAlgorithm
import app.terminalssh.secure.R
import app.terminalssh.secure.ui.theme.Danger
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.vm.AppViewModel

@Composable
fun KeysScreen(viewModel: AppViewModel) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val generatedPublicKey by viewModel.generatedPublicKey.collectAsStateWithLifecycle()
    var generating by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importKey(it, name = it.lastPathSegment?.substringAfterLast('/') ?: "key") }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.tab_keys), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { generating = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.keys_generate)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.keys_import)) }
            Spacer(Modifier.height(12.dp))
        }

        if (keys.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.keys_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }

        items(keys, key = { it.id }) { entry ->
            val deleteDescription = stringResource(R.string.key_delete, entry.name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Stroke, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        entry.algorithm,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.keys_material_hash),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        entry.fingerprint.take(30) + "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                IconButton(
                    onClick = { viewModel.deleteKey(entry) },
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = Danger,
                    )
                }
            }
        }
    }

    if (generating) {
        GenerateKeyDialog(
            onDismiss = { generating = false },
            onGenerate = { algorithm, name ->
                generating = false
                viewModel.generateKey(algorithm, name)
            },
        )
    }

    generatedPublicKey?.let { publicKey ->
        PublicKeyDialog(
            publicKey = publicKey,
            onCopy = {
                viewModel.copyPublicKey(publicKey)
                viewModel.consumeGeneratedPublicKey()
            },
            onDismiss = viewModel::consumeGeneratedPublicKey,
        )
    }
}

/**
 * Algorithm choice plus a name. Only algorithms this device can actually generate are
 * listed, so the dialog can never offer a key that would fail on generate.
 */
@Composable
private fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: (KeyAlgorithm, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val available = remember { KeyAlgorithm.supported() }
    var algorithm by remember { mutableStateOf(KeyAlgorithm.default()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keys_generate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.snippets_name)) },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.keys_algorithm),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { option ->
                        FilterChip(
                            selected = option == algorithm,
                            onClick = { algorithm = option },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(algorithm, name) }) {
                Text(stringResource(R.string.keys_generate))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Shown once after generating. The private half is never rendered anywhere — only this
 * public line, which is what has to reach the server.
 */
@Composable
private fun PublicKeyDialog(publicKey: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keys_public_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.keys_public_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    publicKey,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 180.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.keys_public_copy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
