package com.esper.android.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.esper.android.data.local.NotebookWithDetails
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Packages all Room-cached notebooks into a standard offline ZIP export.
object ZipExporter {

    fun exportToDownloads(context: Context, notebooks: List<NotebookWithDetails>): String {
        val fileName = "esper-backup-${System.currentTimeMillis() / 1000}.zip"

        val outputStream: OutputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry")
            resolver.openOutputStream(uri) ?: throw IllegalStateException("Cannot open output stream")
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            FileOutputStream(File(downloadsDir, fileName))
        }

        ZipOutputStream(outputStream).use { zip ->
            // 1. Manifest
            zip.putNextEntry(ZipEntry("manifest.json"))
            val manifest = """
                {
                  "version": "2.0.0",
                  "source": "esper-android",
                  "exportedAt": "${System.currentTimeMillis()}",
                  "notebookCount": ${notebooks.size}
                }
            """.trimIndent()
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. Notebooks, Chapters, and HTML simulations
            notebooks.forEachIndexed { bIdx, book ->
                val bookFolder = "books/${bIdx + 1}_${sanitize(book.notebook.title)}/"

                book.pages.forEachIndexed { pIdx, page ->
                    val pagePath = "$bookFolder${pIdx + 1}_${sanitize(page.title)}.md"
                    zip.putNextEntry(ZipEntry(pagePath))
                    zip.write(page.notes.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                book.canvases.forEachIndexed { cIdx, canvas ->
                    if (canvas.kind == "html") {
                        val canvasPath = bookFolder + "visuals/${cIdx + 1}_${sanitize(canvas.title)}.html"
                        zip.putNextEntry(ZipEntry(canvasPath))
                        zip.write(canvas.payloadJson.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
        }

        return fileName
    }

    private fun sanitize(input: String): String =
        input.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40).ifEmpty { "untitled" }
}
