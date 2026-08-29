package app.terminalssh.secure.sftp

/**
 * Minimal tokenizer for the remote editor (#27).
 *
 * Editing an `nginx.conf` or a `docker-compose.yml` as undifferentiated grey text is
 * where mistakes come from, and the error the server reports afterwards points at a line
 * number the editor could not show either.
 *
 * Deliberately a lexer and not a parser: a handful of token classes that survive being
 * wrong. A mis-coloured line is a cosmetic bug; a parser that throws on a config it does
 * not understand would block the edit entirely, and this must never do that.
 */
object SyntaxHighlight {

    enum class Token { PLAIN, COMMENT, STRING, NUMBER, KEYWORD, PUNCTUATION }

    data class Span(val start: Int, val end: Int, val token: Token)

    enum class Language { SHELL, YAML, JSON, PROPERTIES, NONE }

    private val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
        "function", "return", "export", "local", "readonly", "source", "exit", "echo", "set",
    )
    private val yamlKeywords = setOf("true", "false", "null", "yes", "no", "on", "off", "~")

    fun languageFor(fileName: String): Language {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".sh") || lower.endsWith(".bash") || lower == ".bashrc" ||
                lower == ".profile" || lower == ".zshrc" -> Language.SHELL
            lower.endsWith(".yml") || lower.endsWith(".yaml") -> Language.YAML
            lower.endsWith(".json") -> Language.JSON
            lower.endsWith(".conf") || lower.endsWith(".cfg") || lower.endsWith(".ini") ||
                lower.endsWith(".properties") || lower.endsWith(".env") || lower == ".env" -> Language.PROPERTIES
            else -> Language.NONE
        }
    }

    /** Non-overlapping spans in source order. An empty list means "render as plain text". */
    fun tokenize(text: String, language: Language): List<Span> {
        if (language == Language.NONE || text.isEmpty()) return emptyList()
        val spans = mutableListOf<Span>()
        val keywords = when (language) {
            Language.SHELL -> shellKeywords
            Language.YAML, Language.JSON -> yamlKeywords
            else -> emptySet()
        }
        val commentChar = if (language == Language.JSON) null else '#'

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                commentChar != null && c == commentChar -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    spans += Span(i, end, Token.COMMENT)
                    i = end
                }
                c == '"' || c == '\'' -> {
                    val end = closingQuote(text, i, c)
                    spans += Span(i, end, Token.STRING)
                    i = end
                }
                c.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                    var end = i
                    while (end < text.length && (text[end].isDigit() || text[end] == '.')) end++
                    spans += Span(i, end, Token.NUMBER)
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    var end = i
                    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                    val word = text.substring(i, end)
                    if (word in keywords) spans += Span(i, end, Token.KEYWORD)
                    i = end
                }
                c in ":{}[],=" -> {
                    spans += Span(i, i + 1, Token.PUNCTUATION)
                    i++
                }
                else -> i++
            }
        }
        return spans
    }

    /** Index just past the closing quote, or end of line for an unterminated string. */
    private fun closingQuote(text: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == quote -> return i + 1
                // An unterminated quote must not swallow the rest of the file.
                text[i] == '\n' -> return i
                else -> i++
            }
        }
        return text.length
    }

    /** 1-based line number containing [offset], for go-to-line and error mapping. */
    fun lineOf(text: String, offset: Int): Int {
        if (offset <= 0) return 1
        var line = 1
        for (i in 0 until minOf(offset, text.length)) if (text[i] == '\n') line++
        return line
    }

    /** Character offset where 1-based [line] starts, clamped into the text. */
    fun offsetOfLine(text: String, line: Int): Int {
        if (line <= 1) return 0
        var seen = 1
        for (i in text.indices) {
            if (text[i] == '\n') {
                seen++
                if (seen == line) return i + 1
            }
        }
        return text.length
    }
}
