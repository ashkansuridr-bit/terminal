package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.Environment
import app.terminalssh.secure.model.HostKeyPolicy
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ui.theme.Danger
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditSheet(
    initial: HostProfile?,
    availableHosts: List<HostProfile> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (HostProfile, CharArray?) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    // ProxyJump uses a separate SSH session whose credential cannot share the target
    // session's password slot. Until independent jump-password prompting exists, only
    // key-backed hosts are valid jump candidates.
    val jumpHostCandidates = availableHosts.filter {
        it.id != initial?.id && it.auth is AuthMethod.PrivateKey
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(initial?.group ?: "") }
    var tags by remember { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var environment by remember { mutableStateOf(initial?.environment ?: Environment.NONE) }
    var reconnectAttempts by remember {
        mutableStateOf((initial?.maxReconnectAttempts ?: HostProfile.DEFAULT_RECONNECT_ATTEMPTS).toString())
    }
    var jumpHostId by remember {
        mutableStateOf(initial?.jumpHostId?.takeIf { id -> jumpHostCandidates.any { it.id == id } } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val requiredFieldsError = stringResource(R.string.err_host_required)
    val portRangeError = stringResource(R.string.err_port_range)
    val reconnectRangeError = stringResource(R.string.err_reconnect_range)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // The fields scroll; the save/delete actions stay pinned to the bottom of the
        // sheet. With everything in one scrolling column, adding fields pushed Save below
        // the fold on a short screen — reachable only by scrolling past a keyboard.
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (initial == null) stringResource(R.string.hosts_add) else initial.displayName,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))

            Field(label, { label = it }, stringResource(R.string.field_label))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(host, { host = it }, stringResource(R.string.field_host), Modifier.weight(1f))
                Field(
                    port, { port = it.filter(Char::isDigit).take(5) },
                    stringResource(R.string.field_port), Modifier.weight(0.4f),
                    keyboard = KeyboardType.Number,
                )
            }
            Field(username, { username = it }, stringResource(R.string.field_username))
            Field(
                password, { password = it }, stringResource(R.string.field_password),
                keyboard = KeyboardType.Password, isPassword = true,
            )
            Field(group, { group = it }, stringResource(R.string.field_group))
            Field(tags, { tags = it }, stringResource(R.string.field_tags))
            Field(notes, { notes = it }, stringResource(R.string.field_notes), singleLine = false)
            Field(
                reconnectAttempts,
                { reconnectAttempts = it.filter(Char::isDigit).take(2) },
                stringResource(R.string.field_reconnect_attempts),
                keyboard = KeyboardType.Number,
            )
            EnvironmentPicker(environment) { environment = it }

            // Jump host picker: select another saved host to tunnel through.
            val otherHosts = jumpHostCandidates
            if (otherHosts.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val selectedJump = otherHosts.firstOrNull { it.id == jumpHostId }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.field_jump_host),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = jumpHostId.isEmpty(),
                            onClick = { jumpHostId = "" },
                            label = { Text(stringResource(R.string.jump_host_none), style = MaterialTheme.typography.labelSmall) },
                        )
                        FilterChip(
                            selected = jumpHostId.isNotEmpty(),
                            onClick = { expanded = true },
                            label = {
                                Text(
                                    selectedJump?.displayName ?: stringResource(R.string.field_jump_host),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        if (expanded) {
                            androidx.compose.material3.DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                otherHosts.forEach { h ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("${h.displayName} (${h.host})") },
                                        onClick = { jumpHostId = h.id; expanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let { Text(it, color = Danger, style = MaterialTheme.typography.labelSmall) }
            Button(
                onClick = {
                    val portNumber = port.toIntOrNull() ?: 22
                    when {
                        host.isBlank() || username.isBlank() -> error = requiredFieldsError
                        portNumber !in 1..65535 -> error = portRangeError
                        (reconnectAttempts.toIntOrNull() ?: 0) > HostProfile.MAX_RECONNECT_ATTEMPTS ->
                            error = reconnectRangeError
                        else -> {
                            val profile = HostProfile(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                label = label.trim(),
                                host = host.trim(),
                                port = portNumber,
                                username = username.trim(),
                                auth = initial?.auth ?: AuthMethod.Password(""),
                                hostKeyPolicy = initial?.hostKeyPolicy ?: HostKeyPolicy.TRUST_ON_FIRST_USE,
                                group = group.trim(),
                                tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
                                favorite = initial?.favorite ?: false,
                                lastConnectedAt = initial?.lastConnectedAt ?: 0L,
                                notes = notes.trim(),
                                environment = environment,
                                maxReconnectAttempts = reconnectAttempts.toIntOrNull()
                                    ?.coerceIn(0, HostProfile.MAX_RECONNECT_ATTEMPTS)
                                    ?: HostProfile.DEFAULT_RECONNECT_ATTEMPTS,
                                jumpHostId = jumpHostId,
                            )
                            onSave(profile, password.takeIf { it.isNotEmpty() }?.toCharArray())
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.save)) }

            if (initial != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.delete), color = Danger)
                }
            }
        }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(ltr(initial.displayName)) },
            text = { Text(stringResource(R.string.delete) + "؟") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(initial) }) {
                    Text(stringResource(R.string.delete), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Environment banding. Rendered as filter chips rather than a dropdown so the current
 * value is visible without a tap — the whole point is noticing "production" before you
 * connect, not after.
 */
@Composable
private fun EnvironmentPicker(selected: Environment, onSelect: (Environment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.field_environment),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Environment.entries.forEach { env ->
                FilterChip(
                    selected = env == selected,
                    onClick = { onSelect(env) },
                    label = { Text(stringResource(env.labelRes), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordPrompt(
    profile: HostProfile,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ltr(profile.displayName)) },
        text = {
            Column {
                Text(ltr(profile.subtitle), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.field_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onSubmit(password.toCharArray()) },
            ) { Text(stringResource(R.string.connect)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
