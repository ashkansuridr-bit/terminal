package app.terminalssh.secure.ssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JumpHostFailureTest {
    @Test fun unsupportedRoutingIsActionableAndNotAutomaticallyRetried() {
        val failure = JumpHostUnavailable()
        assertEquals(ConnectionErrorKind.JUMP_HOST_UNAVAILABLE, ConnectionError.classify(failure))
        assertFalse(ReconnectPolicy.isTransient(failure))
    }
}
