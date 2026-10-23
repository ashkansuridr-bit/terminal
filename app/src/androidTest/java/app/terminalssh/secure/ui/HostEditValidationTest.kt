package app.terminalssh.secure.ui

import android.content.res.Configuration
import android.graphics.Rect
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Read strings from the live Activity. Instrumentation's target Context can
            // retain a different locale configuration on API 26 after tests switch locale.
            lateinit var newConnection: String
            lateinit var save: String
            lateinit var hostLabel: String
            lateinit var portLabel: String
            lateinit var usernameLabel: String
            lateinit var requiredError: String
            var portRangeError = ""
            scenario.onActivity { activity ->
                newConnection = activity.getString(R.string.home_new_connection)
                save = activity.getString(R.string.save)
                hostLabel = activity.getString(R.string.field_host)
                portLabel = activity.getString(R.string.field_port)
                usernameLabel = activity.getString(R.string.field_username)
                requiredError = activity.getString(R.string.err_host_required)
                portRangeError = activity.getString(R.string.err_port_range)
            }

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

            // Compose names the OutlinedTextField wrapper, but the platform EditText
            // child does not inherit that description on API 26/36. Tap each named
            // wrapper and edit the single focused platform editor; validation and
            // durable-save assertions below verify the resulting form state.
            setFieldText(hostLabel, savedHost)
            setFieldText(usernameLabel, "tester")
            setFieldText(portLabel, "0")
            dismissImeIfVisible()
            // The form scrolls inside the sheet. API 26 may omit offscreen field values
            // from the app accessibility root even though Compose retains them. The
            // subsequent save and port-range assertion are the authoritative model check.
            clickWhenAvailable(By.text(save), "Save after invalid port")
            assertTrue(
                "Expected port validation message '$portRangeError' after entering port 0; " +
                    "visibleAppText=${visibleAppText()}",
                device.wait(Until.hasObject(By.text(portRangeError)), TIMEOUT_MS),
            )
            assertTrue(app.hosts.hosts().none { host -> host.host == savedHost })

            setFieldText(portLabel, "22")
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

    private fun setFieldText(label: String, value: String) {
        // Each field focus opens the IME and reduces the sheet viewport. Dismiss it
        // before looking for the next field, then scroll the actual form viewport if
        // the named field is below the fold. Compose exposes the named wrapper
        // separately from its platform EditText child.
        dismissImeIfVisible()
        val visibleField = findFormField(label)
        if (visibleField == null) {
            throw AssertionError("Named field '$label' was not exposed after scrolling the host form")
        }
        visibleField.recycle()
        // The accessibility edit action belongs to the named field even when
        // Compose moves its visual bounds during an IME or sheet relayout.
        val namedNode = AppWindowAccessibility.find {
            it.contentDescription?.toString() == label
        }
        if (namedNode != null) {
            val accepted = try {
                runCatching { AppWindowAccessibility.setText(namedNode, value) }.getOrDefault(false)
            } finally { namedNode.recycle() }
            if (accepted) {
                device.waitForIdle()
                return
            }
        }
        // On API26 Preview the wrapper's visible center can map to the adjacent
        // Host field after Compose and UiAutomator disagree about window insets.
        // Prefer the editable descendant scoped to the named field: writing Port
        // must never silently replace Host while still satisfying a focus check.
        val scopedEditor = runCatching {
            device.findObject(By.desc(label))?.findObject(By.clazz("android.widget.EditText"))
        }.getOrNull()
        if (scopedEditor != null) {
            if (runCatching { scopedEditor.setText(value); true }.getOrDefault(false)) {
                device.waitForIdle()
                return
            }
        }
        val visibleBounds = runCatching {
            device.findObject(By.desc(label))?.visibleBounds
        }.getOrNull() ?: throw AssertionError(
            "Named field '$label' became stale before it could be edited; appTree=${AppWindowAccessibility.snapshot()}",
        )
        assertTrue(
            "Named field '$label' must have a usable visible target: visible=$visibleBounds",
            visibleBounds.width() >= 48 * instrumentation.targetContext.resources.displayMetrics.density &&
                visibleBounds.height() >= 48 * instrumentation.targetContext.resources.displayMetrics.density,
        )
        assertTrue(
            "Touching named field '$label' was rejected",
            device.click(visibleBounds.centerX(), visibleBounds.centerY()),
        )
        val focusedEditors = device.wait(
            Until.findObjects(By.clazz("android.widget.EditText").focused(true)),
            TIMEOUT_MS,
        ) ?: emptyList()
        if (focusedEditors.size != 1) throw AssertionError(
            "Tapping named field '$label' should focus exactly one editable node; " +
                "focusedEditables=${focusedEditors.map { it.contentDescription to it.text }}",
        )
        // Focusing the editor opens the IME and can move the sheet. Compose may
        // also replace the named wrapper with the focused text-field semantics node
        // while the IME relayout is in flight, so do not require the pre-focus
        // wrapper's contentDescription to survive that transition. The test's
        // contract is that the tapped field owns the focused editor and accepts text.
        device.waitForIdle()
        val settledEditors = device.wait(
            Until.findObjects(By.clazz("android.widget.EditText").focused(true)),
            TIMEOUT_MS,
        ) ?: emptyList()
        if (settledEditors.size != 1) throw AssertionError(
            "Exactly one editable node must remain focused after the field layout settles; " +
                "focusedEditables=${settledEditors.map { it.contentDescription to it.text }}",
        )
        val editor = settledEditors.single()
        val editorBounds = editor.visibleBounds
        assertTrue(
            "Focused editor for '$label' must remain visible after IME relayout; " +
                "editorBounds=$editorBounds screen=${device.displayWidth}x${device.displayHeight}",
            editorBounds.width() > 0 && editorBounds.height() > 0 &&
                Rect.intersects(editorBounds, Rect(0, 0, device.displayWidth, device.displayHeight)),
        )
        editor.setText(value)
        device.waitForIdle()
        // The later validation/save assertions verify the form model, which is the
        // behavior this regression protects. API 26 exposes stale text snapshots for
        // Compose BasicTextField even after ACTION_SET_TEXT has reached its callback.
    }

    private fun findFormField(label: String): androidx.test.uiautomator.UiObject2? {
        val deadline = android.os.SystemClock.uptimeMillis() + TIMEOUT_MS
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            device.waitForIdle()
            val field = device.findObject(By.desc(label))
            if (field != null) {
                val visible = runCatching { field.visibleBounds }.getOrNull()
                val appNode = AppWindowAccessibility.find {
                    it.contentDescription?.toString() == label
                }
                val full = appNode?.let { AppWindowAccessibility.bounds(it) }
                appNode?.recycle()
                if (visible != null && full != null && visible.width() == full.width() &&
                    visible.height() == full.height() && visible.height() >= 32) return field
                field.recycle()
            }

            val scrollNode = AppWindowAccessibility.find {
                if (!it.isScrollable) return@find false
                val bounds = AppWindowAccessibility.bounds(it)
                bounds.height() > bounds.width()
            }
            if (scrollNode != null) {
                val bounds = AppWindowAccessibility.bounds(scrollNode)
                scrollNode.recycle()
                // A semantic page scroll can move the form past a short field or leave
                // stale Compose bounds. Move a fraction of the live viewport per step.
                val x = bounds.centerX().coerceIn(24, device.displayWidth - 24)
                val step = (bounds.height() * 0.20f).toInt().coerceAtLeast(48)
                val centerY = bounds.centerY()
                device.swipe(x, centerY + step / 2, x, centerY - step / 2, 350)
            } else {
                val x = (device.displayWidth * 0.75f).toInt()
                device.swipe(x, (device.displayHeight * 0.72f).toInt(), x, (device.displayHeight * 0.38f).toInt(), 350)
            }
        }
        return device.findObject(By.desc(label))
    }

    private fun visibleAppText(): String {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return "<no active root>"
        val values = mutableListOf<String>()
        fun collect(node: android.view.accessibility.AccessibilityNodeInfo) {
            node.text?.toString()?.takeIf(String::isNotBlank)?.let(values::add)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    collect(child)
                } finally {
                    child.recycle()
                }
            }
        }
        return try {
            collect(root)
            values.distinct().joinToString(" | ")
        } finally {
            root.recycle()
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
