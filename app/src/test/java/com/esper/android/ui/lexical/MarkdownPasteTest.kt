package com.esper.android.ui.lexical

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Detecting a Markdown paste inside the live field.
 *
 * The field has to tell three cases apart without seeing the clipboard:
 * ordinary typing (keep the caret), Enter (split the block) and a paste of
 * Markdown (leave the field so the parser can render it).
 */
class MarkdownPasteTest {

    private fun paste(
        old: String,
        new: String,
        selection: TextRange = TextRange(old.length),
        keepsNewlines: Boolean = false
    ) = pastedMarkdownIn(old, new, selection, keepsNewlines)

    @Test
    fun `typing a single character is not a paste`() {
        assertNull(paste("hello", "hello!"))
    }

    @Test
    fun `typing a plain word is not a paste`() {
        assertNull(paste("", "hello"))
    }

    @Test
    fun `pasting plain prose on one line is not a paste`() {
        assertNull(paste("", "Just a sentence."))
    }

    @Test
    fun `pasting a heading leaves the field`() {
        assertEquals("# Title", paste("", "# Title"))
    }

    @Test
    fun `pasting inline markdown leaves the field`() {
        assertEquals("a **bold** word", paste("", "a **bold** word"))
    }

    @Test
    fun `pasting several lines leaves the field`() {
        val pasted = "line one\nline two"
        assertEquals(pasted, paste("", pasted))
    }

    @Test
    fun `pasting over a selection returns only the inserted text`() {
        assertEquals(
            "## Heading",
            paste("Keep THIS.", "Keep ## Heading.", TextRange(5, 9))
        )
    }

    @Test
    fun `pasting a whole chatgpt answer leaves the field`() {
        val answer = "## Title\n\n- a\n- b\n\n```kotlin\nval x = 1\n```"
        assertEquals(answer, paste("", answer))
    }

    @Test
    fun `enter inside a paragraph is not a paste`() {
        assertNull(paste("hello", "hello\n"))
        assertNull(paste("hello", "hel\nlo", TextRange(3)))
    }

    @Test
    fun `code and math blocks keep their newlines`() {
        assertNull(paste("val x = 1", "val x = 1\nval y = 2", keepsNewlines = true))
        assertNull(paste("x^2", "x^2\n+ y", keepsNewlines = true))
    }
}
