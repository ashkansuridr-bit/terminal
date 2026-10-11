package app.terminalssh.secure.sftp

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F026: the preview sample is bounded while reading and does not lose the bytes — a decoded
 * string would have thrown away the NUL that identifies a binary file.
 */
class BoundedTextReadBytesTest {

    private fun stream(vararg values: Int) = ByteArrayInputStream(ByteArray(values.size) { values[it].toByte() })

    @Test
    fun aShortSampleIsComplete() {
        val preview = BoundedText.readBytes(stream(1, 2, 3), maxBytes = 8)
        assertContentEquals(byteArrayOf(1, 2, 3), preview.bytes)
        assertFalse(preview.truncated)
    }

    @Test
    fun aLongSampleIsCappedAndMarkedTruncated() {
        val preview = BoundedText.readBytes(stream(1, 2, 3, 4, 5), maxBytes = 3)
        assertContentEquals(byteArrayOf(1, 2, 3), preview.bytes)
        assertTrue(preview.truncated)
    }

    @Test
    fun anExactlyFullSampleIsNotTruncated() {
        val preview = BoundedText.readBytes(stream(1, 2, 3), maxBytes = 3)
        assertContentEquals(byteArrayOf(1, 2, 3), preview.bytes)
        assertFalse(preview.truncated)
    }

    @Test
    fun anEmptyFileYieldsAnEmptyCompleteSample() {
        val preview = BoundedText.readBytes(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0, preview.bytes.size)
        assertFalse(preview.truncated)
    }

    @Test
    fun limitsOutsideThePreviewBoundAreRejected() {
        assertFailsWith<IllegalArgumentException> { BoundedText.readBytes(stream(1), maxBytes = 0) }
        assertFailsWith<IllegalArgumentException> {
            BoundedText.readBytes(stream(1), maxBytes = MAX_EDIT_BYTES + 1)
        }
    }

    @Test
    fun theExistingTextReadIsUnchanged() {
        val result = BoundedText.read(stream(0x61, 0x62, 0x63), maxBytes = 3)
        assertEquals("abc", result.content)
        assertFalse(result.truncated)
        assertEquals(3L, result.totalSize)
    }
}
