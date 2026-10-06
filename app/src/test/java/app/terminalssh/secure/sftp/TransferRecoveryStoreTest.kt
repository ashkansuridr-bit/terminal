package app.terminalssh.secure.sftp

import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferRecoveryStoreTest {
    private val profile = HostProfile("server", host = "example.test", username = "alice", auth = AuthMethod.Password("vault-secret-ref"))

    @Test fun restartDiscoversPriorSessionButNeverCurrentSession() {
        val root = Files.createTempDirectory("recovery").toFile()
        try {
            TransferStatePaths.registerSession(root, profile.id, "old-session", profile)
            val file = TransferStatePaths.queueFile(root, profile.id, "old-session")
            TransferStatePaths.atomicWrite(file, "[{\"id\":\"pending\"}]")
            assertEquals(1, TransferRecoveryStore(root).discover(emptySet()).size)
            assertTrue(TransferRecoveryStore(root).discover(setOf("old-session")).isEmpty())
            assertTrue(TransferRecoveryStore(root).matches(file, profile))
            assertFalse(java.io.File(file.parentFile, "identity.properties").readText().contains("vault-secret-ref"))
        } finally { root.deleteRecursively() }
    }

    @Test fun changedSavedHostMustNotTakeOverOriginalLedger() {
        val root = Files.createTempDirectory("recovery").toFile()
        try {
            TransferStatePaths.registerSession(root, profile.id, "old-session", profile)
            val file = TransferStatePaths.queueFile(root, profile.id, "old-session")
            TransferStatePaths.atomicWrite(file, "[{\"id\":\"pending\"}]")
            val store = TransferRecoveryStore(root)
            assertFalse(store.matches(file, profile.copy(host = "other.test")))
            assertFalse(store.matches(file, profile.copy(username = "bob")))
            assertFalse(store.matches(file, profile.copy(port = 2222)))
            assertFalse(store.matches(file, profile.copy(jumpHostId = "other-route")))
            assertFalse(store.matches(java.io.File(root, "queue.json"), profile))
        } finally { root.deleteRecursively() }
    }

    @Test fun missingIdentityIsVisibleButCannotResume() {
        val root = Files.createTempDirectory("recovery").toFile()
        try {
            val file = TransferStatePaths.queueFile(root, profile.id, "legacy")
            TransferStatePaths.atomicWrite(file, "[{\"id\":\"pending\"}]")
            val store = TransferRecoveryStore(root)
            assertFalse(store.discover(emptySet()).single().identityComplete)
            assertFalse(store.matches(file, profile))
        } finally { root.deleteRecursively() }
    }
}
