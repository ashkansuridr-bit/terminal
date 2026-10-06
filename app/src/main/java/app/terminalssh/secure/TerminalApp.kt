package app.terminalssh.secure

import android.app.Application
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import app.terminalssh.secure.security.AndroidKeyStoreVault
import app.terminalssh.secure.ssh.JschSshClient
import app.terminalssh.secure.ssh.SessionRegistry
import app.terminalssh.secure.storage.HostStore
import app.terminalssh.secure.storage.KnownHostsStore
import app.terminalssh.secure.settings.SettingsStore
import app.terminalssh.secure.sftp.ThumbnailCache
import app.terminalssh.secure.storage.Settings

/** Single composition root. No DI framework: the graph is six objects. */
class TerminalApp : Application() {
    lateinit var vault: AndroidKeyStoreVault; private set
    lateinit var knownHosts: KnownHostsStore; private set
    lateinit var hosts: HostStore; private set
    lateinit var settings: Settings; private set

    /** Schema-driven store; [settings] stays for the paths not yet migrated to it. */
    lateinit var settingsStore: SettingsStore; private set
    lateinit var client: JschSshClient; private set
    lateinit var sessions: SessionRegistry; private set

    val sessionScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )
    lateinit var lifecycle: app.terminalssh.secure.ssh.SessionLifecycleManager; private set

    private val connectivityManager by lazy { getSystemService(android.net.ConnectivityManager::class.java) }
    private val transferNetworkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) = wakeTransferSchedulers()
        override fun onLost(network: android.net.Network) = wakeTransferSchedulers()
        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) =
            wakeTransferSchedulers()
    }

    private fun wakeTransferSchedulers() {
        // Callback runs on a system thread; dispatch and controller ownership stay on Main.
        sessionScope.launch { lifecycle.controllers.values.forEach { it.onSchedulingConditionsChanged() } }
    }

    /** Unknown network metering holds a Wi-Fi-only queue rather than spending mobile data. */
    fun onUnmeteredNetwork(): Boolean {
        val manager = connectivityManager ?: return false
        return try {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (failure: SecurityException) {
            false
        }
    }

    override fun onCreate() {
        super.onCreate()
        vault = AndroidKeyStoreVault(this)
        knownHosts = KnownHostsStore(this)
        hosts = HostStore(this)
        settingsStore = SettingsStore(this)
        settings = Settings.sharing(settingsStore)
        client = JschSshClient(vault, knownHosts, hosts)
        sessions = SessionRegistry()
        lifecycle = app.terminalssh.secure.ssh.SessionLifecycleManager(sessions, sessionScope) {
            app.terminalssh.secure.service.SshForegroundService.sync(
                this, sessions.liveCount(), app.terminalssh.secure.sftp.TransferCoordinator.activeCount(),
            )
        }
        // Both observations belong to the process, like sessions and foreground transfers.
        // They remain live when the activity's ViewModel is cleared.
        sessionScope.launch {
            settingsStore.revision.drop(1).collect { wakeTransferSchedulers() }
        }
        try {
            connectivityManager?.registerDefaultNetworkCallback(transferNetworkCallback)
        } catch (failure: SecurityException) {
            android.util.Log.w("TransferScheduler", "Network observation permission unavailable")
        }
        ThumbnailCache.init(cacheDir)
    }
}
