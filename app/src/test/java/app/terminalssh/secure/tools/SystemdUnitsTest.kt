package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemdUnitsTest {

    // Built with a normal string so the leading bullet is the real character, not "\u25CF".
    private val sample =
        "\u25CF nginx.service    loaded active running A high performance web server\n" +
            "  ssh.service      loaded active running OpenBSD Secure Shell server\n" +
            "  dead.service     loaded failed failed  a unit that gave up"

    @Test fun parsesUnitsIncludingTheFailedMarker() {
        val units = SystemdUnits.parse(sample)
        assertEquals(3, units.size)
        assertEquals("nginx.service", units[0].name)
        assertTrue(units[0].isRunning)
        assertEquals("a unit that gave up", units[2].description)
        assertTrue(units[2].isFailed)
    }

    @Test fun blankAndNonUnitLinesAreIgnored() {
        assertTrue(SystemdUnits.parse("\nUnit foo bar\n").isEmpty())
    }

    @Test fun unitNameValidationRejectsShellMetacharacters() {
        assertTrue(SystemdUnits.isValidUnit("nginx.service"))
        assertTrue(SystemdUnits.isValidUnit("user@1000.service"))
        assertFalse(SystemdUnits.isValidUnit("nginx; rm -rf /"))
        assertFalse(SystemdUnits.isValidUnit(""))
    }

    @Test fun actionCommandRefusesAnInvalidUnit() {
        assertNull(SystemdUnits.actionCommand(SystemdUnits.Action.STOP, "bad;unit"))
    }

    @Test fun mutatingActionsAreMarked() {
        val stop = SystemdUnits.actionCommand(SystemdUnits.Action.STOP, "nginx.service")!!
        assertTrue(stop.mutating)
        assertTrue(stop.text.startsWith("systemctl stop"))
        assertEquals("'nginx.service'", stop.text.substringAfter("stop "))
    }

    @Test fun statusIsReadOnly() {
        val status = SystemdUnits.actionCommand(SystemdUnits.Action.STATUS, "nginx.service")!!
        assertFalse(status.mutating)
    }
}
