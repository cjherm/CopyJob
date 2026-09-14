package com.akbigchris.copyjob

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
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
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val COPY_BUFFER_SIZE = 64 * 1024
private const val PROGRESS_FLUSH_INTERVAL_MS = 2000L

private enum class CopyUiState { PREPARING, RUNNING, STOPPED, DONE_SUCCESS, DONE_WITH_ERRORS, PLAN_ERROR }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Streams [record]'s source to its destination while hashing, then re-reads the destination to verify. */
private suspend fun copyAndVerify(record: CopyFileRecord): CopyFileRecord {
    return try {
        val source = File(record.sourcePath)
        val destination = File(record.destinationPath)
        destination.parentFile?.mkdirs()

        val sourceDigest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    sourceDigest.update(buffer, 0, read)
                }
            }
        }

        val destinationDigest = MessageDigest.getInstance("SHA-256")
        destination.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                destinationDigest.update(buffer, 0, read)
            }
        }

        val sizesMatch = destination.length() == source.length()
        val sourceHash = sourceDigest.digest()
        val destinationHash = destinationDigest.digest()
        if (sizesMatch && sourceHash.contentEquals(destinationHash)) {
            record.copy(status = FileCopyStatus.VERIFIED, checksum = sourceHash.toHex(), error = null)
        } else {
            record.copy(status = FileCopyStatus.FAILED, error = "Checksum mismatch after copy", checksum = null)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        record.copy(status = FileCopyStatus.FAILED, error = cleanErrorMessage(File(record.sourcePath).path, e))
    }
}

/**
 * [java.io.FileNotFoundException] (and similar) messages are prefixed with the full source path
 * ("<path> (Access is denied)") which, next to the file name already shown in the failures list,
 * pushes the actually useful reason off the end of a truncated single-line row. Strip it.
 * [normalizedSourcePath] must be [File.getPath]-normalized (backslashes on Windows) to match how
 * the JDK renders the path inside these exception messages.
 */
private fun cleanErrorMessage(normalizedSourcePath: String, e: Exception): String {
    val raw = e.message ?: return e.javaClass.simpleName
    return raw.removePrefix(normalizedSourcePath).trim().removeSurrounding("(", ")").ifBlank { raw }
}

