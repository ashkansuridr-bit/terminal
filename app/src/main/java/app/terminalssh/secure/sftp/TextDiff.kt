package app.terminalssh.secure.sftp

/**
 * Line diff between the remote file and what is about to overwrite it.
 *
 * The editor can already tell you *that* the remote changed; this says *what* changed,
 * which is the difference between a warning a user can act on and one they can only
 * accept or cancel blindly.
 *
 * Plain LCS over lines. Not the fastest algorithm published, but the input is a config
 * file someone is editing on a phone, and the quadratic table is bounded by
 * [MAX_LINES] so a stray multi-megabyte file cannot wedge the UI.
 */
object TextDiff {

    enum class Kind { KEPT, ADDED, REMOVED }

    data class Line(val kind: Kind, val text: String, val oldLine: Int?, val newLine: Int?)

    data class Result(val lines: List<Line>, val truncated: Boolean) {
        val added: Int get() = lines.count { it.kind == Kind.ADDED }
        val removed: Int get() = lines.count { it.kind == Kind.REMOVED }
        val hasChanges: Boolean get() = added > 0 || removed > 0
    }

    /** Beyond this the table costs more than the answer is worth on a phone. */
    const val MAX_LINES = 4_000

    fun diff(oldText: String, newText: String): Result {
        val a = oldText.split('\n')
        val b = newText.split('\n')
        if (a.size > MAX_LINES || b.size > MAX_LINES) {
            return Result(emptyList(), truncated = true)
        }

        // lcs[i][j] = length of the longest common subsequence of a[i:] and b[j:]
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val out = ArrayList<Line>(a.size + b.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += Line(Kind.KEPT, a[i], i + 1, j + 1); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { out += Line(Kind.REMOVED, a[i], i + 1, null); i++ }
                else -> { out += Line(Kind.ADDED, b[j], null, j + 1); j++ }
            }
        }
        while (i < a.size) { out += Line(Kind.REMOVED, a[i], i + 1, null); i++ }
        while (j < b.size) { out += Line(Kind.ADDED, b[j], null, j + 1); j++ }

        return Result(out, truncated = false)
    }
}
