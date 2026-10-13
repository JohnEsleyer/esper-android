package com.esper.android.ui.lexical

import java.util.UUID

object LexicalParser {

    private val timestampRegex = Regex("\\[(\\d{1,2}:\\d{2})\\]")

    /**
     * Parses markdown text into a Lexical Node AST.
     */
    fun parseToLexicalAst(markdown: String): List<LexicalBlockNode> {
        val blocks = mutableListOf<LexicalBlockNode>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Code block node: ```
            if (line.trimStart().startsWith("```")) {
                val lang = line.trim().removePrefix("```").trim().ifEmpty { "markdown" }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
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

            // Heading node: #, ##, ###
            when {
                line.startsWith("### ") -> {
                    blocks.add(
                        LexicalHeadingNode(
                            key = UUID.randomUUID().toString(),
                            level = 3,
                            inlines = parseInlineNodes(line.removePrefix("### ").trim())
                        )
                    )
                }
                line.startsWith("## ") -> {
                    blocks.add(
                        LexicalHeadingNode(
                            key = UUID.randomUUID().toString(),
                            level = 2,
                            inlines = parseInlineNodes(line.removePrefix("## ").trim())
                        )
                    )
                }
                line.startsWith("# ") -> {
                    blocks.add(
                        LexicalHeadingNode(
                            key = UUID.randomUUID().toString(),
                            level = 1,
                            inlines = parseInlineNodes(line.removePrefix("# ").trim())
                        )
                    )
                }
                line.startsWith("> ") -> {
                    blocks.add(
                        LexicalQuoteNode(
                            key = UUID.randomUUID().toString(),
                            inlines = parseInlineNodes(line.removePrefix("> ").trim())
                        )
                    )
                }
                line.isNotBlank() -> {
                    blocks.add(
                        LexicalParagraphNode(
                            key = UUID.randomUUID().toString(),
                            inlines = parseInlineNodes(line)
                        )
                    )
                }
            }
            i++
        }

        return blocks
    }

    /**
     * Parses inline text, markdown styling, and timestamp decorators [mm:ss].
     */
    fun parseInlineNodes(text: String): List<LexicalInlineNode> {
        val nodes = mutableListOf<LexicalInlineNode>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val match = timestampRegex.find(text, currentIndex)
            if (match != null && match.range.first == currentIndex) {
                val fullTs = match.groupValues[1]
                val parts = fullTs.split(":")
                val seconds = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                nodes.add(LexicalTimestampNode(rawText = "[$fullTs]", seconds = seconds))
                currentIndex = match.range.last + 1
                continue
            }

            val nextTsMatch = timestampRegex.find(text, currentIndex)
            val segmentEnd = nextTsMatch?.range?.first ?: text.length
            val rawSegment = text.substring(currentIndex, segmentEnd)

            if (rawSegment.isNotEmpty()) {
                nodes.addAll(parseFormattedText(rawSegment))
            }
            currentIndex = segmentEnd
        }

        return nodes
    }

    private fun parseFormattedText(segment: String): List<LexicalInlineNode> {
        val inlineList = mutableListOf<LexicalInlineNode>()
        val codeRegex = Regex("`([^`]+)`")
        val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
        val italicRegex = Regex("\\*([^*]+)\\*")

        var idx = 0
        while (idx < segment.length) {
            val codeMatch = codeRegex.find(segment, idx)
            val boldMatch = boldRegex.find(segment, idx)
            val italicMatch = italicRegex.find(segment, idx)

            val earliest = listOfNotNull(codeMatch, boldMatch, italicMatch).minByOrNull { it.range.first }

            if (earliest == null) {
                inlineList.add(LexicalTextNode(segment.substring(idx)))
                break
            }

            if (earliest.range.first > idx) {
                inlineList.add(LexicalTextNode(segment.substring(idx, earliest.range.first)))
            }

            when (earliest) {
                codeMatch -> inlineList.add(LexicalTextNode(codeMatch.groupValues[1], isCode = true))
                boldMatch -> inlineList.add(LexicalTextNode(boldMatch.groupValues[1], isBold = true))
                italicMatch -> inlineList.add(LexicalTextNode(italicMatch.groupValues[1], isItalic = true))
            }
            idx = earliest.range.last + 1
        }

        return inlineList
    }

    /**
     * Serializes a block back into canonical Markdown string.
     */
    fun serializeBlock(node: LexicalBlockNode): String {
        return when (node) {
            is LexicalHeadingNode -> {
                val prefix = "#".repeat(node.level) + " "
                prefix + node.inlines.joinToString("") { serializeInline(it) }
            }
            is LexicalParagraphNode -> node.inlines.joinToString("") { serializeInline(it) }
            is LexicalQuoteNode -> "> " + node.inlines.joinToString("") { serializeInline(it) }
            is LexicalCodeBlockNode -> "```${node.language}\n${node.code}\n```"
        }
    }

    private fun serializeInline(node: LexicalInlineNode): String {
        return when (node) {
            is LexicalTextNode -> when {
                node.isCode -> "`${node.text}`"
                node.isBold -> "**${node.text}**"
                node.isItalic -> "*${node.text}*"
                else -> node.text
            }
            is LexicalTimestampNode -> node.rawText
        }
    }
}
