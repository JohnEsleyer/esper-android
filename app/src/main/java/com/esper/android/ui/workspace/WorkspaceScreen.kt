package com.esper.android.ui.workspace

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.esper.android.data.model.CanvasEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.theme.*

class WorkspaceBridge(
    private val onNotesChanged: (String) -> Unit,
    private val onTimestamp: (Int) -> Unit
) {
    @JavascriptInterface
    fun onContentChanged(markdown: String) = onNotesChanged(markdown)

    @JavascriptInterface
    fun onTimestampClicked(seconds: Int) = onTimestamp(seconds)
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
    onSaveNotes: (pageId: String, content: String) -> Unit
) {
    var splitRatio by remember { mutableFloatStateOf(0.42f) }
    var isChaptersSheetOpen by remember { mutableStateOf(false) }
    var isQuestionsSheetOpen by remember { mutableStateOf(false) }
    var isCommentsSheetOpen by remember { mutableStateOf(false) }
    var isSimMaximized by remember { mutableStateOf(false) }

    val activePage = pages.getOrNull(activePageIndex)
    val attachedCanvas = canvases.find { it.assignedPagesCsv.split(",").contains(activePage?.id) }

    var editorWebView by remember { mutableStateOf<WebView?>(null) }
    var simWebView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(activePage?.id, activePage?.notes) {
        activePage?.let { page ->
            val escaped = page.notes
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
            editorWebView?.evaluateJavascript("window.setMarkdown(`$escaped`);", null)
        }
    }

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
                    IconButton(onClick = { isQuestionsSheetOpen = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Questions", tint = AmberGlow)
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
                        factory = { context ->
                            WebView(context).apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                loadUrl("file:///android_asset/workspace/sim_default.html")
                                simWebView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Native Touch Splitter (60-120 FPS Pointer Drag)
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

                // Bottom Lexical Notes Viewport
                if (!isSimMaximized) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SpaceBlack)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    addJavascriptInterface(
                                        WorkspaceBridge(
                                            onNotesChanged = { newText ->
                                                activePage?.let { onSaveNotes(it.id, newText) }
                                            },
                                            onTimestamp = { /* Timestamp seeking handler */ }
                                        ),
                                        "EsperBridge"
                                    )
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            activePage?.let { page ->
                                                val escaped = page.notes.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
                                                evaluateJavascript("window.setMarkdown(`$escaped`);", null)
                                            }
                                        }
                                    }
                                    loadUrl("file:///android_asset/workspace/editor.html")
                                    editorWebView = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (isChaptersSheetOpen) {
            ChapterBottomSheet(
                pages = pages,
                activePageIndex = activePageIndex,
                onDismiss = { isChaptersSheetOpen = false },
                onSelectPage = onSelectPage
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
