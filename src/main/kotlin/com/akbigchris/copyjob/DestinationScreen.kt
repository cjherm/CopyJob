package com.akbigchris.copyjob

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToInt

private val rowHeight = 44.dp
private const val DEFAULT_PERCENT = 80

private class DestinationItem(val path: String) {
    val name: String = File(path).name.ifEmpty { path }
    val icon = mutableStateOf<ImageBitmap?>(null)

    /** Percentage of this directory's free space that may be used, editable by the user. */
    val percent = mutableStateOf(DEFAULT_PERCENT)
}

private fun uriStringToFile(uriString: String): File? =
    runCatching { File(URI(uriString)) }.getOrNull()

private fun pickDirectories(): List<File> {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isMultiSelectionEnabled = true
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}

private val saveFileNameFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd'__'HH_mm_ss")

/** e.g. "2026_09_11__18_05_22.json" */
private fun defaultSaveFileName(): String = "${LocalDateTime.now().format(saveFileNameFormatter)}.json"

/** Prompts for a destination .json file, appending the extension and confirming overwrite as needed. */
private fun pickSaveJsonFile(): File? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter(Texts["common.jsonFileFilterDescription"], "json")
    val lastFile = AppPreferences.lastJsonPath?.let(::File)
    val lastDirectory = when {
        lastFile != null && lastFile.isFile -> lastFile.parentFile
        lastFile != null && lastFile.isDirectory -> lastFile
        else -> null
    }
    if (lastDirectory != null) chooser.currentDirectory = lastDirectory
    chooser.selectedFile = if (lastDirectory != null) {
        File(lastDirectory, defaultSaveFileName())
    } else {
        File(defaultSaveFileName())
    }

    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null

    val chosen = chooser.selectedFile
    val file = if (chosen.extension.equals("json", ignoreCase = true)) {
        chosen
    } else {
        File(chosen.parentFile, "${chosen.name}.json")
    }

    if (file.exists()) {
        val overwrite = JOptionPane.showConfirmDialog(
            null,
            Texts.get("destination.overwriteConfirmMessage", file.name),
            Texts["destination.overwriteConfirmTitle"],
            JOptionPane.YES_NO_OPTION,
        ) == JOptionPane.YES_OPTION
        if (!overwrite) return null
    }

    return file
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DestinationScreen(
    selectedItems: List<SelectedItem>,
    initialDestinations: List<DestinationEntry>,
    onBack: () -> Unit,
    onNext: (List<DestinationEntry>) -> Unit,
) {
    val destinations = remember {
        mutableStateListOf<DestinationItem>().apply {
            initialDestinations.forEach { entry ->
                add(DestinationItem(entry.path).also { it.percent.value = entry.percent })
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val requiredBytes = selectedItems.sumOf { it.sizeBytes }

    // Re-load icons for destinations carried over from a previous visit to this screen.
    LaunchedEffect(Unit) {
        for (item in destinations) {
            val file = File(item.path)
            launch(Dispatchers.IO) {
                item.icon.value = runCatching { loadSystemIcon(file) }.getOrNull()
            }
        }
    }

    var isCalculating by remember { mutableStateOf(false) }
    var calculateJob by remember { mutableStateOf<Job?>(null) }
    // Reset whenever the destination set changes, since a previous result no longer applies.
    var availableBytes by remember { mutableStateOf<Long?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    fun addDirectories(files: List<File>) {
        for (file in files) {
            if (!file.isDirectory) continue
            val path = file.absolutePath
            if (destinations.any { it.path == path }) continue

            val item = DestinationItem(path)
            destinations.add(item)
            availableBytes = null

            coroutineScope.launch(Dispatchers.IO) {
                item.icon.value = runCatching { loadSystemIcon(file) }.getOrNull()
            }
        }
    }

    fun removeDirectory(item: DestinationItem) {
        destinations.remove(item)
        availableBytes = null
    }

    fun setPercent(item: DestinationItem, value: Int) {
        item.percent.value = value.coerceIn(0, 100)
        availableBytes = null
    }

    fun resetPercentages() {
        destinations.forEach { it.percent.value = DEFAULT_PERCENT }
        if (destinations.isNotEmpty()) availableBytes = null
    }

    fun calculate() {
        isCalculating = true
        val snapshot = destinations.map { it.path to it.percent.value }
        calculateJob = coroutineScope.launch {
            val total = withContext(Dispatchers.IO) {
                var sum = 0L
                for ((path, percent) in snapshot) {
                    // Checked between directories so Abort takes effect promptly even on slow (e.g. network) drives.
                    ensureActive()
                    val usable = runCatching { File(path).usableSpace }.getOrDefault(0L)
                    sum += usable * percent / 100L
                }
                sum
            }
            availableBytes = total
            isCalculating = false
            calculateJob = null
        }
    }

    fun abortCalculate() {
        calculateJob?.cancel()
        calculateJob = null
        availableBytes = null
        isCalculating = false
    }

    fun save() {
        val file = pickSaveJsonFile() ?: return
        val destinationEntries = destinations.map { DestinationEntry(it.path, it.percent.value) }
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { file.writeText(buildJobJson(selectedItems, destinationEntries)) }
            }
            saveMessage = if (result.isSuccess) {
                AppPreferences.lastJsonPath = file.absolutePath
                Texts.get("destination.savedMessage", file.name)
            } else {
                Texts.get(
                    "destination.saveFailedMessage",
                    result.exceptionOrNull()?.message ?: Texts["destination.saveFailedUnknownError"],
                )
            }
        }
    }

    val hasEnoughSpace = availableBytes?.let { it >= requiredBytes } ?: false

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Texts["destination.title"],
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpTooltip(HelpTexts["destination.resetPercent"]) {
                            OutlinedButton(onClick = ::resetPercentages, enabled = destinations.isNotEmpty()) {
                                Text(Texts["destination.resetPercent"])
                            }
                        }
                        HelpTooltip(HelpTexts["destination.add"]) {
                            Button(onClick = { addDirectories(pickDirectories()) }) {
                                Text(Texts["destination.add"])
                            }
                        }
                    }
                }

                Text(
                    Texts["destination.hint"],
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )

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
                                addDirectories(data.readFiles().mapNotNull(::uriStringToFile))
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
                    if (destinations.isEmpty()) {
                        Text(
                            Texts["destination.dropHint"],
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Gray,
                        )
                    } else {
                        DestinationList(destinations, onRemove = ::removeDirectory, onPercentChange = ::setPercent)
                    }
                }

                Text(
                    Texts.get("destination.spaceRequired", humanReadableSize(requiredBytes)),
                    modifier = Modifier.padding(top = 12.dp),
                )

                val destinationCountText = Texts.get("destination.countSelected", destinations.size)
                Text(
                    when {
                        destinations.isEmpty() -> Texts["destination.statusEmpty"]
                        isCalculating -> Texts.get("destination.statusCalculating", destinationCountText)
                        availableBytes == null -> Texts.get("destination.statusReady", destinationCountText)
                        else -> Texts.get(
                            "destination.statusResult",
                            humanReadableSize(availableBytes!!),
                            if (hasEnoughSpace) Texts["common.enoughSpace"] else Texts["common.notEnoughSpace"],
                        )
                    },
                    color = when {
                        destinations.isEmpty() || isCalculating || availableBytes == null -> Color.Unspecified
                        hasEnoughSpace -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colors.error
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpTooltip(HelpTexts["destination.back"]) {
                            OutlinedButton(onClick = onBack) {
                                Text(Texts["destination.back"])
                            }
                        }
                        HelpTooltip(HelpTexts["destination.calculate"]) {
                            Button(onClick = ::calculate, enabled = destinations.isNotEmpty() && !isCalculating) {
                                Text(Texts["destination.calculate"])
                            }
                        }
                        HelpTooltip(HelpTexts["destination.save"]) {
                            Button(onClick = ::save, enabled = destinations.isNotEmpty()) {
                                Text(Texts["destination.save"])
                            }
                        }
                        HelpTooltip(HelpTexts["destination.next"]) {
                            Button(
                                onClick = {
                                    onNext(destinations.map { DestinationEntry(it.path, it.percent.value) })
                                },
                                enabled = hasEnoughSpace,
                            ) {
                                Text(Texts["destination.next"])
                            }
                        }
                    }
                }

                val message = saveMessage
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (isCalculating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), elevation = 8.dp) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(Texts["destination.calculatingOverlay"], style = MaterialTheme.typography.subtitle1)
                            CircularProgressIndicator()
                            HelpTooltip(HelpTexts["destination.abortCalculate"]) {
                                OutlinedButton(onClick = ::abortCalculate) {
                                    Text(Texts["destination.abortCalculate"])
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationList(
    destinations: SnapshotStateList<DestinationItem>,
    onRemove: (DestinationItem) -> Unit,
    onPercentChange: (DestinationItem, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val draggingItem = remember { mutableStateOf<DestinationItem?>(null) }
    val dragOffset = remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(8.dp).padding(end = 12.dp),
        ) {
            itemsIndexed(destinations, key = { _, item -> item.path }) { index, item ->
                DestinationRow(
                    item = item,
                    index = index,
                    destinations = destinations,
                    rowHeightPx = rowHeightPx,
                    draggingItem = draggingItem,
                    dragOffset = dragOffset,
                    onRemove = { onRemove(item) },
                    onPercentChange = { onPercentChange(item, it) },
                )
                Divider()
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

/**
 * Position during drag is resolved live via `destinations.indexOf(item)` rather than the
 * [index] parameter, since the pointerInput drag-gesture coroutine is only launched once
 * per [item] and would otherwise keep using a stale index after earlier reorders.
 */
@Composable
private fun DestinationRow(
    item: DestinationItem,
    index: Int,
    destinations: SnapshotStateList<DestinationItem>,
    rowHeightPx: Float,
    draggingItem: MutableState<DestinationItem?>,
    dragOffset: MutableState<Float>,
    onRemove: () -> Unit,
    onPercentChange: (Int) -> Unit,
) {
    val isDragged = draggingItem.value === item

    HelpTooltip(
        item.path,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer { translationY = if (isDragged) dragOffset.value else 0f },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}.",
                style = MaterialTheme.typography.subtitle2,
                modifier = Modifier.width(28.dp),
            )

            val icon = item.icon.value
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

            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            PercentEditor(percent = item.percent.value, onPercentChange = onPercentChange)

            Spacer(modifier = Modifier.width(8.dp))

            HelpTooltip(HelpTexts["destination.reorder"]) {
                Text(
                    "⠿⠿",
                    style = MaterialTheme.typography.h6,
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .pointerInput(item.path) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingItem.value = item
                                    dragOffset.value = 0f
                                },
                                onDragEnd = {
                                    draggingItem.value = null
                                    dragOffset.value = 0f
                                },
                                onDragCancel = {
                                    draggingItem.value = null
                                    dragOffset.value = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset.value += amount.y
                                    val from = destinations.indexOf(item)
                                    if (from == -1) return@detectDragGestures
                                    val shift = (dragOffset.value / rowHeightPx).roundToInt()
                                    val target = (from + shift).coerceIn(0, destinations.lastIndex)
                                    if (target != from) {
                                        destinations.removeAt(from)
                                        destinations.add(target, item)
                                        dragOffset.value -= (target - from) * rowHeightPx
                                    }
                                },
                            )
                        },
                )
            }

            HelpTooltip(HelpTexts["destination.removeItem"]) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
private fun PercentEditor(percent: Int, onPercentChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HelpTooltip(HelpTexts["destination.percentDecrease"]) {
            StepButton("−") { onPercentChange(percent - 5) }
        }

        var text by remember(percent) { mutableStateOf(percent.toString()) }
        HelpTooltip(HelpTexts["destination.percent"]) {
            BasicTextField(
                value = text,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }.take(3)
                    text = filtered
                    filtered.toIntOrNull()?.let(onPercentChange)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(28.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
            )
        }

        Text("%", style = MaterialTheme.typography.caption)

        HelpTooltip(HelpTexts["destination.percentIncrease"]) {
            StepButton("+") { onPercentChange(percent + 5) }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.LightGray.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.caption)
    }
}
