package com.esper.android.ui.lexical

/**
 * Reads and writes a single block's text inside the full markdown document.
 *
 * The live editor renders each block in place; when the user types, only the
 * lines belonging to that block are rewritten, so the rest of the document
 * (including constructs the block editor does not own, like tables and images)
 * is byte-for-byte preserved.
 */
object LexicalSource {

    /** Blocks that the live editor can turn into a text field. */
    fun canEditInPlace(block: LexicalBlockNode): Boolean = when (block) {
        is LexicalParagraphNode,
        is LexicalHeadingNode,
        is LexicalQuoteNode,
        is LexicalListItemNode,
        is LexicalCodeBlockNode,
        is LexicalMathBlockNode -> true
        else -> false
    }

    /** True when Enter should insert a line inside the block instead of splitting it. */
    fun keepsNewlines(block: LexicalBlockNode): Boolean =
        block is LexicalCodeBlockNode ||
            block is LexicalMathBlockNode ||
            // A quote is one continuous block: its lines are line breaks.
            block is LexicalQuoteNode

    /**
     * The editable text of [block]: the content without the structural marker
     * (`#`, `>`, `- `, the code fences, …).
     */
    fun editableText(markdown: String, block: LexicalBlockNode): String {
        val lines = markdown.split('\n')
        return when (block) {
            is LexicalCodeBlockNode -> block.code
            is LexicalMathBlockNode -> block.latex
            is LexicalHeadingNode ->
                slice(lines, block)?.mapIndexed { index, line ->
                    if (index == 0) stripHeading(line) else line
                }?.joinToString("\n") ?: ""
            is LexicalQuoteNode ->
                slice(lines, block)?.joinToString("\n") { stripQuote(it) } ?: ""
            is LexicalListItemNode ->
                slice(lines, block)?.mapIndexed { index, line ->
                    if (index == 0) stripMarker(line, block) else stripColumns(line, block.contentColumn)
                }?.joinToString("\n") ?: ""
            else -> slice(lines, block)?.joinToString("\n") ?: ""
        }
    }

    /**
     * Writes [text] back into [block]'s source lines, re-applying the
     * structural marker so the document keeps its shape.
     */
    fun withEditableText(markdown: String, block: LexicalBlockNode, text: String): String {
        val lines = markdown.split('\n').toMutableList()

        // Synthetic trailing paragraph used to keep an empty document editable.
        if (block.lineStart >= lines.size) {
            val extra = text.split('\n')
            while (lines.size < block.lineStart) lines.add("")
            lines.addAll(extra)
            return lines.joinToString("\n")
        }

        val start = block.lineStart.coerceIn(0, lines.size - 1)
        val end = block.lineEnd.coerceIn(start, lines.size - 1)

        val replacement: List<String> = when (block) {
            is LexicalHeadingNode -> text.split('\n').mapIndexed { index, line ->
                if (index == 0) "${"#".repeat(block.level)} $line" else line
            }
            is LexicalQuoteNode -> text.split('\n').map { "> $it" }
            is LexicalListItemNode -> {
                val taskPrefix = when {
                    !block.isTask -> ""
                    block.isChecked -> "[x] "
                    else -> "[ ] "
                }
                val continuation = " ".repeat(block.contentColumn.coerceAtLeast(1))
                text.split('\n').mapIndexed { index, line ->
                    if (index == 0) block.indentPrefix + block.marker + taskPrefix + line
                    else continuation + line
                }
            }
            is LexicalCodeBlockNode -> {
                val opening = lines[start]
                val closing = lines[end]
                val closed = closing.trim().let { it.startsWith("```") || it.startsWith("~~~") }
                buildList {
                    add(opening)
                    addAll(text.split('\n'))
                    if (closed) add(closing)
                }
            }
            is LexicalMathBlockNode -> {
                if (start == end) {
                    val original = lines[start].trim()
                    val bracket = original.startsWith("\\[")
                    val open = if (bracket) "\\[" else "$$"
                    val close = if (bracket) "\\]" else "$$"
                    if (text.contains('\n')) {
                        buildList { add(open); addAll(text.split('\n')); add(close) }
                    } else {
                        listOf(open + text + close)
                    }
                } else {
                    val opening = lines[start]
                    val closing = lines[end]
                    buildList { add(opening); addAll(text.split('\n')); add(closing) }
                }
            }
            else -> text.split('\n')
        }

        lines.subList(start, end + 1).let {
            it.clear()
            it.addAll(replacement)
        }
        return lines.joinToString("\n")
    }

    /** Removes a block (fences included) from the document. */
    fun removeBlock(markdown: String, block: LexicalBlockNode): String {
        val lines = markdown.split('\n').toMutableList()
        if (block.lineStart >= lines.size) return markdown
        val start = block.lineStart
        val end = block.lineEnd.coerceIn(start, lines.size - 1)
        lines.subList(start, end + 1).clear()
        return lines.joinToString("\n")
    }

