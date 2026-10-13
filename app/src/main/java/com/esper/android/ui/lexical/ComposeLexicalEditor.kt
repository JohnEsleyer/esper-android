package com.esper.android.ui.lexical

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pure Jetpack Compose Lexical rich text component.
 */
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
    var isEditRawMode by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    fun commitChanges(newMarkdown: String) {
        rawText = newMarkdown
        blocks = LexicalParser.parseToLexicalAst(newMarkdown)
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(250)
            onContentChanged(newMarkdown)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
    ) {
        // Lexical Formatting Toolbar
        LexicalToolbar(
            isRawMode = isEditRawMode,
            onToggleRawMode = { isEditRawMode = !isEditRawMode },
            onApplyFormat = { action ->
                val updated = applyLexicalAction(rawText, action)
                commitChanges(updated)
            }
        )

        HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f), thickness = 1.dp)

        // Main Editor Surface
        AnimatedContent(
            targetState = isEditRawMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "LexicalContentMode"
        ) { editMode ->
            if (editMode) {
                // Interactive Raw Lexical Studio Mode
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    BasicTextField(
                        value = rawText,
                        onValueChange = { commitChanges(it) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(IndigoAccent),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Rich Native Node Viewport
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(blocks, key = { _, item -> item.key }) { _, block ->
                        LexicalBlockRenderer(
                            block = block,
                            onTimestampClicked = onTimestampClicked
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
    onTimestampClicked: (Int) -> Unit
) {
    when (block) {
        is LexicalHeadingNode -> {
            val (fontSize, fontWeight, color) = when (block.level) {
                1 -> Triple(22.sp, FontWeight.ExtraBold, Color.White)
                2 -> Triple(18.sp, FontWeight.Bold, Color(0xFFF1F5F9))
                else -> Triple(15.sp, FontWeight.SemiBold, Color(0xFFE2E8F0))
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LexicalInlineContent(
                    inlines = block.inlines,
                    baseFontSize = fontSize,
                    baseFontWeight = fontWeight,
                    baseColor = color,
                    onTimestampClicked = onTimestampClicked
                )
                if (block.level == 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSlate))
                }
            }
        }

        is LexicalQuoteNode -> {
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                color = IndigoAccent.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
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
                            modifier = Modifier.size(14.dp)
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
                is LexicalTextNode -> {
                    val styledText = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = if (node.isCode) IndigoGlow else baseColor,
                                fontSize = baseFontSize,
                                fontWeight = if (node.isBold) FontWeight.Bold else baseFontWeight,
                                fontStyle = if (node.isItalic || isItalic) FontStyle.Italic else FontStyle.Normal,
                                fontFamily = if (node.isCode) FontFamily.Monospace else FontFamily.Default,
                                background = if (node.isCode) SurfaceSlate else Color.Transparent
                            )
                        ) {
                            append(node.text)
                        }
                    }
                    Text(
                        text = styledText,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

sealed interface LexicalAction {
    object AddH1 : LexicalAction
    object AddH2 : LexicalAction
    object AddBold : LexicalAction
    object AddItalic : LexicalAction
    object AddCode : LexicalAction
    object AddQuote : LexicalAction
    object AddTimestamp : LexicalAction
}

@Composable
fun LexicalToolbar(
    isRawMode: Boolean,
    onToggleRawMode: () -> Unit,
    onApplyFormat: (LexicalAction) -> Unit
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
        // Mode indicator
        FilterChip(
            selected = !isRawMode,
            onClick = onToggleRawMode,
            label = { Text(if (!isRawMode) "Rich AST" else "Edit Raw", fontSize = 11.sp) },
            leadingIcon = {
                Icon(
                    if (!isRawMode) Icons.Default.Visibility else Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = IndigoAccent,
                selectedLabelColor = TextPrimary
            )
        )

        ToolbarButton(label = "H1", onClick = { onApplyFormat(LexicalAction.AddH1) })
        ToolbarButton(label = "H2", onClick = { onApplyFormat(LexicalAction.AddH2) })
        ToolbarButton(label = "B", fontWeight = FontWeight.Bold, onClick = { onApplyFormat(LexicalAction.AddBold) })
        ToolbarButton(label = "I", fontStyle = FontStyle.Italic, onClick = { onApplyFormat(LexicalAction.AddItalic) })
        ToolbarButton(label = "> Quote", onClick = { onApplyFormat(LexicalAction.AddQuote) })
        ToolbarButton(label = "`Code`", onClick = { onApplyFormat(LexicalAction.AddCode) })
        ToolbarButton(
            label = "+ [00:15]",
            accentColor = IndigoGlow,
            onClick = { onApplyFormat(LexicalAction.AddTimestamp) }
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontStyle: FontStyle = FontStyle.Normal,
    accentColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = SurfaceSlate,
        modifier = Modifier.height(30.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle
            )
        }
    }
}

private fun applyLexicalAction(content: String, action: LexicalAction): String {
    return when (action) {
        LexicalAction.AddH1 -> "$content\n# New Heading"
        LexicalAction.AddH2 -> "$content\n## New Section"
        LexicalAction.AddBold -> "$content **bold note**"
        LexicalAction.AddItalic -> "$content *italicized note*"
        LexicalAction.AddCode -> "$content\n```physics\nr(θ) = p / (1 + e * cos(θ))\n```"
        LexicalAction.AddQuote -> "$content\n> Note of significance."
        LexicalAction.AddTimestamp -> "$content [00:15]"
    }
}
