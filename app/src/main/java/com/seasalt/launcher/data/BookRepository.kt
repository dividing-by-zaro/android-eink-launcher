package com.seasalt.launcher.data

import android.content.Context
import android.net.Uri

object BookRepository {

    private val METADATA_URI: Uri =
        Uri.parse("content://com.onyx.content.database.ContentProvider/Metadata")

    private val PROJECTION = arrayOf(
        "title",
        "nativeAbsolutePath",
        "progress",
        "lastAccess",
        "type",
    )

    fun getRecentBooks(context: Context, limit: Int = 2): List<RecentBook> {
        return try {
            val books = mutableListOf<RecentBook>()
            context.contentResolver.query(
                METADATA_URI,
                PROJECTION,
                "lastAccess IS NOT NULL",
                null,
                "lastAccess DESC",
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex("title")
                val pathIdx = cursor.getColumnIndex("nativeAbsolutePath")
                val progressIdx = cursor.getColumnIndex("progress")
                val lastAccessIdx = cursor.getColumnIndex("lastAccess")
                val typeIdx = cursor.getColumnIndex("type")

                while (cursor.moveToNext() && books.size < limit) {
                    val title = cursor.getString(titleIdx) ?: "Unknown"
                    val filePath = cursor.getString(pathIdx) ?: continue
                    val progress = cursor.getString(progressIdx)
                    val lastAccess = cursor.getLong(lastAccessIdx)
                    val type = cursor.getString(typeIdx) ?: ""

                    books.add(
                        RecentBook(
                            title = title,
                            filePath = filePath,
                            fileType = type,
                            progressPercent = RecentBook.parseProgress(progress),
                            lastAccess = lastAccess,
                        )
                    )
                }
            }
            books
        } catch (_: Exception) {
            emptyList()
        }
    }
}
