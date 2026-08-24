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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostsAccessibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var app: TerminalApp
    private lateinit var profile: HostProfile

    @Before
    fun setUp() {
        app = instrumentation.targetContext.applicationContext as TerminalApp
        profile = HostProfile(
            id = "accessibility-host-action",
            label = "Accessible host",
            host = "127.0.0.1",
            username = "tester",
            auth = AuthMethod.Password(""),
        )
        app.hosts.upsert(profile)
    }

    @After
    fun tearDown() {
        app.hosts.delete(profile.id)
    }

    @Test
    fun hostActionsAreNamedLargeAndPerformDistinctActions() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val favorite = instrumentation.targetContext.getString(
                R.string.host_favorite,
                profile.displayName,
            )
            val unfavorite = instrumentation.targetContext.getString(
                R.string.host_unfavorite,
                profile.displayName,
            )
            val edit = instrumentation.targetContext.getString(R.string.host_edit, profile.displayName)

            assertTrue(device.wait(Until.hasObject(By.desc(favorite)), TIMEOUT_MS))
            assertTouchTargetAtLeast48Dp(favorite)
            assertTouchTargetAtLeast48Dp(edit)

            device.findObject(By.desc(favorite)).click()
            assertTrue(device.wait(Until.hasObject(By.desc(unfavorite)), TIMEOUT_MS))
            assertTrue(app.hosts.hosts().first { it.id == profile.id }.favorite)

            device.findObject(By.desc(edit)).click()
            assertTrue(device.wait(Until.hasObject(By.text(profile.label)), TIMEOUT_MS))
            assertTrue(device.hasObject(By.text(instrumentation.targetContext.getString(R.string.save))))
        }
    }

    @Test
    fun newConnectionIsNamedLargeButtonAndOpensEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val newConnection = instrumentation.targetContext.getString(R.string.home_new_connection)

            assertTrue(device.wait(Until.hasObject(By.desc(newConnection)), TIMEOUT_MS))
            val action = device.findObject(By.desc(newConnection))
            assertTrue("new connection was not exposed as a button", action.className == "android.widget.Button")
            assertTouchTargetAtLeast48Dp(newConnection)

            action.click()
            assertTrue(
                device.wait(
                    Until.hasObject(By.text(instrumentation.targetContext.getString(R.string.hosts_add))),
                    TIMEOUT_MS,
                ),
            )
        }
    }

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
