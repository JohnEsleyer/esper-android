package com.esper.android.ui.lexical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Document-level undo, including the burst coalescing that keeps typing a
 * word from becoming one undo step per keystroke.
 */
class LexicalHistoryTest {

    @Test
    fun `nothing to undo on a fresh document`() {
        val history = LexicalHistory()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo("a"))
        assertNull(history.redo("a"))
    }

    @Test
    fun `undo returns the document before the edit`() {
        val history = LexicalHistory()
        history.record("first", now = 0)
        assertEquals("first", history.undo("second"))
        assertTrue(history.canRedo)
        assertFalse(history.canUndo)
    }

    @Test
    fun `redo returns the document after the edit`() {
        val history = LexicalHistory()
        history.record("first", now = 0)
        history.undo("second")
        assertEquals("second", history.redo("first"))
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `rapid edits collapse into one step`() {
        val history = LexicalHistory(burstMs = 700)
        history.record("doc0", now = 0)
        history.record("doc1", now = 100)
        history.record("doc2", now = 200)

        assertEquals(1, history.undoSize)
        assertEquals("doc0", history.undo("doc3"))
    }

    @Test
    fun `a pause starts a new step`() {
        val history = LexicalHistory(burstMs = 700)
        history.record("doc0", now = 0)
        history.record("doc1", now = 900)

        assertEquals(2, history.undoSize)
        assertEquals("doc1", history.undo("doc2"))
        assertEquals("doc0", history.undo("doc1"))
    }

    @Test
    fun `a fresh edit clears the redo branch`() {
        val history = LexicalHistory()
        history.record("doc0", now = 0)
        history.undo("doc1")
        assertTrue(history.canRedo)

        history.record("doc0-again", now = 5000)
        assertFalse(history.canRedo)
    }

    @Test
    fun `history is bounded`() {
        val history = LexicalHistory(limit = 3)
        for (i in 0..9) history.record("doc$i", now = i * 1000L)
        assertEquals(3, history.undoSize)
    }

    @Test
    fun `clear drops both directions`() {
        val history = LexicalHistory()
        history.record("doc0", now = 0)
        history.undo("doc1")
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
