package com.esper.android.data.model

import androidx.room.*

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val ownerSub: String,
    val visibility: String = "public", // "public", "protected", "restricted"
    val coverPreset: String = "celestial",
    val coverImage: String? = null,
    val isPinned: Boolean = false,
    val tagsCsv: String = "",
    // Creation timestamp (epoch millis) used by the "Recently Created" sort.
    // Server rows are backfilled from created_at; legacy local rows from
    // updatedAt by MIGRATION_4_5.
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pages",
    // Page ids are only unique within a notebook (every web-created book
    // starts with a page called "p1"), so the key must be composite.
    primaryKeys = ["notebookId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId"), Index("position")]
)
data class PageEntity(
    // The page id is only unique *within* a notebook: every book created on
    // the web starts with a page called "p1". A single-column primary key made
    // Room replace the previous book's row, so all but one of those books lost
    // their only chapter. The key is therefore (notebookId, id).
    val id: String,
    val notebookId: String,
    val parentId: String? = null,
    val title: String,
    val notes: String = "",
    val position: Int = 0
)

@Entity(
    tableName = "canvases",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId")]
)
data class CanvasEntity(
    @PrimaryKey val id: String,
    val notebookId: String,
    val kind: String, // "html", "youtube", "excalidraw"
    val title: String,
    val payloadJson: String,
    val assignedPagesCsv: String = ""
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val notebookId: String,
    val title: String,
    val answer: String,
    val position: Int = 0
)

@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val color: String = "#6366F1",
    val coverPreset: String = "midnight",
    val position: Int = 0,
    val notebookIdsCsv: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
