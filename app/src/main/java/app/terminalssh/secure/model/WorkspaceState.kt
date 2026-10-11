package app.terminalssh.secure.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * A saved terminal workspace (F032): which hosts were open as tabs, and which tab was in front.
 *
 * Restoring is deliberately two steps. Decoding only recovers *intent*; [WorkspaceCodec.planRestore]
 * then drops tabs whose host no longer exists, because a stale workspace must never reopen a
 * connection the user did not ask for — and nothing here connects on its own. The caller shows
 * the plan and the user chooses.
 *
 * Only non-secret metadata is stored: a host id, a title and a pin flag. No username, no path,
 * no credential reference.
 */
data class WorkspaceTab(
    val hostId: String,
    val title: String,
    val pinned: Boolean = false,
)

data class WorkspaceState(
    val tabs: List<WorkspaceTab> = emptyList(),
    val selectedIndex: Int = 0,
) {
    companion object {
        val empty = WorkspaceState()
    }
}

/** What of a saved workspace can be offered back, and what is gone. */
data class WorkspaceRestorePlan(
    val tabs: List<WorkspaceTab>,
    val missingHostIds: List<String>,
    val selectedIndex: Int,
) {
    /** True when some saved tabs reference hosts that no longer exist. */
    val isPartial: Boolean get() = missingHostIds.isNotEmpty()
}

object WorkspaceCodec {

    private const val VERSION = 1
    private const val MAX_TABS = 64
    private const val MAX_ENCODED_BYTES = 64 * 1024

    fun encode(state: WorkspaceState): String {
        require(state.tabs.size <= MAX_TABS) { "Too many workspace tabs" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeInt(state.selectedIndex)
            out.writeInt(state.tabs.size)
            state.tabs.forEach { tab ->
                out.writeUTF(tab.hostId)
                out.writeUTF(tab.title)
                out.writeBoolean(tab.pinned)
            }
        }
        val encoded = Base64.getEncoder().encodeToString(bytes.toByteArray())
        require(encoded.length <= MAX_ENCODED_BYTES)
        return encoded
    }

    /**
     * Returns the saved workspace, or null when the data is absent or unreadable.
     *
     * Null means "we could not read it", not "it was empty": the caller then keeps whatever it
     * had rather than flashing to an empty layout because of one bad byte.
     */
    fun decodeOrNull(encoded: String?): WorkspaceState? {
        if (encoded.isNullOrBlank()) return null
        if (encoded.length > MAX_ENCODED_BYTES) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                require(input.readInt() == VERSION) { "Unsupported workspace version" }
                val selected = input.readInt()
                val count = input.readInt()
                require(count in 0..MAX_TABS) { "Bad workspace tab count" }
                val tabs = List(count) {
                    WorkspaceTab(hostId = input.readUTF(), title = input.readUTF(), pinned = input.readBoolean())
                }
                require(input.available() == 0) { "Trailing workspace data" }
                WorkspaceState(tabs = tabs, selectedIndex = selected.coerceIn(0, maxOf(0, tabs.size - 1)))
            }
        }.getOrNull()
    }

    /**
     * Splits a decoded workspace into the tabs whose host still exists and the host ids that are
     * gone. No connection is made; this only tells the UI what it may offer to reopen.
     */
    fun planRestore(state: WorkspaceState, knownHostIds: Set<String>): WorkspaceRestorePlan {
        val tabs = state.tabs.filter { it.hostId in knownHostIds }
        val missing = state.tabs.filterNot { it.hostId in knownHostIds }.map { it.hostId }.distinct()
        val selected = state.selectedIndex.coerceIn(0, maxOf(0, tabs.size - 1))
        return WorkspaceRestorePlan(tabs = tabs, missingHostIds = missing, selectedIndex = selected)
    }
}
