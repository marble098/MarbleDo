"""Tests for the online occasion pipeline: adapters, anchors and cross-source consensus."""
from __future__ import annotations

import importlib.util
import json
import sys
from datetime import date
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from occasions import adapters  # noqa: E402
from occasions.feed import build  # noqa: E402
from occasions.jalali import (  # noqa: E402
    HIJRI_OFFICIAL_LENGTHS,
    LunarAnchorTable,
    gregorian_to_jalali,
    jalali_month_length,
    jalali_to_gregorian,
)
from occasions.merge import HolidayCrossCheckView, collect_votes  # noqa: E402
from occasions.model import RawEvent  # noqa: E402

FIXTURES = ROOT / "scripts/occasions/fixtures"
spec = importlib.util.spec_from_file_location("aggregate", ROOT / "scripts/aggregate_occasions.py")
aggregate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(aggregate)


class JalaliConversionTest(unittest.TestCase):
    def test_round_trip_across_four_decades(self):
        for year in range(1390, 1430):
            for month in range(1, 13):
                for day in (1, 15, jalali_month_length(year, month)):
                    value = jalali_to_gregorian(year, month, day)
                    self.assertEqual((year, month, day), gregorian_to_jalali(value))

    def test_known_anchor_dates(self):
        self.assertEqual(date(2025, 3, 21), jalali_to_gregorian(1404, 1, 1))
        self.assertEqual(date(2026, 11, 13), jalali_to_gregorian(1405, 8, 22))
        self.assertEqual((1405, 11, 15), gregorian_to_jalali(date(2027, 2, 4)))


class OfficialLunarTableTest(unittest.TestCase):
    def setUp(self):
        self.table = LunarAnchorTable()

    def test_fatima_martyrdom_is_22_aban_not_23(self):
        """The reported bug: 3 Jumada al-Thani 1448 is 22 Aban 1405, not the arithmetic 23."""
        resolved = self.table.to_gregorian(1448, 6, 3)
        self.assertEqual(date(2026, 11, 13), resolved)
        self.assertEqual((1405, 8, 22), gregorian_to_jalali(resolved))
        self.assertEqual((1405, 8, 23), gregorian_to_jalali(date(2026, 11, 14)))

    def test_ashura_is_4_tir_not_5(self):
        self.assertEqual(date(2026, 6, 25), self.table.to_gregorian(1448, 1, 10))
        self.assertEqual((1405, 4, 4), gregorian_to_jalali(date(2026, 6, 25)))

    def test_eid_al_fitr_occurs_twice_inside_1405(self):
        self.assertEqual(date(2026, 3, 21), self.table.to_gregorian(1447, 10, 1))
        self.assertEqual(date(2027, 3, 10), self.table.to_gregorian(1448, 10, 1))

    def test_months_are_29_or_30_days_and_contiguous(self):
        months = self.table.official_months()
        self.assertGreater(len(months), 300)
        previous_end = None
        for year, month, length in months:
            self.assertIn(length, (29, 30))
            start = self.table.month_start(year, month)
            if previous_end is not None:
                self.assertEqual(previous_end, start, "Lunar months must be contiguous")
            previous_end = start + timedelta(days=length) if False else start
            previous_end = start + __import__("datetime").timedelta(days=length)
        self.assertEqual((1448, 10), self.table.last_official)

    def test_reverse_lookup_is_consistent(self):
        self.assertEqual((1448, 6, 3), self.table.from_gregorian(date(2026, 11, 13)))
        self.assertEqual((1448, 6, 4), self.table.from_gregorian(date(2026, 11, 14)))

    def test_unpublished_months_fall_back_to_an_estimate(self):
        start = self.table.month_start(1449, 1)
        self.assertGreater(start, self.table.month_start(1448, 10))
        self.assertIn(self.table.month_length(1449, 1), (29, 30))
        self.assertFalse(self.table.is_official(1449, 1))

    def test_catalog_round_trip_keeps_the_table(self):
        fields = self.table.as_catalog_fields()
        restored = LunarAnchorTable.from_catalog_fields(json.loads(json.dumps(fields)))
        self.assertEqual(self.table.to_gregorian(1448, 6, 3), restored.to_gregorian(1448, 6, 3))
        self.assertEqual(len(HIJRI_OFFICIAL_LENGTHS), 26)


