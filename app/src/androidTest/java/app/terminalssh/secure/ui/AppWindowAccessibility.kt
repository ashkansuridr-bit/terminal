package app.terminalssh.secure.ui

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.StaleObjectException

/** Small helpers for assertions against the app's accessibility tree, independent of IME focus. */
internal object AppWindowAccessibility {
    /** Stable-enough view of visible text and bounds for detecting no-op scroll actions. */
    fun snapshot(): String? {
        val rows = mutableListOf<String>()
        fun collect(node: AccessibilityNodeInfo) {
            if (rows.size >= 240) return
            val text = node.text?.toString()?.takeIf(String::isNotBlank)
            val description = node.contentDescription?.toString()?.takeIf(String::isNotBlank)
            if (text != null || description != null || node.isScrollable) {
                val bounds = bounds(node)
                rows += "$text|$description|$bounds|${node.isScrollable}"
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { collect(child) } finally { child.recycle() }
            }
        }
        return try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.forEach { window ->
                try {
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                    val root = window.root ?: return@forEach
                    try { collect(root) } finally { root.recycle() }
                } finally { window.recycle() }
            }
            rows.joinToString(";")
        } catch (_: StaleObjectException) {
            // Compose can replace a live accessibility window during layout. A caller
            // treats an unavailable snapshot as no measurable progress and uses touch.
            null
        }
    }

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

    /** Send text through the named field's accessibility action, never a screen coordinate. */
    fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        // Prefer the editable child when Compose separates a label wrapper from
        // its BasicTextField. A wrapper can accept an action without editing text.
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (setText(child, value)) return true
            } finally { child.recycle() }
        }
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

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