    /** Flips the `[ ]` / `[x]` marker of a task item without touching the text. */
    fun setTaskChecked(markdown: String, block: LexicalListItemNode, checked: Boolean): String {
        val lines = markdown.split('\n').toMutableList()
        val index = block.lineStart
        if (index !in lines.indices) return markdown
        val line = lines[index]
        val existing = listOf("[ ] ", "[x] ", "[X] ").firstOrNull { line.contains(it) } ?: return markdown
        lines[index] = line.replaceFirst(existing, if (checked) "[x] " else "[ ] ")
        return lines.joinToString("\n")
    }

    /**
     * Splits [block] at [offset] and returns the updated document plus the
     * source line where the new block starts (for focus hand-over).
     */
    data class SplitResult(val markdown: String, val focusLine: Int)

    fun splitBlock(
        markdown: String,
        block: LexicalBlockNode,
        text: String,
        offset: Int
    ): SplitResult? {
        if (!canEditInPlace(block) || keepsNewlines(block)) return null
        val safeOffset = offset.coerceIn(0, text.length)
        val before = text.substring(0, safeOffset)
        val after = text.substring(safeOffset).removePrefix("\n")

        // Enter on an empty list item leaves the list (Notion behaviour).
        if (block is LexicalListItemNode && before.isBlank()) {
            val lines = markdown.split('\n').toMutableList()
            val start = block.lineStart.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            val end = block.lineEnd.coerceIn(start, (lines.size - 1).coerceAtLeast(0))
            lines.subList(start, end + 1).clear()
            lines.addAll(start, after.split('\n'))
            return SplitResult(lines.joinToString("\n"), start)
        }

        var updated = withEditableText(markdown, block, before)
        val beforeLines = before.count { it == '\n' } + 1
        val insertAt = block.lineStart + beforeLines

        val prefix = when (block) {
            is LexicalListItemNode ->
                block.indentPrefix + block.marker + if (block.isTask) "[ ] " else ""
            else -> ""
        }
        val body = after.split('\n').mapIndexed { index, line ->
            if (index == 0) prefix + line else line
        }

        // A bare newline joins two lines of the *same* paragraph. Two
        // paragraphs need a blank line between them, so pressing Enter on a
        // paragraph inserts that separator (exactly what the user sees).
        val needsSeparator = block is LexicalParagraphNode && before.isNotBlank()
        val newLines = if (needsSeparator) listOf("") + body else body
        val focusLine = if (needsSeparator) insertAt + 1 else insertAt

        updated = insertLines(updated, insertAt, newLines)
        return SplitResult(updated, focusLine)
    }

    /** Appends a new empty paragraph after [afterLine] (or at the end). */
    fun appendParagraph(markdown: String, afterLine: Int? = null): SplitResult {
        val lines = markdown.split('\n').toMutableList()
        val at = (afterLine ?: lines.size).coerceIn(0, lines.size)
        lines.add(at, "")
        return SplitResult(lines.joinToString("\n"), at)
    }

    // -------------------------------------------------------------- internals

    private fun slice(lines: List<String>, block: LexicalBlockNode): List<String>? {
        if (lines.isEmpty()) return null
        if (block.lineStart !in lines.indices) {
            return if (block.lineStart >= lines.size) emptyList() else null
        }
        val end = block.lineEnd.coerceIn(block.lineStart, lines.size - 1)
        return lines.subList(block.lineStart, end + 1)
    }

    private fun stripHeading(line: String): String =
        headingPrefix.replaceFirst(line.trimStart(), "")

    private val headingPrefix = Regex("""^#{1,6}[ \t]+""")

    private fun stripQuote(line: String): String {
        val trimmed = line.trimStart()
        return when {
            trimmed.startsWith("> ") -> trimmed.substring(2)
            trimmed.startsWith(">") -> trimmed.substring(1)
            else -> trimmed
        }
    }

    private fun stripMarker(line: String, block: LexicalListItemNode): String {
        var result = line
        if (result.startsWith(block.indentPrefix)) {
            result = result.substring(block.indentPrefix.length)
        }
        if (result.startsWith(block.marker)) {
            result = result.substring(block.marker.length)
        }
        if (block.isTask) {
            result = result.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] ")
        }
        return result
    }

    private fun stripColumns(line: String, columns: Int): String {
        var width = 0
        var index = 0
        while (index < line.length && width < columns) {
            when (line[index]) {
                ' ' -> width++
                '\t' -> width += 4
                else -> return line.substring(index)
            }
            index++
        }
        return line.substring(index)
    }

    private fun insertLines(markdown: String, at: Int, newLines: List<String>): String {
        val lines = markdown.split('\n').toMutableList()
        val index = at.coerceIn(0, lines.size)
        lines.addAll(index, newLines)
        return lines.joinToString("\n")
    }
}
