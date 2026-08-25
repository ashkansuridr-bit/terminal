package app.terminalssh.secure.ui

/**
 * How long ago something happened, bucketed for display.
 *
 * Deliberately not a formatted string: the buckets are pure data so they can be unit
 * tested without a Context, and so the wording stays in strings.xml where a translator
 * can reach it. "Yesterday" is its own bucket rather than "1 day" because that is how
 * people actually say it, in Persian as much as in English.
 */
sealed interface Since {
    /** Never connected. Different from "a long time ago" and worth saying so. */
    data object Never : Since
    data object JustNow : Since
    data class Minutes(val count: Int) : Since
    data class Hours(val count: Int) : Since
    data object Yesterday : Since
    data class Days(val count: Int) : Since
    data class Weeks(val count: Int) : Since
    data class Months(val count: Int) : Since
}

object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private const val MONTH = 30 * DAY

    /**
     * A clock that has gone backwards (a timestamp in the future, after a manual clock
     * change or a restored backup) reads as "just now" rather than as a negative age.
     */
    fun since(thenMs: Long, nowMs: Long): Since {
        if (thenMs <= 0L) return Since.Never
        val elapsed = (nowMs - thenMs).coerceAtLeast(0L)
        return when {
            elapsed < MINUTE -> Since.JustNow
            elapsed < HOUR -> Since.Minutes((elapsed / MINUTE).toInt())
            elapsed < DAY -> Since.Hours((elapsed / HOUR).toInt())
            elapsed < 2 * DAY -> Since.Yesterday
            elapsed < WEEK -> Since.Days((elapsed / DAY).toInt())
            elapsed < MONTH -> Since.Weeks((elapsed / WEEK).toInt().coerceAtLeast(1))
            else -> Since.Months((elapsed / MONTH).toInt().coerceAtLeast(1))
        }
    }
}
