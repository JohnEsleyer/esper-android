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
    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    fun observeNotebookDetails(notebookId: String): Flow<NotebookWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotebook(notebook: NotebookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(pages: List<PageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCanvases(canvases: List<CanvasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Query("UPDATE pages SET notes = :notes WHERE id = :pageId")
    suspend fun updatePageNotes(pageId: String, notes: String)

    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteNotebook(notebookId: String)
}
