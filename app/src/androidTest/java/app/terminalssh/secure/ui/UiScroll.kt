package app.terminalssh.secure.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.test.uiautomator.By
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
        val scrollable = pickScrollable(horizontalDirection != null)
        if (scrollable != null) {
            val direction = when (horizontalDirection) {
                LayoutDirection.Rtl -> Direction.RIGHT
                LayoutDirection.Ltr -> Direction.LEFT
                null -> Direction.DOWN
            }
            scrollable.scroll(direction, 0.9f)
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
}

/**
 * Picks the terminal toolbar (bottom-most scrollable) over any other horizontal row when
 * searching sideways; picks the main vertical list otherwise.
 */
private fun UiDevice.pickScrollable(horizontal: Boolean): UiObject2? {
    val candidates = findObjects(By.scrollable(true))
    return if (horizontal) {
        candidates.maxByOrNull { it.visibleBounds.bottom }
    } else {
        candidates.filter { it.visibleBounds.height() >= it.visibleBounds.width() }
            .maxByOrNull { it.visibleBounds.bottom }
    }
}