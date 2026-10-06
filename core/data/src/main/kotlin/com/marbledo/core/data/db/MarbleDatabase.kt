package com.marbledo.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MarbleDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val DATABASE_NAME = "marbledo.db"

        /** v1 contained the original task fields; v2 adds archive, repeat, tags and focus metadata. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isAllDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN colorArgb INTEGER NOT NULL DEFAULT 4285424088")
                db.execSQL("ALTER TABLE tasks ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN project TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN checklistJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN link TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN attachmentUri TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceJson TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN countdownTheme TEXT NOT NULL DEFAULT 'MARBLE'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN focusMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET updatedAtEpochMillis = createdAtEpochMillis WHERE updatedAtEpochMillis = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueAtEpochMillis ON tasks(dueAtEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_isCompleted_isArchived ON tasks(isCompleted, isArchived)")
            }
        }

        fun create(context: Context): MarbleDatabase = Room.databaseBuilder(
            context.applicationContext,
            MarbleDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
