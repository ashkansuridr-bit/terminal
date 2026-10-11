package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetLibraryTest {

    private fun snippet(
        id: String,
        name: String,
        category: String = "",
        tags: List<String> = emptyList(),
        favorite: Boolean = false,
    ) = SnippetEntry(id = id, name = name, createdAt = 0L, category = category, tags = tags, favorite = favorite)

    private val entries = listOf(
        snippet("a", "tail nginx", category = "web", tags = listOf("nginx", "logs")),
        snippet("b", "restart db", category = "db", tags = listOf("postgres"), favorite = true),
        snippet("c", "who is on disk", tags = listOf("ops")),
    )

    @Test fun defaultCriteriaKeepsOrder() {
        assertEquals(entries, SnippetLibrary.apply(entries, SnippetLibrary.Criteria()))
    }

    @Test fun favoritesOnly() {
        assertEquals(listOf("b"), SnippetLibrary.apply(entries, SnippetLibrary.Criteria(favoritesOnly = true)).map { it.id })
    }

    @Test fun categoryFilterIsCaseInsensitive() {
        assertEquals(listOf("a"), SnippetLibrary.apply(entries, SnippetLibrary.Criteria(category = "WEB")).map { it.id })
    }

    @Test fun tagFilterMatchesAny() {
        assertEquals(listOf("c"), SnippetLibrary.apply(entries, SnippetLibrary.Criteria(tags = setOf("ops"))).map { it.id })
    }

    @Test fun searchMatchesNameAndTags() {
        assertEquals(listOf("a"), SnippetLibrary.search(entries, "nginx").map { it.id })
        assertEquals(listOf("b"), SnippetLibrary.search(entries, "postgres").map { it.id })
    }

    @Test fun blankSearchKeepsEverything() {
        assertEquals(entries, SnippetLibrary.search(entries, "  "))
    }

    @Test fun categoriesAreDedupedAndSorted() {
        assertEquals(listOf("db", "web"), SnippetLibrary.categories(entries))
    }

    @Test fun tagsAreDeduped() {
        assertEquals(listOf("logs", "nginx", "ops", "postgres"), SnippetLibrary.availableTags(entries))
    }

    @Test fun activeCriteriaIsReported() {
        assertEquals(true, SnippetLibrary.Criteria(query = "x").isActive)
        assertEquals(false, SnippetLibrary.Criteria().isActive)
    }
}
