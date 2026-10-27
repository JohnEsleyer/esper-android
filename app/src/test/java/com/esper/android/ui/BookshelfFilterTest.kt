package com.esper.android.ui

import com.esper.android.ui.bookshelf.BookshelfFiltering
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.ShelfEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the bookshelf filters.
 *
 * The bugs these cover were: shelf membership tested with a substring match
 * over the joined CSV, a stale shelf id hiding every book, and the tag filter
 * bleeding into the shelf view.
 */
class BookshelfFilterTest {

    private fun book(
        id: String,
        title: String = "Book $id",
        description: String = "",
        tags: String = "General"
    ) = NotebookEntity(
        id = id,
        title = title,
        description = description,
        ownerSub = "u1",
        tagsCsv = tags
    )

    private val book1 = book("book-1", tags = "Physics,Quantum")
    private val book10 = book("book-10", tags = "Physics")
    private val book2 = book("book-2", tags = "Mechanics")
    private val allBooks = listOf(book1, book10, book2)

    // --------------------------------------------------------------- shelf ids

    @Test
    fun `shelf ids are split into an exact set`() {
        val ids = BookshelfFiltering.shelfBookIds("book-10, book-2")
        assertEquals(setOf("book-10", "book-2"), ids)
    }

    @Test
    fun `shelf ids ignore blank entries and surrounding whitespace`() {
        val ids = BookshelfFiltering.shelfBookIds(" book-10 , , book-2 ")
        assertEquals(setOf("book-10", "book-2"), ids)
    }

    @Test
    fun `shelf ids of a null or blank csv are empty`() {
        assertTrue(BookshelfFiltering.shelfBookIds(null as String?).isEmpty())
        assertTrue(BookshelfFiltering.shelfBookIds("").isEmpty())
        assertTrue(BookshelfFiltering.shelfBookIds("  ,  ").isEmpty())
    }

    // ------------------------------------------------------- shelf membership

    @Test
    fun `shelf membership does not match on a substring id collision`() {
        val shelf = ShelfEntity(
            id = "shelf-1",
            title = "Special Books",
            notebookIdsCsv = "book-10,book-2"
        )
        val ids = BookshelfFiltering.shelfBookIds(shelf)

        // "book-1" is a prefix of "book-10" but is NOT on the shelf.
        assertFalse(ids.contains(book1.id))
        assertTrue(ids.contains(book10.id))
        assertTrue(ids.contains(book2.id))
    }

    @Test
    fun `filtering by a shelf returns exactly its books`() {
        val shelf = ShelfEntity(
            id = "shelf-1",
            title = "Special Books",
            notebookIdsCsv = "book-10,book-2"
        )
        val result = BookshelfFiltering.filter(
            books = allBooks,
            tagFilter = "All",
            searchQuery = "",
            shelfBookIds = BookshelfFiltering.shelfBookIds(shelf),
            shelfFilterActive = true
        )
        assertEquals(listOf(book10, book2), result)
    }

    @Test
    fun `a stale shelf id does not hide every notebook`() {
        // The selected shelf id no longer resolves to a known shelf, so the
        // shelf filter is inactive and everything stays visible.
        val result = BookshelfFiltering.filter(
            books = allBooks,
            tagFilter = "All",
            searchQuery = "",
            shelfBookIds = BookshelfFiltering.shelfBookIds(null as ShelfEntity?),
            shelfFilterActive = false
        )
        assertEquals(3, result.size)
    }

    @Test
    fun `an unknown shelf id still yields every notebook`() {
        val shelves = listOf(
            ShelfEntity(id = "shelf-1", title = "Mine", notebookIdsCsv = "book-1")
        )
        val selectedShelfId = "deleted-shelf-id"
        val currentShelf = shelves.find { it.id == selectedShelfId }
        assertNull(currentShelf)

        val result = BookshelfFiltering.filter(
            books = allBooks,
            tagFilter = "All",
            searchQuery = "",
            shelfBookIds = BookshelfFiltering.shelfBookIds(currentShelf),
            shelfFilterActive = selectedShelfId != null && currentShelf != null
        )
        assertEquals(3, result.size)
    }

