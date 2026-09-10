package com.akbigchris.copyjob

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

private fun uriStringToFile(uriString: String): File? =
    runCatching { File(URI(uriString)) }.getOrNull()

private fun pickFilesAndDirs(): List<File> {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
    chooser.isMultiSelectionEnabled = true
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun NewJobScreen(onBack: () -> Unit) {
    val items = remember { mutableStateListOf<JobItem>() }
    val coroutineScope = rememberCoroutineScope()

    fun addFiles(files: List<File>) {
        for (file in files) {
            if (!file.exists()) continue
            val path = file.absolutePath
            if (items.any { it.path == path }) continue

            val item = JobItem(path = path, isDirectory = file.isDirectory)
            items.add(item)

            coroutineScope.launch(Dispatchers.IO) {
                val icon = runCatching { loadSystemIcon(file) }.getOrNull()
                val size = runCatching {
                    if (file.isDirectory) directorySize(file) else file.length()
                }.getOrDefault(0L)
                item.icon.value = icon
                item.sizeBytes.value = size
            }
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("New job", style = MaterialTheme.typography.h6)

            var isDragging by remember { mutableStateOf(false) }

            val dropTarget = remember {
                object : DragAndDropTarget {
                    override fun onEntered(event: DragAndDropEvent) {
                        isDragging = true
                    }

                    override fun onExited(event: DragAndDropEvent) {
                        isDragging = false
                    }

                    override fun onEnded(event: DragAndDropEvent) {
                        isDragging = false
                    }

                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        isDragging = false
                        val data = event.dragData()
                        if (data is DragData.FilesList) {
                            addFiles(data.readFiles().mapNotNull(::uriStringToFile))
                            return true
                        }
                        return false
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp)
                    .border(
                        width = if (isDragging) 2.dp else 1.dp,
                        color = if (isDragging) MaterialTheme.colors.primary else Color.Gray,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = dropTarget,
                    ),
            ) {
                if (items.isEmpty()) {
                    Text(
                        "Drag files or folders here",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(items, key = { it.path }) { item ->
                            JobItemRow(item, onRemove = { items.remove(item) })
                            Divider()
                        }
                    }
                }
            }

            val fileCount = items.count { !it.isDirectory }
            val dirCount = items.count { it.isDirectory }
            val totalSize = items.sumOf { it.sizeBytes.value ?: 0L }
            Text(
                "$fileCount file${if (fileCount == 1) "" else "s"}, " +
                    "$dirCount director${if (dirCount == 1) "y" else "ies"} — " +
                    humanReadableSize(totalSize) + " total",
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) {
                        Text("Back")
                    }
                    Button(onClick = {
                        coroutineScope.launch {
                            val picked = withContext(Dispatchers.IO) { pickFilesAndDirs() }
                            addFiles(picked)
                        }
                    }) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun JobItemRow(item: JobItem, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon: ImageBitmap? = item.icon.value
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Box(modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sizeText = item.sizeBytes.value?.let { humanReadableSize(it) } ?: "…"
            Text(
                "${if (item.isDirectory) "DIR" else "FILE"} · $sizeText",
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
            )
        }

        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove")
        }
    }
}
