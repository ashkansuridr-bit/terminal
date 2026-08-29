package app.terminalssh.secure.sftp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    /** A clock the test drives, so pacing is verified without any real sleeping. */
    private class FakeClock {
        var nanos = 0L
        val slept = mutableListOf<Long>()
        fun read() = nanos
        fun sleep(millis: Long) { slept += millis; nanos += millis * 1_000_000 }
    }

    @Test fun zeroMeansUnlimitedAndNeverSleeps() {
        val clock = FakeClock()
        val limiter = RateLimiter(0, clock::read, clock::sleep)
        assertTrue(limiter.unlimited)
        assertEquals(0L, limiter.acquire(10_000_000))
        assertTrue(clock.slept.isEmpty())
    }

    @Test fun theFirstSecondOfBudgetIsSpentWithoutWaiting() {
        val clock = FakeClock()
        val limiter = RateLimiter(1000, clock::read, clock::sleep)
        assertEquals(0L, limiter.acquire(1000), "one second of budget is available up front")
        assertTrue(clock.slept.isEmpty())
    }

    @Test fun spendingBeyondTheBudgetActuallyWaits() {
        val clock = FakeClock()
        val limiter = RateLimiter(1000, clock::read, clock::sleep)
        limiter.acquire(1000)

        val waited = limiter.acquire(1000)

        assertTrue(waited > 0, "a second 1000 bytes at 1000 B/s must wait")
        assertTrue(clock.slept.isNotEmpty())
    }

    @Test fun throughputConvergesOnTheConfiguredRate() {
        // 10 KB at 1000 B/s should take about ten seconds of simulated time.
        val clock = FakeClock()
        val limiter = RateLimiter(1000, clock::read, clock::sleep)
        repeat(10) { limiter.acquire(1000) }

        val elapsedSeconds = clock.nanos / 1_000_000_000.0
        assertTrue(elapsedSeconds >= 8.0, "expected ~9s of pacing, got $elapsedSeconds")
        assertTrue(elapsedSeconds <= 12.0, "pacing overshot: $elapsedSeconds")
    }

    @Test fun anIdleTransferCannotBankCreditAndThenBurst() {
        // Stall for an hour, then ask for an hour's worth. The bucket caps at one second,
        // so this must still be paced instead of firing off 3.6 MB instantly.
        val clock = FakeClock()
        val limiter = RateLimiter(1000, clock::read, clock::sleep)
        clock.nanos += 3600L * 1_000_000_000

        val waited = limiter.acquire(10_000)

        assertTrue(waited > 0, "banked credit must be capped at one second of budget")
    }

    @Test fun aThrottledStreamStillDeliversEveryByteUnchanged() {
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val clock = FakeClock()
        val stream = ThrottledInputStream(
            ByteArrayInputStream(payload),
            RateLimiter(1024, clock::read, clock::sleep),
        )
        val read = stream.readBytes()
        assertTrue(payload.contentEquals(read), "throttling must not alter or truncate data")
        assertTrue(clock.slept.isNotEmpty(), "4 KB at 1 KB/s must have been paced")
    }

    @Test fun aThrottledOutputStreamAlsoDeliversEverything() {
        val clock = FakeClock()
        val sink = ByteArrayOutputStream()
        val out = ThrottledOutputStream(sink, RateLimiter(1024, clock::read, clock::sleep))
        val payload = ByteArray(2048) { it.toByte() }
        out.write(payload)
        out.flush()
        assertTrue(payload.contentEquals(sink.toByteArray()))
    }

    @Test fun anUnlimitedStreamIsNotWrapped() {
        assertTrue(RateLimiter(0).unlimited)
        assertFalse(RateLimiter(1).unlimited)
    }
}
