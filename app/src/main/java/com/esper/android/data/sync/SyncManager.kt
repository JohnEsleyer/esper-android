package com.esper.android.data.sync

import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.remote.EsperApiService
import com.google.gson.Gson
import com.esper.android.data.model.CanvasEntity
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.data.model.QuestionEntity
import com.esper.android.data.model.ShelfEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Server ↔ Room reconciliation, extracted from MainActivity.
 * Pure data movement: no Compose state, no toasts. Callers apply UI state
 * from the returned [SyncReport] and surface errors themselves.
 */
class SyncManager(
    private val api: EsperApiService,
    private val db: EsperDatabase,
    private val gson: Gson = Gson(),
) {

    data class SyncReport(
        val remoteIds: Set<String>,
        val noteCount: Int,
    )

    /**
     * Full pull: purge locally-deleted notebooks, upsert remote content,
     * reconcile shelves. Throws on failure (except shelves, which degrade
     * silently as before).
     */
    suspend fun sync(ownerSub: String?): SyncReport {
        val remoteNotes = api.getUserNotes()
        val remoteIds = remoteNotes.map { it.id }.toSet()

        // Step A: Purge local notebooks that were deleted on web
        val localNotebooks = db.dao().getAllNotebooksList()
        localNotebooks.forEach { local ->
            if (!local.id.startsWith("esper-journal") && !remoteIds.contains(local.id)) {
                db.dao().deleteNotebookCascade(local.id)
            }
        }

        // Step C: Upsert remote notebooks and preserve whiteboard JSON
        remoteNotes.forEach { r ->
            db.dao().upsertNotebook(
                NotebookEntity(
                    id = r.id,
                    title = r.title,
                    description = r.description ?: "",
                    ownerSub = ownerSub ?: "*",
                    visibility = r.visibility ?: "public",
                    isPinned = r.isPinned ?: false,
                    tagsCsv = r.tags?.joinToString(",") ?: "",
                    coverPreset = r.coverPreset ?: "celestial",
                    coverImage = r.coverImage,
                    updatedAt = System.currentTimeMillis()
                )
            )
            r.pages?.mapIndexed { idx, p ->
                PageEntity(
                    id = p.id,
                    notebookId = r.id,
                    parentId = p.parentId,
                    title = p.title,
                    notes = p.notes ?: "",
                    position = p.position ?: idx
                )
            }?.let { db.dao().upsertPages(it) }

            r.canvases?.map { c ->
                // Preserve whiteboard elements without truncating to ""
                val payload = when (c.type.lowercase()) {
                    "html" -> {
                        when (val d = c.data) {
                            is String -> d
                            is Map<*, *> -> d["code"]?.toString() ?: gson.toJson(d)
                            else -> gson.toJson(d)
                        }
                    }
                    "youtube" -> {
                        when (val d = c.data) {
                            is String -> d
                            is Map<*, *> -> d["url"]?.toString() ?: gson.toJson(d)
                            else -> gson.toJson(d)
                        }
                    }
                    else -> {
                        // "excalidraw", "draw", "canvas"
                        when (val d = c.data) {
                            is String -> d
                            else -> gson.toJson(d)
                        }
                    }
                }
                CanvasEntity(
                    id = c.id,
                    notebookId = r.id,
                    kind = c.type,
                    title = c.title,
                    payloadJson = payload,
                    assignedPagesCsv = c.assignedPages?.joinToString(",") ?: ""
                )
            }?.let { db.dao().upsertCanvases(it) }

            r.questions?.mapIndexed { idx, q ->
                QuestionEntity(
                    id = q.id,
                    notebookId = r.id,
                    title = q.title,
                    answer = q.answer,
                    position = q.position ?: idx
                )
            }?.let { db.dao().upsertQuestions(it) }
        }

        // Step D: Reconcile shelves (best-effort; never fails the sync)
        try {
            val remoteShelves = api.getShelves()
            val remoteShelfIds = remoteShelves.map { it.id }.toSet()
            val localShelves = db.dao().getAllShelvesList()
            localShelves.forEach { s ->
                if (!remoteShelfIds.contains(s.id)) {
                    db.dao().deleteShelf(s.id)
                }
            }
            remoteShelves.forEach { s ->
                db.dao().upsertShelf(
                    ShelfEntity(
                        id = s.id,
                        title = s.title,
                        description = s.description ?: "",
                        color = s.color ?: "#6366F1",
                        coverPreset = s.coverPreset ?: "midnight",
                        position = s.position ?: 0,
                        notebookIdsCsv = s.notebookIds?.joinToString(",") ?: "",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {
        }

        return SyncReport(remoteIds, remoteNotes.size)
    }

    /**
     * Long-lived SSE reader on `<baseUrl>events`. Any broadcast data line
     * invokes [onEvent]; reconnects with fixed backoff. The returned Job is
     * a child of [scope] — cancel it (or the scope) to stop. Unauthenticated
     * by design (EventSource carries no headers); the follow-up sync
     * authenticates normally.
     */
    fun watchEvents(scope: CoroutineScope, baseUrl: String, onEvent: suspend () -> Unit): Job =
        scope.launch(Dispatchers.IO) {
            val sseClient = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("${baseUrl}events")
                        .build()
                    sseClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("SSE HTTP ${response.code}")
                        val source = response.body?.source() ?: return@use
                        while (isActive && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data:")) onEvent()
                        }
                    }
                } catch (_: Exception) {
                    // Server down or offline: back off, then reconnect.
                }
                try {
                    delay(5000)
                } catch (_: Exception) {
                    break
                }
            }
        }
}
