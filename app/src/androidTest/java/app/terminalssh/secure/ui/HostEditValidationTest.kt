package app.terminalssh.secure.ui

import android.content.res.Configuration
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
            val add = context.getString(R.string.hosts_add)
            val save = context.getString(R.string.save)
            val hostLabel = context.getString(R.string.field_host)
            val portLabel = context.getString(R.string.field_port)
            val usernameLabel = context.getString(R.string.field_username)

            assertTrue(device.wait(Until.hasObject(By.text(add)), TIMEOUT_MS))
            device.findObject(By.text(add)).click()
            assertTrue(device.wait(Until.hasObject(By.text(save)), TIMEOUT_MS))

            device.findObject(By.text(save)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Host and username are required")), TIMEOUT_MS))
            assertTrue(app.hosts.hosts().none { host -> host.host == savedHost })

            device.findObject(By.text(hostLabel)).setText(savedHost)
            device.findObject(By.text(usernameLabel)).setText("tester")
            device.findObject(By.text(portLabel)).setText("0")
            device.findObject(By.text(save)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Port must be between 1 and 65535")), TIMEOUT_MS))
            assertTrue(app.hosts.hosts().none { host -> host.host == savedHost })

            device.findObject(By.text(portLabel)).setText("22")
            device.findObject(By.text(save)).click()
            assertTrue(device.wait(Until.gone(By.text(save)), TIMEOUT_MS))
            assertTrue(app.hosts.hosts().any { host ->
                host.host == savedHost && host.username == "tester" && host.port == 22
            })
        }
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
