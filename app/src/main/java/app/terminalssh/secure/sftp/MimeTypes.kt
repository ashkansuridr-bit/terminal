package app.terminalssh.secure.sftp

/**
 * Extension to MIME type, for the file picker and for "open with".
 *
 * Deliberately small and local rather than delegating to `MimeTypeMap`: the picker asks
 * for a type on every row of every listing, and the platform lookup is a content-provider
 * call. The generic fallback is `application/octet-stream`, which every app treats as
 * "some file", rather than `text/plain`, which invites editors to mangle binaries.
 */
object MimeTypes {

    private val byExtension = mapOf(
        "txt" to "text/plain", "md" to "text/markdown", "log" to "text/plain",
        "conf" to "text/plain", "cfg" to "text/plain", "ini" to "text/plain",
        "sh" to "text/x-shellscript", "bash" to "text/x-shellscript",
        "json" to "application/json", "xml" to "text/xml",
        "yml" to "text/yaml", "yaml" to "text/yaml", "toml" to "text/plain",
        "html" to "text/html", "htm" to "text/html", "css" to "text/css",
        "js" to "text/javascript", "ts" to "text/plain",
        "py" to "text/x-python", "rb" to "text/x-ruby", "go" to "text/plain",
        "rs" to "text/plain", "c" to "text/x-c", "h" to "text/x-c",
        "cpp" to "text/x-c", "java" to "text/x-java-source", "kt" to "text/plain",
        "sql" to "application/sql", "csv" to "text/csv",
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "svg" to "image/svg+xml",
        "pdf" to "application/pdf",
        "zip" to "application/zip", "gz" to "application/gzip",
        "tar" to "application/x-tar", "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz", "7z" to "application/x-7z-compressed",
        "mp3" to "audio/mpeg", "wav" to "audio/wav",
        "mp4" to "video/mp4", "mkv" to "video/x-matroska",
        "pem" to "application/x-pem-file", "key" to "application/octet-stream",
    )

    const val FALLBACK = "application/octet-stream"

    fun forFileName(name: String): String {
        // A dotfile with no other dot has no extension: ".bashrc" is not a "bashrc" file.
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return FALLBACK
        return byExtension[name.substring(dot + 1).lowercase()] ?: FALLBACK
    }
}
