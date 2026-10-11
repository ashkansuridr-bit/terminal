package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HexPreviewTest {

    @Test fun plainAsciiIsNotBinary() {
        assertFalse(HexPreview.isProbablyBinary("hello\nworld\t!".toByteArray()))
    }

    @Test fun aNullByteMeansBinary() {
        assertTrue(HexPreview.isProbablyBinary(byteArrayOf(0x41, 0x00, 0x42)))
    }

    @Test fun mostlyNonPrintableBytesMeanBinary() {
        val bytes = ByteArray(64) { 0x01 }
        assertTrue(HexPreview.isProbablyBinary(bytes))
    }

    @Test fun utf8TextIsNotBinary() {
        // Persian text as UTF-8 has high bytes, which must count as printable.
        assertFalse(HexPreview.isProbablyBinary("سرور من".toByteArray(Charsets.UTF_8)))
    }

    @Test fun decodeTextReportsTruncation() {
        val bytes = "0123456789".toByteArray()
        val preview = HexPreview.decodeText(bytes, maxBytes = 4)
        assertEquals("0123", preview.text)
        assertEquals(4, preview.bytesDecoded)
        assertTrue(preview.truncated)
    }

    @Test fun decodeTextHandlesTheWholeBuffer() {
        val preview = HexPreview.decodeText("abc".toByteArray(), maxBytes = 100)
        assertEquals("abc", preview.text)
        assertFalse(preview.truncated)
    }

    @Test fun hexDumpFormatsOffsetHexAndAscii() {
        val lines = HexPreview.hexDump("Hi!".toByteArray())
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("00000000  "), lines[0])
        assertTrue(lines[0].contains("48 69 21"), lines[0])
        assertTrue(lines[0].endsWith("|Hi!|"), lines[0])
    }

    @Test fun hexDumpUsesDotsForNonPrintableBytes() {
        val lines = HexPreview.hexDump(byteArrayOf(0x41, 0x00, 0x42))
        assertTrue(lines[0].endsWith("|A.B|"), lines[0])
    }

    @Test fun hexDumpSplitsLongInputAcrossLines() {
        val lines = HexPreview.hexDump(ByteArray(20))
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("00000010  "))
    }

    @Test fun hexDumpRespectsTheByteCap() {
        val lines = HexPreview.hexDump(ByteArray(100), maxBytes = 16)
        assertEquals(1, lines.size)
    }
}
