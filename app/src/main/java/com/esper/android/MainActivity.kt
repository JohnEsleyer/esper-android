package com.esper.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.room.Room
import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.bookshelf.BookshelfScreen
import com.esper.android.ui.theme.EsperTheme
import com.esper.android.ui.workspace.WorkspaceScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(applicationContext, EsperDatabase::class.java, "esper.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EsperTheme {
                val coroutineScope = rememberCoroutineScope()
                var activeNotebookId by remember { mutableStateOf<String?>(null) }
                var activePageIndex by remember { mutableIntStateOf(0) }

                // Seed Keplerian Astrodynamics sample data
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        db.dao().upsertNotebook(
                            NotebookEntity(
                                id = "kepler-001",
                                title = "Kepler's Laws of Planetary Motion",
                                description = "Astrodynamics notebook with interactive elliptical simulations.",
                                ownerSub = "*",
                                visibility = "public",
                                isEncrypted = false,
                                coverPreset = "celestial",
                                coverImage = null,
                                isPinned = true,
                                tagsCsv = "Astrodynamics,Physics",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        db.dao().upsertPages(
                            listOf(
                                PageEntity(
                                    id = "p1",
                                    notebookId = "kepler-001",
                                    parentId = null,
                                    title = "Introduction to Celestial Dynamics",
                                    notes = "# Introduction to Celestial Dynamics\n\nWelcome to the **Keplerian Astrodynamics Notebook**.\n\nThis first page runs in pure classic reading mode.",
                                    position = 0
                                ),
                                PageEntity(
                                    id = "p2",
                                    notebookId = "kepler-001",
                                    parentId = null,
                                    title = "First Law: The Elliptic Trajectory",
                                    notes = "# First Law: The Elliptic Trajectory\n\nKepler's First Law: The orbit of a planet is an ellipse with the Sun at one focus.\n\nClick [00:15] to sync with the lecture reference.",
                                    position = 1
                                ),
                                PageEntity(
                                    id = "p2_1",
                                    notebookId = "kepler-001",
                                    parentId = "p2",
                                    title = "Mathematical Proof",
                                    notes = "### Derivation\n\nPolar coordinate representation of the conic:\n\n`r(θ) = p / (1 + e * cos(θ))`",
                                    position = 2
                                )
                            )
                        )
                        db.dao().upsertQuestions(
                            listOf(
                                QuestionEntity(
                                    id = "q1",
                                    notebookId = "kepler-001",
                                    title = "What geometric shape describes closed planetary orbits?",
                                    answer = "An ellipse with the central gravitational mass (the Sun) at one focus.",
                                    position = 0
                                ),
                                QuestionEntity(
                                    id = "q2",
                                    notebookId = "kepler-001",
                                    title = "Why does an orbiting body speed up at perihelion?",
                                    answer = "Conservation of angular momentum dictates that r × v remains constant, meaning smaller radius requires higher orbital velocity.",
                                    position = 1
                                )
                            )
                        )
                    }
                }

                if (activeNotebookId == null) {
                    val notebooks by db.dao().observeAllNotebooks().collectAsState(initial = emptyList())
                    BookshelfScreen(
                        notebooks = notebooks,
                        allDetails = emptyList(),
                        onOpenNotebook = { id ->
                            activeNotebookId = id
                            activePageIndex = 0
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
                            onSaveNotes = { pageId, text ->
                                coroutineScope.launch { db.dao().updatePageNotes(pageId, text) }
                            }
                        )
                    }
                }
            }
        }
    }
}
