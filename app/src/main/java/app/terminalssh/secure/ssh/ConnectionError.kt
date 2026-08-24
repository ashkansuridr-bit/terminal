package app.terminalssh.secure.ssh

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Classifies a connection failure into something a user can act on.
 *
 * Raw JSch text ("Session.connect: java.io.IOException: End of IStream") tells a user
 * nothing about what to change. This layer is deliberately Android-free so the mapping
 * itself is unit-testable; the UI turns a [ConnectionErrorKind] into a localized string.
 */
enum class ConnectionErrorKind {
    /** DNS lookup failed — the host name is wrong or there is no network. */
    UNKNOWN_HOST,

    /** TCP refused — reachable machine, nothing listening on that port. */
    CONNECTION_REFUSED,

    /** No response in time — firewall, wrong address, or a very slow link. */
    TIMEOUT,

    /** Network unreachable / route missing — usually no connectivity at all. */
    NO_NETWORK,

    /** The connection dropped mid-conversation. */
    CONNECTION_LOST,

    /** The server rejected the credentials. */
    AUTH_FAILED,

    /** No shared key-exchange, cipher, or MAC algorithm with the server. */
    ALGORITHM_MISMATCH,

    /** The stored host key no longer matches what the server presented. */
    HOST_KEY_CHANGED,

    /** Nothing more specific could be determined. */
    UNKNOWN,
}

object ConnectionError {

    fun classify(t: Throwable): ConnectionErrorKind = when (t) {
        is HostKeyRejected -> ConnectionErrorKind.HOST_KEY_CHANGED
        is MissingCredential -> ConnectionErrorKind.AUTH_FAILED
        is UnknownHostException -> ConnectionErrorKind.UNKNOWN_HOST
        is ConnectException -> ConnectionErrorKind.CONNECTION_REFUSED
        is SocketTimeoutException -> ConnectionErrorKind.TIMEOUT
        is NoRouteToHostException -> ConnectionErrorKind.NO_NETWORK
        is EOFException -> ConnectionErrorKind.CONNECTION_LOST
        is SocketException -> classifyMessage(t.message, ConnectionErrorKind.CONNECTION_LOST)
        is JSchException -> classifyMessage(t.message, ConnectionErrorKind.UNKNOWN)
        else -> ConnectionErrorKind.UNKNOWN
    }

    /**
     * JSch reports most failures as a single exception type with the detail only in the
     * message, so for that type the message is the only signal available. The cause chain
     * is checked first, since a wrapped [UnknownHostException] is far more precise than
     * any keyword match.
     */
    private fun classifyMessage(message: String?, fallback: ConnectionErrorKind): ConnectionErrorKind {
        val text = (message ?: "").lowercase()
        return when {
            "unknownhost" in text || "nodename nor servname" in text -> ConnectionErrorKind.UNKNOWN_HOST
            "auth" in text || "denied" in text || "publickey" in text -> ConnectionErrorKind.AUTH_FAILED
            "algorithm negotiation" in text || "no common" in text -> ConnectionErrorKind.ALGORITHM_MISMATCH
            "connection refused" in text -> ConnectionErrorKind.CONNECTION_REFUSED
            "timeout" in text || "timed out" in text -> ConnectionErrorKind.TIMEOUT
            "network is unreachable" in text -> ConnectionErrorKind.NO_NETWORK
            // JSch spells it "End of IStream" — not "IOStream".
            "connection reset" in text || "end of istream" in text || "broken pipe" in text ->
                ConnectionErrorKind.CONNECTION_LOST
            else -> fallback
        }
    }
}
