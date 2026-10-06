package app.terminalssh.secure.sftp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Non-secret workspace metadata. Version 1 replaces the previous memory-only state. */
data class SftpWorkspaceState(
    val bookmarks: List<String> = emptyList(),
    val presets: List<WorkspaceSyncPreset> = emptyList(),
)

data class WorkspaceSyncPreset(
    val id: String,
    val name: String,
    val localDir: String,
    val remoteDir: String,
    val deleteRemote: Boolean = false,
)

internal object SftpWorkspaceCodec {
    private const val VERSION = 1
    private const val MAX_ITEMS = 4096
    private const val MAX_ENCODED_BYTES = 4 * 1024 * 1024

    fun encode(state: SftpWorkspaceState): String {
        require(state.bookmarks.size <= MAX_ITEMS && state.presets.size <= MAX_ITEMS)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeInt(state.bookmarks.size)
            state.bookmarks.forEach(out::writeUTF)
            out.writeInt(state.presets.size)
            state.presets.forEach { preset ->
                out.writeUTF(preset.id)
                out.writeUTF(preset.name)
                out.writeUTF(preset.localDir)
                out.writeUTF(preset.remoteDir)
                out.writeBoolean(preset.deleteRemote)
            }
        }
        val encoded = Base64.getEncoder().encodeToString(bytes.toByteArray())
        require(encoded.length <= MAX_ENCODED_BYTES)
        return encoded
    }

    /** Invalid or newer metadata fails visibly; it must never be silently reset/overwritten. */
    fun decode(encoded: String?): SftpWorkspaceState {
        if (encoded == null) return SftpWorkspaceState()
        require(encoded.length <= MAX_ENCODED_BYTES)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            require(input.readInt() == VERSION) { "Unsupported SFTP workspace version" }
            val bookmarks = List(checkedCount(input.readInt())) { input.readUTF() }
            val presets = List(checkedCount(input.readInt())) {
                WorkspaceSyncPreset(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readBoolean())
            }
            require(input.available() == 0) { "Trailing workspace metadata" }
            require(bookmarks.distinct().size == bookmarks.size) { "Duplicate bookmark" }
            require(presets.map { it.id }.distinct().size == presets.size) { "Duplicate sync preset" }
            SftpWorkspaceState(bookmarks, presets)
        }
    }

    private fun checkedCount(count: Int): Int {
        require(count in 0..MAX_ITEMS) { "Invalid workspace count" }
        return count
    }
}
