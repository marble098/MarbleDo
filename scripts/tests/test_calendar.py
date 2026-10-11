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

    def test_unsafe_source_url_is_rejected(self):
        self.catalog["sources"][0]["url"] = "file:///data/private"
        with self.assertRaisesRegex(ValueError, "URL"):
            validator.check(self.catalog)


if __name__ == "__main__":
    unittest.main()
