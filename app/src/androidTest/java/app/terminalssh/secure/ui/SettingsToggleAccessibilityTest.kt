package app.terminalssh.secure.ui

import android.graphics.Rect
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
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
                    "Settings tab was tapped but its searchable settings screen did not appear",
                    device.wait(Until.hasObject(By.desc(context.getString(R.string.settings_search))), TIMEOUT_MS),
                )
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
                val initial = stableSnapshot(label)
                assertTrue("toggle row was not clickable", initial.clickable)
                assertFalse("unchanged setting should not expose a reset gesture", initial.longClickable)
                assertTrue("toggle row did not expose switch state", initial.checked)
                assertTrue("toggle row was ${initial.bounds.height()}px tall", initial.bounds.height() >= 48 * density)
                assertTrue("toggle row did not include its label and control", initial.bounds.width() >= 240 * density)

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
                assertTrue(
                    "The switch row became clipped after the accessibility action",
                    device.scrollUntilFullyVisible(By.desc(label), maxAttempts = 20, appPackage = context.packageName),
                )
                val falseSnapshot = stableSnapshot(label)
                assertTrue(
                    "The touch target must remain fully visible after re-scrolling; " +
                        "screenBounds=${falseSnapshot.bounds}, visibleBounds=${falseSnapshot.visibleBounds}, " +
                        "display=${device.displayWidth}x${device.displayHeight}, scrollables=${device.findObjects(By.scrollable(true)).map { it.visibleBounds }}",
                    falseSnapshot.bounds.height() >= 48 * density &&
                        falseSnapshot.visibleBounds.width() == falseSnapshot.bounds.width() &&
                        falseSnapshot.visibleBounds.height() == falseSnapshot.bounds.height(),
                )
                assertTrue(
                    "Physical tap on the switch row was rejected",
                    device.click(falseSnapshot.visibleBounds.centerX(), falseSnapshot.visibleBounds.centerY()),
                )
                assertTrue("Physical tap did not persist true", awaitUiState(TIMEOUT_MS) {
                    app.settings.confirmMultilinePaste && stableSnapshotOrNull(label)?.checked == true
                })

                val trueSnapshot = stableSnapshot(label)
                assertTrue(
                    "Second physical tap target must remain fully visible; " +
                        "screenBounds=${trueSnapshot.bounds}, visibleBounds=${trueSnapshot.visibleBounds}",
                    trueSnapshot.bounds.height() >= 48 * density &&
                        trueSnapshot.visibleBounds.width() == trueSnapshot.bounds.width() &&
                        trueSnapshot.visibleBounds.height() == trueSnapshot.bounds.height(),
                )
                assertTrue(
                    "Second physical tap on the switch row was rejected",
                    device.click(trueSnapshot.visibleBounds.centerX(), trueSnapshot.visibleBounds.centerY()),
                )
                assertTrue("Second physical tap did not persist false", awaitUiState(TIMEOUT_MS) {
                    !app.settings.confirmMultilinePaste && stableSnapshotOrNull(label)?.checked == false
                })

                val changedToggle = stableSnapshot(label)
                assertTrue("changed setting should expose its reset gesture", changedToggle.longClickable)
                longClickFreshNode(label)
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

    private data class SwitchSnapshot(
        val bounds: Rect,
        val visibleBounds: Rect,
        val checked: Boolean,
        val clickable: Boolean,
        val longClickable: Boolean,
    )

    /** Reacquire an accessibility object after Compose recomposition instead of reading stale handles. */
    private fun stableSnapshot(label: String): SwitchSnapshot =
        stableSnapshotOrNull(label) ?: throw AssertionError("Switch '$label' was not stable in the accessibility tree")

    private fun stableSnapshotOrNull(label: String): SwitchSnapshot? {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val node = runCatching { device.wait(Until.findObject(By.desc(label)), 1_000) }.getOrNull()
            if (node != null) {
                try {
                    val appNode = AppWindowAccessibility.find {
                        it.contentDescription?.toString() == label && it.isCheckable
                    } ?: return null
                    try {
                        return SwitchSnapshot(
                            AppWindowAccessibility.bounds(appNode),
                            node.visibleBounds,
                            node.isChecked,
                            node.isClickable,
                            node.isLongClickable,
                        )
                    } finally { appNode.recycle() }
                } catch (_: StaleObjectException) {
                    // Reacquire the node after Compose replaces the semantics tree.
                }
            }
            SystemClock.sleep(100)
        }
        return null
    }

    /** UiObject2 owns the long-press action; UiDevice only provides swipe/tap primitives. */
    private fun longClickFreshNode(label: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val node = runCatching { device.wait(Until.findObject(By.desc(label)), 1_000) }.getOrNull()
            if (node != null) {
                try {
                    node.longClick()
                    return
                } catch (_: StaleObjectException) {
                    // Reacquire after a Compose semantics update.
                }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("Switch '$label' did not accept a long press")
    }
}
