package app.terminalssh.secure.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionStatusTest {

    @Test fun anEmptyListIsIdle() {
        assertEquals(ConnectionStatus.IDLE, aggregate(emptyList()))
    }

    @Test fun aSingleStatusIsItself() {
        for (status in ConnectionStatus.entries) {
            assertEquals(status, aggregate(listOf(status)))
        }
    }

    @Test fun needingApprovalOutranksEverything() {
        val mixed = listOf(
            ConnectionStatus.CONNECTED,
            ConnectionStatus.CONNECTING,
            ConnectionStatus.NEEDS_APPROVAL,
            ConnectionStatus.FAILED,
        )
        assertEquals(ConnectionStatus.NEEDS_APPROVAL, aggregate(mixed))
    }

    @Test fun workingOutranksConnectedAndFailed() {
        assertEquals(
            ConnectionStatus.CONNECTING,
            aggregate(listOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING, ConnectionStatus.FAILED)),
        )
    }

    @Test fun aLiveSessionHidesAFailedOne() {
        assertEquals(ConnectionStatus.CONNECTED, aggregate(listOf(ConnectionStatus.CONNECTED, ConnectionStatus.FAILED)))
    }

    @Test fun failureShowsWhenNothingBetter() {
        assertEquals(ConnectionStatus.FAILED, aggregate(listOf(ConnectionStatus.FAILED, ConnectionStatus.IDLE)))
    }

    @Test fun aHostWithOneConnectedAndOneConnectingReadsAsConnecting() {
        assertEquals(ConnectionStatus.CONNECTING, aggregate(listOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING)))
    }

    @Test fun everyStatusHasALabelResource() {
        for (status in ConnectionStatus.entries) {
            assertTrue(status.labelRes != 0, "missing label for $status")
        }
    }
}
