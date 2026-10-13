package com.esper.android.ui.workspace

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.esper.android.data.model.CanvasEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.components.EditorSkeleton
import com.esper.android.ui.components.SimulationSkeleton
import com.esper.android.ui.lexical.ComposeLexicalEditor
import com.esper.android.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ViewportMode {
    SPLIT,
    ATTACHMENT_FULL,
    EDITOR_FULL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    notebookTitle: String,
    pages: List<PageEntity>,
    canvases: List<CanvasEntity>,
    questions: List<QuestionEntity>,
    activePageIndex: Int,
    onNavigateBack: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onCreatePage: (title: String, parentId: String?) -> Unit,
    onDeletePage: (pageId: String) -> Unit,
    onSaveNotes: (pageId: String, content: String) -> Unit,
    onAttachCanvas: (title: String, kind: String, payload: String) -> Unit,
    onDeleteCanvas: (canvasId: String) -> Unit,
    onUpdateCanvasPayload: (canvasId: String, payload: String) -> Unit,
    onCreateQuestion: (title: String) -> Unit,
    onDeleteQuestion: (questionId: String) -> Unit,
    onOpenQuestionEditor: (QuestionEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var splitRatio by remember { mutableFloatStateOf(0.42f) }
    var viewportMode by remember { mutableStateOf(ViewportMode.SPLIT) }
    var isDraggingSplitter by remember { mutableStateOf(false) }

    // Staged loading states
    var isEditorReady by remember { mutableStateOf(false) }
    var isAttachmentReady by remember { mutableStateOf(false) }

    // Staged render timers: Bottom editor ready after 1.0s, Top simulation after 1.8s
    LaunchedEffect(activePageIndex) {
        isEditorReady = false
        isAttachmentReady = false
        delay(1000)
        isEditorReady = true
        delay(800)
        isAttachmentReady = true
    }

    var isChaptersSheetOpen by remember { mutableStateOf(false) }
    var isCommentsSheetOpen by remember { mutableStateOf(false) }
    var isAttachmentsSheetOpen by remember { mutableStateOf(false) }
    var showHtmlCodeEditor by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var selectedCanvasIndex by remember { mutableIntStateOf(0) }
    val activePage = pages.getOrNull(activePageIndex)
    val activeCanvas = canvases.getOrNull(selectedCanvasIndex)

    var simWebView by remember { mutableStateOf<WebView?>(null) }

    // Right-side drawer for Questions
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    QuestionsDrawerContent(
                        questions = questions,
                        onSelectQuestion = { q ->
                            coroutineScope.launch { drawerState.close() }
                            onOpenQuestionEditor(q)
                        },
                        onCreateQuestion = { title -> onCreateQuestion(title) },
                        onDeleteQuestion = { id -> onDeleteQuestion(id) }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(notebookTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = TextPrimary)
                                    activePage?.let { page ->
                                        TextButton(
                                            onClick = { isChaptersSheetOpen = true },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = "${activePageIndex + 1}. ${page.title}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = IndigoGlow
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                                }
                            },
                            actions = {
                                IconButton(onClick = { isAttachmentsSheetOpen = true }) {
                                    Icon(Icons.Default.Attachment, contentDescription = "Attachments", tint = EmeraldSync)
                                }
                                IconButton(onClick = { isCommentsSheetOpen = true }) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Discussion", tint = IndigoGlow)
                                }
                                // Right Sidebar toggle for Questions
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Questions (Right Sidebar)", tint = AmberGlow)
                                }
                                // 3-State Fullscreen Toggle
                                IconButton(onClick = {
                                    viewportMode = when (viewportMode) {
                                        ViewportMode.SPLIT -> ViewportMode.ATTACHMENT_FULL
                                        ViewportMode.ATTACHMENT_FULL -> ViewportMode.EDITOR_FULL
                                        ViewportMode.EDITOR_FULL -> ViewportMode.SPLIT
                                    }
                                }) {
                                    val (icon, desc) = when (viewportMode) {
                                        ViewportMode.SPLIT -> Icons.Default.ViewAgenda to "Split View"
                                        ViewportMode.ATTACHMENT_FULL -> Icons.Default.Fullscreen to "Attachment Fullscreen"
                                        ViewportMode.EDITOR_FULL -> Icons.Default.EditNote to "Editor Fullscreen"
                                    }
                                    Icon(icon, contentDescription = desc, tint = TextPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
                        )
                    },
                    floatingActionButton = {
                        if (activeCanvas != null && activeCanvas.kind == "html" && viewportMode != ViewportMode.EDITOR_FULL) {
                            FloatingActionButton(
                                onClick = { showHtmlCodeEditor = true },
                                containerColor = IndigoAccent,
                                contentColor = TextPrimary,
                                modifier = Modifier.padding(bottom = 56.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Code, contentDescription = "Edit Code", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("HTML Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(SpaceBlack)
                    ) {
                        val totalHeight = maxHeight
                        val density = LocalDensity.current

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Simulation / Attachment Viewport
                            if (viewportMode != ViewportMode.EDITOR_FULL) {
                                val simHeight = if (viewportMode == ViewportMode.ATTACHMENT_FULL) totalHeight else totalHeight * splitRatio
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(simHeight)
                                        .background(Color.Black)
                                ) {
                                    // When dragging the splitter or during initial 1.8s delay, render smooth skeleton instead of WebView
                                    if (isDraggingSplitter || !isAttachmentReady) {
                                        SimulationSkeleton(
                                            label = if (isDraggingSplitter) "Adjusting viewport..." else "Loading celestial dynamics..."
                                        )
                                    } else {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    @SuppressLint("SetJavaScriptEnabled")
                                                    settings.apply {
                                                        javaScriptEnabled = true
                                                        domStorageEnabled = true
                                                        allowFileAccess = true
                                                        allowContentAccess = true
                                                        databaseEnabled = true
                                                        loadWithOverviewMode = true
                                                        useWideViewPort = true
                                                    }
                                                    webChromeClient = WebChromeClient()
                                                    webViewClient = WebViewClient()

                                                    if (activeCanvas != null && activeCanvas.kind == "youtube") {
                                                        val ytEmbed = "https://www.youtube.com/embed/${activeCanvas.payloadJson}?enablejsapi=1"
                                                        loadUrl(ytEmbed)
                                                    } else if (activeCanvas != null && activeCanvas.payloadJson.isNotBlank()) {
                                                        loadDataWithBaseURL("https://localhost/", activeCanvas.payloadJson, "text/html", "UTF-8", null)
                                                    } else {
                                                        loadUrl("file:///android_asset/workspace/sim_default.html")
                                                    }
                                                    simWebView = this
                                                }
                                            },
                                            update = { wv ->
                                                if (activeCanvas != null && activeCanvas.kind == "youtube") {
                                                    wv.loadUrl("https://www.youtube.com/embed/${activeCanvas.payloadJson}?enablejsapi=1")
                                                } else if (activeCanvas != null && activeCanvas.payloadJson.isNotBlank()) {
                                                    wv.loadDataWithBaseURL("https://localhost/", activeCanvas.payloadJson, "text/html", "UTF-8", null)
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            // Touch Splitter Handle with Drag detection
                            if (viewportMode == ViewportMode.SPLIT) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .background(if (isDraggingSplitter) IndigoAccent.copy(alpha = 0.35f) else CosmicSlate)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { isDraggingSplitter = true },
                                                onDragEnd = { isDraggingSplitter = false },
                                                onDragCancel = { isDraggingSplitter = false },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val delta = dragAmount.y / with(density) { totalHeight.toPx() }
                                                    splitRatio = (splitRatio + delta).coerceIn(0.15f, 0.85f)
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(4.dp)
                                            .background(
                                                if (isDraggingSplitter) IndigoAccent else BorderSlate,
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                }
                            }

                            // Bottom Content Viewport (Editor starts rendering at 1.0s)
                            if (viewportMode != ViewportMode.ATTACHMENT_FULL) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(SpaceBlack)
                                ) {
                                    if (!isEditorReady) {
                                        EditorSkeleton()
                                    } else {
                                        activePage?.let { page ->
                                            ComposeLexicalEditor(
                                                initialMarkdown = page.notes,
                                                onContentChanged = { updatedNotes ->
                                                    onSaveNotes(page.id, updatedNotes)
                                                },
                                                onTimestampClicked = { seconds ->
                                                    Toast.makeText(context, "Seeking visual companion to ${seconds}s", Toast.LENGTH_SHORT).show()
                                                    simWebView?.evaluateJavascript(
                                                        "if (window.seekTo) { window.seekTo($seconds); }",
                                                        null
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sheets & Modals
                    if (isChaptersSheetOpen) {
                        ChapterBottomSheet(
                            pages = pages,
                            activePageIndex = activePageIndex,
                            onDismiss = { isChaptersSheetOpen = false },
                            onSelectPage = onSelectPage,
                            onCreatePage = onCreatePage,
                            onDeletePage = onDeletePage
                        )
                    }

                    if (isAttachmentsSheetOpen) {
                        AttachmentsBottomSheet(
                            canvases = canvases,
                            selectedIndex = selectedCanvasIndex,
                            onSelectCanvas = { idx -> selectedCanvasIndex = idx },
                            onAddAttachment = onAttachCanvas,
                            onDeleteAttachment = onDeleteCanvas,
                            onDismiss = { isAttachmentsSheetOpen = false }
                        )
                    }

                    if (isCommentsSheetOpen) {
                        CommentsBottomSheet(
                            chapterTitle = activePage?.title ?: "Chapter",
                            onDismiss = { isCommentsSheetOpen = false }
                        )
                    }

                    if (showHtmlCodeEditor && activeCanvas != null) {
                        HtmlCodeEditorScreen(
                            title = activeCanvas.title,
                            initialCode = activeCanvas.payloadJson.ifBlank {
                                "<!DOCTYPE html>\n<html>\n<head>\n  <style>body { background: #020617; color: #818cf8; }</style>\n</head>\n<body>\n  <h1>Custom Simulation</h1>\n</body>\n</html>"
                            },
                            onSaveAndRun = { newCode ->
                                onUpdateCanvasPayload(activeCanvas.id, newCode)
                                simWebView?.loadDataWithBaseURL("https://localhost/", newCode, "text/html", "UTF-8", null)
                                showHtmlCodeEditor = false
                            },
                            onDismiss = { showHtmlCodeEditor = false }
                        )
                    }
                }
            }
        }
    }
}
