package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyToolbarConfigTest {

    @Test fun defaultIsNonEmptyAndHasNoDuplicates() {
        assertTrue(KeyToolbarConfig.default.isNotEmpty())
        assertEquals(KeyToolbarConfig.default.size, KeyToolbarConfig.default.toSet().size)
        assertTrue(KeyToolbarConfig.Key.CTRL in KeyToolbarConfig.default)
        assertTrue(KeyToolbarConfig.Key.ESC in KeyToolbarConfig.default)
    }

    @Test fun parseDropsUnknownAndDuplicateTokens() {
        val parsed = KeyToolbarConfig.parse("esc,tab,tab,teleport,ctrl")
        assertEquals(
            listOf(KeyToolbarConfig.Key.ESC, KeyToolbarConfig.Key.TAB, KeyToolbarConfig.Key.CTRL),
            parsed,
        )
    }

    @Test fun parseIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            listOf(KeyToolbarConfig.Key.ESC, KeyToolbarConfig.Key.CTRL),
            KeyToolbarConfig.parse("  ESC , Ctrl "),
        )
    }

    @Test fun anEmptyOrUnusableValueFallsBackToTheDefault() {
        assertEquals(KeyToolbarConfig.default, KeyToolbarConfig.parse(""))
        assertEquals(KeyToolbarConfig.default, KeyToolbarConfig.parse("garbage,unknown"))
    }

    @Test fun serializeRoundTrips() {
        val keys = listOf(KeyToolbarConfig.Key.PIPE, KeyToolbarConfig.Key.DASH, KeyToolbarConfig.Key.CTRL)
        assertEquals(keys, KeyToolbarConfig.parse(KeyToolbarConfig.serialize(keys)))
    }

    @Test fun toggleAddsThenRemoves() {
        val base = listOf(KeyToolbarConfig.Key.ESC)
        val added = KeyToolbarConfig.toggle(base, KeyToolbarConfig.Key.PIPE)
        assertEquals(listOf(KeyToolbarConfig.Key.ESC, KeyToolbarConfig.Key.PIPE), added)
        assertEquals(base, KeyToolbarConfig.toggle(added, KeyToolbarConfig.Key.PIPE))
    }

    @Test fun moveReordersAndClampsTheDestination() {
        val keys = listOf(KeyToolbarConfig.Key.ESC, KeyToolbarConfig.Key.TAB, KeyToolbarConfig.Key.CTRL)
        assertEquals(
            listOf(KeyToolbarConfig.Key.TAB, KeyToolbarConfig.Key.ESC, KeyToolbarConfig.Key.CTRL),
            KeyToolbarConfig.move(keys, 0, 1),
        )
        assertEquals(
            listOf(KeyToolbarConfig.Key.TAB, KeyToolbarConfig.Key.CTRL, KeyToolbarConfig.Key.ESC),
            KeyToolbarConfig.move(keys, 0, 99),
        )
    }

    @Test fun moveWithABadSourceIndexIsANoOp() {
        val keys = listOf(KeyToolbarConfig.Key.ESC)
        assertEquals(keys, KeyToolbarConfig.move(keys, 5, 0))
    }

    @Test fun resetReturnsTheDefault() {
        assertEquals(KeyToolbarConfig.default, KeyToolbarConfig.reset())
    }
}
