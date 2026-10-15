#!/usr/bin/env python3
"""Offline integrity and regression checks for the versioned occasions catalog.

This validates structure and known corrections, NOT the historical truth of every entry.
Source review and yearly Ministry circular review remain human tasks; see docs/OCCASION_SOURCES.md.
"""
from __future__ import annotations

import calendar
import json
from collections import Counter
from datetime import date
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "feature/calendar/src/main/assets/calendar/occasions-v5-fa.json"
CALENDARS = ("jalali", "lunar", "gregorian")
CATEGORIES = {"official", "national", "religious", "personal", "international", "educational"}
EVIDENCE = {"sourced", "traditional", "guidance", "legacy"}
RULES = {"fixed", "month_end", "last_weekday", "nth_weekday", "weekday_on_or_before", "western_easter", "orthodox_easter", "sunday_to_saturday"}
CULTURES = {"iranian", "global", "western", "north_america", "europe", "oceania"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def check(catalog: dict) -> dict:
    require(catalog["schemaVersion"] == 5, "Unsupported schema")
    require(int(catalog["dataVersion"]) >= 8, "Catalog must not roll back to pre-review data")
    date.fromisoformat(catalog["updatedAt"])
    references = catalog["sources"]
    source_ids = {s["id"] for s in references}
    require(len(source_ids) == len(references), "Duplicate source ids")
    for ref in references:
        uri = urlparse(ref["url"])
        require(uri.scheme == "https" and bool(uri.hostname) and uri.username is None, "Unsafe source URL")
        require(bool(ref["titleFa"].strip()) and bool(ref["titleEn"].strip()), "Source title missing")
        date.fromisoformat(ref["checkedAt"])

    def references_exist(row: dict) -> None:
        require(bool(row["sourceIds"]) and set(row["sourceIds"]) <= source_ids, f"Unknown or missing source: {row['id']}")

    def entries(section: dict) -> list[dict]:
        result = []
        ids, identities = set(), set()
        for system in CALENDARS:
            for row in section.get(system, []):
                month, day = row["month"], row["day"]
                require(type(month) is int and 1 <= month <= 12, "Invalid month")
                max_day = (31 if month <= 6 else 30) if system == "jalali" else (30 if system == "lunar" else calendar.monthrange(2024, month)[1])
                require(type(day) is int and 1 <= day <= max_day, "Invalid calendar day")
                require(row["category"] in CATEGORIES, "Unknown category")
                require(type(row["holiday"]) is bool, "Holiday must be a boolean")
                require(bool(row["titleFa"].strip()) and bool(row["titleEn"].strip()), "Title missing")
                require(row["id"] and row["id"] not in ids, "Duplicate or empty stable id")
                ids.add(row["id"])
                identity = (system, month, day, row["titleEn"].casefold())
                require(identity not in identities, "Duplicate date/title")
                identities.add(identity)
                require(row["evidence"] in EVIDENCE, "Unknown evidence status")
                if row["evidence"] == "guidance" or row["category"] == "educational":
                    require(not row["holiday"], "Guidance/school activity must not create a public holiday")
                references_exist(row)
                cultures = row.get("cultures", [])
                require(bool(cultures) and set(cultures) <= CULTURES, "Unknown or missing culture pack")
                if "iranian" not in cultures:
                    require(not row["holiday"], "Foreign observance must not create an Iranian public holiday")
                rule = row.get("dateRule", "fixed")
                require(rule in RULES, "Unknown date rule")
                if rule in {"last_weekday", "nth_weekday", "weekday_on_or_before"}:
                    require(1 <= row.get("weekday", 0) <= 7, "Invalid rule weekday")
                if rule == "nth_weekday":
                    require(1 <= row.get("ordinal", 0) <= 5, "Invalid weekday ordinal")
                require(-60 <= row.get("dayOffset", 0) <= 60, "Unbounded rule offset")
                if rule in {"western_easter", "orthodox_easter"}:
                    require(system == "gregorian", "Easter rules require Gregorian years")
                require(1 <= row.get("durationDays", 1) <= 31, "Invalid window duration")
                result.append(row | {"calendar": system})
        return result

    rows = entries(catalog)
    require(len(rows) >= 990, "Unexpectedly depleted annual catalog")
    require(len(catalog["lunar"]) >= 100, "Unexpectedly depleted Islamic catalog")
    require({r["category"] for r in rows} == CATEGORIES, "Lost category")
    for system in CALENDARS:
        require({r["month"] for r in catalog[system]} == set(range(1, 13)), f"Lost all records in a {system} month")
    for year, section in catalog["yearly"].items():
        require(1200 <= int(year) <= 1700, "Invalid override year")
        entries(section)
    programs = catalog["schoolPrograms"]
    require(len(programs) >= 20, "Missing school programs")
    require(len({p["id"] for p in programs}) == len(programs), "Duplicate school programs")
    for program in programs:
        references_exist(program)
        require(not ({"month", "day", "date", "calendar"} & program.keys()), "Do not invent a fixed date for a variable school program")
        require(program["noteFa"] and program["noteEn"], "Variable program must explain scheduling")
    ids = {r["id"] for r in rows}
    for old, new in catalog["idAliases"].items():
        require(old != new and old not in ids and new in ids, "Invalid favorite migration")

    by_title = {r["titleEn"]: r for r in rows}
    # Independently reviewed regressions; do not silently reintroduce the previous catalog mistakes.
    for title, system, month, day in [
        ("Islamic Education Affairs Day", "jalali", 12, 8),
        ("Death of Lady Ruqayya (traditional report)", "lunar", 2, 5),
        ("Martyrdom of Imam Hassan", "lunar", 2, 7),
        ("Day against British Colonialism", "jalali", 6, 12),
        ("Nahj al-Balagha Day", "lunar", 9, 19),
        ("Sahifa Sajjadiyya Day", "lunar", 8, 5),
        ("Family and Retirees Day", "lunar", 12, 25),
        ("Shah Cheragh Commemoration Day", "lunar", 11, 6),
        ("Mohtasham Kashani Day", "lunar", 1, 1),
    ]:
        row = by_title[title]
        require((row["calendar"], row["month"], row["day"]) == (system, month, day), f"Date regression: {title}")
        require(row["evidence"] != "legacy", f"Missing correction reference: {title}")
    for title in ["Martyrdom of Imam Reza (last day of Safar)", "Martyrdom of Imam Jawad"]:
        require(by_title[title].get("dateRule") == "month_end", f"Lost month-end rule: {title}")
    require(by_title["International Quds Day"].get("weekday") == 5, "Quds must remain Friday")
    require(by_title["Chaharshanbe Suri"].get("dayOffset") == -1, "Chaharshanbe Suri is an evening before Wednesday")
    require(not any(r["calendar"] == "jalali" and r["titleEn"] in {
        "Family and Retirees Day", "Endowment (Waqf) Day", "Girls' Day", "Mother and Women's Day",
        "Anniversary of Ali and Fatima's Wedding",
    } for r in rows), "A lunar occasion was incorrectly frozen to a solar date")
    require({culture for row in rows for culture in row["cultures"]} == CULTURES, "Lost culture pack")
    for title, expected in {
        "Western Easter Sunday": "western_easter", "Orthodox Easter Sunday": "orthodox_easter",
        "Thanksgiving (US)": "nth_weekday", "Thanksgiving (Canada)": "nth_weekday",
        "Black Friday": "nth_weekday", "Cyber Monday": "nth_weekday",
        "Advent Sunday 1": "weekday_on_or_before", "Kings Day (Netherlands)": "sunday_to_saturday",
        "World Maritime Day": "last_weekday", "International Day of Cooperatives": "nth_weekday",
    }.items():
        require(by_title[title].get("dateRule") == expected, f"Lost floating-date rule: {title}")
    return {
        "annual_occasions": len(rows),
        "per_calendar": dict(Counter(r["calendar"] for r in rows)),
        "per_culture": dict(Counter(culture for row in rows for culture in row["cultures"])),
        "evidence": dict(Counter(r["evidence"] for r in rows)),
        "school_relevant": sum("education" in r.get("tags", []) for r in rows),
        "variable_school_programs": len(programs),
        "source_references": len(references),
    }


if __name__ == "__main__":
    print(json.dumps(check(json.loads(CATALOG.read_text())), ensure_ascii=False, indent=2))
