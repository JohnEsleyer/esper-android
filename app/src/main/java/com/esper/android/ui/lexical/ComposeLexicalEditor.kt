package com.esper.android.ui.lexical

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.esper.android.ui.components.LocalReadOnlyMode
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
    object AddNestedBulletList : LexicalAction
    object AddChecklist : LexicalAction
    object AddTable : LexicalAction
    object AddTimestamp : LexicalAction
    object AddDivider : LexicalAction
}

/**
 * Matches the `/code` slash command, optionally with a language tag
 * (`/code python`).
 */
private val SLASH_CODE_REGEX = Regex("^/code(?:\\s+([a-zA-Z0-9_+#.-]+))?$")

/**
 * Expands a `/code` (or `/code <language>`) line into a fenced code block.
 *
 * The block is created with an empty content line — the caret lands there so
 * the user can type the code straight away — and an escape paragraph is added
 * below the closing fence so the cursor can always leave the block again.
 *
 * Returns `null` when the text before the caret is not a slash command.
 */
internal fun expandSlashCode(fresh: TextFieldValue): TextFieldValue? {
    val text = fresh.text
    val cursorPos = fresh.selection.start
    if (cursorPos < 5) return null

    // Only the current line is considered: "/code" must be the whole line.
    val lineStart = text.lastIndexOf('\n', (cursorPos - 1).coerceAtLeast(0)) + 1
    val lineSegment = text.substring(lineStart, cursorPos)

    val match = SLASH_CODE_REGEX.matchEntire(lineSegment.trim()) ?: return null

    val language = match.groupValues.getOrNull(1)?.ifBlank { null } ?: "cpp"
    val prefix = text.substring(0, lineStart)
    val suffix = text.substring(cursorPos)

    // ```lang, an empty content line, the closing fence and an escape line.
    val insert = "```$language\n\n```\n\n"
    val newText = prefix + insert + suffix
    val cursorTarget = prefix.length + "```$language\n".length
    return TextFieldValue(newText, TextRange(cursorTarget))
}

/**
 * True when [text] ends with a fence that actually *closes* a code block.
 *
 * An even number of fence lines means the last one closes a block, so an
 * opening fence that the user is still typing is left alone.
 */
internal fun endsWithClosedFence(text: String): Boolean {
    if (text.isEmpty() || text.endsWith("\n")) return false
    if (text.substringAfterLast('\n').trim() != "```") return false
    val fenceCount = text.lines().count { it.trim().startsWith("```") }
    return fenceCount % 2 == 0
}

/**
 * Guarantees an escape paragraph below a code block that was typed by hand.
 *
 * When the markdown ends with a closing fence the caret would otherwise be
 * stuck on the fence line; appending a blank line gives the user somewhere to
 * write normal text again.
 */
internal fun ensureEscapeParagraph(fresh: TextFieldValue): TextFieldValue {
    if (!endsWithClosedFence(fresh.text)) return fresh
    val newText = fresh.text + "\n\n"
    return TextFieldValue(newText, TextRange(newText.length))
}

/**
 * `String.lines()` drops a trailing newline; this keeps it so a rewrite of
 * the markdown never silently drops the final line break.
 */
private fun trailingNewlineOf(text: String): String = if (text.endsWith("\n")) "\n" else ""

