package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacroEngineTest {

    private class RecordingSink : MacroSink {
        val calls = mutableListOf<String>()
        override fun sendText(text: String) { calls += "text:$text" }
        override fun sendSpecial(key: ToolbarSpecialKey) { calls += "special:${key.name}" }
        override fun sendControl(letter: Char) { calls += "ctrl:$letter" }
        override fun setModifier(modifier: ToolbarModifier, lock: Boolean) { calls += "mod:${modifier.name}:$lock" }
    }

    private fun macro(vararg steps: MacroStep) = MacroDef(id = "m", name = "M", steps = steps.toList())

    @Test fun emptyMacroIsInvalid() {
        assertFalse(MacroEngine.validate(macro()).isValid)
    }

    @Test fun wellFormedMacroIsValid() {
        val def = macro(MacroStep.Text("ls"), MacroStep.Special(ToolbarSpecialKey.ENTER))
        assertTrue(MacroEngine.validate(def).isValid)
    }

    @Test fun tooManyStepsRejected() {
        val steps = Array(MacroEngine.MAX_STEPS + 1) { MacroStep.Text("x") }
        assertFalse(MacroEngine.validate(MacroDef("m", "M", steps = steps.toList())).isValid)
    }

    @Test fun oversizedTextRejected() {
        val def = macro(MacroStep.Text("x".repeat(MacroEngine.MAX_TEXT_LENGTH + 1)))
        assertFalse(MacroEngine.validate(def).isValid)
    }

    @Test fun totalTextBudgetEnforced() {
        val chunk = "x".repeat(MacroEngine.MAX_TEXT_LENGTH)
        val steps = List(8) { MacroStep.Text(chunk) } // 8 * 4096 = 32768 > 16384
        assertFalse(MacroEngine.validate(MacroDef("m", "M", steps = steps)).isValid)
    }

    @Test fun invalidControlLetterRejected() {
        assertFalse(MacroEngine.validate(macro(MacroStep.Controls('1'))).isValid)
    }

    @Test fun delayRangeAndTotalEnforced() {
        assertFalse(MacroEngine.validate(macro(MacroStep.Delay(MacroEngine.MAX_STEP_DELAY_MS + 1))).isValid)
        val steps = List(10) { MacroStep.Delay(MacroEngine.MAX_STEP_DELAY_MS) } // 50s > 30s
        assertFalse(MacroEngine.validate(MacroDef("m", "M", steps = steps)).isValid)
    }

    @Test fun controlLetterValidityBoundaries() {
        listOf('A', 'Z', 'a', 'z', '[', '_').forEach { assertTrue(MacroEngine.isValidControl(it), "$it") }
        listOf('@', '0', ' ', '{', '~').forEach { assertFalse(MacroEngine.isValidControl(it), "$it") }
    }

    @Test fun expandKeepsOrderAndUppercasesControls() {
        val def = macro(
            MacroStep.Text("echo hi"),
            MacroStep.Special(ToolbarSpecialKey.ENTER),
            MacroStep.Controls('c'),
            MacroStep.Delay(50),
        )
        val ops = MacroEngine.expand(def)
        assertEquals(
            listOf(
                MacroOp.SendText("echo hi"),
                MacroOp.SendSpecial(ToolbarSpecialKey.ENTER),
                MacroOp.SendControl('C'),
                MacroOp.Wait(50),
            ),
            ops,
        )
    }

    @Test fun describeAndTotalDelay() {
        val def = macro(MacroStep.Text("ls\n"), MacroStep.Controls('l'), MacroStep.Delay(20), MacroStep.Delay(30))
        assertEquals("wait 50ms", "wait ${MacroEngine.totalDelayMillis(def)}ms")
        assertTrue(MacroEngine.describe(def).contains("^L"))
    }

    @Test fun runImmediateDispatchesInOrderAndSkipsWaits() {
        val sink = RecordingSink()
        val def = macro(
            MacroStep.Text("ab"),
            MacroStep.Modifier(ToolbarModifier.CTRL, lock = true),
            MacroStep.Special(ToolbarSpecialKey.TAB),
            MacroStep.Controls('d'),
            MacroStep.Delay(1000),
        )
        val ok = MacroRunner(sink).runImmediate(MacroEngine.expand(def))
        assertTrue(ok)
        assertEquals(
            listOf("text:ab", "mod:CTRL:true", "special:TAB", "ctrl:D"),
            sink.calls,
        )
    }

    @Test fun runImmediateCancelledBeforeFirstStepDoesNothing() {
        val sink = RecordingSink()
        val ok = MacroRunner(sink).runImmediate(MacroEngine.expand(macro(MacroStep.Text("x")))) { true }
        assertFalse(ok)
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun runImmediateStopsWhenCancelledMidway() {
        val sink = RecordingSink()
        var count = 0
        val ops = MacroEngine.expand(macro(MacroStep.Text("1"), MacroStep.Text("2"), MacroStep.Text("3")))
        MacroRunner(sink).runImmediate(ops) { count++ >= 1 }
        assertEquals(listOf("text:1"), sink.calls)
    }

    @Test fun shippedMacrosAllValidate() {
        ToolbarMacros.all.forEach { macro ->
            val validation = MacroEngine.validate(macro)
            assertTrue(validation.isValid, "shipped macro ${macro.id}: $validation")
        }
    }
}
