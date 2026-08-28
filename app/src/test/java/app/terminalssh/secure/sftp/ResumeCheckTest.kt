package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResumeCheckTest {

    @Test fun trustsResumeWhenActualBytesMatchRecorded() {
        assertTrue(canTrustResume(recordedBytes = 500L, actualBytes = 500L))
    }

    @Test fun distrustsResumeWhenActualBytesDiffer() {
        assertTrue(!canTrustResume(recordedBytes = 500L, actualBytes = 300L))
    }

    @Test fun trustsAFreshTransferWithNothingRecordedOrOnDisk() {
        assertTrue(canTrustResume(recordedBytes = 0L, actualBytes = 0L))
    }

    @Test fun skipFullyAdvancesPastRequestedBytes() {
        val input = "0123456789".byteInputStream()
        skipFully(input, 4)
        assertEquals('4'.code, input.read())
    }

    @Test fun skipFullyStopsEarlyWhenTheStreamRunsOut() {
        val input = "abc".byteInputStream()
        skipFully(input, 100)
        assertEquals(-1, input.read())
    }

    @Test fun skipFullyDoesNothingForZero() {
        val input = "abc".byteInputStream()
        skipFully(input, 0)
        assertEquals('a'.code, input.read())
    }

    // ---- remote-side guard for resumed downloads (silent-corruption regression) ----

    @Test fun remoteReplacedByADifferentSizedFileMustNotResume() {
        // The corruption case: 400 bytes of the old file are already staged locally and
        // the offset is internally consistent, but the server file was replaced. Appending
        // the new file's tail to the old file's head yields a file that is neither.
        assertTrue(canTrustResume(400L, 400L), "local half is intact, so only the remote guard can catch this")
        assertFalse(canTrustRemoteForResume(recordedTotalBytes = 1000L, currentRemoteBytes = 2048L))
    }

    @Test fun remoteTruncatedSinceEnqueueMustNotResume() {
        assertFalse(canTrustRemoteForResume(recordedTotalBytes = 1000L, currentRemoteBytes = 120L))
    }

    @Test fun unchangedRemoteMayResume() {
        assertTrue(canTrustRemoteForResume(recordedTotalBytes = 1000L, currentRemoteBytes = 1000L))
    }

    @Test fun unreadableRemoteStatFailsClosed() {
        // A stat that threw is reported as null; guessing here is how corrupt files ship.
        assertFalse(canTrustRemoteForResume(recordedTotalBytes = 1000L, currentRemoteBytes = null))
    }

    @Test fun unknownRecordedSizeFailsClosed() {
        assertFalse(canTrustRemoteForResume(Transfer.UNKNOWN_SIZE, currentRemoteBytes = 1000L))
        assertFalse(canTrustRemoteForResume(recordedTotalBytes = 0L, currentRemoteBytes = 0L))
    }

    @Test fun emptyRemoteFileIsNeverResumed() {
        // A zero-byte remote has nothing to resume; treating 0 == 0 as trustworthy would
        // let an empty-but-changed file through the guard.
        assertFalse(canTrustRemoteForResume(recordedTotalBytes = 0L, currentRemoteBytes = 0L))
    }
}
