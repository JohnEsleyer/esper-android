package com.esper.android.ui.lexical

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inline scanner and the visual transformation that powers live editing.
 *
 * These tests pin down the contract that made WYSIWYG possible: what the user
 * sees is the source with markers removed, and every display offset maps back
 * to a real offset in the stored markdown.
 */
class LexicalInlineTest {

    private val colors = LexicalInlineColors(
        text = Color.White,
        muted = Color.Gray,
        code = Color.Green,
        codeBackground = Color.DarkGray,
        link = Color.Cyan,
        math = Color.Yellow,
        mathBackground = Color.Black,
        timestamp = Color.Magenta,
        timestampBackground = Color.DarkGray,
        highlightBackground = Color.Yellow
    )

    private fun transform(raw: String, caret: Int = raw.length): TransformedText =
        InlineMarkdownTransformation(caret, SpanStyle(fontSize = 14.sp), colors)
            .filter(AnnotatedString(raw))

    // ------------------------------------------------------------- tokenising

    @Test
    fun `the token stream tiles the source exactly`() {
        val raw = "a **b** [c](https://d) `e`"
        val scan = LexicalInline.scan(raw)

        assertEquals(0, scan.spans.first().rawStart)
        assertEquals(raw.length, scan.spans.last().rawEnd)
        scan.spans.zipWithNext { left, right ->
            assertEquals("gap between ${left.kind} and ${right.kind}", left.rawEnd, right.rawStart)
        }
    }

    @Test
    fun `markers are their own tokens`() {
        val scan = LexicalInline.scan("**bold**")

        assertEquals(
            listOf(LexicalInlineKind.MARKER, LexicalInlineKind.TEXT, LexicalInlineKind.MARKER),
            scan.spans.map { it.kind }
        )
        assertEquals("bold", scan.displayText)
    }

    @Test
    fun `styled runs keep their flags`() {
        val scan = LexicalInline.scan("**b** and *i* and ~~s~~")

        assertTrue(scan.spans.any { it.text == "b" && it.bold })
        assertTrue(scan.spans.any { it.text == "i" && it.italic })
        assertTrue(scan.spans.any { it.text == "s" && it.strike })
    }

    // --------------------------------------------------------- transformation

    @Test
    fun `markers are hidden when the caret is outside the construct`() {
        val out = transform("**bold**", caret = 8)
        assertEquals("bold", out.text.text)
    }

    @Test
    fun `markers are revealed while the caret is inside the construct`() {
        val out = transform("**bold**", caret = 4)
        assertEquals("**bold**", out.text.text)
    }

    @Test
    fun `links show only their label`() {
        val out = transform("see [the docs](https://example.com:8080/x) now", caret = 0)
        assertEquals("see the docs now", out.text.text)
    }

    @Test
    fun `inline code hides its backticks`() {
        val out = transform("run `pnpm dev` first", caret = 19)
        assertEquals("run pnpm dev first", out.text.text)
    }

    @Test
    fun `emoji shortcodes render as the glyph`() {
        val out = transform(":tada: ", caret = 7)
        assertEquals(LexicalInline.EMOJI.getValue("tada") + " ", out.text.text)
    }

    @Test
    fun `plain text is unchanged`() {
        val text = "just words"
        assertEquals(text, transform(text, caret = 0).text.text)
    }

    // --------------------------------------------------------- offset mapping

    @Test
    fun `display offsets map past the opening marker`() {
        // "**bold**": the visible "bold" starts at raw offset 2.
        assertEquals(2, LexicalInline.displayOffsetToRaw("**bold**", 0))
        assertEquals(4, LexicalInline.displayOffsetToRaw("**bold**", 2))
        assertEquals(6, LexicalInline.displayOffsetToRaw("**bold**", 4))
    }

    @Test
    fun `raw offsets map onto the displayed text`() {
        val mapping = transform("**bold**", caret = 8).offsetMapping

        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(0, mapping.originalToTransformed(2))
        assertEquals(2, mapping.originalToTransformed(4))
        assertEquals(4, mapping.originalToTransformed(8))
    }

    @Test
    fun `tapping the start of bold text puts the caret after the marker`() {
        val mapping = transform("**bold**", caret = 8).offsetMapping

        assertEquals(2, mapping.transformedToOriginal(0))
        assertEquals(8, mapping.transformedToOriginal(4))
    }

    @Test
    fun `identity mapping for unformatted text`() {
        val mapping = transform("plain", caret = 0).offsetMapping

        for (offset in 0..5) {
            assertEquals(offset, mapping.originalToTransformed(offset))
            assertEquals(offset, mapping.transformedToOriginal(offset))
        }
    }

    // ------------------------------------------------------------ emoji picker

    @Test
    fun `suggestions filter by prefix`() {
        val suggestions = LexicalInline.suggest("ta")

        assertTrue(suggestions.any { it.first == "tada" })
        assertFalse(suggestions.any { it.first == "smile" })
    }

    @Test
    fun `an empty prefix lists the most useful emoji first`() {
        val suggestions = LexicalInline.suggest("")

        assertEquals("smile", suggestions.first().first)
    }

    @Test
    fun `the active query is detected right after a colon`() {
        assertEquals("ta", activeEmojiQuery("nice :ta", androidx.compose.ui.text.TextRange(8)))
    }

    @Test
    fun `the active query ignores a clock time`() {
        assertEquals(null, activeEmojiQuery("10:30", androidx.compose.ui.text.TextRange(5)))
    }

    @Test
    fun `the active query survives a trailing colon`() {
        // Regression: splitting a block on a colon used to leave "…:" as the
        // last character and `activeEmojiQuery` read past the end of the string.
        assertEquals(null, activeEmojiQuery("note:", androidx.compose.ui.text.TextRange(5)))
        assertEquals(null, activeEmojiQuery("", androidx.compose.ui.text.TextRange(0)))
    }

    @Test
    fun `a bare colon is not a query`() {
        assertEquals(null, activeEmojiQuery("hi :", androidx.compose.ui.text.TextRange(4)))
        assertEquals(null, activeEmojiQuery("a : b", androidx.compose.ui.text.TextRange(4)))
    }

    @Test
    fun `the active query ignores non-collapsed selections`() {
        assertEquals(
            null,
            activeEmojiQuery(
                "nice :ta",
                androidx.compose.ui.text.TextRange(5, 8)
            )
        )
    }
}
