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
import app.terminalssh.secure.security.CredentialReference
import app.terminalssh.secure.security.CredentialReferences
import app.terminalssh.secure.storage.MetadataCommitException
import app.terminalssh.secure.settings.SettingsImportPreview
import app.terminalssh.secure.settings.SettingsRegistry
import app.terminalssh.secure.service.HostShortcuts
import app.terminalssh.secure.service.SshForegroundService
import app.terminalssh.secure.sftp.TransferRecoveryStore
import app.terminalssh.secure.sftp.SftpWorkspaceStore
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class HostMetadataState { Loading, Ready, Failed }
class HostMetadataUnavailableException : IllegalStateException("host metadata unavailable")

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as TerminalApp
    val sessions = container.sessions
    val sessionCleanupFailures = container.lifecycle.cleanupFailures
    val settings = container.settings
    private val account = accountProvider(app)

    init {
        viewModelScope.launch {
            container.settingsStore.revision.drop(1).collect {
                val enabled = container.settingsStore.get(SettingsRegistry.keepAlive)
                sessions.sessions.value.forEach { it.setKeepAlive(enabled) }
            }
        }
    }

    // ---- sftp ----

    private val sftpControllers get() = container.lifecycle.controllers

    /**
     * The SFTP controller for [session], created on first use and kept alive across tab
     * switches — on the application session scope, not tied to the Files tab's composition — so a
     * transfer keeps running while the user is on another tab. Torn down only when the
     * session itself closes, in [closeSession].
     */
    fun sftpControllerFor(session: SshSession): SftpController =
        container.lifecycle.controllerFor(session.id) {
            val application = getApplication<Application>()
            SftpController(
                session,
                application.contentResolver,
                application.cacheDir,
                container.sessionScope,
                SftpWorkspaceStore(
                    application.getSharedPreferences("sftp_workspace", android.content.Context.MODE_PRIVATE),
                    session.profile.id,
                ),
                // Read per transfer, so changing the ceiling takes effect on the next file.
                rateLimitBytesPerSecond = { container.settings.transferLimitKbPerSecond * 1024L },
                mayStartTransfers = { !container.settings.transfersWifiOnly || container.onUnmeteredNetwork() },
                persistentDir = application.filesDir,
            )
        }

    /** False in market builds, which ship without any account integration. */
    val accountSupported: Boolean get() = account.isSupported

    private data class MetadataSnapshot(
        val hosts: List<HostProfile>, val keys: List<KeyEntry>, val snippets: List<SnippetEntry>,
    )
    private fun readMetadataSnapshot() = MetadataSnapshot(
        container.hosts.hosts(), container.hosts.keys(), container.hosts.snippets(),
    )
    // A failed parse is an explicit unavailable state, never an ordinary empty store.
    private val initialMetadata = try { readMetadataSnapshot() } catch (_: Exception) { null }
    private val _hostMetadataState = MutableStateFlow(
        if (initialMetadata == null) HostMetadataState.Failed else HostMetadataState.Ready,
    )
    val hostMetadataState = _hostMetadataState.asStateFlow()

    private val _hosts = MutableStateFlow(initialMetadata?.hosts ?: emptyList())
    val hosts: StateFlow<List<HostProfile>> = _hosts.asStateFlow()

    private val _keys = MutableStateFlow(initialMetadata?.keys ?: emptyList())
    val keys: StateFlow<List<KeyEntry>> = _keys.asStateFlow()

    private val _snippets = MutableStateFlow(initialMetadata?.snippets ?: emptyList())
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

    init { if (_hostMetadataState.value == HostMetadataState.Ready) retryCredentialCleanup() }

    private fun metadataMutationAllowed(): Boolean {
        if (_hostMetadataState.value == HostMetadataState.Ready) {
            try {
                readMetadataSnapshot() // Revalidate before a mutation, including stale UI callbacks.
                return true
            } catch (_: Exception) {
                _hosts.value = emptyList()
                _keys.value = emptyList()
                _snippets.value = emptyList()
                _hostMetadataState.value = HostMetadataState.Failed
            }
        }
        _toast.value = string(R.string.host_metadata_failed)
        return false
    }

    fun reloadHostMetadata() {
        if (_hostMetadataState.value == HostMetadataState.Loading) return
        _hostMetadataState.value = HostMetadataState.Loading
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { readMetadataSnapshot() }
                _hosts.value = snapshot.hosts
                _keys.value = snapshot.keys
                _snippets.value = snapshot.snippets
                _hostMetadataState.value = HostMetadataState.Ready
                retryCredentialCleanup()
            } catch (cancelled: CancellationException) {
                _hostMetadataState.value = HostMetadataState.Failed
                throw cancelled
            } catch (_: Exception) {
                _hosts.value = emptyList()
                _keys.value = emptyList()
                _snippets.value = emptyList()
                _hostMetadataState.value = HostMetadataState.Failed
                _toast.value = string(R.string.host_metadata_failed)
            }
        }
    }

    /** Explicit recovery export preserves raw records; it does not reset or repair trust. */
    fun exportRawHostMetadata(uri: Uri) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val raw = container.hosts.rawMetadataSnapshot()
                val resolver = getApplication<Application>().contentResolver
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "metadata export unavailable" }
                    .bufferedWriter(Charsets.UTF_8).use { it.write(raw) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _toast.value = string(R.string.host_metadata_export_failed) }
    }

    fun setQuery(value: String) { _query.value = value }
    fun consumeToast() { _toast.value = null }
    fun notify(message: String) { _toast.value = message }

    // Recovery is non-secret metadata. Nothing runs until the user chooses a verified
    // connected session; imports are PAUSED and reviewed in Files before Resume.
    private val recoveryStore = TransferRecoveryStore(app.filesDir)
    private val _recoverableTransfers = MutableStateFlow<List<TransferRecoveryStore.Entry>>(emptyList())
    val recoverableTransfers = _recoverableTransfers.asStateFlow()
    private val _recoveryLoading = MutableStateFlow(false)
    val recoveryLoading = _recoveryLoading.asStateFlow()

    fun refreshRecovery() {
        if (_recoveryLoading.value) return
        _recoveryLoading.value = true
        viewModelScope.launch {
            try {
                val ids = sessions.sessions.value.map { it.id }.toSet()
                _recoverableTransfers.value = withContext(Dispatchers.IO) { recoveryStore.discover(ids) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _toast.value = string(R.string.recovery_failed)
            } finally { _recoveryLoading.value = false }
        }
    }

    fun restoreTransfers(entry: TransferRecoveryStore.Entry) {
        if (_recoveryLoading.value) return
        _recoveryLoading.value = true
        viewModelScope.launch {
            try {
                val candidates = sessions.sessions.value.filter { it.state.value.isLive }
                val session = withContext(Dispatchers.IO) {
                    candidates.firstOrNull { recoveryStore.matches(entry.file, it.profile) }
                }
                if (session == null) { _toast.value = string(R.string.recovery_connect_first); return@launch }
                sftpControllerFor(session).importRecoveredQueue(entry.file)
                sessions.select(session.id)
                _toast.value = string(R.string.recovery_imported)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _toast.value = string(R.string.recovery_failed)
            } finally {
                _recoveryLoading.value = false
                refreshRecovery()
            }
        }
    }

    // ---- hosts ----

    /** Fresh refs make failure rollback possible without destroying the saved credential. */
    fun saveHost(profile: HostProfile, password: CharArray?, passphrase: CharArray? = null): Boolean {
        return try {
            if (!metadataMutationAllowed()) return false
            var stored = profile
            if (password != null && password.isNotEmpty()) {
                val ref = UUID.randomUUID().toString()
                storeFreshHostSecret(ref, password, VaultAad.PASSWORD)
                stored = profile.copy(auth = AuthMethod.Password(ref))
            } else if (profile.auth is AuthMethod.PrivateKey && passphrase != null) {
                val ref = if (passphrase.isEmpty()) null else UUID.randomUUID().toString()
                if (ref != null) storeFreshHostSecret(ref, passphrase, VaultAad.PASSPHRASE)
                stored = profile.copy(auth = profile.auth.copy(passphraseVaultRef = ref))
            }
            container.hosts.saveCredentials(stored)
            _hosts.value = container.hosts.hosts()
            retryCredentialCleanup()
            true
        } catch (failure: Exception) {
            // A failed rollback leaves disk ownership ambiguous. Preserve ciphertext and
            // its journal until restart loads durable metadata; never delete speculatively.
            if (failure !is MetadataCommitException || failure.rollbackSucceeded) {
                retryCredentialCleanup()
            }
            _toast.value = getApplication<Application>().getString(R.string.host_save_failed)
            false
        } finally {
            password?.fill('\u0000')
            passphrase?.fill('\u0000')
        }
    }

    private fun storeFreshHostSecret(ref: String, secret: CharArray, aad: VaultAad) {
        container.hosts.scheduleCredentialCleanup(CredentialReference(ref, aad))
        val bytes = SecretEncoding.utf8(secret)
        try {
            container.vault.put(ref, bytes, aad)
        } finally {
            bytes.fill(0)
        }
    }

    /** Durable journal survives cleanup failures/process death; failures are visible. */
    fun retryCredentialCleanup(): Boolean {
        if (!metadataMutationAllowed()) return false
        return try {
            container.hosts.pendingCredentialCleanup().forEach { ref ->
                if (!container.hosts.credentialIsReferenced(ref)) container.vault.delete(ref.ref, ref.aad)
                container.hosts.finishCredentialCleanup(ref)
            }
            true
        } catch (_: Exception) {
            _toast.value = string(R.string.credential_cleanup_pending)
            false
        }
    }

    fun deleteHost(profile: HostProfile) {
        if (!metadataMutationAllowed()) return
        try {
            container.hosts.removeHostCredentials(profile.id)
            _hosts.value = container.hosts.hosts()
            retryCredentialCleanup()
            HostShortcuts.refresh(getApplication(), _hosts.value)
        } catch (_: Exception) {
            _toast.value = string(R.string.host_save_failed)
        }
    }

    /**
     * Imports servers from an OpenSSH config file. Existing hosts are never overwritten:
     * a profile whose host/port/user already exists is skipped, so importing the same
     * file twice does not duplicate the list.
     */
    fun importHostsFromSshConfig(uri: Uri) {
        if (!metadataMutationAllowed()) return
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

    fun previewSettingsImport(uri: Uri, onPreview: (SettingsImportPreview) -> Unit) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { SecretIo.readBounded(it, MAX_SETTINGS_IMPORT_BYTES) }
                        ?: error("cannot read settings file")
                    try {
                        container.settingsStore.previewImport(bytes.toString(Charsets.UTF_8))
                            ?: error("invalid settings file")
                    } finally {
                        bytes.fill(0)
                    }
                }.getOrNull()
            }
            if (preview == null) {
                _toast.value = string(R.string.settings_import_failed)
            } else {
                onPreview(preview)
            }
        }
    }

    fun applySettingsImport(preview: SettingsImportPreview): Boolean {
        val applied = try {
            container.settingsStore.applyImport(preview)
        } catch (failure: IllegalStateException) {
            // Failed durable write/rollback must remain an import error, never success.
            _toast.value = string(R.string.settings_import_failed)
            return false
        }
        return if (applied == null) {
            _toast.value = string(R.string.settings_import_failed)
            false
        } else {
            _toast.value = getApplication<Application>()
                .getString(R.string.settings_imported, applied)
            true
        }
    }

    fun toggleFavorite(profile: HostProfile) {
        if (!metadataMutationAllowed()) return
        try {
            container.hosts.upsert(profile.copy(favorite = !profile.favorite))
            _hosts.value = container.hosts.hosts()
        } catch (failure: Exception) {
            if (failure is MetadataCommitException && !failure.rollbackSucceeded) {
                _hostMetadataState.value = HostMetadataState.Failed
            }
            _toast.value = string(R.string.host_save_failed)
        }
    }

    fun hasStoredSecret(profile: HostProfile): Boolean = when (val auth = profile.auth) {
        is AuthMethod.Password -> auth.vaultRef.isNotBlank()
        is AuthMethod.PrivateKey -> auth.keyVaultRef.isNotBlank()
    }

    // ---- sessions ----

    fun openSession(profile: HostProfile, password: CharArray? = null): SshSession {
        var publishedSessionId: String? = null
        try {
            if (!metadataMutationAllowed()) throw HostMetadataUnavailableException()
            val context = getApplication<Application>()
            // Validate/persist metadata before publishing any new session resources.
            try {
                container.hosts.touch(profile.id)
                _hosts.value = container.hosts.hosts()
                HostShortcuts.refresh(context, _hosts.value)
            } catch (_: Exception) {
                _hostMetadataState.value = HostMetadataState.Failed
                _hosts.value = emptyList()
                _keys.value = emptyList()
                _snippets.value = emptyList()
                _toast.value = string(R.string.host_metadata_failed)
                throw HostMetadataUnavailableException()
            }
            val session = SshSession(
                id = UUID.randomUUID().toString(),
                profile = profile,
                client = container.client,
                keepAlive = settings.keepAlive,
                // Read per output chunk so toggling it takes effect on the next line.
                maskSecretsInOutput = {
                    container.settingsStore.get(SettingsRegistry.maskSecretsInOutput)
                },
                terminalType = settings.terminalType,
                onClipboardCopy = { text -> copyToClipboard(context, text) },
                onPasteRequest = { pasteRequested.value = true },
            )
            sessions.add(session)
            publishedSessionId = session.id

            val bytes = password?.let { SecretEncoding.utf8(it) }
            session.connect(bytes)
            observe(session)
            return session
        } catch (failure: Throwable) {
            publishedSessionId?.let { container.lifecycle.closeSession(it) }
            throw failure
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
                syncForegroundService()
            }
        }
        // Also observe active transfers to keep the foreground service alive during SFTP
        viewModelScope.launch {
            // Small delay to let SftpController initialize if needed
            kotlinx.coroutines.delay(500)
            val sftp = sftpControllers[session.id] ?: return@launch
            sftp.queue.transfers.collect { syncForegroundService() }
        }
    }

    private fun syncForegroundService() {
        val activeTransfers = sftpControllers.values.sumOf {
            it.queue.active.size
        }
        SshForegroundService.sync(getApplication(), sessions.liveCount(), activeTransfers)
    }

    private val trustingHostKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun trustHostKey(session: SshSession, pending: SshSessionState.AwaitingHostKeyApproval) {
        viewModelScope.launch {
            if (!trustingHostKeys.add(session.id)) return@launch
            var copy: ByteArray? = null
            try {
                if (session.state.value !== pending) return@launch
                val key = pending.key.copyOf()
                copy = key
                withContext(Dispatchers.IO) {
                    container.knownHosts.put(pending.host, pending.port, pending.algorithm, key)
                }
                // A durable write is required before discarding the approval or reconnecting.
                if (session.state.value === pending) {
                    pending.key.fill(0)
                    session.retryAfterTrust()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _toast.value = string(R.string.trust_store_failed)
            } finally {
                copy?.fill(0)
                trustingHostKeys.remove(session.id)
            }
        }
    }

    fun forgetHostKey(host: String, port: Int) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { container.knownHosts.remove(host, port) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _toast.value = string(R.string.trust_store_failed)
        }
    }

    suspend fun knownHosts(): Result<List<KnownHostsVerifier.KnownHost>> = withContext(Dispatchers.IO) {
        try {
            Result.success(container.knownHosts.all())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun closeSession(id: String) = container.lifecycle.closeSession(id)
    fun closeAllSessions() = container.lifecycle.closeAllSessions()

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

    private companion object {
        const val MAX_SETTINGS_IMPORT_BYTES = 1_048_576
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
        if (!metadataMutationAllowed()) return
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
        if (!metadataMutationAllowed()) return
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

    /** Called only after explicit UI consent to an isolated tmux launch. */
    fun injectAgentKey(agent: CodingAgent, session: SshSession): Boolean {
        if (agent.apiKeyVariable == null) return false
        val bytes = AgentKeyRef.resolutionOrder(agent, session.profile.id)
            .firstNotNullOfOrNull { ref -> container.vault.get(ref, VaultAad.AGENT_API_KEY) }
            ?: run {
                _toast.value = string(R.string.agent_key_missing)
                return false
            }
        session.launchAgentWithKey(agent, bytes) { success ->
            _toast.value = string(if (success) R.string.agent_key_injected else R.string.agent_key_launch_failed)
        }
        return true // accepted for asynchronous execution; success is reported by callback
    }

    fun keyDependencies(entry: KeyEntry): List<HostProfile> =
        CredentialReferences.keyDependents(entry.id, container.hosts.hosts())

    /** Enforced again at the storage layer; a stale UI cannot delete a referenced key. */
    fun deleteKey(entry: KeyEntry): Boolean {
        if (!metadataMutationAllowed()) return false
        return try {
            if (keyDependencies(entry).isNotEmpty()) {
                _toast.value = string(R.string.key_dependencies_title)
                return false
            }
            container.hosts.deleteKey(entry.id)
            _keys.value = container.hosts.keys()
            retryCredentialCleanup()
            true
        } catch (_: Exception) {
            _toast.value = string(R.string.key_delete_failed)
            false
        }
    }

    /** Replacement or unlink-to-prompt is explicit; key deletion remains a separate action. */
    fun resolveKeyDependencies(entry: KeyEntry, replacementKeyId: String? = null): Boolean {
        if (!metadataMutationAllowed()) return false
        return try {
            val auth = if (replacementKeyId == null) AuthMethod.Password("") else {
                require(replacementKeyId != entry.id && container.hosts.keys().any { it.id == replacementKeyId })
                AuthMethod.PrivateKey(replacementKeyId)
            }
            container.hosts.replaceKeyForHosts(entry.id, auth)
            _hosts.value = container.hosts.hosts()
            retryCredentialCleanup()
            true
        } catch (_: Exception) {
            _toast.value = string(R.string.host_save_failed)
            false
        }
    }

    // ---- encrypted snippets ----

    fun saveSnippet(name: String, command: CharArray) {
        if (!metadataMutationAllowed()) {
            command.fill('\u0000')
            return
        }
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
        if (!metadataMutationAllowed()) return
        container.vault.delete(entry.id, VaultAad.SNIPPET)
        container.hosts.deleteSnippet(entry.id)
        _snippets.value = container.hosts.snippets()
    }
}
