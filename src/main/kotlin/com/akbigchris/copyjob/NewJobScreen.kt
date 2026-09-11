package com.akbigchris.copyjob

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

private enum class SortKey(val labelId: String) {
    Name("newJob.sortByName"),
    Type("newJob.sortByType"),
    Size("newJob.sortBySize"),
}

private fun sortedBy(items: List<JobItem>, sortKey: SortKey): List<JobItem> = when (sortKey) {
    SortKey.Name -> items.sortedBy { it.name.lowercase() }
    SortKey.Type -> items.sortedWith(
        compareByDescending<JobItem> { it.isDirectory }.thenBy { it.name.lowercase() },
    )
    SortKey.Size -> items.sortedWith(
        compareBy<JobItem, Long?>(nullsLast()) { it.sizeBytes.value }.thenBy { it.name.lowercase() },
    )
}

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
fun NewJobScreen(onBack: () -> Unit, onNext: (List<SelectedItem>) -> Unit) {
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
                item.icon.value = icon
                if (file.isDirectory) {
                    val (size, count) = runCatching { directoryStats(file) }.getOrDefault(0L to 0)
                    item.sizeBytes.value = size
                    item.fileCount.value = count
                } else {
                    item.sizeBytes.value = runCatching { file.length() }.getOrDefault(0L)
                }
            }
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Texts["newJob.title"],
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.weight(1f),
                )
                HelpTooltip(HelpTexts["newJob.add"]) {
                    Button(onClick = {
                        coroutineScope.launch {
                            val picked = withContext(Dispatchers.IO) { pickFilesAndDirs() }
                            addFiles(picked)
                        }
                    }) {
                        Text(Texts["newJob.add"])
                    }
                }
            }

            var sortKey by remember { mutableStateOf(SortKey.Name) }
            var sortAscending by remember { mutableStateOf(true) }

            fun onHeaderClick(key: SortKey) {
                if (sortKey == key) {
                    sortAscending = !sortAscending
                } else {
                    sortKey = key
                    sortAscending = true
                }
            }

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
                        Texts["newJob.dropHint"],
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray,
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            SortHeaderLabel(SortKey.Name, HelpTexts["newJob.sortByName"], sortKey, sortAscending) {
                                onHeaderClick(SortKey.Name)
                            }
                            SortHeaderLabel(SortKey.Type, HelpTexts["newJob.sortByType"], sortKey, sortAscending) {
                                onHeaderClick(SortKey.Type)
                            }
                            SortHeaderLabel(SortKey.Size, HelpTexts["newJob.sortBySize"], sortKey, sortAscending) {
                                onHeaderClick(SortKey.Size)
                            }
                        }
                        Divider()

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val listState = rememberLazyListState()
                            val sorted = sortedBy(items, sortKey).let { if (sortAscending) it else it.reversed() }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(8.dp).padding(end = 12.dp),
                            ) {
                                items(sorted, key = { it.path }) { item ->
                                    JobItemRow(item, onRemove = { items.remove(item) })
                                    Divider()
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(listState),
                            )
                        }
                    }
                }
            }

            val fileCount = items.count { !it.isDirectory }
            val dirCount = items.count { it.isDirectory }
            val isCalculating = items.any { it.sizeBytes.value == null }
            val totalSize = items.sumOf { it.sizeBytes.value ?: 0L }
            Text(
                Texts.get(
                    "newJob.statsTemplate",
                    Texts.get("newJob.statsFile", fileCount),
                    Texts.get("newJob.statsDirectory", dirCount),
                    humanReadableSize(totalSize),
                    if (isCalculating) " " + Texts["newJob.statsCalculating"] else "",
                ),
                modifier = Modifier.padding(top = 12.dp),
            )

            val hasCopyableContent = items.isNotEmpty() && items.any { !it.isDirectory || (it.sizeBytes.value ?: 0L) > 0L }
            val nextEnabled = !isCalculating && hasCopyableContent

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpTooltip(HelpTexts["newJob.back"]) {
                        OutlinedButton(onClick = onBack) {
                            Text(Texts["newJob.back"])
                        }
                    }
                    HelpTooltip(HelpTexts["newJob.next"]) {
                        Button(
                            onClick = {
                                onNext(
                                    items.map {
                                        SelectedItem(it.path, it.isDirectory, it.sizeBytes.value ?: 0L, it.fileCount.value)
                                    },
                                )
                            },
                            enabled = nextEnabled,
                        ) {
                            Text(Texts["newJob.next"])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortHeaderLabel(
    key: SortKey,
    helpText: String,
    activeKey: SortKey,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    val active = key == activeKey
    HelpTooltip(helpText) {
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                Texts[key.labelId],
                style = MaterialTheme.typography.caption,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
            if (active) {
                Text(if (ascending) "▲" else "▼", style = MaterialTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun JobItemRow(item: JobItem, onRemove: () -> Unit) {
    HelpTooltip(item.path, modifier = Modifier.fillMaxWidth()) {
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
                val fileCountText = if (item.isDirectory) {
                    item.fileCount.value?.let { count -> " · " + Texts.get("common.fileCountSuffix", count) } ?: ""
                } else {
                    ""
                }
                Text(
                    Texts.get(
                        "common.itemInfoTemplate",
                        if (item.isDirectory) Texts["common.typeDirectory"] else Texts["common.typeFile"],
                        sizeText,
                        fileCountText,
                    ),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                )
            }

            HelpTooltip(HelpTexts["newJob.removeItem"]) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}
