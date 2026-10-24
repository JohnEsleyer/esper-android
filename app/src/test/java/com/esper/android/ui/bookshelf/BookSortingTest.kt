package com.esper.android.ui.bookshelf

import com.esper.android.data.model.NotebookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSortingTest {

    private fun book(
        id: String,
        title: String,
        updatedAt: Long,
        isPinned: Boolean = false
    ) = NotebookEntity(
        id = id,
        title = title,
        description = "",
        ownerSub = "u1",
        tagsCsv = "",
        updatedAt = updatedAt,
        isPinned = isPinned
    )

    private val bookA = book("b1", title = "Alpha Book", updatedAt = 1000L)
    private val bookB = book("b2", title = "bravo book", updatedAt = 3000L)
    private val bookC = book("b3", title = "Charlie Book", updatedAt = 2000L)
    private val pinnedBook = book("b4", title = "Pinned Zulu", updatedAt = 500L, isPinned = true)

    private val allBooks = listOf(bookA, bookB, bookC, pinnedBook)

    private fun sort(books: List<NotebookEntity>, option: BookSortOption): List<NotebookEntity> {
        val nonJournal = books.filter { !it.id.startsWith("esper-journal") }
        return when (option) {
            BookSortOption.RECENTLY_UPDATED -> nonJournal.sortedWith(
                compareByDescending<NotebookEntity> { it.isPinned }.thenByDescending { it.updatedAt }
            )
            BookSortOption.OLDEST_UPDATED -> nonJournal.sortedWith(
                compareByDescending<NotebookEntity> { it.isPinned }.thenBy { it.updatedAt }
            )
            BookSortOption.TITLE_AZ -> nonJournal.sortedWith(
                compareByDescending<NotebookEntity> { it.isPinned }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            BookSortOption.TITLE_ZA -> nonJournal.sortedWith(
                compareByDescending<NotebookEntity> { it.isPinned }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        }
    }

    @Test
    fun `recently updated sorts pinned first, then by updatedAt descending`() {
        val result = sort(allBooks, BookSortOption.RECENTLY_UPDATED)
        assertEquals(listOf(pinnedBook, bookB, bookC, bookA), result)
    }

    @Test
    fun `oldest updated sorts pinned first, then by updatedAt ascending`() {
        val result = sort(allBooks, BookSortOption.OLDEST_UPDATED)
        assertEquals(listOf(pinnedBook, bookA, bookC, bookB), result)
    }

    @Test
    fun `title A-Z sorts pinned first, then by title case-insensitive ascending`() {
        val result = sort(allBooks, BookSortOption.TITLE_AZ)
        assertEquals(listOf(pinnedBook, bookA, bookB, bookC), result)
    }

    @Test
    fun `title Z-A sorts pinned first, then by title case-insensitive descending`() {
        val result = sort(allBooks, BookSortOption.TITLE_ZA)
        assertEquals(listOf(pinnedBook, bookC, bookB, bookA), result)
    }

    @Test
    fun `journal entries are filtered out of sorting`() {
        val journal = book("esper-journal-u1", title = "Journal", updatedAt = 99999L)
        val result = sort(listOf(journal, bookA), BookSortOption.RECENTLY_UPDATED)
        assertEquals(listOf(bookA), result)
    }
}
