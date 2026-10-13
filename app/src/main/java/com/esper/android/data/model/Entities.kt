package com.esper.android.data.model

import androidx.room.*

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val ownerSub: String,
    val visibility: String = "public", // "public", "protected", "restricted"
    val isEncrypted: Boolean = false,
    val coverPreset: String = "celestial",
    val coverImage: String? = null,
    val isPinned: Boolean = false,
    val tagsCsv: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pages",
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
    @PrimaryKey val id: String,
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