class AdapterTest(unittest.TestCase):
    def test_pc_events_keeps_hijri_rows_keyed_by_month_and_day(self):
        records = adapters.pc_events_json((FIXTURES / "pc-events.json").read_text(), "pc-events", "open-source")
        self.assertGreater(len(records), 500)
        fatima = [r for r in records if r.calendar == "lunar" and (r.month, r.day) == (6, 3)]
        self.assertTrue(fatima, "The Hijri-keyed Fatima row must survive normalisation")
        self.assertTrue(all(r.calendar in ("jalali", "lunar", "gregorian") for r in records))

    def test_community_year_feeds_are_parsed(self):
        iran = adapters.iran_holidays_json((FIXTURES / "iran-holidays-1405.json").read_text(), "iran-holidays-json", "open-source")
        shamsi = adapters.shamsi_holidays_json((FIXTURES / "shamsi-holidays-1405.json").read_text(), "shamsi-holidays", "open-source")
        jalali_calendar = adapters.jalali_calendar_year_json((FIXTURES / "jalali-calendar-1405.json").read_text(), "jalali-calendar-year", "open-source")
        self.assertTrue(iran and shamsi and jalali_calendar)
        self.assertTrue(all(record.solar is not None for record in iran))

    def test_hijri_labels_are_read_in_both_scripts(self):
        self.assertEqual((6, 3), adapters.parse_hijri_label("شهادت حضرت فاطمه [ ٣ جمادي الثانيه ]"))
        self.assertEqual((1, 10), adapters.parse_hijri_label("عاشورا [ 10 محرم ]"))
        self.assertEqual((12, 18), adapters.parse_hijri_label("غدیر [ ۱۸ ذوالحجه ]"))
        self.assertIsNone(adapters.parse_hijri_label("روز جهانی دیابت"))

    def test_text_adapter_splits_a_day_into_separate_occasions(self):
        records = adapters.persian_calendar_text((FIXTURES / "bahesab-1405.txt").read_text(), "bahesab", "unofficial")
        nowruz = [r for r in records if r.month == 8 and r.day == 22]
        self.assertTrue(nowruz, "22 Aban must be found")
        self.assertTrue(all(r.holiday for r in nowruz))
        self.assertFalse(any(r.month == 8 and r.day == 23 and r.holiday for r in records), "23 Aban is not a closure")
        two_aban = [r for r in records if r.month == 8 and r.day == 2]
        self.assertTrue(two_aban, "2 Aban must be found")
        self.assertFalse(any(r.holiday for r in two_aban), "2 Aban is an observance, not a closure")

    def test_npm_package_exposes_the_official_month_lengths(self):
        table = adapters.npm_official_hijri_months(
            (FIXTURES / "iran-holidays-1.0.0.tgz").read_bytes(), "iran-holidays-npm", "open-source"
        )
        self.assertEqual(date(2002, 3, 16), table["startDate"])
        self.assertEqual(HIJRI_OFFICIAL_LENGTHS[1448][:10], table["lengths"][1448])


class ConsensusTest(unittest.TestCase):
    def setUp(self):
        self.table = LunarAnchorTable()

    def event(self, source, tier, title, month, day, holiday):
        return RawEvent(source_id=source, tier=tier, title=title, calendar="lunar", month=month, day=day, holiday=holiday)

    def test_two_community_calendars_can_confirm_a_closure(self):
        events = [
            self.event("a", "open-source", "عاشورا", 1, 10, True),
            self.event("b", "open-source", "عاشورا", 1, 10, True),
        ]
        votes = collect_votes(events, self.table).votes
        self.assertTrue(votes[("lunar", 1, 10)].holiday())

    def test_one_community_calendar_is_not_enough(self):
        events = [self.event("a", "open-source", "عاشورا", 1, 10, True)]
        votes = collect_votes(events, self.table).votes
        self.assertFalse(votes[("lunar", 1, 10)].holiday())

    def test_an_unofficial_source_alone_never_creates_a_holiday(self):
        events = [self.event("news", "unofficial", "تعطیلی محلی", 4, 4, True)]
        votes = collect_votes(events, self.table).votes
        self.assertFalse(votes[("lunar", 4, 4)].holiday())

    def test_a_tabular_solar_date_is_recorded_as_a_conflict(self):
        event = RawEvent(
            source_id="iran-holidays-json",
            tier="open-source",
            title="شهادت حضرت فاطمه [ ٣ جمادي الثانيه ]",
            calendar="jalali",
            holiday=True,
            solar=date(2026, 11, 14),
        )
        result = collect_votes([event], self.table)
        self.assertEqual(1, len(result.conflicts))
        conflict = result.conflicts[0]
        self.assertEqual("2026-11-14", conflict["reported"])
        self.assertEqual("2026-11-13", conflict["official"])
        self.assertEqual(1, conflict["deltaDays"])
        self.assertIn(("lunar", 6, 3), result.votes)


class FeedBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.registry = aggregate.load_registry()
        cls.seed = json.loads(aggregate.SEED_ASSET.read_text())
        cls.anchors = LunarAnchorTable()
        cls.events, cls.status = aggregate.collect_events(cls.registry, offline=True)
        cls.result = build(
            cls.seed,
            cls.registry,
            cls.events,
            cls.anchors,
            data_version=14,
            updated_at=date(2026, 11, 7),
            years=[1405],
        )
        cls.catalog = cls.result.catalog

    def test_offline_pipeline_reads_at_least_five_of_each_tier(self):
        by_tier = {}
        for source in self.registry["sources"]:
            by_tier.setdefault(source["tier"], set()).add(source["id"])
        self.assertGreaterEqual(len(by_tier["official"]), 5)
        self.assertGreaterEqual(len(by_tier["unofficial"]), 5)
        self.assertGreaterEqual(len(by_tier["open-source"]), 5)
        fetched = {source_id for source_id, entry in self.status.items() if entry["events"] > 0}
        self.assertGreaterEqual(len(fetched), 5, "Machine-readable sources must actually be parsed")

    def test_only_official_calendar_days_are_published_as_holidays(self):
        resolved = self.catalog["verification"]["resolvedHolidays"]["1405"]
        self.assertIn("2026-11-13", resolved, "22 Aban 1405 is the statutory day off")
        self.assertNotIn("2026-11-14", resolved, "23 Aban 1405 is not a holiday")
        self.assertIn("2026-06-24", resolved)
        self.assertNotIn("2026-06-26", resolved)
        self.assertIn("2026-03-21", resolved, "Nowruz / Eid al-Fitr")

    def test_every_holiday_row_is_iranian_and_referenced(self):
        source_ids = {source["id"] for source in self.catalog["sources"]}
        for calendar in ("jalali", "lunar", "gregorian"):
            for row in self.catalog[calendar]:
                self.assertTrue(set(row["sourceIds"]) <= source_ids, row["id"])
                if row["holiday"]:
                    self.assertIn("iranian", row["cultures"], row["id"])
                    self.assertNotEqual("guidance", row["evidence"], row["id"])
                    self.assertNotEqual("educational", row["category"], row["id"])

    def test_lunar_rows_keep_their_calendar_identity(self):
        lunar = self.catalog["lunar"]
        self.assertTrue(any(row["month"] == 6 and row["day"] == 3 for row in lunar))
        self.assertTrue(all(1 <= row["month"] <= 12 and 1 <= row["day"] <= 30 for row in lunar))

    def test_conflicts_are_reported_without_changing_the_published_date(self):
        self.assertTrue(self.result.conflicts)
        deltas = {conflict["deltaDays"] for conflict in self.result.conflicts}
        self.assertTrue(deltas <= {-2, -1, 1, 2})

    def test_generated_catalog_passes_the_repository_validator(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        validator_spec = importlib.util.spec_from_file_location("check_calendar", ROOT / "scripts/check_calendar.py")
        validator = importlib.util.module_from_spec(validator_spec)
        validator_spec.loader.exec_module(validator)
        summary = validator.check(self.catalog)
        self.assertGreaterEqual(summary["annual_occasions"], 990)


class CrossCheckViewTest(unittest.TestCase):
    def test_community_only_days_are_not_reported_as_confirmed(self):
        view = HolidayCrossCheckView(national={101, 102}, feeds=[{101, 103}, {101, 103}])
        self.assertEqual({101}, view.confirmed)
        self.assertEqual({103}, view.community_only)


if __name__ == "__main__":
    unittest.main()
