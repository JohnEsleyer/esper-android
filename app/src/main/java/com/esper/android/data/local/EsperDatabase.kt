package com.esper.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.esper.android.data.model.*

@Database(
    entities = [
        NotebookEntity::class,
        PageEntity::class,
        CanvasEntity::class,
        QuestionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EsperDatabase : RoomDatabase() {
    abstract fun dao(): EsperDao
}
