package app.terminalssh.secure.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.terminalssh.secure.ui.theme.TerminalTheme
import app.terminalssh.secure.vm.AppViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the terminal out of screenshots, recents previews and screen recordings.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val rtl = Locale.getDefault().language == "fa"
        setContent {
            TerminalTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    RootScreen(viewModel)
                }
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
