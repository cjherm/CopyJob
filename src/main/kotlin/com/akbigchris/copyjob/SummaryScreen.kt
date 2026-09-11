package com.akbigchris.copyjob

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class DestinationCalcResult(val path: String, val percent: Int, val cappedBytes: Long) {
    val name: String = File(path).name.ifEmpty { path }
}

@Composable
fun SummaryScreen(
    selectedItems: List<SelectedItem>,
    destinationEntries: List<DestinationEntry>,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var isSummarizing by remember { mutableStateOf(true) }
    var summarizeJob by remember { mutableStateOf<Job?>(null) }
    var items by remember { mutableStateOf<List<SelectedItem>>(emptyList()) }
    var destinationResults by remember { mutableStateOf<List<DestinationCalcResult>>(emptyList()) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    fun summarize() {
        isSummarizing = true
        summarizeJob = coroutineScope.launch {
            val recalculatedItems = withContext(Dispatchers.IO) {
                selectedItems.map { item ->
                    ensureActive()
                    val file = File(item.path)
                    if (item.isDirectory) {
                        val (size, count) = runCatching { directoryStats(file) }.getOrDefault(0L to 0)
                        item.copy(sizeBytes = size, fileCount = count)
                    } else {
                        item.copy(sizeBytes = runCatching { file.length() }.getOrDefault(0L))
                    }
                }
            }
            val recalculatedDestinations = withContext(Dispatchers.IO) {
                destinationEntries.map { entry ->
                    ensureActive()
                    val usable = runCatching { File(entry.path).usableSpace }.getOrDefault(0L)
                    DestinationCalcResult(entry.path, entry.percent, usable * entry.percent / 100L)
                }
            }

            val jsonPath = AppPreferences.lastJsonPath
            updateMessage = if (jsonPath != null) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { File(jsonPath).writeText(buildJobJson(recalculatedItems, destinationEntries)) }
                }
                if (result.isSuccess) {
                    Texts.get("summary.updatedMessage", File(jsonPath).name)
                } else {
                    Texts["summary.updateFailedMessage"]
                }
            } else {
                null
            }

            items = recalculatedItems
            destinationResults = recalculatedDestinations
            isSummarizing = false
            summarizeJob = null
        }
    }

    fun abortSummarize() {
        summarizeJob?.cancel()
        summarizeJob = null
        isSummarizing = false
        onBack()
    }

    LaunchedEffect(Unit) { summarize() }

    val requiredBytes = items.sumOf { it.sizeBytes }

    // Destinations are used in order until their cumulative capacity covers what's required;
    // anything after that point is superfluous and shown greyed out.
    var cumulative = 0L
    val destinationsWithStatus = destinationResults.map { dest ->
        val necessary = cumulative < requiredBytes
        if (necessary) cumulative += dest.cappedBytes
        dest to necessary
    }
    val totalAvailableBytes = destinationResults.sumOf { it.cappedBytes }
    val hasEnoughSpace = totalAvailableBytes >= requiredBytes

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(Texts["summary.title"], style = MaterialTheme.typography.h6)

                Text(
                    Texts["summary.itemsHeading"],
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                ) {
                    ItemsList(items)
                }
                Text(
                    Texts.get("summary.totalSize", humanReadableSize(requiredBytes)),
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Text(
                    Texts["summary.destinationsHeading"],
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                ) {
                    DestinationResultsList(destinationsWithStatus)
                }
                Text(
                    Texts.get(
                        "summary.totalAvailable",
                        humanReadableSize(totalAvailableBytes),
                        if (hasEnoughSpace) Texts["common.enoughSpace"] else Texts["common.notEnoughSpace"],
                    ),
                    style = MaterialTheme.typography.caption,
                    color = if (hasEnoughSpace) Color(0xFF2E7D32) else MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpTooltip(HelpTexts["summary.back"]) {
                            OutlinedButton(onClick = onBack) {
                                Text(Texts["summary.back"])
                            }
                        }
                        HelpTooltip(HelpTexts["summary.start"]) {
                            Button(onClick = onStart, enabled = hasEnoughSpace) {
                                Text(Texts["summary.start"])
                            }
                        }
                    }
                }

                val message = updateMessage
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (isSummarizing) {
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
                            Text(Texts["summary.summarizingOverlay"], style = MaterialTheme.typography.subtitle1)
                            CircularProgressIndicator()
                            HelpTooltip(HelpTexts["summary.abortSummarize"]) {
                                OutlinedButton(onClick = ::abortSummarize) {
                                    Text(Texts["summary.abortSummarize"])
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
private fun ItemsList(items: List<SelectedItem>) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(8.dp).padding(end = 12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.path }) { index, item ->
                HelpTooltip(item.path, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.subtitle2,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            File(item.path).name.ifEmpty { item.path },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Texts.get(
                                "common.itemInfoTemplate",
                                if (item.isDirectory) Texts["common.typeDirectory"] else Texts["common.typeFile"],
                                humanReadableSize(item.sizeBytes),
                                item.fileCount?.let { " · " + Texts.get("common.fileCountSuffix", it) } ?: "",
                            ),
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray,
                        )
                    }
                }
                Divider()
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

@Composable
private fun DestinationResultsList(destinations: List<Pair<DestinationCalcResult, Boolean>>) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(8.dp).padding(end = 12.dp),
        ) {
            itemsIndexed(destinations, key = { _, (dest, _) -> dest.path }) { index, (dest, necessary) ->
                HelpTooltip(dest.path, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .alpha(if (necessary) 1f else 0.4f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.subtitle2,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            dest.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (necessary) {
                                Texts.get("summary.destinationInfo", dest.percent, humanReadableSize(dest.cappedBytes))
                            } else {
                                Texts["summary.notNecessary"]
                            },
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray,
                        )
                    }
                }
                Divider()
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}
