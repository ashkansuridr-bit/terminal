package app.terminalssh.secure.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsToggleAccessibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun securityToggleUsesFullLabeledRowAndPersistsState() {
        val app = instrumentation.targetContext.applicationContext as TerminalApp
        val previous = app.settings.confirmMultilinePaste

        try {
            app.settings.confirmMultilinePaste = true
            ActivityScenario.launch(MainActivity::class.java).use {
                val context = instrumentation.targetContext
                val settingsTab = context.getString(R.string.tab_settings)
                val label = context.getString(R.string.settings_paste_confirm)

                assertTrue(device.wait(Until.hasObject(By.text(settingsTab)), TIMEOUT_MS))
                device.findObject(By.text(settingsTab)).click()
                assertTrue(device.wait(Until.hasObject(By.desc(label)), TIMEOUT_MS))

                val toggle = device.findObject(By.desc(label))
                val density = context.resources.displayMetrics.density
                assertTrue("toggle row was not clickable", toggle.isClickable)
                assertTrue("toggle row did not expose switch state", toggle.isChecked)
                assertTrue("toggle row was ${toggle.visibleBounds.height()}px tall", toggle.visibleBounds.height() >= 48 * density)
                assertTrue("toggle row did not include its label and control", toggle.visibleBounds.width() >= 240 * density)

                toggle.click()
                assertTrue(device.wait(Until.findObject(By.desc(label)), TIMEOUT_MS).let { !it.isChecked })
                assertFalse(app.settings.confirmMultilinePaste)
            }
        } finally {
            app.settings.confirmMultilinePaste = previous
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
