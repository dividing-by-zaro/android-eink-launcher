package com.palma.launcher.ui

import android.appwidget.AppWidgetHostView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WidgetSection(
    widgetView: AppWidgetHostView? = null,
    onConfigureClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (widgetView != null) {
        AndroidView(
            factory = {
                // Remove from existing parent if it has one
                (widgetView.parent as? ViewGroup)?.removeView(widgetView)
                widgetView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .height(250.dp),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color.White)
                .clickable { onConfigureClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Tap to configure widget",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
