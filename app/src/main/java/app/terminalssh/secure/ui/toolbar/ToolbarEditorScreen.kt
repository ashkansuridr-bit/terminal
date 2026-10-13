package app.terminalssh.secure.ui.toolbar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.security.SecretIo
import app.terminalssh.secure.R
import app.terminalssh.secure.toolbar.KeyWidth
import app.terminalssh.secure.toolbar.KeyStyle
import app.terminalssh.secure.toolbar.MacroAction
import app.terminalssh.secure.toolbar.MacroDef
import app.terminalssh.secure.toolbar.MacroEngine
import app.terminalssh.secure.toolbar.MacroStep
import app.terminalssh.secure.toolbar.SpecialKeyAction
import app.terminalssh.secure.toolbar.ToolKeySpec
import app.terminalssh.secure.toolbar.ToolbarAction
import app.terminalssh.secure.toolbar.SnippetAction
import app.terminalssh.secure.toolbar.TextAction
import app.terminalssh.secure.toolbar.ToolbarEditSession
import app.terminalssh.secure.toolbar.ToolbarEditor
import app.terminalssh.secure.toolbar.ToolbarImportCandidate
import app.terminalssh.secure.toolbar.ToolbarGesture
import app.terminalssh.secure.toolbar.ToolbarGestures
import app.terminalssh.secure.toolbar.ToolbarKeyPalette
import app.terminalssh.secure.toolbar.ToolbarProfiles
import app.terminalssh.secure.toolbar.ToolbarProfile
import app.terminalssh.secure.toolbar.ToolbarRepository
import app.terminalssh.secure.ui.theme.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

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
    activeHostId: String? = null,
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
    var createMacroOpen by remember { mutableStateOf(false) }
    var gestureDialog by remember { mutableStateOf<ToolbarGesture?>(null) }
    var discardConfirmOpen by remember { mutableStateOf(false) }
    val pendingMacros = remember(profileId) { mutableListOf<MacroDef>() }
    var importCandidate by remember { mutableStateOf<ToolbarImportCandidate?>(null) }
    var transferBusy by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { destination ->
            transferBusy = true
            transferMessage = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bytes = repository.export().toByteArray(Charsets.UTF_8)
                        try {
                            context.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                                ?: error("Cannot open configuration destination")
                        } finally {
                            bytes.fill(0)
                        }
                    }
                    transferMessage = R.string.tcs_export_success
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    transferMessage = R.string.tcs_transfer_failed
                } finally {
                    transferBusy = false
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { source ->
            transferBusy = true
            transferMessage = null
            scope.launch {
                try {
                    importCandidate = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(source)?.use {
                            SecretIo.readBounded(it, ToolbarRepository.MAX_IMPORT_BYTES)
                        } ?: error("Cannot read configuration source")
                        try {
                            val text = Charsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString()
                            repository.prepareImport(text) ?: error("Invalid toolbar configuration")
                        } finally {
                            bytes.fill(0)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    transferMessage = R.string.tcs_transfer_failed
                } finally {
                    transferBusy = false
                }
            }
        }
    }

    fun requestDismiss() {
        if (session.isDirty || pendingMacros.isNotEmpty()) discardConfirmOpen = true else onDismiss()
    }

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

    BackHandler(onBack = ::requestDismiss)

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
                TextButton(onClick = ::requestDismiss) { Text(stringResource(R.string.close)) }
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
                            pendingMacros.forEach(repository::saveMacro)
                            pendingMacros.clear()
                            repository.saveProfile(session.current.copy(builtIn = false))
                        }
                        profileId = profile.id
                    }
                }
            }

            if (activeHostId != null) {
                val hostOverride = state.hostOverrides[activeHostId]
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (hostOverride == null) R.string.tcs_host_profile_global
                        else R.string.tcs_host_profile_override,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(
                        stringResource(R.string.tcs_host_profile_apply),
                        enabled = !session.isDirty && !transferBusy,
                    ) {
                        repository.setHostOverride(activeHostId, profileId)
                    }
                    if (hostOverride != null) {
                        SmallButton(stringResource(R.string.tcs_host_profile_clear)) {
                            repository.setHostOverride(activeHostId, null)
                        }
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
                SectionLabel(stringResource(R.string.tcs_gestures))
                ToolbarGesture.entries.forEach { gesture ->
                    val binding = selected.gestures[gesture]
                    val actionName = binding?.let { action ->
                        working.layout.keys.firstOrNull { it.action == action }?.label ?: actionLabel(action)
                    } ?: stringResource(R.string.tcs_no_gesture_action)
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            gestureLabel(gesture),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { gestureDialog = gesture },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(actionName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (binding != null) {
                                TextButton(onClick = {
                                    edit { ToolbarEditor.clearGesture(it, selected.id, gesture) }
                                }) {
                                    Text(stringResource(R.string.tcs_clear_gesture), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(stringResource(R.string.tcs_add_key)) { addKeyOpen = true }
                SmallButton(
                    stringResource(R.string.tcs_create_macro),
                    enabled = working.layout.keys.size < app.terminalssh.secure.toolbar.ToolbarLayout.MAX_KEYS,
                ) { createMacroOpen = true }
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
                    pendingMacros.forEach(repository::saveMacro)
                    pendingMacros.clear()
                    repository.saveProfile(saved)
                    repository.setActiveProfile(saved.id)
                    repository.setActiveTheme(saved.themeId)
                    onDismiss()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(stringResource(R.string.tcs_export), enabled = !transferBusy) {
                    exportLauncher.launch("terminal-control-studio.json")
                }
                SmallButton(
                    stringResource(R.string.tcs_import),
                    enabled = !transferBusy && !session.isDirty && pendingMacros.isEmpty(),
                ) {
                    importLauncher.launch(arrayOf("application/json", "text/*"))
                }
            }
            Text(
                stringResource(R.string.tcs_export_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.isDirty || pendingMacros.isNotEmpty()) Text(
                stringResource(R.string.tcs_import_unsaved_block),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            transferMessage?.let { message ->
                Text(
                    stringResource(message),
                    color = if (message == R.string.tcs_transfer_failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (transferBusy) Text(stringResource(R.string.tcs_transfer_busy))
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

    if (gestureDialog != null && selected != null) {
        val gesture = gestureDialog!!
        val choices = working.layout.keys
            .asSequence()
            .filter { it.id != selected.id && it.enabled }
            .filterNot { it.action is SpecialKeyAction && it.action.key == app.terminalssh.secure.toolbar.ToolbarSpecialKey.ENTER }
            .filterNot { it.action is SnippetAction }
            .filterNot { (it.action as? TextAction)?.text?.let { text -> '\n' in text || '\r' in text } == true }
            .filter { candidate ->
                val action = candidate.action
                if (action !is MacroAction) return@filter true
                val macro = repository.macroById(action.macroId) ?: return@filter false
                macro.steps.none { step ->
                    step is MacroStep.Special && step.key == app.terminalssh.secure.toolbar.ToolbarSpecialKey.ENTER ||
                        step is MacroStep.Text && ('\n' in step.text || '\r' in step.text)
                }
            }
            .filter { candidate -> ToolbarGestures.canBind(selected, gesture, candidate.action) }
            .distinctBy { it.action }
            .toList()
        GestureBindingDialog(
            gesture = gesture,
            choices = choices,
            hasCurrentBinding = gesture in selected.gestures,
            longPressBlockedByRepeat = gesture == ToolbarGesture.LONG_PRESS && selected.repeatWhileHeld,
            onDismiss = { gestureDialog = null },
            onBind = { action ->
                edit { ToolbarEditor.bindGesture(it, selected.id, gesture, action) }
                gestureDialog = null
            },
            onClear = {
                edit { ToolbarEditor.clearGesture(it, selected.id, gesture) }
                gestureDialog = null
            },
        )
    }

    if (createMacroOpen) {
        val macroDescription = stringResource(R.string.tcs_macro_insert_description)
        CreateMacroDialog(
            onDismiss = { createMacroOpen = false },
            onCreate = { name, text ->
                val idBase = "macro.user." + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifBlank { "custom" }
                val usedIds = (repository.allMacros + pendingMacros).map { it.id }.toSet()
                var id = idBase
                var suffix = 2
                while (id in usedIds) id = "$idBase-${suffix++}"
                val macro = MacroDef(
                    id = id,
                    name = name.trim().take(40),
                    description = macroDescription,
                    steps = listOf(MacroStep.Text(text)),
                )
                pendingMacros += macro
                edit { profile ->
                    val key = ToolKeySpec(
                        id = ToolbarEditor.uniqueId(profile.layout, "macro_${id.substringAfterLast('.')}") ,
                        label = macro.name.take(ToolKeySpec.MAX_LABEL),
                        action = MacroAction(macro.id),
                        contentDescription = macro.name,
                        width = KeyWidth.WIDE,
                        style = KeyStyle.ACCENT,
                    )
                    ToolbarEditor.appendKey(profile.copy(macroIds = (profile.macroIds + macro.id).distinct()), key)
                }
                createMacroOpen = false
            },
        )
    }

    if (discardConfirmOpen) {
        AlertDialog(
            onDismissRequest = { discardConfirmOpen = false },
            title = { Text(stringResource(R.string.tcs_discard_title)) },
            text = { Text(stringResource(R.string.tcs_discard_message)) },
            confirmButton = {
                TextButton(onClick = { discardConfirmOpen = false; onDismiss() }) {
                    Text(stringResource(R.string.tcs_discard_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmOpen = false }) {
                    Text(stringResource(R.string.tcs_keep_editing))
                }
            },
        )
    }

    importCandidate?.let { candidate ->
        val preview = candidate.preview
        AlertDialog(
            onDismissRequest = { importCandidate = null },
            title = { Text(stringResource(R.string.tcs_import_preview_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(
                            R.string.tcs_import_preview_summary,
                            preview.profiles,
                            preview.themes,
                            preview.macros,
                            preview.activeProfile,
                            preview.warnings.size,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tcs_import_replace_warning))
                    if (preview.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.tcs_import_warnings_present), color = MaterialTheme.colorScheme.error)
                    }
                    if (transferBusy) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.tcs_transfer_busy))
                    }
                    if (transferMessage == R.string.tcs_transfer_failed) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.tcs_transfer_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !transferBusy,
                    onClick = {
                        transferBusy = true
                        scope.launch {
                            try {
                                val applied = withContext(Dispatchers.IO) { repository.applyImport(candidate) }
                                if (applied) {
                                    importCandidate = null
                                    onDismiss()
                                } else {
                                    transferMessage = R.string.tcs_transfer_failed
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                transferMessage = R.string.tcs_transfer_failed
                            } finally {
                                transferBusy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.tcs_import_replace)) }
            },
            dismissButton = {
                TextButton(enabled = !transferBusy, onClick = { importCandidate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun gestureLabel(gesture: ToolbarGesture): String = when (gesture) {
    ToolbarGesture.DOUBLE_TAP -> stringResource(R.string.tcs_gesture_double_tap)
    ToolbarGesture.LONG_PRESS -> stringResource(R.string.tcs_gesture_long_press)
    ToolbarGesture.SWIPE_UP -> stringResource(R.string.tcs_gesture_swipe_up)
    ToolbarGesture.SWIPE_DOWN -> stringResource(R.string.tcs_gesture_swipe_down)
    ToolbarGesture.SWIPE_LEFT -> stringResource(R.string.tcs_gesture_swipe_left)
    ToolbarGesture.SWIPE_RIGHT -> stringResource(R.string.tcs_gesture_swipe_right)
}

@Composable
private fun GestureBindingDialog(
    gesture: ToolbarGesture,
    choices: List<ToolKeySpec>,
    hasCurrentBinding: Boolean,
    longPressBlockedByRepeat: Boolean,
    onDismiss: () -> Unit,
    onBind: (ToolbarAction) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(gestureLabel(gesture)) },
        text = {
            if (choices.isEmpty()) {
                Text(
                    stringResource(
                        if (longPressBlockedByRepeat) R.string.tcs_long_press_repeat_conflict
                        else R.string.tcs_no_compatible_gesture_action,
                    ),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    choices.forEach { key ->
                        TextButton(
                            onClick = { onBind(key.action) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${key.label} · ${actionLabel(key.action)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasCurrentBinding) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.tcs_clear_gesture)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun CreateMacroDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val credentialLike = MacroEngine.containsCredentialLikeText(text)
    val valid = name.isNotBlank() && text.isNotBlank() && text.length <= MacroEngine.MAX_TEXT_LENGTH && '\n' !in text && '\r' !in text && !credentialLike
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tcs_create_macro_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tcs_macro_insert_description), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.tcs_macro_name)) }, singleLine = true)
                OutlinedTextField(value = text, onValueChange = { text = it.take(MacroEngine.MAX_TEXT_LENGTH).filterNot { ch -> ch == '\n' || ch == '\r' } }, label = { Text(stringResource(R.string.tcs_macro_text)) }, singleLine = true)
                if (credentialLike) {
                    Text(stringResource(R.string.tcs_macro_secret_rejected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim(), text) }, enabled = valid) { Text(stringResource(R.string.tcs_create_macro)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
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
