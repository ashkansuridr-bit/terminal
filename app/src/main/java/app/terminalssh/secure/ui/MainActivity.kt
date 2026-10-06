package app.terminalssh.secure.ui

import android.os.Bundle
import android.provider.Settings
import androidx.biometric.BiometricPrompt
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.fragment.app.FragmentActivity
import app.terminalssh.secure.R
import android.content.Intent
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.service.HostShortcuts
import app.terminalssh.secure.ui.theme.TerminalTheme
import app.terminalssh.secure.vm.AppViewModel

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
    private var locked by mutableStateOf(true)
    private lateinit var lockGate: AppLockGate
    private var lockAvailability by mutableStateOf(LockAvailability.UNAVAILABLE)
    private var activePrompt: BiometricPrompt? = null

    /** Guards against re-prompting while a prompt is already on screen. */
    private var promptingTicket: Long? = null

    /** Host id from a launcher shortcut, if the app was opened through one. */
    private var launchHostId by mutableStateOf<String?>(null)

    private val lockEnabled: Boolean
        get() = (application as TerminalApp).settings.biometricLock

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: it swaps SplashTheme for AppTheme, and after
        // super it would be too late for the system to apply the post-splash theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the terminal out of screenshots, recents previews and screen recordings.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        // Never restore an unlocked flag from a Bundle: recreation or process restoration
        // must satisfy the enabled lock again before any sensitive content is composed.
        lockGate = AppLockGate(lockEnabled)
        locked = lockGate.locked
        lockAvailability = AppLock.availability(this)
        launchHostId = intent?.getStringExtra(HostShortcuts.EXTRA_HOST_ID)

        setContent {
            TerminalTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides terminalLayoutDirection(
                        androidx.compose.ui.platform.LocalConfiguration.current.locales[0],
                    ),
                ) {
                    if (locked) {
                        LockScreen(
                            onUnlock = ::authenticate,
                            availability = lockAvailability,
                            onSecuritySettings = {
                                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            },
                        )
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
    override fun onStart() {
        super.onStart()
        lockGate.start(lockEnabled)
        locked = lockGate.locked
        lockAvailability = AppLock.availability(this)
        if (locked) authenticate()
    }

    override fun onStop() {
        // Device-credential authentication may itself stop this Activity. Keep its
        // ticket until a result or destruction, while hiding content immediately.
        // Background success cannot unlock; only a foreground callback can do so.
        lockGate.stop(preserveSystemPrompt = activePrompt != null)
        locked = lockGate.locked
        super.onStop()
    }

    /** singleTask means a second shortcut tap arrives here, not through onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchHostId = intent.getStringExtra(HostShortcuts.EXTRA_HOST_ID)
    }

    private fun authenticate() {
        lockAvailability = AppLock.availability(this)
        if (promptingTicket != null || lockAvailability != LockAvailability.AVAILABLE) return
        val ticket = lockGate.beginPrompt() ?: return
        promptingTicket = ticket
        activePrompt = AppLock.prompt(
            activity = this,
            title = getString(R.string.lock_title),
            subtitle = getString(R.string.lock_subtitle),
        ) { ok ->
            if (promptingTicket == ticket) {
                promptingTicket = null
                activePrompt = null
            }
            lockGate.authenticationResult(ticket, ok)
            locked = lockGate.locked
        }
    }

    override fun onDestroy() {
        if (::lockGate.isInitialized) {
            lockGate.stop()
            locked = lockGate.locked
        }
        promptingTicket = null
        activePrompt?.cancelAuthentication()
        activePrompt = null
        // Sessions intentionally outlive the Activity: they belong to the Application
        // and the foreground service. Only tear them down when the task is finishing.
        if (isFinishing) viewModel.closeAllSessions()
        super.onDestroy()
    }
}
