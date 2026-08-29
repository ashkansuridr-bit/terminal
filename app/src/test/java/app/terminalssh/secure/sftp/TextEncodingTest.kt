package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextEncodingTest {

    @Test fun asciiAndUtf8AreDetectedAsUtf8() {
        assertEquals("UTF-8", TextEncoding.detect("hello".toByteArray()))
        assertEquals("UTF-8", TextEncoding.detect("سلام دنیا".toByteArray(Charsets.UTF_8)))
    }

    @Test fun aByteOrderMarkIsRecognisedAndStripped() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "x=1".toByteArray()
        assertEquals("UTF-8", TextEncoding.detect(bom))
        assertEquals("x=1", TextEncoding.decode(bom, "UTF-8"), "the BOM must not survive as a character")
    }

    @Test fun invalidUtf8FallsBackInsteadOfProducingMojibake() {
        // A Persian config saved as windows-1256 is still common.
        val cp1256 = "سلام".toByteArray(charset("windows-1256"))
        assertFalse(TextEncoding.isValidUtf8(cp1256))
        assertEquals("windows-1256", TextEncoding.detect(cp1256, fallback = "windows-1256"))
        assertEquals("سلام", TextEncoding.decode(cp1256, "windows-1256"))
    }

    @Test fun utf8ValidationRejectsTruncatedAndOverlongSequences() {
        assertFalse(TextEncoding.isValidUtf8(byteArrayOf(0xC3.toByte())), "truncated two-byte sequence")
        assertFalse(TextEncoding.isValidUtf8(byteArrayOf(0xE2.toByte(), 0x82.toByte())), "truncated three-byte")
        assertFalse(TextEncoding.isValidUtf8(byteArrayOf(0xC0.toByte(), 0x80.toByte())), "overlong encoding")
        assertFalse(TextEncoding.isValidUtf8(byteArrayOf(0xFF.toByte())))
        assertTrue(TextEncoding.isValidUtf8(byteArrayOf()))
    }

    @Test fun utf8RoundTripsAnyText() {
        val text = "پیکربندی سرور — configuration"
        assertEquals(text, TextEncoding.decode(TextEncoding.encode(text, "UTF-8"), "UTF-8"))
    }

    @Test fun aLegacyCharsetRoundTripsOnlyWhatItCanRepresent() {
        // windows-1256 is an Arabic code page: "سلام" survives it, but Persian-only
        // letters such as U+06CC do not exist in it and come back as "?". Worth pinning
        // so nobody later "fixes" the fallback by writing files back in a lossy charset.
        assertEquals("سلام", TextEncoding.decode(TextEncoding.encode("سلام", "windows-1256"), "windows-1256"))
        val lossy = TextEncoding.decode(TextEncoding.encode("ی", "windows-1256"), "windows-1256")
        assertTrue(lossy.contains("?"), "a character outside the code page cannot survive it")
    }

    @Test fun anUnknownCharsetNameDoesNotThrow() {
        assertEquals("abc", TextEncoding.decode("abc".toByteArray(), "not-a-charset"))
    }

    // ---- line endings ----

    @Test fun eachEndingStyleIsDetected() {
        assertEquals(TextEncoding.LineEnding.LF, TextEncoding.detectLineEnding("a\nb\nc"))
        assertEquals(TextEncoding.LineEnding.CRLF, TextEncoding.detectLineEnding("a\r\nb\r\nc"))
        assertEquals(TextEncoding.LineEnding.MIXED, TextEncoding.detectLineEnding("a\r\nb\nc"))
        assertEquals(TextEncoding.LineEnding.NONE, TextEncoding.detectLineEnding("single line"))
    }

    @Test fun conversionIsIdempotentAndConvergesFromMixed() {
        val mixed = "a\r\nb\nc\rd"
        val lf = TextEncoding.convertLineEndings(mixed, TextEncoding.LineEnding.LF)
        assertEquals("a\nb\nc\nd", lf)
        assertEquals(lf, TextEncoding.convertLineEndings(lf, TextEncoding.LineEnding.LF))

        val crlf = TextEncoding.convertLineEndings(mixed, TextEncoding.LineEnding.CRLF)
        assertEquals("a\r\nb\r\nc\r\nd", crlf)
        assertEquals(crlf, TextEncoding.convertLineEndings(crlf, TextEncoding.LineEnding.CRLF),
            "converting twice must not double the carriage returns")
    }

    @Test fun savingPreservesTheStyleTheFileArrivedWith() {
        // Editing one line of a CRLF file must not rewrite every line of it.
        val edited = "a\nb\nc"
        assertEquals("a\r\nb\r\nc", TextEncoding.preserveOriginalEndings(edited, TextEncoding.LineEnding.CRLF))
        assertEquals("a\nb\nc", TextEncoding.preserveOriginalEndings(edited, TextEncoding.LineEnding.LF))
    }
}
