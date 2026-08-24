package app.terminalssh.secure.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.BuildConfig
import app.terminalssh.secure.R
import app.terminalssh.secure.ui.theme.Cyan
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TerminalPalettes
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings = viewModel.settings
    val accountIdentity by viewModel.accountIdentity.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var theme by remember { mutableStateOf(settings.themeName) }
    var fontSize by remember { mutableIntStateOf(settings.fontSizeSp) }
    var pasteConfirm by remember { mutableStateOf(settings.confirmMultilinePaste) }
    var keepAlive by remember { mutableStateOf(settings.keepAlive) }
    val known = remember { viewModel.knownHosts() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

        Section(stringResource(R.string.settings_appearance)) {
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(10.dp))
            // Six 48 dp targets plus 6 dp gaps fit the 320 dp content width of a common
            // 360 dp handset, retaining accessible targets without horizontal clipping.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TerminalPalettes.forEach { palette ->
                    val paletteName = stringResource(
                        when (palette.id) {
                            "oled" -> R.string.settings_palette_oled
                            "midnight" -> R.string.settings_palette_midnight
                            "solarized" -> R.string.settings_palette_solarized
                            "classic" -> R.string.settings_palette_classic
                            "amber" -> R.string.settings_palette_amber
                            else -> R.string.settings_palette_persian_neon
                        },
                    )
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.background)
                            .border(
                                width = if (theme == palette.id) 2.dp else 1.dp,
                                color = if (theme == palette.id) Turquoise else Stroke,
                                shape = CircleShape,
                            )
                            .semantics { contentDescription = paletteName }
                            .selectable(
                                selected = theme == palette.id,
                                role = Role.RadioButton,
                            ) {
                                theme = palette.id
                                settings.themeName = palette.id
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$",
                            color = palette.accent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // Formatted through resources so the digit shows in the locale's numerals;
            // string concatenation would leave a Latin "14" beside Persian counts elsewhere.
            Text(
                stringResource(R.string.settings_fontsize_value, fontSize),
                style = MaterialTheme.typography.labelLarge,
            )
            val fontSizeLabel = stringResource(R.string.settings_fontsize)
            // steps would draw a tick under every one of the 15 sizes, which reads as noise
            // at this width; the value is already stated above the track.
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { fontSize = it.toInt() },
                onValueChangeFinished = { settings.fontSizeSp = fontSize },
                valueRange = 10f..24f,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = fontSizeLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = fontSize.toFloat(),
                        range = 10f..24f,
                        steps = 13,
                    )
                    setProgress { requestedValue ->
                        val adjustedValue = requestedValue.roundToInt().coerceIn(10, 24)
                        fontSize = adjustedValue
                        settings.fontSizeSp = adjustedValue
                        true
                    }
                },
            )
        }

        Section(stringResource(R.string.settings_security)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = Turquoise)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.settings_security_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            ToggleRow(stringResource(R.string.settings_paste_confirm), pasteConfirm) {
                pasteConfirm = it
                settings.confirmMultilinePaste = it
            }
            ToggleRow(stringResource(R.string.settings_keepalive), keepAlive) {
                keepAlive = it
                settings.keepAlive = it
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Turquoise.copy(alpha = 0.16f),
                        Cyan.copy(alpha = 0.07f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .border(1.dp, Turquoise.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
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

        Spacer(Modifier.height(14.dp))
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
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Stroke, RoundedCornerShape(20.dp))
            .padding(17.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Turquoise)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                onClick {
                    onChange(!checked)
                    true
                }
            },
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}
