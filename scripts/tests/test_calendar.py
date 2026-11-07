import copy
import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("check_calendar", Path(__file__).parents[1] / "check_calendar.py")
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class CatalogIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.catalog = json.loads(validator.CATALOG.read_text())

    def test_bundled_catalog(self):
        self.assertGreaterEqual(validator.check(self.catalog)["annual_occasions"], 800)

    def test_older_clients_keep_their_compatible_feed(self):
        legacy = json.loads(validator.LEGACY.with_name("holidays-fa.json").read_text())
        self.assertLess(legacy["schemaVersion"], 4)
        self.assertEqual("5", legacy["dataVersion"])
        old_student_day = next(row for row in legacy["jalali"] if row["titleEn"] == "Student Day")
        self.assertFalse(old_student_day["holiday"], "The legacy client should receive the corrected holiday flag")
        repository = validator.ROOT / "feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/OccasionRepository.kt"
        self.assertNotIn("calendar/holidays-fa.json", repository.read_text())
        # The app ships only the cold-start seed; the descriptive catalog is online.
        self.assertIn("calendar/occasions-v6-fa.json", repository.read_text())
        self.assertIn("data/occasions/occasions-live.json", (
            validator.ROOT / "feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/OccasionFeeds.kt"
        ).read_text())
        v4 = json.loads(validator.LEGACY.with_name("occasions-v4-fa.json").read_text())
        self.assertEqual(4, v4["schemaVersion"])
        v4_student_day = next(row for row in v4["jalali"] if row["titleEn"] == "Student Day")
        self.assertFalse(v4_student_day["holiday"], "The v4 client should receive the corrected holiday flag")

    def test_no_duplicate_stable_ids(self):
        self.catalog["jalali"].append(copy.deepcopy(self.catalog["jalali"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            validator.check(self.catalog)

    def test_no_fake_dates_for_variable_programs(self):
        self.catalog["schoolPrograms"][0]["month"] = 8
        with self.assertRaisesRegex(ValueError, "invent"):
            validator.check(self.catalog)

    def test_sources_are_resolvable(self):
        self.catalog["lunar"][0]["sourceIds"] = ["missing"]
        with self.assertRaisesRegex(ValueError, "source"):
            validator.check(self.catalog)

    def test_lunar_dates_cannot_be_31(self):
        self.catalog["lunar"][0]["day"] = 31
        with self.assertRaisesRegex(ValueError, "day"):
            validator.check(self.catalog)

    def test_guidance_is_not_public_holiday(self):
        row = next(r for r in self.catalog["jalali"] if r["evidence"] == "guidance")
        row["holiday"] = True
        with self.assertRaisesRegex(ValueError, "public holiday"):
            validator.check(self.catalog)

    def test_education_day_does_not_regress(self):
        row = next(r for r in self.catalog["jalali"] if r["titleEn"] == "Islamic Education Affairs Day")
        row["day"] = 24
        with self.assertRaisesRegex(ValueError, "regression"):
            validator.check(self.catalog)

    def test_missing_or_unknown_culture_is_rejected(self):
        for value in [[], ["unknown"]]:
            catalog = copy.deepcopy(self.catalog)
            catalog["gregorian"][0]["cultures"] = value
            with self.assertRaisesRegex(ValueError, "culture"):
                validator.check(catalog)

    def test_easter_cannot_be_applied_to_lunar_years(self):
        self.catalog["lunar"][0]["dateRule"] = "western_easter"
        with self.assertRaisesRegex(ValueError, "Gregorian"):
            validator.check(self.catalog)

    def test_foreign_observances_cannot_create_local_public_holidays(self):
        self.catalog["gregorian"][0]["holiday"] = True
        with self.assertRaisesRegex(ValueError, "Iranian public holiday"):
            validator.check(self.catalog)

    def test_rule_offsets_are_bounded(self):
        self.catalog["gregorian"][0]["dayOffset"] = 61
        with self.assertRaisesRegex(ValueError, "offset"):
            validator.check(self.catalog)

    def test_floating_western_rule_cannot_be_replaced_by_a_guessed_date(self):
        next(r for r in self.catalog["gregorian"] if r["titleEn"] == "Western Easter Sunday")["dateRule"] = "fixed"
        with self.assertRaisesRegex(ValueError, "floating-date"):
            validator.check(self.catalog)

    def test_unsafe_source_url_is_rejected(self):
        self.catalog["sources"][0]["url"] = "file:///data/private"
        with self.assertRaisesRegex(ValueError, "URL"):
            validator.check(self.catalog)


if __name__ == "__main__":
    unittest.main()
