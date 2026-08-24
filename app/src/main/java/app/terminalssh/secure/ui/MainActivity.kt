package app.terminalssh.secure.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.ui.theme.TerminalTheme
import app.terminalssh.secure.vm.AppViewModel
import java.util.Locale

/**
 * [FragmentActivity] rather than ComponentActivity because BiometricPrompt hosts itself
 * in a fragment; it is still a ComponentActivity, so Compose and `by viewModels()` work
 * exactly as before.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** Survives configuration changes, so a rotation does not re-prompt. */
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the terminal out of screenshots, recents previews and screen recordings.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val settings = (application as TerminalApp).settings
        val rtl = Locale.getDefault().language == "fa"

        setContent {
            // Treat the lock as already satisfied when the setting is off, or when the
            // device has no enrolled credential — otherwise enabling the toggle and then
            // removing the screen lock would leave the app permanently unopenable.
            val lockRequired = settings.biometricLock &&
                AppLock.availability(this) == LockAvailability.AVAILABLE
            var locked by mutableStateOf(lockRequired && !unlocked)

            TerminalTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    if (locked) {
                        LockScreen(onUnlock = { authenticate { locked = false } })
                        LaunchedEffect(Unit) { authenticate { locked = false } }
                    } else {
                        RootScreen(viewModel)
                    }
                }
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        AppLock.prompt(
            activity = this,
            title = getString(R.string.lock_title),
            subtitle = getString(R.string.lock_subtitle),
        ) { ok ->
            if (ok) {
                unlocked = true
                onSuccess()
            }
        }
    }

    override fun onDestroy() {
        // Sessions intentionally outlive the Activity: they belong to the Application
        // and the foreground service. Only tear them down when the task is finishing.
        if (isFinishing) viewModel.sessions.closeAll()
        super.onDestroy()
    }
}
