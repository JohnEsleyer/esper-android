package com.esper.android

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.room.Room
import com.esper.android.data.export.ZipExporter
import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.model.*
import com.esper.android.data.remote.RemoteClient
import com.esper.android.ui.auth.LoginScreen
import com.esper.android.ui.bookshelf.AllTagsScreen
import com.esper.android.ui.bookshelf.BookshelfScreen
import com.esper.android.ui.bookshelf.CreateNotebookScreen
import com.esper.android.ui.bookshelf.SettingsScreen
import com.esper.android.ui.theme.EsperTheme
import com.esper.android.ui.workspace.QuestionEditorScreen
import com.esper.android.ui.workspace.TabsScreen
import com.esper.android.ui.workspace.WorkspaceScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ActiveScreen {
    BOOKSHELF,
    ALL_TAGS,
    SETTINGS,
    CREATE_NOTEBOOK,
    TABS,
    WORKSPACE
}

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
                var currentScreen by remember { mutableStateOf(ActiveScreen.BOOKSHELF) }
                var previousScreen by remember { mutableStateOf(ActiveScreen.BOOKSHELF) }
                var activeNotebookId by remember { mutableStateOf<String?>(null) }
                var activePageIndex by remember { mutableIntStateOf(0) }
                var activeEditingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
                var selectedTagFilter by remember { mutableStateOf("All") }

                var openTabIds by remember { mutableStateOf<List<String>>(listOf("kepler-001")) }
                var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
                var isOfflineMode by remember { mutableStateOf(false) }

                fun navigateTo(screen: ActiveScreen) {
                    previousScreen = currentScreen
                    currentScreen = screen
                }

                fun popScreen() {
                    currentScreen = previousScreen
                }

                if (currentScreen != ActiveScreen.BOOKSHELF && currentScreen != ActiveScreen.WORKSPACE) {
                    BackHandler {
                        popScreen()
                    }
                }

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

                fun updateDisplayName(newName: String) {
                    val user = auth.currentUser ?: return
                    val request = userProfileChangeRequest { displayName = newName }
                    user.updateProfile(request).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            user.reload().addOnCompleteListener {
                                currentUser = auth.currentUser
                                Toast.makeText(applicationContext, "Display name updated", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(applicationContext, "Update failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
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
                            Toast.makeText(applicationContext, "No ID Token received", Toast.LENGTH_LONG).show()
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
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EsperAuth", "Sign-in exception", e)
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
                    currentScreen = ActiveScreen.BOOKSHELF
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

                val openNotebooks = remember(notebooks, openTabIds) {
                    notebooks.filter { openTabIds.contains(it.id) }
                }

                var customTags by remember {
                    mutableStateOf(listOf("Research", "Astrodynamics", "Physics", "Lectures", "Drafts"))
                }

                val allTags = remember(notebooks, customTags) {
                    val extracted = notebooks.flatMap { it.tagsCsv.split(",") }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    listOf("All") + (customTags + extracted).distinct()
                }

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
                                    val payload = when (val d = c.data) {
                                        is Map<*, *> -> (d["code"] ?: d["url"] ?: "").toString()
                                        is String -> d
                                        else -> Gson().toJson(d)
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

                when {
                    currentUser == null && !isOfflineMode -> {
                        LoginScreen(
                            onSignInWithGoogle = { launchGoogleSignIn() },
                            onContinueOffline = { isOfflineMode = true }
                        )
                    }

                    currentScreen == ActiveScreen.ALL_TAGS -> {
                        AllTagsScreen(
                            allTags = allTags,
                            notebooks = notebooks,
                            selectedTag = selectedTagFilter,
                            onSelectTag = { tag -> selectedTagFilter = tag },
                            onCreateTag = { newTag ->
                                if (!customTags.contains(newTag)) customTags = customTags + newTag
                            },
                            onRenameTag = { oldTag, newTag ->
                                customTags = customTags.map { if (it == oldTag) newTag else it }
                                if (selectedTagFilter == oldTag) selectedTagFilter = newTag
                            },
                            onDeleteTag = { tag ->
                                customTags = customTags.filter { it != tag }
                                if (selectedTagFilter == tag) selectedTagFilter = "All"
                            },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.TABS -> {
                        TabsScreen(
                            openNotebooks = openNotebooks,
                            activeNotebookId = activeNotebookId ?: "",
                            onSelectTab = { selectedId ->
                                activeNotebookId = selectedId
                                activePageIndex = 0
                                navigateTo(ActiveScreen.WORKSPACE)
                            },
                            onCloseTab = { closedId ->
                                val remaining = openTabIds - closedId
                                openTabIds = remaining
                                if (activeNotebookId == closedId) {
                                    activeNotebookId = remaining.lastOrNull()
                                    activePageIndex = 0
                                }
                            },
                            onCloseAllTabs = {
                                openTabIds = emptyList()
                                activeNotebookId = null
                                navigateTo(ActiveScreen.BOOKSHELF)
                            },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.SETTINGS -> {
                        SettingsScreen(
                            currentUser = currentUser,
                            currentDisplayName = currentUser?.displayName ?: "Esper User",
                            notebookCount = notebooks.size,
                            shelfCount = shelves.size,
                            onUpdateDisplayName = { newName -> updateDisplayName(newName) },
                            onExportZip = {
                                val fileName = ZipExporter.exportToDownloads(applicationContext, allDetails)
                                Toast.makeText(applicationContext, "Saved: $fileName", Toast.LENGTH_LONG).show()
                            },
                            onSyncNow = { syncWithCloud() },
                            onSignOut = { signOut() },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.CREATE_NOTEBOOK -> {
                        CreateNotebookScreen(
                            onSaveNotebook = { title, desc, tags, preset, coverImage ->
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
                                    openTabIds = openTabIds + newId
                                    activeNotebookId = newId
                                    activePageIndex = 0
                                    navigateTo(ActiveScreen.WORKSPACE)
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.createNote(mapOf("id" to newId, "title" to title, "description" to desc))
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    activeEditingQuestion != null -> {
                        QuestionEditorScreen(
                            question = activeEditingQuestion!!,
                            onNavigateBack = { activeEditingQuestion = null },
                            onSaveAnswer = { qId, ans ->
                                coroutineScope.launch { db.dao().updateQuestionAnswer(qId, ans) }
                            },
                            onUpdateTitle = { qId, newTitle ->
                                coroutineScope.launch { db.dao().updateQuestionTitle(qId, newTitle) }
                            }
                        )
                    }

                    activeNotebookId != null && currentScreen == ActiveScreen.WORKSPACE -> {
                        val details by db.dao().observeNotebookDetails(activeNotebookId!!).collectAsState(initial = null)
                        details?.let { book ->
                            WorkspaceScreen(
                                notebookTitle = book.notebook.title,
                                pages = book.pages,
                                canvases = book.canvases,
                                questions = book.questions,
                                activePageIndex = activePageIndex,
                                onOpenTabsScreen = { navigateTo(ActiveScreen.TABS) },
                                onNavigateBack = {
                                    activeNotebookId = null
                                    navigateTo(ActiveScreen.BOOKSHELF)
                                },
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
                                                api.createPage(book.notebook.id, mapOf("id" to newPageId, "title" to title, "parentId" to parentId))
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
                                        val activePageId = book.pages.getOrNull(activePageIndex)?.id ?: ""
                                        db.dao().upsertCanvas(
                                            CanvasEntity(
                                                id = canvasId,
                                                notebookId = book.notebook.id,
                                                kind = kind,
                                                title = title,
                                                payloadJson = payload,
                                                assignedPagesCsv = activePageId
                                            )
                                        )
                                    }
                                },
                                onDeleteCanvas = { canvasId ->
                                    coroutineScope.launch { db.dao().deleteCanvas(canvasId) }
                                },
                                onUpdateCanvasPayload = { canvasId, payload ->
                                    coroutineScope.launch {
                                        val existing = book.canvases.find { it.id == canvasId }
                                        if (existing != null) db.dao().upsertCanvas(existing.copy(payloadJson = payload))
                                    }
                                },
                                onAssignCanvasToPage = { canvasId ->
                                    coroutineScope.launch {
                                        val existing = book.canvases.find { it.id == canvasId }
                                        val activePageId = book.pages.getOrNull(activePageIndex)?.id
                                        if (existing != null && activePageId != null) {
                                            val assigned = existing.assignedPagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                                            assigned.add(activePageId)
                                            db.dao().upsertCanvas(existing.copy(assignedPagesCsv = assigned.joinToString(",")))
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
                                    coroutineScope.launch { db.dao().deleteQuestion(qId) }
                                },
                                onOpenQuestionEditor = { question ->
                                    activeEditingQuestion = question
                                }
                            )
                        }
                    }

                    else -> {
                        BookshelfScreen(
                            notebooks = notebooks,
                            shelves = shelves,
                            allDetails = allDetails,
                            currentUser = currentUser,
                            selectedTagFilter = selectedTagFilter,
                            onSelectTagFilter = { selectedTagFilter = it },
                            onSignOut = { signOut() },
                            onOpenNotebook = { id ->
                                if (!openTabIds.contains(id)) {
                                    openTabIds = openTabIds + id
                                }
                                activeNotebookId = id
                                activePageIndex = 0
                                navigateTo(ActiveScreen.WORKSPACE)
                            },
                            onDeleteNotebook = { id ->
                                coroutineScope.launch {
                                    db.dao().deleteNotebook(id)
                                    openTabIds = openTabIds - id
                                    launch(Dispatchers.IO) {
                                        try { api.deleteNote(id) } catch (_: Exception) {}
                                    }
                                }
                            },
                            onOpenAllTagsScreen = { navigateTo(ActiveScreen.ALL_TAGS) },
                            onOpenTabsScreen = { navigateTo(ActiveScreen.TABS) },
                            onOpenSettingsScreen = { navigateTo(ActiveScreen.SETTINGS) },
                            onOpenCreateNotebookScreen = { navigateTo(ActiveScreen.CREATE_NOTEBOOK) },
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
                }
            }
        }
    }
}
