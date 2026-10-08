package app.terminalssh.secure.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
        // Keep the GMS "Remote Copy" overlay from stealing focus if GMS restarts mid-run.
        device.executeShellCommand("settings delete secure nearby_sharing_component")
        dismissNearbyShareOverlay()
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
            openTerminalTab(terminalTab, keyboardAction)

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
            device.waitForIdle()
            assertAppDescVisible(keyboardAction)
            // Drive the IME up with the app's own action, retrying: right after the
            // AndroidView is laid out at the new size the editor may not hold view focus
            // yet, so a single tap can be dropped and leave the keyboard down.
            ensureImeVisible(scenario, keyboardAction)
        }
    }

    @Test
    fun keyboardActionFocusesTerminalWhenHardwareKeyboardSuppressesIme() {
        writeSecureSetting("show_ime_with_hard_keyboard", "0")
        app.sessions.add(idleSession(id = "hardware-keyboard", title = "Hardware keyboard test"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val keyboardAction = instrumentation.targetContext.getString(R.string.show_keyboard)

            openTerminalTab(terminalTab, keyboardAction)

            // Only dismiss the IME when it is actually up; otherwise BACK would leave
            // the terminal and the toolbar key would no longer be reachable.
            if (waitForIme(scenario, visible = true)) {
                device.pressBack()
                assertTrue(waitForIme(scenario, visible = false))
            }
            clickAppDesc(keyboardAction)

            // IME visibility after an explicit app request varies by Android version even
            // when this hardware-keyboard preference is disabled. The app-controlled
            // invariant is that termlib's text editor receives focus for physical keys.
            assertTrue(focusedViewSummary(scenario), waitForFocusedTextEditor(scenario))

            writeSecureSetting("show_ime_with_hard_keyboard", "1")
            clickAppDesc(keyboardAction)
            assertTrue(waitForIme(scenario, visible = true))
        }
    }

    @Test
    fun toolbarReservesTouchTargetsAboveImeInPortraitAndLandscape() {
        app.sessions.add(idleSession(id = "toolbar-measurement", title = "Toolbar measurement"))
        val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
        val keyboardAction = instrumentation.targetContext.getString(R.string.show_keyboard)
        // Leftover rotation from a crashed earlier run would skew this measurement.
        device.setOrientationNatural()
        device.waitForIdle()
        val baseSize = device.executeShellCommand("wm size")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // Walk the application windows instead of By.* selectors: By follows the
                // a11y active window, which a SystemUI/launcher ANR dialog can steal; the
                // walk reads every application window directly.
                // Sanity: the toolbar row is reachable and its keys are touch-sized in the
                // plain terminal screen layout.
                openTerminalTab(terminalTab, keyboardAction)
                assertToolbarTouchTarget()

                // Measure a real toolbar touch target above the IME after driving the
                // IME up with the app's own toolbar action (a system BACK is racy: it
                // exits the terminal if the keyboard was still opening). Ctrl (the first
                // modifier key) stays in the tree in portrait; in landscape this
                // emulator stops surfacing the toolbar's LazyRow children entirely once
                // the IME is visible (its scrollable band vanishes too), so fall back to
                // the keyboard toggle key, which shares the toolbar row's exact top edge
                // and therefor the row's IME clearance.
                // A consistent snapshot of the toolbar's key bounds, the decor's IME
                // inset and the IME window's on-screen bounds.
                data class ClearanceSnapshot(
                    val bar: Rect?,
                    val imeInset: Int,
                    val imeWindowTop: Int,
                    val decorTop: Int,
                    val decorH: Int,
                )

                fun sampleClearance(): ClearanceSnapshot {
                    val bar = waitForToolbarCtrlBounds(within = 1_500)
                        ?: waitForAppDesc(keyboardAction)?.let(::bounds)
                    var ime = 0
                    var decorTop = 0
                    var decorH = 0
                    scenario.onActivity { activity ->
                        val insets = WindowInsetsCompat.toWindowInsetsCompat(
                            activity.window.decorView.rootWindowInsets,
                        )
                        ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        decorH = activity.window.decorView.height
                        val loc = IntArray(2)
                        activity.window.decorView.getLocationOnScreen(loc)
                        decorTop = loc[1]
                    }
                    return ClearanceSnapshot(bar, ime, waitForImeWindowTop() ?: -1, decorTop, decorH)
                }

                fun verifyToolbarAboveIme() {
                    ensureImeVisible(scenario, keyboardAction)
                    device.waitForIdle()
                    // Read the toolbar, the decor inset and the IME window from one
                    // consistent moment, then repeat until two consecutive samples agree
                    // on all of them. The soft keyboard animates and this emulator's
                    // reported IME height drifts while it settles; comparing a toolbar
                    // position against an inset captured a moment later would look like
                    // an overhang even when the layout is correct.
                    var settled = sampleClearance()
                    val settleDeadline = SystemClock.uptimeMillis() + 10_000
                    while (SystemClock.uptimeMillis() < settleDeadline) {
                        SystemClock.sleep(400)
                        val fresh = sampleClearance()
                        val barStable = settled.bar != null && fresh.bar != null &&
                            settled.bar.top == fresh.bar.top && settled.bar.bottom == fresh.bar.bottom
                        val imeStable = settled.imeInset == fresh.imeInset && fresh.imeInset > 0
                        settled = fresh
                        if (barStable && imeStable) break
                    }
                    val bar = settled.bar
                    assertTrue("Toolbar touch target must be discoverable for IME clearance", bar != null)
                    val minimumPx = 48 * instrumentation.targetContext.resources.displayMetrics.density
                    assertTrue("Toolbar touch height must be at least 48dp", bar!!.height() + 1 >= minimumPx)
                    assertTrue("Toolbar touch width must be at least 48dp", bar.width() + 1 >= minimumPx)
                    // The IME window's real bounds are the keyboard edge a user sees;
                    // fall back to the decor inset only when the a11y tree does not
                    // surface the input-method window on this device.
                    val insetTop = settled.decorTop + settled.decorH - settled.imeInset
                    val keyboardTop =
                        if (settled.imeWindowTop in 1 until (settled.decorTop + settled.decorH)) {
                            settled.imeWindowTop
                        } else {
                            insetTop
                        }
                    assertTrue("Toolbar must sit above IME", bar.bottom <= keyboardTop)
                }

                verifyToolbarAboveIme()
                device.setOrientationLeft()
                device.waitForIdle()
                verifyToolbarAboveIme()
            }
        } finally {
            try { device.setOrientationNatural() } catch (t: Throwable) {}
            device.waitForIdle()
            val restore = wmSizeResetCommand(baseSize)
            device.executeShellCommand(restore)
            device.waitForIdle()
        }
    }

    private fun assertToolbarTouchTarget() {
        val bounds = waitForToolbarCtrlBounds()
        assertTrue("Ctrl key must be discoverable in the app window tree", bounds != null)
        val minimumPx = 48 * instrumentation.targetContext.resources.displayMetrics.density
        assertTrue("Ctrl touch height must be at least 48dp", bounds!!.height() + 1 >= minimumPx)
        assertTrue("Ctrl touch width must be at least 48dp", bounds.width() + 1 >= minimumPx)
    }

    /**
     * Bounds of the toolbar Ctrl key, found by description inside the application
     * windows. UiAutomator's selection filters follow the accessibility *active*
     * window, which the soft keyboard can own while the terminal editor is focused;
     * walking the window tree directly keeps the toolbar reachable no matter which
     * window is active, the same way a screen reader reads the whole screen.
     */
    private fun waitForToolbarCtrlBounds(within: Long = UI_TIMEOUT_MS): Rect? {
        val deadline = SystemClock.uptimeMillis() + within
        while (SystemClock.uptimeMillis() < deadline) {
            findAppNode { n ->
                n.contentDescription == "Ctrl" && bounds(n).let { it.height() in 40..200 }
            }?.let { n ->
                return bounds(n)
            }
            SystemClock.sleep(250)
        }
        return null
    }

    /**
     * Bounds of the toolbar scrollable row, found inside the application windows.
     * The toolbar is the only app-window scrollable on the terminal screen; its
     * height is well under a finger target, unlike any full-screen scrollable.
     */
    private fun waitForToolbarBand(): Rect? {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            findAppNode { n ->
                n.isScrollable && bounds(n).let { it.height() <= 160 && it.width() < 10_000 }
            }?.let { n ->
                val out = bounds(n)
                if (out.height() > 0 && out.width() > 0) return out
            }
            SystemClock.sleep(250)
        }
        return null
    }

    private fun bounds(n: AccessibilityNodeInfo): Rect {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r
    }

    /** Finds [desc] across the application windows, retrying until [within] elapses. */
    private fun waitForAppDesc(desc: String, within: Long = UI_TIMEOUT_MS): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + within
        while (SystemClock.uptimeMillis() < deadline) {
            findAppNode { it.contentDescription == desc }?.let { return it }
            SystemClock.sleep(250)
        }
        return null
    }

    /** Asserts a node carrying [desc] appears in the application windows in time. */
    private fun assertAppDescVisible(desc: String) {
        if (waitForAppDesc(desc) == null) {
            android.util.Log.i("KEYTREE", "missing \"$desc\": ${appWindowTree()}")
        }
        assertTrue("Node with description \"$desc\" must be reachable", waitForAppDesc(desc) != null)
    }

    /** Text/description inventory of every application window, for failure triage. */
    private fun appWindowTree(): String {
        val sb = StringBuilder()
        try {
            for (window in instrumentation.uiAutomation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                appendNode(root, sb)
            }
        } catch (t: Throwable) {
            sb.append("err=").append(t)
        }
        return sb.toString()
    }

    private fun appendNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrEmpty() || !desc.isNullOrEmpty()) {
            sb.append('[').append(text.orEmpty()).append('|').append(desc.orEmpty()).append(']')
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { appendNode(it, sb) }
        }
    }

    /** Finds [text] (exposed as text or description) across the application windows. */
    private fun waitForAppText(text: String, within: Long = UI_TIMEOUT_MS): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + within
        while (SystemClock.uptimeMillis() < deadline) {
            // Keep By.text's semantics: compare the CharSequence CONTENT, not identity.
            // Compose exposes labels as SpannedString and String.equals(SpannedString)
            // is false, so a raw == on n.text misses every Compose tab label.
            findAppNode { n ->
                (n.text?.toString() == text) || (n.contentDescription?.toString() == text)
            }?.let { return it }
            SystemClock.sleep(250)
        }
        return null
    }

    /** Taps the center of the first node exposing [text] in the application windows. */
    private fun clickAppText(text: String) {
        val node = waitForAppText(text)
        assertTrue("Node with text \"$text\" must be reachable", node != null)
        val b = bounds(node!!)
        assertTrue("Node with text \"$text\" must be visible", b.width() > 0 && b.height() > 0)
        device.click(b.centerX(), b.centerY())
    }

    /**
     * Selects the terminal tab, retrying the tap until a node unique to the terminal
     * screen ([terminalMarker]) is in the a11y tree. A single tap can land before
     * Compose has finished laying the navigation bar out — notably on a cold start
     * right after the previous scenario is torn down — which otherwise leaves the app
     * on Hosts and stalls every later assertion. Retrying the tap until the terminal
     * screen is actually up makes the class independent of that launch race.
     */
    private fun openTerminalTab(terminalTab: String, terminalMarker: String) {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
        var attempts = 0
        while (SystemClock.uptimeMillis() < deadline) {
            runCatching { clickAppText(terminalTab) }
            if (waitForAppDesc(terminalMarker, within = 8_000) != null) return
            // A system overlay (e.g. the GMS Remote Copy sheet) can cover the app and
            // swallow the tap; clear it and try again rather than waiting out the clock.
            if (++attempts % 2 == 0) dismissNearbyShareOverlay()
        }
        assertTrue(
            "Selecting the terminal tab must reveal \"$terminalMarker\"",
            waitForAppDesc(terminalMarker) != null,
        )
    }

    /** Taps the center of the first node carrying [desc] in the application windows. */
    private fun clickAppDesc(desc: String) {
        val node = waitForAppDesc(desc)
        assertTrue("Node with description \"$desc\" must be reachable", node != null)
        val b = bounds(node!!)
        assertTrue("Node with description \"$desc\" must be visible", b.width() > 0 && b.height() > 0)
        device.click(b.centerX(), b.centerY())
    }

    private fun findAppNode(
        attempts: Int = 10,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        // A11y trees on this emulator refresh constantly while the terminal renders,
        // so a window snapshot taken mid-walk can throw or come back empty. Retry
        // with short pauses. Walk only application windows: the soft keyboard is a
        // separate window whose rows would pollute toolbar and key lookups.
        for (i in 0 until attempts) {
            try {
                for (window in instrumentation.uiAutomation.windows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    val root = window.root ?: continue
                    findDescendant(root, predicate)?.let { return it }
                }
            } catch (t: Throwable) {
                // A11y tree changed mid-walk; retry the snapshot.
            }
            SystemClock.sleep(100)
        }
        return null
    }

    private fun findDescendant(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = findDescendant(child, predicate) ?: continue
            return hit
        }
        return null
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

            openTerminalTab(terminalTab, firstClose)
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

        ActivityScenario.launch(MainActivity::class.java).use {
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)

            // Adding a session makes it the active one immediately (SessionRegistry.add).
            app.sessions.add(idleSession(id = "first-selectable-session", title = firstTitle))
            openTerminalTab(terminalTab, firstTitle)
            val firstSession = device.wait(Until.findObject(By.desc(firstTitle)), UI_TIMEOUT_MS)
            assertTrue(firstSession.isSelected)

            app.sessions.add(idleSession(id = "second-selectable-session", title = secondTitle))
            assertTrue(device.wait(Until.hasObject(By.desc(secondTitle)), UI_TIMEOUT_MS))
            assertFalse(device.wait(Until.findObject(By.desc(firstTitle)), UI_TIMEOUT_MS).isSelected)
            assertTrue(device.wait(Until.findObject(By.desc(secondTitle)), UI_TIMEOUT_MS).isSelected)

            // Tapping the other tab moves the selection back to it.
            device.wait(Until.findObject(By.desc(firstTitle)), UI_TIMEOUT_MS).click()
            assertTrue(device.wait(Until.findObject(By.desc(firstTitle)), UI_TIMEOUT_MS).isSelected)
            assertFalse(device.wait(Until.findObject(By.desc(secondTitle)), UI_TIMEOUT_MS).isSelected)
        }
    }

    @Test
    fun multilinePasteRequiresConfirmationAndCancelDoesNotPaste() {
        app.sessions.add(idleSession(id = "paste-confirmation", title = "Paste test"))
        setClipboardWithoutNearbyShareOverlay("test", "first\nsecond")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val dialogTitle = instrumentation.targetContext.getString(R.string.paste_confirm_title, 2)
            val cancel = instrumentation.targetContext.getString(R.string.cancel)

            // The terminal input can pull up the soft keyboard and a system clipboard
            // panel the moment it shows, which steals the a11y active window. Wait for
            // the terminal toolbar (unique to the terminal screen) so the paste request
            // is not fired while the nav change is still settling.
            val showKeyboard = instrumentation.targetContext.getString(R.string.show_keyboard)
            openTerminalTab(terminalTab, showKeyboard)
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
        setClipboardWithoutNearbyShareOverlay("test", "first\rsecond")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val dialogTitle = instrumentation.targetContext.getString(R.string.paste_confirm_title, 2)
            val cancel = instrumentation.targetContext.getString(R.string.cancel)

            // See multilinePaste...: only request the paste once the terminal screen is
            // actually showing its toolbar.
            val showKeyboard = instrumentation.targetContext.getString(R.string.show_keyboard)
            openTerminalTab(terminalTab, showKeyboard)
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
            openTerminalTab(terminalTab, "Ctrl")

            // Essential terminal modifiers are deliberately first in the phone viewport.
            assertTrue("Ctrl must be visible without scrolling", findAppNode { it.contentDescription == "Ctrl" } != null)
            assertTrue("Alt must be visible without scrolling", findAppNode { it.contentDescription == "Alt" } != null)
            assertTrue("Shift must be visible without scrolling", findAppNode { it.contentDescription == "Shift" } != null)

            // Compose splits a toggle key into three nodes: the stateful 48dp control,
            // a full-size child carrying the announced description, and a bare child
            // with the printed label. The checkable box is the control the user taps.
            val ctrl = modifierControl("Ctrl")
            val alt = modifierControl("Alt")
            val shift = modifierControl("Shift")
            assertTrue(ctrl.isCheckable)
            assertFalse(ctrl.isChecked)
            assertTrue(alt.isCheckable)
            assertFalse(alt.isChecked)
            assertTrue(shift.isCheckable)
            assertFalse(shift.isChecked)

            ctrl.click()
            assertTrue(modifierControl("Ctrl").isChecked)

            alt.click()
            assertTrue(modifierControl("Ctrl").isChecked)
            assertTrue(modifierControl("Alt").isChecked)

            shift.click()
            assertTrue(modifierControl("Ctrl").isChecked)
            assertTrue(modifierControl("Alt").isChecked)
            assertTrue(modifierControl("Shift").isChecked)

            clickAppDesc("Tab")
            assertFalse(modifierControl("Ctrl").isChecked)
            assertFalse(modifierControl("Alt").isChecked)
            assertFalse(modifierControl("Shift").isChecked)
            assertTrue(focusedViewSummary(scenario), waitForFocusedTextEditor(scenario))
        }
    }

    /**
     * Returns the 48dp control node of a modifier key. The toggleable modifier keeps
     * state on the control and pushes the announced description onto a full-size
     * descendant, so select the checkable box that carries a descendant with the
     * key's name.
     */
    private fun modifierControl(name: String): UiObject2 =
        device.wait(
            Until.findObject(By.checkable(true).hasDescendant(By.desc(name))),
            UI_TIMEOUT_MS,
        )

    @Test
    fun symbolicToolbarKeysHaveReadableActionNames() {
        app.sessions.add(idleSession(id = "toolbar-semantics", title = "Toolbar test"))

        val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
        val interrupt = instrumentation.targetContext.getString(R.string.terminal_key_interrupt)
        val endOfInput = instrumentation.targetContext.getString(R.string.terminal_key_eof)

        // Narrow, single-row phone layout: the toolbar must expose scroll semantics as
        // the layout's only vertically-small scrollable. Open the terminal first, then
        // (if needed) re-fit the very same window wide below.
        ActivityScenario.launch(MainActivity::class.java).use {
            openTerminalTab(terminalTab, instrumentation.targetContext.getString(R.string.show_keyboard))
            assertTrue("Terminal toolbar must expose scroll semantics", waitForToolbarBand() != null)

            fun assertReadableSymbolicNames() {
                assertTrue(
                    "Interrupt key must have a readable action name in the toolbar semantics tree",
                    findAppNode { it.contentDescription == interrupt } != null,
                )
                assertTrue(
                    "End-of-input key must have a readable action name in the toolbar semantics tree",
                    findAppNode { it.contentDescription == endOfInput } != null,
                )
            }

            // The symbolic keys must be discoverable in the a11y tree carrying their
            // readable action names. This emulator cannot drive the toolbar's LazyRow
            // with synthetic swipes or a11y scroll actions (UiObject2.scroll and
            // ACTION_SCROLL_FORWARD report success without moving the row), so on the
            // narrow phone layout the tail keys never compose. MainActivity handles the
            // screenSize change in place, so re-fit the same window wide where the
            // toolbar's two-row layout composes every key, verify, then restore.
            if (findAppNode { it.contentDescription == interrupt } == null ||
                findAppNode { it.contentDescription == endOfInput } == null
            ) {
                refitWideForKeyboardLayout { assertReadableSymbolicNames() }
            } else {
                assertReadableSymbolicNames()
            }
        }
    }

    /**
     * Re-fits the current window wide (the activity keeps its state; MainActivity
     * declares screenSize in configChanges) until the toolbar's two-row layout composes
     * every key into the a11y tree, runs [verify] against the wide layout, and restores
     * the previous display geometry and IME preference before returning. The IME is
     * kept away so the toolbar does not collapse back to a single scrollable row.
     */
    private fun refitWideForKeyboardLayout(verify: () -> Unit) {
        val sizeBefore = device.executeShellCommand("wm size")
        val imeBefore = readSecureSetting("show_ime_with_hard_keyboard")
        try {
            writeSecureSetting("show_ime_with_hard_keyboard", "0")
            device.executeShellCommand("wm size 1180x500")
            device.waitForIdle()
            SystemClock.sleep(3000)
            verify()
        } finally {
            writeSecureSetting("show_ime_with_hard_keyboard", imeBefore)
            device.executeShellCommand(wmSizeResetCommand(sizeBefore))
            device.waitForIdle()
        }
    }

    /** "wm size reset" unless the device already carried a size override worth keeping. */
    private fun wmSizeResetCommand(sizeOutput: String): String {
        val override = sizeOutput.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Override size:", ignoreCase = true) }
        return override?.substringAfter(':')?.trim()?.let { "wm size $it" } ?: "wm size reset"
    }

    /**
     * The toolbar keyboard key toggles the IME. Click it until the input method is
     * actually visible, so measurements always run with the keyboard up.
     */
    private fun ensureImeVisible(scenario: ActivityScenario<MainActivity>, keyboardAction: String) {
        repeat(3) {
            if (waitForIme(scenario, visible = true)) return
            // The IME only attaches to termlib's editor when that editor holds view
            // focus. On flaky slow launches the automatic focus request can be dropped
            // even though the window has focus, so tap the terminal surface the way a
            // user would to re-establish it before driving the toolbar action.
            if (!waitForFocusedTextEditor(scenario)) {
                device.click(device.displayWidth / 2, (device.displayHeight * 0.45).toInt())
                device.waitForIdle()
                val focusDeadline = SystemClock.uptimeMillis() + 4_000
                while (SystemClock.uptimeMillis() < focusDeadline &&
                    !waitForFocusedTextEditor(scenario)
                ) {
                    SystemClock.sleep(200)
                }
            }
            clickAppDesc(keyboardAction)
            if (waitForIme(scenario, visible = true)) return
        }
        assertTrue("IME must be visible for toolbar measurement", waitForIme(scenario, visible = true))
    }

    private fun dismissAndReopenKeyboard(
        scenario: ActivityScenario<MainActivity>,
        keyboardAction: String,
    ) {
        // BACK only dismisses a visible IME. With none up it would leave the terminal
        // entirely, so make sure the keyboard is actually showing before dismissing it.
        ensureImeVisible(scenario, keyboardAction)
        device.pressBack()
        assertTrue(waitForIme(scenario, visible = false))
        clickAppDesc(keyboardAction)
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

    /**
     * Top edge (screen Y) of the on-screen soft-keyboard window, or null when the
     * accessibility tree does not surface an [AccessibilityWindowInfo.TYPE_INPUT_METHOD]
     * window on this device. This is the keyboard edge a user actually sees, which is
     * more reliable than the decor's reported IME inset on emulators whose inset drifts.
     */
    private fun waitForImeWindowTop(within: Long = 1_500): Int? {
        val deadline = SystemClock.uptimeMillis() + within
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                for (window in instrumentation.uiAutomation.windows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    val bounds = Rect()
                    window.getBoundsInScreen(bounds)
                    if (bounds.height() > 0) return bounds.top
                }
            } catch (t: Throwable) {
                // Window list changed mid-walk; retry.
            }
            SystemClock.sleep(100)
        }
        return null
    }

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

    /**
     * Writes [text] to the clipboard without letting the platform launch GMS's
     * "Nearby Share / Remote Copy" share sheet.
     *
     * Each clipboard write is intercepted by the system, which resolves the secure
     * setting `nearby_sharing_component` and starts that GMS activity. It takes the
     * foreground about two seconds later and drops the tab tap that follows, leaving
     * the app on Hosts and stalling the test. Deleting the setting immediately before
     * the write stops the interception; GMS only rewrites the setting when its own
     * process (re)starts, which the short window here avoids.
     */
    private fun setClipboardWithoutNearbyShareOverlay(label: String, text: String) {
        device.executeShellCommand("settings delete secure nearby_sharing_component")
        clipboardManager().setPrimaryClip(ClipData.newPlainText(label, text))
        // SystemUI caches the component and launches it regardless, ~2s later. Give it a
        // beat to appear, then kill GMS so the share sheet cannot cover the app and drop
        // the input that follows. The clipboard itself lives in the system service, so it
        // survives the GMS restart.
        SystemClock.sleep(2_500)
        device.executeShellCommand("am force-stop com.google.android.gms")
    }

    /** Kills a lingering Nearby Share sheet so it cannot cover the next test's app. */
    private fun dismissNearbyShareOverlay() {
        val dump = device.executeShellCommand("dumpsys activity activities")
        val top = dump.lineSequence().firstOrNull { it.contains("topResumedActivity") } ?: return
        if (top.contains("com.google.android.gms")) {
            device.executeShellCommand("am force-stop com.google.android.gms")
        }
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
