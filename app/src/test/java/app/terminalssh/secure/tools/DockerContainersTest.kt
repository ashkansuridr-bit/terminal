package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockerContainersTest {

    private val sample =
        "abc123def456\tweb\tnginx:latest\tUp 3 hours\trunning\n" +
            "7890abcdef12\tdb\tpostgres:16\tExited (0) 2 hours ago\texited"

    @Test fun parsesTabSeparatedRows() {
        val containers = DockerContainers.parse(sample)
        assertEquals(2, containers.size)
        assertEquals("abc123def456", containers[0].id)
        assertEquals("web", containers[0].name)
        assertEquals("nginx:latest", containers[0].image)
        assertTrue(containers[0].isRunning)
        assertFalse(containers[1].isRunning)
    }

    @Test fun shortIdIsTwelveCharacters() {
        assertEquals("abc123def456", DockerContainers.parse(sample)[0].shortId)
    }

    @Test fun malformedLinesAreIgnored() {
        assertTrue(DockerContainers.parse("docker: command not found\n").isEmpty())
    }

    @Test fun targetValidationRejectsSpacesAndMetacharacters() {
        assertTrue(DockerContainers.isValidTarget("web"))
        assertTrue(DockerContainers.isValidTarget("my-app_1"))
        assertFalse(DockerContainers.isValidTarget("web; rm -rf /"))
        assertFalse(DockerContainers.isValidTarget(""))
    }

    @Test fun actionCommandRefusesBadTargetAndQuotesAGoodOne() {
        assertNull(DockerContainers.actionCommand(DockerContainers.Action.STOP, "a b"))
        val stop = DockerContainers.actionCommand(DockerContainers.Action.STOP, "web")!!
        assertEquals("docker stop 'web'", stop.text)
        assertTrue(stop.isMutating)
    }

    @Test fun logsCommandIsBoundedAndQuoted() {
        assertEquals("docker logs --tail 50 'web' 2>&1", DockerContainers.logsCommand("web", 50))
        assertNull(DockerContainers.logsCommand("bad target"))
    }
}
