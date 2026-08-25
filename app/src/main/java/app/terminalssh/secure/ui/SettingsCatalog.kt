package app.terminalssh.secure.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.settings.SettingGroup
import app.terminalssh.secure.settings.SettingsRegistry
import app.terminalssh.secure.settings.SettingsStore
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * The whole settings list, generated from the schema.
 *
 * Search and advanced mode are written once here rather than per setting, which is the
 * point of the schema: the list stays short by default and stays findable when it is not.
 */
@Composable
@Suppress("LocalContextGetResourceValueCall")
fun SettingsCatalog(store: SettingsStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    // Search matches against localized text, which a plain composable stringResource call
    // cannot provide inside a lambda. Reading LocalConfiguration is what makes this recompose
    // when the locale changes — the reason the lint rule exists — so it is keyed on that.
    val configuration = LocalConfiguration.current
    val resolve: (Int) -> String = remember(configuration) { { id: Int -> context.getString(id) } }

    var query by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.settings_search), color = TextSecondary) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary) },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Turquoise.copy(alpha = 0.45f),
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = context.getString(R.string.settings_search) },
        )

        // Hidden while searching: a query already crosses the basic/advanced line, and
        // a toggle that changes what a search finds is a trap.
        AnimatedVisibility(query.isBlank(), enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_advanced_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = advanced,
                    onCheckedChange = { advanced = it },
                    modifier = Modifier.semantics {
                        contentDescription = context.getString(R.string.settings_advanced_mode)
                    },
                )
            }
        }

        if (query.isNotBlank()) {
            val hits = SettingsRegistry.search(query, resolve)
            if (hits.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                hits.forEach { spec ->
                    SettingRow(spec, store, optionLabel = { optionLabel(it, context) }, onChanged = onChanged)
                }
            }
            return@Column
        }

        SettingGroup.entries.forEach { group ->
            val specs = SettingsRegistry.byGroup(group).filter { advanced || !it.advanced }
            if (specs.isEmpty()) return@forEach

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(group.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Turquoise,
            )
            specs.forEach { spec ->
                SettingRow(spec, store, optionLabel = { optionLabel(it, context) }, onChanged = onChanged)
            }
        }
    }
}

private val SettingGroup.titleRes: Int
    get() = when (this) {
        SettingGroup.APPEARANCE -> R.string.settings_group_appearance
        SettingGroup.TERMINAL -> R.string.settings_group_terminal
        SettingGroup.BEHAVIOR -> R.string.settings_group_behavior
        SettingGroup.SECURITY -> R.string.settings_group_security
        SettingGroup.ADVANCED -> R.string.settings_group_advanced
    }

/**
 * Stored option identifiers are stable; their labels are looked up here so renaming a
 * label can never orphan a stored value.
 */
private fun optionLabel(value: String, context: android.content.Context): String {
    val res = when (value) {
        "persian_neon" -> R.string.settings_palette_persian_neon
        "oled" -> R.string.settings_palette_oled
        "midnight" -> R.string.settings_palette_midnight
        "solarized" -> R.string.settings_palette_solarized
        "classic" -> R.string.settings_palette_classic
        "amber" -> R.string.settings_palette_amber
        "dracula" -> R.string.settings_palette_dracula
        "nord" -> R.string.settings_palette_nord
        "gruvbox" -> R.string.settings_palette_gruvbox
        "catppuccin" -> R.string.settings_palette_catppuccin
        "tokyo_night" -> R.string.settings_palette_tokyo_night
        "block" -> R.string.opt_block
        "bar" -> R.string.opt_bar
        "underline" -> R.string.opt_underline
        "none" -> R.string.opt_none
        "vibrate" -> R.string.opt_vibrate
        "sound" -> R.string.opt_sound
        "monospace" -> R.string.opt_monospace
        "jetbrains_mono" -> R.string.opt_jetbrains_mono
        "fira_code" -> R.string.opt_fira_code
        "source_code_pro" -> R.string.opt_source_code_pro
        else -> return value
    }
    return context.getString(res)
}
