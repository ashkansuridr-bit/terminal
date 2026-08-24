package app.terminalssh.secure.security

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class SecretIoTest {
    @Test
    fun readsWithinLimit() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val actual = SecretIo.readBounded(ByteArrayInputStream(expected), 4)
        assertContentEquals(expected, actual)
        actual.fill(0)
    }

    @Test
    fun rejectsOneByteOverLimit() {
        assertFailsWith<IllegalArgumentException> {
            SecretIo.readBounded(ByteArrayInputStream(ByteArray(5)), 4)
        }
    }

    @Test
    fun makesProgressWhenBulkReadReturnsZero() {
        val input = ZeroThenDataInputStream(byteArrayOf(1, 2, 3))

        val actual = SecretIo.readBounded(input, 3)

        assertContentEquals(byteArrayOf(1, 2, 3), actual)
        actual.fill(0)
    }

    @Test
    fun terminatesWhenBulkReadReturnsZeroAtEof() {
        val actual = SecretIo.readBounded(ZeroThenDataInputStream(byteArrayOf()), 1)

        assertContentEquals(byteArrayOf(), actual)
    }

    private class ZeroThenDataInputStream(private val data: ByteArray) : InputStream() {
        private var position = 0
        private var returnZero = true

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (returnZero) {
                returnZero = false
                return 0
            }
            if (position >= data.size) return -1
            val count = minOf(length, data.size - position)
            data.copyInto(target, offset, position, position + count)
            position += count
            return count
        }

        override fun read(): Int = if (position < data.size) data[position++].toInt() and 0xff else -1
    }
}
