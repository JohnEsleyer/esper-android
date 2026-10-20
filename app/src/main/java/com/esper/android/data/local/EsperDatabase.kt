package com.esper.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.esper.android.data.model.*

@Database(
    entities = [
        NotebookEntity::class,
        PageEntity::class,
        CanvasEntity::class,
        QuestionEntity::class,
        ShelfEntity::class
    ],
    // v4: the pages table switched to a composite (notebookId, id) primary key
    // because page ids are only unique within a notebook. The app opens the
    // database with fallbackToDestructiveMigration, so the local copy is
    // rebuilt and re-synced from the server.
    version = 4,
    exportSchema = true
)
abstract class EsperDatabase : RoomDatabase() {
    abstract fun dao(): EsperDao
}
