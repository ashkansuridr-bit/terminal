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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import app.terminalssh.secure.R
import app.terminalssh.secure.ui.theme.Danger
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.vm.AppViewModel

@Composable
fun KeysScreen(viewModel: AppViewModel) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importKey(it, name = it.lastPathSegment?.substringAfterLast('/') ?: "key") }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.tab_keys), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(stringResource(R.string.keys_import)) }
            Spacer(Modifier.height(10.dp))
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
}
