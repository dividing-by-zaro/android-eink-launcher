package com.palma.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palma.launcher.data.RecentBook

@Composable
fun LibrarySection(
    recentBooks: List<RecentBook>,
    onAllBooksClick: () -> Unit,
    onBookClick: (RecentBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header row: "All >" on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "All >",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                modifier = Modifier.clickable { onAllBooksClick() },
            )
        }

        // Book covers row
        if (recentBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No recent books",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (book in recentBooks.take(2)) {
                    BookCover(
                        book = book,
                        onClick = { onBookClick(book) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // If only 1 book, fill the second slot with empty space
                if (recentBooks.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun BookCover(
    book: RecentBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .clickable { onClick() },
    ) {
        if (book.coverBitmap != null) {
            Image(
                bitmap = book.coverBitmap.asImageBitmap(),
                contentDescription = book.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(180.dp),
            )
        } else {
            // Placeholder: black border box with title text
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(120.dp)
                    .height(180.dp)
                    .border(1.dp, Color.Black)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // Progress banner overlay at bottom-left
        if (book.progressPercent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${book.progressPercent}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
