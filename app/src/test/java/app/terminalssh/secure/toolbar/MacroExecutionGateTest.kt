package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class MacroExecutionGateTest {
    @Test
    fun overlappingMacroIsRejectedUntilActiveSequenceCompletes() = runBlocking {
        val gate = MacroExecutionGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dispatches = mutableListOf<String>()

        val first = async {
            gate.runIfIdle {
                dispatches += "first:start"
                entered.complete(Unit)
                release.await()
                dispatches += "first:enter"
            }
        }
        entered.await()

        val secondAccepted = gate.runIfIdle {
            dispatches += "second:start"
            dispatches += "second:enter"
        }
        assertFalse(secondAccepted)
        assertEquals(listOf("first:start"), dispatches)

        release.complete(Unit)
        assertTrue(first.await())
        assertTrue(gate.runIfIdle { dispatches += "next:start" })
        assertEquals(listOf("first:start", "first:enter", "next:start"), dispatches)
    }
}
