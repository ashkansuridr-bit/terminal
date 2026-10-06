package app.terminalssh.secure.sftp

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SafStagingStoreTest {
    private fun withStore(block: (File, SafStagingStore) -> Unit) {
        val directory = Files.createTempDirectory("saf-test").toFile()
        try { block(directory, SafStagingStore(directory, "process-one")) }
        finally { directory.deleteRecursively() }
    }

    @Test fun concurrentEditorsHaveIndependentFilesAndContents() = withStore { _, store ->
        val pool = Executors.newFixedThreadPool(2)
        try {
            val opens = pool.invokeAll(listOf("first edit", "second edit").map { text -> Callable {
                store.create("session", "host", "server", "/etc/config").also {
                    it.file.writeText(text)
                    store.activate(it)
                }
            } }).map { it.get() }
            assertNotEquals(opens[0].file, opens[1].file)
            assertEquals("first edit", opens[0].file.readText())
            assertEquals("second edit", opens[1].file.readText())
            assertTrue(store.pending().isEmpty())
        } finally { pool.shutdownNow() }
    }

    @Test fun readOnlyFilesAreNeverRecoverableEvenAfterProcessDeath() = withStore { directory, store ->
        val edit = store.create("s", "h", "server", "/file", writable = false)
        store.activate(edit)
        assertTrue(SafStagingStore(directory, "process-two").pending().isEmpty())
        assertFalse(store.claimRetry(edit))
    }

    @Test fun activeEditorCannotBeClaimedByRecovery() = withStore { _, store ->
        val edit = store.create("s", "h", "server", "/file")
        store.activate(edit)
        assertTrue(store.pending().isEmpty())
        assertFalse(store.claimRetry(edit))
    }

    @Test fun interruptedDownloadIsNeverUploadedByRecovery() = withStore { directory, store ->
        val edit = store.create("s", "h", "server", "/large")
        edit.file.writeText("only part of remote content")
        val restarted = SafStagingStore(directory, "process-two")
        assertTrue(restarted.pending().isEmpty())
        assertFalse(restarted.claimRetry(edit))
        assertTrue(edit.file.exists())
    }

    @Test fun failedSaveSurvivesRestartAndHasDestinationMetadata() = withStore { directory, store ->
        val edit = store.create("s", "h", "server", "/file")
        edit.file.writeText("retained edit")
        store.activate(edit)
        store.failed(edit)
        val pending = SafStagingStore(directory, "process-two").pending().single()
        assertEquals(edit, pending)
        assertEquals("retained edit", pending.file.readText())
    }

    @Test fun processDeathAfterEditorOpenRetainsWork() = withStore { directory, store ->
        val edit = store.create("s", "h", "server", "/file")
        store.activate(edit)
        edit.file.writeText("edited")
        assertEquals(edit, SafStagingStore(directory, "process-two").pending().single())
    }

    @Test fun concurrentRecoveryAttemptsClaimOnlyOnce() = withStore { _, store ->
        val edit = store.create("s", "h", "server", "/file")
        store.activate(edit)
        store.failed(edit)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val claims = pool.invokeAll(List(2) { Callable { store.claimRetry(edit) } }).map { it.get() }
            assertEquals(1, claims.count { it })
            assertTrue(store.pending().isEmpty())
            store.failed(edit)
            assertTrue(store.claimRetry(edit))
        } finally { pool.shutdownNow() }
    }

    @Test fun corruptMetadataFailsClosedAndPreservesEdit() = withStore { directory, store ->
        val edit = store.create("s", "h", "server", "/file")
        edit.file.writeText("precious content")
        File(directory, edit.file.name + ".meta").writeText("createdAt=not-a-number\n")
        assertFailsWith<IOException> { store.pending() }
        assertFailsWith<IOException> { store.claimRetry(edit) }
        assertEquals("precious content", edit.file.readText())
    }

    @Test fun originalFingerprintSurvivesProcessDeath() = withStore { directory, store ->
        val edit = store.create("s", "h", "server", "/file")
        val original = EditFingerprint(10, 3, EditConflict.sha256("old"))
        store.recordFingerprint(edit, original)
        store.activate(edit)
        edit.file.writeText("changed locally")
        store.failed(edit)
        val restarted = SafStagingStore(directory, "process-two")
        assertEquals(original, restarted.fingerprint(restarted.pending().single()))
    }

    @Test fun missingFingerprintCannotAuthorizeOrdinarySave() = withStore { _, store ->
        val edit = store.create("s", "h", "server", "/file")
        edit.file.writeText("retain me")
        assertFailsWith<IOException> { store.fingerprint(edit) }
        assertEquals("retain me", edit.file.readText())
    }

    @Test fun successfulCleanupOnlyRemovesItsOwnStage() = withStore { _, store ->
        val first = store.create("s", "h", "server", "/file")
        val second = store.create("s", "h", "server", "/file")
        store.complete(first)
        assertFalse(first.file.exists())
        assertTrue(second.file.exists())
    }

    @Test fun childChecksRespectPathBoundariesAndNormalizedPaths() {
        assertTrue(SafStagingStore.isChild("/a", "/a/file"))
        assertFalse(SafStagingStore.isChild("/a", "/abc"))
        assertTrue(SafStagingStore.isChild("/", "/anything"))
        assertFalse(SafStagingStore.isChild("/a/b", "/a/bc"))
        assertFalse(SafStagingStore.isChild("/a", "/a"))
        assertFalse(SafStagingStore.isChild("/a", "/a/../abc"))
        assertTrue(SafStagingStore.isChild("/a/", "/a//file"))
    }
}
