package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.net.Uri
import app.terminalssh.secure.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Drives the browser and the transfer queue against one SSH session.
 *
 * The scheduling rules live in [TransferQueue], which is pure and unit-tested; this
 * class is the thin layer that does the actual I/O and feeds results back into it.
 */
class SftpController(
    private val session: SshSession,
    private val contentResolver: ContentResolver,
    private val scope: CoroutineScope,
) {
    data class BrowserState(
        val path: String = RemotePath.ROOT,
        val entries: List<RemoteEntry> = emptyList(),
        val loading: Boolean = false,
        val errorKind: TransferErrorKind? = null,
    )

    private val _browser = MutableStateFlow(BrowserState())
    val browser: StateFlow<BrowserState> = _browser.asStateFlow()

    val queue = TransferQueue()

    private var client: SftpClient? = null
    private var pumpJob: Job? = null

    /** Cancellation flags keyed by transfer id, read by the copy loops. */
    private val cancelled = mutableSetOf<String>()

    private suspend fun client(): SftpClient = withContext(Dispatchers.IO) {
        client?.let { return@withContext it }
        val opened = session.openSftp() ?: throw IllegalStateException("session is not connected")
        client = opened
        opened
    }

    fun openHome() = scope.launch {
        val start = runCatching { withContext(Dispatchers.IO) { client().home() } }
            .getOrDefault(RemotePath.ROOT)
        navigate(start)
    }

    fun navigate(path: String) {
        scope.launch {
            _browser.value = _browser.value.copy(loading = true, errorKind = null)
            val result = runCatching {
                withContext(Dispatchers.IO) { client().list(path) }
            }
            _browser.value = result.fold(
                onSuccess = { entries ->
                    BrowserState(path = RemotePath.normalize(path), entries = entries, loading = false)
                },
                onFailure = { failure ->
                    // Keep the previous listing on screen rather than blanking it; an
                    // error banner over stale content beats an empty directory.
                    _browser.value.copy(loading = false, errorKind = SftpClient.classify(failure))
                },
            )
        }
    }

    fun navigateUp() = navigate(RemotePath.parent(_browser.value.path))

    fun refresh() = navigate(_browser.value.path)

    // ---- transfers ----

    fun enqueueDownload(entry: RemoteEntry, destination: Uri) {
        queue.enqueue(
            Transfer(
                id = UUID.randomUUID().toString(),
                direction = TransferDirection.DOWNLOAD,
                remotePath = entry.path,
                localUri = destination.toString(),
                displayName = entry.name,
                totalBytes = entry.sizeBytes,
            ),
        )
        pump()
    }

    fun enqueueUpload(source: Uri, displayName: String, remoteDirectory: String) {
        queue.enqueue(
            Transfer(
                id = UUID.randomUUID().toString(),
                direction = TransferDirection.UPLOAD,
                remotePath = RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(displayName)),
                localUri = source.toString(),
                displayName = displayName,
            ),
        )
        pump()
    }

    fun pause(id: String) {
        cancelled += id
        queue.pause(id)
    }

    fun resume(id: String) {
        cancelled -= id
        queue.resume(id)
        pump()
    }

    fun cancel(id: String) {
        cancelled += id
        queue.cancel(id)
    }

    fun clearFinished() = queue.clearFinished()

    /** Starts the next transfer if the queue allows one; re-entrant and cheap. */
    private fun pump() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch {
            while (isActive) {
                val next = queue.nextToStart() ?: break
                queue.markRunning(next.id)
                cancelled -= next.id
                runTransfer(queue.transfers.value.first { it.id == next.id })
            }
        }
    }

    private suspend fun runTransfer(transfer: Transfer) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                when (transfer.direction) {
                    TransferDirection.DOWNLOAD -> download(transfer)
                    TransferDirection.UPLOAD -> upload(transfer)
                }
            }
        }
        when {
            transfer.id in cancelled -> Unit // pause()/cancel() already set the state.
            result.isSuccess -> queue.markCompleted(transfer.id)
            else -> queue.fail(transfer.id, SftpClient.classify(result.exceptionOrNull()!!))
        }
    }

    private fun download(transfer: Transfer) {
        val uri = Uri.parse(transfer.localUri)
        // "wt" truncates: a resumed download re-fetches from zero rather than appending
        // to a partial file, because SAF gives no reliable way to learn how many bytes
        // actually reached storage.
        val sink = contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("cannot open destination")
        sink.use {
            client!!.download(transfer.remotePath, it, resumeFrom = 0L) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }
    }

    private fun upload(transfer: Transfer) {
        val uri = Uri.parse(transfer.localUri)
        val source = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open source")
        source.use {
            client!!.upload(it, transfer.remotePath, resumeFrom = 0L) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }
    }

    /** Signals a user-requested stop, distinguishing it from a real transfer failure. */
    private class InterruptedTransfer : RuntimeException("transfer interrupted by the user")

    fun onSessionLost() {
        queue.onConnectionLost()
        close()
    }

    fun close() {
        pumpJob?.cancel()
        runCatching { client?.close() }
        client = null
    }
}
