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

/** Per-file copy status, persisted so a stopped job can resume without re-copying verified files. */
enum class FileCopyStatus { PENDING, VERIFIED, FAILED }

/** Overall job status, persisted alongside [FileCopyStatus] records. */
enum class JobCopyStatus { PENDING, IN_PROGRESS, STOPPED, COMPLETED, COMPLETED_WITH_ERRORS }

/** One file's copy plan/progress: where it comes from, where it lands, and how it went. */
data class CopyFileRecord(
    val sourcePath: String,
    val destinationPath: String,
    val sizeBytes: Long,
    val status: FileCopyStatus,
    val error: String? = null,
    val checksum: String? = null,
)

/** The full result of reading back a job JSON — see [buildJobJson] for the schema written. */
data class ParsedJob(
    val items: List<SelectedItem>,
    val destinations: List<DestinationEntry>,
    val copyStatus: JobCopyStatus,
    val files: List<CopyFileRecord>,
)

fun buildJobJson(
    items: List<SelectedItem>,
    destinations: List<DestinationEntry>,
    copyStatus: JobCopyStatus = JobCopyStatus.PENDING,
    files: List<CopyFileRecord> = emptyList(),
): String = buildString {
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
    append("  ]")
    if (files.isNotEmpty() || copyStatus != JobCopyStatus.PENDING) {
        append(",\n  \"copyStatus\": ${jsonString(copyStatus.name.lowercase())},\n")
        append("  \"files\": [\n")
        files.forEachIndexed { index, file ->
            append("    {\n")
            append("      \"sourcePath\": ${jsonString(file.sourcePath)},\n")
            append("      \"destinationPath\": ${jsonString(file.destinationPath)},\n")
            append("      \"sizeBytes\": ${file.sizeBytes},\n")
            append("      \"status\": ${jsonString(file.status.name.lowercase())}")
            if (file.error != null) append(",\n      \"error\": ${jsonString(file.error)}")
            if (file.checksum != null) append(",\n      \"checksum\": ${jsonString(file.checksum)}")
            append("\n    }")
            append(if (index != files.lastIndex) ",\n" else "\n")
        }
        append("  ]\n")
    } else {
        append("\n")
    }
    append("}\n")
}

/** Reads back what [buildJobJson] writes. Missing/absent `copyStatus`/`files` mean "never started". */
fun parseJobJson(text: String): ParsedJob {
    val root = parseJson(text).obj()

    val items = root.arrOrEmpty("itemsToCopy").map { value ->
        val entry = value.obj()
        SelectedItem(
            path = entry.stringOrNull("path").orEmpty(),
            isDirectory = entry.stringOrNull("type") == "directory",
            sizeBytes = entry.longOrNull("sizeBytes") ?: 0L,
            fileCount = entry.intOrNull("fileCount"),
        )
    }

    val destinations = root.arrOrEmpty("destinations").map { value ->
        val entry = value.obj()
        DestinationEntry(
            path = entry.stringOrNull("path").orEmpty(),
            percent = entry.intOrNull("percent") ?: 0,
        )
    }

    val copyStatus = root.stringOrNull("copyStatus")
        ?.let { runCatching { JobCopyStatus.valueOf(it.uppercase()) }.getOrNull() }
        ?: JobCopyStatus.PENDING

    val files = root.arrOrEmpty("files").map { value ->
        val entry = value.obj()
        CopyFileRecord(
            sourcePath = entry.stringOrNull("sourcePath").orEmpty(),
            destinationPath = entry.stringOrNull("destinationPath").orEmpty(),
            sizeBytes = entry.longOrNull("sizeBytes") ?: 0L,
            status = entry.stringOrNull("status")
                ?.let { runCatching { FileCopyStatus.valueOf(it.uppercase()) }.getOrNull() }
                ?: FileCopyStatus.PENDING,
            error = entry.stringOrNull("error"),
            checksum = entry.stringOrNull("checksum"),
        )
    }

    return ParsedJob(items, destinations, copyStatus, files)
}
