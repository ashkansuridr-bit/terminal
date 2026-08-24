package app.terminalssh.secure.ssh

import android.os.Handler
import android.os.Looper
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
        pendingPassword?.fill(0)
        pendingPassword = password
        autoReconnect = true
        val gen = generation.incrementAndGet()
        _state.value = SshSessionState.Connecting
        io.execute { doConnect(gen, attempt = 0) }
    }

    private fun doConnect(gen: Long, attempt: Int) {
        if (gen != generation.get()) return
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
            _state.value = SshSessionState.Failed(changed.message ?: "host key rejected", hostKeyChanged = true)
        } catch (t: Throwable) {
            if (gen != generation.get()) return
            if (autoReconnect && attempt < MAX_RECONNECT && isTransient(t)) {
                _state.value = SshSessionState.Reconnecting(attempt + 1, MAX_RECONNECT)
                Thread.sleep(RECONNECT_DELAY_MS * (attempt + 1))
                doConnect(gen, attempt + 1)
            } else {
                clearPendingPassword()
                _state.value = SshSessionState.Failed(t.message ?: t.javaClass.simpleName)
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
                    if (autoReconnect) {
                        _state.value = SshSessionState.Reconnecting(1, MAX_RECONNECT)
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

    fun requestPaste() = onPasteRequest()

    fun clearScreen() = main.post { runCatching { emulator.clearScreen() } }

    fun disconnect() {
        autoReconnect = false
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

    private fun isTransient(t: Throwable): Boolean {
        val message = (t.message ?: "").lowercase()
        return "auth" !in message && "denied" !in message && "credential" !in message
    }

    companion object {
        private const val INITIAL_ROWS = 24
        private const val INITIAL_COLS = 80
        private const val READ_BUFFER = 16 * 1024
        private const val MAX_RECONNECT = 3
        private const val RECONNECT_DELAY_MS = 1500L
    }
}
