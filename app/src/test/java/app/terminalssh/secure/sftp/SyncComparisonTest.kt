package app.terminalssh.secure.sftp

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SyncComparisonTest {
    private fun hash(text: String) = SyncComparison.sha256(text.byteInputStream())

    @Test fun equalSizesAndTimesWithDifferentContentAreNotIdenticalByDefaultSafeMode() {
        assertFalse(SyncComparison.identical(SyncMode.SAFE, 4, 100, 4, 100, { hash("abcd") }, { hash("wxyz") }))
    }

    @Test fun verifyAllChecksContentEvenWhenMetadataMatches() {
        assertFalse(SyncComparison.identical(SyncMode.VERIFY_ALL, 4, 100, 4, 100, { hash("abcd") }, { hash("abce") }))
        assertTrue(SyncComparison.identical(SyncMode.VERIFY_ALL, 4, 100, 4, 101, { hash("abcd") }, { hash("abcd") }))
    }

    @Test fun fastUsesTimestampAndSizeWithoutRequestingHashes() {
        assertFalse(SyncComparison.identical(SyncMode.FAST, 4, 100, 4, 101, { error("Unexpected hash") }, { error("Unexpected hash") }))
        assertTrue(SyncComparison.identical(SyncMode.FAST, 4, 100, 4, 100, { error("Unexpected hash") }, { error("Unexpected hash") }))
    }

    @Test fun unknownSizeAndHashFailuresAbortRatherThanSkipping() {
        assertFailsWith<IllegalArgumentException> {
            SyncComparison.identical(SyncMode.SAFE, 4, 100, -1, 100, { hash("abcd") }, { hash("abcd") })
        }
        assertFailsWith<IOException> {
            SyncComparison.identical(SyncMode.SAFE, 4, 100, 4, 100, { hash("abcd") }, { throw IOException("Disconnected") })
        }
    }

    @Test fun hashingUsesStreamingBytesIncludingFilesLargerThanBuffer() {
        val data = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        kotlin.test.assertContentEquals(expected, SyncComparison.sha256(data.inputStream()))
    }
}
