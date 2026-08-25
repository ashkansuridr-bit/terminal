package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.agents.AgentInstallScript
import app.terminalssh.secure.agents.CodingAgent
import app.terminalssh.secure.agents.PackageManager
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * Installs a coding agent on the connected server.
 *
 * The script is shown in full before anything runs. That is the entire point: the
 * alternative people actually use is pasting `curl … | bash` from a web page into a
 * root shell on their phone, having read none of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentInstallSheet(
    onDismiss: () -> Unit,
    onRunScript: (String) -> Unit,
    hasKey: (CodingAgent) -> Boolean = { false },
    onSaveKey: (CodingAgent, Boolean, CharArray) -> Unit = { _, _, _ -> },
    onInjectKey: (CodingAgent) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var agent by remember { mutableStateOf(CodingAgent.CLAUDE_CODE) }
    var packageManager by remember { mutableStateOf<PackageManager?>(PackageManager.APT) }
    var installTmux by remember { mutableStateOf(true) }
    var projectDir by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var hostScopedKey by remember { mutableStateOf(false) }

    val script = remember(agent, packageManager, installTmux) {
        AgentInstallScript.installScript(agent, packageManager, installTmux)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.agent_install_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.agent_install_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Label(stringResource(R.string.agent_which))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CodingAgent.entries.forEach { option ->
                        FilterChip(
                            selected = option == agent,
                            onClick = { agent = option },
                            label = { Text(option.displayName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Label(stringResource(R.string.agent_package_manager))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PackageManager.entries.forEach { option ->
                        FilterChip(
                            selected = option == packageManager,
                            onClick = { packageManager = option },
                            label = { Text(option.id, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    // Explicitly choosing "unknown" skips prerequisites rather than
                    // installing them with the wrong tool.
                    FilterChip(
                        selected = packageManager == null,
                        onClick = { packageManager = null },
                        label = {
                            Text(stringResource(R.string.agent_pm_skip), style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = installTmux, onCheckedChange = { installTmux = it })
                    Column {
                        Text(
                            stringResource(R.string.agent_install_tmux),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.agent_install_tmux_why),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }

                OutlinedTextField(
                    value = projectDir,
                    onValueChange = { projectDir = it },
                    label = { Text(stringResource(R.string.agent_project_dir)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Only shown for agents that authenticate with an environment variable;
                // OpenCode signs in its own way and a key field would be a dead end.
                agent.apiKeyVariable?.let { variable ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(stringResource(R.string.agent_key_title) + "  ·  " + variable)
                        if (hasKey(agent)) {
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  " + stringResource(R.string.agent_key_stored),
                                style = MaterialTheme.typography.labelSmall,
                                color = Turquoise,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.agent_key_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.agent_key_title)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hostScopedKey, onCheckedChange = { hostScopedKey = it })
                        Text(
                            stringResource(R.string.agent_key_scope_host),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onSaveKey(agent, hostScopedKey, apiKey.toCharArray())
                                apiKey = ""
                            },
                            enabled = apiKey.isNotBlank(),
                        ) { Text(stringResource(R.string.agent_key_save)) }
                        TextButton(
                            onClick = { onInjectKey(agent) },
                            enabled = hasKey(agent),
                        ) { Text(stringResource(R.string.agent_key_inject)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Label(stringResource(R.string.agent_review_script))
                Text(
                    script,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onRunScript(script) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(stringResource(R.string.agent_run_install)) }

                TextButton(
                    onClick = {
                        onRunScript(
                            AgentInstallScript.launchInTmuxCommand(
                                agent = agent,
                                sessionName = agent.id,
                                projectDir = projectDir,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.agent_launch), color = Turquoise) }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
