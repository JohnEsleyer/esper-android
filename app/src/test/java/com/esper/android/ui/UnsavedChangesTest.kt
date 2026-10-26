package com.esper.android.ui

import com.esper.android.ui.components.UnsavedChangesGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the dirty-state transitions reported by the lexical editor and the
 * navigation-guard callbacks behind the unsaved-changes dialog.
 */
class UnsavedChangesTest {

    @Test
    fun `dirty state follows editor transitions`() {
        val guard = UnsavedChangesGuard()

        assertFalse(guard.hasUnsavedChanges)

        guard.setDirty(true)
        assertTrue(guard.hasUnsavedChanges)

        guard.setDirty(false)
        assertFalse(guard.hasUnsavedChanges)
    }

    @Test
    fun `navigation runs immediately when the draft is clean`() {
        val guard = UnsavedChangesGuard()
        var navigated = false

        val deferred = guard.requestNavigation { navigated = true }

        assertFalse(deferred)
        assertTrue(navigated)
        assertFalse(guard.isDialogVisible)
    }

    @Test
    fun `navigation is deferred when the draft is dirty`() {
        val guard = UnsavedChangesGuard()
        guard.setDirty(true)
        var navigated = false

        val deferred = guard.requestNavigation { navigated = true }

        assertTrue(deferred)
        assertFalse(navigated)
        assertTrue(guard.isDialogVisible)
    }

    @Test
    fun `save success clears dirty state and continues navigation`() {
        val guard = UnsavedChangesGuard()
        guard.setDirty(true)
        var navigated = false
        guard.requestNavigation { navigated = true }

        guard.completeAfterSave()

        assertFalse(guard.hasUnsavedChanges)
        assertFalse(guard.isDialogVisible)
        assertTrue(navigated)
    }

    @Test
    fun `failed save keeps the dialog and parked navigation`() {
        val guard = UnsavedChangesGuard()
        guard.setDirty(true)
        var navigated = false
        guard.requestNavigation { navigated = true }

        // The screen only calls completeAfterSave() when the save succeeds.
        assertTrue(guard.isDialogVisible)
        assertTrue(guard.hasUnsavedChanges)
        assertFalse(navigated)
    }

    @Test
    fun `discard drops edits and continues navigation`() {
        val guard = UnsavedChangesGuard()
        guard.setDirty(true)
        var navigated = false
        guard.requestNavigation { navigated = true }

        guard.discardAndContinue()

        assertFalse(guard.hasUnsavedChanges)
        assertFalse(guard.isDialogVisible)
        assertTrue(navigated)
    }

    @Test
    fun `cancel keeps the draft and drops the parked action`() {
        val guard = UnsavedChangesGuard()
        guard.setDirty(true)
        var navigated = false
        guard.requestNavigation { navigated = true }

        guard.cancel()

        assertTrue(guard.hasUnsavedChanges)
        assertFalse(guard.isDialogVisible)
        assertNull(guard.pendingAction)
        assertFalse(navigated)
    }

    @Test
    fun `guarding again replaces the parked action`() {
        val guard = UnsavedChangesGuard()
        guard.setDirty(true)
        val visited = mutableListOf<String>()

        guard.requestNavigation { visited += "first" }
        guard.requestNavigation { visited += "second" }

        assertEquals(emptyList<String>(), visited)

        guard.discardAndContinue()

        assertEquals(listOf("second"), visited)
    }
}
