package com.esper.android.ui.lexical

import java.util.UUID

object LexicalParser {

    private val timestampRegex = Regex("\\[(\\d{1,2}:\\d{2})\\]")
    private val inlineMathRegex = Regex("\\$([^$]+)\\$")
    private val linkRegex = Regex("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)")
    private val imageRegex = Regex("^!\\[([^\\]]*)\\]\\((https?://[^)]+)\\)$")

    fun parseToLexicalAst(markdown: String): List<LexicalBlockNode> {
        val blocks = mutableListOf<LexicalBlockNode>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Calculate indentation level (supports both 2-space and 4-space markdown indents)
            val leadingSpaces = line.takeWhile { it == ' ' || it == '\t' }
                .fold(0) { count, char -> if (char == '\t') count + 4 else count + 1 }
            val indentLevel = (leadingSpaces / 2).coerceIn(0, 6)

            // 1. Math Block: $$
            if (trimmed.startsWith("$$")) {
                val mathLines = mutableListOf<String>()
                if (trimmed.length > 2 && trimmed.endsWith("$$") && trimmed != "$$") {
                    // Inline single-line math block: $$E = mc^2$$
                    mathLines.add(trimmed.removePrefix("$$").removeSuffix("$$").trim())
                } else {
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        mathLines.add(lines[i])
                        i++
                    }
                }
                blocks.add(
                    LexicalMathBlockNode(
                        key = UUID.randomUUID().toString(),
                        latex = mathLines.joinToString("\n")
                    )
                )
                i++
                continue
            }

