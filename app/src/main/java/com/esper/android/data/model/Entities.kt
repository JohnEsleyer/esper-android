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
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Timestamp (epoch millis) of the most recent update to notes content.
    // Kept separate from updatedAt so configuring metadata (title, cover, etc.)
    // does not alter the "Recently Updated" notes sort order.
    @ColumnInfo(defaultValue = "0") val notesUpdatedAt: Long = 0L
)

@Entity(
    tableName = "pages",
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