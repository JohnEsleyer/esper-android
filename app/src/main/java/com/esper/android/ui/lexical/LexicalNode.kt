package com.esper.android.ui.lexical

sealed interface LexicalNode

sealed class LexicalBlockNode : LexicalNode {
    abstract val key: String
}

data class LexicalHeadingNode(
    override val key: String,
    val level: Int, // 1 to 6
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

data class LexicalMathBlockNode(
    override val key: String,
    val latex: String
) : LexicalBlockNode()

data class LexicalImageNode(
    override val key: String,
    val altText: String,
    val imageUrl: String
) : LexicalBlockNode()

data class LexicalListItemNode(
    override val key: String,
    val isOrdered: Boolean,
    val indexNumber: Int?,
    val isTask: Boolean = false,
    val isChecked: Boolean = false,
    val inlines: List<LexicalInlineNode>
) : LexicalBlockNode()

data class LexicalHorizontalRuleNode(
    override val key: String
) : LexicalBlockNode()

data class LexicalTableNode(
    override val key: String,
    val headers: List<String>,
    val rows: List<List<String>>
) : LexicalBlockNode()

sealed interface LexicalInlineNode : LexicalNode

data class LexicalTextNode(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isCode: Boolean = false
) : LexicalInlineNode

data class LexicalLinkNode(
    val text: String,
    val url: String
) : LexicalInlineNode

data class LexicalInlineMathNode(
    val latex: String
) : LexicalInlineNode

data class LexicalTimestampNode(
    val rawText: String,
    val seconds: Int
) : LexicalInlineNode
