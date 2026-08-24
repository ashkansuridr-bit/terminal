package app.terminalssh.secure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.model.FuzzyMatch
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ui.theme.Cyan
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel

@Composable
fun HostsScreen(
    viewModel: AppViewModel,
    onConnect: (HostProfile, CharArray?) -> Unit,
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.sessions.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HostProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var askPasswordFor by remember { mutableStateOf<HostProfile?>(null) }
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importHostsFromSshConfig)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(viewModel::exportHostsToSshConfig) }

    // With no query the store's own order (favourites, then most recent) is what the user
    // expects; once they type, best match wins and that ordering is what helps.
    val filtered = if (query.isBlank()) {
        hosts
    } else {
        hosts.map { it to it.searchScore(query) }
            .filter { (_, score) -> score > FuzzyMatch.NO_MATCH }
            .sortedByDescending { (_, score) -> score }
            .map { (profile, _) -> profile }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_hosts),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                HeroCard(
                    hostCount = hosts.size,
                    sessionCount = sessions.size,
                    onAdd = { creating = true },
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.hosts_search), color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Turquoise.copy(alpha = 0.45f),
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                // Anyone who already uses SSH from a desktop has this file; retyping a
                // dozen servers on a phone keyboard is where people give up.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { configPicker.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.hosts_import_config))
                    }
                    if (hosts.isNotEmpty()) {
                        TextButton(onClick = { exportPicker.launch("ssh_config") }) {
                            Text(stringResource(R.string.hosts_export_config))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (filtered.isEmpty()) {
                item { EmptyState() }
            }

            items(filtered, key = { it.id }) { profile ->
                HostCard(
                    profile = profile,
                    onOpen = {
                        if (viewModel.hasStoredSecret(profile)) onConnect(profile, null)
                        else askPasswordFor = profile
                    },
                    onEdit = { editing = profile },
                    onToggleFavorite = { viewModel.toggleFavorite(profile) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            containerColor = Turquoise,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.hosts_add), fontWeight = FontWeight.SemiBold) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (creating || editing != null) {
        HostEditSheet(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { profile, password ->
                if (viewModel.saveHost(profile, password)) {
                    creating = false
                    editing = null
                }
            },
            onDelete = { profile ->
                viewModel.deleteHost(profile)
                creating = false
                editing = null
            },
        )
    }

    askPasswordFor?.let { profile ->
        PasswordPrompt(
            profile = profile,
            onDismiss = { askPasswordFor = null },
            onSubmit = { password ->
                askPasswordFor = null
                onConnect(profile, password)
            },
        )
    }
}

@Composable
private fun HeroCard(
    hostCount: Int,
    sessionCount: Int,
    onAdd: () -> Unit,
) {
    val newConnectionDescription = stringResource(R.string.home_new_connection)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Turquoise.copy(alpha = 0.20f),
                        Cyan.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .border(1.dp, Turquoise.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Turquoise.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Terminal, null, tint = Turquoise)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_secure_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.home_secure_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip(Icons.Outlined.Dns, stringResource(R.string.home_hosts_count, hostCount))
            MetricChip(Icons.Outlined.Terminal, stringResource(R.string.home_sessions_count, sessionCount))
            MetricChip(Icons.Outlined.Security, stringResource(R.string.home_vault_badge))
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Turquoise)
                .clickable(onClick = onAdd)
                .heightIn(min = 48.dp)
                .clearAndSetSemantics {
                    contentDescription = newConnectionDescription
                    role = Role.Button
                    onClick {
                        onAdd()
                        true
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.home_new_connection),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = Turquoise)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun HostCard(
    profile: HostProfile,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Stroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        // The environment band sits on the leading edge so "production" is visible
        // while the thumb is still travelling toward the row.
        profile.environment.color?.let { bandColor ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bandColor),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Turquoise.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.displayName.take(1).uppercase(),
                color = Turquoise,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(ltr(profile.displayName), style = MaterialTheme.typography.titleMedium)
            Text(ltr(profile.subtitle), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            profile.environment.color?.let { bandColor ->
                Text(
                    stringResource(profile.environment.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = bandColor,
                )
            }
            if (profile.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.tags.take(3).forEach { TagChip(it) }
                }
            }
        }
        val favoriteDescription = stringResource(
            if (profile.favorite) R.string.host_unfavorite else R.string.host_favorite,
            profile.displayName,
        )
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.semantics { contentDescription = favoriteDescription },
        ) {
            Icon(
                imageVector = if (profile.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (profile.favorite) Turquoise else TextSecondary,
            )
        }
        val editDescription = stringResource(R.string.host_edit, profile.displayName)
        IconButton(
            onClick = onEdit,
            modifier = Modifier.semantics { contentDescription = editDescription },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = TextSecondary,
            )
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 58.dp),
    ) {
        Text(stringResource(R.string.hosts_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.hosts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
