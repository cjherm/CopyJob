package com.akbigchris.copyjob

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun StartScreen(
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onHelp: () -> Unit,
    onExit: () -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CopyJob", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Text(
                "Welcome to CopyJob!\nLet's get the job done!",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                HelpTooltip(HelpTexts["start.new"], modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                        Text("New job")
                    }
                }
                HelpTooltip(HelpTexts["start.open"], modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("Open job")
                    }
                }
                HelpTooltip(HelpTexts["start.help"], modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Button(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                        Text("Help")
                    }
                }
                HelpTooltip(HelpTexts["start.exit"], modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text("Exit")
                    }
                }
            }
        }
    }
}
