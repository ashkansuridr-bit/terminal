package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnippetTemplateTest {

    private val ctx = SnippetTemplate.Context(
        host = "box.example.com",
        user = "deploy",
        port = 2222,
        path = "/var/log/app.log",
        label = "Prod web",
        name = "tail logs",
    )

    @Test fun namedPlaceholdersAreSubstitutedAndQuoted() {
        val out = SnippetTemplate.render("ssh {{user}}@{{host}} -p {{port}}", ctx)
        assertEquals("ssh 'deploy'@'box.example.com' -p '2222'", out)
    }

    @Test fun pathPlaceholderIsQuoted() {
        assertEquals("tail -f '/var/log/app.log'", SnippetTemplate.render("tail -f {{path}}", ctx))
    }

    @Test fun whitespaceInsideBracesIsTolerated() {
        assertEquals("'box.example.com'", SnippetTemplate.render("{{  host  }}", ctx))
    }

    @Test fun unknownPlaceholdersAreReported() {
        assertEquals(listOf("typo"), SnippetTemplate.unknown("echo {{typo}} {{host}}"))
    }

    @Test fun unknownPlaceholdersAreLeftVerbatim() {
        assertEquals("echo {{typo}}", SnippetTemplate.render("echo {{typo}}", ctx))
    }

    @Test fun injectionAttemptInAValueIsNeutralised() {
        val nasty = SnippetTemplate.Context(path = "x'; rm -rf ~; '")
        val out = SnippetTemplate.render("cat {{path}}", nasty)
        // The whole value stays inside one single-quoted token.
        assertTrue(out.startsWith("cat '"))
        assertTrue(out.endsWith("'"))
        assertTrue(out.contains("'\\''"), "embedded quote must be escaped: $out")
    }

    @Test fun escapingCanBeOptedOut() {
        assertEquals("box.example.com", SnippetTemplate.render("{{host}}", ctx, escape = false))
    }

    @Test fun labelFallsBackToHostWhenBlank() {
        val noLabel = SnippetTemplate.Context(host = "h", label = "")
        assertEquals("'h'", SnippetTemplate.render("{{label}}", noLabel))
    }

    @Test fun previewFlagsUnsafeTemplates() {
        val preview = SnippetTemplate.describe("echo {{nope}}", ctx)
        assertFalse(preview.isSafeToSend)
        assertEquals(listOf("nope"), preview.unknownPlaceholders)
    }

    @Test fun previewListsResolvedPlaceholders() {
        val preview = SnippetTemplate.describe("echo {{host}} {{user}}", ctx)
        assertTrue(preview.isSafeToSend)
        assertEquals(listOf("host", "user"), preview.resolved)
    }

    @Test fun shellQuoteHandlesEmbeddedSingleQuote() {
        assertEquals("'it'\\''s'", SnippetTemplate.shellQuote("it's"))
    }
}
