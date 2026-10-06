package app.terminalssh.secure.ssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SshIdentityCleanupTest {
    @Test fun successfulIdentityCleanupLeavesReturnedTransportOpen() {
        var cleanups = 0
        var closes = 0
        cleanupSshIdentities(null, { cleanups++ }, { closes++ }, { closes++ })
        assertEquals(1, cleanups)
        assertEquals(0, closes)
    }

    @Test fun cleanupFailureAfterSuccessfulConnectClosesChannelAndSessionAndFails() {
        val failure = IllegalStateException("identity cleanup")
        val closed = mutableListOf<String>()
        val actual = assertFailsWith<IllegalStateException> {
            cleanupSshIdentities(null, { throw failure }, { closed.add("channel") }, { closed.add("session") })
        }
        assertSame(failure, actual)
        assertEquals(listOf("channel", "session"), closed)
    }

    @Test fun originalConnectFailureIsPreservedAndCleanupFailureIsAttached() {
        val primary = IllegalArgumentException("setup")
        val secondary = IllegalStateException("identity cleanup")
        var closes = 0
        cleanupSshIdentities(primary, { throw secondary }, { closes++ }, { closes++ })
        assertEquals(2, closes)
        assertSame(secondary, primary.suppressed.single())
    }

    @Test fun channelCloseFailureDoesNotPreventSessionCloseOrHideCleanupFailure() {
        val cleanup = IllegalStateException("identity cleanup")
        val channel = IllegalArgumentException("channel close")
        var sessionClosed = false
        val actual = assertFailsWith<IllegalStateException> {
            cleanupSshIdentities(null, { throw cleanup }, { throw channel }, { sessionClosed = true })
        }
        assertSame(cleanup, actual)
        assertSame(channel, cleanup.suppressed.single())
        assertEquals(true, sessionClosed)
    }
}
