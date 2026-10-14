package com.esper.android.ui.lexical

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.esper.android.ui.components.KaTeXMathView
import com.esper.android.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface LexicalAction {
    object AddH1 : LexicalAction
    object AddH2 : LexicalAction
    object AddH3 : LexicalAction
    object AddBold : LexicalAction
    object AddItalic : LexicalAction
    object AddStrike : LexicalAction
    object AddMathBlock : LexicalAction
    object AddInlineMath : LexicalAction
    object AddImage : LexicalAction
    object AddQuote : LexicalAction
    object AddCodeBlock : LexicalAction
    object AddBulletList : LexicalAction
    object AddChecklist : LexicalAction
    object AddTable : LexicalAction
    object AddTimestamp : LexicalAction
    object AddDivider : LexicalAction
}

@Composable
fun ComposeLexicalEditor(
    initialMarkdown: String,
    onContentChanged: (String) -> Unit,
    onTimestampClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var rawText by remember(initialMarkdown) { mutableStateOf(initialMarkdown) }
    var blocks by remember(rawText) { mutableStateOf(LexicalParser.parseToLexicalAst(rawText)) }
    var isRawEditMode by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // A pending debounce belongs to the previous chapter instance: cancel it
    // on chapter switch / dispose so it can never PATCH the new page with
    // stale text after the 300ms window.
    DisposableEffect(initialMarkdown) {
        onDispose { debounceJob?.cancel() }
    }

    fun commit(newText: String) {
        rawText = newText
        blocks = LexicalParser.parseToLexicalAst(newText)
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(300)
            onContentChanged(newText)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(SpaceBlack)
    ) {
        // Formatting Toolbar
        LexicalToolbar(
            isRawMode = isRawEditMode,
            onToggleMode = { isRawEditMode = !isRawEditMode },
            onAction = { action ->
                val updated = applyAction(rawText, action)
                commit(updated)
            }
        )

        HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f), thickness = 1.dp)

        AnimatedContent(
            targetState = isRawEditMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "EditorEngineView"
        ) { isRaw ->
            if (isRaw) {
                // Interactive Raw Markdown mode with generous fixed bottom padding for keyboard comfort
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = rawText,
                        onValueChange = { commit(it) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(IndigoAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Ample fixed empty space below the editor so typing text is always visible above the keyboard
                    Spacer(modifier = Modifier.height(340.dp))
                }
            } else {
                // Native Lexical Component Viewport with fixed bottom padding
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(blocks, key = { _, it -> it.key }) { _, block ->
                        LexicalBlockRenderer(
                            block = block,
                            onTimestampClicked = onTimestampClicked,
                            onToggleTask = { _ -> }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LexicalBlockRenderer(
    block: LexicalBlockNode,
    onTimestampClicked: (Int) -> Unit,
    onToggleTask: (Boolean) -> Unit
) {
    when (block) {
        is LexicalHeadingNode -> {
            val (size, weight, color) = when (block.level) {
                1 -> Triple(24.sp, FontWeight.ExtraBold, Color.White)
                2 -> Triple(19.sp, FontWeight.Bold, Color(0xFFF1F5F9))
                3 -> Triple(16.sp, FontWeight.SemiBold, Color(0xFFE2E8F0))
                else -> Triple(14.sp, FontWeight.Medium, TextSecondary)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LexicalInlineContent(
                    inlines = block.inlines,
                    baseFontSize = size,
                    baseFontWeight = weight,
                    baseColor = color,
                    onTimestampClicked = onTimestampClicked
                )
                if (block.level <= 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSlate))
                }
            }
        }

        is LexicalMathBlockNode -> {
            KaTeXMathView(latex = block.latex, isDisplayMode = true)
        }

        is LexicalImageNode -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CosmicSlate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AsyncImage(
                        model = block.imageUrl,
                        contentDescription = block.altText,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (block.altText.isNotBlank()) {
                        Text(
                            text = block.altText,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
            }
        }

        is LexicalQuoteNode -> {
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                color = IndigoAccent.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .background(IndigoAccent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LexicalInlineContent(
                        inlines = block.inlines,
                        baseFontSize = 14.sp,
                        baseFontWeight = FontWeight.Normal,
                        baseColor = TextSecondary,
                        isItalic = true,
                        onTimestampClicked = onTimestampClicked
                    )
                }
            }
        }

        is LexicalCodeBlockNode -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF030712),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = block.language.uppercase(),
                            color = IndigoGlow,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Code",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = block.code,
                        color = Color(0xFFE0E7FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        is LexicalListItemNode -> {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (block.isTask) {
                    Checkbox(
                        checked = block.isChecked,
                        onCheckedChange = { onToggleTask(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = IndigoAccent,
                            uncheckedColor = TextMuted
                        ),
                        modifier = Modifier.size(24.dp).padding(end = 4.dp)
                    )
                } else if (block.isOrdered) {
                    Text(
                        text = "${block.indexNumber ?: 1}.",
                        color = IndigoGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.width(24.dp)
                    )
                } else {
                    Text(
                        text = "•",
                        color = IndigoGlow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(18.dp)
                    )
                }
                LexicalInlineContent(
                    inlines = block.inlines,
                    baseFontSize = 14.sp,
                    baseFontWeight = FontWeight.Normal,
                    baseColor = TextPrimary,
                    onTimestampClicked = onTimestampClicked
                )
            }
        }

        is LexicalTableNode -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CosmicSlate,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().background(SurfaceSlate).padding(6.dp)) {
                        block.headers.forEach { h ->
                            Text(
                                text = h,
                                color = IndigoGlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    block.rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                            row.forEach { cell ->
                                Text(
                                    text = cell,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        is LexicalHorizontalRuleNode -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSlate)
            )
        }

        is LexicalParagraphNode -> {
            LexicalInlineContent(
                inlines = block.inlines,
                baseFontSize = 14.sp,
                baseFontWeight = FontWeight.Normal,
                baseColor = TextPrimary,
                onTimestampClicked = onTimestampClicked
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LexicalInlineContent(
    inlines: List<LexicalInlineNode>,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    baseFontWeight: FontWeight,
    baseColor: Color,
    isItalic: Boolean = false,
    onTimestampClicked: (Int) -> Unit
) {
    val context = LocalContext.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        inlines.forEach { node ->
            when (node) {
                is LexicalTimestampNode -> {
                    Surface(
                        onClick = { onTimestampClicked(node.seconds) },
                        shape = RoundedCornerShape(6.dp),
                        color = IndigoAccent.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = IndigoGlow,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = node.rawText,
                                color = IndigoGlow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is LexicalInlineMathNode -> {
                    KaTeXMathView(latex = node.latex, isDisplayMode = false)
                }

                is LexicalLinkNode -> {
                    Text(
                        text = node.text,
                        color = IndigoGlow,
                        textDecoration = TextDecoration.Underline,
                        fontSize = baseFontSize,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(node.url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }

                is LexicalTextNode -> {
                    val styled = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = if (node.isCode) IndigoGlow else baseColor,
                                fontSize = baseFontSize,
                                fontWeight = if (node.isBold) FontWeight.Bold else baseFontWeight,
                                fontStyle = if (node.isItalic || isItalic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (node.isStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                                fontFamily = if (node.isCode) FontFamily.Monospace else FontFamily.Default,
                                background = if (node.isCode) SurfaceSlate else Color.Transparent
                            )
                        ) {
                            append(node.text)
                        }
                    }
                    Text(text = styled, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
fun LexicalToolbar(
    isRawMode: Boolean,
    onToggleMode: () -> Unit,
    onAction: (LexicalAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmicSlate)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = !isRawMode,
            onClick = onToggleMode,
            label = { Text(if (!isRawMode) "Native View" else "Raw MD", fontSize = 11.sp) },
            leadingIcon = {
                Icon(
                    if (!isRawMode) Icons.Default.Visibility else Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = IndigoAccent,
                selectedLabelColor = TextPrimary
            )
        )

        ToolbarBtn(label = "H1", onClick = { onAction(LexicalAction.AddH1) })
        ToolbarBtn(label = "H2", onClick = { onAction(LexicalAction.AddH2) })
        ToolbarBtn(label = "B", fontWeight = FontWeight.Bold, onClick = { onAction(LexicalAction.AddBold) })
        ToolbarBtn(label = "I", fontStyle = FontStyle.Italic, onClick = { onAction(LexicalAction.AddItalic) })
        ToolbarBtn(label = "S", onClick = { onAction(LexicalAction.AddStrike) })
        ToolbarBtn(label = "KaTeX $$", accent = AmberGlow, onClick = { onAction(LexicalAction.AddMathBlock) })
        ToolbarBtn(label = "KaTeX $", accent = AmberGlow, onClick = { onAction(LexicalAction.AddInlineMath) })
        ToolbarBtn(label = "Image", accent = EmeraldSync, onClick = { onAction(LexicalAction.AddImage) })
        ToolbarBtn(label = "List", onClick = { onAction(LexicalAction.AddBulletList) })
        ToolbarBtn(label = "Task", onClick = { onAction(LexicalAction.AddChecklist) })
        ToolbarBtn(label = "Table", onClick = { onAction(LexicalAction.AddTable) })
        ToolbarBtn(label = "Code", onClick = { onAction(LexicalAction.AddCodeBlock) })
        ToolbarBtn(label = "> Quote", onClick = { onAction(LexicalAction.AddQuote) })
        ToolbarBtn(label = "[00:15]", accent = IndigoGlow, onClick = { onAction(LexicalAction.AddTimestamp) })
        ToolbarBtn(label = "---", onClick = { onAction(LexicalAction.AddDivider) })
    }
}

@Composable
private fun ToolbarBtn(
    label: String,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontStyle: FontStyle = FontStyle.Normal,
    accent: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = SurfaceSlate,
        modifier = Modifier.height(30.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = accent,
                fontSize = 12.sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle
            )
        }
    }
}

private fun applyAction(content: String, action: LexicalAction): String {
    return when (action) {
        LexicalAction.AddH1 -> "$content\n# New Chapter Heading"
        LexicalAction.AddH2 -> "$content\n## New Sub-section"
        LexicalAction.AddH3 -> "$content\n### Topic Details"
        LexicalAction.AddBold -> "$content **bold statement**"
        LexicalAction.AddItalic -> "$content *italicized phrasing*"
        LexicalAction.AddStrike -> "$content ~~strikethrough text~~"
        LexicalAction.AddMathBlock -> "$content\n$$\n\\oint \\mathbf{B} \\cdot d\\mathbf{l} = \\mu_0 I_{\\text{enc}}\n$$\n"
        LexicalAction.AddInlineMath -> "$content \$E = mc^2\$"
        LexicalAction.AddImage -> "$content\n![Kepler Ellipse](https://images.unsplash.com/photo-1614728894747-a83421e2b9c9?w=800)\n"
        LexicalAction.AddBulletList -> "$content\n- First observation\n- Second observation"
        LexicalAction.AddChecklist -> "$content\n- [ ] Unfinished task\n- [x] Verified item"
        LexicalAction.AddTable -> "$content\n| Parameter | Value | Unit |\n|---|---|---|\n| Semi-Major Axis (a) | 1.496e8 | km |\n| Eccentricity (e) | 0.0167 | - |\n"
        LexicalAction.AddCodeBlock -> "$content\n```python\ndef orbital_period(a, mu):\n    return 2 * math.pi * math.sqrt(a**3 / mu)\n```"
        LexicalAction.AddQuote -> "$content\n> Conservation of angular momentum yields constant areal velocity."
        LexicalAction.AddTimestamp -> "$content [00:15]"
        LexicalAction.AddDivider -> "$content\n---\n"
    }
}
