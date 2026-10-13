package app.terminalssh.secure.toolbar

/**
 * A deliberately tiny JSON reader/writer.
 *
 * The Android `org.json` classes are non-functional stubs in JVM unit tests, and the
 * toolbar configuration — layouts, themes, macro steps — is exactly the kind of thing
 * that has to survive version changes, corrupt files and hostile imports. That logic is
 * worth testing on a plain JVM, so it must not depend on Android's JSON.
 *
 * This supports the subset the configuration format actually uses: objects, arrays,
 * strings, numbers, booleans and null. It is strict on input (bad JSON is rejected rather
 * than silently coerced) and deterministic on output.
 */
sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = entries[key]
    }

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Str(val value: String) : JsonValue

    /** Numbers are carried as [Double]; every field in this format is an Int or a Long colour. */
    data class Num(val value: Double) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    object Null : JsonValue {
        override fun toString(): String = "null"
    }
}

fun JsonValue.encode(): String {
    val builder = StringBuilder()
    appendJson(builder)
    return builder.toString()
}

/** Reads JSON text. Returns null when the whole document is not valid JSON. */
object Json {
    const val MAX_NESTING_DEPTH = 64

    fun parse(text: String): JsonValue? {
        val reader = Reader(text)
        return try {
            val value = reader.readValue(0)
            reader.skipWhitespace()
            if (reader.remaining()) null else value
        } catch (_: JsonException) {
            null
        }
    }

    fun obj(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj =
        JsonValue.Obj(pairs.toMap())

    fun arr(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)

    fun str(value: String): JsonValue.Str = JsonValue.Str(value)

    fun num(value: Int): JsonValue.Num = JsonValue.Num(value.toDouble())

    fun num(value: Long): JsonValue.Num = JsonValue.Num(value.toDouble())

    fun bool(value: Boolean): JsonValue.Bool = JsonValue.Bool(value)

    private class JsonException : RuntimeException()

    private class Reader(private val text: String) {
        private var index = 0

        fun remaining(): Boolean = index < text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(depth: Int): JsonValue {
            skipWhitespace()
            if (index >= text.length) throw JsonException()
            if (depth > MAX_NESTING_DEPTH) throw JsonException()
            return when (val c = text[index]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw JsonException()
            }
        }

        private fun readObject(depth: Int): JsonValue.Obj {
            index++ // {
            val map = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') { index++; return JsonValue.Obj(map) }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException()
                val key = readString()
                skipWhitespace()
                if (peek() != ':') throw JsonException()
                index++
                map[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> { index++; return JsonValue.Obj(map) }
                    else -> throw JsonException()
                }
            }
        }

        private fun readArray(depth: Int): JsonValue.Arr {
            index++ // [
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (peek() == ']') { index++; return JsonValue.Arr(items) }
            while (true) {
                items.add(readValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> { index++; return JsonValue.Arr(items) }
                    else -> throw JsonException()
                }
            }
        }

        private fun readString(): String {
            index++ // opening quote
            val builder = StringBuilder()
            while (index < text.length) {
                val c = text[index++]
                when (c) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (index >= text.length) throw JsonException()
                        when (val esc = text[index++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw JsonException()
                                val hex = text.substring(index, index + 4)
                                builder.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonException())
                                index += 4
                            }
                            else -> throw JsonException()
                        }
                    }
                    else -> builder.append(c)
                }
            }
            throw JsonException()
        }

        private fun readBoolean(): JsonValue.Bool {
            return when {
                text.startsWith("true", index) -> { index += 4; JsonValue.Bool(true) }
                text.startsWith("false", index) -> { index += 5; JsonValue.Bool(false) }
                else -> throw JsonException()
            }
        }

        private fun readNull(): JsonValue {
            if (!text.startsWith("null", index)) throw JsonException()
            index += 4
            return JsonValue.Null
        }

        private fun readNumber(): JsonValue.Num {
            val start = index
            if (peek() == '-') index++
            while (index < text.length && (text[index] in '0'..'9' || text[index] == '.' ||
                    text[index] == 'e' || text[index] == 'E' || text[index] == '+' || text[index] == '-')
            ) index++
            val raw = text.substring(start, index)
            val value = raw.toDoubleOrNull() ?: throw JsonException()
            if (value.isNaN() || value.isInfinite()) throw JsonException()
            return JsonValue.Num(value)
        }

        private fun peek(): Char {
            if (index >= text.length) throw JsonException()
            return text[index]
        }
    }
}

fun JsonValue.appendJson(builder: StringBuilder) {
    when (this) {
        is JsonValue.Obj -> {
            builder.append('{')
            var first = true
            entries.forEach { (key, value) ->
                if (!first) builder.append(',')
                first = false
                builder.append(quote(key)).append(':')
                value.appendJson(builder)
            }
            builder.append('}')
        }
        is JsonValue.Arr -> {
            builder.append('[')
            items.forEachIndexed { i, value ->
                if (i > 0) builder.append(',')
                value.appendJson(builder)
            }
            builder.append(']')
        }
        is JsonValue.Str -> builder.append(quote(value))
        is JsonValue.Num -> builder.append(formatNumber(value))
        is JsonValue.Bool -> builder.append(if (value) "true" else "false")
        JsonValue.Null -> builder.append("null")
    }
}

private fun formatNumber(value: Double): String {
    // Colours are Longs up to 2^32; double formatting would turn them into 4.29E9.
    return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

private fun quote(value: String): String {
    val builder = StringBuilder(value.length + 2)
    builder.append('"')
    value.forEach { c ->
        when (c) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            else -> if (c < ' ') builder.append("\\u%04x".format(c.code)) else builder.append(c)
        }
    }
    builder.append('"')
    return builder.toString()
}
