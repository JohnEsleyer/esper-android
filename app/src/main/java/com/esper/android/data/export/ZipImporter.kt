package com.esper.android.data.export

import android.content.Context
import android.net.Uri
import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Client-side book package importer for AI-agent generated bundles.
 * Mirrors web bookImporter.js: `.zip` archives (book.json plus chapter
 * markdown files plus visual HTML files) and single-file `.esper` and
 * `.json` books.
 *
 * Everything runs on-device: the VPS only receives the standard JSON
 * records when MainActivity pushes the imported book after this returns.
 */
data class ImportedBook(
    val id: String,
    val title: String,
    val description: String,
    val visibility: String,
    val tagsCsv: String,
    val coverPreset: String,
    val pages: List<PageEntity>,
    val canvases: List<CanvasEntity>,
    val questions: List<QuestionEntity>
)

object ZipImporter {

    suspend fun importPackage(
        context: Context,
        uri: Uri,
        db: EsperDatabase,
        userId: String?
    ): ImportedBook = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot open selected file")
        if (raw.size >= 2 && raw[0] == 0x50.toByte() && raw[1] == 0x4B.toByte()) {
            importZip(raw, db, userId)
        } else {
            importSingleJson(String(raw, Charsets.UTF_8), db, userId)
        }
    }

    private suspend fun importZip(raw: ByteArray, db: EsperDatabase, userId: String?): ImportedBook {
        val files = mutableMapOf<String, String>()
        ZipInputStream(raw.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                val name = entry.name ?: ""
                if (!entry.isDirectory && !name.endsWith("/") &&
                    !name.startsWith("__MACOSX/") && !name.endsWith(".DS_Store")
                ) {
                    if (++count > 2000) throw IllegalArgumentException("Archive has too many entries")
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var n = zip.read(buf)
                    while (n != -1) {
                        out.write(buf, 0, n)
                        if (out.size() > 8 * 1024 * 1024) throw IllegalArgumentException("Entry too large: $name")
                        n = zip.read(buf)
                    }
                    files[name] = out.toString(Charsets.UTF_8.name())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (files.isEmpty()) throw IllegalArgumentException("Archive is empty")

        val metaEntry = files.entries.find { it.key.lowercase().endsWith("book.json") }?.value
        val meta = if (!metaEntry.isNullOrBlank()) {
            try { Gson().fromJson(metaEntry, JsonObject::class.java) } catch (_: Exception) { JsonObject() }
        } else JsonObject()

        val mdFiles = files
            .filter { it.key.lowercase().endsWith(".md") && !it.key.contains("readme", ignoreCase = true) }
            .toList()
            .sortedWith(Comparator { a, b -> naturalCompare(a.first, b.first) })
        if (mdFiles.isEmpty()) throw IllegalArgumentException("Archive contains no chapter markdown files (.md)")

        val newBookId = "doc-${UUID.randomUUID().toString().take(8)}"
        val title = meta.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "Imported Book"
        val desc = meta.get("description")?.takeIf { it.isJsonPrimitive }?.asString ?: "Imported from agent package"
        val visibility = meta.get("visibility")?.takeIf { it.isJsonPrimitive }?.asString ?: "public"
        val preset = meta.get("coverPreset")?.takeIf { it.isJsonPrimitive }?.asString ?: "celestial"
        val tagsCsv = meta.get("tags")?.takeIf { it.isJsonArray }
            ?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            ?.filter { it.isNotBlank() }?.joinToString(",")?.ifEmpty { null } ?: "Imported"

        // Chapters with `NN.M_*.md` subchapter nesting (mirrors web importer).
        val pageIdByNumber = mutableMapOf<Int, String>()
        val pageEntities = mdFiles.mapIndexed { idx, (path, content) ->
            val baseName = path.substringAfterLast("/").removeSuffix(".md").removeSuffix(".MD")
            val cleanTitle = content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.replace(Regex("^[#\\s*_\\->`]+"), "")?.replace(Regex("[#\\s*_\\->`]+$"), "")?.trim()?.take(100)
                ?.ifEmpty { null }
                ?: stripNumericPrefix(baseName).ifEmpty { "Chapter ${idx + 1}" }
            val pageId = "p-${UUID.randomUUID().toString().take(8)}"
            pageIdByNumber[idx + 1] = pageId
            var parentId: String? = null
            Regex("^0*(\\d+)\\.(\\d+)").find(baseName)?.let { m ->
                parentId = pageIdByNumber[m.groupValues[1].toIntOrNull() ?: -1]
            }
            PageEntity(
                id = pageId,
                notebookId = newBookId,
                parentId = parentId,
                title = cleanTitle,
                notes = content,
                position = idx
            )
        }

        // Visuals: every HTML file, optionally scoped via book.json visuals[].
        val visualsMeta = meta.get("visuals")?.takeIf { it.isJsonArray }?.asJsonArray
        val htmlFiles = files.filter { it.key.lowercase().endsWith(".html") && !it.key.lowercase().endsWith("index.html") }
        val canvasEntities = htmlFiles.entries.map { (path, code) ->
            val baseName = path.substringAfterLast("/").removeSuffix(".html").removeSuffix(".HTML")
            var matchedTitle: String? = null
            var assigned: List<String>? = null
            visualsMeta?.forEach { v ->
                val obj = v.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val fileRef = obj.get("file")?.takeIf { it.isJsonPrimitive }?.asString
                if (fileRef != null && path.endsWith(fileRef)) {
                    matchedTitle = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                    obj.get("assignedChapters")?.takeIf { it.isJsonArray }?.asJsonArray?.let { arr ->
                        val resolved = arr.mapNotNull { n ->
                            n.takeIf { it.isJsonPrimitive }?.asInt?.let { pageIdByNumber[it] }
                        }
                        if (resolved.isNotEmpty()) assigned = resolved
                    }
                }
            }
            CanvasEntity(
                id = "c-${UUID.randomUUID().toString().take(8)}",
                notebookId = newBookId,
                kind = "html",
                title = matchedTitle ?: stripNumericPrefix(baseName).ifEmpty { "Simulation" },
                payloadJson = code,
                assignedPagesCsv = (assigned ?: pageEntities.map { it.id }).joinToString(",")
            )
        }

        val questionsMeta = meta.get("questions")?.takeIf { it.isJsonArray }?.asJsonArray
        val questionEntities = (questionsMeta?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject } ?: emptyList())
            .mapIndexed { idx, q ->
                QuestionEntity(
                    id = "q-${UUID.randomUUID().toString().take(8)}",
                    notebookId = newBookId,
                    title = q.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "Review Question ${idx + 1}",
                    answer = q.get("answer")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    position = idx
                )
            }

        db.dao().upsertNotebook(
            NotebookEntity(
                id = newBookId,
                title = title,
                description = desc,
                ownerSub = userId ?: "*",
                visibility = visibility,
                coverPreset = preset,
                tagsCsv = tagsCsv,
                updatedAt = System.currentTimeMillis()
            )
        )
        db.dao().upsertPages(pageEntities)
        if (canvasEntities.isNotEmpty()) db.dao().upsertCanvases(canvasEntities)
        if (questionEntities.isNotEmpty()) db.dao().upsertQuestions(questionEntities)

        return ImportedBook(newBookId, title, desc, visibility, tagsCsv, preset, pageEntities, canvasEntities, questionEntities)
    }

    private suspend fun importSingleJson(text: String, db: EsperDatabase, userId: String?): ImportedBook {
        val root = try {
            Gson().fromJson(text, JsonObject::class.java)
        } catch (_: Exception) {
            throw IllegalArgumentException("File is not valid JSON")
        } ?: throw IllegalArgumentException("File is not valid JSON")
        val pagesArr = root.get("pages")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException("Book file contains no pages")

        val newBookId = "doc-${UUID.randomUUID().toString().take(8)}"
        val title = root.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "Imported Book"
        val desc = root.get("description")?.takeIf { it.isJsonPrimitive }?.asString ?: "Imported from file"
        val visibility = root.get("visibility")?.takeIf { it.isJsonPrimitive }?.asString ?: "public"
        val preset = root.get("coverPreset")?.takeIf { it.isJsonPrimitive }?.asString ?: "celestial"
        val tagsCsv = root.get("tags")?.takeIf { it.isJsonArray }
            ?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            ?.filter { it.isNotBlank() }?.joinToString(",")?.ifEmpty { null } ?: "Imported"

        val pageEntities = pagesArr.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            .mapIndexed { idx, p ->
                PageEntity(
                    id = p.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "p-${UUID.randomUUID().toString().take(8)}",
                    notebookId = newBookId,
                    parentId = p.get("parentId")?.takeIf { it.isJsonPrimitive }?.asString,
                    title = p.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "Chapter ${idx + 1}",
                    notes = p.get("notes")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    position = idx
                )
            }
        if (pageEntities.isEmpty()) throw IllegalArgumentException("Book file contains no pages")

        val canvasEntities = (root.get("canvases")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject } ?: emptyList())
            .map { c ->
                val dataEl = c.get("data")
                CanvasEntity(
                    id = c.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "c-${UUID.randomUUID().toString().take(8)}",
                    notebookId = newBookId,
                    kind = c.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "html",
                    title = c.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "Companion Visual",
                    payloadJson = if (dataEl != null && dataEl.isJsonObject) {
                        val code = dataEl.asJsonObject.get("code")?.takeIf { it.isJsonPrimitive }?.asString
                        code ?: Gson().toJson(dataEl)
                    } else dataEl?.takeIf { it.isJsonPrimitive }?.asString ?: "{}",
                    assignedPagesCsv = c.get("assignedPages")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                        ?.joinToString(",") ?: ""
                )
            }

        val questionEntities = (root.get("questions")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject } ?: emptyList())
            .mapIndexed { idx, q ->
                QuestionEntity(
                    id = q.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "q-${UUID.randomUUID().toString().take(8)}",
                    notebookId = newBookId,
                    title = q.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.ifEmpty { null }
                        ?: "Question ${idx + 1}",
                    answer = q.get("answer")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    position = idx
                )
            }

        db.dao().upsertNotebook(
            NotebookEntity(
                id = newBookId,
                title = title,
                description = desc,
                ownerSub = userId ?: "*",
                visibility = visibility,
                coverPreset = preset,
                tagsCsv = tagsCsv,
                updatedAt = System.currentTimeMillis()
            )
        )
        db.dao().upsertPages(pageEntities)
        if (canvasEntities.isNotEmpty()) db.dao().upsertCanvases(canvasEntities)
        if (questionEntities.isNotEmpty()) db.dao().upsertQuestions(questionEntities)

        return ImportedBook(newBookId, title, desc, visibility, tagsCsv, preset, pageEntities, canvasEntities, questionEntities)
    }

    private fun stripNumericPrefix(name: String): String =
        name.replace(Regex("^[0-9._\\-\\s]+"), "").replace(Regex("[_\\-]+"), " ").trim()

    private fun naturalCompare(a: String, b: String): Int {
        val regex = Regex("\\d+|\\D+")
        val aParts = regex.findAll(a).map { it.value }.toList()
        val bParts = regex.findAll(b).map { it.value }.toList()
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val ap = aParts.getOrNull(i) ?: return -1
            val bp = bParts.getOrNull(i) ?: return 1
            val cmp = if (ap[0].isDigit() && bp[0].isDigit()) {
                ap.toLongOrNull()?.compareTo(bp.toLongOrNull() ?: 0) ?: ap.compareTo(bp)
            } else ap.compareTo(bp, ignoreCase = true)
            if (cmp != 0) return cmp
        }
        return 0
    }
}
