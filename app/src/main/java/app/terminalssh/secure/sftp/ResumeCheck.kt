package app.terminalssh.secure.sftp

import java.io.InputStream

/**
 * Size-only pre-resume consistency check: is it safe to trust [recordedBytes] as the
 * point to resume from, given what's actually on disk (a download's staging file) or on
 * the server (an upload's remote file) right now?
 *
 * This is deliberately not a content checksum — re-reading the already-transferred
 * prefix to hash it would mean re-downloading or re-uploading it, which defeats the
 * point of resuming. A size mismatch is cheap to detect and catches the common failure
 * modes (a staging file lost to app-restart, a remote file touched since the last
 * attempt); anything subtler than that is out of scope for a mobile SFTP client.
 */
fun canTrustResume(recordedBytes: Long, actualBytes: Long): Boolean = actualBytes == recordedBytes

/**
 * The remote half of the same question, for a resumed **download**.
 *
 * [canTrustResume] on a download only compares the recorded offset against the local
 * staging file, which proves the bytes already on disk are intact — but says nothing
 * about the file they came from. Resuming appends server bytes from [recordedTotalBytes]
 * onward, so if the remote file was replaced between attempts the two halves belong to
 * different files and the result is a silently corrupted download that still reports
 * success.
 *
 * Fails closed on purpose: an unknown recorded size, or a stat that could not be read
 * ([currentRemoteBytes] null), restarts the transfer instead of guessing. Re-downloading
 * is cheap; handing the user a corrupted file is not.
 */
fun canTrustRemoteForResume(recordedTotalBytes: Long, currentRemoteBytes: Long?): Boolean =
    recordedTotalBytes > 0L && currentRemoteBytes == recordedTotalBytes

/**
 * [InputStream.skip] is not guaranteed to skip everything requested in one call, so this
 * loops until [count] bytes are skipped or the stream runs out. Stopping early (a local
 * file that shrank since [count] was recorded) is left for the subsequent read/upload to
 * fail naturally rather than throwing here.
 */
fun skipFully(input: InputStream, count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped <= 0) break
        remaining -= skipped
    }
}
