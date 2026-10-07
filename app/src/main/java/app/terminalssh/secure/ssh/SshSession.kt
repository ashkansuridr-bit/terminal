package app.terminalssh.secure.ssh

import android.os.Handler
import android.os.Looper
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.security.StreamingSecretMasker
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
    keepAlive: Boolean,
    private val terminalType: String = "xterm-256color",
    /**
     * Whether to redact secret-shaped output before it reaches the screen.
     *
     * Read per chunk rather than captured once, so turning it on takes effect on the
     * next line instead of needing a reconnect. The audit flagged this setting as
     * configurable in the UI but never actually wired to rendering; this is the wiring.
     */
    private val maskSecretsInOutput: () -> Boolean = { false },
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
    @Volatile private var keepAlive = keepAlive

    // A write error is the difference between "your command ran" and "your keystrokes
    // vanished into a socket that had already died". Never swallowed; the reader loop
    // consumes it and surfaces it as the state the user actually sees.
    @Volatile private var terminalWriteFailure: String? = null

    // Keystrokes typed while a session is (re)connecting are held here and flushed to
    // the new channel in order. Bounded so a wedged reconnect cannot grow memory; when
    // the cap is hit further input is dropped and the disconnect path reports it.
    private val pendingInput = BoundedPendingInput(MAX_BUFFERED_INPUT_BYTES)

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
                terminalType = terminalType,
            )
            if (gen != generation.get()) {
                opened.close()
                return
            }
            shell = opened
            // Everything typed while (re)connecting goes to the fresh channel now, in
            // FIFO order, before a new keystroke can overtake it on the same io thread.
            val buffered = pendingInput.takeAll()
            for (bytes in buffered) {
                try {
                    opened.output.write(bytes)
                    opened.output.flush()
                } catch (failure: Throwable) {
                    terminalWriteFailure = failure.message ?: "terminal write failed"
                    runCatching { opened.channel.disconnect() }
                    break
                } finally {
                    bytes.fill(0)
                }
            }
            buffered.forEach { it.fill(0) }
            // Port forwards the user requested survive reconnects: a fresh SSH session
            // has none, so re-establish them (and report failures truthfully).
            reapplyForwards()
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
                val lost = pendingInput.clearBytes()
                _state.value = SshSessionState.Failed(
                    message = if (lost > 0) {
                        "${t.message ?: t.javaClass.simpleName} · $lost bytes of typed input were lost"
                    } else {
                        t.message ?: t.javaClass.simpleName
                    },
                    kind = ConnectionError.classify(t),
                )
            }
        }
    }

    fun retryAfterTrust() {
        clearPendingHostKey()
        val gen = generation.incrementAndGet()
        _state.value = SshSessionState.Connecting
        io.execute { doConnect(gen, attempt = 0) }
    }

    private fun startReader(open: JschSshClient.Shell, gen: Long) {
        // Masking is re-read on every chunk, so toggling the setting while a session
        // runs takes effect on the next line. Disabling first flushes the current
        // masker: a half-seen secret prefix is suppressed in full, never exposed.
        var masker: StreamingSecretMasker? = if (maskSecretsInOutput()) StreamingSecretMasker() else null
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
                        val enabledNow = maskSecretsInOutput()
                        if (enabledNow && masker == null) {
                            masker = StreamingSecretMasker()
                        } else if (!enabledNow && masker != null) {
                            // The tail is posted before this chunk, so output order is
                            // preserved and the disabling boundary never leaks a held
                            // candidate from the old stream.
                            val tail = masker!!.finish()
                            masker = null
                            if (tail.isNotEmpty()) {
                                val secured = tail.copyOf()
                                tail.fill(0)
                                main.post {
                                    try {
                                        if (gen == generation.get() && shell === open) {
                                            emulator.writeInput(secured, 0, secured.size)
                                        }
                                    } finally {
                                        secured.fill(0)
                                    }
                                }
                            }
                        }
                        val display = if (masker == null) chunk else {
                            try { masker!!.accept(chunk) } finally { chunk.fill(0) }
                        }
                        main.post {
                            try {
                                if (gen == generation.get() && shell === open) {
                                    emulator.writeInput(display, 0, display.size)
                                }
                            } finally {
                                display.fill(0)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Falls through to the disconnect handling below.
            } finally {
                buffer.fill(0)
                val tail = masker?.finish()
                main.post {
                    if (tail != null) {
                        try {
                            if (gen == generation.get() && (shell === open || shell == null)) emulator.writeInput(tail, 0, tail.size)
                        } finally {
                            tail.fill(0)
                        }
                    }
                }
                if (gen == generation.get()) {
                    shell = null
                    val writeFailure = terminalWriteFailure
                    terminalWriteFailure = null
                    // The remote end sends an exit-status when the shell process itself
                    // terminated (e.g. the user typed `exit`) — that is a deliberate
                    // close, not a dropped connection, and must not trigger a reconnect
                    // loop the user has no way to stop short of the explicit disconnect
                    // action. Absence of an exit-status (-1) means the channel went away
                    // without the remote side saying why, which is what reconnect exists
                    // for.
                    val remoteExitedCleanly = runCatching { open.channel.exitStatus }.getOrDefault(-1) >= 0
                    when {
                        // Truthful terminal write failure: the user's keystrokes did not
                        // reach the server. Reconnect when possible; otherwise show why.
                        writeFailure != null -> {
                            pendingInput.clear()
                            if (autoReconnect && profile.maxReconnectAttempts > 0) {
                                _portForwards.value = _portForwards.value.map {
                                    it.copy(state = PortForwardState.PENDING, error = null)
                                }
                                _state.value = SshSessionState.Reconnecting(1, profile.maxReconnectAttempts)
                                io.execute { doConnect(gen, attempt = 0) }
                            } else {
                                _portForwards.value = _portForwards.value.map {
                                    it.copy(state = PortForwardState.FAILED, error = "session closed")
                                }
                                _state.value = SshSessionState.Failed(
                                    message = writeFailure,
                                    kind = ConnectionErrorKind.CONNECTION_LOST,
                                )
                            }
                        }
                        autoReconnect && !remoteExitedCleanly && profile.maxReconnectAttempts > 0 -> {
                            _portForwards.value = _portForwards.value.map {
                                it.copy(state = PortForwardState.PENDING, error = null)
                            }
                            _state.value = SshSessionState.Reconnecting(1, profile.maxReconnectAttempts)
                            io.execute { doConnect(gen, attempt = 0) }
                        }
                        else -> {
                            _portForwards.value = _portForwards.value.map {
                                it.copy(state = PortForwardState.FAILED, error = "session closed")
                            }
                            _state.value = SshSessionState.Closed
                        }
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
        val copy = bytes.copyOf()
        val current = shell
        when {
            current != null -> io.execute { doWrite(current, copy) }
            isBufferingInput() -> {
                // Keep the keystroke for the channel that is being established rather
                // than silently discarding it (the previous behaviour lost input typed
                // during every reconnect).
                if (!pendingInput.offer(copy)) {
                    // Cap reached: hold what fits, drop the rest, and say so via the
                    // terminal write-failure path once the session settles.
                    terminalWriteFailure = "reconnect buffered too long — excess input dropped"
                    copy.fill(0)
                }
            }
            else -> copy.fill(0)
        }
    }

    /** Writes to a live channel or records the real failure; never swallows it. */
    private fun doWrite(current: JschSshClient.Shell, bytes: ByteArray) {
        try {
            current.output.write(bytes)
            current.output.flush()
        } catch (failure: Throwable) {
            terminalWriteFailure = failure.message ?: "terminal write failed"
            runCatching { current.channel.disconnect() }
        } finally {
            bytes.fill(0)
        }
    }

    private fun isBufferingInput(): Boolean {
        val s = _state.value
        return s is SshSessionState.Connecting || s is SshSessionState.Reconnecting
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

    /** Applies immediately to a live JSch session and to every later reconnect. */
    fun setKeepAlive(enabled: Boolean) {
        keepAlive = enabled
        val open = shell ?: return
        io.execute {
            if (shell === open) runCatching { open.setKeepAlive(enabled) }
        }
    }

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
        pendingInput.clear()
        _portForwards.value = _portForwards.value.map {
            it.copy(state = PortForwardState.FAILED, error = "session ended")
        }
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

    /** Takes ownership of [key]. No PTY, terminal echo, command history or output capture.
     * Completion means the detached launch command returned zero, not that the agent authenticated.
     */
    fun launchAgentWithKey(agent: app.terminalssh.secure.agents.CodingAgent, key: ByteArray,
                           onComplete: (Boolean) -> Unit) {
        if (key.isEmpty() || key.size > 16 * 1024 || key.any { it == 0.toByte() || it == 10.toByte() || it == 13.toByte() }) {
            key.fill(0)
            main.post { onComplete(false) }
            return
        }
        try {
            io.execute {
                var channel: com.jcraft.jsch.ChannelExec? = null
                val payload = key.copyOf(key.size + 1)
                key.fill(0)
                payload[payload.lastIndex] = 10
                var success = false
                try {
                    val launchingShell = shell ?: error("No connected session")
                    val transport = launchingShell.session
                    val socket = "terminal-agent-" + java.util.UUID.randomUUID()
                    channel = transport.openChannel("exec") as com.jcraft.jsch.ChannelExec
                    channel.setPty(false)
                    channel.setCommand(app.terminalssh.secure.agents.AgentInstallScript.secureLaunchCommand(agent, socket))
                    channel.setInputStream(java.io.ByteArrayInputStream(payload))
                    // An agent or a remote startup script could print its environment. Never render it.
                    channel.setOutputStream(object : java.io.OutputStream() { override fun write(b: Int) {} override fun write(b: ByteArray, off: Int, len: Int) {} })
                    channel.setErrStream(object : java.io.OutputStream() { override fun write(b: Int) {} override fun write(b: ByteArray, off: Int, len: Int) {} })
                    channel.connect(10_000)
                    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
                    while (!channel.isClosed && System.nanoTime() < deadline) Thread.sleep(20)
                    success = channel.isClosed && channel.exitStatus == 0
                    if (success && shell !== launchingShell) success = false
                    if (success) {
                        val attach = app.terminalssh.secure.agents.AgentInstallScript.secureAttachCommand(socket) + "\n"
                        launchingShell.output.let { it.write(attach.toByteArray(Charsets.UTF_8)); it.flush() }
                    }
                } catch (_: Exception) {
                    // Report a sanitized failure; remote exception text may contain credentials.
                    success = false
                } finally {
                    try { channel?.disconnect() } finally { payload.fill(0); key.fill(0) }
                    val result = success
                    main.post { onComplete(result) }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            key.fill(0)
            main.post { onComplete(false) }
        }
    }

    // ---- port forwarding ----

    enum class PortForwardState { PENDING, ACTIVE, FAILED }

    data class PortForward(
        val bindPort: Int,
        val host: String,
        val port: Int,
        val isLocal: Boolean,
        val state: PortForwardState = PortForwardState.PENDING,
        val error: String? = null,
    ) {
        /** Stable identity for the forward, independent of runtime state. */
        val key: String get() = (if (isLocal) "L" else "R") + ":$bindPort"
    }

    private val _portForwards = MutableStateFlow<List<PortForward>>(emptyList())
    val portForwards: StateFlow<List<PortForward>> = _portForwards.asStateFlow()

    /** Records the forward immediately and applies it to the live session if there is one. */
    private fun pushForward(forward: PortForward) {
        _portForwards.value = _portForwards.value + forward
        applyForwardOnce(forward)
    }

    /** Creates a local port forward (L); failures surface as PENDING→FAILED, never vanish. */
    fun addLocalForward(bindPort: Int, host: String, port: Int) {
        pushForward(PortForward(bindPort, host, port, isLocal = true))
    }

    /** Creates a remote port forward (R); failures surface as PENDING→FAILED, never vanish. */
    fun addRemoteForward(bindPort: Int, host: String, port: Int) {
        pushForward(PortForward(bindPort, host, port, isLocal = false))
    }

    /** Applies one forward to the current session (no-op while not connected). */
    private fun applyForwardOnce(forward: PortForward) {
        io.execute {
            val s = shell?.session ?: return@execute
            val outcome = runCatching { installForward(s, forward) }
            updateForward(forward) { current ->
                outcome.fold(
                    onSuccess = { current.copy(state = PortForwardState.ACTIVE, error = null) },
                    onFailure = { current.copy(state = PortForwardState.FAILED, error = sanitizedForwardError(it)) },
                )
            }
        }
    }

    /** Re-establishes every requested forward after (re)connect: the fresh session has none. */
    private fun reapplyForwards() {
        val targets = _portForwards.value
        if (targets.isEmpty()) return
        io.execute {
            val s = shell?.session ?: return@execute
            for (forward in targets) {
                val outcome = runCatching { installForward(s, forward) }
                updateForward(forward) { current ->
                    outcome.fold(
                        onSuccess = { current.copy(state = PortForwardState.ACTIVE, error = null) },
                        onFailure = { current.copy(state = PortForwardState.FAILED, error = sanitizedForwardError(it)) },
                    )
                }
            }
        }
    }

    private fun installForward(session: com.jcraft.jsch.Session, forward: PortForward) {
        if (forward.isLocal) {
            session.setPortForwardingL("127.0.0.1", forward.bindPort, forward.host, forward.port)
        } else {
            session.setPortForwardingR("127.0.0.1", forward.bindPort, forward.host, forward.port)
        }
    }

    private fun updateForward(target: PortForward, transform: (PortForward) -> PortForward) {
        _portForwards.value = _portForwards.value.map { if (it.key == target.key) transform(it) else it }
    }

    /** JSch exceptions can embed paths and addresses; keep the readable essence only. */
    private fun sanitizedForwardError(failure: Throwable): String =
        failure.message?.substringBefore('\n')?.take(120) ?: "port forward failed"

    /** Removes a local port forward (harmless if the current session never had it). */
    fun removeLocalForward(bindPort: Int) = removeForward(PortForward(bindPort, "", 0, isLocal = true))

    /** Removes a remote port forward (harmless if the current session never had it). */
    fun removeRemoteForward(bindPort: Int) = removeForward(PortForward(bindPort, "", 0, isLocal = false))

    private fun removeForward(forward: PortForward) {
        io.execute {
            shell?.session?.let { session ->
                runCatching {
                    if (forward.isLocal) session.delPortForwardingL("127.0.0.1", forward.bindPort)
                    else session.delPortForwardingR("127.0.0.1", forward.bindPort)
                }
            }
            _portForwards.value = _portForwards.value.filterNot { it.key == forward.key }
        }
    }

    companion object {
        private const val INITIAL_ROWS = 24
        private const val INITIAL_COLS = 80
        private const val READ_BUFFER = 16 * 1024
        private const val MAX_BUFFERED_INPUT_BYTES = 64 * 1024
    }
}

/**
 * Holds keystrokes produced while a session is (re)connecting, so a reconnect does not
 * eat what the user typed. Bounded because a wedged reconnect must not grow memory
 * without limit; when full, further input is refused and the disconnect path reports it.
 */
private class BoundedPendingInput(private val maxTotalBytes: Int) {

    private val items = ArrayDeque<ByteArray>()
    private var total = 0

    /**
     * Accepts a copy the caller already owns. On success the buffer owns [bytes] and
     * zeroes it eventually; on refusal the caller must zero it, [bytes] is untouched.
     */
    @Synchronized
    fun offer(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        if (total + bytes.size > maxTotalBytes) return false
        items.addLast(bytes)
        total += bytes.size
        return true
    }

    /** Returns all buffered input in FIFO order and clears the queue of ownership. */
    @Synchronized
    fun takeAll(): ArrayDeque<ByteArray> {
        val out = ArrayDeque<ByteArray>(items.size)
        while (items.isNotEmpty()) out.addLast(items.removeFirst())
        total = 0
        return out
    }

    /** Zeroes and discards everything buffered; returns the number of bytes dropped. */
    @Synchronized
    fun clearBytes(): Int {
        var dropped = 0
        while (items.isNotEmpty()) {
            val bytes = items.removeFirst()
            dropped += bytes.size
            bytes.fill(0)
        }
        total = 0
        return dropped
    }

    @Synchronized
    fun clear() {
        while (items.isNotEmpty()) items.removeFirst().fill(0)
        total = 0
    }
}
