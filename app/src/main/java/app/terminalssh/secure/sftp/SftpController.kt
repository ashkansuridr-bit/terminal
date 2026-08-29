package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.net.Uri
import app.terminalssh.secure.ssh.SshSession
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    /**
     * Bytes per second ceiling, re-read per transfer so a settings change takes effect on
     * the next file rather than needing a restart. 0 means unlimited.
     */
    private val rateLimitBytesPerSecond: () -> Long = { 0L },
    /**
     * Whether the queue may start work right now. False holds everything QUEUED — used by
     * the Wi-Fi-only setting, so a large upload cannot quietly spend a mobile data plan.
     * Queued transfers resume on their own once this goes true again.
     */
    private val mayStartTransfers: () -> Boolean = { true },
) {
    data class BrowserState(
        val path: String = RemotePath.ROOT,
        /** Already sorted and filtered for display; [rawEntries] is what the server sent. */
        val entries: List<RemoteEntry> = emptyList(),
        val loading: Boolean = false,
        val errorKind: TransferErrorKind? = null,
        val rawEntries: List<RemoteEntry> = emptyList(),
        val sortMode: EntrySort.Mode = EntrySort.Mode.NAME,
        val sortDescending: Boolean = false,
        val showHidden: Boolean = false,
    ) {
        /** How many entries the hidden-files toggle is currently keeping out of sight. */
        val hiddenCount: Int get() = if (showHidden) 0 else rawEntries.count { EntrySort.isHidden(it) }
    }

    /** Re-sorts and re-filters what is already loaded. No network call. */
    private fun BrowserState.withView(
        mode: EntrySort.Mode = sortMode,
        descending: Boolean = sortDescending,
        hidden: Boolean = showHidden,
    ): BrowserState = copy(
        entries = EntrySort.apply(rawEntries, mode, descending, hidden),
        sortMode = mode,
        sortDescending = descending,
        showHidden = hidden,
    )

    /** Changes the order; re-selecting the same column flips direction. */
    fun setSortMode(mode: EntrySort.Mode) {
        _browser.value = _browser.value.let {
            it.withView(mode = mode, descending = if (it.sortMode == mode) !it.sortDescending else false)
        }
    }

    fun setShowHidden(show: Boolean) {
        _browser.value = _browser.value.withView(hidden = show)
    }

    fun toggleShowHidden() = setShowHidden(!_browser.value.showHidden)

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

    /** Persists pending transfers across process death. Stored in cacheDir for simplicity. */
    private val persistFile = java.io.File(cacheDir, "transfer_queue.json")
    val queue = TransferQueue.fromPersisted(persistFile)

    private val _uploadConflict = MutableStateFlow<UploadConflict?>(null)
    val uploadConflict: StateFlow<UploadConflict?> = _uploadConflict.asStateFlow()

    // Written under `pumpLock`-independent monitor `this`; @Volatile so the close path
    // below (a different thread) cannot observe a stale reference and leak the channel.
    @Volatile
    private var client: SftpClient? = null
    private val pumpLock = Any()
    private var pumpJob: Job? = null

    /** Auto-persist queue whenever it changes so pending transfers survive process death. */
    private val persistJob = scope.launch {
        queue.transfers.collect {
            withContext(Dispatchers.IO) {
                if (it.any { t -> !t.state.isTerminal }) {
                    queue.persist(persistFile)
                } else {
                    queue.clearPersisted(persistFile)
                }
            }
        }
    }

    /** Persist transfer history to disk. */
    private val historyFile = java.io.File(cacheDir, "transfer_history.json")

    init {
        queue.loadHistory(historyFile)
        scope.launch {
            queue.history.collect {
                withContext(Dispatchers.IO) {
                    queue.persistHistory(historyFile)
                }
            }
        }
    }

    /**
     * Per-transfer SftpClient instances for parallel transfers. Each concurrent
     * transfer gets its own ChannelSftp channel so they don't block each other.
     */
    private val transferClients = ConcurrentHashMap<String, SftpClient>()

    /** Thread-safe cancellation flags keyed by transfer id. */
    private val cancelled = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * True once [openHome] has been called for this controller's lifetime. Lets the UI
     * layer avoid re-navigating to the home directory — and losing the user's current
     * browsed path — every time the Files tab is revisited for a still-live session.
     */
    var hasOpened: Boolean = false
        private set

    private suspend fun client(): SftpClient = withContext(Dispatchers.IO) {
        synchronized(this@SftpController) {
            client?.let { return@withContext it }
            val opened = session.openSftp() ?: throw IllegalStateException("session is not connected")
            client = opened
            opened
        }
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
                    // Carry the user's sort and hidden-files choice across navigation;
                    // resetting them on every directory change would make both useless.
                    val previous = _browser.value
                    BrowserState(
                        path = RemotePath.normalize(path),
                        loading = false,
                        rawEntries = entries,
                        sortMode = previous.sortMode,
                        sortDescending = previous.sortDescending,
                        showHidden = previous.showHidden,
                    ).let { it.copy(entries = EntrySort.apply(entries, it.sortMode, it.sortDescending, it.showHidden)) }
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

    /**
     * Counts files in a remote directory recursively. Used by the confirmation
     * dialog for recursive folder download (#26).
     */
    suspend fun countRemoteFiles(path: String): Int =
        withContext(Dispatchers.IO) { client().listRecursive(path).size }

    /**
     * Recursively downloads a remote folder: walks the directory tree, queues each
     * file as its own resumable Transfer. The destination tree is mirrored under
     * [destinationTreeUri] using SAF's DocumentsContract.
     */
    fun downloadFolder(remotePath: String, destinationTreeUri: android.net.Uri, displayName: String) = scope.launch {
        val entries = withContext(Dispatchers.IO) { client().listRecursive(remotePath) }
        val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            destinationTreeUri,
            android.provider.DocumentsContract.getTreeDocumentId(destinationTreeUri),
        )
        // Create subdirectories
        val subdirs = entries.map { it.second.substringBeforeLast('/', "") }.filter { it.isNotEmpty() }.toSet()
        val dirMap = mutableMapOf<String, android.net.Uri>()
        dirMap[""] = parentDocUri

        for (subdir in subdirs) {
            val parts = subdir.split("/")
            var currentParent = parentDocUri
            var currentPath = ""
            for (part in parts) {
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                if (currentPath !in dirMap) {
                    val dirUri = runCatching {
                        android.provider.DocumentsContract.createDocument(
                            contentResolver, currentParent,
                            android.provider.DocumentsContract.Document.MIME_TYPE_DIR, part,
                        )
                    }.getOrNull()
                    if (dirUri != null) {
                        dirMap[currentPath] = dirUri
                        currentParent = dirUri
                    }
                } else {
                    currentParent = dirMap[currentPath]!!
                }
            }
        }

        // Queue downloads
        for ((remoteFilePath, relativePath) in entries) {
            val parentDir = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val targetParent = dirMap[parentDir] ?: parentDocUri
            val destination = runCatching {
                android.provider.DocumentsContract.createDocument(
                    contentResolver, targetParent,
                    "application/octet-stream", fileName,
                )
            }.getOrNull() ?: continue
            val entry = RemoteEntry(
                name = fileName,
                path = remoteFilePath,
                isDirectory = false,
                isSymlink = false,
                sizeBytes = withContext(Dispatchers.IO) { client().size(remoteFilePath) },
                modifiedEpochSeconds = 0L,
                permissions = "",
            )
            enqueueDownload(entry, destination)
        }
    }

    /**
     * Counts local files in a directory recursively. Used by the confirmation
     * dialog for recursive folder upload (#27).
     */
    fun countLocalFiles(path: java.io.File): Int = path.walkTopDown().filter { it.isFile }.count()

    /**
     * Recursively uploads a local folder to the remote server: mirrors the
     * directory structure via makeDirectory, then queues each file.
     */
    fun uploadFolder(localPath: java.io.File, remotePath: String) = scope.launch {
        val entries = withContext(Dispatchers.IO) { client().listLocalRecursive(localPath) }
        val remoteBase = RemotePath.join(remotePath, localPath.name)

        // Create subdirectories
        val subdirs = entries.map { it.second.substringBeforeLast('/', "") }.filter { it.isNotEmpty() }.toSet()
        withContext(Dispatchers.IO) {
            val sftp = client()
            for (subdir in subdirs) {
                val fullRemoteDir = RemotePath.join(remoteBase, subdir)
                // Create each path segment
                val parts = subdir.split("/")
                var current = remoteBase
                for (part in parts) {
                    current = RemotePath.join(current, part)
                    runCatching { sftp.makeDirectory(current) }
                }
            }
        }

        // Queue uploads
        for ((localFile, relativePath) in entries) {
            val remoteFilePath = RemotePath.join(remoteBase, relativePath)
            val displayName = localFile.name
            val size = localFile.length()
            val entry = RemoteEntry(
                name = displayName,
                path = remoteFilePath,
                isDirectory = false,
                isSymlink = false,
                sizeBytes = size,
                modifiedEpochSeconds = localFile.lastModified() / 1000,
                permissions = "",
            )
            // Check for conflict
            val exists = withContext(Dispatchers.IO) { client().exists(remoteFilePath) }
            if (exists) {
                val renamed = nonCollidingName(RemotePath.parent(remoteFilePath), displayName)
                val renamedPath = RemotePath.join(RemotePath.parent(remoteFilePath), renamed)
                enqueueUploadNow(android.net.Uri.fromFile(localFile), renamed, renamedPath)
            } else {
                enqueueUploadNow(android.net.Uri.fromFile(localFile), displayName, remoteFilePath)
            }
        }
    }

    /**
     * Downloads a remote file as raw bytes, refusing anything past [maxBytes].
     *
     * The limit is enforced on the stream, not on the stat size the server reports:
     * stat-then-read is a race, and a server can always send more than it advertised.
     */
    suspend fun downloadFileBytes(
        remotePath: String,
        maxBytes: Long = BoundedImage.MAX_PREVIEW_BYTES,
    ): Result<ByteArray> =
        runCatching {
            withContext(Dispatchers.IO) {
                val sftp = client()
                val baos = java.io.ByteArrayOutputStream()
                val limited = LimitedOutputStream(baos, maxBytes)
                sftp.download(remotePath, limited, 0L) {}
                baos.toByteArray()
            }
        }

    /** Downloads a remote text file's content (for the in-app editor / preview). */
    suspend fun downloadFileText(remotePath: String, maxBytes: Long = 512_000): Result<String> =
        runCatching { withContext(Dispatchers.IO) { client().downloadText(remotePath, maxBytes) } }

    /**
     * Records the mtime of a file when the user starts editing it. Used by
     * [uploadFileText] to detect concurrent edits (#37).
     */
    private val editFingerprints = ConcurrentHashMap<String, EditFingerprint>()

    /** Downloads text AND records the mtime for concurrent-edit detection. */
    suspend fun downloadFileTextForEdit(remotePath: String, maxBytes: Long = 512_000): Result<Pair<String, Long>> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val sftp = client()
                val text = sftp.downloadText(remotePath, maxBytes)
                val mtime = sftp.mtime(remotePath)
                // Hash what we actually opened. mtime and size are only the fast path;
                // this is what catches an edit that preserved both.
                editFingerprints[remotePath] = EditFingerprint(
                    mtimeEpochSeconds = mtime,
                    sizeBytes = sftp.size(remotePath),
                    sha256 = EditConflict.sha256(text),
                )
                text to mtime
            }
        }
    }

    /** Returns true when the remote file changed since this editor loaded it. */
    suspend fun checkFileTextConflict(remotePath: String, maxBytes: Long = 512_000): Result<Boolean> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val saved = editFingerprints[remotePath]
                    ?: error("no fingerprint recorded for $remotePath")
                val sftp = client()
                val currentMtime = sftp.mtime(remotePath)
                val currentSize = sftp.size(remotePath)

                // Only pay for a re-read when stat cannot already prove a change. When it
                // can, we are done; when it cannot, the hash is the only thing that
                // separates "identical" from "same length, different bytes".
                val currentSha = if (EditConflict.statProvesChange(saved, currentMtime, currentSize)) {
                    null
                } else {
                    runCatching { EditConflict.sha256(sftp.downloadText(remotePath, maxBytes)) }.getOrNull()
                }

                when (EditConflict.verdict(saved, currentMtime, currentSize, currentSha)) {
                    ConflictVerdict.UNCHANGED -> false
                    ConflictVerdict.CHANGED -> true
                    // Unreadable remote is not permission to overwrite it.
                    ConflictVerdict.UNKNOWN -> error("cannot verify remote state for $remotePath")
                }
            }
        }
    }

    /** Uploads text content back to a remote path (after editing). */
    fun uploadFileText(remotePath: String, text: String) = scope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { client().uploadText(remotePath, text) } }
        if (result.isSuccess) {
            editFingerprints.remove(remotePath)
            refresh()
        } else {
            showBrowserError(result.exceptionOrNull()!!)
        }
    }

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

    /**
     * Compresses the selected remote files into a .zip on the server side:
     * downloads them to a temp dir, zips them, and uploads the result.
     * Returns the remote path of the created zip, or throws on failure.
     */
    suspend fun compressSelection(entries: List<RemoteEntry>, remoteDestDir: String): String {
        return withContext(Dispatchers.IO) {
            val sftp = client()
            val tempDir = java.io.File(cacheDir, "compress_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                // Download all files to temp dir
                for (entry in entries) {
                    if (entry.isDirectory) continue
                    val localFile = java.io.File(tempDir, entry.name)
                    java.io.FileOutputStream(localFile).use { out ->
                        sftp.download(entry.path, out, 0L) {}
                    }
                }
                // Create zip from temp files
                val zipName = "archive_${System.currentTimeMillis()}.zip"
                val zipFile = java.io.File(cacheDir, zipName)
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                    tempDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                        java.util.zip.ZipEntry(file.name).also { zos.putNextEntry(it) }
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                // Upload zip to remote destination
                val remotePath = RemotePath.join(remoteDestDir, zipName)
                java.io.FileInputStream(zipFile).use { inp ->
                    sftp.upload(inp, remotePath, 0L) {}
                }
                zipFile.delete()
                remotePath
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    // ---- one-way sync (#28/#29) ----

    // ---- bookmarked folders (#31) ----
    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    fun toggleBookmark(path: String) {
        val current = _bookmarks.value
        _bookmarks.value = if (path in current) current - path else current + path
    }

    fun isBookmarked(path: String): Boolean = path in _bookmarks.value

    // ---- sync presets (#48) ----
    data class SyncPreset(
        val id: String,
        val name: String,
        val localDir: String,
        val remoteDir: String,
        val deleteRemote: Boolean = false,
    )

    private val _syncPresets = MutableStateFlow<List<SyncPreset>>(emptyList())
    val syncPresets: StateFlow<List<SyncPreset>> = _syncPresets.asStateFlow()

    fun saveSyncPreset(preset: SyncPreset) {
        _syncPresets.value = _syncPresets.value.filter { it.id != preset.id } + preset
    }

    fun deleteSyncPreset(id: String) {
        _syncPresets.value = _syncPresets.value.filter { it.id != id }
    }

    // ---- folder size (#45) ----
    private val _folderSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val folderSizes: StateFlow<Map<String, Long>> = _folderSizes.asStateFlow()

    fun computeFolderSize(path: String) {
        scope.launch {
            val size = withContext(Dispatchers.IO) {
                runCatching { client().recursiveSize(path) }.getOrDefault(0L)
            }
            _folderSizes.value = _folderSizes.value + (path to size)
        }
    }

    /** Describes one change in a sync plan. */
    data class SyncAction(
        val relativePath: String,
        val kind: Kind,
        val localSize: Long = 0L,
        val remoteSize: Long = 0L,
    ) {
        enum class Kind { UPLOAD, DELETE_REMOTE, SKIP_IDENTICAL }
    }

    /**
     * Computes a one-way sync plan: local → remote.
     * Compares files by relative path and size (fast heuristic; mtime not available via SFTP universally).
     */
    suspend fun computeSyncPlan(localDir: java.io.File, remoteDir: String, deleteRemote: Boolean = false): List<SyncAction> {
        return withContext(Dispatchers.IO) {
            val sftp = client()
            // Local files
            val localFiles = localDir.walkTopDown().filter { it.isFile }.map { file ->
                file.relativeTo(localDir).path to file.length()
            }.toMap()
            // Remote files
            val remoteFiles = sftp.listRecursive(remoteDir).map { (remotePath, relative) ->
                val size = runCatching { sftp.size(remotePath) }.getOrDefault(0L)
                relative to size
            }.toMap()

            val actions = mutableListOf<SyncAction>()

            // Files to upload: new or different size
            for ((relPath, localSize) in localFiles) {
                val remoteSize = remoteFiles[relPath]
                if (remoteSize == null) {
                    actions.add(SyncAction(relPath, SyncAction.Kind.UPLOAD, localSize, 0L))
                } else if (remoteSize != localSize) {
                    actions.add(SyncAction(relPath, SyncAction.Kind.UPLOAD, localSize, remoteSize))
                } else {
                    actions.add(SyncAction(relPath, SyncAction.Kind.SKIP_IDENTICAL, localSize, remoteSize))
                }
            }

            // Files to delete on remote (remote has files not in local)
            if (deleteRemote) {
                for ((relPath, remoteSize) in remoteFiles) {
                    if (relPath !in localFiles) {
                        actions.add(SyncAction(relPath, SyncAction.Kind.DELETE_REMOTE, 0L, remoteSize))
                    }
                }
            }

            actions.sortedBy { it.relativePath }
        }
    }

    /**
     * Executes a sync plan: uploads new/changed files, deletes remote-only files.
     */
    suspend fun executeSyncPlan(localDir: java.io.File, remoteDir: String, actions: List<SyncAction>) {
        withContext(Dispatchers.IO) {
            val sftp = client()
            for (action in actions) {
                when (action.kind) {
                    SyncAction.Kind.UPLOAD -> {
                        val localFile = java.io.File(localDir, action.relativePath)
                        if (!localFile.exists()) continue
                        val remotePath = RemotePath.join(remoteDir, action.relativePath)
                        // Ensure parent directory exists
                        val parentDir = RemotePath.parent(remotePath)
                        runCatching { sftp.makeDirectory(parentDir) }
                        java.io.FileInputStream(localFile).use { inp ->
                            sftp.upload(inp, remotePath, 0L) {}
                        }
                    }
                    SyncAction.Kind.DELETE_REMOTE -> {
                        val remotePath = RemotePath.join(remoteDir, action.relativePath)
                        runCatching { sftp.delete(remotePath, false) }
                    }
                    SyncAction.Kind.SKIP_IDENTICAL -> { /* no-op */ }
                }
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
    /**
     * A decision the user asked to apply to every remaining collision in this batch.
     *
     * Queueing a hundred files used to mean answering the same dialog a hundred times,
     * and the answer is almost always the same for the whole batch. Cleared by
     * [clearConflictPolicy] so a later, unrelated upload asks again rather than silently
     * inheriting a choice made minutes ago.
     */
    private val standingConflictPolicy = java.util.concurrent.atomic.AtomicReference<ConflictResolution?>(null)

    fun setConflictPolicy(resolution: ConflictResolution?) = standingConflictPolicy.set(resolution)

    fun clearConflictPolicy() = standingConflictPolicy.set(null)

    fun enqueueUpload(source: Uri, displayName: String, remoteDirectory: String) {
        scope.launch {
            val remotePath = RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(displayName))
            val collides = runCatching {
                withContext(Dispatchers.IO) { client().exists(remotePath) }
            }.getOrDefault(false)
            if (!collides) {
                enqueueUploadNow(source, displayName, remotePath)
                return@launch
            }
            when (standingConflictPolicy.get()) {
                null, ConflictResolution.CANCEL ->
                    _uploadConflict.value = UploadConflict(source, displayName, remoteDirectory, remotePath)
                ConflictResolution.OVERWRITE ->
                    enqueueUploadNow(source, displayName, remotePath)
                ConflictResolution.SKIP -> Unit
                ConflictResolution.RENAME -> {
                    val renamed = nonCollidingName(remoteDirectory, displayName)
                    enqueueUploadNow(
                        source,
                        renamed,
                        RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(renamed)),
                    )
                }
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
    /**
     * @param applyToAll remembers [resolution] for the rest of this batch, so a large
     *   queue is answered once instead of per file.
     */
    fun resolveConflict(resolution: ConflictResolution, applyToAll: Boolean = false) {
        val conflict = _uploadConflict.value ?: return
        _uploadConflict.value = null
        if (applyToAll) standingConflictPolicy.set(resolution)
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

    /**
     * Starts the next transfer if the queue allows one; re-entrant and cheap.
     * Adaptive concurrency: launches up to maxConcurrent transfers in parallel,
     * each on its own SFTP channel for true parallel I/O.
     */
    private fun pump() {
        // Guarded because pump() is called from UI callbacks (enqueue/retry/resume) that
        // are not confined to one thread. Two callers racing the isActive check would each
        // launch a pump loop, and both could take the same transfer from nextToStart()
        // before either marked it RUNNING — two uploads writing one remote path.
        synchronized(pumpLock) {
            if (pumpJob?.isActive == true) return
            pumpJob = scope.launch {
                while (isActive) {
                    queue.adaptConcurrency()
                    // Holding rather than failing: the user asked to wait for Wi-Fi, not
                    // to lose the queue.
                    val next = if (mayStartTransfers()) queue.nextToStart() else null
                    if (next == null) {
                        // Nothing eligible right now. Re-check once after a beat instead of
                        // recursing into pump(): this job is still active here, so pump()'s
                        // own guard returned immediately and the queue stalled until some
                        // external call happened to restart it. Retry backoffs expiring and
                        // adaptConcurrency() raising the limit both land in this window.
                        delay(PUMP_IDLE_RECHECK_MS)
                        if (queue.nextToStart() == null) break else continue
                    }
                    queue.markRunning(next.id)
                    cancelled -= next.id
                    val transfer = queue.transfers.value.first { it.id == next.id }
                    // Launch each transfer as a separate coroutine for parallel execution
                    launch {
                        runTransfer(transfer)
                        transferClients.remove(transfer.id)?.close()
                    }
                }
            }
        }
    }

    private suspend fun runTransfer(transfer: Transfer) {
        // Each concurrent transfer gets its own SFTP channel for true parallel I/O.
        val transferClient = runCatching {
            withContext(Dispatchers.IO) { session.openSftp() }
        }.getOrNull()
        if (transferClient != null) {
            transferClients[transfer.id] = transferClient
        }

        val result = runCatching {
            val sftp = transferClient ?: client()
            withContext(Dispatchers.IO) {
                when (transfer.direction) {
                    TransferDirection.DOWNLOAD -> download(sftp, transfer)
                    TransferDirection.UPLOAD -> upload(sftp, transfer)
                }
            }
        }
        // Verify before the channel is closed. Doing it after left verifyIntegrity
        // holding a closed client, so every check silently degraded to UNVERIFIED and
        // the feature never actually ran.
        val integrity = if (result.isSuccess && transfer.id !in cancelled) {
            runCatching {
                withContext(Dispatchers.IO) { verifyIntegrity(transferClient, transfer) }
            }.getOrDefault(IntegrityResult.UNVERIFIED)
        } else {
            IntegrityResult.UNVERIFIED
        }

        transferClient?.close()
        transferClients.remove(transfer.id)
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
            result.isSuccess -> {
                if (integrity == IntegrityResult.MISMATCH) {
                    queue.fail(transfer.id, TransferErrorKind.INTEGRITY_MISMATCH)
                } else {
                    queue.markCompleted(transfer.id)
                }
            }
            else -> queue.fail(transfer.id, SftpClient.classify(result.exceptionOrNull()!!))
        }
    }

    // ---- Phase 3: second pane (24) ----

    private val _secondPane = MutableStateFlow<BrowserState?>(null)

    /**
     * An optional second directory view on the same session.
     *
     * Moving a file between two distant paths meant navigating there, remembering, coming
     * back. Two panes make it one drag. Null means the pane is closed, which is also the
     * signal the UI uses to fall back to the single-pane layout on a phone.
     */
    val secondPane: StateFlow<BrowserState?> = _secondPane.asStateFlow()

    fun openSecondPane(path: String = _browser.value.path) {
        _secondPane.value = BrowserState(path = RemotePath.normalize(path), loading = true)
        navigateSecondPane(path)
    }

    fun closeSecondPane() {
        _secondPane.value = null
    }

    fun navigateSecondPane(path: String) {
        scope.launch {
            val current = _secondPane.value ?: return@launch
            _secondPane.value = current.copy(loading = true, errorKind = null)
            val result = runCatching { withContext(Dispatchers.IO) { client().list(path) } }
            _secondPane.value = result.fold(
                onSuccess = { entries ->
                    val previous = _secondPane.value ?: current
                    BrowserState(
                        path = RemotePath.normalize(path),
                        loading = false,
                        rawEntries = entries,
                        sortMode = previous.sortMode,
                        sortDescending = previous.sortDescending,
                        showHidden = previous.showHidden,
                    ).let { it.copy(entries = EntrySort.apply(entries, it.sortMode, it.sortDescending, it.showHidden)) }
                },
                onFailure = { failure ->
                    (_secondPane.value ?: current).copy(loading = false, errorKind = SftpClient.classify(failure))
                },
            )
        }
    }

    /** Copies from whichever pane holds [entry] into the other one, on this session. */
    suspend fun copyBetweenPanes(entry: RemoteEntry, toSecondPane: Boolean): Result<String> = runCatching {
        val destination = if (toSecondPane) {
            _secondPane.value?.path ?: error("the second pane is not open")
        } else {
            _browser.value.path
        }
        withContext(Dispatchers.IO) {
            val sftp = client()
            val name = nonCollidingName(destination, entry.name)
            val target = RemotePath.join(destination, RemotePath.sanitizeDownloadName(name))
            // cp -a on the server: never round-trips the bytes through the phone.
            sftp.exec(RemoteOps.duplicateCommand(entry.path, target)) ?: error("the server did not allow copy")
            target
        }
    }.onSuccess {
        refresh()
        _secondPane.value?.let { navigateSecondPane(it.path) }
    }

    // ---- Phase 3: browsing, recovery and previews (22-30) ----

    /**
     * Recoverable delete: moves into the trash instead of unlinking.
     *
     * Falls back to a real delete only when the entry is already inside the trash —
     * emptying it has to actually remove things.
     */
    suspend fun trash(entry: RemoteEntry): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val home = sftp.home()
            if (RemoteTrash.isInTrash(entry.path, home)) {
                sftp.delete(entry.path, entry.isDirectory)
                return@withContext entry.path
            }
            val dir = RemoteTrash.trashDir(home)
            if (!sftp.exists(dir)) sftp.makeDirectory(dir)
            val target = RemoteTrash.trashedPath(home, entry.path, System.currentTimeMillis())
            sftp.rename(entry.path, target)
            target
        }
    }.onSuccess { refresh() }

    /** Everything currently recoverable, newest first. */
    suspend fun listTrash(): Result<List<Pair<RemoteEntry, String>>> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val dir = RemoteTrash.trashDir(sftp.home())
            if (!sftp.exists(dir)) return@withContext emptyList()
            sftp.list(dir)
                .mapNotNull { e -> RemoteTrash.parseTrashedName(e.name)?.let { (stamp, name) -> Triple(e, name, stamp) } }
                .sortedByDescending { it.third }
                .map { it.first to it.second }
        }
    }

    /** Puts a trashed entry back, into [destinationDir] or the current directory. */
    suspend fun restoreFromTrash(entry: RemoteEntry, destinationDir: String? = null): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val original = RemoteTrash.parseTrashedName(entry.name)?.second
                ?: error("not a trashed entry")
            val dir = destinationDir ?: _browser.value.path
            val target = RemotePath.join(dir, nonCollidingName(dir, original))
            sftp.rename(entry.path, target)
            target
        }
    }.onSuccess { refresh() }

    /**
     * First [maxBytes] of a file without downloading the rest (#28).
     *
     * Opening a one-gigabyte log used to mean transferring all of it. This reads a window
     * and decodes it with the file's own charset, so a non-UTF-8 config is readable too.
     */
    suspend fun previewHead(path: String, maxBytes: Long = PREVIEW_WINDOW): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val out = java.io.ByteArrayOutputStream()
            runCatching {
                client().download(path, LimitedOutputStream(out, maxBytes), 0L) {}
            }.exceptionOrNull()?.let { if (it !is TransferTooLargeException) throw it }
            val bytes = out.toByteArray()
            TextEncoding.decode(bytes, TextEncoding.detect(bytes))
        }
    }

    /** Last [maxBytes] of a file, for the end of a long log. */
    suspend fun previewTail(path: String, maxBytes: Long = PREVIEW_WINDOW): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val size = sftp.size(path)
            val from = if (size > maxBytes) size - maxBytes else 0L
            val out = java.io.ByteArrayOutputStream()
            runCatching {
                sftp.download(path, LimitedOutputStream(out, maxBytes), from) {}
            }.exceptionOrNull()?.let { if (it !is TransferTooLargeException) throw it }
            val bytes = out.toByteArray()
            TextEncoding.decode(bytes, TextEncoding.detect(bytes))
        }
    }

    /**
     * Copies straight from another server to this one (#25), without staging the whole
     * file on the phone.
     *
     * A pipe rather than a temp file: a 4 GB database dump moved between two servers
     * should not need 4 GB of free space on a handset, and the phone is only the relay.
     */
    suspend fun copyFromRemote(
        sourceController: SftpController,
        sourceEntry: RemoteEntry,
        destinationDir: String,
    ): Result<String> = runCatching {
        require(!sourceEntry.isDirectory) { "only files can be copied between servers" }
        withContext(Dispatchers.IO) {
            val source = sourceController.client()
            val destination = client()
            val name = nonCollidingName(destinationDir, sourceEntry.name)
            val target = RemotePath.join(destinationDir, RemotePath.sanitizeDownloadName(name))

            val pipeIn = java.io.PipedInputStream(PIPE_BUFFER)
            val pipeOut = java.io.PipedOutputStream(pipeIn)
            val pump = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    pipeOut.use { out -> source.download(sourceEntry.path, out, 0L) {} }
                } catch (_: Exception) {
                    runCatching { pipeOut.close() }
                }
            }
            try {
                pipeIn.use { input -> destination.upload(input, target, 0L) {} }
            } finally {
                pump.cancel()
            }
            target
        }
    }.onSuccess { refresh() }

    /**
     * Downloads [entry] into the share staging directory and returns the local copy, so
     * the UI can hand it to another app (#30). Bounded: a share is a convenience, not a
     * reason to fill the cache with a database dump.
     */
    suspend fun stageForShare(entry: RemoteEntry, cacheRoot: File): Result<File> = runCatching {
        require(!entry.isDirectory) { "a directory cannot be shared as a file" }
        withContext(Dispatchers.IO) {
            val dir = File(cacheRoot, "shared").apply { mkdirs() }
            val target = File(dir, RemotePath.sanitizeDownloadName(entry.name))
            java.io.FileOutputStream(target).use { out ->
                client().download(entry.path, LimitedOutputStream(out, MAX_SHARE_BYTES), 0L) {}
            }
            target
        }
    }

    // ---- Phase 2: server-side operations (13-21) ----

    /**
     * Result of a shell operation. [output] is whatever the command printed; a null
     * result means the server would not run it at all, which callers must surface rather
     * than treat as success — "exec is disabled" and "the command did nothing" look
     * identical otherwise.
     */
    suspend fun runRemote(command: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            client().exec(command) ?: error("the server did not allow this command")
        }
    }

    /** Last lines of a remote file, for the follow view. */
    suspend fun tailFile(path: String, lines: Int = 200): Result<String> =
        runRemote(RemoteOps.tailCommand(path, lines))

    /**
     * Lines appended since [fromOffset], plus the new offset to poll from next time.
     * Returns an empty string when nothing was appended.
     */
    suspend fun tailSince(path: String, fromOffset: Long): Result<Pair<String, Long>> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val size = sftp.size(path)
            // A file that shrank was rotated: start over rather than reading from an
            // offset that now points into the middle of a different file.
            val from = if (size in 0 until fromOffset) 0L else fromOffset
            if (size <= from) return@withContext "" to from
            val text = sftp.exec(RemoteOps.tailSinceCommand(path, from)) ?: error("exec unavailable")
            text to size
        }
    }

    /** Recursive fixed-string search under [dir]. */
    suspend fun searchInFiles(
        dir: String,
        needle: String,
        ignoreCase: Boolean = true,
    ): Result<List<RemoteOps.SearchHit>> = runCatching {
        require(needle.isNotBlank()) { "empty search" }
        withContext(Dispatchers.IO) {
            val out = client().exec(RemoteOps.grepCommand(dir, needle, ignoreCase))
                ?: error("the server did not allow search")
            RemoteOps.parseGrepOutput(out)
        }
    }

    /** Runs [template] against every selected path, stopping at the first failure. */
    suspend fun runOnSelection(template: String, entries: List<RemoteEntry>): Result<String> {
        val command = RemoteOps.buildSelectionCommand(template, entries.map { it.path })
            ?: return Result.failure(IllegalArgumentException("nothing to run"))
        return runRemote(command).onSuccess { refresh() }
    }

    /** Unpacks an archive on the server, next to itself unless [destDir] says otherwise. */
    suspend fun extractArchive(entry: RemoteEntry, destDir: String? = null): Result<String> {
        val target = destDir ?: RemotePath.parentOf(entry.path)
        val command = RemoteOps.extractCommand(entry.path, target)
            ?: return Result.failure(IllegalArgumentException("not a supported archive"))
        return runRemote(command).onSuccess { refresh() }
    }

    fun canExtract(entry: RemoteEntry): Boolean = !entry.isDirectory && RemoteOps.isExtractable(entry.name)

    /** Changes owner, and group when given. */
    suspend fun chown(
        entry: RemoteEntry,
        owner: String,
        group: String? = null,
        recursive: Boolean = false,
    ): Result<String> {
        val command = RemoteOps.chownCommand(entry.path, owner, group, recursive)
            ?: return Result.failure(IllegalArgumentException("invalid user or group name"))
        return runRemote(command).onSuccess { refresh() }
    }

    suspend fun createSymlink(targetPath: String, linkPath: String): Result<String> =
        runRemote(RemoteOps.symlinkCommand(targetPath, linkPath)).onSuccess { refresh() }

    suspend fun createHardLink(targetPath: String, linkPath: String): Result<String> =
        runRemote(RemoteOps.hardLinkCommand(targetPath, linkPath)).onSuccess { refresh() }

    suspend fun createEmptyFile(path: String): Result<String> =
        runRemote(RemoteOps.touchCommand(path)).onSuccess { refresh() }

    suspend fun duplicate(entry: RemoteEntry): Result<String> {
        val copyName = nonCollidingName(RemotePath.parentOf(entry.path), entry.name)
        val destination = RemotePath.join(RemotePath.parentOf(entry.path), copyName)
        return runRemote(RemoteOps.duplicateCommand(entry.path, destination)).onSuccess { refresh() }
    }

    /** Diffs the remote file against [newText] so a save can be reviewed before it lands. */
    suspend fun diffAgainstRemote(
        remotePath: String,
        newText: String,
        maxBytes: Long = 512_000,
    ): Result<TextDiff.Result> = runCatching {
        withContext(Dispatchers.IO) {
            TextDiff.diff(client().downloadText(remotePath, maxBytes), newText)
        }
    }

    /**
     * Applies a rename plan. Stops at the first failure and reports how far it got, so a
     * partially applied batch is visible rather than silently mixed.
     */
    suspend fun applyBatchRename(directory: String, plan: BatchRename.Plan): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            var done = 0
            for (change in plan.applicable) {
                sftp.rename(
                    RemotePath.join(directory, change.from),
                    RemotePath.join(directory, change.to),
                )
                done++
            }
            done
        }
    }.onSuccess { refresh() }

    private fun stagingFile(transfer: Transfer): File = File(cacheDir, "sftp-download-${transfer.id}")

    enum class IntegrityResult { MATCH, MISMATCH, UNVERIFIED }

    /**
     * Compares what the server has against what this device has, using the server's own
     * sha256 and a local hash of the same bytes.
     *
     * [IntegrityResult.UNVERIFIED] is not a failure. Servers that disable `exec`, and
     * files too large to be worth hashing twice, both land here; the transfer stands.
     * Only a hash that actually disagrees fails the transfer, because that is the one
     * case where reporting success would be a lie.
     */
    private fun verifyIntegrity(sftp: SftpClient?, transfer: Transfer): IntegrityResult {
        val client = sftp ?: return IntegrityResult.UNVERIFIED
        val size = transfer.totalBytes
        if (size <= 0L || size > MAX_VERIFY_BYTES) return IntegrityResult.UNVERIFIED

        val remote = client.remoteSha256(transfer.remotePath) ?: return IntegrityResult.UNVERIFIED
        val local = when (transfer.direction) {
            TransferDirection.DOWNLOAD -> {
                val staged = stagingFile(transfer)
                // On the success path the staging file is already copied out and deleted,
                // so a download is verified from the destination the user actually got.
                if (staged.exists()) sha256Of(staged.inputStream())
                else runCatching {
                    contentResolver.openInputStream(Uri.parse(transfer.localUri))?.use { sha256Of(it) }
                }.getOrNull()
            }
            TransferDirection.UPLOAD -> runCatching {
                contentResolver.openInputStream(Uri.parse(transfer.localUri))?.use { sha256Of(it) }
            }.getOrNull()
        } ?: return IntegrityResult.UNVERIFIED

        return if (local.equals(remote, ignoreCase = true)) IntegrityResult.MATCH else IntegrityResult.MISMATCH
    }

    private fun sha256Of(stream: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun download(sftp: SftpClient, transfer: Transfer) {
        val staging = stagingFile(transfer)
        // Pre-resume consistency check: trust transfer.transferredBytes only if the
        // staging file on disk actually holds that many bytes already. A mismatch (no
        // staging file, a shorter one than expected) means the recorded offset can't be
        // trusted, so restart this transfer from zero rather than risk corrupting the
        // resumed copy.
        val actualStagedBytes = if (staging.exists()) staging.length() else 0L
        // The staging file matching the recorded offset only proves the local half is
        // intact; it says nothing about the file those bytes came from. Re-stat the
        // remote too, or a file replaced between attempts gets its tail appended to the
        // old file's head and the user receives a corrupted download reported as success.
        val currentRemoteBytes = runCatching { sftp.size(transfer.remotePath) }.getOrNull()
        val resumeFrom = if (canTrustRemoteForResume(transfer.totalBytes, currentRemoteBytes) &&
            canTrustResume(transfer.transferredBytes, actualStagedBytes)
        ) {
            transfer.transferredBytes
        } else {
            0L
        }
        if (resumeFrom == 0L) {
            if (transfer.transferredBytes != 0L) queue.resetProgress(transfer.id)
            staging.delete()
        }

        // A fresh, large download is worth splitting across channels; a resumed one is
        // not, because the ranges were planned against the original offsets.
        val plan = if (resumeFrom == 0L) MultipartPlan.planFor(transfer.totalBytes) else emptyList()
        if (plan.size > 1 && MultipartPlan.covers(plan, transfer.totalBytes)) {
            downloadMultipart(transfer, staging, plan)
        } else {
            downloadSingleStream(sftp, transfer, staging, resumeFrom)
        }
        deliverStagedDownload(transfer, staging)
    }

    /**
     * Downloads [plan]'s ranges concurrently, each on its own channel, into one
     * pre-sized file.
     *
     * Every part writes at its own absolute offset through its own RandomAccessFile
     * handle, so the parts never contend and a partial failure leaves a file that is
     * simply incomplete rather than interleaved. Any part failing fails the whole
     * transfer: a file assembled from some new ranges and some missing ones is exactly
     * the silent corruption the resume guard exists to prevent.
     */
    private suspend fun downloadMultipart(transfer: Transfer, staging: File, plan: List<ByteRange>) {
        staging.delete()
        java.io.RandomAccessFile(staging, "rw").use { it.setLength(transfer.totalBytes) }

        // One shared counter, advanced by each part's delta. Reporting
        // "completed parts + my own bytes" made every running part report only its own
        // progress, and markProgress takes the max — so a four-way split showed roughly
        // a quarter of the real figure until parts began finishing.
        val done = java.util.concurrent.atomic.AtomicLong(0L)
        coroutineScope {
            plan.map { range ->
                async(Dispatchers.IO) {
                    val channel = session.openSftp() ?: throw IllegalStateException("session is not connected")
                    try {
                        java.io.RandomAccessFile(staging, "rw").use { raf ->
                            raf.seek(range.start)
                            val limiter = RateLimiter(rateLimitBytesPerSecond())
                            val sink = RangeOutputStream(raf, range.length)
                            val throttled = if (limiter.unlimited) sink else ThrottledOutputStream(sink, limiter)
                            var reported = 0L
                            runCatching {
                                channel.download(transfer.remotePath, throttled, resumeFrom = range.start) { _ ->
                                    if (transfer.id in cancelled) throw InterruptedTransfer()
                                    val delta = sink.written - reported
                                    if (delta > 0) {
                                        reported = sink.written
                                        queue.markProgress(transfer.id, done.addAndGet(delta))
                                    }
                                }
                            }.exceptionOrNull()?.let { failure ->
                                // The range filling up is how a range download ends: JSch
                                // has no "read n bytes" call, so the stream stops it.
                                if (failure !is RangeCompleteException) throw failure
                            }
                            if (sink.written != range.length) {
                                throw java.io.IOException(
                                    "part ${range.index} got ${sink.written} of ${range.length} bytes",
                                )
                            }
                            // Settle any bytes the progress callback did not see.
                            val unreported = sink.written - reported
                            if (unreported > 0) queue.markProgress(transfer.id, done.addAndGet(unreported))
                        }
                    } finally {
                        runCatching { channel.close() }
                    }
                }
            }.awaitAll()
        }
    }

    private fun downloadSingleStream(sftp: SftpClient, transfer: Transfer, staging: File, resumeFrom: Long) {
        val limiter = RateLimiter(rateLimitBytesPerSecond())
        FileOutputStream(staging, resumeFrom > 0L).use { rawSink ->
            val sink = if (limiter.unlimited) rawSink else ThrottledOutputStream(rawSink, limiter)
            sftp.download(transfer.remotePath, sink, resumeFrom = resumeFrom) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }
    }

    /**
     * Copies the completed staging file to the user's chosen SAF destination, then cleans
     * up. Cleanup only happens here and on a genuine cancel (see runTransfer) — a paused
     * or failed transfer keeps its staging file so a later resume/retry can reuse the
     * bytes already on disk.
     */
    private fun deliverStagedDownload(transfer: Transfer, staging: File) {
        val uri = Uri.parse(transfer.localUri)
        // A queue restored after process death can hold a URI whose grant is gone. Say so
        // in terms the queue understands instead of letting SecurityException escape.
        if (!SafPermissions.isAccessible(contentResolver, uri)) {
            throw LocalUriUnavailableException(transfer.localUri)
        }
        val sink = contentResolver.openOutputStream(uri, "wt")
            ?: throw LocalUriUnavailableException(transfer.localUri)
        sink.use { out -> staging.inputStream().use { it.copyTo(out) } }
        staging.delete()
    }

    private fun upload(sftp: SftpClient, transfer: Transfer) {
        val uri = Uri.parse(transfer.localUri)
        if (!SafPermissions.isAccessible(contentResolver, uri)) {
            throw LocalUriUnavailableException(transfer.localUri)
        }
        val source = contentResolver.openInputStream(uri)
            ?: throw LocalUriUnavailableException(transfer.localUri)
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

            // Ask the server whether this fits before sending gigabytes at a full disk.
            // A server that will not answer df is not a reason to refuse the upload.
            val outstanding = (transfer.totalBytes - resumeFrom).coerceAtLeast(0L)
            val free = sftp.freeSpaceBytes(RemotePath.parentOf(transfer.remotePath))
            if (free != null && transfer.totalBytes > 0 &&
                !RemoteCommands.fitsInFreeSpace(outstanding, free)
            ) {
                throw NotEnoughRemoteSpaceException(outstanding, free)
            }

            skipFully(input, resumeFrom)
            val limiter = RateLimiter(rateLimitBytesPerSecond())
            val throttled = if (limiter.unlimited) input else ThrottledInputStream(input, limiter)
            sftp.upload(throttled, transfer.remotePath, resumeFrom = resumeFrom) { total ->
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
        persistJob.cancel()
        queue.persist(persistFile)
        transferClients.values.forEach { runCatching { it.close() } }
        transferClients.clear()
        // Same monitor client() opens under: without it, close() can null the field while
        // client() is mid-open, leaking that channel, or hand back a channel just closed.
        synchronized(this) {
            runCatching { client?.close() }
            client = null
        }
    }

    private companion object {
        const val MAX_RENAME_ATTEMPTS = 500

        /** How much of a large file a preview reads before stopping. */
        const val PREVIEW_WINDOW = 256L * 1024

        /** Relay buffer for a server-to-server copy; the phone never holds the whole file. */
        const val PIPE_BUFFER = 1 shl 16

        /** Ceiling for a file staged into the share cache. */
        const val MAX_SHARE_BYTES = 256L * 1024 * 1024

        /** Past this, hashing the file twice costs more than the assurance is worth. */
        const val MAX_VERIFY_BYTES = 512L * 1024 * 1024
        const val PUMP_IDLE_RECHECK_MS = 50L
    }
}