            // 2. Code Block: ```
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim().ifEmpty { "text" }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(
                    LexicalCodeBlockNode(
                        key = UUID.randomUUID().toString(),
                        code = codeLines.joinToString("\n"),
                        language = lang
                    )
                )
                i++
                continue
            }

            // 3. Embedded Image: ![alt](url)
            val imgMatch = imageRegex.matchEntire(trimmed)
            if (imgMatch != null) {
                blocks.add(
                    LexicalImageNode(
                        key = UUID.randomUUID().toString(),
                        altText = imgMatch.groupValues[1],
                        imageUrl = imgMatch.groupValues[2]
                    )
                )
                i++
                continue
            }

            // 4. Horizontal Rule: ---, ***, ___
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(LexicalHorizontalRuleNode(key = UUID.randomUUID().toString()))
                i++
                continue
            }

            // 5. Headings: # to ######
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                if (trimmed.length > level && trimmed[level] == ' ') {
                    blocks.add(
                        LexicalHeadingNode(
                            key = UUID.randomUUID().toString(),
                            level = level,
                            inlines = parseInlineNodes(trimmed.substring(level).trim())
                        )
                    )
                    i++
                    continue
                }
            }

            // 6. Blockquote: >
            if (trimmed.startsWith(">")) {
                blocks.add(
                    LexicalQuoteNode(
                        key = UUID.randomUUID().toString(),
                        inlines = parseInlineNodes(trimmed.removePrefix(">").trim())
                    )
                )
                i++
                continue
            }

            // 7. Checkbox Tasks: - [ ] or - [x] (preserving indentation)
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ")) {
                val isChecked = trimmed.startsWith("- [x] ")
                val content = trimmed.substring(6).trim()
                blocks.add(
                    LexicalListItemNode(
                        key = UUID.randomUUID().toString(),
                        isOrdered = false,
                        indexNumber = null,
                        indentLevel = indentLevel,
                        isTask = true,
                        isChecked = isChecked,
                        inlines = parseInlineNodes(content)
                    )
                )
                i++
                continue
            }

            // 8. Bullet Lists: - , * , or + (preserving indentation)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                blocks.add(
                    LexicalListItemNode(
                        key = UUID.randomUUID().toString(),
                        isOrdered = false,
                        indexNumber = null,
                        indentLevel = indentLevel,
                        inlines = parseInlineNodes(trimmed.substring(2).trim())
                    )
                )
                i++
                continue
            }

            // 9. Ordered Lists: 1. , 2. etc (preserving indentation)
            val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)$").matchEntire(trimmed)
            if (orderedMatch != null) {
                val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
                val content = orderedMatch.groupValues[2]
                blocks.add(
                    LexicalListItemNode(
                        key = UUID.randomUUID().toString(),
                        isOrdered = true,
                        indexNumber = num,
                        indentLevel = indentLevel,
                        inlines = parseInlineNodes(content)
                    )
                )
                i++
                continue
            }

            // 10. Table: | col1 | col2 |
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].contains("---")) {
                val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                i += 2 // skip header + divider
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val rowCells = lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    rows.add(rowCells)
                    i++
                }
                blocks.add(
                    LexicalTableNode(
                        key = UUID.randomUUID().toString(),
                        headers = headers,
                        rows = rows
                    )
                )
                continue
            }

            // 11. Normal Paragraph
            if (trimmed.isNotEmpty()) {
                blocks.add(
                    LexicalParagraphNode(
                        key = UUID.randomUUID().toString(),
                        inlines = parseInlineNodes(trimmed)
                    )
                )
            }
            i++
        }

        return blocks
    }

    fun parseInlineNodes(text: String): List<LexicalInlineNode> {
        val result = mutableListOf<LexicalInlineNode>()
        var cursor = 0

        while (cursor < text.length) {
            val tsMatch = timestampRegex.find(text, cursor)
            val mathMatch = inlineMathRegex.find(text, cursor)
            val linkMatch = linkRegex.find(text, cursor)

            val earliest = listOfNotNull(tsMatch, mathMatch, linkMatch).minByOrNull { it.range.first }

            if (earliest == null) {
                result.addAll(parseStyling(text.substring(cursor)))
                break
            }

            if (earliest.range.first > cursor) {
                result.addAll(parseStyling(text.substring(cursor, earliest.range.first)))
            }

            when (earliest) {
                tsMatch -> {
                    val fullTs = tsMatch.groupValues[1]
                    val parts = fullTs.split(":")
                    val s = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                    result.add(LexicalTimestampNode(rawText = "[$fullTs]", seconds = s))
                }
                mathMatch -> {
                    result.add(LexicalInlineMathNode(latex = mathMatch.groupValues[1]))
                }
                linkMatch -> {
                    result.add(LexicalLinkNode(text = linkMatch.groupValues[1], url = linkMatch.groupValues[2]))
                }
            }
            cursor = earliest.range.last + 1
        }
        return result
    }

    private fun parseStyling(segment: String): List<LexicalInlineNode> {
        val list = mutableListOf<LexicalInlineNode>()
        val strikeRegex = Regex("~~([^~]+)~~")
        val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
        val italicRegex = Regex("\\*([^*]+)\\*")
        val codeRegex = Regex("`([^`]+)`")

        var idx = 0
        while (idx < segment.length) {
            val strike = strikeRegex.find(segment, idx)
            val bold = boldRegex.find(segment, idx)
            val italic = italicRegex.find(segment, idx)
            val code = codeRegex.find(segment, idx)

            val match = listOfNotNull(strike, bold, italic, code).minByOrNull { it.range.first }
            if (match == null) {
                list.add(LexicalTextNode(segment.substring(idx)))
                break
            }

            if (match.range.first > idx) {
                list.add(LexicalTextNode(segment.substring(idx, match.range.first)))
            }

            when (match) {
                strike -> list.add(LexicalTextNode(strike.groupValues[1], isStrikethrough = true))
                bold -> list.add(LexicalTextNode(bold.groupValues[1], isBold = true))
                italic -> list.add(LexicalTextNode(italic.groupValues[1], isItalic = true))
                code -> list.add(LexicalTextNode(code.groupValues[1], isCode = true))
            }
            idx = match.range.last + 1
        }
        return list
    }
}
