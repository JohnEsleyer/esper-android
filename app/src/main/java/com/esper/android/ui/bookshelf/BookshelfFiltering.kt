package com.esper.android.ui.bookshelf

import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.ShelfEntity

/**
 * Pure filtering helpers for the bookshelf grid.
 *
 * These live outside the composable so the rules can be unit tested and so
 * there is exactly one definition of "does this book belong here".
 *
 * Two rules matter:
 *  - membership is tested against a **split set**, never with `String.contains`
 *    over a joined CSV (that made `book-1` match a shelf holding `book-10`);
 *  - a shelf that no longer exists must not hide every book, so an unknown
 *    shelf id degrades to "no shelf filter" instead of "match nothing".
 */
object BookshelfFiltering {

    /** The exact set of notebook ids on a shelf. */
    fun shelfBookIds(shelf: ShelfEntity?): Set<String> = shelfBookIds(shelf?.notebookIdsCsv)

    /** The exact set of notebook ids encoded in a comma separated list. */
    fun shelfBookIds(notebookIdsCsv: String?): Set<String> =
        notebookIdsCsv
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /** Adds a bookId to a shelf's notebookIdsCsv, returning the new CSV. */
    fun addBookToShelf(notebookIdsCsv: String?, bookId: String): String {
        val ids = shelfBookIds(notebookIdsCsv).toMutableSet()
        ids.add(bookId.trim())
        return ids.joinToString(",")
    }

    /** Removes a bookId from a shelf's notebookIdsCsv, returning the new CSV. */
    fun removeBookFromShelf(notebookIdsCsv: String?, bookId: String): String {
        val ids = shelfBookIds(notebookIdsCsv).toMutableSet()
        ids.remove(bookId.trim())
        return ids.joinToString(",")
    }

    /** Toggles book membership on a shelf, returning the new CSV. */
    fun toggleBookOnShelf(notebookIdsCsv: String?, bookId: String): String {
        val ids = shelfBookIds(notebookIdsCsv).toMutableSet()
        val cleanId = bookId.trim()
        if (ids.contains(cleanId)) {
            ids.remove(cleanId)
        } else {
            ids.add(cleanId)
        }
        return ids.joinToString(",")
    }

    /** A book matches a tag when one of its tags equals the filter exactly. */
    fun matchesTag(tagsCsv: String, tagFilter: String): Boolean {
        if (tagFilter == "All") return true
        return tagsCsv.split(",").any {
            it.trim().equals(tagFilter.trim(), ignoreCase = true)
        }
    }

    /** A book matches the search box on its title or description. */
    fun matchesSearch(title: String, description: String, searchQuery: String): Boolean {
        if (searchQuery.isBlank()) return true
        return title.contains(searchQuery, ignoreCase = true) ||
            description.contains(searchQuery, ignoreCase = true)
    }

    /**
     * A book matches the shelf when it is on the shelf. When no shelf filter is
     * active (no shelf selected, or the selected shelf id is stale) every book
     * matches.
     */
    fun matchesShelf(bookId: String, shelfBookIds: Set<String>?, shelfFilterActive: Boolean): Boolean {
        if (!shelfFilterActive) return true
        return shelfBookIds?.contains(bookId) == true
    }

    /**
     * Applies the tag, search and shelf filters.
     *
     * @param shelfFilterActive true when a shelf filter should be applied at all.
     *   Pass `false` when no shelf is selected or the selected shelf id no
     *   longer resolves to a known shelf.
     */
    fun filter(
        books: List<NotebookEntity>,
        tagFilter: String,
        searchQuery: String,
        shelfBookIds: Set<String>?,
        shelfFilterActive: Boolean
    ): List<NotebookEntity> = books.filter { book ->
        matchesTag(book.tagsCsv, tagFilter) &&
            matchesSearch(book.title, book.description, searchQuery) &&
            matchesShelf(book.id, shelfBookIds, shelfFilterActive)
    }
}
