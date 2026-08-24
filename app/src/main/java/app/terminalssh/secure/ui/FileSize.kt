package app.terminalssh.secure.ui

import java.util.Locale
import kotlin.math.abs

/**
 * Human-readable byte counts.
 *
 * Binary units (1024) with SI-style labels, matching what `ls -lh` prints — the file
 * sizes a user is comparing against are the ones their shell just showed them.
 */
object FileSize {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes ${UNITS[0]}"

        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < UNITS.lastIndex) {
            value /= 1024
            unit++
        }
        // One decimal below 10 keeps "9.4 MB" precise while "421 MB" stays uncluttered.
        val pattern = if (abs(value) < 10) "%.1f %s" else "%.0f %s"
        return String.format(locale, pattern, value, UNITS[unit])
    }
}