@Composable
fun CopyScreen(
    items: List<SelectedItem>,
    destinationEntries: List<DestinationEntry>,
    initialFiles: List<CopyFileRecord>,
    onDone: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val records = remember { mutableListOf<CopyFileRecord>().apply { addAll(initialFiles) } }

    var uiState by remember { mutableStateOf(if (initialFiles.isEmpty()) CopyUiState.PREPARING else CopyUiState.RUNNING) }
    var planErrorMessage by remember { mutableStateOf<String?>(null) }
    var progressSaveFailed by remember { mutableStateOf(false) }
    var totalBytes by remember { mutableStateOf(initialFiles.sumOf { it.sizeBytes }) }
    var totalCount by remember { mutableStateOf(initialFiles.size) }
    var completedBytes by remember {
        mutableStateOf(initialFiles.filter { it.status == FileCopyStatus.VERIFIED }.sumOf { it.sizeBytes })
    }
    var completedCount by remember {
        mutableStateOf(initialFiles.count { it.status == FileCopyStatus.VERIFIED })
    }
    var currentFileName by remember { mutableStateOf("") }
    var failures by remember {
        mutableStateOf(initialFiles.filter { it.status == FileCopyStatus.FAILED })
    }
    var copyJob by remember { mutableStateOf<Job?>(null) }

    fun writeProgress(status: JobCopyStatus) {
        val path = AppPreferences.lastJsonPath ?: return
        val result = runCatching {
            File(path).writeText(buildJobJson(items, destinationEntries, status, records.toList()))
        }
        progressSaveFailed = result.isFailure
    }

    fun runCopy() {
        uiState = CopyUiState.RUNNING
        copyJob = coroutineScope.launch {
            withContext(Dispatchers.IO) {
                var lastFlush = System.currentTimeMillis()
                for (index in records.indices) {
                    ensureActive()
                    val record = records[index]
                    if (record.status == FileCopyStatus.VERIFIED) continue
                    currentFileName = File(record.sourcePath).name
                    val updated = copyAndVerify(record)
                    records[index] = updated
                    if (updated.status == FileCopyStatus.VERIFIED) {
                        completedBytes += updated.sizeBytes
                        completedCount++
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastFlush >= PROGRESS_FLUSH_INTERVAL_MS) {
                        writeProgress(JobCopyStatus.IN_PROGRESS)
                        lastFlush = now
                    }
                }
            }
            currentFileName = ""
            failures = records.filter { it.status == FileCopyStatus.FAILED }
            uiState = if (failures.isEmpty()) CopyUiState.DONE_SUCCESS else CopyUiState.DONE_WITH_ERRORS
            writeProgress(if (failures.isEmpty()) JobCopyStatus.COMPLETED else JobCopyStatus.COMPLETED_WITH_ERRORS)
            copyJob = null
        }
    }

    fun stopCopy() {
        val jobToCancel = copyJob
        copyJob = null
        coroutineScope.launch {
            jobToCancel?.cancelAndJoin()
            currentFileName = ""
            uiState = CopyUiState.STOPPED
            withContext(Dispatchers.IO) { writeProgress(JobCopyStatus.STOPPED) }
        }
    }

    fun retryFailed() {
        for (index in records.indices) {
            if (records[index].status == FileCopyStatus.FAILED) {
                records[index] = records[index].copy(status = FileCopyStatus.PENDING, error = null)
            }
        }
        failures = emptyList()
        runCopy()
    }

    LaunchedEffect(Unit) {
        if (initialFiles.isEmpty()) {
            val plan = withContext(Dispatchers.IO) {
                val capacities = destinationEntries.map { entry ->
                    val usable = runCatching { File(entry.path).usableSpace }.getOrDefault(0L)
                    entry.path to (usable * entry.percent / 100L)
                }
                buildCopyPlan(items, capacities)
            }
            when (plan) {
                is CopyPlanResult.InsufficientSpace -> {
                    planErrorMessage = Texts.get("copy.planErrorMessage", File(plan.failure.sourcePath).name)
                    uiState = CopyUiState.PLAN_ERROR
                    return@LaunchedEffect
                }
                is CopyPlanResult.Success -> {
                    records.addAll(plan.records)
                    totalBytes = records.sumOf { it.sizeBytes }
                    totalCount = records.size
                }
            }
        }
        withContext(Dispatchers.IO) { writeProgress(JobCopyStatus.IN_PROGRESS) }
        runCopy()
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(Texts["copy.title"], style = MaterialTheme.typography.h6)

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (uiState) {
                    CopyUiState.PLAN_ERROR -> Text(
                        planErrorMessage.orEmpty(),
                        color = MaterialTheme.colors.error,
                    )
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (uiState == CopyUiState.PREPARING) {
                                Text(Texts["copy.preparing"])
                            } else {
                                val progress = if (totalBytes > 0) completedBytes.toFloat() / totalBytes.toFloat() else 1f
                                LinearProgressIndicator(
                                    progress = progress.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    Texts.get(
                                        "copy.progressTemplate",
                                        completedCount,
                                        totalCount,
                                        humanReadableSize(completedBytes),
                                        humanReadableSize(totalBytes),
                                    ),
                                )
                                when (uiState) {
                                    CopyUiState.RUNNING -> Text(
                                        Texts.get("copy.currentFileTemplate", currentFileName),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.Gray,
                                    )
                                    CopyUiState.STOPPED -> Text(
                                        Texts.get("copy.statusStopped", completedCount, totalCount),
                                        color = Color.Gray,
                                    )
                                    CopyUiState.DONE_SUCCESS -> Text(
                                        Texts["copy.statusCompleted"],
                                        color = Color(0xFF2E7D32),
                                    )
                                    CopyUiState.DONE_WITH_ERRORS -> Text(
                                        Texts.get("copy.statusCompletedWithErrors", failures.size),
                                        color = MaterialTheme.colors.error,
                                    )
                                    else -> {}
                                }
                                if (progressSaveFailed) {
                                    Text(
                                        Texts["copy.progressSaveFailedMessage"],
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.error,
                                    )
                                }
                                if (failures.isNotEmpty() && uiState != CopyUiState.RUNNING) {
                                    Text(
                                        Texts["copy.failuresHeading"],
                                        style = MaterialTheme.typography.subtitle2,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                                    ) {
                                        FailuresList(failures)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (uiState) {
                        CopyUiState.PLAN_ERROR -> {
                            OutlinedButton(onClick = onDone) { Text(Texts["copy.abandon"]) }
                        }
                        CopyUiState.RUNNING, CopyUiState.PREPARING -> {
                            HelpTooltip(HelpTexts["copy.stop"]) {
                                OutlinedButton(onClick = ::stopCopy, enabled = uiState == CopyUiState.RUNNING) {
                                    Text(Texts["copy.stop"])
                                }
                            }
                        }
                        CopyUiState.STOPPED -> {
                            HelpTooltip(HelpTexts["copy.abandon"]) {
                                OutlinedButton(onClick = onDone) { Text(Texts["copy.abandon"]) }
                            }
                            HelpTooltip(HelpTexts["copy.resume"]) {
                                Button(onClick = ::runCopy) { Text(Texts["copy.resume"]) }
                            }
                        }
                        CopyUiState.DONE_SUCCESS -> {
                            HelpTooltip(HelpTexts["copy.done"]) {
                                Button(onClick = onDone) { Text(Texts["copy.done"]) }
                            }
                        }
                        CopyUiState.DONE_WITH_ERRORS -> {
                            HelpTooltip(HelpTexts["copy.done"]) {
                                OutlinedButton(onClick = onDone) { Text(Texts["copy.done"]) }
                            }
                            HelpTooltip(HelpTexts["copy.retryFailed"]) {
                                Button(onClick = ::retryFailed) { Text(Texts["copy.retryFailed"]) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailuresList(failures: List<CopyFileRecord>) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(8.dp).padding(end = 12.dp),
        ) {
            itemsIndexed(failures, key = { _, record -> record.sourcePath }) { _, record ->
                HelpTooltip(record.sourcePath, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        Texts.get("copy.failureRowTemplate", File(record.sourcePath).name, record.error.orEmpty()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
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
