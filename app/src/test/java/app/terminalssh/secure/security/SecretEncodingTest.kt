package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SecretEncodingTest {
    @Test
    fun encodesUtf8AndWipesInput() {
        val chars = charArrayOf('ر', 'م', 'ز', '1', '2', '3')
        val encoded = SecretEncoding.utf8(chars)
        assertContentEquals("رمز123".encodeToByteArray(), encoded)
        assertEquals(true, chars.all { it == '\u0000' })
        encoded.fill(0)
    }

    @Test
    fun emptySecretIsSupported() {
        val chars = CharArray(0)
        val encoded = SecretEncoding.utf8(chars)
        assertEquals(0, encoded.size)
    }
}