@Composable
fun ComposeLexicalEditor(
    initialMarkdown: String,
    onContentChanged: (String) -> Unit,
    onTimestampClicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = LocalReadOnlyMode.current,
    /** Journal UX: tapping anywhere starts editing — switches out of the
     *  rendered view, focuses the raw field and summons the keyboard. */
    tapToEditAnywhere: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val currentOnContentChanged by rememberUpdatedState(onContentChanged)
    var rawText by remember { mutableStateOf(initialMarkdown) }
    val currentRawText by rememberUpdatedState(rawText)
    var blocks by remember(rawText) { mutableStateOf(LexicalParser.parseToLexicalAst(rawText)) }
    var isRawEditMode by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Editable field state (text + cursor selection), so tap-anywhere can
    // place the cursor at the end the way "start writing" implies.
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(initialMarkdown, TextRange(initialMarkdown.length)))
    }

    // Tracking focus lets background syncs (SSE echo -> pull -> Room re-emit)
    // be buffered while the user is typing instead of resetting the field.
    var isFieldFocused by remember { mutableStateOf(false) }
    var pendingExternalText by remember { mutableStateOf<String?>(null) }

    // Tap-anywhere editing support: incrementing armRequest counts edit
    // intents (page taps, mode toggles) and fires focus + keyboard once the
    // raw field is composed. It never resets to 0, so a new intent always
    // re-key the effect without cancelling an in-flight focus request.
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var armRequest by remember { mutableIntStateOf(0) }

    fun commitText(newText: String) {
        if (readOnly) return
        rawText = newText
        blocks = LexicalParser.parseToLexicalAst(newText)
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(300)
            currentOnContentChanged(newText)
        }
    }

    /** Toolbar actions append at the content end; move both text and the
     *  cursor there afterwards. */
    fun commitToolbarAction(action: LexicalAction) {
        val updated = applyAction(rawText, action)
        commitText(updated)
        fieldValue = TextFieldValue(updated, TextRange(updated.length))
    }

    /** Remote/Room re-emissions: update source of truth + rendered blocks
     *  without scheduling a save (no local change to push back). */
    fun renderExternal(text: String) {
        rawText = text
        blocks = LexicalParser.parseToLexicalAst(text)
        fieldValue = TextFieldValue(text, TextRange(text.length))
    }

    /** Removes a code block — fence included — from the raw markdown. */
    fun deleteCodeBlock(block: LexicalCodeBlockNode) {
        if (readOnly) return
        val lines = rawText.lines()
        val start = block.lineStartIndex
        if (start !in lines.indices) return
        val end = block.lineEndIndex.coerceIn(start, lines.size - 1)
        val newLines = lines.toMutableList()
        newLines.subList(start, end + 1).clear()
        val newText = newLines.joinToString("\n") + trailingNewlineOf(rawText)
        commitText(newText)
        fieldValue = TextFieldValue(newText, TextRange(newText.length))
    }

    /** Rewrites the fence tag of a code block, e.g. ```python -> ```cpp. */
    fun changeCodeLanguage(block: LexicalCodeBlockNode, language: String) {
        if (readOnly) return
        val lines = rawText.lines()
        val index = block.lineStartIndex
        if (index !in lines.indices) return
        val fence = lines[index]
        val indent = fence.takeWhile { it == ' ' || it == '\t' }
        val newLines = lines.toMutableList()
        newLines[index] = indent + "```" + language
        val newText = newLines.joinToString("\n") + trailingNewlineOf(rawText)
        commitText(newText)
        fieldValue = TextFieldValue(newText, TextRange(newText.length))
    }

    // Synchronize external markdown changes (Room re-emissions) without ever
    // clobbering in-flight keystrokes: while the field is focused, incoming
    // text is buffered and applied only once the field is unfocused.
    LaunchedEffect(initialMarkdown) {
        if (rawText == initialMarkdown) return@LaunchedEffect
        if (isFieldFocused || debounceJob?.isActive == true) {
            pendingExternalText = initialMarkdown
        } else {
            renderExternal(initialMarkdown)
        }
    }

    // Drain the buffered remote/Room text (if any still differs) when the
    // user idles away from the field. Skip while a debounce is still saving
    // so a stale snapshot never replays over fresher local text.
    LaunchedEffect(isFieldFocused) {
        if (isFieldFocused) return@LaunchedEffect
        val buffered = pendingExternalText
        pendingExternalText = null
        if (buffered != null && debounceJob?.isActive != true && buffered != rawText) {
            renderExternal(buffered)
        }
    }

    // Flush pending changes on dispose so a 300ms window never drops the tail
    // of an edit when the user leaves the screen.
    DisposableEffect(Unit) {
        onDispose {
            if (debounceJob?.isActive == true) {
                debounceJob?.cancel()
                currentOnContentChanged(currentRawText)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(SpaceBlack)
    ) {
        // Formatting Toolbar (read-only: only the Native/Raw view toggle remains)
        LexicalToolbar(
            isRawMode = isRawEditMode,
            readOnly = readOnly,
            onToggleMode = {
                isRawEditMode = !isRawEditMode
                // Switching into the editable view from the chip is an explicit
                // edit intent: arm focus + keyboard alongside tap-anywhere.
                if (tapToEditAnywhere && isRawEditMode && !readOnly) armRequest++
            },
            onAction = { action -> commitToolbarAction(action) }
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
                        // Tap anywhere in the page (not just on the text line)
                        // to summon the cursor and keyboard.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (tapToEditAnywhere && !readOnly) armRequest++ }
                ) {
                    // Focus + keyboard once the field is composed, either via
                    // tap-anywhere or the Native/Raw chip toggling into edit.
                    LaunchedEffect(armRequest) {
                        if (armRequest == 0 || readOnly || !isRawEditMode) return@LaunchedEffect
                        // Tap-to-edit means "start writing": place the cursor
                        // at the end before requesting focus.
                        fieldValue = TextFieldValue(rawText, TextRange(rawText.length))
                        // Retry briefly: the field may still be entering the
                        // composition on the first frame after the mode flip.
                        repeat(3) {
                            delay(50)
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { fresh ->
                            if (!readOnly) {
                                // Keep the IME's own value (text + selection)
                                // exactly as delivered; only then mirror the
                                // text into rawText/blocks and save.
                                // `/code` is expanded into a real code block
                                // first, and a freshly closed fence gets an
                                // escape paragraph underneath.
                                val processed = expandSlashCode(fresh) ?: ensureEscapeParagraph(fresh)
                                fieldValue = processed
                                commitText(processed.text)
                            }
                        },
                        readOnly = readOnly,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (readOnly) TextSecondary else TextPrimary,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(IndigoAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFieldFocused = it.isFocused }
                    )
                    // Ample fixed empty space below the editor so typing text is always visible above the keyboard
                    Spacer(modifier = Modifier.height(340.dp))
                }
            } else {
                // Native Lexical Component Viewport with fixed bottom padding.
                // In tap-to-edit screens, any tap starts editing: flips to the
                // raw field and focuses it (child taps like links still win,
                // they consume their own touches first).
                val nativeTapModifier = if (tapToEditAnywhere && !readOnly) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            isRawEditMode = true
                            armRequest++
                        }
                    }
                } else Modifier
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(nativeTapModifier)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(blocks, key = { _, it -> it.key }) { _, block ->
                        LexicalBlockRenderer(
                            block = block,
                            onTimestampClicked = onTimestampClicked,
                            onToggleTask = { _ -> },
                            readOnly = readOnly,
                            onDeleteCodeBlock = { deleteCodeBlock(it) },
                            onChangeCodeLanguage = { target, language -> changeCodeLanguage(target, language) }
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
    onToggleTask: (Boolean) -> Unit,
    readOnly: Boolean = false,
    onDeleteCodeBlock: (LexicalCodeBlockNode) -> Unit = { },
    onChangeCodeLanguage: (LexicalCodeBlockNode, String) -> Unit = { _, _ -> }
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
            LexicalCodeBlock(
                block = block,
                readOnly = readOnly,
                onDeleteCodeBlock = onDeleteCodeBlock,
                onChangeCodeLanguage = onChangeCodeLanguage
            )
        }

        is LexicalListItemNode -> {
            // Left padding based on nesting depth (20.dp per indent level)
            val startPadding = (block.indentLevel * 20).dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (block.isTask) {
                    Checkbox(
                        checked = block.isChecked,
                        onCheckedChange = { onToggleTask(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = IndigoAccent,
                            uncheckedColor = TextMuted
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 4.dp)
                    )
                } else if (block.isOrdered) {
                    // Cycle format by nesting level: 1. -> a. -> i.
                    val prefix = when (block.indentLevel % 3) {
                        1 -> "${('a'.code + ((block.indexNumber ?: 1) - 1) % 26).toChar()}."
                        2 -> "i."
                        else -> "${block.indexNumber ?: 1}."
                    }
                    Text(
                        text = prefix,
                        color = IndigoGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.width(24.dp)
                    )
                } else {
                    // Cycle bullet marker: • (disc) -> ◦ (circle) -> ▪ (square)
                    val bulletChar = when (block.indentLevel % 3) {
                        1 -> "◦"
                        2 -> "▪"
                        else -> "•"
                    }
                    Text(
                        text = bulletChar,
                        color = if (block.indentLevel == 0) IndigoGlow else TextSecondary,
                        fontSize = if (bulletChar == "▪") 11.sp else 16.sp,
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

/**
 * Renders a fenced code block with syntax highlighting, a language badge
 * that doubles as a picker, a copy button and a delete button.
 */
@Composable
private fun LexicalCodeBlock(
    block: LexicalCodeBlockNode,
    readOnly: Boolean,
    onDeleteCodeBlock: (LexicalCodeBlockNode) -> Unit,
    onChangeCodeLanguage: (LexicalCodeBlockNode, String) -> Unit
) {
    val context = LocalContext.current
    var copied by remember(block.key) { mutableStateOf(false) }
    var isLanguageMenuOpen by remember(block.key) { mutableStateOf(false) }

    // Reset the "Copied!" confirmation a couple of seconds after the tap.
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

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
                // Language badge — tapping it opens the language picker.
                Box {
                    Text(
                        text = CodeSyntaxHighlighter.displayLanguageName(block.language),
                        color = IndigoGlow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !readOnly) { isLanguageMenuOpen = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    DropdownMenu(
                        expanded = isLanguageMenuOpen,
                        onDismissRequest = { isLanguageMenuOpen = false }
                    ) {
                        CodeSyntaxHighlighter.SUPPORTED_LANGUAGES.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 13.sp) },
                                onClick = {
                                    isLanguageMenuOpen = false
                                    onChangeCodeLanguage(block, value)
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy the block to the clipboard.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("code", block.code))
                                copied = true
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        if (copied) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Copied",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Copied!",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    if (!readOnly) {
                        // Remove the whole block from the note.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onDeleteCodeBlock(block) }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete code block",
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = CodeSyntaxHighlighter.highlight(block.code, block.language),
                color = Color(0xFFE0E7FF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun LexicalToolbar(
    isRawMode: Boolean,
    onToggleMode: () -> Unit,
    onAction: (LexicalAction) -> Unit,
    readOnly: Boolean = false
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

        if (!readOnly) {
            ToolbarBtn(label = "H1", onClick = { onAction(LexicalAction.AddH1) })
            ToolbarBtn(label = "H2", onClick = { onAction(LexicalAction.AddH2) })
            ToolbarBtn(label = "B", fontWeight = FontWeight.Bold, onClick = { onAction(LexicalAction.AddBold) })
            ToolbarBtn(label = "I", fontStyle = FontStyle.Italic, onClick = { onAction(LexicalAction.AddItalic) })
            ToolbarBtn(label = "S", onClick = { onAction(LexicalAction.AddStrike) })
            ToolbarBtn(label = "KaTeX $$", accent = AmberGlow, onClick = { onAction(LexicalAction.AddMathBlock) })
            ToolbarBtn(label = "KaTeX $", accent = AmberGlow, onClick = { onAction(LexicalAction.AddInlineMath) })
            ToolbarBtn(label = "Image", accent = EmeraldSync, onClick = { onAction(LexicalAction.AddImage) })
            ToolbarBtn(label = "List", onClick = { onAction(LexicalAction.AddBulletList) })
            ToolbarBtn(label = "Sub-list", accent = IndigoGlow, onClick = { onAction(LexicalAction.AddNestedBulletList) })
            ToolbarBtn(label = "Task", onClick = { onAction(LexicalAction.AddChecklist) })
            ToolbarBtn(label = "Table", onClick = { onAction(LexicalAction.AddTable) })
            ToolbarBtn(label = "Code", onClick = { onAction(LexicalAction.AddCodeBlock) })
            ToolbarBtn(label = "> Quote", onClick = { onAction(LexicalAction.AddQuote) })
            ToolbarBtn(label = "[00:15]", accent = IndigoGlow, onClick = { onAction(LexicalAction.AddTimestamp) })
            ToolbarBtn(label = "---", onClick = { onAction(LexicalAction.AddDivider) })
        }
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
        LexicalAction.AddBulletList -> "$content\n- Top-level item"
        LexicalAction.AddNestedBulletList -> "$content\n  - Nested sub-item"
        LexicalAction.AddChecklist -> "$content\n- [ ] Unfinished task\n- [x] Verified item"
        LexicalAction.AddTable -> "$content\n| Parameter | Value | Unit |\n|---|---|---|\n| Semi-Major Axis (a) | 1.496e8 | km |\n| Eccentricity (e) | 0.0167 | - |\n"
        // The trailing blank line is the escape paragraph: it lets the user
        // step out of the code block and keep writing normal notes.
        LexicalAction.AddCodeBlock -> "$content\n```python\ndef orbital_period(a, mu):\n    return 2 * math.pi * math.sqrt(a**3 / mu)\n```\n\n"
        LexicalAction.AddQuote -> "$content\n> Conservation of angular momentum yields constant areal velocity."
        LexicalAction.AddTimestamp -> "$content [00:15]"
        LexicalAction.AddDivider -> "$content\n---\n"
    }
}
