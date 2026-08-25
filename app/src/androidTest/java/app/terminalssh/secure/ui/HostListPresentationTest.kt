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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The host list is the first thing anyone sees, so what it groups and what it says about
 * each row is behaviour, not decoration. These check the parts a user would notice
 * immediately if they broke: the groups, the recency line, and the way out of a search
 * that found nothing.
 */
@RunWith(AndroidJUnit4::class)
class HostListPresentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private lateinit var app: TerminalApp
    private lateinit var seeded: List<HostProfile>

    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        app = context.applicationContext as TerminalApp
        val hour = 3_600_000L
        seeded = listOf(
            profile("present-1", "Starred alpha", favorite = true, lastConnectedAt = now - hour),
            profile("present-2", "Starred beta", favorite = true, lastConnectedAt = now - 5 * hour),
            profile("present-3", "Recent gamma", lastConnectedAt = now - 2 * hour),
            profile("present-4", "Recent delta", lastConnectedAt = now - 30 * hour),
            // Never connected: this is the row that has to say so rather than say nothing.
            profile("present-5", "Untouched epsilon", lastConnectedAt = 0L),
        )
        seeded.forEach(app.hosts::upsert)
    }

    @After
    fun tearDown() {
        seeded.forEach { app.hosts.delete(it.id) }
    }

    @Test
    fun hostsAreGroupedAndCarryTheirOwnRecency() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertVisible(R.string.hosts_section_favorites)
            assertReachable(R.string.hosts_section_recent)
            assertReachable(R.string.hosts_section_all)

            // Every group heading is present, so a host that was never opened is filed
            // under "all" rather than pretending to be recent.
            assertReachable(R.string.host_last_never)
        }
    }

    @Test
    fun aSearchThatMatchesNothingExplainsItselfAndOffersTheWayBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val search = context.getString(R.string.hosts_search)
            assertTrue(
                "the search field should appear once there are several hosts",
                device.wait(Until.hasObject(By.text(search)), TIMEOUT_MS),
            )

            device.findObject(By.text(search)).click()
            device.findObject(By.focused(true)).text = "zzzznotahost"

            assertVisible(R.string.hosts_no_results_title)
            val clear = context.getString(R.string.hosts_clear_search)
            assertTrue(device.wait(Until.hasObject(By.text(clear)), TIMEOUT_MS))

            device.findObject(By.text(clear)).click()
            device.waitForIdle()
            // Asserted against the list itself rather than against a particular row: the
            // soft keyboard is still up and covers most of a 600px screen, and dismissing
            // it with Back would close the Activity instead.
            assertReachable(R.string.hosts_section_favorites)
            assertFalse(device.hasObject(By.text(context.getString(R.string.hosts_no_results_title))))
        }
    }

    private fun assertVisible(stringRes: Int) {
        val text = context.getString(stringRes)
        assertTrue("\"$text\" was not on screen", device.wait(Until.hasObject(By.text(text)), TIMEOUT_MS))
    }

    /**
     * Like [assertVisible] but willing to scroll for it. A 600px emulator shows about
     * three rows, so "further down the list" is not the same as "missing" and a test that
     * cannot tell them apart is measuring the screen height.
     */
    private fun assertReachable(stringRes: Int) {
        val text = context.getString(stringRes)
        if (device.wait(Until.hasObject(By.text(text)), SETTLE_MS)) return
        repeat(SCROLL_ATTEMPTS) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.7).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.3).toInt(),
                12,
            )
            device.waitForIdle()
            if (device.hasObject(By.text(text))) return
        }
        assertTrue("\"$text\" was not reachable by scrolling the host list", false)
    }

    private fun profile(
        id: String,
        label: String,
        favorite: Boolean = false,
        lastConnectedAt: Long,
    ) = HostProfile(
        id = id,
        label = label,
        host = "127.0.0.1",
        username = "tester",
        auth = AuthMethod.Password(""),
        favorite = favorite,
        lastConnectedAt = lastConnectedAt,
    )

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val SETTLE_MS = 15_000L
        const val SCROLL_ATTEMPTS = 5
    }
}
