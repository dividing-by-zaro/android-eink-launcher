package com.palma.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.palma.launcher.data.AppEntry
import com.palma.launcher.data.RecentBook
import com.palma.launcher.data.WeatherData

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    weatherData: WeatherData? = null,
    recentBooks: List<RecentBook> = emptyList(),
    onAppClick: (AppEntry) -> Unit,
    onContextMenuAction: (AppEntry, ContextMenuAction) -> Unit = { _, _ -> },
    onAllBooksClick: () -> Unit = {},
    onBookClick: (RecentBook) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(top = 24.dp),
        ) {
            // Zone 1: Header (pinned)
            HeaderSection(
                weatherData = weatherData,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Zone 2: Library (pinned, full width)
            LibrarySection(
                recentBooks = recentBooks,
                onAllBooksClick = onAllBooksClick,
                onBookClick = onBookClick,
            )

            // Zone 3: App list (scrollable)
            AppListSection(
                apps = apps,
                onAppClick = onAppClick,
                onContextMenuAction = onContextMenuAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
