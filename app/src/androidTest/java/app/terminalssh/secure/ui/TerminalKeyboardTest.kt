package app.terminalssh.secure.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.view.View
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
import org.junit.Assert.assertEquals
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
    private var previousPrimaryClip: ClipData? = null
    private var previousHardwareKeyboardSetting = "0"
    private var previousPasteConfirmation = true
    private var previousToolbarProfile: String? = null

    @Before
    fun setUp() {
        app = instrumentation.targetContext.applicationContext as TerminalApp
        previousToolbarProfile = app.toolbar.state.value.activeProfileId
        instrumentation.runOnMainSync { previousPrimaryClip = clipboardManager().primaryClip }
        app.sessions.closeAll()
        dismissImeIfVisible()
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
        previousToolbarProfile?.let(app.toolbar::setActiveProfile)
        app.settings.confirmMultilinePaste = previousPasteConfirmation
        // ClipboardManager must be resolved on Main on API 26. Restore the prior
        // clip so test data does not overwrite a device user's clipboard. Android
        // only added a true clear operation in API 28; on older systems an originally
        // empty clipboard can only be approximated with an empty clip.
        instrumentation.runOnMainSync {
            val clipboard = clipboardManager()
            val previous = previousPrimaryClip
            if (previous != null) {
                clipboard.setPrimaryClip(previous)
            } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        writeSecureSetting("show_ime_with_hard_keyboard", previousHardwareKeyboardSetting)
    }

    @Test
    fun bottomNavigationTabsExposeLocalizedAccessibleNames() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = instrumentation.targetContext
            listOf(
                R.string.tab_hosts,
                R.string.tab_terminal,
                R.string.tab_files,
                R.string.tab_keys,
                R.string.tab_settings,
            ).forEach { id ->
                val label = context.getString(id)
                assertTrue("Localized navigation label '$label' must be visible", device.wait(Until.hasObject(By.text(label)), UI_TIMEOUT_MS))
            }
        }
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
                        ?: waitForAppDesc(keyboardAction)?.let { node ->
                            try { bounds(node) } finally { node.recycle() }
                        }
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
                n.contentDescription?.startsWith(
                    instrumentation.targetContext.getString(R.string.tcs_control_modifier),
                ) == true && bounds(n).let { it.height() in 40..200 }
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

    /** Waits for a settled actionable key node rather than a transient named layout node. */
    private fun waitForClickableAppDesc(desc: String, within: Long = UI_TIMEOUT_MS): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + within
        while (SystemClock.uptimeMillis() < deadline) {
            findAppNode { it.contentDescription == desc && it.isClickable }?.let { return it }
            SystemClock.sleep(100)
        }
        return null
    }

    /** Asserts a node carrying [desc] appears in the application windows in time. */
    private fun assertAppDescVisible(desc: String) {
        val node = waitForAppDesc(desc)
        if (node == null) {
            android.util.Log.i("KEYTREE", "missing \"$desc\": ${appWindowTree()}")
        }
        try {
            assertTrue("Node with description \"$desc\" must be reachable", node != null)
        } finally {
            node?.recycle()
        }
    }

    /** Text/description inventory of every application window, for failure triage. */
    private fun appWindowTree(): String {
        val sb = StringBuilder()
        try {
            for (window in instrumentation.uiAutomation.windows) {
                try {
                    try {
                        if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                        val root = window.root ?: continue
                        try {
                            appendNode(root, sb)
                        } finally {
                            root.recycle()
                        }
                    } catch (_: Throwable) {
                        // Ignore this stale window and continue with the remaining snapshot.
                    }
                } finally {
                    window.recycle()
                }
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
            val child = node.getChild(i) ?: continue
            try {
                appendNode(child, sb)
            } finally {
                child.recycle()
            }
        }
    }

    /** Taps the labeled item in the bottom dock, allowing the session-count badge to join its text. */
    private fun clickAppText(text: String) {
        val node = waitForNavigationTab(text)
        assertTrue("Navigation item \"$text\" must be reachable", node != null)
        try {
            val b = bounds(node!!)
            assertTrue("Navigation item \"$text\" must be visible", b.width() > 0 && b.height() > 0)
            device.click(b.centerX(), b.centerY())
        } finally {
            node?.recycle()
        }
    }

    private fun waitForNavigationTab(label: String, within: Long = UI_TIMEOUT_MS): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + within
        while (SystemClock.uptimeMillis() < deadline) {
            findAppNode { node ->
                val text = node.text?.toString()
                val description = node.contentDescription?.toString()
                val matchesLabel = listOfNotNull(text, description).any { value ->
                    value == label || value.startsWith("$label,") || value.startsWith("$label ")
                }
                if (!matchesLabel) return@findAppNode false
                val nodeBounds = bounds(node)
                // NavigationBarItem bounds are exported differently across Android
                // releases (API 26 can report window-relative coordinates). Match the
                // unique localized tab label and require a real visible target instead
                // of assuming its screen Y coordinate is below a fixed threshold.
                nodeBounds.width() > 0 && nodeBounds.height() > 0
            }?.let { return it }
            SystemClock.sleep(200)
        }
        return null
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
        var lastClickFailure: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            runCatching { clickAppText(terminalTab) }
                .onFailure { lastClickFailure = it.javaClass.simpleName }
            val marker = waitForAppDesc(terminalMarker, within = 8_000)
            if (marker != null) {
                marker.recycle()
                return
            }
            // A system overlay (e.g. the GMS Remote Copy sheet) can cover the app and
            // swallow the tap; clear it and try again rather than waiting out the clock.
            if (++attempts % 2 == 0) dismissNearbyShareOverlay()
        }
        assertTrue(
            "Selecting the terminal tab must reveal \"$terminalMarker\"; " +
                "lastClickFailure=$lastClickFailure; ${safeNavigationSnapshot()}",
            false,
        )
    }

    /** A previous instrumentation case can leave the system IME open after its Activity exits. */
    private fun dismissImeIfVisible() {
        device.waitForIdle()
        if (isImeWindowVisible()) {
            device.pressBack()
            repeat(8) {
                device.waitForIdle()
                if (!isImeWindowVisible()) return
                SystemClock.sleep(250)
            }
        }
    }

    private fun isImeWindowVisible(): Boolean {
        var imeVisible = false
        instrumentation.uiAutomation.windows.forEach { window ->
            try {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) imeVisible = true
            } finally {
                window.recycle()
            }
        }
        return imeVisible
    }

    /** Reports only fixed UI anchors and geometry; never dumps terminal or host content to CI. */
    private fun safeNavigationSnapshot(): String {
        val context = instrumentation.targetContext
        val anchors = listOf(
            context.getString(R.string.tab_hosts),
            context.getString(R.string.tab_terminal),
            context.getString(R.string.show_keyboard),
            context.getString(R.string.tcs_control_modifier),
            "Alt",
            "Shift",
        )
        val found = anchors.mapNotNull { anchor ->
            findAppNode { node ->
                node.text?.toString() == anchor || node.contentDescription?.toString()?.startsWith(anchor) == true
            }?.let { node ->
                try {
                    val bounds = bounds(node)
                    "$anchor:${node.className}@${bounds.flattenToString()}"
                } finally {
                    node.recycle()
                }
            }
        }
        val windows = instrumentation.uiAutomation.windows
        var applicationWindowCount = 0
        windows.forEach { window ->
            try {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) applicationWindowCount++
            } catch (_: Throwable) {
                // A stale window does not change the diagnostic anchor summary.
            } finally {
                window.recycle()
            }
        }
        return "applicationWindows=$applicationWindowCount; anchors=$found"
    }

    /** Taps the center of the first node carrying [desc] in the application windows. */
    private fun clickAppDesc(desc: String) {
        val node = waitForAppDesc(desc)
        assertTrue("Node with description \"$desc\" must be reachable", node != null)
        try {
            val b = bounds(node!!)
            assertTrue("Node with description \"$desc\" must be visible", b.width() > 0 && b.height() > 0)
            device.click(b.centerX(), b.centerY())
        } finally {
            node?.recycle()
        }
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
                    try {
                        try {
                            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                            val root = window.root ?: continue
                            try {
                                findDescendant(root, predicate)?.let { return it }
                            } finally {
                                root.recycle()
                            }
                        } catch (_: Throwable) {
                            // Continue after a stale window or tree snapshot.
                        }
                    } finally {
                        window.recycle()
                    }
                }
            } catch (t: Throwable) {
                // Window enumeration itself changed; retry the snapshot.
            }
            // Some Android releases expose a transient or incomplete `windows`
            // snapshot during Compose semantics updates. The active root remains
            // useful as a fallback, while the predicate still filters out IME nodes
            // for callers that are looking for app-specific controls.
            try {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root != null) {
                    try {
                        findDescendant(root, predicate)?.let { return it }
                    } finally {
                        root.recycle()
                    }
                }
            } catch (_: Throwable) {
                // Retry after the next short delay if the active root is stale.
            }
            SystemClock.sleep(100)
        }
        return null
    }

    private fun findDescendant(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                findDescendant(child, predicate)?.let { return it }
            } finally {
                child.recycle()
            }
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

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val dialogTitle = instrumentation.targetContext.getString(R.string.paste_confirm_title, 2)
            val cancel = instrumentation.targetContext.getString(R.string.cancel)

            // See multilinePaste...: only request the paste once the terminal screen is
            // actually showing its toolbar.
            val showKeyboard = instrumentation.targetContext.getString(R.string.show_keyboard)
            openTerminalTab(terminalTab, showKeyboard)
            // Populate the clipboard only after navigation has settled. On API 36 the
            // pre-launch clipboard notification can replace the app's accessibility
            // window before the tab tap; this keeps that system UI out of navigation.
            setClipboardWithoutNearbyShareOverlay("test", "first\rsecond")
            val terminalAfterClipboard = waitForAppDesc(showKeyboard)
            assertTrue("Terminal accessibility tree did not return after clipboard setup", terminalAfterClipboard != null)
            terminalAfterClipboard?.recycle()
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
        val session = idleSession(id = "modifier-semantics", title = "Modifier test")
        // User preferences survive instrumentation methods. Pin the preset so this
        // assertion always exercises the shipped shell layout, including its Tab key.
        app.toolbar.setActiveProfile("shell")
        app.sessions.add(session)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val controlName = instrumentation.targetContext.getString(R.string.tcs_control_modifier)
            val altName = instrumentation.targetContext.getString(R.string.tcs_alt_modifier)
            val shiftName = instrumentation.targetContext.getString(R.string.tcs_shift_modifier)
            val tabName = instrumentation.targetContext.getString(R.string.terminal_key_tab)
            val keyboardAction = instrumentation.targetContext.getString(R.string.show_keyboard)
            openTerminalTab(terminalTab, controlName)

            // Essential terminal modifiers are deliberately first in the phone viewport.
            assertTrue("Ctrl must be visible without scrolling", findAppNode { it.contentDescription == controlName } != null)
            assertTrue("Alt must be visible without scrolling", findAppNode { it.contentDescription == altName } != null)
            assertTrue("Shift must be visible without scrolling", findAppNode { it.contentDescription == shiftName } != null)

            // The accessible name, toggle state, and click action should be exposed
            // on the same checkable semantics node.
            val ctrl = modifierControl(controlName)
            val alt = modifierControl(altName)
            val shift = modifierControl(shiftName)
            assertTrue(ctrl.isCheckable)
            assertTrue("Ctrl's named semantics node must own its click action", ctrl.isClickable)
            assertFalse(ctrl.isChecked)
            assertTrue(alt.isCheckable)
            assertTrue("Alt's named semantics node must own its click action", alt.isClickable)
            assertFalse(alt.isChecked)
            assertTrue(shift.isCheckable)
            assertTrue("Shift's named semantics node must own its click action", shift.isClickable)
            assertFalse(shift.isChecked)

            ctrl.click()
            assertTrue("Ctrl state was not published after its accessibility click", awaitUiState(UI_TIMEOUT_MS) {
                modifierControl(controlName).isChecked
            })

            alt.click()
            assertTrue("Ctrl state changed while activating Alt", awaitUiState(UI_TIMEOUT_MS) {
                modifierControl(controlName).isChecked && modifierControl(altName).isChecked
            })

            shift.click()
            assertTrue("Modifier states were not all published after activating Shift", awaitUiState(UI_TIMEOUT_MS) {
                modifierControl(controlName).isChecked && modifierControl(altName).isChecked &&
                    modifierControl(shiftName).isChecked
            })

            // This method verifies modifier state and Tab dispatch. The API26
            // UiAutomator bridge accepts toolbar swipes without moving Compose's
            // compact LazyRow, so expose the same profile in its supported wide
            // two-row layout before testing Tab's actual accessibility action.
            // Compact reachability is tracked separately by the Snippets test.
            refitWideForKeyboardLayout(scenario) {
                val tabKey = waitForClickableAppDesc(tabName)
                assertTrue("Tab key must expose an accessibility click action", tabKey?.isClickable == true)
                try {
                    assertTrue("Tab key accessibility action was rejected", tabKey!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                } finally {
                    tabKey?.recycle()
                }
            }
            // First check the input controller itself, then check what accessibility
            // exposes. That separates dispatch/latch bugs from stale platform snapshots.
            assertTrue(
                "Tab did not consume one-shot modifiers; state=${session.terminalInput.modifiers.value}",
                awaitUiState(UI_TIMEOUT_MS) {
                    val current = session.terminalInput.modifiers.value
                    !current.ctrl && !current.alt && !current.shift
                },
            )
            val ctrlCleared = awaitUiState(UI_TIMEOUT_MS) {
                modifierIsChecked(controlName) == false
            }
            assertTrue("Ctrl semantics did not clear after Tab; tree=${if (ctrlCleared) "" else appWindowTree()}", ctrlCleared)
            val altCleared = awaitUiState(UI_TIMEOUT_MS) {
                modifierIsChecked(altName) == false
            }
            assertTrue("Alt semantics did not clear after Tab; tree=${if (altCleared) "" else appWindowTree()}", altCleared)
            val shiftCleared = awaitUiState(UI_TIMEOUT_MS) {
                modifierIsChecked(shiftName) == false
            }
            assertTrue("Shift semantics did not clear after Tab; tree=${if (shiftCleared) "" else appWindowTree()}", shiftCleared)
            assertTrue(focusedViewSummary(scenario), waitForFocusedTextEditor(scenario))
        }
    }

    private fun horizontalScrollableRowBounds(referenceY: Int): Rect? {
        val row = findAppNode {
            if (!it.isScrollable) return@findAppNode false
            val candidate = bounds(it)
            candidate.width() > candidate.height() * 2 &&
                kotlin.math.abs(candidate.centerY() - referenceY) < 120
        } ?: return null
        return try { bounds(row) } finally { row.recycle() }
    }

    /** Returns the checkable semantics node that exposes a modifier's current state. */
    private fun modifierControl(name: String): UiObject2 =
        device.wait(
            Until.findObject(By.checkable(true).descStartsWith(name)),
            UI_TIMEOUT_MS,
        ) ?: throw AssertionError("Modifier '$name' must expose one checkable accessible control")

    private fun modifierIsChecked(name: String): Boolean? =
        findAppNode { node ->
            node.isCheckable && node.contentDescription?.toString()?.startsWith(name) == true
        }?.let { node ->
            try {
                node.isChecked
            } finally {
                node.recycle()
            }
        }

    @Test
    fun symbolicToolbarKeysHaveReadableActionNames() {
        app.toolbar.setActiveProfile("shell")
        app.sessions.add(idleSession(id = "toolbar-semantics", title = "Toolbar test"))

        val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
        val interrupt = instrumentation.targetContext.getString(R.string.terminal_key_interrupt)
        val endOfInput = instrumentation.targetContext.getString(R.string.terminal_key_eof)
        val localizedSpecials = listOf(
            R.string.terminal_key_escape,
            R.string.terminal_key_tab,
            R.string.terminal_key_up,
            R.string.terminal_key_down,
            R.string.terminal_key_left,
            R.string.terminal_key_right,
        ).map { instrumentation.targetContext.getString(it) }

        // Narrow, single-row phone layout: the toolbar must expose scroll semantics as
        // the layout's only vertically-small scrollable. Open the terminal first, then
        // (if needed) re-fit the very same window wide below.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openTerminalTab(terminalTab, instrumentation.targetContext.getString(R.string.show_keyboard))
            assertTrue("Terminal toolbar must expose scroll semantics", waitForToolbarBand() != null)

            fun assertReadableSymbolicNames() {
                (listOf(interrupt, endOfInput) + localizedSpecials).forEach { name ->
                    val node = waitForAppDesc(name, within = UI_TIMEOUT_MS)
                    assertTrue(
                        if (node == null) "Toolbar action '$name' is missing; visibleToolbar=${appWindowTree()}"
                        else "Toolbar action '$name' must have a readable name in the accessibility tree",
                        node != null,
                    )
                    node?.recycle()
                }
            }

            // The symbolic keys must be discoverable in the a11y tree carrying their
            // readable action names. This emulator cannot drive the toolbar's LazyRow
            // with synthetic swipes or a11y scroll actions (UiObject2.scroll and
            // ACTION_SCROLL_FORWARD report success without moving the row), so on the
            // narrow phone layout the tail keys never compose. MainActivity handles the
            // screenSize change in place, so re-fit the same window wide where the
            // toolbar's two-row layout composes every key, verify, then restore.
            if (findAppNode { it.contentDescription == interrupt } == null ||
                findAppNode { it.contentDescription == endOfInput } == null ||
                localizedSpecials.any { name -> findAppNode { it.contentDescription == name } == null }
            ) {
                refitWideForKeyboardLayout(scenario) { assertReadableSymbolicNames() }
            } else {
                assertReadableSymbolicNames()
            }
        }
    }

    @Test
    fun editorCanAssignAndClearAProfileForOnlyTheActiveHost() {
        val session = idleSession(id = "host-toolbar-override", title = "Host profile test")
        app.sessions.add(session)
        val repository = app.toolbar
        // Keep the test independent of a profile choice left by an earlier method.
        repository.setActiveProfile("shell")
        val globalProfile = repository.state.value.activeProfileId
        val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
        val keyboardAction = instrumentation.targetContext.getString(R.string.show_keyboard)
        val applyLabel = instrumentation.targetContext.getString(R.string.tcs_host_profile_apply)
        val clearLabel = instrumentation.targetContext.getString(R.string.tcs_host_profile_clear)
        val targetProfile = repository.profileById("vim") ?: error("Built-in Vim profile is required")
        val profileHeading = instrumentation.targetContext.getString(R.string.tcs_profile)

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openTerminalTab(terminalTab, keyboardAction)
                // BACK is intercepted by the editor's BackHandler once its dialog is
                // open. Dismiss the IME first so that a cleanup BACK can never dismiss
                // the editor and make the following profile assertion ambiguous.
                if (waitForIme(scenario, visible = true)) {
                    device.pressBack()
                    assertTrue("IME should close before opening the toolbar editor", waitForIme(scenario, visible = false))
                }
                val keyboardKey = device.wait(Until.findObject(By.desc(keyboardAction)), UI_TIMEOUT_MS)
                    ?: throw AssertionError("Keyboard key is needed to reach the toolbar editor")
                keyboardKey.longClick()

                val editorOpened = device.wait(Until.hasObject(By.text(profileHeading)), UI_TIMEOUT_MS)
                assertTrue(
                    "Profile chooser must be present after opening the toolbar editor; " +
                        "appTree=${appWindowTree()}",
                    editorOpened,
                )
                assertTrue(
                    "Vim profile chip must be reachable by accessibility scrolling",
                    device.scrollUntilFullyVisible(
                        By.text(targetProfile.name),
                        maxAttempts = 16,
                        appPackage = instrumentation.targetContext.packageName,
                    ),
                )
                val profileChip = device.wait(Until.findObject(By.text(targetProfile.name)), UI_TIMEOUT_MS)
                    ?: throw AssertionError("Vim profile must be selectable in the editor")
                profileChip.click()
                assertTrue(
                    "Host-profile action must be scroll-reachable in the editor",
                    device.scrollUntilFullyVisible(
                        By.text(applyLabel),
                        maxAttempts = 16,
                        appPackage = instrumentation.targetContext.packageName,
                    ),
                )
                val apply = device.wait(Until.findObject(By.text(applyLabel)), UI_TIMEOUT_MS)
                    ?: throw AssertionError("Host profile action must be visible in an active SSH session")
                apply.click()

                assertTrue(awaitUiState(UI_TIMEOUT_MS) {
                    repository.state.value.hostOverrides[session.profile.id] == targetProfile.id
                })
                assertEquals(globalProfile, repository.state.value.activeProfileId)
                assertEquals(targetProfile.id, repository.effectiveProfile(session.profile.id).id)
                assertEquals(globalProfile, repository.effectiveProfile("another-host").id)

                device.wait(Until.findObject(By.text(clearLabel)), UI_TIMEOUT_MS)?.click()
                    ?: throw AssertionError("An active host override must be clearable")
                assertTrue(awaitUiState(UI_TIMEOUT_MS) {
                    session.profile.id !in repository.state.value.hostOverrides
                })
                assertEquals(globalProfile, repository.effectiveProfile(session.profile.id).id)
            }
        } finally {
            repository.setHostOverride(session.profile.id, null)
        }
    }

    private fun isFullyVisibleAccessibleText(text: String, referenceY: Int): Boolean {
        val viewport = horizontalScrollableRowBounds(referenceY) ?: return false
        val appNode = AppWindowAccessibility.find {
            it.text?.toString() == text && it.isClickable
        } ?: return false
        return try {
            val chipBounds = AppWindowAccessibility.bounds(appNode)
            viewport.contains(chipBounds) && appNode.isVisibleToUser && chipBounds.width() > 0 && chipBounds.height() > 0
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            false
        } finally {
            appNode.recycle()
        }
    }

    /**
     * Re-fits the current window wide (the activity keeps its state; MainActivity
     * declares screenSize in configChanges) until the toolbar's two-row layout composes
     * every key into the a11y tree, runs [verify] against the wide layout, and restores
     * the previous display geometry and IME preference before returning. The IME is
     * kept away so the toolbar does not collapse back to a single scrollable row.
     */
    private fun refitWideForKeyboardLayout(scenario: ActivityScenario<MainActivity>, verify: () -> Unit) {
        val sizeBefore = device.executeShellCommand("wm size")
        val imeBefore = readSecureSetting("show_ime_with_hard_keyboard")
        try {
            dismissImeIfVisible()
            writeSecureSetting("show_ime_with_hard_keyboard", "0")
            // `wm size` takes physical pixels while StudioToolbar switches to two rows
            // at 600dp. A fixed 1180px width is still compact on dense API 26 emulators.
            val density = instrumentation.targetContext.resources.displayMetrics.density
            val widthPx = (700f * density).toInt()
            // Android reports the shorter side as screenWidthDp in portrait. Keep
            // the emulated height longer than the requested 700dp wide viewport.
            val heightPx = (900f * density).toInt()
            device.executeShellCommand("wm size ${widthPx}x$heightPx")
            device.waitForIdle()
            SystemClock.sleep(3000)
            val expectedWidthDp = (widthPx / density).toInt()
            scenario.onActivity { activity ->
                assertTrue(
                    "Wide toolbar verification requires >=600dp, actual=${activity.resources.configuration.screenWidthDp}dp, " +
                        "requested=${expectedWidthDp}dp",
                    activity.resources.configuration.screenWidthDp >= 600,
                )
            }
            assertTrue("IME must be hidden for the two-row toolbar layout", !isImeWindowVisible())
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
    ): SshSession {
        var created: SshSession? = null
        val create = {
            created = SshSession(
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
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) create()
        else instrumentation.runOnMainSync { create() }
        return checkNotNull(created)
    }

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
                    try {
                        try {
                            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                            val bounds = Rect()
                            window.getBoundsInScreen(bounds)
                            if (bounds.height() > 0) return bounds.top
                        } catch (_: Throwable) {
                            // Continue through the window snapshot after stale entries.
                        }
                    } finally {
                        window.recycle()
                    }
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
