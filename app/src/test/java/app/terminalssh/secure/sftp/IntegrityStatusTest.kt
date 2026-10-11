package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntegrityStatusTest {

    @Test fun matchingHashesAreTheOnlyProvenOutcome() {
        val report = IntegrityStatus.verify("abc123", "abc123")
        assertEquals(IntegrityStatus.State.VERIFIED, report.state)
        assertTrue(report.isProven)
        assertFalse(report.needsAttention)
    }

    @Test fun comparisonIgnoresCaseAndAlgorithmPrefix() {
        val report = IntegrityStatus.verify("SHA256:ABC123", "sha256:abc123")
        assertEquals(IntegrityStatus.State.VERIFIED, report.state)
    }

    @Test fun differingHashesAreAMismatch() {
        val report = IntegrityStatus.verify("abc", "def")
        assertEquals(IntegrityStatus.State.MISMATCH, report.state)
        assertFalse(report.isProven)
        assertTrue(report.needsAttention)
    }

    @Test fun aMissingSourceHashIsUnverifiedNotSuccess() {
        val report = IntegrityStatus.verify(null, "abc")
        assertEquals(IntegrityStatus.State.UNVERIFIED, report.state)
        assertFalse(report.isProven)
        assertTrue(report.reason.contains("source"))
    }

    @Test fun aMissingDestinationHashIsUnverified() {
        val report = IntegrityStatus.verify("abc", null)
        assertEquals(IntegrityStatus.State.UNVERIFIED, report.state)
        assertFalse(report.isProven)
        assertTrue(report.reason.contains("destination"))
    }

    @Test fun nothingAvailableIsUnverified() {
        val report = IntegrityStatus.verify(null, null)
        assertEquals(IntegrityStatus.State.UNVERIFIED, report.state)
        assertFalse(report.isProven)
    }

    @Test fun blankIsTreatedAsAbsent() {
        assertEquals(IntegrityStatus.State.UNVERIFIED, IntegrityStatus.verify("   ", "").state)
    }

    @Test fun anEmptyHashNeverVerifies() {
        // Two "equal" blanks must not be read as a match.
        assertFalse(IntegrityStatus.verify("", "").isProven)
    }

    @Test fun normalizeStripsPrefixTrimsAndLowercases() {
        assertEquals("abc", IntegrityStatus.normalize("  SHA256:ABC  "))
        assertEquals("abc", IntegrityStatus.normalize("ABC"))
        assertNull(IntegrityStatus.normalize(null))
        assertNull(IntegrityStatus.normalize("   "))
    }

    @Test fun everyStateHasALocalizationKey() {
        for (state in IntegrityStatus.State.entries) {
            assertTrue(IntegrityStatus.labelKey(state).startsWith("integrity.state."))
        }
    }
}
