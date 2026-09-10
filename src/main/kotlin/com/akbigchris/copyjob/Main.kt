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

private enum class Screen(val windowSize: DpSize) {
    Start(DpSize(360.dp, 480.dp)),
    New(DpSize(480.dp, 340.dp)),
    OpenFile(DpSize(600.dp, 320.dp)),
    OpenNext(DpSize(480.dp, 340.dp)),
    Help(DpSize(480.dp, 340.dp)),
}

fun main() = application {
    var screen by remember { mutableStateOf(Screen.Start) }
    val windowState = rememberWindowState(size = screen.windowSize)

    fun navigateTo(target: Screen) {
        screen = target
        windowState.size = target.windowSize
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CopyJob",
        state = windowState,
    ) {
        when (screen) {
            Screen.Start -> StartScreen(
                onNew = { navigateTo(Screen.New) },
                onOpen = { navigateTo(Screen.OpenFile) },
                onHelp = { navigateTo(Screen.Help) },
                onExit = ::exitApplication,
            )
            Screen.New -> PlaceholderScreen("New job", onBack = { navigateTo(Screen.Start) })
            Screen.OpenFile -> FileSelectionScreen(
                onNext = { navigateTo(Screen.OpenNext) },
                onBack = { navigateTo(Screen.Start) },
            )
            Screen.OpenNext -> PlaceholderScreen("Open job", onBack = { navigateTo(Screen.Start) })
            Screen.Help -> PlaceholderScreen("Help", onBack = { navigateTo(Screen.Start) })
        }
    }
}
