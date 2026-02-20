package com.seasalt.launcher.data

import android.graphics.Bitmap

data class RecentBook(
    val title: String,
    val filePath: String,
    val fileType: String,
    val progressPercent: Int?,
    val lastAccess: Long,
    val coverBitmap: Bitmap? = null,
) {
    companion object {
        fun parseProgress(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("/")
            if (parts.size != 2) return null
            val current = parts[0].trim().toIntOrNull() ?: return null
            val total = parts[1].trim().toIntOrNull() ?: return null
            if (total <= 0) return null
            return (current * 100) / total
        }
    }
}
