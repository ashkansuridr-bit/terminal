package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableBackupTest {

    // A small iteration count keeps the tests fast; the production default is asserted below.
    private val fast = 2_000

    @Test fun roundTripsAPayload() {
        val payload = "hosts and settings".toByteArray()
        val sealed = PortableBackup.seal(payload, "correct horse".toCharArray(), iterations = fast)
        assertContentEquals(payload, PortableBackup.open(sealed, "correct horse".toCharArray()))
    }

    @Test fun roundTripsEmptyAndBinaryPayloads() {
        val empty = PortableBackup.seal(ByteArray(0), "pw".toCharArray(), iterations = fast)
        assertContentEquals(ByteArray(0), PortableBackup.open(empty, "pw".toCharArray()))

        val binary = ByteArray(256) { it.toByte() }
        val sealed = PortableBackup.seal(binary, "pw".toCharArray(), iterations = fast)
        assertContentEquals(binary, PortableBackup.open(sealed, "pw".toCharArray()))
    }

    @Test fun wrongPassphraseIsReportedAsSuch() {
        val sealed = PortableBackup.seal("data".toByteArray(), "right".toCharArray(), iterations = fast)
        assertFailsWith<PortableBackup.WrongPassphrase> {
            PortableBackup.open(sealed, "wrong".toCharArray())
        }
    }

    @Test fun tamperingWithTheCiphertextFailsTheTag() {
        val sealed = PortableBackup.seal("data".toByteArray(), "pw".toCharArray(), iterations = fast)
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 0x01).toByte()
        assertFailsWith<PortableBackup.WrongPassphrase> {
            PortableBackup.open(sealed, "pw".toCharArray())
        }
    }

    @Test fun downgradingTheIterationCountIsDetected() {
        // The header is authenticated as AAD, so editing the iteration count breaks the tag.
        val sealed = PortableBackup.seal("data".toByteArray(), "pw".toCharArray(), iterations = fast)
        val lowered = sealed.copyOf()
        // iterations live at bytes 5..8 (magic=4, version=1).
        lowered[8] = 1.toByte()
        assertFailsWith<PortableBackup.WrongPassphrase> {
            PortableBackup.open(lowered, "pw".toCharArray())
        }
    }

    @Test fun envelopeIsRecognisableAndExposesItsHeader() {
        val sealed = PortableBackup.seal("data".toByteArray(), "pw".toCharArray(), iterations = fast)
        assertTrue(PortableBackup.isEnvelope(sealed))
        assertEquals(fast, PortableBackup.peekIterations(sealed))
        assertFalse(PortableBackup.isEnvelope("not a backup".toByteArray()))
    }

    @Test fun garbageInIsRejectedAsMalformed() {
        assertFailsWith<PortableBackup.MalformedBackup> {
            PortableBackup.open("garbage".toByteArray(), "pw".toCharArray())
        }
    }

    @Test fun everySealUsesAFreshSaltAndNonce() {
        val a = PortableBackup.seal("same".toByteArray(), "pw".toCharArray(), iterations = fast)
        val b = PortableBackup.seal("same".toByteArray(), "pw".toCharArray(), iterations = fast)
        assertFalse(a.contentEquals(b), "identical ciphertext would mean a reused salt/nonce")
    }

    @Test fun emptyPassphraseAndIterationsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            PortableBackup.seal("data".toByteArray(), CharArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            PortableBackup.seal("data".toByteArray(), "pw".toCharArray(), iterations = 0)
        }
    }

    @Test fun productionDefaultIsStrongAndStable() {
        assertEquals(210_000, PortableBackup.DEFAULT_ITERATIONS)
    }
}
