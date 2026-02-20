package com.palma.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palma.launcher.data.AppEntry

enum class ContextMenuAction {
    RENAME,
    RESET_NAME,
    HIDE,
    SHOW_HIDDEN,
    APP_INFO,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListSection(
    apps: List<AppEntry>,
    onAppClick: (AppEntry) -> Unit,
    onContextMenuAction: (AppEntry, ContextMenuAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No apps to display",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
        ) {
            items(
                items = apps,
                key = { it.packageName },
            ) { app ->
                AppListItem(
                    app = app,
                    onAppClick = onAppClick,
                    onContextMenuAction = onContextMenuAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    app: AppEntry,
    onAppClick: (AppEntry) -> Unit,
    onContextMenuAction: (AppEntry, ContextMenuAction) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(
                onClick = { onAppClick(app) },
                onLongClick = { showMenu = true },
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = app.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color.White),
        ) {
            if (app.customName != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Reset name",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        showMenu = false
                        onContextMenuAction(app, ContextMenuAction.RESET_NAME)
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Rename",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    showMenu = false
                    onContextMenuAction(app, ContextMenuAction.RENAME)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Hide from home screen",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    showMenu = false
                    onContextMenuAction(app, ContextMenuAction.HIDE)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Show hidden apps",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    showMenu = false
                    onContextMenuAction(app, ContextMenuAction.SHOW_HIDDEN)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "App info",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    showMenu = false
                    onContextMenuAction(app, ContextMenuAction.APP_INFO)
                },
            )
        }
    }
}
