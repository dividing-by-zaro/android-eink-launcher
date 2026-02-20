package com.palma.launcher.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
                    .height(150.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
            ) {
                val books = recentBooks.take(3).reversed()
                for (book in books) {
                    BookCover(
                        book = book,
                        onClick = { onBookClick(book) },
                    )
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
    val coverShape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(150.dp)
            .clip(coverShape)
            .clickable { onClick() },
    ) {
        if (book.coverBitmap != null) {
            Image(
                bitmap = book.coverBitmap.asImageBitmap(),
                contentDescription = book.title,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(150.dp).clip(coverShape),
            )
        } else {
            // Placeholder: black border box with title text
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(150.dp)
                    .border(1.dp, Color.Black, coverShape)
                    .background(Color.White, coverShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // Progress banner overlay at top-right with chevron bottom
        if (book.progressPercent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp)
                    .width(27.dp)
                    .height(22.dp),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val chevronDepth = 4.dp.toPx()
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, 0f)
                        lineTo(w, h)
                        lineTo(w / 2f, h - chevronDepth)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path, Color.White)
                }
                Text(
                    text = "${book.progressPercent}%",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
