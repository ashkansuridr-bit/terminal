package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.net.Uri
import app.terminalssh.secure.ssh.SshSession
import java.io.File
import java.io.FileOutputStream
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
 *
 * Owned by [app.terminalssh.secure.vm.AppViewModel] on a per-session basis and kept
 * alive for as long as that session is — not tied to the Files tab's composition — so a
 * transfer keeps running while the user is on another tab. See
 * `AppViewModel.sftpControllerFor`/`closeSession`.
 */
class SftpController(
    private val session: SshSession,
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) {
    data class BrowserState(
        val path: String = RemotePath.ROOT,
        val entries: List<RemoteEntry> = emptyList(),
        val loading: Boolean = false,
        val errorKind: TransferErrorKind? = null,
    )

    /** A queued upload whose remote target already exists, awaiting the user's decision. */
    data class UploadConflict(
        val source: Uri,
        val displayName: String,
        val remoteDirectory: String,
        val remotePath: String,
    )

    enum class ConflictResolution { OVERWRITE, RENAME, SKIP, CANCEL }

    private val _browser = MutableStateFlow(BrowserState())
    val browser: StateFlow<BrowserState> = _browser.asStateFlow()

    val queue = TransferQueue()

    private val _uploadConflict = MutableStateFlow<UploadConflict?>(null)
    val uploadConflict: StateFlow<UploadConflict?> = _uploadConflict.asStateFlow()

    private var client: SftpClient? = null
    private var pumpJob: Job? = null

    /** Cancellation flags keyed by transfer id, read by the copy loops. */
    private val cancelled = mutableSetOf<String>()

    /**
     * True once [openHome] has been called for this controller's lifetime. Lets the UI
     * layer avoid re-navigating to the home directory — and losing the user's current
     * browsed path — every time the Files tab is revisited for a still-live session.
     */
    var hasOpened: Boolean = false
        private set

    private suspend fun client(): SftpClient = withContext(Dispatchers.IO) {
        client?.let { return@withContext it }
        val opened = session.openSftp() ?: throw IllegalStateException("session is not connected")
        client = opened
        opened
    }

    fun openHome() {
        hasOpened = true
        scope.launch {
            val start = runCatching { withContext(Dispatchers.IO) { client().home() } }
                .getOrDefault(RemotePath.ROOT)
            navigate(start)
        }
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

    /** Creates [name] as a new subdirectory of the current directory, then refreshes. */
    fun createDirectory(name: String) = scope.launch {
        val path = RemotePath.join(_browser.value.path, RemotePath.sanitizeDownloadName(name))
        val result = runCatching { withContext(Dispatchers.IO) { client().makeDirectory(path) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** Renames [entry] to [newName] within its current directory, then refreshes. */
    fun rename(entry: RemoteEntry, newName: String) = scope.launch {
        val target = RemotePath.join(RemotePath.parent(entry.path), RemotePath.sanitizeDownloadName(newName))
        val result = runCatching { withContext(Dispatchers.IO) { client().rename(entry.path, target) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** Deletes [entry] (file or directory) from the server, then refreshes. */
    fun delete(entry: RemoteEntry) = scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) { client().delete(entry.path, entry.isDirectory) }
        }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /**
     * Deletes every entry in [entries] (files or directories), then refreshes once rather
     * than once per item. Best-effort: one failure doesn't stop the rest from being
     * attempted, and only the first failure is surfaced, since the refreshed listing
     * itself shows exactly what actually got removed.
     */
    fun deleteAll(entries: List<RemoteEntry>) = scope.launch {
        val failures = mutableListOf<Throwable>()
        withContext(Dispatchers.IO) {
            val sftp = client()
            entries.forEach { entry ->
                runCatching { sftp.delete(entry.path, entry.isDirectory) }
                    .onFailure { failures += it }
            }
        }
        refresh()
        failures.firstOrNull()?.let { showBrowserError(it) }
    }

    /** Directory-only listing for the "move/copy to" folder picker; doesn't touch [browser]. */
    suspend fun listDirectories(path: String): List<RemoteEntry> =
        runCatching { withContext(Dispatchers.IO) { client().list(path).filter { it.isDirectory } } }
            .getOrDefault(emptyList())

    /** Moves [entry] into [destinationDirectory], keeping its filename, then refreshes. */
    fun moveTo(entry: RemoteEntry, destinationDirectory: String) = scope.launch {
        val target = RemotePath.join(destinationDirectory, RemotePath.name(entry.path))
        val result = runCatching { withContext(Dispatchers.IO) { client().rename(entry.path, target) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /**
     * Copies [entry] into [destinationDirectory]. SFTP has no server-side copy, so this
     * streams the file through a private temp file (download, then upload) rather than
     * transferring it through the device twice over the network. Files only — copying a
     * directory would mean walking and copying every descendant, out of scope here.
     */
    fun copyTo(entry: RemoteEntry, destinationDirectory: String) = scope.launch {
        if (entry.isDirectory) return@launch
        val target = RemotePath.join(destinationDirectory, RemotePath.name(entry.path))
        val temp = File(cacheDir, "sftp-copy-${UUID.randomUUID()}")
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val sftp = client()
                temp.outputStream().use { sink -> sftp.download(entry.path, sink, resumeFrom = 0L) {} }
                temp.inputStream().use { source -> sftp.upload(source, target, resumeFrom = 0L) {} }
            }
        }
        temp.delete()
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** The target of a symlink, for the properties panel; null for anything else. */
    suspend fun symlinkTarget(path: String): String? =
        runCatching { withContext(Dispatchers.IO) { client().readlink(path) } }.getOrNull()

    /** Changes POSIX mode bits on a remote file/directory, then refreshes. */
    fun chmod(entry: RemoteEntry, mode: Int) = scope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { client().chmod(entry.path, mode) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /**
     * Recursive chmod: applies [mode] to all files under a directory.
     * Shows consequences before action — matches the app's security principle.
     */
    fun chmodRecursive(path: String, mode: Int) = scope.launch {
        val failures = mutableListOf<Throwable>()
        withContext(Dispatchers.IO) {
            val sftp = client()
            sftp.chmod(path, mode)
            recursiveChmod(sftp, path, mode, failures)
        }
        refresh()
        failures.firstOrNull()?.let { showBrowserError(it) }
    }

    private fun recursiveChmod(sftp: SftpClient, path: String, mode: Int, failures: MutableList<Throwable>) {
        val entries = runCatching { sftp.list(path) }.getOrDefault(emptyList())
        for (entry in entries) {
            if (entry.isDirectory) {
                runCatching { sftp.chmod(entry.path, mode) }.onFailure { failures += it }
                recursiveChmod(sftp, entry.path, mode, failures)
            } else {
                runCatching { sftp.chmod(entry.path, mode) }.onFailure { failures += it }
            }
        }
    }

    /** Surfaces a failed browser-adjacent action (create/rename/delete/chmod) the same way a
     *  failed listing does: keep the current entries on screen, show an error banner. */
    private fun showBrowserError(t: Throwable) {
        _browser.value = _browser.value.copy(errorKind = SftpClient.classify(t))
    }

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

    /**
     * Checks whether [displayName] would collide with an existing remote file before
     * queuing anything; a real conflict is surfaced via [uploadConflict] for the UI to
     * resolve rather than silently overwriting.
     */
    fun enqueueUpload(source: Uri, displayName: String, remoteDirectory: String) {
        scope.launch {
            val remotePath = RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(displayName))
            val collides = runCatching {
                withContext(Dispatchers.IO) { client().exists(remotePath) }
            }.getOrDefault(false)
            if (collides) {
                _uploadConflict.value = UploadConflict(source, displayName, remoteDirectory, remotePath)
            } else {
                enqueueUploadNow(source, displayName, remotePath)
            }
        }
    }

    private fun enqueueUploadNow(source: Uri, displayName: String, remotePath: String) {
        queue.enqueue(
            Transfer(
                id = UUID.randomUUID().toString(),
                direction = TransferDirection.UPLOAD,
                remotePath = remotePath,
                localUri = source.toString(),
                displayName = displayName,
            ),
        )
        pump()
    }

    /** Resolves a pending [uploadConflict]; a no-op if there isn't one. */
    fun resolveConflict(resolution: ConflictResolution) {
        val conflict = _uploadConflict.value ?: return
        _uploadConflict.value = null
        when (resolution) {
            ConflictResolution.OVERWRITE ->
                enqueueUploadNow(conflict.source, conflict.displayName, conflict.remotePath)
            ConflictResolution.RENAME -> scope.launch {
                val renamed = nonCollidingName(conflict.remoteDirectory, conflict.displayName)
                val renamedPath = RemotePath.join(conflict.remoteDirectory, RemotePath.sanitizeDownloadName(renamed))
                enqueueUploadNow(conflict.source, renamed, renamedPath)
            }
            ConflictResolution.SKIP, ConflictResolution.CANCEL -> Unit
        }
    }

    /**
     * `file.txt` -> `file (1).txt` -> `file (2).txt`, probing the server for each
     * candidate until one is free. Gives up after [MAX_RENAME_ATTEMPTS] probes and falls
     * back to a timestamp suffix, so a directory with hundreds of colliding names can't
     * turn this into an unbounded loop.
     */
    private suspend fun nonCollidingName(remoteDirectory: String, displayName: String): String {
        val sanitized = RemotePath.sanitizeDownloadName(displayName)
        for (n in 1..MAX_RENAME_ATTEMPTS) {
            val candidate = RemotePath.withCollisionSuffix(sanitized, n)
            val taken = withContext(Dispatchers.IO) { client().exists(RemotePath.join(remoteDirectory, candidate)) }
            if (!taken) return candidate
        }
        val (stem, ext) = RemotePath.splitExtension(sanitized)
        return if (ext.isEmpty()) "$stem-${System.currentTimeMillis()}" else "$stem-${System.currentTimeMillis()}.$ext"
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
            // Resolve the client here rather than reaching for a field that is only
            // populated once a listing has succeeded: a transfer queued before the first
            // successful navigate would otherwise dereference null.
            val sftp = client()
            withContext(Dispatchers.IO) {
                when (transfer.direction) {
                    TransferDirection.DOWNLOAD -> download(sftp, transfer)
                    TransferDirection.UPLOAD -> upload(sftp, transfer)
                }
            }
        }
        when {
            transfer.id in cancelled -> {
                // pause() and cancel() both add to `cancelled` — they're the same
                // interrupt mechanism, so the queue's own final state is what actually
                // distinguishes them. Only a genuinely CANCELLED download's staging file
                // is dead weight; a PAUSED one is exactly what a later resume reuses.
                val finalState = queue.transfers.value.firstOrNull { it.id == transfer.id }?.state
                if (transfer.direction == TransferDirection.DOWNLOAD && finalState == TransferState.CANCELLED) {
                    stagingFile(transfer).delete()
                }
            }
            result.isSuccess -> queue.markCompleted(transfer.id)
            else -> queue.fail(transfer.id, SftpClient.classify(result.exceptionOrNull()!!))
        }
    }

    private fun stagingFile(transfer: Transfer): File = File(cacheDir, "sftp-download-${transfer.id}")

    private fun download(sftp: SftpClient, transfer: Transfer) {
        val staging = stagingFile(transfer)
        // Pre-resume consistency check: trust transfer.transferredBytes only if the
        // staging file on disk actually holds that many bytes already. A mismatch (no
        // staging file, a shorter one than expected) means the recorded offset can't be
        // trusted, so restart this transfer from zero rather than risk corrupting the
        // resumed copy.
        val actualStagedBytes = if (staging.exists()) staging.length() else 0L
        val resumeFrom = if (canTrustResume(transfer.transferredBytes, actualStagedBytes)) {
            transfer.transferredBytes
        } else {
            0L
        }
        if (resumeFrom == 0L) {
            if (transfer.transferredBytes != 0L) queue.resetProgress(transfer.id)
            staging.delete()
        }

        FileOutputStream(staging, resumeFrom > 0L).use { sink ->
            sftp.download(transfer.remotePath, sink, resumeFrom = resumeFrom) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }

        // Success: copy the completed staging file to the user's chosen SAF destination,
        // then clean up. Cleanup only happens here and on a genuine cancel (see
        // runTransfer) — a paused or failed transfer keeps its staging file so a later
        // resume/retry can reuse the bytes already on disk.
        val uri = Uri.parse(transfer.localUri)
        val sink = contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("cannot open destination")
        sink.use { out -> staging.inputStream().use { it.copyTo(out) } }
        staging.delete()
    }

    private fun upload(sftp: SftpClient, transfer: Transfer) {
        val uri = Uri.parse(transfer.localUri)
        val source = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open source")
        source.use { input ->
            // Pre-resume consistency check: trust transfer.transferredBytes only if the
            // remote file's current size still matches it. A mismatch (the file changed
            // since the last attempt, or was never actually started) means the recorded
            // offset can't be trusted, so restart from zero.
            val remoteSize = sftp.size(transfer.remotePath)
            val resumeFrom = if (canTrustResume(transfer.transferredBytes, remoteSize)) {
                transfer.transferredBytes
            } else {
                0L
            }
            if (resumeFrom == 0L && transfer.transferredBytes != 0L) queue.resetProgress(transfer.id)

            skipFully(input, resumeFrom)
            sftp.upload(input, transfer.remotePath, resumeFrom = resumeFrom) { total ->
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

    private companion object {
        const val MAX_RENAME_ATTEMPTS = 500
    }
}
