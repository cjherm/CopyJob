package com.akbigchris.copyjob

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private fun isValidJsonFile(path: String): Boolean {
    if (path.isBlank()) return false
    val file = File(path)
    return file.isFile && file.extension.equals("json", ignoreCase = true)
}

private fun pickJsonFile(currentPath: String): String? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
    val currentFile = File(currentPath)
    when {
        currentFile.isFile -> {
            chooser.currentDirectory = currentFile.parentFile
            chooser.selectedFile = currentFile
        }
        currentFile.isDirectory -> chooser.currentDirectory = currentFile
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

@Composable
fun FileSelectionScreen(onNext: () -> Unit, onCancel: () -> Unit) {
    var jsonPath by remember {
        mutableStateOf(AppPreferences.lastJsonPath?.takeIf { isValidJsonFile(it) } ?: "")
    }
    val coroutineScope = rememberCoroutineScope()
    val isValid = isValidJsonFile(jsonPath)

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Select a JSON file to continue")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = jsonPath,
                    onValueChange = { jsonPath = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("JSON file path") },
                )
                OutlinedButton(onClick = {
                    coroutineScope.launch {
                        val chosen = withContext(Dispatchers.IO) { pickJsonFile(jsonPath) }
                        if (chosen != null) {
                            jsonPath = chosen
                        }
                    }
                }) {
                    Text("Browse…")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            AppPreferences.lastJsonPath = jsonPath
                            onNext()
                        },
                        enabled = isValid,
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}
