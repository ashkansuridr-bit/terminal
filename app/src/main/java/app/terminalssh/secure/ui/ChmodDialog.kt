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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.PosixPermissions

/**
 * A chmod-style permission editor. Shows a 3×3 grid of r/w/x checkboxes for
 * owner/group/other, with an octal text field that stays in sync. Changing
 * either side updates the other. This is the UI for SFTP suggestion #42.
 */
@Composable
fun ChmodDialog(
    currentPermissions: String,
    fileName: String,
    onConfirm: (newMode: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMode = PosixPermissions.parse(currentPermissions) ?: 0b110_100_100
    var mode by remember { mutableIntStateOf(initialMode) }
    var octalText by remember { mutableStateOf(PosixPermissions.toOctalString(initialMode)) }
    var octalError by remember { mutableStateOf(false) }

    fun syncFromOctal() {
        val parsed = PosixPermissions.parseOctal(octalText)
        if (parsed != null) {
            mode = parsed
            octalError = false
        } else {
            octalError = true
        }
    }

    fun syncFromCheckboxes() {
        octalText = PosixPermissions.toOctalString(mode)
        octalError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_chmod_title, fileName), maxLines = 1) },
        text = {
            Column {
                // Octal field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Mode:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = octalText,
                        onValueChange = { octalText = it; syncFromOctal() },
                        isError = octalError,
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        PosixPermissions.format(mode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Column headers
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("")
                    Text("Read", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text("Write", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text("Exec", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(4.dp))

                // Owner row
                PermissionRow(
                    label = "Owner",
                    readSet = mode and 0b100_000_000 != 0,
                    writeSet = mode and 0b010_000_000 != 0,
                    execSet = mode and 0b001_000_000 != 0,
                    onToggle = { bit, set ->
                        mode = if (set) mode or bit else mode and bit.inv()
                        syncFromCheckboxes()
                    },
                    bits = intArrayOf(0b100_000_000, 0b010_000_000, 0b001_000_000),
                )

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                // Group row
                PermissionRow(
                    label = "Group",
                    readSet = mode and 0b000_100_000 != 0,
                    writeSet = mode and 0b000_010_000 != 0,
                    execSet = mode and 0b000_001_000 != 0,
                    onToggle = { bit, set ->
                        mode = if (set) mode or bit else mode and bit.inv()
                        syncFromCheckboxes()
                    },
                    bits = intArrayOf(0b000_100_000, 0b000_010_000, 0b000_001_000),
                )

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                // Other row
                PermissionRow(
                    label = "Other",
                    readSet = mode and 0b000_000_100 != 0,
                    writeSet = mode and 0b000_000_010 != 0,
                    execSet = mode and 0b000_000_001 != 0,
                    onToggle = { bit, set ->
                        mode = if (set) mode or bit else mode and bit.inv()
                        syncFromCheckboxes()
                    },
                    bits = intArrayOf(0b000_000_100, 0b000_000_010, 0b000_000_001),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode) },
                enabled = !octalError,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PermissionRow(
    label: String,
    readSet: Boolean,
    writeSet: Boolean,
    execSet: Boolean,
    onToggle: (bit: Int, set: Boolean) -> Unit,
    bits: IntArray,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
        Checkbox(
            checked = readSet,
            onCheckedChange = { onToggle(bits[0], it) },
            colors = CheckboxDefaults.colors(checkedColor = app.terminalssh.secure.ui.theme.Turquoise),
        )
        Checkbox(
            checked = writeSet,
            onCheckedChange = { onToggle(bits[1], it) },
            colors = CheckboxDefaults.colors(checkedColor = app.terminalssh.secure.ui.theme.Turquoise),
        )
        Checkbox(
            checked = execSet,
            onCheckedChange = { onToggle(bits[2], it) },
            colors = CheckboxDefaults.colors(checkedColor = app.terminalssh.secure.ui.theme.Turquoise),
        )
    }
}
