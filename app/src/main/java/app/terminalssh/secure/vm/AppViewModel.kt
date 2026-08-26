package app.terminalssh.secure.vm

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.account.AccountException
import app.terminalssh.secure.account.AccountFailure
import app.terminalssh.secure.account.AccountIdentity
import app.terminalssh.secure.account.accountProvider
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.model.KeyEntry
import app.terminalssh.secure.model.SnippetEntry
import app.terminalssh.secure.agents.AgentInstallScript
import app.terminalssh.secure.agents.AgentKeyRef
import app.terminalssh.secure.agents.CodingAgent
import app.terminalssh.secure.security.KeyAlgorithm
import app.terminalssh.secure.security.KeyGeneration
import app.terminalssh.secure.security.SecretEncoding
import app.terminalssh.secure.security.SecretIo
import app.terminalssh.secure.security.PrivateKeyFormat
import app.terminalssh.secure.security.VaultAad
import app.terminalssh.secure.security.VaultLimits
import app.terminalssh.secure.service.HostShortcuts
import app.terminalssh.secure.service.SshForegroundService
import app.terminalssh.secure.sftp.SftpController
import app.terminalssh.secure.storage.SshConfigExport
import app.terminalssh.secure.storage.SshConfigImport
import app.terminalssh.secure.ssh.KnownHostsVerifier
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ui.stringRes
import app.terminalssh.secure.ssh.SshSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as TerminalApp
    val sessions = container.sessions
    val settings = container.settings
    private val account = accountProvider(app)

    // ---- sftp ----

    // Assumes closeSession is the only teardown path for a session: if SessionRegistry's
    // closeAll() is ever called directly, any controller cached here for that session
    // would leak (its pump loop keeps running against a now-destroyed SshSession).
    private val sftpControllers = mutableMapOf<String, SftpController>()

    /**
     * The SFTP controller for [session], created on first use and kept alive across tab
     * switches — on [viewModelScope], not tied to the Files tab's composition — so a
     * transfer keeps running while the user is on another tab. Torn down only when the
     * session itself closes, in [closeSession].
     */
    fun sftpControllerFor(session: SshSession): SftpController =
        sftpControllers.getOrPut(session.id) {
            val application = getApplication<Application>()
            SftpController(session, application.contentResolver, application.cacheDir, viewModelScope)
        }

    /** False in market builds, which ship without any account integration. */
    val accountSupported: Boolean get() = account.isSupported

    private val _hosts = MutableStateFlow(container.hosts.hosts())
    val hosts: StateFlow<List<HostProfile>> = _hosts.asStateFlow()

    private val _keys = MutableStateFlow(container.hosts.keys())
    val keys: StateFlow<List<KeyEntry>> = _keys.asStateFlow()

    private val _snippets = MutableStateFlow(container.hosts.snippets())
    val snippets: StateFlow<List<SnippetEntry>> = _snippets.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _accountIdentity = MutableStateFlow<AccountIdentity?>(null)
    val accountIdentity: StateFlow<AccountIdentity?> = _accountIdentity.asStateFlow()

    /** Set once after a successful key generation, so the UI can show the public half. */
    private val _generatedPublicKey = MutableStateFlow<String?>(null)
    val generatedPublicKey: StateFlow<String?> = _generatedPublicKey.asStateFlow()

    fun setQuery(value: String) { _query.value = value }
    fun consumeToast() { _toast.value = null }
    fun notify(message: String) { _toast.value = message }

    // ---- hosts ----

    fun saveHost(profile: HostProfile, password: CharArray?): Boolean {
        return try {
            var stored = profile
            if (password != null && password.isNotEmpty()) {
                val ref = (profile.auth as? AuthMethod.Password)?.vaultRef?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString()
                val bytes = SecretEncoding.utf8(password)
                try {
                    container.vault.put(ref, bytes, VaultAad.PASSWORD)
                } finally {
                    bytes.fill(0)
                }
                stored = profile.copy(auth = AuthMethod.Password(ref))
            }
            container.hosts.upsert(stored)
            _hosts.value = container.hosts.hosts()
            true
        } catch (_: Exception) {
            _toast.value = getApplication<Application>().getString(R.string.host_save_failed)
            false
        } finally {
            password?.fill('\u0000')
        }
    }

    fun deleteHost(profile: HostProfile) {
        // The private key itself is a reusable Keys-screen entity, removed separately
        // via deleteKey(); only the per-host secret dies with the host.
        when (val auth = profile.auth) {
            is AuthMethod.Password -> auth.vaultRef.takeIf { it.isNotBlank() }
                ?.let { container.vault.delete(it, VaultAad.PASSWORD) }
            is AuthMethod.PrivateKey -> auth.passphraseVaultRef?.takeIf { it.isNotBlank() }
                ?.let { container.vault.delete(it, VaultAad.PASSPHRASE) }
        }
        container.hosts.delete(profile.id)
        _hosts.value = container.hosts.hosts()
        // Replaces the whole shortcut set, so the deleted host cannot linger in the
        // launcher pointing at an id that no longer resolves.
        HostShortcuts.refresh(getApplication(), _hosts.value)
    }

    /**
     * Imports servers from an OpenSSH config file. Existing hosts are never overwritten:
     * a profile whose host/port/user already exists is skipped, so importing the same
     * file twice does not duplicate the list.
     */
    fun importHostsFromSshConfig(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("cannot read config file")

                    val existing = container.hosts.hosts()
                        .map { Triple(it.host.lowercase(), it.port, it.username.lowercase()) }
                        .toSet()

                    val imported = SshConfigImport.parse(text).filter {
                        Triple(it.host.lowercase(), it.port, it.username.lowercase()) !in existing
                    }
                    imported.forEach { container.hosts.upsert(it) }
                    imported.size
                }
            }
            result.onSuccess { count ->
                _hosts.value = container.hosts.hosts()
                _toast.value = getApplication<Application>()
                    .resources.getQuantityString(R.plurals.hosts_imported, count, count)
            }.onFailure {
                _toast.value = string(R.string.hosts_import_failed)
            }
        }
    }

    /**
     * Writes the host list out as an OpenSSH config the user picks the destination for.
     * Contains no secrets, so it is safe to put in ordinary storage or send to yourself.
     */
    fun exportHostsToSshConfig(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = SshConfigExport.render(container.hosts.hosts())
                    getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("cannot write config file")
                }
            }
            _toast.value = string(
                if (result.isSuccess) R.string.hosts_exported else R.string.hosts_export_failed,
            )
        }
    }

    /** Schema-driven settings store, rendered generically by the settings screen. */
    val settingsStore get() = container.settingsStore

    /**
     * Writes preferences to a file the user picks. Contains no credentials: the registry
     * holds preferences only, and only values that differ from their default are written
     * so a restored backup cannot freeze today's defaults in place.
     */
    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = container.settingsStore.exportJson()
                    getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("cannot write settings file")
                }.isSuccess
            }
            _toast.value = string(if (ok) R.string.settings_exported else R.string.settings_export_failed)
        }
    }

    /** @param onApplied invoked on the main thread so the settings screen can re-read values. */
    fun importSettings(uri: Uri, onApplied: () -> Unit) {
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("cannot read settings file")
                    container.settingsStore.importJson(text)
                }.getOrNull()
            }
            if (applied == null) {
                _toast.value = string(R.string.settings_import_failed)
            } else {
                onApplied()
                _toast.value = getApplication<Application>()
                    .getString(R.string.settings_imported, applied)
            }
        }
    }

    fun toggleFavorite(profile: HostProfile) {
        container.hosts.upsert(profile.copy(favorite = !profile.favorite))
        _hosts.value = container.hosts.hosts()
    }

    fun hasStoredSecret(profile: HostProfile): Boolean = when (val auth = profile.auth) {
        is AuthMethod.Password -> auth.vaultRef.isNotBlank()
        is AuthMethod.PrivateKey -> auth.keyVaultRef.isNotBlank()
    }

    // ---- sessions ----

    fun openSession(profile: HostProfile, password: CharArray? = null): SshSession {
        try {
            val context = getApplication<Application>()
            val session = SshSession(
                id = UUID.randomUUID().toString(),
                profile = profile,
                client = container.client,
                keepAlive = settings.keepAlive,
                onClipboardCopy = { text -> copyToClipboard(context, text) },
                onPasteRequest = { pasteRequested.value = true },
            )
            sessions.add(session)
            container.hosts.touch(profile.id)
            _hosts.value = container.hosts.hosts()
            HostShortcuts.refresh(context, _hosts.value)

            val bytes = password?.let { SecretEncoding.utf8(it) }
            session.connect(bytes)
            observe(session)
            return session
        } finally {
            password?.fill('\u0000')
        }
    }

    private fun observe(session: SshSession) {
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is SshSessionState.Failed && !state.hostKeyChanged) {
                    _toast.value = string(state.kind.stringRes)
                }
                SshForegroundService.sync(getApplication(), sessions.liveCount())
            }
        }
    }

    fun trustHostKey(session: SshSession, pending: SshSessionState.AwaitingHostKeyApproval) {
        try {
            container.knownHosts.put(pending.host, pending.port, pending.algorithm, pending.key)
        } finally {
            pending.key.fill(0)
        }
        session.retryAfterTrust()
    }

    fun forgetHostKey(host: String, port: Int) = container.knownHosts.remove(host, port)

    fun knownHosts(): List<KnownHostsVerifier.KnownHost> = container.knownHosts.all()

    fun closeSession(id: String) {
        sftpControllers.remove(id)?.close()
        sessions.close(id)
        SshForegroundService.sync(getApplication(), sessions.liveCount())
    }

    // ---- clipboard ----

    val pasteRequested = MutableStateFlow(false)

    private var clipboardClearJob: Job? = null

    fun clipboardText(): String? {
        val manager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return manager.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString()
    }

    private fun copyToClipboard(context: Context, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.app_name), text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = (clip.description.extras ?: PersistableBundle()).apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        scheduleClipboardClear(manager, text)
    }

    /**
     * Wipes a terminal copy from the clipboard after the configured delay, but only if
     * the clipboard still holds exactly what we put there — clearing something the user
     * copied afterwards from another app would be data loss, not a security win.
     */
    private fun scheduleClipboardClear(manager: ClipboardManager, copied: String) {
        val delaySeconds = settings.clipboardClearSeconds
        if (delaySeconds <= 0) return
        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(delaySeconds * 1_000L)
            val current = runCatching {
                manager.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString()
            }.getOrNull()
            if (current == copied) {
                runCatching {
                    // clearPrimaryClip only exists from API 28; an empty clip is the
                    // equivalent on older releases.
                    if (Build.VERSION.SDK_INT >= 28) {
                        manager.clearPrimaryClip()
                    } else {
                        manager.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }
    }


    // ---- optional account ----

    fun signInAccount(activity: Activity) {
        if (!account.isSupported) return

        viewModelScope.launch {
            account.signIn(activity)
                .onSuccess { identity ->
                    _accountIdentity.value = identity
                    _toast.value = string(R.string.google_sign_in_success)
                }
                .onFailure { error ->
                    val failure = (error as? AccountException)?.failure ?: AccountFailure.ERROR
                    _toast.value = string(
                        when (failure) {
                            AccountFailure.NOT_CONFIGURED -> R.string.google_not_configured
                            AccountFailure.NO_CREDENTIAL -> R.string.google_no_credential
                            else -> R.string.google_sign_in_failed
                        }
                    )
                }
        }
    }

    fun signOutAccount() {
        viewModelScope.launch {
            runCatching { account.signOut() }
            _accountIdentity.value = null
            _toast.value = string(R.string.google_sign_out_success)
        }
    }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)

    // ---- private keys ----

    fun importKey(uri: Uri, name: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { input ->
                            SecretIo.readBounded(input, VaultLimits.MAX_PRIVATE_KEY_BYTES)
                        } ?: error("cannot read key file")

                    var ref: String? = null
                    try {
                        VaultLimits.requirePrivateKeySize(bytes)
                        val algorithm = PrivateKeyFormat.detect(bytes)

                        ref = UUID.randomUUID().toString()
                        container.vault.put(ref, bytes, VaultAad.PRIVATE_KEY)

                        // This is a private-material integrity hash, not an SSH public-key
                        // fingerprint. The UI labels it accordingly to avoid ambiguity.
                        val fingerprint = KnownHostsVerifier.sha256Fingerprint(bytes)
                        val entry = KeyEntry(
                            id = ref,
                            name = name.ifBlank { "key-${System.currentTimeMillis()}" },
                            fingerprint = fingerprint,
                            algorithm = algorithm,
                            createdAt = System.currentTimeMillis(),
                            hasPassphrase = false,
                        )
                        container.hosts.upsertKey(entry)
                        entry
                    } catch (t: Throwable) {
                        ref?.let { runCatching { container.vault.delete(it, VaultAad.PRIVATE_KEY) } }
                        throw t
                    } finally {
                        bytes.fill(0)
                    }
                }
            }
            result.onSuccess {
                _keys.value = container.hosts.keys()
                _toast.value = getApplication<Application>().getString(R.string.keys_imported)
            }.onFailure {
                _toast.value = getApplication<Application>().getString(R.string.keys_import_failed)
            }
        }
    }

    /**
     * Generates a key pair on this device and stores the private half in the vault.
     * The public half is returned through [generatedPublicKey] so the user can copy it
     * to the server; it is not a secret and is deliberately kept outside the vault.
     */
    fun generateKey(algorithm: KeyAlgorithm, name: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val comment = name.trim().ifBlank { "terminalssh" }
                    val generated = KeyGeneration.generate(algorithm, comment)
                    var ref: String? = null
                    try {
                        ref = UUID.randomUUID().toString()
                        container.vault.put(ref, generated.privateKey, VaultAad.PRIVATE_KEY)
                        val entry = KeyEntry(
                            id = ref,
                            name = comment,
                            fingerprint = KnownHostsVerifier.sha256Fingerprint(generated.privateKey),
                            algorithm = generated.algorithm.label,
                            createdAt = System.currentTimeMillis(),
                            hasPassphrase = false,
                        )
                        try {
                            container.hosts.upsertKey(entry)
                        } catch (t: Throwable) {
                            // Never leave private-key ciphertext behind without metadata.
                            container.vault.delete(ref, VaultAad.PRIVATE_KEY)
                            throw t
                        }
                        generated.publicKey
                    } catch (t: Throwable) {
                        ref?.let { runCatching { container.vault.delete(it, VaultAad.PRIVATE_KEY) } }
                        throw t
                    } finally {
                        generated.wipe()
                    }
                }
            }
            result.onSuccess { publicKey ->
                _keys.value = container.hosts.keys()
                _generatedPublicKey.value = publicKey
                _toast.value = string(R.string.keys_generated)
            }.onFailure {
                _toast.value = string(R.string.keys_generate_failed)
            }
        }
    }

    fun consumeGeneratedPublicKey() { _generatedPublicKey.value = null }

    fun copyPublicKey(publicKey: String) {
        val manager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // A public key is not a secret, so this deliberately skips the auto-clear that
        // terminal copies get — the user needs it long enough to paste into a server.
        manager.setPrimaryClip(ClipData.newPlainText("ssh public key", publicKey))
        _toast.value = string(R.string.keys_public_copied)
    }

    /**
     * The user-facing name behind a SAF uri, falling back to the last path segment.
     * SAF uris are opaque, so the display name has to be queried from the provider.
     */
    fun displayNameFor(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "upload"
    }

    // ---- coding agent API keys ----

    /**
     * Stores an agent credential in the same vault the SSH secrets use.
     *
     * @param hostId scope the key to one server, or null to make it the fallback for all.
     */
    fun saveAgentKey(agent: CodingAgent, hostId: String?, key: CharArray) {
        val bytes = SecretEncoding.utf8(key)
        try {
            val ref = if (hostId.isNullOrBlank()) {
                AgentKeyRef.global(agent)
            } else {
                AgentKeyRef.forHost(agent, hostId)
            }
            container.vault.put(ref, bytes, VaultAad.AGENT_API_KEY)
            _toast.value = string(R.string.agent_key_saved)
        } catch (_: Exception) {
            _toast.value = string(R.string.agent_key_save_failed)
        } finally {
            bytes.fill(0)
            key.fill('\u0000')
        }
    }

    fun hasAgentKey(agent: CodingAgent, hostId: String?): Boolean =
        AgentKeyRef.resolutionOrder(agent, hostId).any { ref ->
            container.vault.get(ref, VaultAad.AGENT_API_KEY)?.also { it.fill(0) } != null
        }

    fun deleteAgentKey(agent: CodingAgent, hostId: String?) {
        val ref = if (hostId.isNullOrBlank()) {
            AgentKeyRef.global(agent)
        } else {
            AgentKeyRef.forHost(agent, hostId)
        }
        container.vault.delete(ref, VaultAad.AGENT_API_KEY)
    }

    /**
     * Sends the agent's key into the session as an environment variable.
     *
     * The command is written so it does not land in shell history, and the decrypted
     * bytes are wiped as soon as they have been handed to the session.
     */
    fun injectAgentKey(agent: CodingAgent, session: SshSession): Boolean {
        val variable = agent.apiKeyVariable ?: return false
        val bytes = AgentKeyRef.resolutionOrder(agent, session.profile.id)
            .firstNotNullOfOrNull { ref -> container.vault.get(ref, VaultAad.AGENT_API_KEY) }
            ?: run {
                _toast.value = string(R.string.agent_key_missing)
                return false
            }
        return try {
            val command = AgentInstallScript.exportKeyCommand(variable, bytes.toString(Charsets.UTF_8))
            session.send(command + "\n")
            _toast.value = string(R.string.agent_key_injected)
            true
        } finally {
            bytes.fill(0)
        }
    }

    fun deleteKey(entry: KeyEntry) {
        container.vault.delete(entry.id, VaultAad.PRIVATE_KEY)
        container.hosts.deleteKey(entry.id)
        _keys.value = container.hosts.keys()
    }

    // ---- encrypted snippets ----

    fun saveSnippet(name: String, command: CharArray) {
        if (command.isEmpty()) {
            command.fill('\u0000')
            return
        }
        val bytes = SecretEncoding.utf8(command)
        val ref = UUID.randomUUID().toString()
        try {
            VaultLimits.requireSnippetSize(bytes)
            container.vault.put(ref, bytes, VaultAad.SNIPPET)
            val entry = SnippetEntry(
                id = ref,
                name = name.trim().ifBlank { "snippet-${System.currentTimeMillis()}" },
                createdAt = System.currentTimeMillis(),
            )
            try {
                container.hosts.upsertSnippet(entry)
            } catch (t: Throwable) {
                container.vault.delete(ref, VaultAad.SNIPPET)
                throw t
            }
            _snippets.value = container.hosts.snippets()
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Inserts a snippet exactly as stored and never appends Enter. This prevents a
     * one-tap destructive command from executing without a final explicit user action.
     */
    fun insertSnippet(entry: SnippetEntry, session: SshSession) {
        val bytes = container.vault.get(entry.id, VaultAad.SNIPPET) ?: return
        try {
            session.send(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    fun deleteSnippet(entry: SnippetEntry) {
        container.vault.delete(entry.id, VaultAad.SNIPPET)
        container.hosts.deleteSnippet(entry.id)
        _snippets.value = container.hosts.snippets()
    }
}
