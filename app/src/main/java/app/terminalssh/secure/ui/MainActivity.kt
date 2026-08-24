package app.terminalssh.secure.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import android.content.Intent
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.service.HostShortcuts
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

    /**
     * Held on the Activity rather than created inside the composable: state created
     * during composition is rebuilt on every recomposition, which would snap the app
     * straight back to locked the moment anything above it recomposed.
     */
    private var locked by mutableStateOf(false)

    /** Guards against re-prompting while a prompt is already on screen. */
    private var prompting = false

    /** Host id from a launcher shortcut, if the app was opened through one. */
    private var launchHostId by mutableStateOf<String?>(null)

    private val lockEnabled: Boolean
        get() = (application as TerminalApp).settings.biometricLock &&
            AppLock.availability(this) == LockAvailability.AVAILABLE

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: it swaps SplashTheme for AppTheme, and after
        // super it would be too late for the system to apply the post-splash theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the terminal out of screenshots, recents previews and screen recordings.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        // A rotation re-runs onCreate; only lock on a genuinely fresh start.
        if (savedInstanceState == null) locked = lockEnabled
        launchHostId = intent?.getStringExtra(HostShortcuts.EXTRA_HOST_ID)

        val rtl = Locale.getDefault().language == "fa"
        setContent {
            TerminalTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    if (locked) {
                        LockScreen(onUnlock = ::authenticate)
                        // Offer the prompt immediately; the button is the way back
                        // after a cancel.
                        LaunchedEffect(Unit) { authenticate() }
                    } else {
                        RootScreen(viewModel, launchHostId = launchHostId)
                    }
                }
            }
        }
    }

    /**
     * Re-arm the lock whenever the app leaves the foreground. Locking only at cold start
     * would leave the content readable to anyone who picks the phone up mid-session,
     * which is the case the lock exists for.
     */
    override fun onStop() {
        super.onStop()
        if (lockEnabled && !isChangingConfigurations) locked = true
    }

    /** singleTask means a second shortcut tap arrives here, not through onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchHostId = intent.getStringExtra(HostShortcuts.EXTRA_HOST_ID)
    }

    private fun authenticate() {
        if (prompting) return
        prompting = true
        AppLock.prompt(
            activity = this,
            title = getString(R.string.lock_title),
            subtitle = getString(R.string.lock_subtitle),
        ) { ok ->
            prompting = false
            if (ok) locked = false
        }
    }

    override fun onDestroy() {
        // Sessions intentionally outlive the Activity: they belong to the Application
        // and the foreground service. Only tear them down when the task is finishing.
        if (isFinishing) viewModel.sessions.closeAll()
        super.onDestroy()
    }
}
