package app.terminalssh.secure.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    private fun ago(millis: Long) = RelativeTime.since(now - millis, now)

    @Test
    fun `zero timestamp means never connected`() {
        assertEquals(Since.Never, RelativeTime.since(0L, now))
        assertEquals(Since.Never, RelativeTime.since(-1L, now))
    }

    @Test
    fun `under a minute reads as just now`() {
        assertEquals(Since.JustNow, ago(0))
        assertEquals(Since.JustNow, ago(59_000))
    }

    @Test
    fun `minutes and hours round down to the unit that was crossed`() {
        assertEquals(Since.Minutes(1), ago(60_000))
        assertEquals(Since.Minutes(59), ago(59 * 60_000))
        assertEquals(Since.Hours(1), ago(60 * 60_000))
        assertEquals(Since.Hours(23), ago(23 * 3_600_000L))
    }

    @Test
    fun `the day before today is yesterday, not one day`() {
        assertEquals(Since.Yesterday, ago(24 * 3_600_000L))
        assertEquals(Since.Yesterday, ago(47 * 3_600_000L))
        assertEquals(Since.Days(2), ago(48 * 3_600_000L))
    }

    @Test
    fun `weeks and months take over from days`() {
        val day = 24 * 3_600_000L
        assertEquals(Since.Days(6), ago(6 * day))
        assertEquals(Since.Weeks(1), ago(7 * day))
        assertEquals(Since.Weeks(4), ago(29 * day))
        assertEquals(Since.Months(1), ago(30 * day))
        assertEquals(Since.Months(12), ago(365 * day))
    }

    /**
     * A restored backup or a hand-set clock can leave a timestamp in the future. Showing
     * "-3 hours ago" is how a user learns not to trust anything else on the screen.
     */
    @Test
    fun `a future timestamp reads as just now rather than negative`() {
        assertEquals(Since.JustNow, RelativeTime.since(now + 86_400_000L, now))
    }

    /** Weeks and months never report zero: crossing into a bucket means at least one. */
    @Test
    fun `bucket counts are never zero`() {
        val day = 24 * 3_600_000L
        listOf(7 * day, 8 * day, 30 * day, 31 * day).forEach { elapsed ->
            val count = when (val since = ago(elapsed)) {
                is Since.Weeks -> since.count
                is Since.Months -> since.count
                else -> error("expected a week or month bucket for $elapsed, got ${ago(elapsed)}")
            }
            assertEquals(true, count >= 1)
        }
    }
}
