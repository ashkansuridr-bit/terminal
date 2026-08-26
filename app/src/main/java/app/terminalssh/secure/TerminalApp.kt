package app.terminalssh.secure

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        vault = AndroidKeyStoreVault(this)
        knownHosts = KnownHostsStore(this)
        hosts = HostStore(this)
        settings = Settings(this)
        settingsStore = SettingsStore(this)
        client = JschSshClient(vault, knownHosts)
        sessions = SessionRegistry()
        ThumbnailCache.init(cacheDir)
    }
}
