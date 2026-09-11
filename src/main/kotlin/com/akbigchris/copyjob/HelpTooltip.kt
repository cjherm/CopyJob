package com.akbigchris.copyjob

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HelpTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(elevation = 4.dp, shape = RoundedCornerShape(4.dp)) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.caption,
                )
            }
        },
        modifier = modifier,
        delayMillis = 1000,
        content = content,
    )
}
