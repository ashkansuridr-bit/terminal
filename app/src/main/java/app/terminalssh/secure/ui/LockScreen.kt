package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R

/**
 * Shown instead of the app content while locked. It deliberately renders nothing from
 * the session list or host list, so the lock also covers the app-switcher preview.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
