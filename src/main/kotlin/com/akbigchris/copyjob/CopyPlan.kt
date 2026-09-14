package com.akbigchris.copyjob

import java.io.File

/** A file too large to fit in any remaining destination during planning. */
data class CopyPlanFailure(val sourcePath: String, val sizeBytes: Long)

sealed class CopyPlanResult {
    data class Success(val records: List<CopyFileRecord>) : CopyPlanResult()
    data class InsufficientSpace(val failure: CopyPlanFailure) : CopyPlanResult()
}

private class DestinationCapacity(val path: String, var remainingBytes: Long)

/**
 * Flattens [items] into individual file copy tasks (a file item is one task; a directory item's
 * files are each their own task, recreated under the destination at their relative path) and
 * assigns each to a destination.
 *
 * Files are never split — assignment walks [destinationCapacities] in order with a running
 * remaining-capacity counter: once a destination can no longer fit the current file, move on to
 * the next and never come back (mirrors SummaryScreen's cumulative-capacity "necessary" logic —
 * simple and predictable, at the cost of occasionally leaving a small unfilled gap behind).
 */
fun buildCopyPlan(items: List<SelectedItem>, destinationCapacities: List<Pair<String, Long>>): CopyPlanResult {
    val destinations = destinationCapacities.map { (path, capacity) -> DestinationCapacity(path, capacity) }
    var destIndex = 0
    val records = mutableListOf<CopyFileRecord>()

    fun assign(sourcePath: String, destinationRelativePath: String, sizeBytes: Long): Boolean {
        while (destIndex < destinations.size && destinations[destIndex].remainingBytes < sizeBytes) {
            destIndex++
        }
        if (destIndex >= destinations.size) return false
        val destination = destinations[destIndex]
        destination.remainingBytes -= sizeBytes
        val fullDestinationPath = File(destination.path, destinationRelativePath).path
        records.add(CopyFileRecord(sourcePath, fullDestinationPath, sizeBytes, FileCopyStatus.PENDING))
        return true
    }

    for (item in items) {
        val root = File(item.path)
        val itemName = root.name.ifEmpty { item.path }
        if (item.isDirectory) {
            for (file in root.walkTopDown()) {
                if (!file.isFile) continue
                val relative = file.relativeTo(root).path
                val destinationRelativePath = if (relative.isEmpty()) itemName else "$itemName${File.separator}$relative"
                val size = runCatching { file.length() }.getOrDefault(0L)
                if (!assign(file.path, destinationRelativePath, size)) {
                    return CopyPlanResult.InsufficientSpace(CopyPlanFailure(file.path, size))
                }
            }
        } else {
            val size = runCatching { root.length() }.getOrDefault(item.sizeBytes)
            if (!assign(root.path, itemName, size)) {
                return CopyPlanResult.InsufficientSpace(CopyPlanFailure(item.path, size))
            }
        }
    }

    return CopyPlanResult.Success(records)
}
