package com.esper.android.sync

import com.esper.android.JournalBootstrapAction
import com.esper.android.data.local.NotebookWithDetails
import com.esper.android.data.model.CanvasEntity
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.determineJournalBootstrapAction
import com.esper.android.hasMeaningfulJournalContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalBootstrapTest {

    @Test
    fun `server journal existence always adopts server first`() {
        assertEquals(
            JournalBootstrapAction.ADOPT_SERVER,
            determineJournalBootstrapAction(serverJournalExists = true, legacyLocalExists = false)
        )
        // Even if legacy local exists, server takes precedence to prevent overwriting
        assertEquals(
            JournalBootstrapAction.ADOPT_SERVER,
            determineJournalBootstrapAction(serverJournalExists = true, legacyLocalExists = true)
        )
    }

    @Test
    fun `migrates legacy shared journal only when server journal is absent`() {
        assertEquals(
            JournalBootstrapAction.MIGRATE_LEGACY,
            determineJournalBootstrapAction(serverJournalExists = false, legacyLocalExists = true)
        )
    }

    @Test
    fun `seeds fresh locally when neither server nor legacy journal exists`() {
        assertEquals(
            JournalBootstrapAction.SEED_FRESH,
            determineJournalBootstrapAction(serverJournalExists = false, legacyLocalExists = false)
        )
    }

    @Test
    fun `default seed notes are not treated as meaningful content`() {
        val book = NotebookEntity(id = "j1", title = "Journal", description = "", ownerSub = "u1")
        val seedPage = PageEntity(
            id = "jp-1",
            notebookId = "j1",
            title = "Tue, Oct 20",
            notes = "# Tue, Oct 20\n\nJot down ideas and quick thoughts here..."
        )
        val details = NotebookWithDetails(book, pages = listOf(seedPage), canvases = emptyList(), questions = emptyList())

        assertFalse(hasMeaningfulJournalContent(details))
        assertFalse(hasMeaningfulJournalContent(null))
        assertFalse(hasMeaningfulJournalContent(details.copy(pages = emptyList())))
    }

    @Test
    fun `user notes are identified as meaningful content`() {
        val book = NotebookEntity(id = "j1", title = "Journal", description = "", ownerSub = "u1")
        val userPage = PageEntity(
            id = "jp-1",
            notebookId = "j1",
            title = "Research",
            notes = "# Research\n\nOrbital trajectory calculation notes."
        )
        val details = NotebookWithDetails(book, pages = listOf(userPage), canvases = emptyList(), questions = emptyList())

        assertTrue(hasMeaningfulJournalContent(details))
    }

    @Test
    fun `multiple pages or attachments count as meaningful content`() {
        val book = NotebookEntity(id = "j1", title = "Journal", description = "", ownerSub = "u1")
        val page1 = PageEntity(id = "jp-1", notebookId = "j1", title = "Entry 1", notes = "")
        val page2 = PageEntity(id = "jp-2", notebookId = "j1", title = "Entry 2", notes = "")
        val detailsMultiPage = NotebookWithDetails(book, pages = listOf(page1, page2), canvases = emptyList(), questions = emptyList())
        assertTrue(hasMeaningfulJournalContent(detailsMultiPage))

        val canvas = CanvasEntity(id = "c1", notebookId = "j1", kind = "html", title = "Sim", payloadJson = "{}")
        val detailsWithCanvas = NotebookWithDetails(book, pages = listOf(page1), canvases = listOf(canvas), questions = emptyList())
        assertTrue(hasMeaningfulJournalContent(detailsWithCanvas))
    }
}
