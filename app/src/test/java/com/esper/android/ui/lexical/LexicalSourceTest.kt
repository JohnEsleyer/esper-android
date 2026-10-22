package com.esper.android.ui.lexical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading and writing one block in place.
 *
 * These are the tests that keep live WYSIWYG editing from corrupting the
 * document: every edit must round-trip through the exact same markdown shape.
 */
class LexicalSourceTest {

    private fun blockOf(markdown: String, index: Int = 0): LexicalBlockNode =
        LexicalParser.parseToLexicalAst(markdown)[index]

    // ------------------------------------------------------------- reading

    @Test
    fun `heading text excludes the marker`() {
        val markdown = "## Orbital mechanics"
        assertEquals(
            "Orbital mechanics",
            LexicalSource.editableText(markdown, blockOf(markdown))
        )
    }

    @Test
    fun `quote text excludes every chevron`() {
        val markdown = "> first\n> second"
        assertEquals("first\nsecond", LexicalSource.editableText(markdown, blockOf(markdown)))
    }

    @Test
    fun `list item text excludes marker and continuation indent`() {
        val markdown = "- item one\n  continued"
        assertEquals("item one\ncontinued", LexicalSource.editableText(markdown, blockOf(markdown)))
    }

    @Test
    fun `task item text excludes the checkbox`() {
        val markdown = "- [x] done"
        assertEquals("done", LexicalSource.editableText(markdown, blockOf(markdown)))
    }

    @Test
    fun `code block text is the body without fences`() {
        val markdown = "```cpp\nint x = 1;\n```"
        assertEquals("int x = 1;", LexicalSource.editableText(markdown, blockOf(markdown)))
    }

    @Test
    fun `math block text is the latex without delimiters`() {
        val markdown = "$$\nE = mc^2\n$$"
        assertEquals("E = mc^2", LexicalSource.editableText(markdown, blockOf(markdown)))
    }

    // ------------------------------------------------------------- writing

    @Test
    fun `editing a paragraph leaves the rest of the document alone`() {
        val markdown = "# Title\n\nold body\n\n---"
        val paragraph = LexicalParser.parseToLexicalAst(markdown)
            .filterIsInstance<LexicalParagraphNode>()
            .single()

        val updated = LexicalSource.withEditableText(markdown, paragraph, "new body")

        assertEquals("# Title\n\nnew body\n\n---", updated)
    }

    @Test
    fun `editing a heading restores its marker`() {
        val markdown = "## Old"
        val updated = LexicalSource.withEditableText(markdown, blockOf(markdown), "New")

        assertEquals("## New", updated)
    }

    @Test
    fun `editing a quote restores every chevron`() {
        val markdown = "> one\n> two"
        val updated = LexicalSource.withEditableText(markdown, blockOf(markdown), "a\nb")

        assertEquals("> a\n> b", updated)
    }

    @Test
    fun `editing a list item keeps its marker and continuation indent`() {
        val markdown = "- one\n  two"
        val block = blockOf(markdown) as LexicalListItemNode
        val updated = LexicalSource.withEditableText(markdown, block, "alpha\nbeta")

        assertEquals("- alpha\n  beta", updated)
    }

    @Test
    fun `editing a task item keeps its state`() {
        val markdown = "- [x] done"
        val updated = LexicalSource.withEditableText(markdown, blockOf(markdown), "still done")

        assertEquals("- [x] still done", updated)
    }

    @Test
    fun `editing a code block keeps the fences and language`() {
        val markdown = "```js\nconst x = 1;\n```"
        val updated = LexicalSource.withEditableText(markdown, blockOf(markdown), "const y = 2;\nconst z = 3;")

        assertEquals("```js\nconst y = 2;\nconst z = 3;\n```", updated)
    }

    @Test
    fun `editing a single line math block keeps the delimiters`() {
        val markdown = "\$\$x^2\$\$"
        val updated = LexicalSource.withEditableText(markdown, blockOf(markdown), "y^2")

        assertEquals("\$\$y^2\$\$", updated)
    }

    @Test
    fun `editing the synthetic trailing paragraph appends a line`() {
        val markdown = "- a"
        val line = markdown.split('\n').size
        val synthetic = LexicalParagraphNode(
            key = "p@tail",
            inlines = emptyList(),
            lineStart = line,
            lineEnd = line
        )

        val updated = LexicalSource.withEditableText(markdown, synthetic, "after the list")

        assertEquals("- a\nafter the list", updated)
    }

    @Test
    fun `removing a block removes its fences too`() {
        val markdown = "intro\n\n```js\nx\n```\n\noutro"
        val code = LexicalParser.parseToLexicalAst(markdown)
            .filterIsInstance<LexicalCodeBlockNode>()
            .single()

        assertEquals("intro\n\n\noutro", LexicalSource.removeBlock(markdown, code))
    }

    @Test
    fun `toggling a task rewrites only the checkbox`() {
        val markdown = "- [ ] todo"
        val block = blockOf(markdown) as LexicalListItemNode

        assertEquals("- [x] todo", LexicalSource.setTaskChecked(markdown, block, true))
    }

    // -------------------------------------------------------------- splitting

    @Test
    fun `splitting a paragraph creates a second paragraph`() {
        val markdown = "hello world"
        val block = blockOf(markdown)

        val result = LexicalSource.splitBlock(markdown, block, "hello world", 5)
        assertNotNull(result)
        // A paragraph break needs a blank line between the two paragraphs.
        assertEquals("hello\n\n world", result!!.markdown)
        assertEquals(2, result.focusLine)

        val parsed = LexicalParser.parseToLexicalAst(result.markdown)
        assertEquals(2, parsed.count { it is LexicalParagraphNode })
    }

    @Test
    fun `splitting a heading produces a plain paragraph`() {
        val markdown = "## Title"
        val block = blockOf(markdown)

        val result = LexicalSource.splitBlock(markdown, block, "Title", 3)!!
        val parsed = LexicalParser.parseToLexicalAst(result.markdown)

        assertEquals(1, parsed.count { it is LexicalHeadingNode })
        assertEquals(1, parsed.count { it is LexicalParagraphNode })
    }

    @Test
    fun `splitting a list item continues the list`() {
        val markdown = "- one"
        val block = blockOf(markdown)

        val result = LexicalSource.splitBlock(markdown, block, "one", 3)!!
        val items = LexicalParser.parseToLexicalAst(result.markdown)
            .filterIsInstance<LexicalListItemNode>()

        assertEquals(2, items.size)
        assertEquals("one", items[0].plainText)
    }

    @Test
    fun `enter on an empty list item leaves the list`() {
        val markdown = "- one\n- "
        val block = LexicalParser.parseToLexicalAst(markdown)
            .filterIsInstance<LexicalListItemNode>()
            .last()

        val result = LexicalSource.splitBlock(markdown, block, "", 0)!!
        val parsed = LexicalParser.parseToLexicalAst(result.markdown)

        assertEquals(1, parsed.count { it is LexicalListItemNode })
    }

    @Test
    fun `code blocks absorb the newline instead of splitting`() {
        val markdown = "```js\nx\n```"
        val block = blockOf(markdown)

        assertNull(LexicalSource.splitBlock(markdown, block, "x", 1))
        assertTrue(LexicalSource.keepsNewlines(block))
    }
}
