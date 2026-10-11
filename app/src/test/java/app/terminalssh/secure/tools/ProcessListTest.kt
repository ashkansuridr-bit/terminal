package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessListTest {

    private val sample = """
        PID USER      %CPU %MEM    RSS STAT COMMAND
        1234 root      12.5  3.2  65536 S    nginx
         567 www-data   1.0  0.5   8192 S    php-fpm
    """.trimIndent()

    @Test fun parsesProcessRowsAndSkipsTheHeader() {
        val rows = ProcessList.parse(sample)
        assertEquals(2, rows.size)
        assertEquals(1234, rows[0].pid)
        assertEquals("root", rows[0].user)
        assertEquals(12.5, rows[0].cpuPercent)
        assertEquals(65536L, rows[0].rssKb)
        assertEquals("nginx", rows[0].command)
        assertEquals(567, rows[1].pid)
    }

    @Test fun malformedRowsAreIgnored() {
        val rows = ProcessList.parse("garbage line\n\nnot a process")
        assertTrue(rows.isEmpty())
    }

    @Test fun listCommandIsReadOnly() {
        val cmd = ProcessList.listCommand()
        assertTrue(cmd.contains("ps -eo"))
        assertTrue(cmd.contains("head -n"))
    }

    @Test fun signalCommandRejectsNonPositivePid() {
        assertNull(ProcessList.signalCommand(0))
        assertNull(ProcessList.signalCommand(-5))
    }

    @Test fun signalCommandIsBuiltFromAnAllowListedSignal() {
        val cmd = ProcessList.signalCommand(1234, ProcessList.Signal.TERM)!!
        assertEquals("kill -TERM 1234", cmd.text)
        assertTrue(cmd.isMutating)
    }

    @Test fun killCarriesADistinctExplanation() {
        val cmd = ProcessList.signalCommand(42, ProcessList.Signal.KILL)!!
        assertEquals("kill -KILL 42", cmd.text)
        assertTrue(cmd.reason.contains("cannot be caught"))
    }
}
