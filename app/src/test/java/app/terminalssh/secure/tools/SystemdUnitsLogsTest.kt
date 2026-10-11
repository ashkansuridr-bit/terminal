package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F044: service logs are a read-only tail, so the command never mutates and never floods. */
class SystemdUnitsLogsTest {

    @Test
    fun logsCommandTargetsTheUnitAndBoundsLines() {
        val cmd = SystemdUnits.logsCommand("nginx.service", tailLines = 50)!!
        assertTrue(cmd.contains("journalctl -u 'nginx.service'"), cmd)
        assertTrue(cmd.contains("-n 50"), cmd)
        assertTrue(cmd.contains("--no-pager"), cmd)
        // It must remain a read, not an action against the service.
        assertTrue(!cmd.contains("systemctl"), cmd)
    }

    @Test
    fun tailLinesAreClampedToAUsefulRange() {
        assertTrue(SystemdUnits.logsCommand("a.service", tailLines = 0)!!.contains("-n 1"))
        assertTrue(SystemdUnits.logsCommand("a.service", tailLines = -5)!!.contains("-n 1"))
        assertTrue(
            SystemdUnits.logsCommand("a.service", tailLines = 999_999)!!
                .contains("-n ${SystemdUnits.MAX_LOG_LINES}"),
        )
    }

    @Test
    fun anInvalidUnitIsRejectedRatherThanQuotedIntoTheShell() {
        assertNull(SystemdUnits.logsCommand("bad unit"))
        assertNull(SystemdUnits.logsCommand("a;rm -rf /"))
        assertNull(SystemdUnits.logsCommand(""))
    }
}
