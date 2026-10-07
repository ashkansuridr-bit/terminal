package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import app.terminalssh.secure.settings.SettingsPersistenceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import app.terminalssh.secure.R
import app.terminalssh.secure.settings.BoolSetting
import app.terminalssh.secure.settings.ChoiceSetting
import app.terminalssh.secure.settings.IntSetting
import app.terminalssh.secure.settings.SettingSpec
import app.terminalssh.secure.settings.SettingsStore
import app.terminalssh.secure.settings.TextSetting
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import kotlin.math.roundToInt

/**
 * Renders any setting from its spec.
 *
 * One renderer per type rather than per setting is what makes adding a setting a
 * one-line change, and it is also why every setting gets the same long-press-to-reset
 * and changed-marker behaviour for free.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingRow(
    spec: SettingSpec<*>,
    store: SettingsStore,
    optionLabel: (String) -> String,
) {
    val scope = rememberCoroutineScope()
    var saving by remember(spec.key) { mutableStateOf(false) }
    fun persist(onFailure: () -> Unit = {}, action: () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (failure: SettingsPersistenceException) {
                // Store restored the last state (or quarantined uncertain state).
                // The catalog displays its durable persistence error.
                onFailure()
            } finally {
                saving = false
            }
        }
    }
    val revision by store.revision.collectAsStateWithLifecycle()
    val persistenceFailure by store.persistenceFailure.collectAsStateWithLifecycle()
    val changed = remember(spec, revision, persistenceFailure) { store.isChanged(spec) }
    val title = stringResource(spec.titleRes)
    val resetLabel = stringResource(R.string.settings_reset_one)
    val booleanValue = remember(spec, revision, persistenceFailure) {
        (spec as? BoolSetting)?.let(store::get)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Long-press resets. Discoverability is the trade-off, so the changed marker
            // below doubles as the hint that there is something to go back from.
            .combinedClickable(
                enabled = !saving,
                onClick = {
                    if (spec is BoolSetting && booleanValue != null) {
                        persist { store.set(spec, !booleanValue) }
                    }
                },
                onLongClick = {
                    if (changed) {
                        persist { store.reset(spec) }
                    }
                },
                onLongClickLabel = resetLabel,
            )
            .then(
                if (spec is BoolSetting && booleanValue != null) Modifier.clearAndSetSemantics {
                    contentDescription = title
                    role = Role.Switch
                    toggleableState = ToggleableState(booleanValue)
                    onClick {
                        if (saving) false else {
                            persist { store.set(spec, !booleanValue) }
                            true
                        }
                    }
                } else Modifier,
            )
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (changed) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_changed_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = Turquoise,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Turquoise.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
                spec.summaryRes?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            if (spec is BoolSetting) {
                val value = store.get(spec)
                Switch(
                    checked = value,
                    onCheckedChange = null,
                    enabled = !saving,
                )
            }
        }

        when (spec) {
            is BoolSetting -> Unit // Rendered inline above.

            is IntSetting -> {
                val value = store.get(spec)
                var draft by remember(value) { mutableStateOf(value.toFloat()) }
                Text(
                    intValueLabel(spec, spec.coerce(draft.roundToInt())),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Slider(
                    value = draft,
                    onValueChange = { draft = it },
                    onValueChangeFinished = {
                        val proposed = draft.roundToInt()
                        persist(onFailure = { draft = store.get(spec).toFloat() }) {
                            store.set(spec, proposed)
                        }
                    },
                    enabled = !saving,
                    valueRange = spec.min.toFloat()..spec.max.toFloat(),
                    // Compose counts the gaps between stops, not the stops themselves.
                    // Hundreds of painted tick marks make large ranges (such as transfer
                    // rate) stall accessibility and rendering; coercion still snaps them.
                    steps = ((spec.max - spec.min) / spec.step - 1)
                        .takeIf { it in 1..100 } ?: 0,
                    modifier = Modifier.semantics { contentDescription = title },
                )
            }

            is ChoiceSetting -> {
                val value = store.get(spec)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    spec.values.forEach { option ->
                        // FilterChip's 32dp default is below the 48dp touch target. The
                        // accessibility label belongs to the whole chip, not to its label
                        // text, so the node a screen reader lands on is also the one it
                        // can actually tap.
                        FilterChip(
                            selected = option == value,
                            onClick = { persist { store.set(spec, option) } },
                            enabled = !saving,
                            label = {
                                Text(optionLabel(option), style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics(mergeDescendants = true) {
                                    role = Role.RadioButton
                                    contentDescription = optionLabel(option)
                                },
                        )
                    }
                }
            }

            is TextSetting -> {
                val value = store.get(spec)
                var draft by remember(value) { mutableStateOf(value) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = !saving,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = title },
                )
                if (draft != value) {
                    TextButton(onClick = {
                        val proposed = draft
                        persist { store.set(spec, proposed) }
                    }, enabled = !saving) {
                        Text(stringResource(R.string.settings_save_change))
                    }
                }
            }
        }
        if (saving) Text(stringResource(R.string.loading), color = TextSecondary)
    }
}

/** Zero often means "off" rather than the number zero; say so instead of showing "0". */
@Composable
private fun intValueLabel(spec: IntSetting, value: Int): String =
    if (value == 0 && spec.min == 0) stringResource(R.string.opt_none) else value.toString()
