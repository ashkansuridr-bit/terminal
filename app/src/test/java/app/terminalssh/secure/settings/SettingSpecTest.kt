package app.terminalssh.secure.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingSpecTest {

    private fun intSpec(default: Int = 14, min: Int = 10, max: Int = 24, step: Int = 1) =
        IntSetting(
            key = "k", default = default, min = min, max = max, step = step,
            titleRes = 1, group = SettingGroup.APPEARANCE,
        )

    @Test fun intSettingClampsOutOfRangeValues() {
        val spec = intSpec()
        assertEquals(10, spec.coerce(-5))
        assertEquals(24, spec.coerce(999))
        assertEquals(14, spec.coerce(14))
    }

    @Test fun intSettingSnapsRelativeToMinNotZero() {
        // With min=10 step=5 the valid values are 10, 15, 20 — not 10, 15, 20 by accident.
        val spec = intSpec(default = 10, min = 10, max = 25, step = 5)
        assertEquals(10, spec.coerce(11))
        assertEquals(15, spec.coerce(13))
        assertEquals(15, spec.coerce(16))
        assertEquals(20, spec.coerce(19))
    }

    @Test fun intSettingSnappingNeverEscapesTheRange() {
        val spec = intSpec(default = 0, min = 0, max = 180, step = 15)
        for (raw in -50..250) {
            val coerced = spec.coerce(raw)
            assertTrue(coerced in 0..180, "coerce($raw) = $coerced escaped the range")
            assertEquals(0, coerced % 15, "coerce($raw) = $coerced is not on a step")
        }
    }

    @Test fun intSettingRejectsAnImpossibleDeclaration() {
        assertFailsWith<IllegalArgumentException> { intSpec(default = 99) }
        assertFailsWith<IllegalArgumentException> { intSpec(min = 30, max = 10) }
        assertFailsWith<IllegalArgumentException> { intSpec(step = 0) }
    }

    @Test fun choiceSettingFallsBackToDefaultForUnknownOptions() {
        val spec = ChoiceSetting(
            key = "c", default = "b", values = listOf("a", "b"),
            titleRes = 1, group = SettingGroup.APPEARANCE,
        )
        assertEquals("a", spec.coerce("a"))
        assertEquals("b", spec.coerce("nonsense"))
        assertTrue(spec.isValid("a"))
        assertFalse(spec.isValid("nonsense"))
    }

    @Test fun choiceSettingRejectsAnImpossibleDeclaration() {
        assertFailsWith<IllegalArgumentException> {
            ChoiceSetting("c", "z", listOf("a", "b"), titleRes = 1, group = SettingGroup.APPEARANCE)
        }
        assertFailsWith<IllegalArgumentException> {
            ChoiceSetting("c", "a", listOf("a", "a"), titleRes = 1, group = SettingGroup.APPEARANCE)
        }
        assertFailsWith<IllegalArgumentException> {
            ChoiceSetting("c", "a", emptyList(), titleRes = 1, group = SettingGroup.APPEARANCE)
        }
    }

    @Test fun textSettingTruncatesRatherThanRejecting() {
        val spec = TextSetting("t", "", maxLength = 5, titleRes = 1, group = SettingGroup.TERMINAL)
        assertEquals("abcde", spec.coerce("abcdefghij"))
        assertFalse(spec.isValid("abcdefghij"))
        assertTrue(spec.isValid("abc"))
    }
}
