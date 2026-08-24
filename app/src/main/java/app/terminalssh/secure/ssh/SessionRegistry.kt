package app.terminalssh.secure.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All open sessions (terminal tabs). Survives Activity recreation because it lives in the Application. */
class SessionRegistry {
    private val _sessions = MutableStateFlow<List<SshSession>>(emptyList())
    val sessions: StateFlow<List<SshSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    val active: SshSession? get() = _sessions.value.firstOrNull { it.id == _activeId.value }

    fun add(session: SshSession) {
        _sessions.value = _sessions.value + session
        _activeId.value = session.id
    }

    fun select(id: String) {
        if (_sessions.value.any { it.id == id }) _activeId.value = id
    }

    fun close(id: String) {
        val session = _sessions.value.firstOrNull { it.id == id } ?: return
        session.destroy()
        val remaining = _sessions.value.filterNot { it.id == id }
        _sessions.value = remaining
        if (_activeId.value == id) _activeId.value = remaining.lastOrNull()?.id
    }

    fun closeAll() {
        _sessions.value.forEach { it.destroy() }
        _sessions.value = emptyList()
        _activeId.value = null
    }

    fun liveCount(): Int = _sessions.value.count { it.state.value.isLive }
}
