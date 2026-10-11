package app.terminalssh.secure.tools

import app.terminalssh.secure.agents.DangerousCommand

/**
 * Safe Runbook / ordered automation (F050).
 *
 * A runbook is a named, ordered list of commands — "deploy", "restart the stack" — that a
 * user runs the same way every time. The danger is that automation on a phone is a single
 * tap away from running destructive commands unattended. So the model enforces three rules:
 *
 *  1. Every step is previewed before anything runs ([preview] is a dry-run).
 *  2. A step the [DangerousCommand] detector flags is marked [Preview.requiresConfirmation]
 *     and the runner will not advance past it without an explicit acknowledgement.
 *  3. A non-zero exit stops the run by default ([RunState.halted]); later steps do not fire
 *     against a server that is already in a bad state.
 *
 * Pure state machine: the caller owns the SSH channel and feeds exit codes back in.
 */
object Runbook {

    data class Step(val id: String, val command: String, val note: String = "")

    data class Book(val id: String, val name: String, val steps: List<Step>)

    enum class Outcome { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED }

    sealed interface Preview {
        val step: Step

        data class Safe(override val step: Step) : Preview
        data class NeedsConfirmation(override val step: Step, val reasons: List<String>) : Preview
    }

    data class RunState(
        val index: Int = 0,
        val outcomes: List<Outcome>,
        val halted: Boolean = false,
    ) {
        val total: Int get() = outcomes.size
        val isComplete: Boolean get() = index >= outcomes.size
        val currentIndex: Int? get() = index.takeIf { it < outcomes.size }
    }

    /** A dry-run: what each step will do, and which ones need an explicit confirmation. */
    fun preview(book: Book): List<Preview> = book.steps.map { step ->
        val finding = DangerousCommand.inspect(step.command)
        if (finding == null) {
            Preview.Safe(step)
        } else {
            Preview.NeedsConfirmation(step, listOf("${finding.severity}: ${finding.reason}"))
        }
    }

    fun start(book: Book): RunState =
        RunState(index = 0, outcomes = List(book.steps.size) { Outcome.PENDING })

    fun current(book: Book, state: RunState): Step? =
        state.currentIndex?.let { book.steps.getOrNull(it) }

    /** Marks the current step running. Does not advance; the caller now executes it. */
    fun markRunning(state: RunState): RunState =
        stepOutcome(state, Outcome.RUNNING)

    /**
     * Records the result of the current step and advances.
     *
     * @param confirmed must be true for a step the detector flagged; otherwise the step is
     *   skipped and the run halts, so a mis-tap cannot run a destructive command.
     * @param stopOnFailure when true (the default) a non-zero [exitCode] halts the run.
     */
    fun onResult(
        book: Book,
        state: RunState,
        exitCode: Int,
        confirmed: Boolean = true,
        stopOnFailure: Boolean = true,
    ): RunState {
        val step = current(book, state) ?: return state
        val needsConfirmation = preview(book)[state.index] is Preview.NeedsConfirmation
        if (needsConfirmation && !confirmed) {
            return advance(state.copy(halted = true), Outcome.SKIPPED)
        }
        val outcome = if (exitCode == 0) Outcome.SUCCEEDED else Outcome.FAILED
        val advanced = advance(state, outcome)
        return if (exitCode != 0 && stopOnFailure) advanced.copy(halted = true) else advanced
    }

    /** Skips the current step without running it and keeps going. */
    fun skip(state: RunState): RunState = advance(state, Outcome.SKIPPED)

    /** Force-continues after a halted failed step, when the user insists. */
    fun resumeAfterFailure(state: RunState): RunState = state.copy(halted = false)

    private fun advance(state: RunState, outcome: Outcome): RunState = state.copy(
        index = state.index + 1,
        outcomes = state.outcomes.toMutableList().also { it[state.index] = outcome },
    )

    private fun stepOutcome(state: RunState, outcome: Outcome): RunState {
        if (state.index >= state.outcomes.size) return state
        return state.copy(
            outcomes = state.outcomes.toMutableList().also { it[state.index] = outcome },
        )
    }

    /** A one-line progress summary for the run header. */
    fun summary(state: RunState): String {
        val done = state.outcomes.count { it == Outcome.SUCCEEDED }
        val failed = state.outcomes.count { it == Outcome.FAILED }
        return when {
            state.halted && failed > 0 -> "stopped at step ${state.index}, $failed failed"
            state.isComplete -> "finished, $done/${state.total} succeeded"
            else -> "step ${state.index + 1}/${state.total}"
        }
    }
}
