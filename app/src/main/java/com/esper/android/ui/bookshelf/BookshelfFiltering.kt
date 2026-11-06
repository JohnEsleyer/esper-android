package com.esper.android.ui.bookshelf

import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.ShelfEntity

object BookshelfFiltering {

    fun shelfBookIds(shelf: ShelfEntity?): Set<String> = shelfBookIds(shelf?.notebookIdsCsv)

    fun shelfBookIds(notebookIdsCsv: String?): Set<String> =
        notebookIdsCsv
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    fun addBookToShelf(notebookIdsCsv: String?, bookId: String): String {
        val ids = shelfBookIds(notebookIdsCsv).toMutableSet()
        ids.add(bookId.trim())
        return ids.joinToString(",")
    }

    fun removeBookFromShelf(notebookIdsCsv: String?, bookId: String): String {
        val ids = shelfBookIds(notebookIdsCsv).toMutableSet()
        ids.remove(bookId.trim())
        return ids.joinToString(",")
    }

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

    fun matchesTag(tagsCsv: String, tagFilter: String): Boolean {
        if (tagFilter.equals("All", ignoreCase = true)) return true
        return tagsCsv.split(",").any {
            it.trim().equals(tagFilter.trim(), ignoreCase = true)
        }
    }

    fun matchesTag(book: NotebookEntity, tagFilter: String): Boolean {
        if (tagFilter.equals("All", ignoreCase = true)) return true
        if (tagFilter.equals("Bookmarks", ignoreCase = true)) {
            return book.isPinned || book.tagsCsv.split(",").any { it.trim().equals("Bookmarks", ignoreCase = true) }
        }
        return matchesTag(book.tagsCsv, tagFilter)
    }

    fun matchesSearch(title: String, description: String, searchQuery: String): Boolean {
        if (searchQuery.isBlank()) return true
        return title.contains(searchQuery, ignoreCase = true) ||
            description.contains(searchQuery, ignoreCase = true)
    }

    fun matchesShelf(bookId: String, shelfBookIds: Set<String>?, shelfFilterActive: Boolean): Boolean {
        if (!shelfFilterActive) return true
        return shelfBookIds?.contains(bookId) == true
    }

    fun filter(
        books: List<NotebookEntity>,
        tagFilter: String,
        searchQuery: String,
        shelfBookIds: Set<String>?,
        shelfFilterActive: Boolean
    ): List<NotebookEntity> = books.filter { book ->
        matchesTag(book, tagFilter) &&
            matchesSearch(book.title, book.description, searchQuery) &&
            matchesShelf(book.id, shelfBookIds, shelfFilterActive)
    }
}