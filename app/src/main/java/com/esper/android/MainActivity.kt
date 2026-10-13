package com.esper.android

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.model.*
import com.esper.android.data.remote.RemoteClient
import com.esper.android.ui.auth.LoginScreen
import com.esper.android.ui.bookshelf.BookshelfScreen
import com.esper.android.ui.theme.CosmicSlate
import com.esper.android.ui.theme.EsperTheme
import com.esper.android.ui.theme.TextMuted
import com.esper.android.ui.theme.TextPrimary
import com.esper.android.ui.theme.TextSecondary
import com.esper.android.ui.workspace.QuestionEditorScreen
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
                val prefs = remember { getSharedPreferences("esper_auth_prefs", Context.MODE_PRIVATE) }

                var isAuthenticated by remember { mutableStateOf(prefs.getBoolean("is_authenticated", false)) }
                var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }

                var activeNotebookId by remember { mutableStateOf<String?>(null) }
                var activePageIndex by remember { mutableIntStateOf(0) }
                var activeEditingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                val notebooks by db.dao().observeAllNotebooks().collectAsState(initial = emptyList())
                val shelves by db.dao().observeAllShelves().collectAsState(initial = emptyList())
                val allDetails by db.dao().observeAllNotebooksWithDetails().collectAsState(initial = emptyList())

                // Cloud Sync logic (always called when online)
                fun syncWithCloud() {
                    if (isOfflineMode) return
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
                                        coverPreset = r.coverPreset ?: "celestial",
                                        coverImage = r.coverImage
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
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Synced ${remoteNotes.size} notebooks from Cloud", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "API Note: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Initial Seed
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
                            db.dao().upsertQuestion(
                                QuestionEntity(
                                    id = "q1",
                                    notebookId = "kepler-001",
                                    title = "What is Kepler's First Law?",
                                    answer = "# Kepler's First Law\n\nThe orbit of a planet is an ellipse with the Sun at one of the two foci:\n$$\nr(\\theta) = \\frac{a(1-e^2)}{1 + e\\cos\\theta}\n$$\nwhere \$a\$ is the semi-major axis and \$e\$ is eccentricity."
                                )
                            )
                        }
                    }
                }

                // 1. Initial Screen: Authentication
                if (!isAuthenticated && !isOfflineMode) {
                    LoginScreen(
                        onContinueOffline = {
                            isOfflineMode = true
                            isAuthenticated = true
                            prefs.edit().putBoolean("is_offline_mode", true).putBoolean("is_authenticated", true).apply()
                        },
                        onAuthSuccess = { token ->
                            prefs.edit()
                                .putString("clerk_jwt_token", token)
                                .putBoolean("is_authenticated", true)
                                .putBoolean("is_offline_mode", false)
                                .apply()
                            isOfflineMode = false
                            isAuthenticated = true
                            Toast.makeText(applicationContext, "Signed in via Clerk!", Toast.LENGTH_SHORT).show()
                            syncWithCloud()
                        }
                    )
                } else if (activeEditingQuestion != null) {
                    // 2. Full-Screen Question Answer KaTeX Editor Screen
                    QuestionEditorScreen(
                        question = activeEditingQuestion!!,
                        onNavigateBack = { activeEditingQuestion = null },
                        onSaveAnswer = { qId, ans ->
                            coroutineScope.launch {
                                db.dao().updateQuestionAnswer(qId, ans)
                                if (!isOfflineMode) {
                                    activeNotebookId?.let { nId ->
                                        try {
                                            api.updateNote(nId, mapOf("questionAnswer" to ans))
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        onUpdateTitle = { qId, newTitle ->
                            coroutineScope.launch {
                                db.dao().updateQuestionTitle(qId, newTitle)
                            }
                        }
                    )
                } else if (activeNotebookId == null) {
                    // 3. Bookshelf Screen
                    LaunchedEffect(Unit) {
                        if (!isOfflineMode) syncWithCloud()
                    }

                    BookshelfScreen(
                        notebooks = notebooks,
                        shelves = shelves,
                        allDetails = allDetails,
                        isOfflineMode = isOfflineMode,
                        onOpenNotebook = { id ->
                            activeNotebookId = id
                            activePageIndex = 0
                            if (!isOfflineMode) syncWithCloud()
                        },
                        onCreateNotebook = { title, desc, tags, preset, coverImage ->
                            coroutineScope.launch {
                                val newId = "doc-${UUID.randomUUID().toString().take(8)}"
                                val book = NotebookEntity(
                                    id = newId,
                                    title = title,
                                    description = desc,
                                    ownerSub = "*",
                                    coverPreset = preset,
                                    coverImage = coverImage,
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
                                if (!isOfflineMode) {
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.createNote(
                                                mapOf(
                                                    "id" to newId,
                                                    "title" to title,
                                                    "description" to desc,
                                                    "coverImage" to coverImage
                                                )
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        onDeleteNotebook = { id ->
                            coroutineScope.launch {
                                db.dao().deleteNotebook(id)
                                if (!isOfflineMode) {
                                    launch(Dispatchers.IO) {
                                        try { api.deleteNote(id) } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        onCreateShelf = { title, desc, color ->
                            coroutineScope.launch {
                                val sId = "s-${UUID.randomUUID().toString().take(8)}"
                                db.dao().upsertShelf(
                                    ShelfEntity(id = sId, title = title, description = desc, color = color)
                                )
                                if (!isOfflineMode) {
                                    launch(Dispatchers.IO) {
                                        try { api.createShelf(mapOf("id" to sId, "title" to title, "color" to color)) } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        onDeleteShelf = { id ->
                            coroutineScope.launch {
                                db.dao().deleteShelf(id)
                                if (!isOfflineMode) {
                                    launch(Dispatchers.IO) {
                                        try { api.deleteShelf(id) } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        onSyncWithRemote = { syncWithCloud() },
                        onLogout = {
                            prefs.edit().clear().apply()
                            isAuthenticated = false
                            isOfflineMode = false
                            activeNotebookId = null
                        },
                        onOpenSettings = { showSettingsDialog = true }
                    )
                } else {
                    // 4. Workspace Screen
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
                                    if (!isOfflineMode) {
                                        launch(Dispatchers.IO) {
                                            try {
                                                api.createPage(
                                                    book.notebook.id,
                                                    mapOf("id" to newPageId, "title" to title, "parentId" to parentId)
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            },
                            onDeletePage = { pageId ->
                                coroutineScope.launch {
                                    db.dao().deletePageAndChildren(pageId)
                                    if (!isOfflineMode) {
                                        launch(Dispatchers.IO) {
                                            try { api.deletePage(book.notebook.id, pageId) } catch (_: Exception) {}
                                        }
                                    }
                                }
                            },
                            onSaveNotes = { pageId, text ->
                                coroutineScope.launch {
                                    db.dao().updatePageNotes(pageId, text)
                                    if (!isOfflineMode) {
                                        launch(Dispatchers.IO) {
                                            try {
                                                api.patchPage(book.notebook.id, pageId, mapOf("notes" to text))
                                            } catch (_: Exception) {}
                                        }
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
                            },
                            onUpdateCanvasPayload = { canvasId, payload ->
                                coroutineScope.launch {
                                    val existing = book.canvases.find { it.id == canvasId }
                                    if (existing != null) {
                                        db.dao().upsertCanvas(existing.copy(payloadJson = payload))
                                    }
                                }
                            },
                            onCreateQuestion = { qTitle ->
                                coroutineScope.launch {
                                    val qId = "q-${UUID.randomUUID().toString().take(8)}"
                                    val newQ = QuestionEntity(
                                        id = qId,
                                        notebookId = book.notebook.id,
                                        title = qTitle,
                                        answer = "",
                                        position = book.questions.size
                                    )
                                    db.dao().upsertQuestion(newQ)
                                    activeEditingQuestion = newQ
                                }
                            },
                            onDeleteQuestion = { qId ->
                                coroutineScope.launch {
                                    db.dao().deleteQuestion(qId)
                                }
                            },
                            onOpenQuestionEditor = { question ->
                                activeEditingQuestion = question
                            }
                        )
                    }
                }

                // Profile & Settings Dialog
                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = { Text("Profile & Settings", color = TextPrimary) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Mode: ${if (isOfflineMode) "Offline (Local SQLite DB)" else "Online (Esper REST API)"}",
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Base API: ${RemoteClient.BASE_URL}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Clerk JWKS: ${RemoteClient.CLERK_JWKS_URL}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showSettingsDialog = false }) {
                                Text("Close")
                            }
                        },
                        containerColor = CosmicSlate
                    )
                }
            }
        }
    }
}
