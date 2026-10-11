package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * F026: the read-only preview has to decide text-vs-binary from the bytes and stay honest about
 * truncation — the two ways the old "always decode as text" path went wrong.
 */
class PreviewContentTest {

    @Test
    fun plainAsciiIsShownAsText() {
        val view = PreviewContent.of("hello\nworld".toByteArray())
        assertIs<PreviewContent.View.Text>(view)
        assertEquals("hello\nworld", view.text)
        assertFalse(view.truncated)
        assertEquals(11, view.bytesDecoded)
    }

    @Test
    fun accentedUtf8StaysTextRatherThanBeingMistakenForBinary() {
        val view = PreviewContent.of("héllo wörld".toByteArray())
        assertIs<PreviewContent.View.Text>(view)
        assertEquals("héllo wörld", view.text)
    }

    @Test
    fun aNullByteSwitchesToHex() {
        val view = PreviewContent.of(byteArrayOf(0, 0x41))
        assertIs<PreviewContent.View.Hex>(view)
        assertEquals(2, view.bytesShown)
        assertFalse(view.truncated)
    }

    @Test
    fun hexRowsCarryTheOffsetAndAsciiColumn() {
        val view = PreviewContent.of(byteArrayOf(0, 0x41))
        assertIs<PreviewContent.View.Hex>(view)
        assertEquals(1, view.lines.size)
        assertTrue(view.lines[0].startsWith("00000000"), "row must start with the offset")
        assertTrue(view.lines[0].contains("|.A|"), "unprintable bytes must show as dots")
    }

    @Test
    fun aCappedTextSampleIsReportedAsTruncated() {
        val view = PreviewContent.of("abcdef".toByteArray(), maxBytes = 3)
        assertIs<PreviewContent.View.Text>(view)
        assertEquals("abc", view.text)
        assertEquals(3, view.bytesDecoded)
        assertTrue(view.truncated)
    }

    @Test
    fun aCappedHexSampleIsReportedAsTruncated() {
        val bytes = ByteArray(17)
        bytes[0] = 0
        val view = PreviewContent.of(bytes, maxBytes = 16)
        assertIs<PreviewContent.View.Hex>(view)
        assertEquals(1, view.lines.size)
        assertEquals(16, view.bytesShown)
        assertTrue(view.truncated)
    }

    @Test
    fun aSampleThatFitsIsStillTruncatedWhenTheFetchStoppedEarly() {
        val view = PreviewContent.of(byteArrayOf('a'.code.toByte()), maxBytes = 64, sourceTruncated = true)
        assertIs<PreviewContent.View.Text>(view)
        assertTrue(view.truncated)
    }

    @Test
    fun anEmptyFileIsAnEmptyTextPreview() {
        val view = PreviewContent.of(ByteArray(0))
        assertIs<PreviewContent.View.Text>(view)
        assertEquals("", view.text)
        assertFalse(view.truncated)
        assertEquals(0, view.bytesDecoded)
    }
}
