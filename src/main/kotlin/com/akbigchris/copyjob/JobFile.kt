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

/** A destination directory plus the percentage of its free space that may be used. */
data class DestinationEntry(val path: String, val percent: Int)

fun buildJobJson(items: List<SelectedItem>, destinations: List<DestinationEntry>): String = buildString {
    append("{\n")
    append("  \"itemsToCopy\": [\n")
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
    destinations.forEachIndexed { index, dest ->
        append("    {\n")
        append("      \"path\": ${jsonString(dest.path)},\n")
        append("      \"order\": ${index + 1},\n")
        append("      \"percent\": ${dest.percent}\n")
        append("    }")
        append(if (index != destinations.lastIndex) ",\n" else "\n")
    }
    append("  ]\n")
    append("}\n")
}
