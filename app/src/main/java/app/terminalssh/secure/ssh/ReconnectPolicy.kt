package app.terminalssh.secure.ssh

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pure policy layer, deliberately outside [SshSession]: decides whether a connect
 * failure is worth an automatic retry.
 */
object ReconnectPolicy {

    /**
     * Network-level failures (host unreachable, connection refused, timed out, the
     * socket dropping mid-handshake) are always worth retrying regardless of message
     * text.
     *
     * [JSchException] has no typed subclasses for "auth failed" vs. everything else,
     * so for that one type only, a message match against [NON_TRANSIENT_MARKERS] is
     * the best available signal.
     *
     * Anything else — including an exception with no message, such as a bug — is
     * treated as non-transient: surfacing it immediately beats silently retrying and
     * hiding a real defect behind a few seconds of "Reconnecting…".
     */
    fun isTransient(t: Throwable): Boolean = when (t) {
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is SocketException,
        is EOFException,
        -> true
        is JSchException -> {
            val message = (t.message ?: "").lowercase()
            NON_TRANSIENT_MARKERS.none { it in message }
        }
        else -> false
    }

    private val NON_TRANSIENT_MARKERS = listOf("auth", "denied", "credential", "userauth", "publickey")

    const val BASE_DELAY_MS = 1_500L
    const val MAX_DELAY_MS = 30_000L

    /**
     * Exponential backoff with full jitter, replacing the previous linear
     * `base * (attempt + 1)`.
     *
     * Exponential growth stops a flapping link from being hammered once per 1.5s, and
     * the jitter matters specifically here: reconnecting several tabs to the same server
     * after one Wi-Fi drop would otherwise retry them in lockstep forever. [random] is a
     * parameter so the spread is testable without a fake clock.
     *
     * @param attempt zero-based retry counter.
     */
    fun delayMillis(attempt: Int, random: (Long) -> Long = { if (it <= 0) 0 else (0 until it).random() }): Long {
        val exponential = BASE_DELAY_MS shl attempt.coerceIn(0, 30)
        val ceiling = exponential.coerceIn(BASE_DELAY_MS, MAX_DELAY_MS)
        // Full jitter keeps a floor of BASE_DELAY_MS so the first retry is never instant.
        return BASE_DELAY_MS + random(ceiling - BASE_DELAY_MS + 1)
    }
}
