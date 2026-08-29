package app.terminalssh.secure.sftp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Stages a remote file so another app can open or receive it (#30).
 *
 * Android cannot hand a foreign app an SFTP path, so sharing means a real local copy plus
 * a one-shot read grant. The copy lives in its own cache subdirectory, which is the only
 * thing the FileProvider is allowed to see — the transfer staging files and the queue
 * file sit elsewhere in the cache and must stay unreachable from a share grant.
 */
object ShareStaging {

    private const val SUBDIR = "shared"

    fun stagingDir(context: Context): File =
        File(context.cacheDir, SUBDIR).apply { mkdirs() }

    fun stagedFile(context: Context, name: String): File =
        File(stagingDir(context), RemotePath.sanitizeDownloadName(name))

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.shared", file)

    /**
     * A share chooser intent for [file]. FLAG_GRANT_READ_URI_PERMISSION is what makes the
     * grant one-shot; without it the receiving app cannot read the file at all.
     */
    fun shareIntent(context: Context, file: File, displayName: String): Intent {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypes.forFileName(displayName)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, displayName).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** An "open with" intent, for viewing rather than sending. */
    fun openIntent(context: Context, file: File, displayName: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), MimeTypes.forFileName(displayName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Clears staged copies; they are disposable and can hold sensitive content. */
    fun clear(context: Context) {
        runCatching { stagingDir(context).listFiles()?.forEach { it.delete() } }
    }
}
