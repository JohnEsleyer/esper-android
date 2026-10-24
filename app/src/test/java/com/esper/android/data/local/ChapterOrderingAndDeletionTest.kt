package com.esper.android.data.local

import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.ui.workspace.RecentPageItem
import com.esper.android.ui.workspace.TabItemData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterOrderingAndDeletionTest {

    private fun page(id: String, position: Int, title: String = "Chapter $position") = PageEntity(
        id = id,
        notebookId = "notebook-1",
        title = title,
        notes = "# $title",
        position = position
    )

    private val sampleNotebook = NotebookEntity(
        id = "notebook-1",
        title = "My Book",
        description = "",
        ownerSub = "user-1",
        tagsCsv = ""
    )

    @Test
    fun `pages are sorted by position so latest chapter is positioned last`() {
        // Suppose there are 9 current chapters (positions 0 to 8)
        val existingPages = (0 until 9).map { page("p-$it", position = it) }
        val maxPos = existingPages.maxOfOrNull { it.position } ?: -1
        val nextPos = maxOf(existingPages.size, maxPos + 1)

        val newPage = page("p-9", position = nextPos, title = "Chapter 10")
        val allPages = (existingPages + newPage).shuffled()

        val sorted = allPages.sortedWith(compareBy({ it.position }, { it.id }))
        assertEquals(10, sorted.size)
        assertEquals("p-9", sorted.last().id)
        assertEquals(9, sorted.last().position)
    }

    @Test
    fun `new chapter position calculation when existing positions have gaps`() {
        // Positions: 0, 2, 5
        val pages = listOf(
            page("p-0", 0),
            page("p-1", 2),
            page("p-2", 5)
        )
        val maxPos = pages.maxOfOrNull { it.position } ?: -1
        val nextPos = maxOf(pages.size, maxPos + 1)

        assertEquals(6, nextPos)
    }

    @Test
    fun `clamping activePageIndex on chapter deletion`() {
        var activePageIndex = 4 // pointing to 5th chapter
        val remainingAfterDelete = listOf(
            page("p-0", 0),
            page("p-1", 1),
            page("p-2", 2),
            page("p-3", 3)
        ) // size is now 4, valid indices are 0..3

        if (activePageIndex >= remainingAfterDelete.size) {
            activePageIndex = (remainingAfterDelete.size - 1).coerceAtLeast(0)
        }

        assertEquals(3, activePageIndex)
    }

    @Test
    fun `clamping activePageIndex when all chapters are deleted`() {
        var activePageIndex = 0
        val remainingAfterDelete = emptyList<PageEntity>()

        if (activePageIndex >= remainingAfterDelete.size) {
            activePageIndex = (remainingAfterDelete.size - 1).coerceAtLeast(0)
        }

        assertEquals(0, activePageIndex)
    }

    @Test
    fun `open tabs and recent pages are updated on chapter deletion`() {
        val deletedPageId = "p-deleted"
        val remainingPages = listOf(page("p-0", 0), page("p-1", 1))
        val activePageIndex = 1

        val openTabItems = listOf(
            TabItemData(notebook = sampleNotebook, pageNumber = 3, pageId = deletedPageId),
            TabItemData(notebook = sampleNotebook.copy(id = "other-nb"), pageNumber = 1, pageId = "other-page")
        )

        val updatedTabs = openTabItems.mapNotNull { item ->
            if (item.notebook.id == sampleNotebook.id && item.pageId == deletedPageId) {
                val newPage = remainingPages.getOrNull(activePageIndex)
                if (newPage != null) {
                    item.copy(pageId = newPage.id, pageNumber = activePageIndex + 1)
                } else null
            } else item
        }

        assertEquals(2, updatedTabs.size)
        assertEquals("p-1", updatedTabs[0].pageId)
        assertEquals(2, updatedTabs[0].pageNumber)
        assertEquals("other-nb", updatedTabs[1].notebook.id)

        val recentPageItems = listOf(
            RecentPageItem(notebook = sampleNotebook, page = page(deletedPageId, 2), pageNumber = 3),
            RecentPageItem(notebook = sampleNotebook, page = remainingPages[0], pageNumber = 1)
        )
        val filteredRecent = recentPageItems.filterNot {
            it.notebook.id == sampleNotebook.id && it.page.id == deletedPageId
        }

        assertEquals(1, filteredRecent.size)
        assertEquals("p-0", filteredRecent[0].page.id)
    }

    @Test
    fun `remote notebook sync identifies deleted local pages to purge`() {
        val localPages = listOf(
            page("p-1", 0),
            page("p-2", 1),
            page("p-3", 2)
        )
        // Remote only has p-1 and p-3 (p-2 was deleted)
        val remotePageIds = setOf("p-1", "p-3")

        val toDelete = localPages.filter { !remotePageIds.contains(it.id) }.map { it.id }
        assertEquals(listOf("p-2"), toDelete)
    }
}
