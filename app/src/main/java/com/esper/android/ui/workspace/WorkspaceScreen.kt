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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.esper.android.data.model.CanvasEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.lexical.ComposeLexicalEditor
import com.esper.android.ui.theme.*

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
    onDeleteCanvas: (canvasId: String) -> Unit
) {
    val context = LocalContext.current
    var splitRatio by remember { mutableFloatStateOf(0.42f) }
    var isChaptersSheetOpen by remember { mutableStateOf(false) }
    var isQuestionsSheetOpen by remember { mutableStateOf(false) }
    var isCommentsSheetOpen by remember { mutableStateOf(false) }
    var isAttachmentsSheetOpen by remember { mutableStateOf(false) }
    var isSimMaximized by remember { mutableStateOf(false) }

    var selectedCanvasIndex by remember { mutableIntStateOf(0) }
    val activePage = pages.getOrNull(activePageIndex)
    val activeCanvas = canvases.getOrNull(selectedCanvasIndex)

    var simWebView by remember { mutableStateOf<WebView?>(null) }

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
                        Icon(Icons.Default.Attachment, contentDescription = "Attachments & Canvases", tint = EmeraldSync)
                    }
                    IconButton(onClick = { isQuestionsSheetOpen = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Review Questions", tint = AmberGlow)
                    }
                    IconButton(onClick = { isCommentsSheetOpen = true }) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Discussion", tint = IndigoGlow)
                    }
                    IconButton(onClick = { isSimMaximized = !isSimMaximized }) {
                        Icon(if (isSimMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Toggle Viewport", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
            )
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
                // Top Simulation Viewport
                val simHeight = if (isSimMaximized) totalHeight else totalHeight * splitRatio
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(simHeight)
                        .background(Color.Black)
                ) {
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

                // Native Touch Splitter Drag Handle
                if (!isSimMaximized) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(CosmicSlate)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val delta = dragAmount.y / with(density) { totalHeight.toPx() }
                                    splitRatio = (splitRatio + delta).coerceIn(0.15f, 0.85f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp)
                                .background(BorderSlate, RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Bottom Content Viewport (Native Compose Lexical Editor)
                if (!isSimMaximized) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SpaceBlack)
                    ) {
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

        // Chapters & Chapter CRUD Bottom Sheet
        if (isChaptersSheetOpen) {
            ChapterBottomSheet(
                pages = pages,
                activePageIndex = activePageIndex,
                onDismiss = { isChaptersSheetOpen = false },
                onSelectPage = onSelectPage,
                onCreatePage = { title, parentId ->
                    onCreatePage(title, parentId)
                },
                onDeletePage = { pageId ->
                    onDeletePage(pageId)
                }
            )
        }

        // Attachment / Companion Visuals Sheet
        if (isAttachmentsSheetOpen) {
            AttachmentsBottomSheet(
                canvases = canvases,
                selectedIndex = selectedCanvasIndex,
                onSelectCanvas = { idx -> selectedCanvasIndex = idx },
                onAddAttachment = { title, kind, payload ->
                    onAttachCanvas(title, kind, payload)
                },
                onDeleteAttachment = { canvasId ->
                    onDeleteCanvas(canvasId)
                },
                onDismiss = { isAttachmentsSheetOpen = false }
            )
        }

        if (isQuestionsSheetOpen) {
            QuestionsBottomSheet(
                questions = questions,
                onDismiss = { isQuestionsSheetOpen = false }
            )
        }

        if (isCommentsSheetOpen) {
            CommentsBottomSheet(
                chapterTitle = activePage?.title ?: "Chapter",
                onDismiss = { isCommentsSheetOpen = false }
            )
        }
    }
}
