package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
