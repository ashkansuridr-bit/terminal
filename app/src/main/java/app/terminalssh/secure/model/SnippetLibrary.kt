package app.terminalssh.secure.model

/**
 * Organising saved snippets (F018).
 *
 * Once there are more than a handful, a flat newest-first list stops working. Category,
 * tags and a favourite flag are metadata (never the command itself — that stays encrypted
 * in the vault), so they can be searched and grouped without decrypting anything.
 */
object SnippetLibrary {

    data class Criteria(
        val query: String = "",
        val category: String? = null,
        val tags: Set<String> = emptySet(),
        val favoritesOnly: Boolean = false,
    ) {
        val isActive: Boolean
            get() = query.isNotBlank() || category != null || tags.isNotEmpty() || favoritesOnly
    }

    fun apply(entries: List<SnippetEntry>, criteria: Criteria): List<SnippetEntry> =
        entries.filter { entry ->
            (!criteria.favoritesOnly || entry.favorite) &&
                (criteria.category == null || entry.category.equals(criteria.category, ignoreCase = true)) &&
                (criteria.tags.isEmpty() || entry.tags.any { it in criteria.tags }) &&
                (criteria.query.isBlank() || score(entry, criteria.query) > FuzzyMatch.NO_MATCH)
        }

    /** Name, category and tags are all searchable; name hits outrank tag hits. */
    fun score(entry: SnippetEntry, query: String): Int = maxOf(
        FuzzyMatch.score(query, entry.name),
        FuzzyMatch.score(query, entry.category),
        entry.tags.maxOfOrNull { FuzzyMatch.score(query, it) } ?: FuzzyMatch.NO_MATCH,
    )

    fun search(entries: List<SnippetEntry>, query: String): List<SnippetEntry> {
        if (query.isBlank()) return entries
        return entries
            .map { it to score(it, query) }
            .filter { (_, s) -> s > FuzzyMatch.NO_MATCH }
            .sortedByDescending { (_, s) -> s }
            .map { (entry, _) -> entry }
    }

    fun categories(entries: List<SnippetEntry>): List<String> =
        entries.map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun availableTags(entries: List<SnippetEntry>): List<String> =
        entries.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
}
