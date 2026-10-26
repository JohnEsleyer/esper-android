package com.esper.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    // because page ids are only unique within a notebook.
    // v5: notebooks gained createdAt so the shelf can sort by creation date.
    // The app also opens the database with fallbackToDestructiveMigration, so
    // any *unknown* schema change still rebuilds and re-syncs from the server.
    version = 5,
    exportSchema = true
)
abstract class EsperDatabase : RoomDatabase() {
    abstract fun dao(): EsperDao

    companion object {
        /**
         * Adds `notebooks.createdAt` without dropping local data. Existing rows
         * are backfilled from updatedAt (the best proxy available) and every
         * row is refreshed from the server's created_at on the next sync.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notebooks` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE `notebooks` SET `createdAt` = `updatedAt` WHERE `createdAt` = 0"
                )
            }
        }
    }
}
