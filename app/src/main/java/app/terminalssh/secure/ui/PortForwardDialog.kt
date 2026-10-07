package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.SshSession
import androidx.compose.material3.MaterialTheme

/**
 * Dialog to add a port forward (local or remote).
 * Pre-filled with sensible defaults (localhost:22 → remote).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardDialog(
    existingForwards: List<SshSession.PortForward>,
    onAdd: (SshSession.PortForward) -> Unit,
    onRemove: (SshSession.PortForward) -> Unit,
    onDismiss: () -> Unit,
) {
    var isLocal by remember { mutableStateOf(true) }
    var bindPort by remember { mutableStateOf("8080") }
    var host by remember { mutableStateOf("127.0.0.1") }
    var destPort by remember { mutableStateOf("22") }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.port_forward_add)) },
        text = {
            Column {
                // Forward type selector
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = if (isLocal) stringResource(R.string.port_forward_local)
                                else stringResource(R.string.port_forward_remote),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.port_forward_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.port_forward_local)) },
                            onClick = { isLocal = true; typeExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.port_forward_remote)) },
                            onClick = { isLocal = false; typeExpanded = false },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = bindPort,
                    onValueChange = { bindPort = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.port_forward_bind_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.port_forward_host)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = destPort,
                    onValueChange = { destPort = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.port_forward_dest_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Existing forwards — each shows its real runtime state, because a forward that
                // failed to bind or died with the last reconnect must never look active.
                if (existingForwards.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.port_forward_listing),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    existingForwards.forEach { fwd ->
                        val stateLabel = when (fwd.state) {
                            SshSession.PortForwardState.ACTIVE -> stringResource(R.string.port_forward_active)
                            SshSession.PortForwardState.PENDING -> stringResource(R.string.port_forward_state_pending)
                            SshSession.PortForwardState.FAILED -> stringResource(R.string.port_forward_state_failed)
                        }
                        val stateColor = when (fwd.state) {
                            SshSession.PortForwardState.ACTIVE -> MaterialTheme.colorScheme.primary
                            SshSession.PortForwardState.PENDING -> MaterialTheme.colorScheme.outline
                            SshSession.PortForwardState.FAILED -> MaterialTheme.colorScheme.error
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${if (fwd.isLocal) "L" else "R"} ${fwd.bindPort} → ${fwd.host}:${fwd.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stateColor,
                                )
                                TextButton(onClick = { onRemove(fwd) }) {
                                    Text("✕", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (fwd.state == SshSession.PortForwardState.FAILED && fwd.error != null) {
                                Text(
                                    fwd.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bind = bindPort.toIntOrNull() ?: return@TextButton
                    val dest = destPort.toIntOrNull() ?: return@TextButton
                    if (host.isBlank()) return@TextButton
                    onAdd(SshSession.PortForward(bind, host, dest, isLocal))
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
