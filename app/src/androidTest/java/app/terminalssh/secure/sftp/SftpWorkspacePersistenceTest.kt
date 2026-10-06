package app.terminalssh.secure.sftp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SftpWorkspacePersistenceTest {
    @Test fun freshStoreAndHostIsolationPreserveDurableWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "workspace-test-${UUID.randomUUID()}"
        try {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val expected = SftpWorkspaceState(listOf("/a"), listOf(WorkspaceSyncPreset("p", "Sync", "/local", "/remote", true)))
            SftpWorkspaceStore(prefs, "host-a").update { expected }
            // Reconstruct store from application preferences rather than retaining in-memory state.
            assertEquals(expected, SftpWorkspaceStore(context.getSharedPreferences(name, Context.MODE_PRIVATE), "host-a").load())
            assertTrue(SftpWorkspaceStore(prefs, "host-b").load().bookmarks.isEmpty())
            val otherSession = SftpWorkspaceStore(prefs, "host-a")
            otherSession.update { it.copy(bookmarks = it.bookmarks + "/b") }
            assertEquals(listOf("/a", "/b"), SftpWorkspaceStore(prefs, "host-a").load().bookmarks)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
