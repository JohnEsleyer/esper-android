package com.esper.android.ui.workspace

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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

class WorkspaceBridge(
    private val onNotesChanged: (String) -> Unit,
    private val onTimestamp: (Int) -> Unit
) {
    @JavascriptInterface
    fun onContentChanged(markdown: String) = onNotesChanged(markdown)

    @JavascriptInterface
    fun onTimestampClicked(seconds: Int) = onTimestamp(seconds)
}

enum class EditorEngine {
    LEXICAL_COMPOSE,
    WEBVIEW
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
    val context = LocalContext.current
    var splitRatio by remember { mutableFloatStateOf(0.40f) }
    var isChaptersSheetOpen by remember { mutableStateOf(false) }
    var isQuestionsSheetOpen by remember { mutableStateOf(false) }
    var isCommentsSheetOpen by remember { mutableStateOf(false) }
    var isSimMaximized by remember { mutableStateOf(false) }
    var currentEngine by remember { mutableStateOf(EditorEngine.LEXICAL_COMPOSE) }

    val activePage = pages.getOrNull(activePageIndex)

    var editorWebView by remember { mutableStateOf<WebView?>(null) }
    var simWebView by remember { mutableStateOf<WebView?>(null) }

    // Synchronize notes to WebView if active
    LaunchedEffect(activePage?.id, activePage?.notes) {
        activePage?.let { page ->
            val escaped = page.notes
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
            editorWebView?.evaluateJavascript("if (window.setMarkdown) { window.setMarkdown(`$escaped`); }", null)
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
                    // Toggle between Jetpack Compose Lexical and WebView
                    IconButton(onClick = {
                        currentEngine = if (currentEngine == EditorEngine.LEXICAL_COMPOSE) {
                            EditorEngine.WEBVIEW
                        } else {
                            EditorEngine.LEXICAL_COMPOSE
                        }
                        Toast.makeText(
                            context,
                            if (currentEngine == EditorEngine.LEXICAL_COMPOSE) "Mode: Jetpack Compose Lexical" else "Mode: Web Editor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            if (currentEngine == EditorEngine.LEXICAL_COMPOSE) Icons.Default.DataObject else Icons.Default.Language,
                            contentDescription = "Switch Engine",
                            tint = if (currentEngine == EditorEngine.LEXICAL_COMPOSE) IndigoGlow else EmeraldSync
                        )
                    }
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
                // Top Simulation Viewport (WebView Enabled with full permission & fallback)
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
                                loadUrl("file:///android_asset/workspace/sim_default.html")
                                simWebView = this
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

                // Bottom Content Viewport (Toggle between Pure Lexical Compose and WebView)
                if (!isSimMaximized) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SpaceBlack)
                    ) {
                        if (currentEngine == EditorEngine.LEXICAL_COMPOSE) {
                            // PURE JETPACK COMPOSE LEXICAL COMPONENT
                            activePage?.let { page ->
                                ComposeLexicalEditor(
                                    initialMarkdown = page.notes,
                                    onContentChanged = { updatedNotes ->
                                        onSaveNotes(page.id, updatedNotes)
                                    },
                                    onTimestampClicked = { seconds ->
                                        Toast.makeText(context, "Seeking simulation to ${seconds}s", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else {
                            // HYBRID WEBVIEW EDITOR
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
                                        }
                                        webChromeClient = WebChromeClient()
                                        addJavascriptInterface(
                                            WorkspaceBridge(
                                                onNotesChanged = { newText ->
                                                    activePage?.let { onSaveNotes(it.id, newText) }
                                                },
                                                onTimestamp = { secs ->
                                                    Toast.makeText(context, "Timestamp: ${secs}s", Toast.LENGTH_SHORT).show()
                                                }
                                            ),
                                            "EsperBridge"
                                        )
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                activePage?.let { page ->
                                                    val escaped = page.notes
                                                        .replace("\\", "\\\\")
                                                        .replace("`", "\\`")
                                                        .replace("$", "\\$")
                                                    evaluateJavascript("if (window.setMarkdown) { window.setMarkdown(`$escaped`); }", null)
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
