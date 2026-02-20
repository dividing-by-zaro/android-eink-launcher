package com.palma.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

object CoverExtractor {

    fun getOrExtractCover(
        context: Context,
        filePath: String,
        fileType: String,
    ): Bitmap? {
        val bookFile = File(filePath)
        if (!bookFile.exists()) return null

        // Check disk cache
        val cacheFile = cacheFileFor(context, bookFile)
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
        }

        // Extract cover
        val bitmap = when (fileType.lowercase()) {
            "epub" -> extractEpubCover(bookFile)
            "pdf" -> extractPdfCover(bookFile)
            else -> null
        }

        // Cache to disk
        if (bitmap != null) {
            saveToDiskCache(cacheFile, bitmap)
        }

        return bitmap
    }

    fun extractEpubCover(file: File, maxWidth: Int = 300, maxHeight: Int = 450): Bitmap? {
        return try {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val coverHref = findCoverHref(zip, opfPath) ?: return null

                val opfDir = opfPath.substringBeforeLast('/', "")
                val coverPath = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"

                val coverEntry = zip.getEntry(coverPath) ?: return null
                zip.getInputStream(coverEntry).use { stream ->
                    decodeSampledBitmap(stream, maxWidth, maxHeight)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractPdfCover(file: File, maxWidth: Int = 300, maxHeight: Int = 450): Bitmap? {
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null

                renderer.openPage(0).use { page ->
                    val pageAspect = page.width.toFloat() / page.height.toFloat()
                    val targetAspect = maxWidth.toFloat() / maxHeight.toFloat()

                    val (width, height) = if (pageAspect > targetAspect) {
                        maxWidth to (maxWidth / pageAspect).toInt()
                    } else {
                        (maxHeight * pageAspect).toInt() to maxHeight
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- EPUB XML parsing ---

    private fun findOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        zip.getInputStream(entry).use { stream ->
            val parser = newPullParser(stream)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return parser.getAttributeValue(null, "full-path")
                }
            }
        }
        return null
    }

    private fun findCoverHref(zip: ZipFile, opfPath: String): String? {
        val entry = zip.getEntry(opfPath) ?: return null

        var metaCoverId: String? = null
        val manifestItems = mutableMapOf<String, Pair<String, String>>() // id → (href, mediaType)
        var coverImageHref: String? = null

        zip.getInputStream(entry).use { stream ->
            val parser = newPullParser(stream)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                when (parser.name) {
                    "meta" -> {
                        if (parser.getAttributeValue(null, "name") == "cover") {
                            metaCoverId = parser.getAttributeValue(null, "content")
                        }
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                        val properties = parser.getAttributeValue(null, "properties") ?: ""

                        manifestItems[id] = href to mediaType

                        if (properties.contains("cover-image")) {
                            coverImageHref = href
                        }
                    }
                }
            }
        }

        // Strategy 1: EPUB 2 meta → manifest lookup
        metaCoverId?.let { id ->
            manifestItems[id]?.first?.let { return it }
        }

        // Strategy 2: EPUB 3 properties="cover-image"
        coverImageHref?.let { return it }

        // Strategy 3: heuristic — image item with "cover" in id or href
        manifestItems.entries.firstOrNull { (id, pair) ->
            val (href, mediaType) = pair
            mediaType.startsWith("image/") &&
                (id.contains("cover", ignoreCase = true) ||
                    href.contains("cover", ignoreCase = true))
        }?.value?.first?.let { return it }

        return null
    }

    private fun newPullParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser().apply {
            setInput(stream, null)
        }
    }

    // --- Bitmap decoding ---

    private fun decodeSampledBitmap(
        stream: InputStream,
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap? {
        val bytes = stream.readBytes()

        // Pass 1: decode bounds only
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight)

        // Pass 2: decode with subsampling
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, maxWidth: Int, maxHeight: Int): Int {
        var inSampleSize = 1
        if (rawWidth > maxWidth || rawHeight > maxHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while ((halfWidth / inSampleSize) >= maxWidth && (halfHeight / inSampleSize) >= maxHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // --- Disk cache ---

    private fun cacheFileFor(context: Context, bookFile: File): File {
        val key = "${bookFile.absolutePath}:${bookFile.lastModified()}"
        val hash = key.hashCode().toUInt().toString(16)
        val dir = File(context.cacheDir, "covers").also { it.mkdirs() }
        return File(dir, "$hash.jpg")
    }

    private fun saveToDiskCache(cacheFile: File, bitmap: Bitmap) {
        try {
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        } catch (_: Exception) { }
    }
}
