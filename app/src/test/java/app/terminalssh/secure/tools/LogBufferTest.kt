package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogBufferTest {

    @Test fun appendsSplitOnNewlines() {
        val buffer = LogBuffer(capacity = 10)
        buffer.append("one\ntwo\nthree")
        assertEquals(listOf("one", "two", "three"), buffer.snapshot())
    }

    @Test fun carriageReturnsAreTrimmed() {
        val buffer = LogBuffer(10)
        buffer.append("a\r\nb\r\n")
        assertEquals(listOf("a", "b"), buffer.snapshot())
    }

    @Test fun capacityIsARingThatDropsTheOldest() {
        val buffer = LogBuffer(capacity = 3)
        buffer.append("1\n2\n3\n4\n5")
        assertEquals(listOf("3", "4", "5"), buffer.snapshot())
        assertEquals(3, buffer.size())
    }

    @Test fun pauseFreezesTheSnapshotAndResumeThawsIt() {
        val buffer = LogBuffer(10)
        buffer.append("before\n")
        buffer.pause()
        buffer.append("during\n")
        assertEquals(listOf("before"), buffer.snapshot())
        buffer.resume()
        buffer.append("after\n")
        assertEquals(listOf("before", "after"), buffer.snapshot())
    }

    @Test fun appendAllRespectsPause() {
        val buffer = LogBuffer(10)
        buffer.pause()
        buffer.appendAll(listOf("x", "y"))
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test fun filterIsCaseInsensitiveAndBlankReturnsAll() {
        val buffer = LogBuffer(10)
        buffer.append("Error: disk full\ninfo: ok\nINFO: ready")
        assertEquals(3, buffer.filter("").size)
        assertEquals(2, buffer.filter("info").size)
        assertEquals(1, buffer.filter("ERROR").size)
    }

    @Test fun clearEmptiesTheRing() {
        val buffer = LogBuffer(10)
        buffer.append("a\nb\n")
        buffer.clear()
        assertTrue(buffer.snapshot().isEmpty())
        assertFalse(buffer.paused)
    }
}
