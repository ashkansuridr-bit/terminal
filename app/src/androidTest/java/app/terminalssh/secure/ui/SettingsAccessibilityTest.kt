package app.terminalssh.secure.ui

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
    private lateinit var activityContext: Context

    @Test
    fun terminalPalettesAreNamedLargeAndExposeSelection() {
        val app = instrumentation.targetContext.applicationContext as TerminalApp
        val previousTheme = app.settings.themeName

        try {
            app.settings.themeName = "persian_neon"
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var settingsTab: String
                lateinit var persianNeon: String
                lateinit var oled: String
                lateinit var settingsSearch: String
                scenario.onActivity { activity ->
                    activityContext = activity
                    settingsTab = activity.getString(R.string.tab_settings)
                    persianNeon = activity.getString(R.string.settings_palette_persian_neon)
                    oled = activity.getString(R.string.settings_palette_oled)
                    settingsSearch = activity.getString(R.string.settings_search)
                }

                assertTrue(device.wait(Until.hasObject(By.text(settingsTab)), TIMEOUT_MS))
                device.findObject(By.text(settingsTab)).click()
                assertTrue(
                    "Settings tab was tapped but its searchable settings screen did not appear",
                    device.wait(Until.hasObject(By.desc(settingsSearch)), TIMEOUT_MS),
                )
                // The palettes row can sit below the fold on small emulator screens, and a
                // row scrolled only just into view is clipped — which would make the size
                // assertion below measure the screen edge instead of the row.
                // Reach the palette section vertically and confirm the selected preset
                // before scrolling its horizontal strip to OLED for the touch-target check.
                assertTrue(
                    "Selected palette chip was not fully visible after scrolling: ${visibilityDiagnostics(persianNeon)}",
                    scrollSettingsTo(persianNeon),
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
                    scrollSettingsTo(oled, horizontal = true),
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
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var settingsTab: String
                lateinit var fontSizeLabel: String
                lateinit var settingsSearch: String
                scenario.onActivity { activity ->
                    activityContext = activity
                    settingsTab = activity.getString(R.string.tab_settings)
                    fontSizeLabel = activity.getString(R.string.settings_fontsize)
                    settingsSearch = activity.getString(R.string.settings_search)
                }

                assertTrue(device.wait(Until.hasObject(By.text(settingsTab)), TIMEOUT_MS))
                device.findObject(By.text(settingsTab)).click()
                assertTrue(
                    "Settings tab was tapped but its searchable settings screen did not appear",
                    device.wait(Until.hasObject(By.desc(settingsSearch)), TIMEOUT_MS),
                )
                assertTrue(
                    "Font-size slider was not fully visible after scrolling: ${visibilityDiagnostics(fontSizeLabel)}",
                    scrollSettingsTo(fontSizeLabel),
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
            try {
                instrumentation.uiAutomation.windows.forEach { window ->
                    try {
                        if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                        val root = window.root ?: return@forEach
                        try {
                            findBoundsByDescription(root, description)?.let { return it }
                        } finally {
                            root.recycle()
                        }
                    } finally {
                        window.recycle()
                    }
                }
                val activeRoot = instrumentation.uiAutomation.rootInActiveWindow
                if (activeRoot != null) {
                    try {
                        findBoundsByDescription(activeRoot, description)?.let { return it }
                    } finally {
                        activeRoot.recycle()
                    }
                }
            } catch (_: StaleObjectException) {
                // Retry against the new Compose semantics tree.
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

    /** Scrolls the actual app accessibility viewport and accepts only a fully visible control. */
    private fun scrollSettingsTo(description: String, horizontal: Boolean = false): Boolean {
        val density = activityContext.resources.displayMetrics.density
        repeat(28) {
            // Compare bounds from the same UiAutomator window. On API 26/36,
            // AccessibilityNodeInfo.getBoundsInScreen and UiObject2.visibleBounds
            // report different inset origins and falsely indicate that controls are clipped.
            val target = runCatching { device.findObject(By.desc(description)) }.getOrNull()
            if (target != null) {
                val visibleBounds = runCatching { target.visibleBounds }.getOrNull()
                val fullNode = AppWindowAccessibility.find { it.contentDescription?.toString() == description }
                if (fullNode != null) {
                    try {
                        val fullBounds = AppWindowAccessibility.bounds(fullNode)
                        if (visibleBounds != null && visibleBounds.width() == fullBounds.width() &&
                            visibleBounds.height() == fullBounds.height() && fullBounds.width() >= (48 * density).toInt() &&
                            fullBounds.height() >= (48 * density).toInt()
                        ) return true
                    } finally { fullNode.recycle() }
                }
            }

            val candidates = device.findObjects(By.scrollable(true)).mapNotNull { node ->
                runCatching { node.visibleBounds }.getOrNull()
            }
            val viewport = candidates.filter { bounds ->
                if (horizontal) bounds.width() > bounds.height() && bounds.height() <= (160 * density).toInt()
                else bounds.height() > bounds.width()
            }.maxByOrNull { if (horizontal) it.width() else it.height() } ?: return false

            val scrollable = device.findObjects(By.scrollable(true)).firstOrNull { node ->
                runCatching {
                    val bounds = node.visibleBounds
                    if (horizontal) bounds.width() > bounds.height() else bounds.height() > bounds.width()
                }.getOrDefault(false)
            }
            // UiAutomator and the Compose accessibility window can use different
            // inset origins on API 36. Translate fallback touch coordinates using
            // the target's raw accessibility bounds when both nodes are present.
            val gestureOffsetY = if (horizontal) {
                val visibleTarget = runCatching { device.findObject(By.desc(description))?.visibleBounds }.getOrNull()
                val fullTarget = AppWindowAccessibility.find { it.contentDescription?.toString() == description }
                try {
                    if (visibleTarget != null && fullTarget != null) {
                        AppWindowAccessibility.bounds(fullTarget).centerY() - visibleTarget.centerY()
                    } else 0
                } finally {
                    fullTarget?.recycle()
                }
            } else 0
            if (scrollable != null) {
                val direction = if (horizontal) {
                    if (activityContext.resources.configuration.layoutDirection.toComposeLayoutDirection() == LayoutDirection.Ltr) {
                        Direction.LEFT
                    } else Direction.RIGHT
                } else Direction.UP
                runCatching { scrollable.scroll(direction, 0.7f) }
                device.waitForIdle()
                val after = runCatching { device.findObject(By.desc(description)) }.getOrNull()
                if (after != null) {
                    val visibleBounds = runCatching { after.visibleBounds }.getOrNull()
                    val fullNode = AppWindowAccessibility.find { it.contentDescription?.toString() == description }
                    if (fullNode != null) {
                        try {
                            val fullBounds = AppWindowAccessibility.bounds(fullNode)
                            if (visibleBounds != null && visibleBounds.width() == fullBounds.width() &&
                                visibleBounds.height() == fullBounds.height() && fullBounds.width() >= (48 * density).toInt() &&
                                fullBounds.height() >= (48 * density).toInt()
                            ) return true
                        } finally { fullNode.recycle() }
                    }
                }
                // Some Compose versions omit ACTION_SCROLL for the outer vertical
                // container or move less than a full control; gesture fallback below
                // continues toward the target in UiAutomator's screen coordinates.
            }
            val (startX, startY, endX, endY) = if (horizontal) {
                val inset = (24 * density).toInt().coerceAtMost(viewport.width() / 4)
                val y = (viewport.centerY() + gestureOffsetY).coerceIn(1, device.displayHeight - 2)
                if (LayoutDirection.Ltr == instrumentation.targetContext.resources.configuration.layoutDirection.toComposeLayoutDirection()) {
                    intArrayOf(viewport.right - inset, y, viewport.left + inset, y)
                } else {
                    intArrayOf(viewport.left + inset, y, viewport.right - inset, y)
                }
            } else {
                intArrayOf(viewport.centerX(), viewport.bottom - viewport.height() / 5,
                    viewport.centerX(), viewport.top + viewport.height() / 5)
            }
            device.swipe(startX, startY, endX, endY, 16)
            device.waitForIdle()
            SystemClock.sleep(100)
        }
        return false
    }

    private fun Int.toComposeLayoutDirection(): LayoutDirection =
        if (this == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr

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
