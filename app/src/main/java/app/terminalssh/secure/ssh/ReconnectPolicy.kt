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
}
