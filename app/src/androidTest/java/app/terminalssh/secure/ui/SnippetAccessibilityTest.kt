package app.terminalssh.secure.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.model.SnippetEntry
import app.terminalssh.secure.ssh.SshSession
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetAccessibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var app: TerminalApp
    private val first = SnippetEntry("snippet-accessibility-first", "List files", 2L)
    private val second = SnippetEntry("snippet-accessibility-second", "Show directory", 1L)
    private var previousToolbarProfile: String? = null
    private val toolbarTrace = mutableListOf<String>()

    @Before
    fun setUp() {
        toolbarTrace.clear()
        app = instrumentation.targetContext.applicationContext as TerminalApp
        previousToolbarProfile = app.toolbar.state.value.activeProfileId
        app.toolbar.setActiveProfile("shell")
        app.sessions.closeAll()
        app.hosts.upsertSnippet(first)
        app.hosts.upsertSnippet(second)
        app.sessions.add(idleSession())
    }

    @After
    fun tearDown() {
        app.sessions.closeAll()
        app.hosts.deleteSnippet(first.id)
        app.hosts.deleteSnippet(second.id)
        previousToolbarProfile?.let(app.toolbar::setActiveProfile)
    }

    @Test
    fun deleteActionsNameAndDeleteOnlyTheirSnippet() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                toolbarTrace += "activityLocale=${activity.resources.configuration.locales.toLanguageTags()}, " +
                    "activityDensity=${activity.resources.displayMetrics.density}, " +
                    "activitySnippets=${activity.getString(R.string.tcs_snippets_action)}, " +
                    "targetLocale=${instrumentation.targetContext.resources.configuration.locales.toLanguageTags()}, " +
                    "targetDensity=${instrumentation.targetContext.resources.displayMetrics.density}"
            }
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val snippetsAction = instrumentation.targetContext.getString(R.string.tcs_snippets_action)
            val firstDelete = instrumentation.targetContext.getString(R.string.snippet_delete, first.name)
            val secondDelete = instrumentation.targetContext.getString(R.string.snippet_delete, second.name)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            val showKeyboard = instrumentation.targetContext.getString(R.string.show_keyboard)
            assertTrue(
                "Terminal toolbar did not appear after selecting the Terminal tab",
                device.wait(Until.hasObject(By.desc(showKeyboard)), TIMEOUT_MS),
            )
            // The snippets key is deep in the horizontal toolbar; scroll it into view
            // (RTL rows start at the right edge, so the gesture mirrors).
            val direction = terminalLayoutDirection(
                instrumentation.targetContext.resources.configuration.locales[0],
            )
            val shellKeys = app.toolbar.effectiveProfile("snippet-accessibility-host").layout.keys
                .map { it.id to it.label }
            assertTrue("Shell preset no longer includes the real Snippets action: $shellKeys", shellKeys.any { it.first == "snippets" })
            // StudioToolbar clears the visible text from its a11y node on purpose and
            // publishes the localized action name instead. Scroll and click by that
            // stable accessible name, not the visual label that is absent from the tree.
            val snippetsKey = By.desc(snippetsAction)
            val oppositeDirection = if (direction == LayoutDirection.Ltr) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            val snippetsReachable = scrollToolbarToKey(snippetsKey, listOf(direction, oppositeDirection))
            assertTrue(
                "Snippets toolbar key was not fully reachable; profileKeys=$shellKeys, " +
                    "targetBounds=${runCatching { device.findObject(snippetsKey)?.visibleBounds }.getOrNull()}, " +
                    "scrollables=${device.findObjects(By.scrollable(true)).map { runCatching { it.visibleBounds }.getOrNull() }}, " +
                    "display=${device.displayWidth}x${device.displayHeight}; " +
                    "toolbarTrace=${toolbarTrace.joinToString("\n").take(16_000)}",
                snippetsReachable,
            )
            assertTrue(
                "Snippets toolbar key must expose its localized action name",
                device.wait(Until.hasObject(By.desc(snippetsAction)), TIMEOUT_MS),
            )
            device.findObject(snippetsKey).click()

            assertTrue(device.wait(Until.hasObject(By.desc(firstDelete)), TIMEOUT_MS))
            assertTrue(device.wait(Until.hasObject(By.desc(secondDelete)), TIMEOUT_MS))
            assertTouchTargetAtLeast48Dp(firstDelete)

            device.findObject(By.desc(firstDelete)).click()

            assertTrue(device.wait(Until.gone(By.desc(firstDelete)), TIMEOUT_MS))
            assertTrue(device.hasObject(By.desc(secondDelete)))
            assertFalse(app.hosts.snippets().any { it.id == first.id })
            assertTrue(app.hosts.snippets().any { it.id == second.id })
        }
    }

    /** Swipe only inside the compact terminal toolbar viewport, never another page scroller. */
    private fun scrollToolbarToKey(target: androidx.test.uiautomator.BySelector, directions: List<LayoutDirection>): Boolean {
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val minTargetPx = (48 * density).toInt()
        for (direction in directions) {
            repeat(20) { attempt ->
                val candidate = runCatching { device.findObject(target) }.getOrNull()
                if (candidate != null) {
                    val visible = runCatching { candidate.visibleBounds }.getOrNull()
                    if (visible != null && visible.width() >= minTargetPx && visible.height() >= minTargetPx) return true
                }
                val band = device.findObjects(By.scrollable(true))
                    .mapNotNull { node -> runCatching { node to node.visibleBounds }.getOrNull() }
                    // The viewport's height is a scroll-container dimension, not an
                    // accessibility touch target. API 26 can report a 48px row at
                    // density 1; comparing it to 48dp incorrectly rejects it on denser
                    // devices and exits before sending any swipe.
                    .filter { (_, bounds) ->
                        bounds.height() in (24 * density).toInt()..(160 * density).toInt() && bounds.width() > minTargetPx
                    }
                    .maxByOrNull { it.second.width() } ?: run {
                        toolbarTrace += "No UiObject2 toolbar viewport; ${toolbarSnapshot(null)}"
                        return false
                    }
                val viewport = band.second
                val traceGesture = attempt == 0 || attempt == 1 || attempt == 19
                if (traceGesture) {
                    toolbarTrace += "$direction/$attempt before: ${toolbarSnapshot(viewport)}"
                }
                val y = viewport.centerY()
                val step = (viewport.width() * 0.44f).toInt().coerceAtLeast(48)
                val (from, to) = if (direction == LayoutDirection.Ltr) {
                    (viewport.right - step / 2) to (viewport.right - step / 2 - step)
                } else {
                    (viewport.left + step / 2) to (viewport.left + step / 2 + step)
                }
                if (from !in viewport.left until viewport.right || to !in viewport.left until viewport.right) return false
                // UiDevice takes steps, approximately 5ms each: 70 steps is about
                // 350ms. Record actual scrolling rather than inferring it from injection.
                if (traceGesture) {
                    var injected: Boolean? = null
                    val eventResult = try {
                        val event = instrumentation.uiAutomation.executeAndWaitForEvent(
                            { injected = device.swipe(from, y, to, y, 70) },
                            { event ->
                                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                                    event.packageName?.toString() == instrumentation.targetContext.packageName
                            },
                            2_000L,
                        )
                        try {
                            "scrollX=${event.scrollX}, maxScrollX=${event.maxScrollX}, " +
                                "fromIndex=${event.fromIndex}, toIndex=${event.toIndex}"
                        } finally {
                            event.recycle()
                        }
                    } catch (_: TimeoutException) {
                        "No app TYPE_VIEW_SCROLLED event within 2000ms"
                    }
                    toolbarTrace += "$direction/$attempt gesture=($from,$y)->($to,$y), " +
                        "steps=70, injected=$injected, $eventResult"
                } else {
                    device.swipe(from, y, to, y, 70)
                }
                device.waitForIdle()
                if (traceGesture) {
                    toolbarTrace += "$direction/$attempt after: ${toolbarSnapshot(viewport)}"
                }
            }
        }
        return false
    }

    /** Capture only toolbar semantics and window geometry, never terminal text or credentials. */
    private fun toolbarSnapshot(viewport: Rect?): String {
        val details = mutableListOf("uiObjectViewport=$viewport")
        try {
            if (viewport != null) {
                val visibleKeys = device.findObjects(By.descContains(""))
                    .mapNotNull { node ->
                        val bounds = node.visibleBounds
                        if (Rect.intersects(viewport, bounds) && viewport.contains(bounds)) {
                            "${node.contentDescription?.take(80)}@$bounds"
                        } else null
                    }.take(16)
                details += "uiObjectKeys=$visibleKeys"
            }
        } catch (error: RuntimeException) {
            details += "uiObjectSnapshotError=${error.javaClass.simpleName}"
        }
        val windows = instrumentation.uiAutomation.windows
        try {
            for (window in windows) {
                val windowBounds = Rect().also(window::getBoundsInScreen)
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    details += "IME=$windowBounds, active=${window.isActive}, focused=${window.isFocused}"
                }
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() != instrumentation.targetContext.packageName) continue
                    details += "appWindow=$windowBounds, active=${window.isActive}, focused=${window.isFocused}"
                    var visited = 0
                    var rows = 0
                    fun inspect(node: AccessibilityNodeInfo, insideToolbar: Boolean = false) {
                        if (++visited > 500 || rows > 3) return
                        val bounds = Rect().also(node::getBoundsInScreen)
                        val isToolbar = node.isScrollable && bounds.width() > bounds.height() * 2 &&
                            (viewport == null || Rect.intersects(viewport, bounds))
                        if (isToolbar) {
                            rows++
                            details += "rawRow=$bounds, actions=${node.actionList.map { it.id }}"
                        }
                        // Read descriptions only below an identified toolbar row. Do not
                        // inspect text or descriptions from the terminal's editor node.
                        if (insideToolbar && !node.isPassword && node.contentDescription != null) {
                            details += "rawKey=${node.contentDescription.toString().take(80)}@$bounds, " +
                                "visible=${node.isVisibleToUser}, clickable=${node.isClickable}"
                        }
                        for (index in 0 until node.childCount.coerceAtMost(50)) {
                            val child = node.getChild(index) ?: continue
                            try {
                                inspect(child, insideToolbar || isToolbar)
                            } finally {
                                child.recycle()
                            }
                        }
                    }
                    inspect(root)
                } finally {
                    root.recycle()
                }
            }
        } catch (error: RuntimeException) {
            details += "rawSnapshotError=${error.javaClass.simpleName}"
        } finally {
            windows.forEach { it.recycle() }
        }
        return details.joinToString("; ").take(1_400)
    }

    private fun idleSession(): SshSession {
        var created: SshSession? = null
        val create = {
            created = SshSession(
                id = "snippet-accessibility-session",
                profile = HostProfile(
                    id = "snippet-accessibility-host",
                    label = "Snippet test",
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

    private fun assertTouchTargetAtLeast48Dp(description: String) {
        val bounds = device.findObject(By.desc(description)).visibleBounds
        val minimumPx = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
        assertTrue("$description width was ${bounds.width()}px", bounds.width() >= minimumPx)
        assertTrue("$description height was ${bounds.height()}px", bounds.height() >= minimumPx)
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
