package app.terminalssh.secure.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.KeyEntry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeysAccessibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var app: TerminalApp
    private lateinit var entry: KeyEntry

    @Before
    fun setUp() {
        app = instrumentation.targetContext.applicationContext as TerminalApp
        entry = KeyEntry(
            id = "accessibility-key-action",
            name = "Accessible key",
            fingerprint = "SHA256:accessibility-test",
            algorithm = "ssh-ed25519",
            createdAt = 0L,
            hasPassphrase = false,
        )
        app.hosts.upsertKey(entry)
    }

    @After
    fun tearDown() {
        app.hosts.deleteKey(entry.id)
    }

    @Test
    fun deleteActionIsNamedLargeAndDeletesTheIntendedKey() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val keysTab = instrumentation.targetContext.getString(R.string.tab_keys)
            val delete = instrumentation.targetContext.getString(R.string.key_delete, entry.name)

            // Material navigation merges its icon semantics; the visible label remains the
            // stable selector across Compose and Android versions.
            assertTrue(device.wait(Until.hasObject(By.text(keysTab)), TIMEOUT_MS))
            device.findObject(By.text(keysTab)).click()
            assertTrue(device.wait(Until.hasObject(By.desc(delete)), TIMEOUT_MS))

            val bounds = device.findObject(By.desc(delete)).visibleBounds
            val minimumPx = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
            assertTrue("$delete width was ${bounds.width()}px", bounds.width() >= minimumPx)
            assertTrue("$delete height was ${bounds.height()}px", bounds.height() >= minimumPx)

            device.findObject(By.desc(delete)).click()
            assertTrue(device.wait(Until.gone(By.desc(delete)), TIMEOUT_MS))
            assertFalse(app.hosts.keys().any { it.id == entry.id })
        }
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
