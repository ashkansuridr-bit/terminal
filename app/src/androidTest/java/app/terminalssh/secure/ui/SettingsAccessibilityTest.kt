package app.terminalssh.secure.ui

import android.graphics.Rect
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import androidx.compose.ui.unit.LayoutDirection
import android.view.accessibility.AccessibilityNodeInfo
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun terminalPalettesAreNamedLargeAndExposeSelection() {
        val app = instrumentation.targetContext.applicationContext as TerminalApp
        val previousTheme = app.settings.themeName

        try {
            app.settings.themeName = "persian_neon"
            ActivityScenario.launch(MainActivity::class.java).use {
                val settingsTab = instrumentation.targetContext.getString(R.string.tab_settings)
                val persianNeon = instrumentation.targetContext.getString(R.string.settings_palette_persian_neon)
                val oled = instrumentation.targetContext.getString(R.string.settings_palette_oled)

                assertTrue(device.wait(Until.hasObject(By.text(settingsTab)), TIMEOUT_MS))
                device.findObject(By.text(settingsTab)).click()
                // The palettes row can sit below the fold on small emulator screens, and a
                // row scrolled only just into view is clipped — which would make the size
                // assertion below measure the screen edge instead of the row.
                // Reach the palette section vertically and confirm the selected preset
                // before scrolling its horizontal strip to OLED for the touch-target check.
                assertTrue(
                    "Selected palette chip was not fully visible after scrolling: ${visibilityDiagnostics(persianNeon)}",
                    device.scrollUntilFullyVisible(
                        By.desc(persianNeon),
                        maxAttempts = 20,
                        fullBounds = { fullBoundsByDescription(persianNeon) },
                        appPackage = instrumentation.targetContext.packageName,
                    ),
                )
                val selectedPalette = device.findObject(By.desc(persianNeon))
                assertTrue(
                    "Selected theme must be stored and exposed as selected/checked " +
                        "(selected=${selectedPalette.isSelected}, checked=${selectedPalette.isChecked}, " +
                        "stored=${app.settings.themeName})",
                    selectedPalette.isSelected || selectedPalette.isChecked,
                )
                assertTrue(
                    "OLED palette chip was not fully visible after horizontal scrolling: ${visibilityDiagnostics(oled)}",
                    device.scrollUntilFullyVisible(
                        By.desc(oled),
                        maxAttempts = 20,
                        horizontalDirection = LayoutDirection.Ltr,
                        fullBounds = { fullBoundsByDescription(oled) },
                        appPackage = instrumentation.targetContext.packageName,
                    ),
                )
                val oledAction = device.findObject(By.desc(oled))
                val minimumPx = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
                assertTrue("$oled width was ${oledAction.visibleBounds.width()}px", oledAction.visibleBounds.width() >= minimumPx)
                assertTrue("$oled height was ${oledAction.visibleBounds.height()}px", oledAction.visibleBounds.height() >= minimumPx)
                assertFalse(oledAction.isSelected || oledAction.isChecked)

                oledAction.click()
                assertTrue("Palette selection was not persisted and exposed", awaitUiState(TIMEOUT_MS) {
                    device.findObject(By.desc(oled))?.let { it.isSelected || it.isChecked } == true &&
                        app.settings.themeName == "oled"
                })
            }
        } finally {
            app.settings.themeName = previousTheme
        }
    }

    @Test
    fun fontSizeSliderHasLocalizedNameAndAdjustableValue() {
        val app = instrumentation.targetContext.applicationContext as TerminalApp
        val previousFontSize = app.settings.fontSizeSp

        try {
            app.settings.fontSizeSp = 17
            ActivityScenario.launch(MainActivity::class.java).use {
                val settingsTab = instrumentation.targetContext.getString(R.string.tab_settings)
                val fontSizeLabel = instrumentation.targetContext.getString(R.string.settings_fontsize)

                assertTrue(device.wait(Until.hasObject(By.text(settingsTab)), TIMEOUT_MS))
                device.findObject(By.text(settingsTab)).click()
                assertTrue(
                    "Font-size slider was not fully visible after scrolling: ${visibilityDiagnostics(fontSizeLabel)}",
                    device.scrollUntilFullyVisible(
                        By.desc(fontSizeLabel),
                        maxAttempts = 20,
                        fullBounds = { fullBoundsByDescription(fontSizeLabel) },
                        appPackage = instrumentation.targetContext.packageName,
                    ),
                )

                val sliderNode = findAdjustableNodeByDescription(fontSizeLabel)
                assertNotNull("font-size slider node carrying both its accessible name and adjustable range was missing", sliderNode)
                try {
                    assertEquals(17f, sliderNode?.rangeInfo?.current ?: Float.NaN, 0.01f)
                    val progressArguments = Bundle().apply {
                        putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 18f)
                    }
                    assertTrue(
                        sliderNode!!.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                            progressArguments,
                        ),
                    )
                } finally {
                    sliderNode?.recycle()
                }
                assertTrue("Font-size action was not persisted and exposed", awaitUiState(TIMEOUT_MS) {
                    val current = findAdjustableNodeByDescription(fontSizeLabel)
                    try {
                        app.settings.fontSizeSp == 18 && current?.rangeInfo?.current == 18f
                    } finally {
                        current?.recycle()
                    }
                })
            }
        } finally {
            app.settings.fontSizeSp = previousFontSize
        }
    }

    private fun findAdjustableNodeByDescription(
        node: AccessibilityNodeInfo?,
        description: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString() == description && node.rangeInfo != null) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                findAdjustableNodeByDescription(child, description)?.let { return it }
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun findAdjustableNodeByDescription(description: String): AccessibilityNodeInfo? {
        repeat(5) {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            try {
                findAdjustableNodeByDescription(root, description)?.let { return it }
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Compose can replace the semantics tree while the slider redraws; retry a fresh root.
            } finally {
                root.recycle()
            }
            android.os.SystemClock.sleep(100)
        }
        return null
    }

    private fun fullBoundsByDescription(description: String): Rect? {
        repeat(3) {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            try {
                findBoundsByDescription(root, description)?.let { return it }
            } catch (_: StaleObjectException) {
                // Retry against the new Compose semantics tree.
            } finally {
                root.recycle()
            }
        }
        return null
    }

    private fun visibilityDiagnostics(description: String): String {
        val visible = runCatching { device.findObject(By.desc(description))?.visibleBounds }.getOrNull()
        val full = fullBoundsByDescription(description)
        val scrollables = device.findObjects(By.scrollable(true)).joinToString { node ->
            runCatching { "${node.visibleBounds}:${node.contentDescription}" }.getOrDefault("<stale>")
        }
        return "target=$description visible=$visible full=$full scrollables=[$scrollables] " +
            "display=${device.displayWidth}x${device.displayHeight}"
    }

    private fun findBoundsByDescription(
        node: AccessibilityNodeInfo,
        description: String,
    ): Rect? {
        if (node.contentDescription?.toString() == description) {
            return Rect().also(node::getBoundsInScreen)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                findBoundsByDescription(child, description)?.let { return it }
            } finally {
                child.recycle()
            }
        }
        return null
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
