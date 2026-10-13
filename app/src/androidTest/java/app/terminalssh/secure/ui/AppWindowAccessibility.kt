package app.terminalssh.secure.ui

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.StaleObjectException

/** Small helpers for assertions against the app's accessibility tree, independent of IME focus. */
internal object AppWindowAccessibility {
    fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.uiAutomation.windows.forEach { window ->
                try {
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                    val root = window.root ?: return@forEach
                    try {
                        findDescendant(root, predicate)?.let { return it }
                    } finally {
                        root.recycle()
                    }
                } finally {
                    window.recycle()
                }
            }
        } catch (_: StaleObjectException) {
            // A tree can be replaced during Compose recomposition; callers may retry.
        }
        return null
    }

    fun waitFor(timeoutMs: Long = 10_000, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            find(predicate)?.let { return it }
            SystemClock.sleep(100)
        }
        return null
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    fun click(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    private fun findDescendant(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                findDescendant(child, predicate)?.let { return it }
            } finally {
                child.recycle()
            }
        }
        return null
    }
}
