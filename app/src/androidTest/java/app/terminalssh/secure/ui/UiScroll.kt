package app.terminalssh.secure.ui

import android.graphics.Rect
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/**
 * Scrolls the largest visible scrollable until [present] reports the target reached.
 *
 * The suite runs on small CI emulators where the default androidTest-runner screen shows
 * only a few rows at once, so "below the fold" must mean "scroll to it", never "absent".
 *
 * @param horizontalDirection pass non-null when the scrollable is horizontal (e.g. the
 *   terminal key toolbar). RTL rows start at the right edge, so the gesture mirrors.
 */
fun UiDevice.scrollUntil(
    maxAttempts: Int = 12,
    horizontalDirection: LayoutDirection? = null,
    present: () -> Boolean,
) {
    repeat(maxAttempts) {
        if (present()) return
        scrollOnce(horizontalDirection)
    }
}

/**
 * Like [scrollUntil], but keeps scrolling until [target] is not merely present but fully
 * inside the display.
 *
 * A row that has only just scrolled into view sits clipped at the viewport edge, and its
 * [UiObject2.visibleBounds] then reports the clipped (smaller) size. An assertion such as
 * "the palette row is at least 48dp tall" read against that value is really measuring the
 * screen edge, not the row. Requiring full visibility makes the assertion measure the row.
 */
fun UiDevice.scrollUntilFullyVisible(
    target: BySelector,
    maxAttempts: Int = 12,
    horizontalDirection: LayoutDirection? = null,
) {
    repeat(maxAttempts) {
        val node = findObject(target)
        if (node != null && isFullyVisible(node)) return
        scrollOnce(horizontalDirection)
    }
}

private fun UiDevice.scrollOnce(horizontalDirection: LayoutDirection?) {
    val scrollable = pickScrollable(horizontalDirection != null)
    if (scrollable != null) {
        val direction = when (horizontalDirection) {
            LayoutDirection.Rtl -> Direction.RIGHT
            LayoutDirection.Ltr -> Direction.LEFT
            null -> Direction.DOWN
        }
        // The handle can go stale between the pick and the gesture under a rendering
        // terminal; a skipped step is retried on the next loop iteration.
        runCatching { scrollable.scroll(direction, 0.9f) }
    } else {
        swipe(
            displayWidth / 2,
            (displayHeight * 0.7).toInt(),
            displayWidth / 2,
            (displayHeight * 0.3).toInt(),
            12,
        )
    }
    waitForIdle()
}

private fun UiDevice.isFullyVisible(node: UiObject2): Boolean {
    val bounds = node.safeBounds() ?: return false
    return bounds.width() > 0 && bounds.height() > 0 &&
        bounds.left >= 0 && bounds.top >= 0 &&
        bounds.right <= displayWidth && bounds.bottom <= displayHeight
}

/**
 * Picks the terminal toolbar (bottom-most scrollable) over any other horizontal row when
 * searching sideways; picks the main vertical list otherwise.
 *
 * The accessibility tree refreshes while the terminal renders, so a snapshot from
 * [UiDevice.findObjects] can already be stale by the time it is read. Reading bounds is
 * therefore best-effort: a stale candidate is skipped rather than crashing the test with a
 * [androidx.test.uiautomator.StaleObjectException].
 */
private fun UiDevice.pickScrollable(horizontal: Boolean): UiObject2? {
    val scored = findObjects(By.scrollable(true)).mapNotNull { candidate ->
        val bounds = candidate.safeBounds() ?: return@mapNotNull null
        if (!horizontal && bounds.width() > bounds.height()) return@mapNotNull null
        candidate to bounds
    }
    return scored.maxByOrNull { it.second.bottom }?.first
}

private fun UiObject2.safeBounds(): Rect? = runCatching { visibleBounds }.getOrNull()

/** Main-thread idleness does not imply completion of settings writes on Dispatchers.IO. */
fun awaitUiState(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
    do {
        try {
            if (condition()) return true
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            // Re-query on the next iteration after Compose replaces the node.
        }
        android.os.SystemClock.sleep(50)
    } while (android.os.SystemClock.uptimeMillis() < deadline)
    return false
}
