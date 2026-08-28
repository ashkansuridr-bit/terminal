package app.terminalssh.secure.ssh

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * A local shell running on the Android device itself.
 *
 * Security model:
 * - Runs as the app's own UID in the app sandbox.
 * - Working directory is the app's private cacheDir; no access to /data/data of other apps.
 * - Shell is `sh` (mksh on most Android devices), not `su` — no root escalation.
 * - Command audit log stored in memory (last 200 entries) for the session.
 * - Commands have only the app UID's permissions; this is not a network sandbox.
 */
class LocalShell(private val context: Context) {

    data class ShellState(
        val running: Boolean = false,
        val cwd: String = "",
        val pid: Int = 0,
    )

    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()

    /** Last 200 commands for audit trail. */
    private val _auditLog = MutableStateFlow<List<AuditEntry>>(emptyList())
    val auditLog: StateFlow<List<AuditEntry>> = _auditLog.asStateFlow()

    data class AuditEntry(
        val command: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private var process: Process? = null
    @Volatile private var outputStream: OutputStream? = null
    private var readerThread: Job? = null
    private var onOutput: ((String) -> Unit)? = null

    /**
     * Starts the local shell. The [onOutput] callback receives all stdout+stderr
     * text. Must be called from a coroutine-capable scope.
     */
    fun start(scope: CoroutineScope, onOutput: (String) -> Unit) {
        this.onOutput = onOutput
        val workDir = File(context.cacheDir, "shell")
        workDir.mkdirs()

        val pb = ProcessBuilder("sh")
            .directory(workDir)
            .apply {
                environment().clear()
                environment()["HOME"] = workDir.absolutePath
                environment()["TMPDIR"] = File(context.cacheDir, "tmp").apply { mkdirs() }.absolutePath
                environment()["PATH"] = "/system/bin:/system/xbin"
                environment()["SHELL"] = "/system/bin/sh"
                environment()["LANG"] = "en_US.UTF-8"
                // Prevent the shell from inheriting any sensitive env vars.
                redirectErrorStream(true)
            }

        val proc = pb.start()
        process = proc
        outputStream = proc.outputStream
        _state.value = ShellState(
            running = true,
            cwd = workDir.absolutePath,
            pid = runCatching {
                (proc.javaClass.getMethod("pid").invoke(proc) as Number).toInt()
            }.getOrDefault(0),
        )

        readerThread = scope.launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val buf = CharArray(8192)
            try {
                while (isActive) {
                    val read = reader.read(buf)
                    if (read == -1) break
                    val chunk = String(buf, 0, read)
                    onOutput(chunk)
                }
            } catch (_: Exception) {
            } finally {
                _state.value = _state.value.copy(running = false)
            }
        }

        // Monitor process exit
        scope.launch(Dispatchers.IO) {
            proc.waitFor()
            _state.value = _state.value.copy(running = false)
            readerThread?.cancel()
        }
    }

    /** Sends a command to the shell (adds newline automatically). */
    fun sendCommand(command: String) {
        _auditLog.value = (_auditLog.value + AuditEntry(command)).takeLast(MAX_AUDIT_ENTRIES)
        val os = outputStream ?: return
        synchronized(os) {
            runCatching {
                os.write((command + "\n").toByteArray())
                os.flush()
            }
        }
    }

    /** Sends raw bytes (for Ctrl+C, Ctrl+D, etc.). */
    fun sendBytes(bytes: ByteArray) {
        val os = outputStream ?: return
        synchronized(os) {
            runCatching {
                os.write(bytes)
                os.flush()
            }
        }
    }

    /** Sends Ctrl+C to interrupt the running process. */
    fun sendInterrupt() = sendBytes(byteArrayOf(0x03))

    /** Sends Ctrl+D (EOF). */
    fun sendEof() = sendBytes(byteArrayOf(0x04))

    /** Sends a signal to resize the terminal. */
    fun resize(columns: Int, rows: Int) {
        // Local shell doesn't have PTY, but we track dimensions for future use.
    }

    /** Stops the shell and cleans up. */
    fun stop() {
        readerThread?.cancel()
        outputStream?.let { os ->
            synchronized(os) { runCatching { os.close() } }
        }
        outputStream = null
        process?.destroyForcibly()
        process = null
        _state.value = ShellState()
    }

    val isRunning: Boolean get() = _state.value.running

    companion object {
        private const val MAX_AUDIT_ENTRIES = 200
    }
}