    // ------------------------------------------------- shelf membership edits

    @Test
    fun `addBookToShelf adds book to empty or existing shelf`() {
        val emptyResult = BookshelfFiltering.addBookToShelf("", "book-1")
        assertEquals("book-1", emptyResult)

        val existingResult = BookshelfFiltering.addBookToShelf("book-1,book-2", "book-3")
        val ids = BookshelfFiltering.shelfBookIds(existingResult)
        assertEquals(setOf("book-1", "book-2", "book-3"), ids)
    }

    @Test
    fun `addBookToShelf does not duplicate an existing book`() {
        val result = BookshelfFiltering.addBookToShelf("book-1,book-2", "book-1")
        assertEquals(setOf("book-1", "book-2"), BookshelfFiltering.shelfBookIds(result))
    }

    @Test
    fun `removeBookFromShelf removes book from shelf`() {
        val result = BookshelfFiltering.removeBookFromShelf("book-1,book-2,book-3", "book-2")
        val ids = BookshelfFiltering.shelfBookIds(result)
        assertEquals(setOf("book-1", "book-3"), ids)
    }

    @Test
    fun `removeBookFromShelf leaves other shelves untouched`() {
        val result = BookshelfFiltering.removeBookFromShelf("book-1,book-10", "book-1")
        assertEquals(setOf("book-10"), BookshelfFiltering.shelfBookIds(result))
    }

    @Test
    fun `toggleBookOnShelf adds when absent and removes when present`() {
        val added = BookshelfFiltering.toggleBookOnShelf("book-1", "book-2")
        assertEquals(setOf("book-1", "book-2"), BookshelfFiltering.shelfBookIds(added))

        val removed = BookshelfFiltering.toggleBookOnShelf(added, "book-1")
        assertEquals(setOf("book-2"), BookshelfFiltering.shelfBookIds(removed))
    }

    // ----------------------------------------------------------- tag matching

    @Test
    fun `tag matching is exact and case insensitive`() {
        assertTrue(BookshelfFiltering.matchesTag("Physics,Quantum", "Physics"))
        assertTrue(BookshelfFiltering.matchesTag("Physics,Quantum", "quantum"))
        assertTrue(BookshelfFiltering.matchesTag("Physics,Quantum", "Quantum"))
        assertFalse(BookshelfFiltering.matchesTag("Physics,Quantum", "Phys"))
        assertFalse(BookshelfFiltering.matchesTag("Physics,Quantum", "Mechanics"))
    }

    @Test
    fun `the all tag matches everything`() {
        assertTrue(BookshelfFiltering.matchesTag("", "All"))
        assertTrue(BookshelfFiltering.matchesTag("Anything", "All"))
    }

    // -------------------------------------------------------- search matching

    @Test
    fun `search matches the title and the description`() {
        val b = book("x", title = "Orbital Mechanics", description = "About orbits")
        assertTrue(BookshelfFiltering.matchesSearch(b.title, b.description, "orbital"))
        assertTrue(BookshelfFiltering.matchesSearch(b.title, b.description, "ORBITS"))
        assertFalse(BookshelfFiltering.matchesSearch(b.title, b.description, "relativity"))
        assertTrue(BookshelfFiltering.matchesSearch(b.title, b.description, "  "))
    }

    // ------------------------------------------------------------- combinations

    @Test
    fun `tag and shelf filters are both applied`() {
        val shelf = ShelfEntity(id = "s", title = "S", notebookIdsCsv = "book-1,book-2")
        val result = BookshelfFiltering.filter(
            books = allBooks,
            tagFilter = "Physics",
            searchQuery = "",
            shelfBookIds = BookshelfFiltering.shelfBookIds(shelf),
            shelfFilterActive = true
        )
        // book-1 is Physics and on the shelf; book-10 is Physics but not on it.
        assertEquals(listOf(book1), result)
    }

    @Test
    fun `the library view returns every notebook`() {
        val result = BookshelfFiltering.filter(
            books = allBooks,
            tagFilter = "All",
            searchQuery = "",
            shelfBookIds = null,
            shelfFilterActive = false
        )
        assertEquals(allBooks, result)
    }
}
