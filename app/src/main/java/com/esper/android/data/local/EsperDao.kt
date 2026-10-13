package com.esper.android.data.local

import androidx.room.*
import com.esper.android.data.model.*
import kotlinx.coroutines.flow.Flow

data class NotebookWithDetails(
    @Embedded val notebook: NotebookEntity,
    @Relation(parentColumn = "id", entityColumn = "notebookId")
    val pages: List<PageEntity>,
    @Relation(parentColumn = "id", entityColumn = "notebookId")
    val canvases: List<CanvasEntity>,
    @Relation(parentColumn = "id", entityColumn = "notebookId")
    val questions: List<QuestionEntity>
)

@Dao
interface EsperDao {
    @Query("SELECT * FROM notebooks ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllNotebooks(): Flow<List<NotebookEntity>>

    @Transaction
    @Query("SELECT * FROM notebooks ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllNotebooksWithDetails(): Flow<List<NotebookWithDetails>>

    @Transaction
    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    fun observeNotebookDetails(notebookId: String): Flow<NotebookWithDetails?>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    suspend fun getNotebookById(notebookId: String): NotebookEntity?

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY position ASC")
    suspend fun getPagesForNotebook(notebookId: String): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotebook(notebook: NotebookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(pages: List<PageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCanvases(canvases: List<CanvasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCanvas(canvas: CanvasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestion(question: QuestionEntity)

    @Query("UPDATE questions SET title = :title WHERE id = :questionId")
    suspend fun updateQuestionTitle(questionId: String, title: String)

    @Query("UPDATE questions SET answer = :answer WHERE id = :questionId")
    suspend fun updateQuestionAnswer(questionId: String, answer: String)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestion(questionId: String)

    @Query("UPDATE pages SET notes = :notes WHERE id = :pageId")
    suspend fun updatePageNotes(pageId: String, notes: String)

    @Query("UPDATE pages SET title = :title WHERE id = :pageId")
    suspend fun updatePageTitle(pageId: String, title: String)

    @Query("DELETE FROM pages WHERE id = :pageId OR parentId = :pageId")
    suspend fun deletePageAndChildren(pageId: String)

    @Query("DELETE FROM canvases WHERE id = :canvasId")
    suspend fun deleteCanvas(canvasId: String)

    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteNotebook(notebookId: String)

    // Shelf CRUD
    @Query("SELECT * FROM shelves ORDER BY position ASC, updatedAt DESC")
    fun observeAllShelves(): Flow<List<ShelfEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShelf(shelf: ShelfEntity)

    @Query("DELETE FROM shelves WHERE id = :shelfId")
    suspend fun deleteShelf(shelfId: String)
}
