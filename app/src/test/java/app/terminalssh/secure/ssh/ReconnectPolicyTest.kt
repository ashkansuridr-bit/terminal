package app.terminalssh.secure.ssh

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun backoffGrowsExponentiallyAndIsCapped() {
        // random() forced to its maximum isolates the growth curve from the jitter.
        val upper = { attempt: Int -> ReconnectPolicy.delayMillis(attempt) { it - 1 } }
        // The ceiling doubles each attempt: 1.5s, 3s, 6s, ... up to MAX_DELAY_MS.
        assertEquals(1_500L, upper(0))
        assertEquals(3_000L, upper(1))
        assertEquals(6_000L, upper(2))
        assertEquals(12_000L, upper(3))
        assertEquals(ReconnectPolicy.MAX_DELAY_MS, upper(10))
        assertEquals(ReconnectPolicy.MAX_DELAY_MS, upper(60))
    }

    @Test fun backoffNeverDropsBelowTheBaseDelay() {
        // random() forced to zero is the lower bound: a retry is never instant.
        for (attempt in 0..40) {
            assertEquals(ReconnectPolicy.BASE_DELAY_MS, ReconnectPolicy.delayMillis(attempt) { 0 })
        }
    }

    @Test fun backoffStaysInRangeWithRealJitter() {
        for (attempt in 0..40) {
            val delay = ReconnectPolicy.delayMillis(attempt)
            assertTrue(delay >= ReconnectPolicy.BASE_DELAY_MS, "attempt $attempt produced $delay")
            assertTrue(delay <= ReconnectPolicy.MAX_DELAY_MS, "attempt $attempt produced $delay")
        }
    }

    @Test fun negativeAttemptIsClampedRatherThanCrashing() {
        assertEquals(ReconnectPolicy.BASE_DELAY_MS, ReconnectPolicy.delayMillis(-5) { 0 })
    }

    @Test fun unrecognizedFailuresAreNotSilentlyRetried() {
        // A bug (e.g. an NPE) must surface immediately instead of being retried three
        // times under a "Reconnecting..." label that hides what actually happened.
        assertFalse(ReconnectPolicy.isTransient(NullPointerException()))
        assertFalse(ReconnectPolicy.isTransient(IllegalStateException("unexpected state")))
    }
}
