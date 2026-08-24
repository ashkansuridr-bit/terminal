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
import app.terminalssh.secure.security.SecretEncoding
import app.terminalssh.secure.security.SecretIo
import app.terminalssh.secure.security.PrivateKeyFormat
import app.terminalssh.secure.security.VaultAad
import app.terminalssh.secure.security.VaultLimits
import app.terminalssh.secure.service.SshForegroundService
import app.terminalssh.secure.ssh.KnownHostsVerifier
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ssh.SshSessionState
import kotlinx.coroutines.Dispatchers
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
        (profile.auth as? AuthMethod.Password)?.vaultRef?.takeIf { it.isNotBlank() }
            ?.let { container.vault.delete(it, VaultAad.PASSWORD) }
        container.hosts.delete(profile.id)
        _hosts.value = container.hosts.hosts()
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
                if (state is SshSessionState.Failed && !state.hostKeyChanged) _toast.value = state.message
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
        sessions.close(id)
        SshForegroundService.sync(getApplication(), sessions.liveCount())
    }

    // ---- clipboard ----

    val pasteRequested = MutableStateFlow(false)

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
