package com.esper.android.ui.lexical

sealed interface LexicalNode

sealed class LexicalBlockNode : LexicalNode {
    abstract val key: String
}

data class LexicalHeadingNode(
    override val key: String,
    val level: Int, // 1, 2, 3
    val inlines: List<LexicalInlineNode>
) : LexicalBlockNode()

data class LexicalParagraphNode(
    override val key: String,
    val inlines: List<LexicalInlineNode>
) : LexicalBlockNode()

data class LexicalQuoteNode(
    override val key: String,
    val inlines: List<LexicalInlineNode>
) : LexicalBlockNode()

data class LexicalCodeBlockNode(
    override val key: String,
    val code: String,
    val language: String = "markdown"
) : LexicalBlockNode()

sealed interface LexicalInlineNode : LexicalNode

data class LexicalTextNode(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isCode: Boolean = false
) : LexicalInlineNode

data class LexicalTimestampNode(
    val rawText: String,
    val seconds: Int
) : LexicalInlineNode
