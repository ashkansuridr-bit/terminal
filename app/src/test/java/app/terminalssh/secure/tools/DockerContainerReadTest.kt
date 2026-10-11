package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F045: reading container stats/inspect is safe; only the command build and parse are pure. */
class DockerContainerReadTest {

    @Test
    fun statsIsAOneShotReadNotALiveStream() {
        val cmd = DockerContainers.statsCommand("web")!!
        assertTrue(cmd.contains("docker stats --no-stream"), cmd)
        assertTrue(cmd.contains("'web'"), cmd)
    }

    @Test
    fun inspectTargetsTheContainer() {
        assertEquals("LC_ALL=C docker inspect 'web' 2>&1", DockerContainers.inspectCommand("web"))
    }

    @Test
    fun invalidTargetsAreRejectedRatherThanQuotedIntoTheShell() {
        assertNull(DockerContainers.statsCommand("bad target"))
        assertNull(DockerContainers.inspectCommand("a;b"))
        assertNull(DockerContainers.inspectCommand(""))
    }

    @Test
    fun statsParsesTheFormattedLine() {
        val stats = DockerContainers.parseStats("1.23%\t12MiB / 1GiB\t1.17%\t4")!!
        assertEquals("1.23%", stats.cpuPercent)
        assertEquals("12MiB / 1GiB", stats.memUsage)
        assertEquals("1.17%", stats.memPercent)
        assertEquals("4", stats.pids)
    }

    @Test
    fun statsRejectsEmptyOrUnformattedOutput() {
        assertNull(DockerContainers.parseStats(""))
        assertNull(DockerContainers.parseStats("   "))
        assertNull(DockerContainers.parseStats("no tabs here"))
    }
}
