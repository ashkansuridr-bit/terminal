package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * Multi-line composer for talking to a coding agent.
 *
 * Typing straight into the terminal sends every newline immediately, so a prompt that
 * runs to three lines gets submitted after the first — the agent answers half a question.
 * Here Enter inserts a newline and sending is a deliberate, separate action.
 *
 * The draft is hoisted by the caller so it survives a dropped connection: losing a long
 * prompt to a passing tunnel is the other half of this problem.
 */
@Composable
fun ComposeBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.compose_title),
                style = MaterialTheme.typography.labelMedium,
                color = Turquoise,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.compose_hint),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    stringResource(R.string.cancel),
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                // Deliberately not singleLine: Enter has to insert a newline here, which
                // is the entire reason this bar exists.
                singleLine = false,
                maxLines = 6,
                placeholder = {
                    Text(stringResource(R.string.compose_placeholder), color = TextSecondary)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp, max = 180.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (draft.isNotBlank()) onSend(draft) },
                enabled = draft.isNotBlank(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    stringResource(R.string.compose_send),
                    tint = if (draft.isNotBlank()) Turquoise else TextSecondary,
                )
            }
        }

        if (draft.isNotBlank()) {
            Text(
                stringResource(R.string.compose_lines, draft.lines().size),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Start,
            )
        }
    }
}
