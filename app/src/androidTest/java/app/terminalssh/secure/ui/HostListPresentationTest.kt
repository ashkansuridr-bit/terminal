package app.terminalssh.secure.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.vm.AppViewModel
import app.terminalssh.secure.vm.HostMetadataState
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
    private lateinit var activityContext: Context
    private lateinit var app: TerminalApp
    private lateinit var viewModel: AppViewModel
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activityContext = activity
                // The test fixture is written directly to the durable store before
                // launch. Refresh a retained ViewModel so this checks rendering rather
                // than a stale initial snapshot left by another UI test.
                viewModel = ViewModelProvider(activity)[AppViewModel::class.java]
                viewModel.reloadHostMetadata()
            }
            assertTrue(
                "Seeded host fixture properties were not durable: " +
                    app.hosts.hosts().filter { stored -> seeded.any { it.id == stored.id } }
                        .map { it.id to (it.favorite to it.lastConnectedAt) },
                seeded.all { wanted ->
                    app.hosts.hosts().any { stored ->
                        stored.id == wanted.id && stored.favorite == wanted.favorite &&
                            stored.lastConnectedAt == wanted.lastConnectedAt
                    }
                },
            )
            assertTrue(
                "ViewModel did not publish the durable host fixture before rendering: " +
                    "state=${viewModel.hostMetadataState.value}, " +
                    "hosts=${viewModel.hosts.value.map { it.id to (it.favorite to it.lastConnectedAt) }}, " +
                    "expected=${seeded.map { it.id }}",
                awaitUiState(TIMEOUT_MS) {
                    viewModel.hostMetadataState.value == HostMetadataState.Ready &&
                        seeded.all { expected -> viewModel.hosts.value.any { it.id == expected.id &&
                            it.favorite == expected.favorite && it.lastConnectedAt == expected.lastConnectedAt } }
                },
            )
            // Synchronize on an initial visible group before asking the lazy list to
            // compose and expose an offscreen recent row to accessibility services.
            assertVisible(R.string.hosts_section_favorites)
            assertReachableText("Starred alpha")
            assertReachableText("Recent gamma")
            assertReachable(R.string.hosts_section_recent)
            assertReachable(R.string.hosts_section_all)

            // Every group heading is present, so a host that was never opened is filed
            // under "all" rather than pretending to be recent.
            assertReachable(R.string.host_last_never)
        }
    }

    @Test
    fun aSearchThatMatchesNothingExplainsItselfAndOffersTheWayBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activityContext = activity }
            val search = activityContext.getString(R.string.hosts_search)
            assertTrue(
                "the search field should appear once there are several hosts",
                device.wait(Until.hasObject(By.text(search)), TIMEOUT_MS),
            )

            device.findObject(By.text(search)).click()
            // The Compose editable field is an EditText; "focused" can race the IME on
            // first tap, so target the editable node directly.
            val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), TIMEOUT_MS)
            input.text = "zzzznotahost"

            assertVisible(R.string.hosts_no_results_title)
            val clear = activityContext.getString(R.string.hosts_clear_search)
            assertTrue(device.wait(Until.hasObject(By.text(clear)), TIMEOUT_MS))

            device.findObject(By.text(clear)).click()
            device.waitForIdle()
            // Asserted against the list itself rather than against a particular row: the
            // soft keyboard is still up and covers most of a 600px screen, and dismissing
            // it with Back would close the Activity instead.
            assertReachable(R.string.hosts_section_favorites)
            assertFalse(device.hasObject(By.text(activityContext.getString(R.string.hosts_no_results_title))))
        }
    }

    private fun assertVisible(stringRes: Int) {
        val text = activityContext.getString(stringRes)
        assertTrue("\"$text\" was not on screen", device.wait(Until.hasObject(By.text(text)), TIMEOUT_MS))
    }

    /**
     * Like [assertVisible] but willing to scroll for it. A 600px emulator shows about
     * three rows, so "further down the list" is not the same as "missing" and a test that
     * cannot tell them apart is measuring the screen height.
     */
    private fun assertReachable(stringRes: Int) {
        assertReachableText(activityContext.getString(stringRes))
    }

    private fun assertReachableText(text: String) {
        var swipeCount = 0
        var lastTargetBounds: Rect? = null
        var lastViewportBounds: Rect? = null
        // UiObject2.scroll reports success without moving this Compose LazyColumn on
        // some emulator images. Swipe inside the freshly resolved vertical list bounds;
        // require the entire row to be visible after every gesture, not merely present
        // in the semantics tree or clipped to a few pixels at the viewport edge.
        repeat(24) {
            val target = device.findObject(By.text(text))
            if (hasFullyVisibleBounds(target) || hasFullyVisibleAppText(text)) return
            val listBounds = appHostListBounds() ?: hostListBounds()
            lastTargetBounds = runCatching { target?.visibleBounds }.getOrNull()
            lastViewportBounds = listBounds
            if (listBounds != null) {
                val scrollNode = AppWindowAccessibility.find {
                    if (!it.isScrollable) return@find false
                    val bounds = AppWindowAccessibility.bounds(it)
                    bounds.height() > bounds.width() && bounds.contains(listBounds)
                }
                val moved = if (scrollNode != null) {
                    try { scrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
                    finally { scrollNode.recycle() }
                } else false
                if (!moved) {
                    // Keep the fallback drag inside the measured list lane so it cannot
                    // hit the floating action button. A deliberate drag avoids flinging
                    // over groups on short emulator screens.
                    val swipeX = listBounds.left + (listBounds.width() * 0.10f).toInt()
                    device.swipe(
                        swipeX, listBounds.bottom - (listBounds.height() * 0.18f).toInt(),
                        swipeX, listBounds.top + (listBounds.height() * 0.18f).toInt(),
                        350,
                    )
                }
                swipeCount++
            } else {
                // Older Android Compose trees do not always export the LazyColumn as an
                // accessibility scroll container. Keep the fallback lane near the left
                // edge for the same reason: a center gesture can hit the floating action
                // button drawn over the list.
                val swipeX = (device.displayWidth * 0.12f).toInt()
                device.swipe(
                    swipeX, (device.displayHeight * 0.68f).toInt(),
                    swipeX, (device.displayHeight * 0.30f).toInt(),
                    350,
                )
                swipeCount++
            }
            device.waitForIdle()
        }
        val target = device.findObject(By.text(text))
        val storedFixture = app.hosts.hosts()
            .filter { stored -> seeded.any { it.id == stored.id } }
            .map { it.id to (it.favorite to it.lastConnectedAt) }
        assertTrue(
            "\"$text\" was not fully reachable after $swipeCount swipes; " +
                "targetBounds=${runCatching { target?.visibleBounds }.getOrNull()}, previousTargetBounds=$lastTargetBounds, " +
                "viewportBounds=$lastViewportBounds, display=${device.displayWidth}x${device.displayHeight}, " +
                "storedFixture=$storedFixture, " +
                "appTree=${accessibilityTreeSnapshot()}",
            hasFullyVisibleBounds(target) || hasFullyVisibleAppText(text),
        )
    }

    private fun hasFullyVisibleAppText(text: String): Boolean {
        val node = AppWindowAccessibility.find { it.text?.toString() == text } ?: return false
        val bounds = try { AppWindowAccessibility.bounds(node) } finally { node.recycle() }
        val viewport = appHostListBounds() ?: return false
        return bounds.width() > 0 && bounds.height() > 0 && viewport.contains(bounds)
    }

    /** Captures the composed list's actual accessible contents when an expected group is missing. */
    private fun accessibilityTreeSnapshot(): String {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return "<no active app window>"
        val rows = mutableListOf<String>()
        fun collect(node: android.view.accessibility.AccessibilityNodeInfo) {
            if (rows.size >= 120) return
            val text = node.text?.toString()?.takeIf(String::isNotBlank)
            val description = node.contentDescription?.toString()?.takeIf(String::isNotBlank)
            if (text != null || description != null || node.isScrollable) {
                val bounds = Rect().also(node::getBoundsInScreen)
                rows += "text=$text desc=$description scroll=${node.isScrollable} bounds=$bounds"
            }
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
            rows.joinToString("; ")
        } catch (_: StaleObjectException) {
            "<accessibility tree changed while collecting diagnostics>"
        } finally {
            root.recycle()
        }
    }

    private fun hasFullyVisibleBounds(node: UiObject2?): Boolean {
        if (node == null) return false
        return try {
            val visible = node.visibleBounds
            val text = node.text?.toString() ?: return false
            val full = AppWindowAccessibility.find { it.text?.toString() == text } ?: return false
            try {
                val fullBounds = AppWindowAccessibility.bounds(full)
                // Screen origins differ between these APIs; element dimensions do not.
                visible.width() == fullBounds.width() && visible.height() == fullBounds.height()
            } finally { full.recycle() }
        } catch (_: StaleObjectException) {
            false
        }
    }

    private fun hostListBounds(): Rect? = device.findObjects(By.scrollable(true))
        .mapNotNull { candidate ->
            try {
                candidate.visibleBounds.takeIf { it.height() > it.width() }
            } catch (_: StaleObjectException) {
                null
            }
        }
        .maxByOrNull { it.height() }

    private fun appHostListBounds(): Rect? {
        val node = AppWindowAccessibility.find {
            if (!it.isScrollable) return@find false
            val bounds = AppWindowAccessibility.bounds(it)
            bounds.height() > bounds.width()
        } ?: return null
        return try { AppWindowAccessibility.bounds(node) } finally { node.recycle() }
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
    }
}
