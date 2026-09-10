package com.akbigchris.copyjob

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.filechooser.FileSystemView

class JobItem(val path: String, val isDirectory: Boolean) {
    val name: String = File(path).name.ifEmpty { path }
    val sizeBytes = mutableStateOf<Long?>(null)
    val fileCount = mutableStateOf<Int?>(null)
    val icon = mutableStateOf<ImageBitmap?>(null)
}

fun loadSystemIcon(file: File): ImageBitmap? {
    val icon = FileSystemView.getFileSystemView().getSystemIcon(file) ?: return null
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    icon.paintIcon(null, graphics, 0, 0)
    graphics.dispose()
    return image.toComposeImageBitmap()
}

/** Total size in bytes and count of files (recursively) inside [dir]. */
fun directoryStats(dir: File): Pair<Long, Int> {
    var size = 0L
    var count = 0
    dir.walkTopDown().forEach { file ->
        if (file.isFile) {
            size += file.length()
            count++
        }
    }
    return size to count
}

fun humanReadableSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
