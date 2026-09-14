package com.akbigchris.copyjob

/**
 * Minimal hand-rolled JSON reader (no external library, matching [buildJobJson]'s hand-rolled
 * writer) — only needs to parse what this app's own writer produces.
 */
sealed class JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    object Null : JsonValue()
}

fun JsonValue.obj(): Map<String, JsonValue> = (this as JsonValue.Obj).entries
fun JsonValue.arr(): List<JsonValue> = (this as JsonValue.Arr).items
fun JsonValue.str(): String = (this as JsonValue.Str).value

fun Map<String, JsonValue>.stringOrNull(key: String): String? = (this[key] as? JsonValue.Str)?.value
fun Map<String, JsonValue>.longOrNull(key: String): Long? = (this[key] as? JsonValue.Num)?.value?.toLong()
fun Map<String, JsonValue>.intOrNull(key: String): Int? = (this[key] as? JsonValue.Num)?.value?.toInt()
fun Map<String, JsonValue>.arrOrEmpty(key: String): List<JsonValue> = (this[key] as? JsonValue.Arr)?.items ?: emptyList()

fun parseJson(text: String): JsonValue = JsonParser(text).parse()

private class JsonParser(private val text: String) {
    private var pos = 0

    fun parse(): JsonValue {
        skipWhitespace()
        return parseValue()
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't' -> { expectLiteral("true"); JsonValue.Bool(true) }
            'f' -> { expectLiteral("false"); JsonValue.Bool(false) }
            'n' -> { expectLiteral("null"); JsonValue.Null }
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue.Obj {
        expectChar('{')
        val entries = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return JsonValue.Obj(entries)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expectChar(':')
            entries[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> { pos++; break }
                else -> error("Expected ',' or '}' at position $pos")
            }
        }
        return JsonValue.Obj(entries)
    }

    private fun parseArray(): JsonValue.Arr {
        expectChar('[')
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return JsonValue.Arr(items)
        }
        while (true) {
            items.add(parseValue())
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> { pos++; break }
                else -> error("Expected ',' or ']' at position $pos")
            }
        }
        return JsonValue.Arr(items)
    }

    private fun parseString(): String {
        expectChar('"')
        val sb = StringBuilder()
        while (true) {
            when (val c = text[pos++]) {
                '"' -> return sb.toString()
                '\\' -> when (val escape = text[pos++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'u' -> {
                        val hex = text.substring(pos, pos + 4)
                        pos += 4
                        sb.append(hex.toInt(16).toChar())
                    }
                    else -> error("Unknown escape sequence \\$escape at position $pos")
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseNumber(): JsonValue.Num {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        return JsonValue.Num(text.substring(start, pos).toDouble())
    }

    private fun peek(): Char = text[pos]

    private fun expectChar(c: Char) {
        if (text[pos] != c) error("Expected '$c' at position $pos")
        pos++
    }

    private fun expectLiteral(literal: String) {
        if (!text.startsWith(literal, pos)) error("Expected '$literal' at position $pos")
        pos += literal.length
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }
}
