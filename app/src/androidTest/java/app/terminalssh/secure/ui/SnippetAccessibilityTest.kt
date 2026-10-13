package app.terminalssh.secure.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.model.SnippetEntry
import app.terminalssh.secure.ssh.SshSession
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

    @Before
    fun setUp() {
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
        ActivityScenario.launch(MainActivity::class.java).use {
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val snippetsAction = instrumentation.targetContext.getString(R.string.tcs_snippets_action)
            val firstDelete = instrumentation.targetContext.getString(R.string.snippet_delete, first.name)
            val secondDelete = instrumentation.targetContext.getString(R.string.snippet_delete, second.name)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
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
                    "display=${device.displayWidth}x${device.displayHeight}",
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
            repeat(20) {
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
                    .maxByOrNull { it.second.width() } ?: return false
                val scrollDirection = if (direction == LayoutDirection.Ltr) Direction.LEFT else Direction.RIGHT
                runCatching { band.first.scroll(scrollDirection, 0.75f) }
                device.waitForIdle()
                val afterAccessibilityScroll = runCatching { device.findObject(target)?.visibleBounds }.getOrNull()
                if (afterAccessibilityScroll != null && afterAccessibilityScroll.width() >= minTargetPx &&
                    afterAccessibilityScroll.height() >= minTargetPx
                ) return true

                val viewport = band.second
                val y = viewport.centerY()
                val (from, to) = if (direction == LayoutDirection.Ltr) {
                    (viewport.right - (viewport.width() / 10)) to (viewport.left + (viewport.width() / 10))
                } else {
                    (viewport.left + (viewport.width() / 10)) to (viewport.right - (viewport.width() / 10))
                }
                if (from == to) return false
                device.swipe(from, y, to, y, 18)
                device.waitForIdle()
            }
        }
        return false
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
