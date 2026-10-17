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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import com.esper.android.data.export.ZipExporter
import com.esper.android.data.export.ZipImporter
import com.esper.android.data.local.EsperDatabase
import com.esper.android.data.local.NotebookWithDetails
import com.esper.android.data.model.*
import com.esper.android.data.remote.RemoteClient
import com.esper.android.data.remote.MeLimits
import com.esper.android.ui.auth.LoginScreen
import com.esper.android.ui.bookshelf.AllTagsScreen
import com.esper.android.ui.bookshelf.BookshelfScreen
import com.esper.android.ui.bookshelf.ConfigureNotebookScreen
import com.esper.android.ui.bookshelf.CreateNotebookScreen
import com.esper.android.ui.bookshelf.SettingsScreen
import com.esper.android.ui.components.LocalReadOnlyMode
import com.esper.android.ui.journal.JournalScreen
import com.esper.android.ui.theme.EsperTheme
import com.esper.android.data.remote.GuestTokenStore
import com.esper.android.data.remote.RemoteTabItem
import com.esper.android.data.sync.NetworkMonitor
import com.esper.android.data.sync.PrefsOpStore
import com.esper.android.data.sync.SyncManager
import com.esper.android.data.sync.SyncOp
import com.esper.android.data.sync.WriteQueue
import com.esper.android.ui.workspace.PasswordGate
import com.esper.android.ui.workspace.PasswordGateDialog
import com.esper.android.ui.workspace.QuestionEditorScreen
import com.esper.android.ui.workspace.RecentPageItem
import com.esper.android.ui.workspace.TabItemData
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
    CONFIGURE_NOTEBOOK,
    TABS,
    WORKSPACE,
    JOURNAL
}

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    private val db by lazy {
        Room.databaseBuilder(applicationContext, EsperDatabase::class.java, "esper.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val api by lazy { RemoteClient.getService(applicationContext) }

    // Offline write queue: every mutating call registers its op first and
    // clears it on success. Leftovers replay from Room on reconnect/sync.
    private val writeQueue by lazy {
        WriteQueue(PrefsOpStore(getSharedPreferences("esper_sync", MODE_PRIVATE)))
    }

    // Guest tokens for password-protected notebooks (see GuestTokenStore).
    private val guestTokens by lazy {
        GuestTokenStore(getSharedPreferences("esper_guest", MODE_PRIVATE))
    }

    // Server ↔ Room reconciliation (no Compose state inside).
    private val syncManager by lazy { SyncManager(api, db) }

    private fun canvasDataForUpload(entity: CanvasEntity): Any {
        return try {
            @Suppress("UNCHECKED_CAST")
            (Gson().fromJson(entity.payloadJson, Map::class.java) as? Map<String, Any?>) ?: mapOf("code" to entity.payloadJson)
        } catch (_: Exception) {
            if (entity.kind == "youtube") mapOf("url" to entity.payloadJson)
            else mapOf("code" to entity.payloadJson)
        }
    }

    private fun fullBookPayload(details: NotebookWithDetails): Map<String, Any?> {
        val nb = details.notebook
        return mapOf(
            "title" to nb.title,
            "description" to nb.description,
            "tags" to nb.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            "coverPreset" to nb.coverPreset,
            "coverImage" to nb.coverImage,
            "visibility" to nb.visibility,
            "isPinned" to nb.isPinned,
            "pages" to details.pages.map {
                mapOf("id" to it.id, "parentId" to it.parentId, "title" to it.title, "notes" to it.notes)
            },
            "canvases" to details.canvases.map { c ->
                mapOf(
                    "id" to c.id, "type" to c.kind, "title" to c.title,
                    "assignedPages" to c.assignedPagesCsv.split(",").map { s -> s.trim() }.filter { it.isNotEmpty() },
                    "data" to canvasDataForUpload(c)
                )
            },
            "questions" to details.questions.map { q ->
                mapOf("id" to q.id, "title" to q.title, "answer" to q.answer)
            }
        )
    }

    /**
     * Replays one queued op against the server using current Room content.
     * Returns true when the op is resolved (sent, or provably obsolete).
     * 404 on update/create means the server side is gone → resolve.
     */
    private suspend fun replayOp(op: SyncOp): Boolean {
        return try {
            when (op) {
                is SyncOp.PatchPage -> {
                    val page = db.dao().getPageById(op.pageId) ?: return true
                    api.patchPage(op.docId, op.pageId, mapOf("notes" to page.notes, "title" to page.title))
                }
                is SyncOp.CreatePage -> {
                    val page = db.dao().getPageById(op.pageId) ?: return true
                    try {
                        api.createPage(
                            op.docId,
                            mapOf("id" to page.id, "title" to page.title, "notes" to page.notes, "parentId" to page.parentId)
                        )
                    } catch (e: Exception) {
                        if (e is retrofit2.HttpException && e.code() == 404) {
                            // Notebook itself is gone server-side: re-PUT the whole book.
                            val details = db.dao().getNotebookDetails(op.docId) ?: return true
                            api.updateNote(op.docId, fullBookPayload(details))
                        } else throw e
                    }
                }
                is SyncOp.FullBook -> {
                    val details = db.dao().getNotebookDetails(op.docId) ?: return true
                    try {
                        api.updateNote(op.docId, fullBookPayload(details))
                    } catch (e: Exception) {
                        if (e is retrofit2.HttpException && e.code() == 404) {
                            api.createNote(fullBookPayload(details) + ("id" to op.docId))
                        } else throw e
                    }
                }
                is SyncOp.DeletePage -> {
                    try {
                        api.deletePage(op.docId, op.pageId)
                    } catch (e: Exception) {
                        if (!(e is retrofit2.HttpException && e.code() == 404)) throw e
                    }
                }
                is SyncOp.DeleteBook -> {
                    try {
                        api.deleteNote(op.docId)
                    } catch (e: Exception) {
                        if (!(e is retrofit2.HttpException && e.code() == 404)) throw e
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EsperTheme {
                val coroutineScope = rememberCoroutineScope()
                var screenStack by remember { mutableStateOf(listOf(ActiveScreen.BOOKSHELF)) }
                val currentScreen = screenStack.lastOrNull() ?: ActiveScreen.BOOKSHELF

                var activeNotebookId by remember { mutableStateOf<String?>(null) }
                var activePageIndex by remember { mutableIntStateOf(0) }
                var activeEditingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
                var configuringNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
                var selectedTagFilter by remember { mutableStateOf("All") }

                var openTabItems by remember {
                    mutableStateOf(emptyList<TabItemData>())
                }
                var recentPageItems by remember { mutableStateOf<List<RecentPageItem>>(emptyList()) }
                var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
                var isOfflineMode by remember { mutableStateOf(false) }

                // Single source of truth for connectivity-driven read-only mode:
                // offline gateway mode OR no validated network => whole app is read-only.
                val isNetworkOnline by NetworkMonitor.observe(applicationContext)
                    .collectAsState(initial = true)
                val isReadOnly = !isNetworkOnline || isOfflineMode

                fun persistTabsToServer(items: List<TabItemData>) {
                    if (currentUser == null) return
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val payload = items.map {
                                RemoteTabItem(
                                    docId = it.notebook.id,
                                    pageId = it.pageId ?: "p1",
                                    pageNumber = it.pageNumber,
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            api.updateUserTabs(mapOf("tabs" to payload))
                        } catch (_: Exception) {}
                    }
                }

                fun navigateTo(screen: ActiveScreen) {
                    if (screen == ActiveScreen.BOOKSHELF) {
                        screenStack = listOf(ActiveScreen.BOOKSHELF)
                    } else if (screenStack.lastOrNull() == screen) {
                        // Already on target screen
                    } else if (screen == ActiveScreen.WORKSPACE && screenStack.contains(ActiveScreen.TABS)) {
                        // Switching tabs replaces the TABS route with WORKSPACE
                        screenStack = listOf(ActiveScreen.BOOKSHELF, ActiveScreen.WORKSPACE)
                    } else {
                        screenStack = screenStack + screen
                    }
                }

                fun popScreen() {
                    if (screenStack.size > 1) {
                        screenStack = screenStack.dropLast(1)
                    } else {
                        screenStack = listOf(ActiveScreen.BOOKSHELF)
                    }
                }

                BackHandler(enabled = screenStack.size > 1) {
                    popScreen()
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
                    screenStack = listOf(ActiveScreen.BOOKSHELF)
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

                // Server-resolved membership tier (GET /api/me). Null = unknown/offline;
                // Settings falls back to visitor-limit placeholders until this resolves.
                var memberType by remember { mutableStateOf<String?>(null) }
                var tierLimits by remember { mutableStateOf<MeLimits?>(null) }

                // Password gate for protected notebooks (server-verified,
                // guest token cached in GuestTokenStore).
                var passwordGate by remember { mutableStateOf<PasswordGate?>(null) }
                var gateBusy by remember { mutableStateOf(false) }
                var gateError by remember { mutableStateOf<String?>(null) }

                // Fetch persisted tabs + membership tier on sign-in
                // (server wins when it has tabs; tier drives Settings quotas).
                LaunchedEffect(currentUser) {
                    if (currentUser != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                val me = api.getMe()
                                withContext(Dispatchers.Main) {
                                    memberType = me.memberType
                                    tierLimits = me.limits
                                }
                            } catch (_: Exception) {}
                            try {
                                val serverTabs = api.getUserTabs()
                                if (serverTabs.isNotEmpty()) {
                                    val allBooks = db.dao().getAllNotebooksList()
                                    val mapped = serverTabs.mapNotNull { st ->
                                        allBooks.find { it.id == st.docId }?.let { nb ->
                                            TabItemData(
                                                notebook = nb,
                                                pageNumber = (st.pageNumber ?: 1).coerceAtLeast(1),
                                                pageId = st.pageId
                                            )
                                        }
                                    }
                                    if (mapped.isNotEmpty()) {
                                        withContext(Dispatchers.Main) { openTabItems = mapped }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        memberType = null
                        tierLimits = null
                    }
                }

                var customTags by remember {
                    mutableStateOf(listOf("Research", "Lectures", "Drafts"))
                }

                val allTags = remember(notebooks, customTags) {
                    val extracted = notebooks.filter { it.id != "esper-journal" }.flatMap { it.tagsCsv.split(",") }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    listOf("All") + (customTags + extracted).distinct()
                }

                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        // Single-book journal seed (deterministic id shared with web).
                        val existingJournal = db.dao().getNotebookById("esper-journal")
                        if (existingJournal == null) {
                            val dateStr = try {
                                java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date())
                            } catch (_: Exception) { "Today" }
                            db.dao().upsertNotebook(
                                NotebookEntity(
                                    id = "esper-journal",
                                    title = "Personal Journal",
                                    description = "Single book dumping ground for quick ideas",
                                    ownerSub = "*",
                                    visibility = "restricted",
                                    tagsCsv = "Journal",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            db.dao().upsertPage(
                                PageEntity(
                                    id = "jp-1",
                                    notebookId = "esper-journal",
                                    title = dateStr,
                                    notes = "# $dateStr\n\nJot down ideas and quick thoughts here...",
                                    position = 0
                                )
                            )
                        }
                    }
                }

                // Last sync outcome for the Settings screen. Null = never attempted
                // or last attempt succeeded; otherwise the short failure reason.
                var lastSyncError by remember { mutableStateOf<String?>(null) }

                /** Replays queued offline writes from Room content. Best-effort:
                 *  failures stay queued for the next pass. */
                fun flushWriteQueue(onDone: (flushed: Int) -> Unit = {}) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val ops = writeQueue.pending()
                        if (ops.isEmpty()) {
                            onDone(0)
                            return@launch
                        }
                        var flushed = 0
                        ops.forEach { op ->
                            if (replayOp(op)) {
                                writeQueue.markClean(op)
                                flushed++
                            }
                        }
                        val done = flushed
                        withContext(Dispatchers.Main) { onDone(done) }
                    }
                }

                fun openBookDirect(id: String) {
                    val existing = openTabItems.find { it.notebook.id == id }
                    if (existing == null) {
                        val nb = notebooks.find { it.id == id }
                        if (nb != null) {
                            val updated = openTabItems + TabItemData(
                                notebook = nb,
                                pageNumber = 1,
                                pageId = null
                            )
                            openTabItems = updated
                            persistTabsToServer(updated)
                        }
                        activeNotebookId = id
                        activePageIndex = 0
                    } else {
                        activeNotebookId = id
                        activePageIndex = (existing.pageNumber - 1).coerceAtLeast(0)
                    }
                    navigateTo(ActiveScreen.WORKSPACE)
                }

                /** Owner bypasses the gate; everyone else needs a cached
                 *  guest token or the book password (verified server-side). */
                fun requestOpenBook(id: String) {
                    val nb = notebooks.find { it.id == id }
                    val locked = nb?.visibility == "protected" &&
                        nb.ownerSub != currentUser?.uid &&
                        guestTokens.get(id) == null
                    if (locked && nb != null) {
                        gateError = null
                        passwordGate = PasswordGate(docId = id, title = nb.title)
                    } else {
                        openBookDirect(id)
                    }
                }

                fun unlockProtected(docId: String, password: String) {
                    gateBusy = true
                    gateError = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val res = api.verifyPassword(docId, mapOf("password" to password))
                            val token = res.guestToken
                                ?: throw IllegalStateException("No guest token issued")
                            guestTokens.put(docId, token)
                            // Pull the now-readable book into Room, then open it.
                            val remote = api.getNote(docId, token)
                            db.dao().upsertNotebook(
                                NotebookEntity(
                                    id = remote.id,
                                    title = remote.title,
                                    description = remote.description ?: "",
                                    ownerSub = currentUser?.uid ?: "*",
                                    visibility = remote.visibility ?: "protected",
                                    isPinned = false,
                                    tagsCsv = remote.tags?.joinToString(",") ?: "",
                                    coverPreset = remote.coverPreset ?: "celestial",
                                    coverImage = remote.coverImage,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            remote.pages?.mapIndexed { idx, p ->
                                PageEntity(
                                    id = p.id, notebookId = remote.id, parentId = p.parentId,
                                    title = p.title, notes = p.notes ?: "", position = p.position ?: idx
                                )
                            }?.let { db.dao().upsertPages(it) }
                            withContext(Dispatchers.Main) {
                                gateBusy = false
                                passwordGate = null
                                openBookDirect(docId)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                gateBusy = false
                                gateError = e.localizedMessage ?: "Unlock failed"
                            }
                        }
                    }
                }

                fun syncWithCloud() {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val report = syncManager.sync(currentUser?.uid)

                            // Step B: Reconcile open tabs
                            withContext(Dispatchers.Main) {
                                openTabItems = openTabItems.filter { report.remoteIds.contains(it.notebook.id) || it.notebook.id == "esper-journal" }
                                if (activeNotebookId != null && !report.remoteIds.contains(activeNotebookId) && activeNotebookId != "esper-journal") {
                                    val next = openTabItems.lastOrNull()
                                    activeNotebookId = next?.notebook?.id
                                    activePageIndex = (next?.pageNumber ?: 1) - 1
                                }
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Synced ${report.noteCount} notebooks", Toast.LENGTH_SHORT).show()
                                lastSyncError = null
                            }
                            // Replay any writes that failed while offline, now that
                            // we know the server is reachable.
                            flushWriteQueue()
                        } catch (e: Exception) {
                            val reason = e.localizedMessage ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Sync failed: $reason", Toast.LENGTH_LONG).show()
                                lastSyncError = reason
                            }
                        }
                    }
                }

                // Real-time: refetch on foreground resume so edits made on
                // web while the app was backgrounded appear immediately.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            if (currentUser != null || isOfflineMode) syncWithCloud()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Re-sync when connectivity returns (heals reads that failed
                // offline; queued writes flush right after the fresh sync).
                LaunchedEffect(Unit) {
                    var wasOnline = true
                    NetworkMonitor.observe(applicationContext).collect { online ->
                        if (online && !wasOnline && currentUser != null) {
                            syncWithCloud()
                            flushWriteQueue()
                        }
                        wasOnline = online
                    }
                }

                // Real-time: long-lived SSE reader. Any broadcast data line
                // triggers a background sync; reconnects with backoff.
                // Derived from the effective base URL so local dev still works.
                LaunchedEffect(Unit) {
                    syncManager.watchEvents(this, RemoteClient.baseUrl(applicationContext)) {
                        syncWithCloud()
                    }
                }

                CompositionLocalProvider(LocalReadOnlyMode provides isReadOnly) {
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

                    currentScreen == ActiveScreen.JOURNAL -> {
                        val journalDetails by db.dao().observeNotebookDetails("esper-journal").collectAsState(initial = null)
                        val journalPages = remember(journalDetails) {
                            (journalDetails?.pages ?: emptyList()).sortedBy { it.position }
                        }
                        JournalScreen(
                            pages = journalPages,
                            onSavePageNotes = { pageId, text ->
                                if (isReadOnly) return@JournalScreen
                                coroutineScope.launch {
                                    val derivedTitle = deriveChapterTitle(text)
                                    db.dao().updatePageNotesAndTitle(pageId, text, derivedTitle)
                                    db.dao().updateNotebookUpdatedAt("esper-journal", System.currentTimeMillis())
                                    val journalOp = SyncOp.PatchPage("esper-journal", pageId)
                                    writeQueue.markDirty(journalOp)
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.patchPage("esper-journal", pageId, mapOf("notes" to text, "title" to derivedTitle))
                                            writeQueue.markClean(journalOp)
                                        } catch (e: Exception) {
                                            Log.w("EsperSync", "journal patch failed for $pageId", e)
                                        }
                                    }
                                }
                            },
                            onCreateNewPage = { dateTitle ->
                                if (isReadOnly) return@JournalScreen
                                coroutineScope.launch {
                                    val newPageId = "jp-${UUID.randomUUID().toString().take(8)}"
                                    db.dao().upsertPage(
                                        PageEntity(
                                            id = newPageId,
                                            notebookId = "esper-journal",
                                            title = dateTitle,
                                            notes = "# $dateTitle\n\n",
                                            position = journalPages.size
                                        )
                                    )
                                    db.dao().updateNotebookUpdatedAt("esper-journal", System.currentTimeMillis())
                                    val journalCreateOp = SyncOp.CreatePage("esper-journal", newPageId)
                                    writeQueue.markDirty(journalCreateOp)
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.createPage(
                                                "esper-journal",
                                                mapOf(
                                                    "id" to newPageId,
                                                    "title" to dateTitle,
                                                    "notes" to "# $dateTitle\n\n"
                                                )
                                            )
                                            writeQueue.markClean(journalCreateOp)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onDeletePage = { pageId ->
                                if (isReadOnly) return@JournalScreen
                                coroutineScope.launch {
                                    db.dao().deletePageAndChildren(pageId)
                                    val delOp = SyncOp.DeletePage("esper-journal", pageId)
                                    writeQueue.markDirty(delOp)
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.deletePage("esper-journal", pageId)
                                            writeQueue.markClean(delOp)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.TABS -> {
                        // Resolve tab entities against loaded books; drop tabs for deleted books.
                        val resolvedTabs = remember(openTabItems, notebooks) {
                            openTabItems.filter { item ->
                                notebooks.isEmpty() || notebooks.any { it.id == item.notebook.id }
                            }.map { item ->
                                val nb = notebooks.find { it.id == item.notebook.id } ?: item.notebook
                                item.copy(notebook = nb)
                            }
                        }
                        val resolvedRecent = remember(recentPageItems, notebooks) {
                            recentPageItems.filter { item ->
                                notebooks.isEmpty() || notebooks.any { it.id == item.notebook.id }
                            }.map { item ->
                                val nb = notebooks.find { it.id == item.notebook.id } ?: item.notebook
                                item.copy(notebook = nb)
                            }
                        }

                        fun selectTab(docId: String, pageIndex: Int) {
                            val details = allDetails.find { it.notebook.id == docId }
                            val pages = details?.pages ?: emptyList()
                            val safeIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                            val page = pages.getOrNull(safeIndex)
                            activeNotebookId = docId
                            activePageIndex = safeIndex
                            openTabItems = openTabItems.map { item ->
                                if (item.notebook.id == docId) {
                                    item.copy(
                                        pageNumber = safeIndex + 1,
                                        pageId = page?.id ?: item.pageId
                                    )
                                } else item
                            }.let {
                                // Ensure the selected book is a tab.
                                if (it.any { t -> t.notebook.id == docId }) it
                                else {
                                    val nb = notebooks.find { n -> n.id == docId }
                                        ?: details?.notebook
                                    if (nb != null) {
                                        it + TabItemData(
                                            notebook = nb,
                                            pageNumber = safeIndex + 1,
                                            pageId = page?.id
                                        )
                                    } else it
                                }
                            }
                            persistTabsToServer(openTabItems)
                            navigateTo(ActiveScreen.WORKSPACE)
                        }

                        TabsScreen(
                            openTabs = resolvedTabs,
                            recentPages = resolvedRecent,
                            activeNotebookId = activeNotebookId ?: "",
                            onSelectTab = { docId, pageIndex -> selectTab(docId, pageIndex) },
                            onCloseTab = { closedId ->
                                val remaining = openTabItems.filter { it.notebook.id != closedId }
                                openTabItems = remaining
                                persistTabsToServer(remaining)
                                if (activeNotebookId == closedId) {
                                    val next = remaining.lastOrNull()
                                    activeNotebookId = next?.notebook?.id
                                    activePageIndex = (next?.pageNumber ?: 1) - 1
                                    if (activeNotebookId == null) {
                                        navigateTo(ActiveScreen.BOOKSHELF)
                                    }
                                }
                            },
                            onCloseAllTabs = {
                                openTabItems = emptyList()
                                activeNotebookId = null
                                persistTabsToServer(emptyList())
                                navigateTo(ActiveScreen.BOOKSHELF)
                            },
                            onNewTabClick = {
                                // Direct user to Bookshelf / shelf selection to pick another book
                                navigateTo(ActiveScreen.BOOKSHELF)
                            },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.SETTINGS -> {
                        SettingsScreen(
                            currentUser = currentUser,
                            currentDisplayName = currentUser?.displayName ?: "Esper User",
                            memberType = memberType,
                            maxNotebooks = tierLimits?.max_notebooks,
                            maxShelves = tierLimits?.max_shelves,
                            inactivityDays = tierLimits?.inactivity_days,
                            notebookCount = notebooks.size,
                            shelfCount = shelves.size,
                            lastSyncError = lastSyncError,
                            apiBaseUrl = RemoteClient.baseUrl(applicationContext),
                            onApiBaseUrlChange = { url ->
                                RemoteClient.setBaseUrlOverride(applicationContext, url)
                                Toast.makeText(
                                    applicationContext,
                                    "Endpoint updated — restart the app to reconnect",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onUpdateDisplayName = { newName ->
                                if (isReadOnly) return@SettingsScreen
                                updateDisplayName(newName)
                            },
                            onExportZip = {
                                val fileName = ZipExporter.exportToDownloads(applicationContext, allDetails)
                                Toast.makeText(applicationContext, "Saved: $fileName", Toast.LENGTH_LONG).show()
                            },
                            onImportPackage = { uri ->
                                if (isReadOnly) return@SettingsScreen
                                coroutineScope.launch {
                                    try {
                                        val imported = ZipImporter.importPackage(
                                            applicationContext, uri, db, currentUser?.uid
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                applicationContext,
                                                "Imported \"${imported.title}\" (${imported.pages.size} chapters)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        if (openTabItems.none { it.notebook.id == imported.id }) {
                                            val importedNotebook = NotebookEntity(
                                                id = imported.id,
                                                title = imported.title,
                                                description = imported.description,
                                                ownerSub = currentUser?.uid ?: "*",
                                                visibility = imported.visibility,
                                                tagsCsv = imported.tagsCsv,
                                                coverPreset = imported.coverPreset,
                                                coverImage = imported.coverImage,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            val updated = openTabItems + TabItemData(
                                                notebook = importedNotebook,
                                                pageNumber = 1,
                                                pageId = imported.pages.firstOrNull()?.id
                                            )
                                            openTabItems = updated
                                            persistTabsToServer(updated)
                                        }
                                        activeNotebookId = imported.id
                                        activePageIndex = 0
                                        navigateTo(ActiveScreen.WORKSPACE)
                                        // Push the full book to the server so
                                        // other devices receive it on next sync.
                                        val importOp = SyncOp.FullBook(imported.id)
                                        writeQueue.markDirty(importOp)
                                        launch(Dispatchers.IO) {
                                            try {
                                                api.updateNote(
                                                    imported.id,
                                                    mapOf(
                                                        "title" to imported.title,
                                                        "description" to imported.description,
                                                        "visibility" to imported.visibility,
                                                        "tags" to imported.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                                        "coverPreset" to imported.coverPreset,
                                                        "coverImage" to imported.coverImage,
                                                        "isPinned" to false,
                                                        "pages" to imported.pages.map { p ->
                                                            mapOf(
                                                                "id" to p.id,
                                                                "parentId" to p.parentId,
                                                                "title" to p.title,
                                                                "notes" to p.notes
                                                            )
                                                        },
                                                        "canvases" to imported.canvases.map { c ->
                                                            mapOf(
                                                                "id" to c.id,
                                                                "type" to c.kind,
                                                                "title" to c.title,
                                                                "assignedPages" to c.assignedPagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                                                "data" to mapOf("code" to c.payloadJson)
                                                            )
                                                        },
                                                        "questions" to imported.questions.map { q ->
                                                            mapOf(
                                                                "id" to q.id,
                                                                "title" to q.title,
                                                                "answer" to q.answer
                                                            )
                                                        }
                                                    )
                                                )
                                                writeQueue.markClean(importOp)
                                            } catch (_: Exception) {}
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                applicationContext,
                                                "Import failed: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onSyncNow = {
                                if (isReadOnly) return@SettingsScreen
                                syncWithCloud()
                            },
                            onSignOut = { signOut() },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.CREATE_NOTEBOOK -> {
                        CreateNotebookScreen(
                            onSaveNotebook = { title, desc, tags, preset, coverImage ->
                                if (isReadOnly) return@CreateNotebookScreen
                                coroutineScope.launch {
                                    tierLimits?.max_notebooks?.let { max ->
                                        if (notebooks.size >= max) {
                                            Toast.makeText(
                                                applicationContext,
                                                "Notebook limit reached ($max) for your plan",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@launch
                                        }
                                    }
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
                                    openTabItems = openTabItems + TabItemData(
                                        notebook = book,
                                        pageNumber = 1,
                                        pageId = null
                                    )
                                    persistTabsToServer(openTabItems)
                                    activeNotebookId = newId
                                    activePageIndex = 0
                                    navigateTo(ActiveScreen.WORKSPACE)
                                    val createBookOp = SyncOp.FullBook(newId)
                                    writeQueue.markDirty(createBookOp)
                                    launch(Dispatchers.IO) {
                                        try {
                                            api.createNote(mapOf("id" to newId, "title" to title, "description" to desc))
                                            writeQueue.markClean(createBookOp)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onNavigateBack = { popScreen() }
                        )
                    }

                    currentScreen == ActiveScreen.CONFIGURE_NOTEBOOK && configuringNotebook != null -> {
                        val targetBook = configuringNotebook!!
                        key(targetBook.id) {
                            ConfigureNotebookScreen(
                                notebook = targetBook,
                                onSaveConfig = { title, desc, tags, preset, coverImage, visibility, password ->
                                    if (isReadOnly) return@ConfigureNotebookScreen
                                    coroutineScope.launch {
                                        val updated = targetBook.copy(
                                            title = title,
                                            description = desc,
                                            tagsCsv = tags,
                                            coverPreset = preset,
                                            coverImage = coverImage,
                                            visibility = visibility,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        db.dao().upsertNotebook(updated)

                                        // Keep open tabs and recents in sync with the new metadata.
                                        openTabItems = openTabItems.map { item ->
                                            if (item.notebook.id == updated.id) item.copy(notebook = updated)
                                            else item
                                        }
                                        recentPageItems = recentPageItems.map { item ->
                                            if (item.notebook.id == updated.id) item.copy(notebook = updated)
                                            else item
                                        }
                                        if (configuringNotebook?.id == updated.id) {
                                            configuringNotebook = updated
                                        }

                                        // Background sync to server with full payload.
                                        val configOp = SyncOp.FullBook(updated.id)
                                        writeQueue.markDirty(configOp)
                                        launch(Dispatchers.IO) {
                                            try {
                                                val details = db.dao().getPagesForNotebook(updated.id)
                                                val payload = mutableMapOf<String, Any?>(
                                                    "title" to title,
                                                    "description" to desc,
                                                    "tags" to tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                                    "coverPreset" to preset,
                                                    "coverImage" to coverImage,
                                                    "visibility" to visibility,
                                                    "isPinned" to updated.isPinned,
                                                    "pages" to details.map {
                                                        mapOf(
                                                            "id" to it.id,
                                                            "parentId" to it.parentId,
                                                            "title" to it.title,
                                                            "notes" to it.notes
                                                        )
                                                    }
                                                )
                                                if (!password.isNullOrBlank()) {
                                                    payload["password"] = password
                                                }
                                                api.updateNote(updated.id, payload)
                                                writeQueue.markClean(configOp)
                                            } catch (e: Exception) {
                                                Log.w("EsperConfig", "Failed to sync config to server", e)
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(applicationContext, "Book settings updated", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onNavigateBack = { popScreen() }
                            )
                        }
                    }

                    activeEditingQuestion != null -> {
                        QuestionEditorScreen(
                            question = activeEditingQuestion!!,
                            onNavigateBack = { activeEditingQuestion = null },
                            onSaveAnswer = { qId, ans ->
                                if (isReadOnly) return@QuestionEditorScreen
                                coroutineScope.launch { db.dao().updateQuestionAnswer(qId, ans) }
                            },
                            onUpdateTitle = { qId, newTitle ->
                                if (isReadOnly) return@QuestionEditorScreen
                                coroutineScope.launch { db.dao().updateQuestionTitle(qId, newTitle) }
                            }
                        )
                    }

                    activeNotebookId != null && currentScreen == ActiveScreen.WORKSPACE -> {
                        val details by db.dao().observeNotebookDetails(activeNotebookId!!).collectAsState(initial = null)
                        key(activeNotebookId) {
                            details?.let { book ->
                            WorkspaceScreen(
                                notebookTitle = book.notebook.title,
                                pages = book.pages,
                                canvases = book.canvases,
                                questions = book.questions,
                                activePageIndex = activePageIndex,
                                onOpenTabsScreen = { navigateTo(ActiveScreen.TABS) },
                                onOpenConfigureBook = {
                                    configuringNotebook = book.notebook
                                    navigateTo(ActiveScreen.CONFIGURE_NOTEBOOK)
                                },
                                onNavigateBack = {
                                    activeNotebookId = null
                                    navigateTo(ActiveScreen.BOOKSHELF)
                                },
                                onSelectPage = { idx ->
                                    activePageIndex = idx
                                    val selectedPage = book.pages.getOrNull(idx)
                                    openTabItems = openTabItems.map { item ->
                                        if (item.notebook.id == book.notebook.id) {
                                            item.copy(pageNumber = idx + 1, pageId = selectedPage?.id ?: item.pageId)
                                        } else item
                                    }
                                    persistTabsToServer(openTabItems)
                                    if (selectedPage != null) {
                                        recentPageItems = (
                                            listOf(
                                                RecentPageItem(
                                                    notebook = book.notebook,
                                                    page = selectedPage,
                                                    pageNumber = idx + 1
                                                )
                                            ) + recentPageItems.filterNot {
                                                it.notebook.id == book.notebook.id && it.page.id == selectedPage.id
                                            }
                                            ).take(8)
                                    }
                                },
                                onCreatePage = { title, parentId ->
                                    if (isReadOnly) return@WorkspaceScreen
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
                                        val createOp = SyncOp.CreatePage(book.notebook.id, newPageId)
                                        writeQueue.markDirty(createOp)
                                        launch(Dispatchers.IO) {
                                            try {
                                                api.createPage(
                                                    book.notebook.id,
                                                    mapOf(
                                                        "id" to newPageId,
                                                        "title" to title,
                                                        "notes" to "# $title\n\n",
                                                        "parentId" to parentId
                                                    )
                                                )
                                                writeQueue.markClean(createOp)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                onDeletePage = { pageId ->
                                    if (isReadOnly) return@WorkspaceScreen
                                    coroutineScope.launch {
                                        db.dao().deletePageAndChildren(pageId)
                                        val delOp = SyncOp.DeletePage(book.notebook.id, pageId)
                                        writeQueue.markDirty(delOp)
                                        launch(Dispatchers.IO) {
                                            try {
                                                api.deletePage(book.notebook.id, pageId)
                                                writeQueue.markClean(delOp)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                onSaveNotes = { pageId, text ->
                                    if (isReadOnly) return@WorkspaceScreen
                                    coroutineScope.launch {
                                        // Parity with web extractFirstLineTitle: keep sidebar
                                        // title in sync with the first markdown line.
                                        val derivedTitle = deriveChapterTitle(text)
                                        db.dao().updatePageNotesAndTitle(pageId, text, derivedTitle)
                                        db.dao().updateNotebookUpdatedAt(book.notebook.id, System.currentTimeMillis())
                                        val patchOp = SyncOp.PatchPage(book.notebook.id, pageId)
                                        writeQueue.markDirty(patchOp)
                                        launch(Dispatchers.IO) {
                                            try {
                                                api.patchPage(
                                                    book.notebook.id,
                                                    pageId,
                                                    mapOf("notes" to text, "title" to derivedTitle)
                                                )
                                                writeQueue.markClean(patchOp)
                                            } catch (e: Exception) {
                                                Log.w("EsperSync", "patchPage failed for $pageId", e)
                                            }
                                        }
                                    }
                                },
                                onAttachCanvas = { title, kind, payload ->
                                    if (isReadOnly) return@WorkspaceScreen
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
                                    if (isReadOnly) return@WorkspaceScreen
                                    coroutineScope.launch { db.dao().deleteCanvas(canvasId) }
                                },
                                onUpdateCanvasPayload = { canvasId, payload ->
                                    if (isReadOnly) return@WorkspaceScreen
                                    coroutineScope.launch {
                                        val existing = book.canvases.find { it.id == canvasId }
                                        if (existing != null) db.dao().upsertCanvas(existing.copy(payloadJson = payload))
                                    }
                                },
                                onAssignCanvasToPage = { canvasId ->
                                    if (isReadOnly) return@WorkspaceScreen
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
                                    if (isReadOnly) return@WorkspaceScreen
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
                                    if (isReadOnly) return@WorkspaceScreen
                                    coroutineScope.launch { db.dao().deleteQuestion(qId) }
                                },
                                onOpenQuestionEditor = { question ->
                                    activeEditingQuestion = question
                                }
                            )
                        }
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
                                requestOpenBook(id)
                            },
                            onConfigureNotebook = { book ->
                                configuringNotebook = book
                                navigateTo(ActiveScreen.CONFIGURE_NOTEBOOK)
                            },
                            onDeleteNotebook = { id ->
                                if (isReadOnly) return@BookshelfScreen
                                coroutineScope.launch {
                                    db.dao().deleteNotebookCascade(id)
                                    val remaining = openTabItems.filter { it.notebook.id != id }
                                    openTabItems = remaining
                                    persistTabsToServer(remaining)
                                    if (activeNotebookId == id) {
                                        val next = remaining.lastOrNull()
                                        activeNotebookId = next?.notebook?.id
                                        activePageIndex = (next?.pageNumber ?: 1) - 1
                                    }
                                    launch(Dispatchers.IO) {
                                        val delBookOp = SyncOp.DeleteBook(id)
                                        writeQueue.markDirty(delBookOp)
                                        try {
                                            api.deleteNote(id)
                                            writeQueue.markClean(delBookOp)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onOpenAllTagsScreen = { navigateTo(ActiveScreen.ALL_TAGS) },
                            onOpenTabsScreen = { navigateTo(ActiveScreen.TABS) },
                            onOpenSettingsScreen = { navigateTo(ActiveScreen.SETTINGS) },
                            onOpenCreateNotebookScreen = {
                                if (isReadOnly) {
                                    Toast.makeText(
                                        applicationContext,
                                        "Creation disabled in offline mode",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    navigateTo(ActiveScreen.CREATE_NOTEBOOK)
                                }
                            },
                            onOpenJournalScreen = { navigateTo(ActiveScreen.JOURNAL) },
                            onDeleteShelf = { id ->
                                if (isReadOnly) return@BookshelfScreen
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

                    // Password gate for protected notebooks.
                    val gate = passwordGate
                    if (gate != null) {
                        PasswordGateDialog(
                            gate = gate,
                            busy = gateBusy,
                            error = gateError,
                            onConfirm = { password -> unlockProtected(gate.docId, password) },
                            onDismiss = { passwordGate = null }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Web parity (presets.extractFirstLineTitle): first non-empty markdown line,
 * stripped of heading/quote/list markers, max 100 chars.
 */
internal fun deriveChapterTitle(markdown: String): String {
    val firstLine = markdown.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return "Untitled Chapter"
    val stripped = firstLine
        .replace(Regex("^[#\\s*_\\->`]+"), "")
        .replace(Regex("[#\\s*_\\->`]+$"), "")
        .trim()
        .take(100)
    return stripped.ifEmpty { "Untitled Chapter" }
}
