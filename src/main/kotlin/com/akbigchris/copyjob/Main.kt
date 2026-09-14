package com.akbigchris.copyjob

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

private enum class Screen(val windowSize: DpSize) {
    Start(DpSize(360.dp, 480.dp)),
    New(DpSize(640.dp, 560.dp)),
    Destination(DpSize(640.dp, 560.dp)),
    Summary(DpSize(640.dp, 680.dp)),
    Copy(DpSize(640.dp, 600.dp)),
    OpenFile(DpSize(600.dp, 320.dp)),
    Help(DpSize(480.dp, 340.dp)),
}

fun main() = application {
    var screen by remember { mutableStateOf(Screen.Start) }
    var selectedItems by remember { mutableStateOf<List<SelectedItem>>(emptyList()) }
    var destinationEntries by remember { mutableStateOf<List<DestinationEntry>>(emptyList()) }
    var copyFiles by remember { mutableStateOf<List<CopyFileRecord>>(emptyList()) }
    val windowState = rememberWindowState(
        size = screen.windowSize,
        position = WindowPosition(Alignment.Center),
    )

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
            Screen.New -> NewJobScreen(
                onBack = { navigateTo(Screen.Start) },
                onNext = { items ->
                    selectedItems = items
                    navigateTo(Screen.Destination)
                },
            )
            Screen.Destination -> DestinationScreen(
                selectedItems = selectedItems,
                initialDestinations = destinationEntries,
                onBack = { navigateTo(Screen.New) },
                onNext = { entries ->
                    destinationEntries = entries
                    navigateTo(Screen.Summary)
                },
            )
            Screen.Summary -> SummaryScreen(
                selectedItems = selectedItems,
                destinationEntries = destinationEntries,
                onBack = { navigateTo(Screen.Destination) },
                onStart = {
                    copyFiles = emptyList()
                    navigateTo(Screen.Copy)
                },
            )
            Screen.Copy -> CopyScreen(
                items = selectedItems,
                destinationEntries = destinationEntries,
                initialFiles = copyFiles,
                onDone = { navigateTo(Screen.Start) },
            )
            Screen.OpenFile -> FileSelectionScreen(
                onNext = { parsed ->
                    selectedItems = parsed.items
                    destinationEntries = parsed.destinations
                    copyFiles = parsed.files
                    navigateTo(if (parsed.files.isNotEmpty()) Screen.Copy else Screen.Summary)
                },
                onBack = { navigateTo(Screen.Start) },
            )
            Screen.Help -> PlaceholderScreen(Texts["placeholder.helpTitle"], onBack = { navigateTo(Screen.Start) })
        }
    }
}
