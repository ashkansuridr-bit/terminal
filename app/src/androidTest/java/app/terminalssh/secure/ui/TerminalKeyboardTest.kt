package app.terminalssh.secure.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.vm.AppViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalKeyboardTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var app: TerminalApp
    private var previousHardwareKeyboardSetting = "0"
    private var previousPasteConfirmation = true

    @Before
    fun setUp() {
        app = instrumentation.targetContext.applicationContext as TerminalApp
        app.sessions.closeAll()
        previousHardwareKeyboardSetting = readSecureSetting("show_ime_with_hard_keyboard")
        writeSecureSetting("show_ime_with_hard_keyboard", "1")
        previousPasteConfirmation = app.settings.confirmMultilinePaste
        app.settings.confirmMultilinePaste = true
    }

    @After
    fun tearDown() {
        device.setOrientationNatural()
        app.sessions.closeAll()
        app.settings.confirmMultilinePaste = previousPasteConfirmation
        clipboardManager().clearPrimaryClip()
        writeSecureSetting("show_ime_with_hard_keyboard", previousHardwareKeyboardSetting)
    }

    @Test
    fun keyboardActionReopensImeAfterBackDismissal() {
        app.sessions.add(idleSession())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val keyboardAction = instrumentation.targetContext.getString(R.string.show_keyboard)

            // Material navigation merges icon semantics into the item; its visible label is
            // the stable selector across Compose and Android versions.
            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            assertTrue(device.wait(Until.hasObject(By.desc(keyboardAction)), UI_TIMEOUT_MS))

            // Exercise more than the first recovery. Input-method races commonly appear only
            // after Android reuses an existing input connection.
            repeat(2) {
                dismissAndReopenKeyboard(scenario, keyboardAction)
            }

            // MainActivity handles orientation changes in place. Ensure the embedded terminal
            // editor remains discoverable after its AndroidView is laid out at a new size.
            device.pressBack()
            assertTrue(waitForIme(scenario, visible = false))
            device.setOrientationLeft()
            assertTrue(device.wait(Until.hasObject(By.desc(keyboardAction)), UI_TIMEOUT_MS))
            device.findObject(By.desc(keyboardAction)).click()
            assertTrue(waitForIme(scenario, visible = true))
        }
    }

    @Test
    fun keyboardActionFocusesTerminalWhenHardwareKeyboardSuppressesIme() {
        writeSecureSetting("show_ime_with_hard_keyboard", "0")
        app.sessions.add(idleSession(id = "hardware-keyboard", title = "Hardware keyboard test"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val keyboardAction = instrumentation.targetContext.getString(R.string.show_keyboard)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            assertTrue(device.wait(Until.hasObject(By.desc(keyboardAction)), UI_TIMEOUT_MS))

            device.pressBack()
            assertTrue(waitForIme(scenario, visible = false))
            device.findObject(By.desc(keyboardAction)).click()

            // IME visibility after an explicit app request varies by Android version even
            // when this hardware-keyboard preference is disabled. The app-controlled
            // invariant is that termlib's text editor receives focus for physical keys.
            assertTrue(focusedViewSummary(scenario), waitForFocusedTextEditor(scenario))

            writeSecureSetting("show_ime_with_hard_keyboard", "1")
            device.findObject(By.desc(keyboardAction)).click()
            assertTrue(waitForIme(scenario, visible = true))
        }
    }

    @Test
    fun closeActionNamesAndClosesOnlyItsSession() {
        val firstTitle = "First host"
        val secondTitle = "Second host"
        app.sessions.add(idleSession(id = "first-session", title = firstTitle))
        app.sessions.add(idleSession(id = "second-session", title = secondTitle))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val firstClose = instrumentation.targetContext.getString(R.string.close_session, firstTitle)
            val secondClose = instrumentation.targetContext.getString(R.string.close_session, secondTitle)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            assertTrue(device.wait(Until.hasObject(By.desc(firstClose)), UI_TIMEOUT_MS))
            assertTrue(device.wait(Until.hasObject(By.desc(secondClose)), UI_TIMEOUT_MS))

            device.findObject(By.desc(firstClose)).click()

            assertTrue(device.wait(Until.gone(By.desc(firstClose)), UI_TIMEOUT_MS))
            assertTrue(device.hasObject(By.desc(secondClose)))
        }
    }

    @Test
    fun sessionTabsExposeAndUpdateSelectedState() {
        val firstTitle = "First selectable host"
        val secondTitle = "Second selectable host"
        app.sessions.add(idleSession(id = "first-selectable-session", title = firstTitle))
        app.sessions.add(idleSession(id = "second-selectable-session", title = secondTitle))

        ActivityScenario.launch(MainActivity::class.java).use {
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()

            val firstSession = device.wait(Until.findObject(By.text(firstTitle)), UI_TIMEOUT_MS)
            val secondSession = device.wait(Until.findObject(By.text(secondTitle)), UI_TIMEOUT_MS)
            assertTrue(firstSession.isSelected)
            assertFalse(secondSession.isSelected)

            secondSession.click()

            assertFalse(device.wait(Until.findObject(By.text(firstTitle)), UI_TIMEOUT_MS).isSelected)
            assertTrue(device.wait(Until.findObject(By.text(secondTitle)), UI_TIMEOUT_MS).isSelected)
        }
    }

    @Test
    fun multilinePasteRequiresConfirmationAndCancelDoesNotPaste() {
        app.sessions.add(idleSession(id = "paste-confirmation", title = "Paste test"))
        clipboardManager().setPrimaryClip(ClipData.newPlainText("test", "first\nsecond"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val dialogTitle = instrumentation.targetContext.getString(R.string.paste_confirm_title, 2)
            val cancel = instrumentation.targetContext.getString(R.string.cancel)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[AppViewModel::class.java]
                    .pasteRequested.value = true
            }

            assertTrue(device.wait(Until.hasObject(By.text(dialogTitle)), UI_TIMEOUT_MS))
            device.findObject(By.text(cancel)).click()
            assertTrue(device.wait(Until.gone(By.text(dialogTitle)), UI_TIMEOUT_MS))

            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[AppViewModel::class.java]
                assertFalse(viewModel.pasteRequested.value)
            }
        }
    }

    @Test
    fun carriageReturnMultilinePasteRequiresConfirmation() {
        app.sessions.add(idleSession(id = "cr-paste-confirmation", title = "CR paste test"))
        clipboardManager().setPrimaryClip(ClipData.newPlainText("test", "first\rsecond"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val dialogTitle = instrumentation.targetContext.getString(R.string.paste_confirm_title, 2)
            val cancel = instrumentation.targetContext.getString(R.string.cancel)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[AppViewModel::class.java]
                    .pasteRequested.value = true
            }

            assertTrue(device.wait(Until.hasObject(By.text(dialogTitle)), UI_TIMEOUT_MS))
            scenario.onActivity { activity ->
                assertTrue(ViewModelProvider(activity)[AppViewModel::class.java].pasteRequested.value)
            }
            device.findObject(By.text(cancel)).click()
            assertTrue(device.wait(Until.gone(By.text(dialogTitle)), UI_TIMEOUT_MS))
        }
    }

    @Test
    fun modifierKeysExposeAndUpdateToggleState() {
        app.sessions.add(idleSession(id = "modifier-semantics", title = "Modifier test"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()

            // Essential terminal modifiers are deliberately first in the phone viewport.
            assertTrue("Ctrl must be visible without scrolling", device.hasObject(By.text("Ctrl")))
            assertTrue(device.hasObject(By.text("Alt")))
            assertTrue(device.hasObject(By.text("Shift")))

            val ctrl = device.findObject(By.text("Ctrl"))
            val alt = device.findObject(By.text("Alt"))
            val shift = device.findObject(By.text("Shift"))
            assertTrue(ctrl.isCheckable)
            assertFalse(ctrl.isChecked)
            assertTrue(alt.isCheckable)
            assertFalse(alt.isChecked)
            assertTrue(shift.isCheckable)
            assertFalse(shift.isChecked)

            ctrl.click()
            assertTrue(device.wait(Until.findObject(By.text("Ctrl")), UI_TIMEOUT_MS).isChecked)

            alt.click()
            assertTrue(device.wait(Until.findObject(By.text("Ctrl")), UI_TIMEOUT_MS).isChecked)
            assertTrue(device.wait(Until.findObject(By.text("Alt")), UI_TIMEOUT_MS).isChecked)

            shift.click()
            assertTrue(device.wait(Until.findObject(By.text("Ctrl")), UI_TIMEOUT_MS).isChecked)
            assertTrue(device.wait(Until.findObject(By.text("Alt")), UI_TIMEOUT_MS).isChecked)
            assertTrue(device.wait(Until.findObject(By.text("Shift")), UI_TIMEOUT_MS).isChecked)

            device.findObject(By.text("Tab")).click()
            assertFalse(device.wait(Until.findObject(By.text("Ctrl")), UI_TIMEOUT_MS).isChecked)
            assertFalse(device.wait(Until.findObject(By.text("Alt")), UI_TIMEOUT_MS).isChecked)
            assertFalse(device.wait(Until.findObject(By.text("Shift")), UI_TIMEOUT_MS).isChecked)
            assertTrue(focusedViewSummary(scenario), waitForFocusedTextEditor(scenario))
        }
    }

    @Test
    fun symbolicToolbarKeysHaveReadableActionNames() {
        app.sessions.add(idleSession(id = "toolbar-semantics", title = "Toolbar test"))

        ActivityScenario.launch(MainActivity::class.java).use {
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val interrupt = instrumentation.targetContext.getString(R.string.terminal_key_interrupt)
            val endOfInput = instrumentation.targetContext.getString(R.string.terminal_key_eof)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), UI_TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()

            // Reveal the symbolic actions in the horizontally scrolling toolbar.
            repeat(3) { device.swipe(900, 2100, 200, 2100, 20) }
            assertTrue(device.wait(Until.hasObject(By.desc(interrupt)), UI_TIMEOUT_MS))
            assertTrue(device.wait(Until.hasObject(By.desc(endOfInput)), UI_TIMEOUT_MS))
        }
    }

    private fun dismissAndReopenKeyboard(
        scenario: ActivityScenario<MainActivity>,
        keyboardAction: String,
    ) {
        device.waitForIdle()
        device.pressBack()
        assertTrue(waitForIme(scenario, visible = false))
        device.findObject(By.desc(keyboardAction)).click()
        assertTrue(waitForIme(scenario, visible = true))
    }

    private fun idleSession(
        id: String = "keyboard-regression",
        title: String = "Keyboard test",
    ): SshSession = SshSession(
        id = id,
        profile = HostProfile(
            id = "$id-host",
            label = title,
            host = "127.0.0.1",
            port = 1,
            username = "tester",
            auth = AuthMethod.Password(""),
        ),
        client = app.client,
        keepAlive = false,
        onClipboardCopy = {},
        onPasteRequest = {},
    )

    private fun waitForIme(scenario: ActivityScenario<MainActivity>, visible: Boolean): Boolean {
        repeat(IME_POLL_ATTEMPTS) {
            var isVisible = false
            scenario.onActivity { activity ->
                isVisible = WindowInsetsCompat.toWindowInsetsCompat(
                    activity.window.decorView.rootWindowInsets,
                ).isVisible(WindowInsetsCompat.Type.ime())
            }
            if (isVisible == visible) return true
            Thread.sleep(IME_POLL_MS)
        }
        return false
    }

    private fun waitForFocusedTextEditor(scenario: ActivityScenario<MainActivity>): Boolean {
        repeat(IME_POLL_ATTEMPTS) {
            var hasFocusedEditor = false
            scenario.onActivity { activity ->
                hasFocusedEditor = activity.currentFocus?.let { focused ->
                    focused.hasFocus() && focused.onCheckIsTextEditor()
                } == true
            }
            if (hasFocusedEditor) return true
            Thread.sleep(IME_POLL_MS)
        }
        return false
    }

    private fun focusedViewSummary(scenario: ActivityScenario<MainActivity>): String {
        var summary = "No focused view"
        scenario.onActivity { activity ->
            activity.currentFocus?.let { focused ->
                summary = "Focused ${focused.javaClass.name}; textEditor=${focused.onCheckIsTextEditor()}"
            }
        }
        return summary
    }

    private fun readSecureSetting(name: String): String =
        Settings.Secure.getString(instrumentation.targetContext.contentResolver, name) ?: "0"

    private fun writeSecureSetting(name: String, value: String) {
        instrumentation.uiAutomation.executeShellCommand("settings put secure $name $value").close()
        device.waitForIdle()
    }

    private fun clipboardManager(): ClipboardManager =
        instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private companion object {
        /**
         * Generous on purpose. These assert that a control exists and is reachable, not
         * that it appeared quickly: an emulator without KVM takes tens of seconds to
         * bring up a cold Compose screen, and a tight bound there fails honest code for
         * reasons that have nothing to do with the code.
         */
        const val UI_TIMEOUT_MS = 60_000L
        const val IME_POLL_ATTEMPTS = 30
        const val IME_POLL_MS = 100L
    }
}
