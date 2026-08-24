package app.terminalssh.secure.sftp

/**
 * POSIX path arithmetic for the remote browser.
 *
 * Deliberately not `java.io.File`: that resolves against the *device's* filesystem rules,
 * and on a phone the separator and root semantics happen to match POSIX only by accident.
 * Remote paths are always POSIX regardless of what the phone runs.
 */
object RemotePath {

    const val ROOT = "/"

    /** Normalises `.`, `..`, duplicate slashes, and trailing slashes. */
    fun normalize(path: String): String {
        val absolute = path.startsWith('/')
        val parts = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty() && parts.last() != "..") {
                    parts.removeLast()
                } else if (!absolute) {
                    // A relative path may legitimately climb above its own start.
                    parts.addLast("..")
                }
                else -> parts.addLast(segment)
            }
        }
        val joined = parts.joinToString("/")
        return when {
            absolute -> "/$joined"
            joined.isEmpty() -> "."
            else -> joined
        }
    }

    /** Appends [child] to [base], treating an absolute [child] as a replacement. */
    fun join(base: String, child: String): String = when {
        child.startsWith('/') -> normalize(child)
        base.endsWith('/') -> normalize(base + child)
        else -> normalize("$base/$child")
    }

    /** The containing directory, or [ROOT] when already at the top. */
    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == ROOT) return ROOT
        val cut = normalized.lastIndexOf('/')
        return when {
            cut <= 0 -> ROOT
            else -> normalized.substring(0, cut)
        }
    }

    /** The final component, or [ROOT] for the root itself. */
    fun name(path: String): String {
        val normalized = normalize(path)
        if (normalized == ROOT) return ROOT
        return normalized.substringAfterLast('/')
    }

    /**
     * Breadcrumb segments with the absolute path each one navigates to.
     * `/var/log` becomes `[("/", "/"), ("var", "/var"), ("log", "/var/log")]`.
     */
    fun breadcrumbs(path: String): List<Pair<String, String>> {
        val normalized = normalize(path)
        val crumbs = mutableListOf(ROOT to ROOT)
        if (normalized == ROOT) return crumbs
        var accumulated = ""
        for (segment in normalized.trim('/').split('/')) {
            accumulated += "/$segment"
            crumbs += segment to accumulated
        }
        return crumbs
    }

    /**
     * A filename safe to write into device storage.
     *
     * A remote server is untrusted: a file literally named `../../evil` must not be able
     * to steer a download outside the folder the user picked.
     */
    fun sanitizeDownloadName(remoteName: String): String {
        val base = remoteName.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.filterNot { it.isISOControl() }.trim()
        return when {
            cleaned.isEmpty() || cleaned == "." || cleaned == ".." -> "download"
            else -> cleaned
        }
    }
}
