package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostFilterTest {

    private fun host(
        id: String,
        label: String = "",
        tags: List<String> = emptyList(),
        environment: Environment = Environment.NONE,
        favorite: Boolean = false,
    ) = HostProfile(
        id = id,
        label = label,
        host = "$id.example.com",
        username = "root",
        auth = AuthMethod.Password(""),
        tags = tags,
        environment = environment,
        favorite = favorite,
    )

    private val hosts = listOf(
        host("a", label = "prod-web", tags = listOf("ops", "web"), environment = Environment.PRODUCTION),
        host("b", label = "dev-db", tags = listOf("ops", "db"), environment = Environment.DEVELOPMENT, favorite = true),
        host("c", label = "misc", tags = emptyList(), environment = Environment.NONE),
    )

    @Test fun defaultCriteriaKeepsEverything() {
        assertEquals(hosts, HostFilter.apply(hosts, HostFilter.Criteria()))
        assertEquals(false, HostFilter.Criteria().isActive)
    }

    @Test fun favoritesOnlyKeepsStarredHosts() {
        val result = HostFilter.apply(hosts, HostFilter.Criteria(favoritesOnly = true))
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun tagFilterMatchesAnySelectedTag() {
        val result = HostFilter.apply(hosts, HostFilter.Criteria(tags = setOf("db")))
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun environmentFilterNarrows() {
        val result = HostFilter.apply(hosts, HostFilter.Criteria(environments = setOf(Environment.PRODUCTION)))
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test fun filtersCombineConjunctively() {
        val result = HostFilter.apply(
            hosts,
            HostFilter.Criteria(tags = setOf("ops"), environments = setOf(Environment.PRODUCTION)),
        )
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test fun queryUsesFuzzySearch() {
        val result = HostFilter.apply(hosts, HostFilter.Criteria(query = "proweb"))
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test fun availableTagsAreDedupedCaseInsensitivelyAndSorted() {
        val tags = HostFilter.availableTags(hosts + host("d", tags = listOf("OPS", " zeta ")))
        assertEquals(listOf("db", "ops", "web", "zeta"), tags)
    }

    @Test fun availableEnvironmentsSkipNoneAndAbsentOnes() {
        assertEquals(
            listOf(Environment.DEVELOPMENT, Environment.PRODUCTION),
            HostFilter.availableEnvironments(hosts),
        )
    }

    @Test fun activeCriteriaIsReported() {
        assertTrue(HostFilter.Criteria(query = "x").isActive)
        assertTrue(HostFilter.Criteria(tags = setOf("t")).isActive)
    }
}
