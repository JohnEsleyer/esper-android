package com.esper.android.ui.workspace

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.esper.android.data.model.CanvasEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.canvas.DrawingCanvasView
import com.esper.android.ui.components.ConfirmDeleteDialog
import com.esper.android.ui.components.EditorSkeleton
import com.esper.android.ui.components.LocalReadOnlyMode
import com.esper.android.ui.components.OfflineNoticeBanner
import com.esper.android.ui.components.SimulationSkeleton
import com.esper.android.ui.components.YouTubePlayer
import com.esper.android.ui.components.YouTubePlayerHostState
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
    onOpenTabsScreen: () -> Unit,
    onOpenConfigureBook: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onCreatePage: (title: String, parentId: String?) -> Unit,
    onDeletePage: (pageId: String) -> Unit,
    onSaveNotes: (pageId: String, content: String) -> Unit,
    onAttachCanvas: (title: String, kind: String, payload: String) -> Unit,
    onDeleteCanvas: (canvasId: String) -> Unit,
    onUpdateCanvasPayload: (canvasId: String, payload: String) -> Unit,
    onAssignCanvasToPage: (canvasId: String) -> Unit = {},
    onCreateQuestion: (title: String) -> Unit,
    onDeleteQuestion: (questionId: String) -> Unit,
    onOpenQuestionEditor: (QuestionEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isReadOnly = LocalReadOnlyMode.current
    var splitRatio by remember { mutableFloatStateOf(0.42f) }
    var viewportMode by remember { mutableStateOf(ViewportMode.SPLIT) }
    var isDraggingSplitter by remember { mutableStateOf(false) }

    val leftChaptersDrawerState = rememberDrawerState(DrawerValue.Closed)

    var isCommentsSheetOpen by remember { mutableStateOf(false) }
    var isQuestionsSheetOpen by remember { mutableStateOf(false) }
    var isAttachmentsSheetOpen by remember { mutableStateOf(false) }
    var showHtmlCodeEditor by remember { mutableStateOf(false) }
    var showDeleteCanvasConfirm by remember { mutableStateOf(false) }

    val activePage = pages.getOrNull(activePageIndex)
    val youtubeHostState = remember { YouTubePlayerHostState() }

    val attachedCanvas = remember(canvases, activePage?.id) {
        activePage?.let { page ->
            canvases.firstOrNull { c ->
                c.assignedPagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.contains(page.id)
            }
        }
    }

    val hasAttachment = attachedCanvas != null

    var isEditorReady by remember { mutableStateOf(false) }
    var isAttachmentReady by remember { mutableStateOf(false) }

    LaunchedEffect(activePageIndex, hasAttachment) {
        isEditorReady = false
        isAttachmentReady = false
        delay(300)
        isEditorReady = true
        if (hasAttachment) {
            delay(300)
            isAttachmentReady = true
        }
    }

    var simWebView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (leftChaptersDrawerState.isOpen) {
            coroutineScope.launch { leftChaptersDrawerState.close() }
        } else {
            onNavigateBack()
        }
    }

    ModalNavigationDrawer(
        drawerState = leftChaptersDrawerState,
        gesturesEnabled = true,
        drawerContent = {
            ChapterDrawerContent(
                pages = pages,
                canvases = canvases,
                activePageIndex = activePageIndex,
                onSelectPage = { idx ->
                    coroutineScope.launch { leftChaptersDrawerState.close() }
                    onSelectPage(idx)
                },
                onCreatePage = onCreatePage,
                onDeletePage = onDeletePage
            )
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    if (isReadOnly) {
                        OfflineNoticeBanner()
                    }
                    TopAppBar(
                    title = {
                        Column {
                            Text(notebookTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = TextPrimary)
                            activePage?.let { page ->
                                Text(
                                    text = "${activePageIndex + 1}. ${page.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IndigoGlow,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                            IconButton(onClick = { coroutineScope.launch { leftChaptersDrawerState.open() } }) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Chapters Menu", tint = IndigoGlow)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isAttachmentsSheetOpen = true }) {
                            Icon(Icons.Default.Attachment, contentDescription = "Attachments", tint = EmeraldSync)
                        }
                        IconButton(onClick = { isQuestionsSheetOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Questions", tint = AmberGlow)
                        }
                        IconButton(onClick = { isCommentsSheetOpen = true }) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Discussion", tint = IndigoGlow)
                        }
                        IconButton(onClick = onOpenTabsScreen) {
                            Icon(Icons.Default.Layers, contentDescription = "Tabs Screen", tint = ElectricViolet)
                        }
                        if (!isReadOnly) {
                            IconButton(onClick = onOpenConfigureBook) {
                                Icon(Icons.Default.Tune, contentDescription = "Configure Book", tint = IndigoGlow)
                            }
                        }
                        if (hasAttachment) {
                            IconButton(onClick = {
                                viewportMode = when (viewportMode) {
                                    ViewportMode.SPLIT -> ViewportMode.ATTACHMENT_FULL
                                    ViewportMode.ATTACHMENT_FULL -> ViewportMode.EDITOR_FULL
                                    ViewportMode.EDITOR_FULL -> ViewportMode.SPLIT
                                }
                            }) {
                                val icon = when (viewportMode) {
                                    ViewportMode.SPLIT -> Icons.Default.ViewAgenda
                                    ViewportMode.ATTACHMENT_FULL -> Icons.Default.Fullscreen
                                    ViewportMode.EDITOR_FULL -> Icons.Default.EditNote
                                }
                                Icon(icon, contentDescription = "Viewport Mode", tint = TextPrimary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
                    )
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

                if (!hasAttachment) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpaceBlack)
                    ) {
                        if (!isEditorReady) {
                            EditorSkeleton()
                        } else {
                            activePage?.let { page ->
                                key(page.id) {
                                ComposeLexicalEditor(
                                    initialMarkdown = page.notes,
                                    onContentChanged = { updatedNotes ->
                                        onSaveNotes(page.id, updatedNotes)
                                    },
                                    onTimestampClicked = { seconds ->
                                        Toast.makeText(context, "Timestamp: ${seconds}s", Toast.LENGTH_SHORT).show()
                                    },
                                    readOnly = isReadOnly
                                )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (viewportMode != ViewportMode.EDITOR_FULL) {
                            val simHeight = if (viewportMode == ViewportMode.ATTACHMENT_FULL) totalHeight else totalHeight * splitRatio
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(simHeight)
                                    .background(Color.Black)
                            ) {
                                val canvas = attachedCanvas
                                if (isDraggingSplitter || !isAttachmentReady || canvas == null) {
                                    SimulationSkeleton(label = "Rendering ${canvas?.title ?: "attachment"}...")
                                } else if (canvas.kind == "youtube") {
                                    val ytId = extractYouTubeId(canvas.payloadJson)
                                    YouTubePlayer(
                                        videoId = ytId,
                                        hostState = youtubeHostState,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (canvas.kind == "draw" || canvas.kind == "excalidraw" || canvas.kind == "canvas" || canvas.kind == "whiteboard") {
                                    DrawingCanvasView(
                                        rawJson = canvas.payloadJson,
                                        modifier = Modifier.fillMaxSize()
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
                                                    mediaPlaybackRequiresUserGesture = false
                                                    loadWithOverviewMode = true
                                                    useWideViewPort = true
                                                }
                                                webChromeClient = WebChromeClient()
                                                webViewClient = WebViewClient()
                                                loadDataWithBaseURL("https://localhost/", canvas.payloadJson, "text/html", "UTF-8", null)
                                                simWebView = this
                                            }
                                        },
                                        update = { wv ->
                                            wv.loadDataWithBaseURL("https://localhost/", canvas.payloadJson, "text/html", "UTF-8", null)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (attachedCanvas?.kind == "html" && !isReadOnly) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.75f),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shadowElevation = 6.dp
                                        ) {
                                            IconButton(
                                                onClick = { showHtmlCodeEditor = true },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit HTML", tint = IndigoGlow, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    if (!isReadOnly) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.75f),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shadowElevation = 6.dp
                                        ) {
                                            IconButton(
                                                onClick = { showDeleteCanvasConfirm = true },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Attachment", tint = RoseRuby, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                                        key(page.id) {
                                        ComposeLexicalEditor(
                                            initialMarkdown = page.notes,
                                            onContentChanged = { updatedNotes ->
                                                onSaveNotes(page.id, updatedNotes)
                                            },
                                            onTimestampClicked = { seconds ->
                                                Toast.makeText(context, "Seeking to ${seconds}s", Toast.LENGTH_SHORT).show()
                                                youtubeHostState.seekTo(seconds)
                                                simWebView?.evaluateJavascript(
                                                    "if (window.seekTo) { window.seekTo($seconds); }",
                                                    null
                                                )
                                            },
                                            readOnly = isReadOnly
                                        )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAttachmentsSheetOpen) {
                    val selectedIdx = canvases.indexOf(attachedCanvas).coerceAtLeast(0)
                    AttachmentsBottomSheet(
                        canvases = canvases,
                        selectedIndex = if (canvases.isEmpty()) 0 else selectedIdx,
                        onSelectCanvas = { idx ->
                            val c = canvases.getOrNull(idx)
                            if (c != null) {
                                onAssignCanvasToPage(c.id)
                                isAttachmentsSheetOpen = false
                            }
                        },
                        onAddAttachment = { title, kind, payload ->
                            onAttachCanvas(title, kind, payload)
                        },
                        onDeleteAttachment = onDeleteCanvas,
                        onDismiss = { isAttachmentsSheetOpen = false }
                    )
                }

                if (isQuestionsSheetOpen) {
                    QuestionsBottomSheet(
                        questions = questions,
                        onDismiss = { isQuestionsSheetOpen = false },
                        onCreateQuestion = onCreateQuestion,
                        onDeleteQuestion = onDeleteQuestion,
                        onOpenQuestionEditor = { q ->
                            isQuestionsSheetOpen = false
                            onOpenQuestionEditor(q)
                        }
                    )
                }

                if (isCommentsSheetOpen) {
                    CommentsBottomSheet(
                        chapterTitle = activePage?.title ?: "Chapter",
                        onDismiss = { isCommentsSheetOpen = false }
                    )
                }

                if (showHtmlCodeEditor && attachedCanvas != null) {
                    HtmlCodeEditorScreen(
                        title = attachedCanvas?.title ?: "HTML",
                        initialCode = attachedCanvas?.payloadJson?.ifBlank {
                            "<!DOCTYPE html>\n<html>\n<head>\n  <style>body { background: #020617; color: #818cf8; }</style>\n</head>\n<body>\n  <h1>Simulation</h1>\n</body>\n</html>"
                        } ?: "",
                        onSaveAndRun = { newCode ->
                            attachedCanvas?.let { onUpdateCanvasPayload(it.id, newCode) }
                            simWebView?.loadDataWithBaseURL("https://localhost/", newCode, "text/html", "UTF-8", null)
                            showHtmlCodeEditor = false
                        },
                        onDismiss = { showHtmlCodeEditor = false }
                    )
                }

                if (showDeleteCanvasConfirm && attachedCanvas != null) {
                    ConfirmDeleteDialog(
                        title = "Delete Companion Visual",
                        message = "Are you sure you want to delete \u201C${attachedCanvas.title}\u201D?",
                        onConfirm = {
                            onDeleteCanvas(attachedCanvas.id)
                            showDeleteCanvasConfirm = false
                        },
                        onDismiss = { showDeleteCanvasConfirm = false }
                    )
                }
            }
        }
    }
}

private fun extractYouTubeId(raw: String): String {
    val trimmed = raw.trim()
    val candidate = if (trimmed.startsWith("{") && trimmed.contains("\"url\"")) {
        try {
            val obj = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
            obj.get("url")?.asString ?: trimmed
        } catch (_: Exception) { trimmed }
    } else trimmed

    val pattern = Regex("(?:youtube\\.com\\/(?:[^/]+\\/.+\\/|(?:v|e(?:mbed)?|shorts|live)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?/\\s]{11})")
    val match = pattern.find(candidate)
    return match?.groupValues?.get(1) ?: candidate.takeIf { it.length == 11 } ?: candidate
}
