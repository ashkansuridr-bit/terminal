package app.terminalssh.secure.toolbar

import kotlinx.coroutines.delay

/** A single executable macro instruction, produced by [MacroEngine.expand]. */
sealed interface MacroOp {
    data class SendText(val text: String) : MacroOp
    data class SendSpecial(val key: ToolbarSpecialKey) : MacroOp
    data class SendControl(val letter: Char) : MacroOp
    data class SetModifier(val modifier: ToolbarModifier, val lock: Boolean) : MacroOp
    data class Wait(val millis: Int) : MacroOp
}

sealed interface MacroValidation {
    object Valid : MacroValidation
    data class Invalid(val errors: List<String>) : MacroValidation

    val isValid: Boolean get() = this is Valid
}

/** Where a macro's instructions go. Implemented by the session dispatcher, faked in tests. */
interface MacroSink {
    fun sendText(text: String)
    fun sendSpecial(key: ToolbarSpecialKey)
    fun sendControl(letter: Char)
    fun setModifier(modifier: ToolbarModifier, lock: Boolean)
}

/**
 * Turns a [MacroDef] into a checked list of operations, and runs them safely.
 *
 * Macros are the most dangerous surface in the toolbar — an unbounded loop, a runaway
 * payload or a step that keeps firing after a disconnect is a real hazard. So the engine
 * validates before planning, caps every quantity, and exposes cancellation that stops the
 * remaining steps rather than racing them.
 */
object MacroEngine {

    const val MAX_STEPS = 64
    const val MAX_TEXT_LENGTH = 4096
    const val MAX_TOTAL_TEXT = 16_384
    const val MAX_STEP_DELAY_MS = 5_000
    const val MAX_TOTAL_DELAY_MS = 30_000

    fun validate(def: MacroDef): MacroValidation {
        val errors = mutableListOf<String>()
        if (def.steps.isEmpty()) errors.add("macro has no steps")
        if (def.steps.size > MAX_STEPS) errors.add("macro has more than $MAX_STEPS steps")
        var totalText = 0
        var totalDelay = 0
        def.steps.forEachIndexed { index, step ->
            when (step) {
                is MacroStep.Text -> {
                    if (step.text.length > MAX_TEXT_LENGTH) {
                        errors.add("step ${index + 1}: text is too long")
                    }
                    totalText += step.text.length
                }
                is MacroStep.Controls -> {
                    if (!isValidControl(step.letter)) {
                        errors.add("step ${index + 1}: '${step.letter}' is not a control letter")
                    }
                }
                is MacroStep.Delay -> {
                    if (step.millis !in 0..MAX_STEP_DELAY_MS) {
                        errors.add("step ${index + 1}: delay is out of range")
                    }
                    totalDelay += step.millis
                }
                is MacroStep.Special, is MacroStep.Modifier -> Unit
            }
        }
        if (totalText > MAX_TOTAL_TEXT) errors.add("macro pastes too much text")
        if (totalDelay > MAX_TOTAL_DELAY_MS) errors.add("macro waits too long in total")
        return if (errors.isEmpty()) MacroValidation.Valid else MacroValidation.Invalid(errors)
    }

    /** Expands a (validated) macro. Delays stay as [MacroOp.Wait] so the runner controls timing. */
    fun expand(def: MacroDef): List<MacroOp> = def.steps.map { step ->
        when (step) {
            is MacroStep.Text -> MacroOp.SendText(step.text)
            is MacroStep.Special -> MacroOp.SendSpecial(step.key)
            is MacroStep.Controls -> MacroOp.SendControl(step.letter.uppercaseChar())
            is MacroStep.Modifier -> MacroOp.SetModifier(step.modifier, step.lock)
            is MacroStep.Delay -> MacroOp.Wait(step.millis)
        }
    }

    fun isValidControl(letter: Char): Boolean {
        val code = letter.uppercaseChar().code - '@'.code
        return code in 1..31
    }

    /** A short human description of what a macro will do, for confirmations and previews. */
    fun describe(def: MacroDef): String {
        if (def.steps.isEmpty()) return "no steps"
        return def.steps.joinToString(" · ") { step ->
            when (step) {
                is MacroStep.Text -> "\"${step.text.replace("\n", "\\n")}\""
                is MacroStep.Special -> step.key.name.lowercase().replace('_', ' ')
                is MacroStep.Controls -> "^${step.letter.uppercaseChar()}"
                is MacroStep.Modifier -> step.modifier.name.lowercase() + if (step.lock) " (lock)" else ""
                is MacroStep.Delay -> "wait ${step.millis}ms"
            }
        }
    }

    fun totalDelayMillis(def: MacroDef): Int =
        def.steps.filterIsInstance<MacroStep.Delay>().sumOf { it.millis }
}

/**
 * Executes expanded macro operations against a [MacroSink].
 *
 * [execute] checks [cancelled] before every step, so cancellation stops the sequence
 * immediately instead of finishing it. It is used by the UI layer with a real coroutine;
 * [runImmediate] covers the delay-free path and is what the unit tests exercise.
 */
class MacroRunner(private val sink: MacroSink) {

    /** Runs every non-delay op in order. Returns false when cancelled before the first step. */
    fun runImmediate(ops: List<MacroOp>, cancelled: () -> Boolean = { false }): Boolean {
        for (op in ops) {
            if (cancelled()) return false
            when (op) {
                is MacroOp.SendText -> sink.sendText(op.text)
                is MacroOp.SendSpecial -> sink.sendSpecial(op.key)
                is MacroOp.SendControl -> sink.sendControl(op.letter)
                is MacroOp.SetModifier -> sink.setModifier(op.modifier, op.lock)
                is MacroOp.Wait -> Unit // delays are honoured by execute(); immediate mode skips them
            }
        }
        return true
    }

    /** Full execution honouring delays and cancellation. */
    suspend fun execute(
        ops: List<MacroOp>,
        cancelled: () -> Boolean = { false },
        sleep: suspend (Int) -> Unit = { delay(it.toLong()) },
    ): Boolean {
        for (op in ops) {
            if (cancelled()) return false
            when (op) {
                is MacroOp.SendText -> sink.sendText(op.text)
                is MacroOp.SendSpecial -> sink.sendSpecial(op.key)
                is MacroOp.SendControl -> sink.sendControl(op.letter)
                is MacroOp.SetModifier -> sink.setModifier(op.modifier, op.lock)
                is MacroOp.Wait -> if (op.millis > 0) sleep(op.millis)
            }
        }
        return !cancelled()
    }
}
