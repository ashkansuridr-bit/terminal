package app.terminalssh.secure.ssh

import app.terminalssh.secure.sftp.SftpController
import app.terminalssh.secure.sftp.TransferCoordinator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-owned teardown; service and activity use the same cleanup path. */
class SessionLifecycleManager(
    private val registry: SessionRegistry,
    private val scope: CoroutineScope,
    private val syncService: () -> Unit,
) {
    val controllers = ConcurrentHashMap<String, SftpController>()
    private val closing = ConcurrentHashMap.newKeySet<String>()
    private val _cleanupFailures = MutableStateFlow<Set<String>>(emptySet())
    val cleanupFailures = _cleanupFailures.asStateFlow()

    @Synchronized
    fun controllerFor(id: String, create: () -> SftpController): SftpController {
        check(id !in closing && registry.sessions.value.any { it.id == id }) { "Session is closing" }
        return controllers.getOrPut(id) {
            create().also { TransferCoordinator.register(id, it) }
        }
    }

    @Synchronized
    fun closeSession(id: String) {
        if (!closing.add(id)) return
        val controller = controllers.remove(id)
        scope.launch(Dispatchers.IO) {
            var failed = false
            try {
                try { controller?.closeAndJoin() } catch (failure: Exception) { failed = true }
                // Always unregister even if persistence/cleanup reports a failure.
                TransferCoordinator.unregister(id)
                try { registry.close(id) } catch (failure: Exception) { failed = true }
            } finally {
                synchronized(this@SessionLifecycleManager) {
                    if (failed) _cleanupFailures.value = _cleanupFailures.value + id
                    closing.remove(id)
                }
                syncService()
            }
        }
    }

    fun closeAllSessions() = registry.sessions.value.map { it.id }.forEach(::closeSession)
}
