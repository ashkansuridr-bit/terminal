package app.terminalssh.secure.ui.toolbar

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.toolbar.KeyWidth
import app.terminalssh.secure.toolbar.ToolKeySpec
import app.terminalssh.secure.toolbar.ToolbarEditSession
import app.terminalssh.secure.toolbar.ToolbarEditor
import app.terminalssh.secure.toolbar.ToolbarGesture
import app.terminalssh.secure.toolbar.ToolbarKeyPalette
import app.terminalssh.secure.toolbar.ToolbarProfiles
import app.terminalssh.secure.toolbar.ToolbarProfile
import app.terminalssh.secure.toolbar.ToolbarRepository
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * The customization screen.
 *
 * Edits run against a [ToolbarEditSession] copy of the selected profile and reach the live bar
 * only on Save, so rearranging keys can never half-apply. The screen is deliberately built
 * from the same model the bar renders, so what is previewed is what will be drawn.
 */
@Composable
fun ToolbarEditorScreen(
    repository: ToolbarRepository,
    onDismiss: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    var profileId by rememberSaveable { mutableStateOf(state.activeProfileId) }
    val baseProfile = remember(profileId) {
        repository.profileById(profileId) ?: ToolbarProfiles.default
    }
    val session = remember(profileId) { ToolbarEditSession(baseProfile) }
    var working by remember(profileId) { mutableStateOf(baseProfile) }
    var selectedKeyId by remember(profileId) {
        mutableStateOf(baseProfile.layout.keys.firstOrNull()?.id)
    }
    var addKeyOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    fun normaliseSelection(profile: ToolbarProfile) {
        selectedKeyId = selectedKeyId
            ?.takeIf { profile.layout.keyById(it) != null }
            ?: profile.layout.keys.firstOrNull()?.id
    }

    fun edit(transform: (ToolbarProfile) -> ToolbarProfile) {
        session.apply(transform)
        working = session.current
        normaliseSelection(working)
    }

    fun undo() {
        session.undo()
        working = session.current
        normaliseSelection(working)
    }

    fun redo() {
        session.redo()
        working = session.current
        normaliseSelection(working)
    }

    BackHandler(onBack = onDismiss)

    val selected = selectedKeyId?.let { working.layout.keyById(it) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tcs_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.tcs_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }

            repository.recoveryNotice?.let { notice ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.tcs_recover_notice) + " ($notice)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionLabel(stringResource(R.string.tcs_profile))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repository.allProfiles, key = { it.id }) { profile ->
                    Chip(
                        text = profile.name,
                        selected = profile.id == profileId,
                        badge = if (profile.id in state.customProfiles.map { it.id }) {
                            stringResource(R.string.tcs_customized_badge)
                        } else {
                            null
                        },
                    ) {
                        // Keep any in-flight edit to the profile currently being edited before
                        // switching, so a user who moves a key then taps another profile does
                        // not lose the change silently.
                        if (session.isDirty) {
                            repository.saveProfile(session.current.copy(builtIn = false))
                        }
                        profileId = profile.id
                    }
                }
            }

            SectionLabel(stringResource(R.string.tcs_theme))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repository.allThemes, key = { it.id }) { theme ->
                    Chip(text = theme.name, selected = theme.id == working.themeId) {
                        edit { it.copy(themeId = theme.id) }
                    }
                }
            }

            SectionLabel(stringResource(R.string.tcs_layout))
            working.layout.rows.forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${rowIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(16.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(row, key = { it.id }) { key ->
                            KeyChip(
                                key = key,
                                selected = key.id == selectedKeyId,
                                onClick = { selectedKeyId = key.id },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (selected == null) {
                Text(
                    stringResource(R.string.tcs_select_key_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    selected.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallButton(stringResource(R.string.tcs_move_earlier)) {
                        edit { ToolbarEditor.moveKeyBy(it, selected.id, -1) }
                    }
                    SmallButton(stringResource(R.string.tcs_move_later)) {
                        edit { ToolbarEditor.moveKeyBy(it, selected.id, +1) }
                    }
                    SmallButton(stringResource(R.string.tcs_move_other_row)) {
                        edit { ToolbarEditor.moveKeyToOtherRow(it, selected.id) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallButton(stringResource(R.string.tcs_narrow)) {
                        edit { ToolbarEditor.setWidth(it, selected.id, selected.width.narrower()) }
                    }
                    SmallButton(stringResource(R.string.tcs_wide)) {
                        edit { ToolbarEditor.setWidth(it, selected.id, selected.width.wider()) }
                    }
                    SmallButton(stringResource(R.string.tcs_duplicate)) {
                        edit { ToolbarEditor.duplicateKey(it, selected.id) }
                    }
                    SmallButton(stringResource(R.string.tcs_rename)) { renameOpen = true }
                    SmallButton(stringResource(R.string.tcs_remove_key)) {
                        edit { ToolbarEditor.removeKey(it, selected.id) }
                    }
                }
                if (selected.gestures.isNotEmpty()) {
                    Text(
                        selected.gestures.entries.joinToString(", ") { (g, a) -> "${g.name.lowercase()} → ${actionLabel(a)}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(stringResource(R.string.tcs_add_key)) { addKeyOpen = true }
                SmallButton(stringResource(R.string.tcs_undo), enabled = session.canUndo) { undo() }
                SmallButton(stringResource(R.string.tcs_redo), enabled = session.canRedo) { redo() }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(stringResource(R.string.tcs_reset)) {
                    edit { ToolbarEditor.reset(it, baseProfile) }
                }
                SmallButton(stringResource(R.string.tcs_save), accent = true) {
                    val saved = working.copy(builtIn = false)
                    repository.saveProfile(saved)
                    repository.setActiveProfile(saved.id)
                    repository.setActiveTheme(saved.themeId)
                    onDismiss()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addKeyOpen) {
        AddKeyDialog(
            onDismiss = { addKeyOpen = false },
            onPick = { entry ->
                var newId: String? = null
                edit { profile ->
                    val key = ToolbarKeyPalette.materialise(entry, profile.layout)
                    newId = key.id
                    ToolbarEditor.appendKey(profile, key)
                }
                newId?.let { selectedKeyId = it }
                addKeyOpen = false
            },
        )
    }

    if (renameOpen && selected != null) {
        RenameDialog(
            initial = selected.label,
            onDismiss = { renameOpen = false },
            onConfirm = { label ->
                edit { ToolbarEditor.renameKey(it, selected.id, label) }
                renameOpen = false
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val background = if (selected) Turquoise.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) Turquoise else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Turquoise, maxLines = 1)
            }
        }
    }
}

@Composable
private fun KeyChip(key: ToolKeySpec, selected: Boolean, onClick: () -> Unit) {
    val border = when {
        selected -> Turquoise
        !key.enabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        key.gestures.isNotEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.5.dp, color = border, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            key.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        accent -> Turquoise
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (accent && enabled) Color(0xFF04231A) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddKeyDialog(onDismiss: () -> Unit, onPick: (ToolbarKeyPalette.Entry) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.tcs_add_key_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                ToolbarKeyPalette.Group.entries.forEach { group ->
                    Text(
                        group.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    // Every entry in a group is reachable; the row scrolls rather than the
                    // dialog truncating a group that does not fit.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToolbarKeyPalette.entries.filter { it.group == group }, key = { it.idHint }) { entry ->
                            SmallButton(entry.label) { onPick(entry) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.tcs_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.tcs_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ToolKeySpec.MAX_LABEL) },
                singleLine = true,
                label = { Text(stringResource(R.string.tcs_rename)) },
            )
        },
    )
}

private fun KeyWidth.wider(): KeyWidth = when (this) {
    KeyWidth.NARROW -> KeyWidth.NORMAL
    KeyWidth.NORMAL -> KeyWidth.WIDE
    KeyWidth.WIDE -> KeyWidth.WIDE
}

private fun KeyWidth.narrower(): KeyWidth = when (this) {
    KeyWidth.WIDE -> KeyWidth.NORMAL
    KeyWidth.NORMAL -> KeyWidth.NARROW
    KeyWidth.NARROW -> KeyWidth.NARROW
}

private fun actionLabel(action: app.terminalssh.secure.toolbar.ToolbarAction): String = when (action) {
    is app.terminalssh.secure.toolbar.SpecialKeyAction -> action.key.name.lowercase()
    is app.terminalssh.secure.toolbar.ControlAction -> "^${action.letter.uppercaseChar()}"
    is app.terminalssh.secure.toolbar.TextAction -> "\"${action.text}\""
    is app.terminalssh.secure.toolbar.ModifierAction -> action.modifier.name.lowercase()
    is app.terminalssh.secure.toolbar.MacroAction -> "macro"
    is app.terminalssh.secure.toolbar.SnippetAction -> "snippet"
    else -> action::class.simpleName ?: "action"
}
