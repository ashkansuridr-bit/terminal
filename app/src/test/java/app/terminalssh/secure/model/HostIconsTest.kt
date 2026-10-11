package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostIconsTest {

    @Test fun knownKeysResolveToAGlyph() {
        assertEquals("\uD83D\uDDA5", HostIcons.fromKey("server")?.glyph)
        assertEquals("\uD83D\uDC33", HostIcons.fromKey("container")?.glyph)
    }

    @Test fun keysAreUnique() {
        val keys = HostIcons.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun unknownKeyFallsBackToTheInitial() {
        assertEquals("W", HostIcons.avatarText("nope", "web-01"))
    }

    @Test fun chosenIconOverridesTheInitial() {
        assertEquals("\uD83D\uDDC4", HostIcons.avatarText("database", "db-01"))
    }

    @Test fun blankNameDoesNotCrash() {
        assertEquals("?", HostIcons.avatarText("", "   "))
    }

    @Test fun noneIsKnown() {
        assertTrue(HostIcons.isKnown(HostIcons.NONE))
        assertTrue(HostIcons.isKnown("server"))
        assertFalse(HostIcons.isKnown("bogus"))
        assertNull(HostIcons.fromKey("bogus"))
    }
}
