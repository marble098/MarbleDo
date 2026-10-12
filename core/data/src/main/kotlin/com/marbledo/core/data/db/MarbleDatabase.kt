package com.marbledo.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, HabitEntity::class, HabitEntryEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class MarbleDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun habitDao(): HabitDao

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

        /** v3 adds task categories and pin state without changing existing task data. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 lets every task opt in or out of the dashboard countdown. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN countdownEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v5 adds the habit tracker: one table for habits and one for their daily records. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`tracking` TEXT NOT NULL, " +
                        "`scheduleType` TEXT NOT NULL, " +
                        "`weekdaysCsv` TEXT NOT NULL, " +
                        "`timesPerWeek` INTEGER NOT NULL, " +
                        "`dailyTarget` INTEGER NOT NULL, " +
                        "`unitLabel` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL, " +
                        "`themeId` TEXT NOT NULL, " +
                        "`cue` TEXT NOT NULL, " +
                        "`identity` TEXT NOT NULL, " +
                        "`why` TEXT NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, " +
                        "`reminderMinuteOfDay` INTEGER NOT NULL, " +
                        "`startDate` TEXT NOT NULL, " +
                        // HabitEntity declares @ColumnInfo(defaultValue = "0"); Room validates that
                        // literal after migration, so the DEFAULT must be part of the definition.
                        "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAtEpochMillis` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_entries` (" +
                        "`habitId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`value` INTEGER NOT NULL, " +
                        "`updatedAtEpochMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `date`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_isArchived` ON `habits` (`isArchived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_entries_habitId` ON `habit_entries` (`habitId`)")
            }
        }

        fun create(context: Context): MarbleDatabase = Room.databaseBuilder(
            context.applicationContext,
            MarbleDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
