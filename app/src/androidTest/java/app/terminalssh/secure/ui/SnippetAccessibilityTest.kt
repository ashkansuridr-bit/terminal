package app.terminalssh.secure.ui

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

    @Before
    fun setUp() {
        app = instrumentation.targetContext.applicationContext as TerminalApp
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
    }

    @Test
    fun deleteActionsNameAndDeleteOnlyTheirSnippet() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val terminalTab = instrumentation.targetContext.getString(R.string.tab_terminal)
            val snippets = instrumentation.targetContext.getString(R.string.snippets_short)
            val firstDelete = instrumentation.targetContext.getString(R.string.snippet_delete, first.name)
            val secondDelete = instrumentation.targetContext.getString(R.string.snippet_delete, second.name)

            assertTrue(device.wait(Until.hasObject(By.text(terminalTab)), TIMEOUT_MS))
            device.findObject(By.text(terminalTab)).click()
            assertTrue(device.wait(Until.hasObject(By.text(snippets)), TIMEOUT_MS))
            device.findObject(By.text(snippets)).click()

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

    private fun idleSession(): SshSession = SshSession(
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

    private fun assertTouchTargetAtLeast48Dp(description: String) {
        val bounds = device.findObject(By.desc(description)).visibleBounds
        val minimumPx = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
        assertTrue("$description width was ${bounds.width()}px", bounds.width() >= minimumPx)
        assertTrue("$description height was ${bounds.height()}px", bounds.height() >= minimumPx)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
