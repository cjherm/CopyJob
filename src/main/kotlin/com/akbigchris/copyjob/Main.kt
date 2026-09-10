package com.akbigchris.copyjob

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

private enum class Screen {
    FileSelection,
    Next,
}

fun main() = application {
    var screen by remember { mutableStateOf(Screen.FileSelection) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CopyJob",
        state = rememberWindowState(size = DpSize(600.dp, 280.dp)),
    ) {
        when (screen) {
            Screen.FileSelection -> FileSelectionScreen(
                onNext = { screen = Screen.Next },
                onCancel = ::exitApplication,
            )
            Screen.Next -> NextScreen()
        }
    }
}
