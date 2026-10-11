package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictBatchPolicyTest {

    private val overwrite = ConflictBatchPolicy.Decision.OVERWRITE
    private val skip = ConflictBatchPolicy.Decision.SKIP
    private val ask = ConflictBatchPolicy.Decision.ASK

    @Test fun applyToAllRemembersSoLaterConflictsDoNotPrompt() {
        val resolver = ConflictBatchPolicy.Resolver()
        resolver.answer(overwrite, applyToAll = true)
        assertTrue(resolver.hasRemembered)
        val next = resolver.next()
        assertEquals(overwrite, next.decision)
        assertTrue(next.automatic)
    }

    @Test fun aSingleAnswerWithoutApplyToAllIsNotRemembered() {
        val resolver = ConflictBatchPolicy.Resolver()
        resolver.answer(skip, applyToAll = false)
        assertFalse(resolver.hasRemembered)
        val next = resolver.next()
        assertEquals(ask, next.decision)
        assertFalse(next.automatic)
    }

    @Test fun askIsNeverRememberedEvenWhenApplyToAllIsSet() {
        val resolver = ConflictBatchPolicy.Resolver()
        resolver.answer(ask, applyToAll = true)
        assertFalse(resolver.hasRemembered)
        assertEquals(ask, resolver.next().decision)
    }

    @Test fun forgetClearsTheBatchDecision() {
        val resolver = ConflictBatchPolicy.Resolver()
        resolver.answer(overwrite, applyToAll = true)
        resolver.forget()
        assertFalse(resolver.hasRemembered)
        assertEquals(ask, resolver.next().decision)
    }

    @Test fun aFreshResolverAlwaysAsks() {
        assertEquals(ask, ConflictBatchPolicy.Resolver().next().decision)
    }

    @Test fun theFirstAnswerItselfIsNeverMarkedAutomatic() {
        val resolver = ConflictBatchPolicy.Resolver()
        assertFalse(resolver.answer(overwrite, applyToAll = true).automatic)
    }

    @Test fun autoResolvedCountReflectsAMeaningfulRememberedDecision() {
        assertEquals(5, ConflictBatchPolicy.autoResolvedCount(5, overwrite))
        assertEquals(0, ConflictBatchPolicy.autoResolvedCount(5, ask))
        assertEquals(0, ConflictBatchPolicy.autoResolvedCount(5, null))
        assertEquals(0, ConflictBatchPolicy.autoResolvedCount(0, overwrite))
    }
}
