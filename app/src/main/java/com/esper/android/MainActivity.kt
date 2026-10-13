package com.esper.android

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.room.Room
import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.model.*
import com.esper.android.data.remote.RemoteClient
import com.esper.android.ui.bookshelf.BookshelfScreen
import com.esper.android.ui.theme.EsperTheme
import com.esper.android.ui.workspace.WorkspaceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(applicationContext, EsperDatabase::class.java, "esper.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val api by lazy { RemoteClient.getService(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EsperTheme {
                val coroutineScope = rememberCoroutineScope()
                var activeNotebookId by remember { mutableStateOf<String?>(null) }
                var activePageIndex by remember { mutableIntStateOf(0) }

                val notebooks by db.dao().observeAllNotebooks().collectAsState(initial = emptyList())
                val shelves by db.dao().observeAllShelves().collectAsState(initial = emptyList())
                val allDetails by db.dao().observeAllNotebooksWithDetails().collectAsState(initial = emptyList())

                // Cloud Sync logic
                fun syncWithCloud() {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val remoteNotes = api.getUserNotes()
                            remoteNotes.forEach { r ->
                                db.dao().upsertNotebook(
                                    NotebookEntity(
                                        id = r.id,
                                        title = r.title,
                                        description = r.description ?: "",
                                        ownerSub = "*",
                                        visibility = r.visibility ?: "public",
                                        isPinned = r.isPinned ?: false,
                                        tagsCsv = r.tags?.joinToString(",") ?: "",
                                        coverPreset = r.coverPreset ?: "celestial"
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
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Synced ${remoteNotes.size} notebooks from Cloud", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Offline or API Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // Initial Seed if database empty
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        val existing = db.dao().getNotebookById("kepler-001")
                        if (existing == null) {
                            db.dao().upsertNotebook(
                                NotebookEntity(
                                    id = "kepler-001",
                                    title = "Kepler's Laws of Planetary Motion",
                                    description = "Astrodynamics notebook with interactive elliptical simulations.",
                                    ownerSub = "*",
                                    visibility = "public",
                                    tagsCsv = "Astrodynamics,Physics",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            db.dao().upsertPages(
                                listOf(
                                    PageEntity(
                                        id = "p1",
                                        notebookId = "kepler-001",
                                        title = "Introduction to Celestial Dynamics",
                                        notes = "# Introduction to Celestial Dynamics\n\nWelcome to **Esper** on Android with full KaTeX math!\n\n$$\n\\frac{d^2\\mathbf{r}}{dt^2} = -\\frac{\\mu}{r^3}\\mathbf{r}\n$$\n\nPolar coordinate representation of the conic:\n\$r(\\theta) = \\frac{p}{1 + e \\cos(\\theta)}\$\n\n![Cosmos](https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800)\n\nClick [00:15] to sync simulation.",
                                        position = 0
                                    )
                                )
                            )
                        }
                    }
                }

                if (activeNotebookId == null) {
                    BookshelfScreen(
                        notebooks = notebooks,
                        shelves = shelves,
                        allDetails = allDetails,
                        onOpenNotebook = { id ->
                            activeNotebookId = id
                            activePageIndex = 0
                        },
                        onCreateNotebook = { title, desc, tags, preset ->
                            coroutineScope.launch {
                                val newId = "doc-${UUID.randomUUID().toString().take(8)}"
                                val book = NotebookEntity(
                                    id = newId,
                                    title = title,
                                    description = desc,
                                    ownerSub = "*",
                                    coverPreset = preset,
                                    tagsCsv = tags,
                                    updatedAt = System.currentTimeMillis()
                                )
                                db.dao().upsertNotebook(book)
                                db.dao().upsertPage(
                                    PageEntity(
                                        id = "p-${UUID.randomUUID().toString().take(8)}",
                                        notebookId = newId,
                                        title = "Introduction",
                                        notes = "# $title\n\nStart writing notes...",
                                        position = 0
                                    )
                                )
                                // Push to cloud asynchronously
                                launch(Dispatchers.IO) {
                                    try {
                                        api.createNote(mapOf("id" to newId, "title" to title, "description" to desc))
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        onDeleteNotebook = { id ->
                            coroutineScope.launch {
                                db.dao().deleteNotebook(id)
                                launch(Dispatchers.IO) {
                                    try { api.deleteNote(id) } catch (_: Exception) {}
                                }
                            }
                        },
                        onCreateShelf = { title, desc ->
                            coroutineScope.launch {
                                val sId = "s-${UUID.randomUUID().toString().take(8)}"
                                db.dao().upsertShelf(
                                    ShelfEntity(id = sId, title = title, description = desc)
                                )
                                launch(Dispatchers.IO) {
                                    try { api.createShelf(mapOf("id" to sId, "title" to title)) } catch (_: Exception) {}
                                }
                            }
                        },
                        onDeleteShelf = { id ->
                            coroutineScope.launch {
                                db.dao().deleteShelf(id)
                                launch(Dispatchers.IO) {
                                    try { api.deleteShelf(id) } catch (_: Exception) {}
                                }
                            }
                        },
                        onSyncWithRemote = { syncWithCloud() },
                        onConfigureAuth = { token ->
                            val prefs = getSharedPreferences("esper_auth_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("clerk_jwt_token", token).apply()
                            Toast.makeText(applicationContext, "Saved Clerk JWT Token", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    val details by db.dao().observeNotebookDetails(activeNotebookId!!).collectAsState(initial = null)
                    details?.let { book ->
                        WorkspaceScreen(
                            notebookTitle = book.notebook.title,
                            pages = book.pages,
                            canvases = book.canvases,
                            questions = book.questions,
                            activePageIndex = activePageIndex,
                            onNavigateBack = { activeNotebookId = null },
                            onSelectPage = { idx -> activePageIndex = idx },
                            onCreatePage = { title, parentId ->
                                coroutineScope.launch {
                                    val newPageId = "p-${UUID.randomUUID().toString().take(8)}"
                                    db.dao().upsertPage(
                                        PageEntity(
                                            id = newPageId,
                                            notebookId = book.notebook.id,
                                            parentId = parentId,
                                            title = title,
                                            notes = "# $title\n\n",
                                            position = book.pages.size
                                        )
                                    )
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.createPage(
                                                book.notebook.id,
                                                mapOf("id" to newPageId, "title" to title, "parentId" to parentId)
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onDeletePage = { pageId ->
                                coroutineScope.launch {
                                    db.dao().deletePageAndChildren(pageId)
                                    launch(Dispatchers.IO) {
                                        try { api.deletePage(book.notebook.id, pageId) } catch (_: Exception) {}
                                    }
                                }
                            },
                            onSaveNotes = { pageId, text ->
                                coroutineScope.launch {
                                    db.dao().updatePageNotes(pageId, text)
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.patchPage(book.notebook.id, pageId, mapOf("notes" to text))
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onAttachCanvas = { title, kind, payload ->
                                coroutineScope.launch {
                                    val canvasId = "c-${UUID.randomUUID().toString().take(8)}"
                                    db.dao().upsertCanvas(
                                        CanvasEntity(
                                            id = canvasId,
                                            notebookId = book.notebook.id,
                                            kind = kind,
                                            title = title,
                                            payloadJson = payload
                                        )
                                    )
                                }
                            },
                            onDeleteCanvas = { canvasId ->
                                coroutineScope.launch {
                                    db.dao().deleteCanvas(canvasId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
