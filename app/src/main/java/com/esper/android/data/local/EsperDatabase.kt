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
    version = 6,
    exportSchema = true
)
abstract class EsperDatabase : RoomDatabase() {
    abstract fun dao(): EsperDao

    companion object {
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notebooks` ADD COLUMN `notesUpdatedAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE `notebooks` SET `notesUpdatedAt` = `updatedAt` WHERE `notesUpdatedAt` = 0"
                )
            }
        }
    }
}