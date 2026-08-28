package app.terminalssh.secure.ssh

import android.os.Handler
import android.os.Looper
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * One live SSH shell plus its terminal emulator.
 *
 * Threading contract — the previous version crashed because it was ignored:
 *  - every socket operation (connect, write, resize, close) runs on [io];
 *  - every emulator mutation is posted to the main thread;
 *  - the reader loop owns its own thread and never touches the emulator directly.
 */
class SshSession(
    val id: String,
    @Volatile var profile: HostProfile,
    private val client: JschSshClient,
    private val keepAlive: Boolean,
    private val onClipboardCopy: (String) -> Unit,
    private val onPasteRequest: () -> Unit,
) {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-io-$id") }
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)

    private val _state = MutableStateFlow<SshSessionState>(SshSessionState.Idle)
    val state: StateFlow<SshSessionState> = _state.asStateFlow()

    @Volatile private var shell: JschSshClient.Shell? = null
    @Volatile private var reader: Thread? = null
    @Volatile private var pendingPassword: ByteArray? = null
    @Volatile private var autoReconnect = true

    val terminalInput = TerminalInputController()

    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        initialRows = INITIAL_ROWS,
        initialCols = INITIAL_COLS,
        onKeyboardInput = { bytes -> send(bytes) },
        onResize = { dimensions ->
            val channel = shell?.channel ?: return@create
            io.execute {
                runCatching { channel.setPtySize(dimensions.columns, dimensions.rows, 0, 0) }
            }
        },
        onClipboardCopy = onClipboardCopy,
    )

    val title: String get() = profile.displayName

    /** @param password only for hosts without a stored secret; zeroed once used. */
    fun connect(password: ByteArray? = null) {
        terminalInput.clearTransients()
        pendingPassword?.fill(0)
        pendingPassword = password
        autoReconnect = true
        val gen = generation.incrementAndGet()
        _state.value = SshSessionState.Connecting
        io.execute { doConnect(gen, attempt = 0) }
    }

    private fun doConnect(gen: Long, attempt: Int) {
        if (gen != generation.get()) return
        terminalInput.clearTransients()
        try {
            val dimensions = emulator.dimensions
            val opened = client.connect(
                profile = profile,
                columns = dimensions.columns.coerceAtLeast(20),
                rows = dimensions.rows.coerceAtLeast(4),
                passwordOverride = pendingPassword?.copyOf(),
                keepAlive = keepAlive,
            )
            if (gen != generation.get()) {
                opened.close()
                return
            }
            shell = opened
            _state.value = SshSessionState.Connected
            // A password only needs to stay decrypted in memory if reconnecting will
            // need it again; a saved host re-fetches it from the vault instead, so
            // wipe it as soon as it has served its purpose.
            if (hasStoredCredential()) clearPendingPassword()
            startReader(opened, gen)
        } catch (first: FirstUseRequired) {
            if (gen != generation.get()) {
                first.key.fill(0)
                return
            }
            _state.value = SshSessionState.AwaitingHostKeyApproval(
                host = first.host,
                port = first.port,
                algorithm = first.algorithm,
                fingerprint = first.fingerprint,
                key = first.key,
            )
        } catch (changed: HostKeyRejected) {
            if (gen != generation.get()) return
            clearPendingPassword()
            _state.value = SshSessionState.Failed(
                message = changed.message ?: "host key rejected",
                hostKeyChanged = true,
                kind = ConnectionErrorKind.HOST_KEY_CHANGED,
            )
        } catch (t: Throwable) {
            if (gen != generation.get()) return
            val maxAttempts = profile.maxReconnectAttempts
            if (autoReconnect && attempt < maxAttempts && ReconnectPolicy.isTransient(t)) {
                _state.value = SshSessionState.Reconnecting(attempt + 1, maxAttempts)
                Thread.sleep(ReconnectPolicy.delayMillis(attempt))
                doConnect(gen, attempt + 1)
            } else {
                clearPendingPassword()
                _state.value = SshSessionState.Failed(
                    message = t.message ?: t.javaClass.simpleName,
                    kind = ConnectionError.classify(t),
                )
            }
        }
    }

    /** Called after the user approves a first-use fingerprint; the caller stores the key. */
    fun retryAfterTrust() {
        clearPendingHostKey()
        val gen = generation.incrementAndGet()
        _state.value = SshSessionState.Connecting
        io.execute { doConnect(gen, attempt = 0) }
    }

    private fun startReader(open: JschSshClient.Shell, gen: Long) {
        val thread = Thread({
            val buffer = ByteArray(READ_BUFFER)
            try {
                while (gen == generation.get() && !open.channel.isClosed) {
                    val read = open.input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        if (gen != generation.get() || shell !== open) {
                            buffer.fill(0, 0, read)
                            break
                        }
                        val chunk = buffer.copyOf(read)
                        buffer.fill(0, 0, read)
                        main.post {
                            try {
                                if (gen == generation.get() && shell === open) {
                                    emulator.writeInput(chunk, 0, chunk.size)
                                }
                            } finally {
                                chunk.fill(0)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Falls through to the disconnect handling below.
            } finally {
                buffer.fill(0)
                if (gen == generation.get()) {
                    shell = null
                    // The remote end sends an exit-status when the shell process itself
                    // terminated (e.g. the user typed `exit`) — that is a deliberate
                    // close, not a dropped connection, and must not trigger a reconnect
                    // loop the user has no way to stop short of the explicit disconnect
                    // action. Absence of an exit-status (-1) means the channel went away
                    // without the remote side saying why, which is what reconnect exists
                    // for.
                    val remoteExitedCleanly = runCatching { open.channel.exitStatus }.getOrDefault(-1) >= 0
                    if (autoReconnect && !remoteExitedCleanly && profile.maxReconnectAttempts > 0) {
                        _state.value = SshSessionState.Reconnecting(1, profile.maxReconnectAttempts)
                        io.execute { doConnect(gen, attempt = 0) }
                    } else {
                        _state.value = SshSessionState.Closed
                    }
                }
                runCatching { open.close() }
            }
        }, "ssh-reader-$id")
        thread.isDaemon = true
        reader = thread
        thread.start()
    }

    fun send(bytes: ByteArray) {
        val current = shell ?: return
        val copy = bytes.copyOf()
        io.execute {
            try {
                current.output.write(copy)
                current.output.flush()
            } catch (_: Throwable) {
            } finally {
                copy.fill(0)
            }
        }
    }

    fun send(text: String) = send(text.encodeToByteArray())

    fun pressTerminalKey(key: TerminalKey) {
        emulator.dispatchKey(terminalInput.modifierMask(), key.vTermKey)
        terminalInput.clearTransients()
    }

    fun typeToolbarCharacter(character: Char) {
        emulator.dispatchCharacter(terminalInput.modifierMask(), character.code)
        terminalInput.clearTransients()
    }

    fun pressControl(letter: Char) {
        terminalInput.clearTransients()
        val controlByte = letter.uppercaseChar().code - '@'.code
        if (controlByte in 1..31) send(byteArrayOf(controlByte.toByte()))
    }

    fun requestPaste() = onPasteRequest()

    /**
     * An SFTP client riding this session's existing connection, or null when the session
     * is not connected. Reusing the connection avoids a second authentication and a
     * second host-key check for a server the user is already inside.
     *
     * The caller owns the returned client and must close it.
     */
    fun openSftp(): app.terminalssh.secure.sftp.SftpClient? =
        shell?.takeIf { it.alive }?.let { app.terminalssh.secure.sftp.SftpClient(it.session) }

    fun clearScreen() = main.post { runCatching { emulator.clearScreen() } }

    fun disconnect() {
        autoReconnect = false
        terminalInput.clearTransients()
        generation.incrementAndGet()
        clearPendingHostKey()
        val open = shell
        shell = null
        clearPendingPassword()
        _state.value = SshSessionState.Closed
        if (open != null) io.execute { runCatching { open.close() } }
    }

    fun destroy() {
        disconnect()
        io.shutdown()
    }

    private fun clearPendingHostKey() {
        (state.value as? SshSessionState.AwaitingHostKeyApproval)?.key?.fill(0)
    }

    private fun clearPendingPassword() {
        pendingPassword?.fill(0)
        pendingPassword = null
    }

    /** True when a reconnect can re-derive the credential from the vault, without [pendingPassword]. */
    private fun hasStoredCredential(): Boolean = when (val auth = profile.auth) {
        is AuthMethod.Password -> auth.vaultRef.isNotBlank()
        is AuthMethod.PrivateKey -> true
    }

    // ---- port forwarding ----

    data class PortForward(
        val bindPort: Int,
        val host: String,
        val port: Int,
        val isLocal: Boolean,
    )

    private val _portForwards = MutableStateFlow<List<PortForward>>(emptyList())
    val portForwards: StateFlow<List<PortForward>> = _portForwards.asStateFlow()

    /** Creates a local port forward (L) on the [io] thread. */
    fun addLocalForward(bindPort: Int, host: String, port: Int) {
        io.execute {
            val s = shell?.session ?: return@execute
            s.setPortForwardingL("127.0.0.1", bindPort, host, port)
            _portForwards.value = _portForwards.value + PortForward(bindPort, host, port, isLocal = true)
        }
    }

    /** Creates a remote port forward (R) on the [io] thread. */
    fun addRemoteForward(bindPort: Int, host: String, port: Int) {
        io.execute {
            val s = shell?.session ?: return@execute
            s.setPortForwardingR("127.0.0.1", bindPort, host, port)
            _portForwards.value = _portForwards.value + PortForward(bindPort, host, port, isLocal = false)
        }
    }

    /** Removes a local port forward. */
    fun removeLocalForward(bindPort: Int) {
        io.execute {
            shell?.session?.let { runCatching { it.delPortForwardingL("127.0.0.1", bindPort) } }
            _portForwards.value = _portForwards.value.filterNot { it.bindPort == bindPort && it.isLocal }
        }
    }

    /** Removes a remote port forward. */
    fun removeRemoteForward(bindPort: Int) {
        io.execute {
            shell?.session?.let { runCatching { it.delPortForwardingR("127.0.0.1", bindPort) } }
            _portForwards.value = _portForwards.value.filterNot { it.bindPort == bindPort && !it.isLocal }
        }
    }

    companion object {
        private const val INITIAL_ROWS = 24
        private const val INITIAL_COLS = 80
        private const val READ_BUFFER = 16 * 1024
    }
}
