package app.terminalssh.secure.sftp

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class UploadCheckpointTest {
    @get:Rule val temp = TemporaryFolder()
    private val hash = "a".repeat(64)

    @Test fun preservesOriginalDestinationAcrossRestart() {
        val file = temp.newFile("upload.checkpoint")
        val expected = UploadCheckpoint(hash, SyncFileSnapshot(42, 88, "b".repeat(64)))
        expected.save(file)
        assertEquals(expected, UploadCheckpoint.load(file))
    }

    @Test fun explicitlyMissingDestinationSurvivesRestart() {
        val file = temp.newFile("missing.checkpoint")
        val expected = UploadCheckpoint(hash, null)
        expected.save(file)
        assertEquals(expected, UploadCheckpoint.load(file))
    }

    @Test fun differentTransfersHaveUnpredictableIndependentStagingNames() {
        val first = UploadCheckpoint(hash, null)
        val second = UploadCheckpoint(hash, null)
        assertNotEquals(first.stagingName, second.stagingName)
        assertTrue(first.stagingName.startsWith(".terminalssh-transfer-"))
    }

    @Test fun missingStagingIdentityCannotBeReconstructedFromTransferId() {
        val file = temp.newFile("legacy.checkpoint")
        file.writeText("sourceSha256=$hash\ntargetExists=false\n")
        try {
            UploadCheckpoint.load(file)
            fail("Missing private staging identity accepted")
        } catch (_: java.io.IOException) { }
    }

    @Test fun malformedAuthorizationFailsClosed() {
        val file = temp.newFile("bad.checkpoint")
        file.writeText("sourceSha256=$hash\ntargetExists=unknown\n")
        try {
            UploadCheckpoint.load(file)
            fail("Invalid destination authorization accepted")
        } catch (_: java.io.IOException) { }
    }
}
