import copy
import importlib.util
import json
import hashlib
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
        legacy = validator.CATALOG.with_name("holidays-fa.json").read_bytes()
        self.assertEqual("eeaa96c51504e078ebf8766af26d8a5062764b7e19522bb0447226bca8183fce", hashlib.sha256(legacy).hexdigest(),
                         "Legacy clients ignore new rules: do not overwrite their feed with v4 data")
        self.assertLess(json.loads(legacy)["schemaVersion"], 4)
        repository = validator.ROOT / "feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/OccasionRepository.kt"
        self.assertNotIn("calendar/holidays-fa.json", repository.read_text())
        self.assertIn("calendar/occasions-v5-fa.json", repository.read_text())
        v4 = validator.CATALOG.with_name("occasions-v4-fa.json").read_bytes()
        self.assertEqual("e5d3ec6b3761b62a46df797fba09d699843887a80295e941c93ec9c23ddb82c1", hashlib.sha256(v4).hexdigest(),
                         "Rule-aware v4 clients also retain their own compatible feed")

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
