package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunbookTest {

    private val book = Runbook.Book(
        id = "b1",
        name = "deploy",
        steps = listOf(
            Runbook.Step("s1", "git -C /srv/app pull --ff-only", "update"),
            Runbook.Step("s2", "systemctl stop nginx", "stop the old one"),
            Runbook.Step("s3", "systemctl start nginx", "start the new one"),
        ),
    )

    @Test fun previewFlagsOnlyDangerousSteps() {
        val preview = Runbook.preview(book)
        assertTrue(preview[0] is Runbook.Preview.Safe)
        assertTrue(preview[1] is Runbook.Preview.NeedsConfirmation)
        assertTrue(preview[2] is Runbook.Preview.Safe)
    }

    @Test fun everyStepStartsPending() {
        val state = Runbook.start(book)
        assertEquals(listOf(Runbook.Outcome.PENDING, Runbook.Outcome.PENDING, Runbook.Outcome.PENDING), state.outcomes)
        assertEquals(0, state.index)
        assertFalse(state.isComplete)
    }

    @Test fun safeStepAdvancesOnSuccess() {
        var state = Runbook.start(book)
        state = Runbook.onResult(book, state, exitCode = 0)
        assertEquals(1, state.index)
        assertEquals(Runbook.Outcome.SUCCEEDED, state.outcomes[0])
        assertFalse(state.halted)
    }

    @Test fun dangerousStepIsSkippedAndHaltsIfNotConfirmed() {
        var state = Runbook.start(book)
        state = Runbook.onResult(book, state, exitCode = 0) // s1 safe
        state = Runbook.onResult(book, state, exitCode = 0, confirmed = false) // s2 dangerous
        assertTrue(state.halted)
        assertEquals(Runbook.Outcome.SKIPPED, state.outcomes[1])
        assertEquals(2, state.index)
    }

    @Test fun dangerousStepRunsWhenConfirmed() {
        var state = Runbook.start(book)
        state = Runbook.onResult(book, state, exitCode = 0)
        state = Runbook.onResult(book, state, exitCode = 0, confirmed = true)
        assertEquals(Runbook.Outcome.SUCCEEDED, state.outcomes[1])
        assertFalse(state.halted)
    }

    @Test fun failureHaltsByDefault() {
        var state = Runbook.start(book)
        state = Runbook.onResult(book, state, exitCode = 1)
        assertTrue(state.halted)
        assertEquals(Runbook.Outcome.FAILED, state.outcomes[0])
        // Later steps did not run.
        assertEquals(Runbook.Outcome.PENDING, state.outcomes[1])
    }

    @Test fun failureCanBeConfiguredToContinue() {
        var state = Runbook.start(book)
        state = Runbook.onResult(book, state, exitCode = 2, stopOnFailure = false)
        assertFalse(state.halted)
        assertEquals(Runbook.Outcome.FAILED, state.outcomes[0])
    }

    @Test fun resumeAfterFailureClearsTheHalt() {
        var state = Runbook.start(book)
        state = Runbook.onResult(book, state, exitCode = 1)
        assertFalse(Runbook.resumeAfterFailure(state).halted)
    }

    @Test fun skipAdvancesWithoutRunning() {
        val state = Runbook.skip(Runbook.start(book))
        assertEquals(1, state.index)
        assertEquals(Runbook.Outcome.SKIPPED, state.outcomes[0])
    }

    @Test fun currentAndSummaryTrackProgress() {
        var state = Runbook.start(book)
        assertEquals("s1", Runbook.current(book, state)?.id)
        assertEquals("step 1/3", Runbook.summary(state))
        state = Runbook.onResult(book, state, exitCode = 0)
        state = Runbook.onResult(book, state, exitCode = 0, confirmed = true)
        state = Runbook.onResult(book, state, exitCode = 0)
        assertTrue(state.isComplete)
        assertEquals("finished, 3/3 succeeded", Runbook.summary(state))
    }

    @Test fun completeStateHasNoCurrentStep() {
        var state = Runbook.start(Runbook.Book("b", "one", listOf(Runbook.Step("s", "ls"))))
        state = Runbook.onResult(Runbook.Book("b", "one", listOf(Runbook.Step("s", "ls"))), state, exitCode = 0)
        assertEquals(null, Runbook.current(Runbook.Book("b", "one", listOf(Runbook.Step("s", "ls"))), state))
    }
}
