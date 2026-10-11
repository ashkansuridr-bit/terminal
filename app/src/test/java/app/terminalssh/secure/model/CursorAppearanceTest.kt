package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorAppearanceTest {

    @Test fun knownShapesAreRecognized() {
        assertEquals(CursorShape.BLOCK, CursorShape.fromStored("block"))
        assertEquals(CursorShape.BAR, CursorShape.fromStored("bar"))
        assertEquals(CursorShape.UNDERLINE, CursorShape.fromStored("underline"))
    }

    @Test fun parsingIsCaseAndWhitespaceInsensitive() {
        assertEquals(CursorShape.BAR, CursorShape.fromStored("  BAR "))
        assertEquals(CursorShape.UNDERLINE, CursorShape.fromStored("\tUnderline"))
    }

    @Test fun unknownOrBlankFallsBackToTheDefault() {
        assertEquals(CursorShape.default, CursorShape.fromStored("teleport"))
        assertEquals(CursorShape.default, CursorShape.fromStored(null))
        assertEquals(CursorShape.default, CursorShape.fromStored(""))
        assertEquals(CursorShape.default, CursorShape.fromStored("   "))
    }

    @Test fun defaultIsBlockAndBlinking() {
        assertEquals(CursorShape.BLOCK, CursorAppearance.default.shape)
        assertTrue(CursorAppearance.default.blink)
    }

    @Test fun fromStoredBuildsThePair() {
        val appearance = CursorAppearance.fromStored("underline", blink = false)
        assertEquals(CursorShape.UNDERLINE, appearance.shape)
        assertFalse(appearance.blink)
    }

    @Test fun storedStringsAreStableAndLowercase() {
        for (shape in CursorShape.entries) {
            assertEquals(shape.stored, shape.stored.lowercase())
            assertEquals(shape, CursorShape.fromStored(shape.stored))
        }
    }
}
