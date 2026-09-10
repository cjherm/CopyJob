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

fun directorySize(dir: File): Long =
    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

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
