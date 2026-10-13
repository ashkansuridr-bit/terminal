package app.terminalssh.secure.ui

import android.content.res.Configuration
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostEditValidationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var app: TerminalApp
    private lateinit var originalLocale: Locale
    private val savedHost = "validation.example.test"

    @Before
    fun setUp() {
        app = instrumentation.targetContext.applicationContext as TerminalApp
        originalLocale = Locale.getDefault()
        setLocale(Locale.ENGLISH)
        app.hosts.hosts().filter { it.host == savedHost }.forEach { app.hosts.delete(it.id) }
    }

    @After
    fun tearDown() {
        app.hosts.hosts().filter { it.host == savedHost }.forEach { app.hosts.delete(it.id) }
        setLocale(originalLocale)
    }

    @Test
    fun validationUsesActiveLocaleAndOnlySavesValidInput() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = instrumentation.targetContext
            val newConnection = context.getString(R.string.home_new_connection)
            val save = context.getString(R.string.save)
            val hostLabel = context.getString(R.string.field_host)
            val portLabel = context.getString(R.string.field_port)
            val usernameLabel = context.getString(R.string.field_username)
            val requiredError = context.getString(R.string.err_host_required)
            val portRangeError = context.getString(R.string.err_port_range)

            // The primary action clears descendant text semantics and exposes one
            // accessible button name, so select the control through its description.
            assertTrue(device.wait(Until.hasObject(By.desc(newConnection)), TIMEOUT_MS))
            device.findObject(By.desc(newConnection)).click()
            assertTrue(device.wait(Until.hasObject(By.text(save)), TIMEOUT_MS))

            // The host field receives focus when the sheet opens. On older Android
            // releases its IME can cover the pinned Save action while keeping stale
            // text in UiAutomator's accessibility snapshot.
            dismissImeIfVisible()
            clickWhenAvailable(By.text(save), "Save after empty host")
            assertTrue(device.wait(Until.hasObject(By.text(requiredError)), TIMEOUT_MS))
            assertTrue(app.hosts.hosts().none { host -> host.host == savedHost })

            // Each host field is named for accessibility (contentDescription = its label).
            // Compose text fields can expose a stale UiAutomator text snapshot on Android 8
            // even after ACTION_SET_TEXT updates the Compose value. Verify the observable
            // behavior through validation and the persisted HostProfile instead of that
            // snapshot; otherwise the harness can fail a correct field update.
            device.findObject(By.desc(hostLabel)).setText(savedHost)
            device.findObject(By.desc(usernameLabel)).setText("tester")
            device.findObject(By.desc(portLabel)).setText("0")
            dismissImeIfVisible()
            clickWhenAvailable(By.text(save), "Save after invalid port")
            assertTrue(
                "Expected port validation message '$portRangeError' after entering port 0",
                device.wait(Until.hasObject(By.text(portRangeError)), TIMEOUT_MS),
            )
            assertTrue(app.hosts.hosts().none { host -> host.host == savedHost })

            device.findObject(By.desc(portLabel)).setText("22")
            dismissImeIfVisible()
            clickWhenAvailable(By.text(save), "Save after valid port")
            assertTrue(device.wait(Until.gone(By.text(save)), TIMEOUT_MS))
            assertTrue(app.hosts.hosts().any { host ->
                host.host == savedHost && host.username == "tester" && host.port == 22
            })
        }
    }

    private fun dismissImeIfVisible() {
        device.waitForIdle()
        var imeVisible = false
        instrumentation.uiAutomation.windows.forEach { window ->
            try {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) imeVisible = true
            } finally {
                window.recycle()
            }
        }
        if (imeVisible) device.pressBack()
    }

    private fun clickWhenAvailable(selector: androidx.test.uiautomator.BySelector, label: String) {
        val button = device.wait(Until.findObject(selector), TIMEOUT_MS)
        if (button == null) throw AssertionError("$label button was not available")
        button.click()
    }

    @Suppress("DEPRECATION")
    private fun setLocale(locale: Locale) {
        Locale.setDefault(locale)
        val resources = instrumentation.targetContext.resources
        val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    private companion object {
        /**
         * Generous on purpose. These assert that a control exists and is reachable, not
         * that it appeared quickly: an emulator without KVM takes tens of seconds to
         * bring up a cold Compose screen, and a tight bound there fails honest code for
         * reasons that have nothing to do with the code.
         */
        const val TIMEOUT_MS = 60_000L
    }
}
