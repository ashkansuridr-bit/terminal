package app.terminalssh.secure.ssh

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconnectPolicyTest {

    @Test fun networkFailuresAreTransientRegardlessOfMessage() {
        assertTrue(ReconnectPolicy.isTransient(UnknownHostException("nope.invalid")))
        assertTrue(ReconnectPolicy.isTransient(ConnectException("Connection refused")))
        assertTrue(ReconnectPolicy.isTransient(SocketTimeoutException()))
        assertTrue(ReconnectPolicy.isTransient(SocketException("Connection reset")))
        assertTrue(ReconnectPolicy.isTransient(EOFException()))
    }

    @Test fun jschAuthFailuresAreNotTransient() {
        assertFalse(ReconnectPolicy.isTransient(JSchException("Auth fail")))
        assertFalse(ReconnectPolicy.isTransient(JSchException("USERAUTH fail")))
        assertFalse(ReconnectPolicy.isTransient(JSchException("permission denied")))
        assertFalse(ReconnectPolicy.isTransient(JSchException("publickey auth failed")))
    }

    @Test fun jschNonAuthFailuresAreTransient() {
        assertTrue(ReconnectPolicy.isTransient(JSchException("Session.connect: java.io.IOException: End of IStream")))
    }

    @Test fun unrecognizedFailuresAreNotSilentlyRetried() {
        // A bug (e.g. an NPE) must surface immediately instead of being retried three
        // times under a "Reconnecting..." label that hides what actually happened.
        assertFalse(ReconnectPolicy.isTransient(NullPointerException()))
        assertFalse(ReconnectPolicy.isTransient(IllegalStateException("unexpected state")))
    }
}
