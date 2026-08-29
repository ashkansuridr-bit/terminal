package app.terminalssh.secure.sftp

import java.io.InputStream
import java.io.OutputStream

/**
 * A token bucket that paces a transfer to a byte-per-second ceiling.
 *
 * Mobile data is the reason this exists: a multi-gigabyte upload on a metered connection
 * can burn a month's allowance before anyone notices. Throttling at the stream is the
 * only place it actually works — pausing between files does nothing for the one file
 * that is the whole problem.
 *
 * The bucket holds at most one second of budget, so a transfer that stalls cannot bank
 * credit and then burst at full speed when it resumes. [sleeper] is injected so the
 * pacing maths can be tested without a test that actually sleeps.
 */
class RateLimiter(
    private val bytesPerSecond: Long,
    private val clock: () -> Long = System::nanoTime,
    private val sleeper: (Long) -> Unit = { millis -> if (millis > 0) Thread.sleep(millis) },
) {
    private var available: Double = bytesPerSecond.toDouble()
    private var lastRefill: Long = clock()

    val unlimited: Boolean get() = bytesPerSecond <= 0

    /** Blocks until [count] bytes may be sent. Returns the milliseconds it waited. */
    fun acquire(count: Int): Long {
        if (unlimited) return 0L
        var waited = 0L
        var remaining = count.toDouble()
        while (remaining > 0) {
            refill()
            val take = minOf(remaining, available)
            available -= take
            remaining -= take
            if (remaining > 0) {
                // Wait exactly long enough to earn what is still owed, rounded up so a
                // sub-millisecond debt cannot spin.
                val millis = ((remaining / bytesPerSecond) * 1000.0).toLong().coerceAtLeast(1L)
                sleeper(millis)
                waited += millis
            }
        }
        return waited
    }

    private fun refill() {
        val now = clock()
        val elapsedSeconds = (now - lastRefill) / 1_000_000_000.0
        if (elapsedSeconds <= 0) return
        lastRefill = now
        available = (available + elapsedSeconds * bytesPerSecond)
            .coerceAtMost(bytesPerSecond.toDouble())
    }
}

/** Applies a [RateLimiter] to everything read from [delegate]. */
class ThrottledInputStream(
    private val delegate: InputStream,
    private val limiter: RateLimiter,
) : InputStream() {
    override fun read(): Int {
        limiter.acquire(1)
        return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = delegate.read(b, off, len)
        if (read > 0) limiter.acquire(read)
        return read
    }

    override fun available(): Int = delegate.available()
    override fun close() = delegate.close()
}

/** Applies a [RateLimiter] to everything written to [delegate]. */
class ThrottledOutputStream(
    private val delegate: OutputStream,
    private val limiter: RateLimiter,
) : OutputStream() {
    override fun write(b: Int) {
        limiter.acquire(1)
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        limiter.acquire(len)
        delegate.write(b, off, len)
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
