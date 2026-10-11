package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputExportTest {

    private val lines = listOf("one", "two", "three", "four")

    @Test fun sliceIsInclusiveAndClamped() {
        assertEquals("two\nthree", OutputExport.slice(lines, 1, 2).text)
        assertEquals("four", OutputExport.slice(lines, 3, 9).text)
    }

    @Test fun wholeReturnsEveryLine() {
        assertEquals("one\ntwo\nthree\nfour", OutputExport.whole(lines).text)
        assertEquals(4, OutputExport.whole(lines).lineCount)
        assertFalse(OutputExport.whole(lines).truncated)
    }

    @Test fun byteCapTruncatesRatherThanDroppingSilently() {
        val slice = OutputExport.whole(listOf("aaaa", "bbbb", "cccc"), maxBytes = 10)
        assertEquals("aaaa\nbbbb", slice.text)
        assertEquals(2, slice.lineCount)
        assertTrue(slice.truncated)
    }

    @Test fun aSingleLineLongerThanTheCapKeepsABoundedPrefix() {
        val slice = OutputExport.whole(listOf("0123456789"), maxBytes = 4)
        assertEquals("0123", slice.text)
        assertTrue(slice.truncated)
        assertTrue(slice.bytes <= 4)
    }

    @Test fun emptyInputIsEmpty() {
        val slice = OutputExport.whole(emptyList())
        assertEquals("", slice.text)
        assertEquals(0, slice.lineCount)
        assertFalse(slice.truncated)
    }

    @Test fun secretLookingOutputIsFlagged() {
        assertTrue(OutputExport.whole(listOf("export API_KEY=12345")).mayContainSecrets)
        assertTrue(OutputExport.whole(listOf("curl -H 'Authorization: Bearer x'")).mayContainSecrets)
        assertTrue(OutputExport.looksSensitive("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertFalse(OutputExport.whole(listOf("total 0", "drwxr-xr-x")).mayContainSecrets)
    }
}
