package app.terminalssh.secure.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.BuildConfig
import app.terminalssh.secure.R
import app.terminalssh.secure.settings.SettingsImportPreview
import app.terminalssh.secure.settings.SettingsRegistry
import app.terminalssh.secure.ui.theme.Cyan
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.Danger
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val accountIdentity by viewModel.accountIdentity.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val context = LocalContext.current
    val lockAvailability = remember { AppLock.availability(context) }
    val known = remember { viewModel.knownHosts() }
    val settingsStore = viewModel.settingsStore
    var confirmResetAll by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<SettingsImportPreview?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportSettings(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.previewSettingsImport(it) { preview -> pendingImport = preview } } }

    val window = rememberWindowSize()
    val maxContentWidth = window.width.contentMaxWidth()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(if (maxContentWidth != Dp.Unspecified) Modifier.widthIn(max = maxContentWidth) else Modifier)
            .padding(horizontal = window.width.pageMargin(), vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        // Market builds compile without any account integration; the card would only
        // offer a button that can never succeed.
        if (viewModel.accountSupported) {
            AccountSection(
                signedInName = accountIdentity?.displayName ?: accountIdentity?.accountId,
                onSignIn = { if (activity != null) viewModel.signInAccount(activity) },
                onSignOut = viewModel::signOutAccount,
            )
        }

        // Everything the schema knows about, with search, advanced mode, per-row reset
        // and changed markers — all generated rather than hand-written per setting.
        Section(stringResource(R.string.tab_settings)) {
            SettingsCatalog(
                store = settingsStore,
                isVisible = { spec ->
                    spec != SettingsRegistry.biometricLock || lockAvailability == LockAvailability.AVAILABLE
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { exportLauncher.launch("terminal-ssh-settings.json") }) {
                    Text(stringResource(R.string.settings_export), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                    Text(stringResource(R.string.settings_import), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { confirmResetAll = true }) {
                    Text(
                        stringResource(R.string.settings_reset_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = Danger,
                    )
                }
            }
        }

        Section(stringResource(R.string.settings_security)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = Turquoise)
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.settings_security_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            if (lockAvailability != LockAvailability.AVAILABLE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (lockAvailability == LockAvailability.NOT_ENROLLED) {
                            R.string.settings_lock_not_enrolled
                        } else {
                            R.string.settings_lock_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_known_hosts, known.size),
                style = MaterialTheme.typography.labelLarge,
            )
            known.take(8).forEach { entry ->
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(ltr("${entry.host}:${entry.port}"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        entry.algorithm,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Section(stringResource(R.string.settings_about)) {
            Text(
                stringResource(R.string.settings_version, ltr(BuildConfig.VERSION_NAME)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text(stringResource(R.string.settings_reset_all)) },
            text = { Text(stringResource(R.string.settings_reset_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    settingsStore.resetAll()
                    confirmResetAll = false
                }) {
                    Text(stringResource(R.string.settings_reset_all), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_import_preview_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(
                            R.string.settings_import_preview_summary,
                            preview.changes.size,
                            preview.invalidKeys.size,
                            preview.unknownKeys.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    preview.changes.forEach { change ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${stringResource(change.spec.titleRes)}: ${change.oldValue} → ${change.newValue}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (preview.invalidKeys.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                R.string.settings_import_invalid_keys,
                                preview.invalidKeys.joinToString(),
                            ),
                            color = Danger,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (preview.unknownKeys.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.settings_import_unknown_keys,
                                preview.unknownKeys.joinToString(),
                            ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applySettingsImport(preview)
                    pendingImport = null
                }) {
                    Text(stringResource(R.string.settings_import_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountSection(
    signedInName: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Turquoise.copy(alpha = 0.16f),
                        Cyan.copy(alpha = 0.07f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .border(1.dp, Turquoise.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Turquoise.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (signedInName == null) Icons.Outlined.AccountCircle else Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = Turquoise,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.google_account_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    signedInName ?: stringResource(R.string.google_account_optional),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(visible = signedInName == null) {
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Turquoise,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.google_sign_in))
            }
        }
        AnimatedVisibility(visible = signedInName != null) {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.google_sign_out))
            }
        }
    }

}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Stroke, RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Turquoise)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
