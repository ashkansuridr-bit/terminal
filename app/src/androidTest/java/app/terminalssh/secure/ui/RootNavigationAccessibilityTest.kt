package app.terminalssh.secure.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.Lifecycle
import app.terminalssh.secure.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootNavigationAccessibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun tabsUseVisibleLabelsWithoutDuplicateIconDescriptions() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val labels = listOf(
                R.string.tab_hosts,
                R.string.tab_terminal,
                R.string.tab_keys,
                R.string.tab_settings,
            ).map(instrumentation.targetContext::getString)

            labels.forEach { label ->
                assertTrue(device.wait(Until.hasObject(By.text(label)), TIMEOUT_MS))
                assertFalse("$label was announced twice", device.hasObject(By.desc(label)))
            }

            val settings = navigationLabel(labels.last())
            assertTrue("initial tab was not selected", isNavigationSelected(labels.first()))

            settings.click()
            assertTrue("settings tab did not become selected", waitUntil { isNavigationSelected(labels.last()) })
            assertFalse("previous tab remained selected", isNavigationSelected(labels.first()))
        }
    }

    @Test
    fun backFromSecondaryTabReturnsToHostsWithoutFinishingActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val hosts = instrumentation.targetContext.getString(R.string.tab_hosts)
            val settings = instrumentation.targetContext.getString(R.string.tab_settings)
            val newConnection = instrumentation.targetContext.getString(R.string.home_new_connection)

            assertTrue(device.wait(Until.hasObject(By.text(settings)), TIMEOUT_MS))
            navigationLabel(hosts).click()
            assertTrue("Hosts tab did not become selected", waitUntil { isNavigationSelected(hosts) })
            navigationLabel(settings).click()
            assertTrue("settings tab did not become selected", waitUntil { isNavigationSelected(settings) })

            device.pressBack()

            assertTrue(
                "Back did not return to the Hosts screen",
                device.wait(Until.hasObject(By.desc(newConnection)), TIMEOUT_MS),
            )
            assertTrue("Back finished the Activity", scenario.state == Lifecycle.State.RESUMED)
        }
    }

    private fun isNavigationSelected(label: String): Boolean {
        val labelBounds = navigationLabel(label).visibleBounds
        return device.findObjects(By.selected(true)).any { selected ->
            selected.visibleBounds.contains(labelBounds.centerX(), labelBounds.centerY())
        }
    }

    private fun navigationLabel(label: String) =
        device.findObjects(By.text(label)).maxBy { it.visibleBounds.centerY() }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
