package com.esper.android

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
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
import com.esper.android.ui.workspace.QuestionEditorScreen
import com.esper.android.ui.workspace.WorkspaceScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

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
                var activeEditingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
                var isOfflineMode by remember { mutableStateOf(false) }

                fun updateAuthToken(user: FirebaseUser?) {
                    val prefs = getSharedPreferences("esper_auth_prefs", Context.MODE_PRIVATE)
                    if (user != null) {
                        user.getIdToken(false).addOnSuccessListener { result ->
                            prefs.edit().putString("firebase_jwt_token", result.token).apply()
                        }
                    } else {
                        prefs.edit().remove("firebase_jwt_token").apply()
                    }
                }

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        val idToken = account.idToken
                        if (idToken.isNullOrBlank()) {
                            Toast.makeText(
                                applicationContext,
                                "Google Sign-In failed: No ID Token. Verify SHA-1 in Firebase Console.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@rememberLauncherForActivityResult
                        }

                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val user = auth.currentUser
                                currentUser = user
                                isOfflineMode = false
                                updateAuthToken(user)
                                Toast.makeText(applicationContext, "Signed in as ${user?.displayName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    applicationContext,
                                    "Firebase auth failed: ${authTask.exception?.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: ApiException) {
                        Log.e("EsperAuth", "Google Sign-In ApiException code: ${e.statusCode}", e)
                        val message = when (e.statusCode) {
                            10 -> "Developer Error (10): Missing debug SHA-1 fingerprint in Firebase Console."
                            7 -> "Network error (7): Please check your internet connection."
                            12500 -> "Sign-in failed (12500): Check Play Services config."
                            else -> "Google Sign-In failed (${e.statusCode}): ${e.localizedMessage ?: "Canceled"}"
                        }
                        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("EsperAuth", "Unexpected auth error", e)
                        Toast.makeText(applicationContext, "Authentication failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }

                fun launchGoogleSignIn() {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(this@MainActivity, gso)
                    client.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(client.signInIntent)
                    }
                }

                fun signOut() {
                    auth.signOut()
                    currentUser = null
                    isOfflineMode = false
                    updateAuthToken(null)
                    Toast.makeText(applicationContext, "Signed out", Toast.LENGTH_SHORT).show()
                }

                LaunchedEffect(Unit) {
                    auth.addAuthStateListener { firebaseAuth ->
                        currentUser = firebaseAuth.currentUser
                        updateAuthToken(firebaseAuth.currentUser)
                    }
                    updateAuthToken(auth.currentUser)
                }

                val notebooks by db.dao().observeAllNotebooks().collectAsState(initial = emptyList())
                val shelves by db.dao().observeAllShelves().collectAsState(initial = emptyList())
                val allDetails by db.dao().observeAllNotebooksWithDetails().collectAsState(initial = emptyList())

                // Seed demo notebook on first run
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
                                        notes = "# Introduction to Celestial Dynamics\n\nWelcome to **Esper** on Android!\n",
                                        position = 0
                                    )
                                )
                            )
                        }
                    }
                }

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
                                        ownerSub = currentUser?.uid ?: "*",
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
                                Toast.makeText(applicationContext, "Synced ${remoteNotes.size} notebooks", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Sync failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // SCREEN NAVIGATION TREE
                when {
                    // 1. Not signed in and offline mode not active -> show Login Screen
                    currentUser == null && !isOfflineMode -> {
                        LoginScreen(
                            onSignInWithGoogle = { launchGoogleSignIn() },
                            onContinueOffline = { isOfflineMode = true }
                        )
                    }

                    // 2. Question Editor View
                    activeEditingQuestion != null -> {
                        QuestionEditorScreen(
                            question = activeEditingQuestion!!,
                            onNavigateBack = { activeEditingQuestion = null },
                            onSaveAnswer = { qId, ans ->
                                coroutineScope.launch {
                                    db.dao().updateQuestionAnswer(qId, ans)
                                }
                            },
                            onUpdateTitle = { qId, newTitle ->
                                coroutineScope.launch {
                                    db.dao().updateQuestionTitle(qId, newTitle)
                                }
                            }
                        )
                    }

                    // 3. Bookshelf View
                    activeNotebookId == null -> {
                        BookshelfScreen(
                            notebooks = notebooks,
                            shelves = shelves,
                            allDetails = allDetails,
                            currentUser = currentUser,
                            onSignOut = { signOut() },
                            onOpenNotebook = { id ->
                                activeNotebookId = id
                                activePageIndex = 0
                            },
                            onCreateNotebook = { title, desc, tags, preset, coverImage ->
                                coroutineScope.launch {
                                    val newId = "doc-${UUID.randomUUID().toString().take(8)}"
                                    val book = NotebookEntity(
                                        id = newId,
                                        title = title,
                                        description = desc,
                                        ownerSub = currentUser?.uid ?: "*",
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
                            onCreateShelf = { title, desc, color ->
                                coroutineScope.launch {
                                    val sId = "s-${UUID.randomUUID().toString().take(8)}"
                                    db.dao().upsertShelf(
                                        ShelfEntity(id = sId, title = title, description = desc, color = color)
                                    )
                                    launch(Dispatchers.IO) {
                                        try { api.createShelf(mapOf("id" to sId, "title" to title, "color" to color)) } catch (_: Exception) {}
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
                            onSyncWithRemote = { syncWithCloud() }
                        )
                    }

                    // 4. Workspace View
                    else -> {
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
                }

                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = { Text("Profile & Settings", color = TextPrimary) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "User: ${currentUser?.displayName ?: currentUser?.email ?: "Offline mode"}",
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Base API: ${RemoteClient.BASE_URL}",
                                    color = TextMuted
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
