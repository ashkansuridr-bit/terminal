package app.terminalssh.secure.ssh

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    /**
     * The state of each host that currently has a session, keyed by host id.
     *
     * The host list needs this to show a server as live without the user having to
     * remember which tab they left open. Sessions come and go, so the per-session state
     * flows are re-combined whenever the session list changes rather than sampled once.
     *
     * Two sessions on the same host collapse to the more interesting state: a host with
     * one connected and one reconnecting session reads as connected, because it is.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val hostStates: Flow<Map<String, SshSessionState>> = _sessions.flatMapLatest { open ->
        if (open.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(open.map { session -> session.state.map { session.profile.id to it } }) { pairs ->
                pairs.groupBy({ it.first }, { it.second })
                    .mapValues { (_, states) -> states.maxBy(::interest) }
            }
        }
    }

    private companion object {
        /** Higher wins when one host has several sessions. */
        fun interest(state: SshSessionState): Int = when {
            state.isLive -> 3
            state.isBusy -> 2
            state is SshSessionState.Failed -> 1
            else -> 0
        }
    }
}
