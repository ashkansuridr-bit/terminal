package app.terminalssh.secure.model

/**
 * Subsequence matching for the host search box.
 *
 * A plain `contains` forces the exact substring: "dbprod" finds nothing when the host is
 * "prod-db-01". Matching the query as an ordered subsequence lets a few remembered
 * characters reach the right server, which is what actually happens on a phone keyboard.
 *
 * Scoring exists so the list can be ordered by how well each host matched rather than by
 * whether it matched at all: a contiguous run and a match at a word boundary both beat
 * characters scattered across the string.
 */
object FuzzyMatch {

    /** Not a match. Callers filter on `score > NO_MATCH`. */
    const val NO_MATCH = 0

    /**
     * @return a positive score when every character of [query] appears in [candidate] in
     *   order, or [NO_MATCH] otherwise. A blank query matches everything with a score of 1.
     */
    fun score(query: String, candidate: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 1
        val c = candidate.lowercase()
        if (q.length > c.length) return NO_MATCH

        // An exact substring is always the strongest signal; rank it above any
        // scattered subsequence so typing a full host name puts it first.
        val substringAt = c.indexOf(q)
        if (substringAt >= 0) {
            val boundary = substringAt == 0 || !c[substringAt - 1].isLetterOrDigit()
            return EXACT_BASE + q.length * PER_CHAR + (if (boundary) BOUNDARY_BONUS else 0)
        }

        var score = 0
        var streak = 0
        var index = 0
        for (ch in q) {
            val found = c.indexOf(ch, index)
            if (found < 0) return NO_MATCH
            score += PER_CHAR
            // Reward characters that continue a run, and characters that start a word.
            streak = if (found == index && index > 0) streak + 1 else 0
            score += streak * STREAK_BONUS
            if (found == 0 || !c[found - 1].isLetterOrDigit()) score += BOUNDARY_BONUS
            index = found + 1
        }
        return score
    }

    fun matches(query: String, candidate: String): Boolean = score(query, candidate) > NO_MATCH

    private const val EXACT_BASE = 1_000
    private const val PER_CHAR = 10
    private const val STREAK_BONUS = 5
    private const val BOUNDARY_BONUS = 8
}
