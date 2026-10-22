package com.esper.android.ui.lexical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rebuilding block structure from flattened chat-client Markdown.
 *
 * Both Gemini and ChatGPT copy as one line per paragraph with single newlines
 * between everything; Gemini also drops code fences in favour of indentation.
 * Pasting that verbatim used to render as one long paragraph, which is what
 * "paste does not render" looked like on the device.
 */
class MarkdownNormalizeTest {

    private fun normalize(text: String) = LexicalSource.normalizePastedMarkdown(text)

    /** The exact Gemini answer that failed on the device (3465 characters). */
    private val gemini: String by lazy {
        MarkdownNormalizeTest::class.java.getResource("/gemini-rust.md")!!.readText()
    }

    @Test
    fun `gemini answer is rebuilt into paragraphs lists and fenced code`() {
        val normalized = normalize(gemini)

        // Paragraphs that arrived as consecutive lines now have air between them.
        assertTrue(normalized.contains("ownership.\n\nTo get you up to speed"))
        assertTrue(normalized.contains("code examples.\n\n1. Variables and Mutability"))

        // Code that arrived unfenced is fenced, and the indentation survives.
        assertTrue(normalized.contains("```rust\nfn main() {\n    let x = 5;"))
        assertTrue(normalized.contains("    y = 15;         // This works fine!"))
        assertTrue(normalized.contains("\n}\n```"))

        // Hand-written bullets lose the stray leading space.
        assertTrue(normalized.contains("\n* Scalars: Integers"))
        assertTrue(normalized.contains("\n* Compound: Tuples and Arrays."))

        // Stand-alone short lines between blocks become headings.
        assertTrue(normalized.contains("\n## References and Borrowing\n"))
        assertTrue(normalized.contains("\n## Next Steps & Practice\n"))

        // A code block whose opening brace is followed by a comment is still code.
        assertTrue(normalized.contains("fn calculate_length(s: &String) -> usize {"))
        assertTrue(normalized.contains("    s.len()\n}\n```"))

        // Nothing was lost on the way through.
        assertTrue(normalized.startsWith("Welcome to Rust!"))
        assertTrue(normalized.trimEnd().endsWith("writing your first small project?"))
    }

    @Test
    fun `gemini answer parses into rich blocks`() {
        val updated = LexicalSource.insertMarkdownAtBlock(
            currentMarkdown = "",
            block = LexicalParagraphNode("p@tail", emptyList(), 1, 1),
            pastedMarkdown = gemini
        )
        val parsed = LexicalParser.parseToLexicalAst(updated)

        val code = parsed.filterIsInstance<LexicalCodeBlockNode>()
        assertTrue("expected fenced code, got ${code.size}", code.size >= 4)
        assertTrue(code.all { it.language == "rust" })
        assertTrue(code.any { it.code.contains("let guess: u32") })

        assertTrue(parsed.count { it is LexicalListItemNode } >= 4)
        assertTrue(parsed.count { it is LexicalParagraphNode } >= 5)

        // The API header survived, so the document is complete.
        assertTrue(updated.contains("Next Steps & Practice"))
    }

    @Test
    fun `hard wrapped chatgpt prose is joined`() {
        val wrapped = "Coroutines are cooperative, so they do not\nblock a thread."

        assertEquals("Coroutines are cooperative, so they do not block a thread.", normalize(wrapped))
    }

    @Test
    fun `a sentence that already ended starts a new paragraph`() {
        assertEquals("First paragraph.\n\nSecond paragraph.", normalize("First paragraph.\nSecond paragraph."))
    }

    @Test
    fun `explicit fences and their contents are untouched`() {
        val fenced = "```kotlin\nfun main() {\n    println(\"hi\")\n}\n```"
        assertEquals(fenced, normalize(fenced))
    }

    @Test
    fun `two trailing spaces keep the hard break`() {
        assertEquals("line one  \nline two", normalize("line one  \nline two"))
    }

    @Test
    fun `leading-space bullets become list items`() {
        val parsed = LexicalParser.parseToLexicalAst(
            LexicalSource.insertMarkdownAtBlock(
                currentMarkdown = "",
                block = LexicalParagraphNode("p@tail", emptyList(), 1, 1),
                pastedMarkdown = " * alpha\n * beta"
            )
        )
        assertEquals(2, parsed.count { it is LexicalListItemNode })
    }

    @Test
    fun `a single plain line is left alone`() {
        assertEquals("Just a sentence.", normalize("Just a sentence."))
        assertEquals("# Title", normalize("# Title"))
        assertEquals("a **bold** word", normalize("a **bold** word"))
    }

    @Test
    fun `windows newlines and non-breaking spaces are normalised`() {
        val normalized = normalize("First\r\nsecond\u00A0line")
        assertFalse(normalized.contains('\r'))
        assertFalse(normalized.contains('\u00A0'))
    }
}
