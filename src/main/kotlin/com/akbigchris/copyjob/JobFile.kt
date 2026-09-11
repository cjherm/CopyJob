package com.akbigchris.copyjob

/**
 * JSON serialization for a saved copy job — the format the "Open job" JSON picker
 * (see FileSelectionScreen.kt) expects to eventually read back.
 */

private fun jsonEscape(value: String): String = buildString {
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

private fun jsonString(value: String): String = "\"${jsonEscape(value)}\""

fun buildJobJson(items: List<SelectedItem>, destinationPaths: List<String>): String = buildString {
    append("{\n")
    append("  \"items\": [\n")
    items.forEachIndexed { index, item ->
        append("    {\n")
        append("      \"path\": ${jsonString(item.path)},\n")
        append("      \"type\": ${jsonString(if (item.isDirectory) "directory" else "file")},\n")
        append("      \"sizeBytes\": ${item.sizeBytes}")
        if (item.isDirectory && item.fileCount != null) {
            append(",\n      \"fileCount\": ${item.fileCount}")
        }
        append("\n    }")
        append(if (index != items.lastIndex) ",\n" else "\n")
    }
    append("  ],\n")
    append("  \"destinations\": [\n")
    destinationPaths.forEachIndexed { index, path ->
        append("    {\n")
        append("      \"path\": ${jsonString(path)},\n")
        append("      \"order\": ${index + 1}\n")
        append("    }")
        append(if (index != destinationPaths.lastIndex) ",\n" else "\n")
    }
    append("  ]\n")
    append("}\n")
}
