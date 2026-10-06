package app.terminalssh.secure.sftp

import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Application preferences survive cache eviction, process death and reboot. Scoped to host, not session. */
class SftpWorkspaceStore(private val preferences: SharedPreferences, hostId: String) {
    private val key = "workspace-v1:$hostId"
    // Sessions using the same host serialize read/modify/commit, avoiding lost updates.
    private val lock = locks.getOrPut(key) { Any() }

    fun load(): SftpWorkspaceState = synchronized(lock) {
        SftpWorkspaceCodec.decode(preferences.getString(key, null))
    }

    fun update(change: (SftpWorkspaceState) -> SftpWorkspaceState): SftpWorkspaceState = synchronized(lock) {
        val next = change(SftpWorkspaceCodec.decode(preferences.getString(key, null)))
        if (!preferences.edit().putString(key, SftpWorkspaceCodec.encode(next)).commit()) {
            throw IOException("SFTP workspace metadata could not be persisted")
        }
        next
    }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
    }
}
