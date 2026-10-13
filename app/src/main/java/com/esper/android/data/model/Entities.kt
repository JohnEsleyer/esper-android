package com.esper.android.data.model

import androidx.room.*

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val ownerSub: String,
    val visibility: String,
    val isEncrypted: Boolean,
    val coverPreset: String,
    val coverImage: String?,
    val isPinned: Boolean,
    val tagsCsv: String,
    val updatedAt: Long
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
    val parentId: String?,
    val title: String,
    val notes: String,
    val position: Int
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
    val assignedPagesCsv: String
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
    val position: Int
)
