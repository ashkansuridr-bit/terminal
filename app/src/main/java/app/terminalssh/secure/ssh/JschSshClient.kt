package app.terminalssh.secure.ssh

import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.security.AndroidKeyStoreVault
import app.terminalssh.secure.security.VaultAad
import app.terminalssh.secure.storage.HostStore
import app.terminalssh.secure.storage.KnownHostsStore
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Thin JSch adapter. Must never be called from the main thread: every method here
 * performs socket I/O.
 */
class JschSshClient(
    private val vault: AndroidKeyStoreVault,
    private val knownHosts: KnownHostsStore,
    private val hostStore: HostStore,
) {
    data class Shell(
        val session: Session,
        val channel: ChannelShell,
        val input: InputStream,
        val output: OutputStream,
    ) : AutoCloseable {
        val alive: Boolean get() = session.isConnected && !channel.isClosed

        /** Local port forwarding: binds [bindPort] on localhost, forwards to [host]:[port] via the tunnel. */
        fun forwardLocal(bindPort: Int, host: String, port: Int) {
            session.setPortForwardingL("127.0.0.1", bindPort, host, port)
        }

        /** Remote port forwarding: binds [bindPort] on the remote host, forwards to [host]:[port] locally. */
        fun forwardRemote(bindPort: Int, host: String, port: Int) {
            session.setPortForwardingR("127.0.0.1", bindPort, host, port)
        }

        /** Removes a local port forward. */
        fun removeLocalForward(bindPort: Int) {
            session.delPortForwardingL("127.0.0.1", bindPort)
        }

        /** Removes a remote port forward. */
        fun removeRemoteForward(bindPort: Int) {
            session.delPortForwardingR("127.0.0.1", bindPort)
        }

        override fun close() {
            runCatching { output.close() }
            runCatching { input.close() }
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }

    /**
     * @param passwordOverride used when the profile has no stored secret (quick connect).
     *        The array is zeroed before returning.
     */
    fun connect(
        profile: HostProfile,
        columns: Int,
        rows: Int,
        passwordOverride: ByteArray? = null,
        keepAlive: Boolean = true,
    ): Shell {
        val jsch = JSch()
        val captured = AtomicReference<PresentedHostKey?>()
        jsch.hostKeyRepository = PolicyHostKeyRepository(profile, knownHosts, captured)

        when (val auth = profile.auth) {
            is AuthMethod.Password -> Unit
            is AuthMethod.PrivateKey -> {
                val privateKey = requireNotNull(vault.get(auth.keyVaultRef, VaultAad.PRIVATE_KEY)) {
                    "missing private key"
                }
                val passphrase = auth.passphraseVaultRef?.let { vault.get(it, VaultAad.PASSPHRASE) }
                try {
                    jsch.addIdentity(profile.id, privateKey, null, passphrase)
                } finally {
                    privateKey.fill(0)
                    passphrase?.fill(0)
                }
            }
        }

        val session = jsch.getSession(profile.username, profile.host, profile.port)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")

        // ProxyJump: if this host has a jump host configured, tunnel through it.
        val jumpProfile = profile.jumpHostId.takeIf { it.isNotBlank() }
            ?.let { id -> hostStore.hosts().firstOrNull { it.id == id } }
        if (jumpProfile != null) {
            // Add the jump host's identity so JSch can authenticate to it.
            when (val jumpAuth = jumpProfile.auth) {
                is AuthMethod.PrivateKey -> {
                    val key = requireNotNull(vault.get(jumpAuth.keyVaultRef, VaultAad.PRIVATE_KEY)) {
                        "missing jump host private key"
                    }
                    val pass = jumpAuth.passphraseVaultRef?.let { vault.get(it, VaultAad.PASSPHRASE) }
                    try {
                        jsch.addIdentity("jump-${jumpProfile.id}", key, null, pass)
                    } finally {
                        key.fill(0)
                        pass?.fill(0)
                    }
                }
                is AuthMethod.Password -> {
                    val pw = jumpProfile.auth.vaultRef.takeIf { it.isNotBlank() }
                        ?.let { vault.get(it, VaultAad.PASSWORD) }
                    if (pw != null) {
                        try { session.setPassword(String(pw)) } finally { pw.fill(0) }
                    }
                }
            }
            val jumpUser = jumpProfile.username
            val jumpHost = jumpProfile.host
            val jumpPort = jumpProfile.port
            session.setConfig("ProxyJump", "$jumpUser@$jumpHost:$jumpPort")
        }

        if (profile.auth is AuthMethod.Password) {
            val password = passwordOverride
                ?: profile.auth.vaultRef.takeIf { it.isNotBlank() }?.let { vault.get(it, VaultAad.PASSWORD) }
                ?: throw MissingCredential()
            try {
                session.setPassword(password)
            } finally {
                password.fill(0)
            }
        } else {
            passwordOverride?.fill(0)
        }

        try {
            session.connect(CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            runCatching { session.disconnect() }
            captured.getAndSet(null)?.let { presented ->
                when (val decision = presented.decision) {
                    is KnownHostsVerifier.Decision.FirstUse -> {
                        val ownedKey = requireNotNull(presented.key)
                        presented.key = null
                        throw FirstUseRequired(
                            profile.host, profile.port, presented.algorithm, ownedKey, decision.fingerprint,
                        )
                    }
                    is KnownHostsVerifier.Decision.Reject -> {
                        presented.clear()
                        throw HostKeyRejected(
                            buildString {
                                append(decision.reason)
                                decision.expected?.let { append("; expected ").append(it) }
                                decision.actual?.let { append("; received ").append(it) }
                            },
                        )
                    }
                    KnownHostsVerifier.Decision.Accept -> presented.clear()
                }
            }
            throw e
        } finally {
            runCatching { jsch.removeAllIdentity() }
        }

        captured.getAndSet(null)?.clear()

        // The connect timeout doubles as SO_TIMEOUT. Leaving it set would tear the
        // session down after 15 idle seconds, so clear it and use keepalive instead.
        session.timeout = 0
        if (keepAlive) {
            session.serverAliveInterval = KEEPALIVE_MS
            session.serverAliveCountMax = KEEPALIVE_RETRIES
        }

        var channel: ChannelShell? = null
        try {
            channel = session.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType(PTY_TYPE, columns, rows, 0, 0)
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(CONNECT_TIMEOUT_MS)
            return Shell(session, channel, input, output)
        } catch (e: Exception) {
            runCatching { channel?.disconnect() }
            runCatching { session.disconnect() }
            throw e
        }
    }

    private data class PresentedHostKey(
        val algorithm: String,
        var key: ByteArray?,
        val decision: KnownHostsVerifier.Decision,
    ) {
        fun clear() {
            key?.fill(0)
            key = null
        }
    }

    private class PolicyHostKeyRepository(
        private val profile: HostProfile,
        private val store: KnownHostsStore,
        private val captured: AtomicReference<PresentedHostKey?>,
        private val verifier: KnownHostsVerifier = KnownHostsVerifier(),
    ) : HostKeyRepository {

        override fun check(host: String?, key: ByteArray): Int {
            val algorithm = runCatching { HostKey(profile.host, key).type }.getOrDefault("unknown")
            val known = store.get(profile.host, profile.port)
            val decision = verifier.verify(profile.host, profile.port, algorithm, key, known, profile.hostKeyPolicy)
            val ownedKey = if (decision is KnownHostsVerifier.Decision.FirstUse) key.copyOf() else null
            captured.getAndSet(PresentedHostKey(algorithm, ownedKey, decision))?.clear()
            return when (decision) {
                KnownHostsVerifier.Decision.Accept -> HostKeyRepository.OK
                is KnownHostsVerifier.Decision.FirstUse -> HostKeyRepository.NOT_INCLUDED
                is KnownHostsVerifier.Decision.Reject -> HostKeyRepository.CHANGED
            }
        }

        /**
         * JSch reads this to pin the server-host-key algorithm it negotiates. Returning an
         * empty array lets the server pick a different algorithm on a later connection,
         * which the verifier would then report as a key change — a false MITM alarm.
         */
        override fun getHostKey(host: String?, type: String?): Array<HostKey> {
            val known = store.get(profile.host, profile.port) ?: return emptyArray()
            if (type != null && type != known.algorithm) return emptyArray()
            return runCatching { arrayOf(HostKey(profile.host, known.key)) }.getOrDefault(emptyArray())
        }

        override fun getHostKey(): Array<HostKey> = getHostKey(profile.host, null)
        override fun add(hostkey: HostKey?, ui: com.jcraft.jsch.UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "TerminalSSH known hosts"
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val KEEPALIVE_MS = 30_000
        private const val KEEPALIVE_RETRIES = 3
        private const val PTY_TYPE = "xterm-256color"
    }
}
