package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxHighlightTest {

    @Test fun languageIsPickedFromTheFileName() {
        assertEquals(SyntaxHighlight.Language.SHELL, SyntaxHighlight.languageFor("deploy.sh"))
        assertEquals(SyntaxHighlight.Language.SHELL, SyntaxHighlight.languageFor(".bashrc"))
        assertEquals(SyntaxHighlight.Language.YAML, SyntaxHighlight.languageFor("docker-compose.yml"))
        assertEquals(SyntaxHighlight.Language.JSON, SyntaxHighlight.languageFor("package.json"))
        assertEquals(SyntaxHighlight.Language.PROPERTIES, SyntaxHighlight.languageFor("nginx.conf"))
        assertEquals(SyntaxHighlight.Language.PROPERTIES, SyntaxHighlight.languageFor(".env"))
        assertEquals(SyntaxHighlight.Language.NONE, SyntaxHighlight.languageFor("core.dump"))
    }

    @Test fun anUnknownLanguageProducesNoSpans() {
        assertTrue(SyntaxHighlight.tokenize("anything", SyntaxHighlight.Language.NONE).isEmpty())
        assertTrue(SyntaxHighlight.tokenize("", SyntaxHighlight.Language.SHELL).isEmpty())
    }

    @Test fun commentsRunToEndOfLineOnly() {
        val text = "# a comment\nreal line"
        val spans = SyntaxHighlight.tokenize(text, SyntaxHighlight.Language.PROPERTIES)
        val comment = spans.first { it.token == SyntaxHighlight.Token.COMMENT }
        assertEquals(0, comment.start)
        assertEquals(text.indexOf('\n'), comment.end, "a comment must not swallow the next line")
    }

    @Test fun jsonHasNoHashComments() {
        val spans = SyntaxHighlight.tokenize("""{"a": 1}""", SyntaxHighlight.Language.JSON)
        assertTrue(spans.none { it.token == SyntaxHighlight.Token.COMMENT })
    }

    @Test fun stringsAreTokenisedAndEscapesDoNotTerminateThem() {
        val text = """key = "a \" still string" after"""
        val spans = SyntaxHighlight.tokenize(text, SyntaxHighlight.Language.PROPERTIES)
        val str = spans.first { it.token == SyntaxHighlight.Token.STRING }
        assertEquals(text.indexOf('"'), str.start)
        assertTrue(text.substring(str.start, str.end).endsWith("\""))
        assertTrue(str.end < text.length, "the trailing word is not part of the string")
    }

    @Test fun anUnterminatedQuoteStopsAtTheLineNotTheFile() {
        // Otherwise one stray quote greys out the rest of a config.
        val text = "a = \"oops\nb = 2\nc = 3"
        val spans = SyntaxHighlight.tokenize(text, SyntaxHighlight.Language.PROPERTIES)
        val str = spans.first { it.token == SyntaxHighlight.Token.STRING }
        assertTrue(str.end <= text.indexOf('\n') + 1, "an unterminated string must not run to EOF")
    }

    @Test fun keywordsAreRecognisedButSubstringsAreNot() {
        val spans = SyntaxHighlight.tokenize("if then fifty", SyntaxHighlight.Language.SHELL)
        val keywords = spans.filter { it.token == SyntaxHighlight.Token.KEYWORD }
        assertEquals(2, keywords.size, "'fifty' contains 'if' but is not a keyword")
    }

    @Test fun numbersAreTokenisedButNotInsideIdentifiers() {
        val spans = SyntaxHighlight.tokenize("port 8080 utf8", SyntaxHighlight.Language.PROPERTIES)
        val numbers = spans.filter { it.token == SyntaxHighlight.Token.NUMBER }
        assertEquals(1, numbers.size, "the 8 in utf8 is part of an identifier")
    }

    @Test fun spansAreOrderedAndNonOverlapping() {
        val text = "# c\nlisten 80;\nname = \"x\"\n"
        val spans = SyntaxHighlight.tokenize(text, SyntaxHighlight.Language.PROPERTIES)
        var previousEnd = 0
        for (s in spans) {
            assertTrue(s.start >= previousEnd, "spans overlap or go backwards at ${s.start}")
            assertTrue(s.end > s.start)
            assertTrue(s.end <= text.length)
            previousEnd = s.end
        }
    }

    // ---- go-to-line, which is what makes a server error message actionable ----

    @Test fun lineNumbersAndOffsetsRoundTrip() {
        val text = "one\ntwo\nthree\nfour"
        for (line in 1..4) {
            assertEquals(line, SyntaxHighlight.lineOf(text, SyntaxHighlight.offsetOfLine(text, line)))
        }
    }

    @Test fun lineOfIsOneBasedAndClamped() {
        val text = "a\nb"
        assertEquals(1, SyntaxHighlight.lineOf(text, 0))
        assertEquals(1, SyntaxHighlight.lineOf(text, -5))
        assertEquals(2, SyntaxHighlight.lineOf(text, 2))
        assertEquals(2, SyntaxHighlight.lineOf(text, 9999))
    }

    @Test fun offsetOfLineClampsBeyondTheEnd() {
        val text = "a\nb"
        assertEquals(0, SyntaxHighlight.offsetOfLine(text, 0))
        assertEquals(text.length, SyntaxHighlight.offsetOfLine(text, 99))
    }
}
