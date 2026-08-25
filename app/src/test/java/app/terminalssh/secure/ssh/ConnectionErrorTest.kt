package app.terminalssh.secure.ssh

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionErrorTest {

    @Test fun typedNetworkFailuresMapToTheirOwnKind() {
        assertEquals(ConnectionErrorKind.UNKNOWN_HOST, ConnectionError.classify(UnknownHostException("nope")))
        assertEquals(ConnectionErrorKind.CONNECTION_REFUSED, ConnectionError.classify(ConnectException("refused")))
        assertEquals(ConnectionErrorKind.TIMEOUT, ConnectionError.classify(SocketTimeoutException()))
        assertEquals(ConnectionErrorKind.NO_NETWORK, ConnectionError.classify(NoRouteToHostException()))
        assertEquals(ConnectionErrorKind.CONNECTION_LOST, ConnectionError.classify(EOFException()))
    }

    @Test fun appExceptionsMapToTheirOwnKind() {
        assertEquals(ConnectionErrorKind.HOST_KEY_CHANGED, ConnectionError.classify(HostKeyRejected("host_key_changed")))
        assertEquals(ConnectionErrorKind.AUTH_FAILED, ConnectionError.classify(MissingCredential()))
    }

    @Test fun jschMessagesAreClassifiedByKeyword() {
        assertEquals(ConnectionErrorKind.AUTH_FAILED, ConnectionError.classify(JSchException("Auth fail")))
        assertEquals(
            ConnectionErrorKind.ALGORITHM_MISMATCH,
            ConnectionError.classify(JSchException("Algorithm negotiation fail")),
        )
        assertEquals(
            ConnectionErrorKind.CONNECTION_LOST,
            ConnectionError.classify(JSchException("Session.connect: java.io.IOException: End of IStream")),
        )
        assertEquals(ConnectionErrorKind.TIMEOUT, ConnectionError.classify(JSchException("connection is timed out")))
    }

    @Test fun socketExceptionFallsBackToConnectionLost() {
        assertEquals(ConnectionErrorKind.CONNECTION_LOST, ConnectionError.classify(SocketException("Connection reset")))
        assertEquals(ConnectionErrorKind.CONNECTION_LOST, ConnectionError.classify(SocketException()))
    }

    @Test fun unrecognizedFailuresAreUnknownRatherThanMisreported() {
        assertEquals(ConnectionErrorKind.UNKNOWN, ConnectionError.classify(IllegalStateException("???")))
        assertEquals(ConnectionErrorKind.UNKNOWN, ConnectionError.classify(JSchException("something new")))
    }

    @Test fun everyKindHasADistinctIdentity() {
        // Guards against a copy-paste collapse in the when-branches above.
        assertEquals(ConnectionErrorKind.entries.size, ConnectionErrorKind.entries.toSet().size)
    }
}
