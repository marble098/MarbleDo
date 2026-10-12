import importlib.util
import sys
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("check_migrations", Path(__file__).parents[1] / "check_migrations.py")
validator = importlib.util.module_from_spec(spec)
sys.modules["check_migrations"] = validator
spec.loader.exec_module(validator)


def entity_source(default: str) -> str:
    return f'''
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits", indices = [Index(value = ["isArchived"])])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val title: String?,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = {default},
)
'''


def database_source(habit_definition: str, *, with_index: bool = True) -> str:
    index = 'db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_isArchived` ON `habits` (`isArchived`)")' if with_index else ""
    return f'''
val MIGRATION: Migration = object : Migration(1, 2) {{
    override fun migrate(db: SupportSQLiteDatabase) {{
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`title` TEXT, " +
                {habit_definition})
        {index}
    }}
}}
'''


class MigrationConsistencyTest(unittest.TestCase):
    def test_repository_migrations_match_entities(self):
        entities = validator.load_entity_sources()
        database = validator.DATABASE_SOURCE.read_text(encoding="utf-8")
        self.assertEqual([], validator.check(entities, database))

    def test_missing_sql_default_reproduces_the_released_crash(self):
        # Regression for the crash "Migration didn't properly handle: habits":
        # the entity declared a default but the CREATE TABLE statement omitted it.
        source = database_source('"`isArchived` INTEGER NOT NULL"')
        problems = validator.check({"HabitEntity.kt": entity_source("false")}, source)
        self.assertEqual(1, len(problems))
        self.assertIn("isArchived", problems[0])
        self.assertIn("Migration didn't properly handle", problems[0])

    def test_matching_default_passes(self):
        source = database_source('"`isArchived` INTEGER NOT NULL DEFAULT 0"')
        self.assertEqual([], validator.check({"HabitEntity.kt": entity_source("false")}, source))

    def test_wrong_default_literal_fails(self):
        source = database_source('"`isArchived` INTEGER NOT NULL DEFAULT 1"')
        problems = validator.check({"HabitEntity.kt": entity_source("false")}, source)
        self.assertTrue(any("DEFAULT 0" in problem and "DEFAULT 1" in problem for problem in problems))

    def test_defaulted_column_missing_from_migrations_fails(self):
        source = database_source('""').replace("+ \n                \"\"", "")
        problems = validator.check({"HabitEntity.kt": entity_source("false")}, source)
        self.assertTrue(any("no migration defines habits.isArchived" in problem for problem in problems))

    def test_nullability_mismatch_fails(self):
        source = database_source('"`isArchived` INTEGER NOT NULL DEFAULT 0"').replace(
            '"`title` TEXT, "', '"`title` TEXT NOT NULL, "'
        )
        problems = validator.check({"HabitEntity.kt": entity_source("false")}, source)
        self.assertTrue(any("'title' nullability mismatch" in problem for problem in problems))

    def test_wrong_affinity_fails(self):
        source = database_source('"`isArchived` INTEGER NOT NULL DEFAULT 0"').replace(
            '"`name` TEXT NOT NULL, "', '"`name` INTEGER NOT NULL, "'
        )
        problems = validator.check({"HabitEntity.kt": entity_source("false")}, source)
        self.assertTrue(any("'name' is TEXT in the entity" in problem for problem in problems))

    def test_missing_index_fails(self):
        source = database_source('"`isArchived` INTEGER NOT NULL DEFAULT 0"', with_index=False)
        problems = validator.check({"HabitEntity.kt": entity_source("false")}, source)
        self.assertTrue(any("index_habits_isArchived" in problem for problem in problems))

    def test_kotlin_comments_with_quotes_do_not_confuse_the_parser(self):
        # The fix for the released crash carries a comment containing quotes; the
        # parser must ignore those instead of misreading string literals.
        source = database_source('"`isArchived` INTEGER NOT NULL DEFAULT 0"').replace(
            "{habit_definition}", '""'
        )
        source = source.replace(
            '"`title` TEXT, " +',
            '"`title` TEXT, " +\n                // Room validates defaultValue = "0" after migration',
        )
        self.assertEqual([], validator.check({"HabitEntity.kt": entity_source("false")}, source))


if __name__ == "__main__":
    unittest.main()
