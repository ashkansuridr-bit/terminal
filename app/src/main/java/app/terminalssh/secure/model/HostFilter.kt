package app.terminalssh.secure.model

/**
 * Filtering and grouping for the host list (F003/F002).
 *
 * The list is the app's home screen; once a user has more than a screenful of servers,
 * "which ones are production" and "the ones I tagged ops" is the question, not "what is
 * the alphabetical order". Filtering stays a pure function over the host list so the
 * tab bar, the tag chips and the search box can all feed the same predicate.
 */
object HostFilter {

    data class Criteria(
        val query: String = "",
        val tags: Set<String> = emptySet(),
        val environments: Set<Environment> = emptySet(),
        val favoritesOnly: Boolean = false,
    ) {
        val isActive: Boolean
            get() = query.isNotBlank() || tags.isNotEmpty() || environments.isNotEmpty() || favoritesOnly
    }

    /**
     * Applies [criteria] and returns the hosts in list order.
     *
     * Tag and environment filters are conjunctive (a host must carry at least one selected
     * tag and be in one of the selected environments) so selecting more chips narrows the
     * list, which is what a filter bar is expected to do.
     */
    fun apply(hosts: List<HostProfile>, criteria: Criteria): List<HostProfile> =
        hosts.filter { host ->
            (!criteria.favoritesOnly || host.favorite) &&
                (criteria.tags.isEmpty() || host.tags.any { it in criteria.tags }) &&
                (criteria.environments.isEmpty() || host.environment in criteria.environments) &&
                (criteria.query.isBlank() || host.matches(criteria.query))
        }

    /** Every distinct tag, case-insensitively de-duplicated and sorted for stable chips. */
    fun availableTags(hosts: List<HostProfile>): List<String> =
        hosts.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** Environments actually present, so the filter bar never shows an empty chip. */
    fun availableEnvironments(hosts: List<HostProfile>): List<Environment> =
        Environment.entries.filter { env -> env != Environment.NONE && hosts.any { it.environment == env } }
}
