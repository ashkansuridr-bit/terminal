package app.terminalssh.secure.sftp

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SftpWorkspaceCodecTest {
    @Test fun firstRunMigratesMemoryOnlyStateToEmptyWorkspace() {
        assertEquals(SftpWorkspaceState(), SftpWorkspaceCodec.decode(null))
    }

    @Test fun reloadPreservesPathsUnicodeAndDestructivePresetFlag() {
        val expected = SftpWorkspaceState(
            listOf("/home/کاربر", "/folder with spaces"),
            listOf(WorkspaceSyncPreset("preset-a", "همگام‌سازی", "/local/a", "/remote/a", true)),
        )
        assertEquals(expected, SftpWorkspaceCodec.decode(SftpWorkspaceCodec.encode(expected)))
    }

    @Test fun truncatedMetadataNeverReturnsAnEmptySuccessfulWorkspace() {
        val encoded = SftpWorkspaceCodec.encode(SftpWorkspaceState(listOf("/important")))
        val bytes = Base64.getDecoder().decode(encoded)
        assertFails { SftpWorkspaceCodec.decode(Base64.getEncoder().encodeToString(bytes.copyOf(bytes.size - 1))) }
    }

    @Test fun unknownSchemaAndInvalidCountsFailClosed() {
        assertFails { SftpWorkspaceCodec.decode(Base64.getEncoder().encodeToString(byteArrayOf(0, 0, 0, 2))) }
        assertFails { SftpWorkspaceCodec.decode(Base64.getEncoder().encodeToString(byteArrayOf(0, 0, 0, 1, -1, -1, -1, -1))) }
    }

    @Test fun duplicateIdentifiersAreRejected() {
        val preset = WorkspaceSyncPreset("same", "A", "/local", "/remote")
        val encoded = SftpWorkspaceCodec.encode(SftpWorkspaceState(presets = listOf(preset, preset)))
        assertFails { SftpWorkspaceCodec.decode(encoded) }
    }
}
