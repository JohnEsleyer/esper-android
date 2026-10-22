package com.esper.android.ui.lexical

/**
 * Bounded undo/redo stack for the whole markdown document.
 *
 * The live editor renders one block at a time, but history has to span blocks:
 * a paste can replace a paragraph with twenty, and the IME's own undo only
 * knows about the field it is attached to. So snapshots are whole documents.
 *
 * Rapid edits are coalesced: typing a word is one step, not one step per
 * keystroke. A new step is recorded when the previous one is older than
 * [burstMs], which keeps the stack small and the undo distance short.
 */
class LexicalHistory(
    private val limit: Int = 100,
    private val burstMs: Long = 700L
) {
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var lastRecordAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size

    /**
     * Records [current] as the state to return to. Called before the document
     * changes.
     */
    fun record(current: String, now: Long = System.currentTimeMillis()) {
        val sameBurst = undoStack.isNotEmpty() && now - lastRecordAt < burstMs
        if (!sameBurst) {
            undoStack.add(current)
            if (undoStack.size > limit) undoStack.removeAt(0)
        }
        lastRecordAt = now
        redoStack.clear()
    }

    /** Returns the document to restore, or null when there is nothing to undo. */
    fun undo(current: String): String? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.add(current)
        lastRecordAt = 0
        return previous
    }

    /** Returns the document to restore, or null when there is nothing to redo. */
    fun redo(current: String): String? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.add(current)
        if (undoStack.size > limit) undoStack.removeAt(0)
        lastRecordAt = 0
        return next
    }

    /**
     * Drops the timeline. Used when text arrives from outside the editor
     * (Room or the server), where undoing into the previous document would
     * resurrect someone else's snapshot.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastRecordAt = 0
    }
}
