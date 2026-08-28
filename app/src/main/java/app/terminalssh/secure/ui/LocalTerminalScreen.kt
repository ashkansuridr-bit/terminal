package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.LocalShell
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * Local terminal shell running on the phone itself.
 * Uses [LocalShell] to spawn a `sh` process in the app sandbox.
 */
@Composable
fun LocalTerminalScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val shell = remember { LocalShell(context) }
    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val shellState by shell.state.collectAsStateWithLifecycle()

    // Start shell on first composition
    LaunchedEffect(Unit) {
        if (!shell.isRunning) {
            shell.start(scope) { chunk ->
                output = (output + chunk).takeLast(MAX_OUTPUT_CHARS)
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose { shell.stop() }
    }

    // Auto-scroll to bottom
    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B26))  // Dark terminal background
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF16161E))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.local_terminal),
                style = MaterialTheme.typography.titleMedium,
                color = Turquoise,
                modifier = Modifier.weight(1f),
            )
            if (shellState.running) {
                Text(
                    "PID ${shellState.pid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = TextSecondary,
                )
            }
        }

        // Output area
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = output.ifEmpty { stringResource(R.string.local_terminal_welcome) },
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFA9B1D6),
                    lineHeight = 18.sp,
                ),
            )
        }

        // Input bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF16161E))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "$",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Turquoise,
                ),
                modifier = Modifier.padding(start = 4.dp),
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFA9B1D6),
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotEmpty()) {
                        shell.sendCommand(input)
                        input = ""
                    }
                    focusManager.clearFocus()
                }),
            )
            TextButton(onClick = {
                if (input.isNotEmpty()) {
                    shell.sendCommand(input)
                    input = ""
                }
                focusManager.clearFocus()
            }) {
                Text("▶", color = Turquoise)
            }
        }

        // Quick action bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF12131A))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickKey("ls") { shell.sendCommand("ls -la") }
            QuickKey("pwd") { shell.sendCommand("pwd") }
            QuickKey("whoami") { shell.sendCommand("whoami") }
        }
    }
}

@Composable
private fun QuickKey(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

/** Max output chars kept in memory to prevent OOM on long-running shells. */
private const val MAX_OUTPUT_CHARS = 100_000
