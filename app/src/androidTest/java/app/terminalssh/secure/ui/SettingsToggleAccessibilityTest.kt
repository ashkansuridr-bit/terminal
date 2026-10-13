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
                assertTrue(
                    "Toggle row was not fully reachable by vertical scrolling",
                    device.scrollUntilFullyVisible(
                        By.desc(label),
                        maxAttempts = 20,
                        appPackage = context.packageName,
                    ),
                )

                val toggle = device.findObject(By.desc(label))
                val density = context.resources.displayMetrics.density
                assertTrue("toggle row was not clickable", toggle.isClickable)
                assertFalse("unchanged setting should not expose a reset gesture", toggle.isLongClickable)
                assertTrue("toggle row did not expose switch state", toggle.isChecked)
                assertTrue("toggle row was ${toggle.visibleBounds.height()}px tall", toggle.visibleBounds.height() >= 48 * density)
                assertTrue("toggle row did not include its label and control", toggle.visibleBounds.width() >= 240 * density)

                val toggleNode = AppWindowAccessibility.waitFor(TIMEOUT_MS) {
                    it.contentDescription?.toString() == label && it.isCheckable
                } ?: throw AssertionError("The labeled setting must remain exposed as a switch")
                try {
                    assertTrue("The labeled switch must expose an accessibility click action", toggleNode.isClickable)
                    assertTrue(
                        "Accessibility service click action was rejected for the labeled switch",
                        AppWindowAccessibility.click(toggleNode),
                    )
                } finally {
                    toggleNode.recycle()
                }
                assertTrue(
                    "Toggle action did not persist false " +
                        "(value=${app.settings.confirmMultilinePaste}, " +
                        "persistenceFailure=${app.settingsStore.persistenceFailure.value}, " +
                        "rowStillPresent=${device.hasObject(By.desc(label))})",
                    awaitUiState(TIMEOUT_MS) {
                    !app.settings.confirmMultilinePaste
                    },
                )
                assertTrue("Persisted false value was not reflected by switch semantics", awaitUiState(TIMEOUT_MS) {
                    device.findObject(By.desc(label))?.isChecked == false
                })

                // Exercise the physical touch path independently from ACTION_CLICK.
                // This guards against an accessibility action working while the row's
                // actual tap target is clipped or its pointer handler is disconnected.
                val falseToggle = device.findObject(By.desc(label))
                val falseBounds = falseToggle.visibleBounds
                assertTrue("The touch target must remain fully visible", falseBounds.height() >= 48 * density)
                assertTrue("Physical tap on the switch row was rejected", device.click(falseBounds.centerX(), falseBounds.centerY()))
                assertTrue("Physical tap did not persist true", awaitUiState(TIMEOUT_MS) {
                    app.settings.confirmMultilinePaste && device.findObject(By.desc(label))?.isChecked == true
                })

                val trueBounds = device.findObject(By.desc(label)).visibleBounds
                assertTrue("Second physical tap target must remain fully visible", trueBounds.height() >= 48 * density)
                assertTrue("Second physical tap on the switch row was rejected", device.click(trueBounds.centerX(), trueBounds.centerY()))
                assertTrue("Second physical tap did not persist false", awaitUiState(TIMEOUT_MS) {
                    !app.settings.confirmMultilinePaste && device.findObject(By.desc(label))?.isChecked == false
                })

                val changedToggle = device.findObject(By.desc(label))
                assertTrue("changed setting should expose its reset gesture", changedToggle.isLongClickable)
                changedToggle.longClick()
                assertTrue("Long-press reset did not restore the default true value", awaitUiState(TIMEOUT_MS) {
                    app.settings.confirmMultilinePaste
                })
                assertTrue("reset value was not reflected by switch semantics", awaitUiState(TIMEOUT_MS) {
                    device.findObject(By.desc(label))?.isChecked == true
                })
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
