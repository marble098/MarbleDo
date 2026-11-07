"""Build the published occasion feed (schema 6) from live sources plus the seed catalog.

Output shape is the same JSON contract the Android app parses. The seed catalog is
only a *seed*: it supplies English titles, notes, school programmes and ids that the
sources do not carry. Every date and every public-holiday flag in the output comes
from the sources and from the official lunar table.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date, timedelta

from .jalali import LunarAnchorTable, jalali_to_gregorian
from .merge import (
    GREGORIAN,
    JALALI,
    LUNAR,
    OFFICIAL,
    Vote,
    collect_votes,
    match_seed_title,
    resolved_dates,
    similarity,
)
from .model import RawEvent, normalise

CATEGORIES = ("official", "national", "religious", "personal", "international", "educational")
CATALOG_CALENDARS = ("jalali", "lunar", "gregorian")
TIER_KIND = {"official": "institution", "unofficial": "report", "open-source": "community-rules"}


@dataclass
class FeedBuild:
    catalog: dict
    health: dict
    conflicts: list[dict]

    def to_json(self) -> dict:
        return {"catalog": self.catalog, "health": self.health, "conflicts": self.conflicts}


def _slug(value: str) -> str:
    return "".join(character if character.isalnum() else "-" for character in normalise(value)).strip("-")[:60]


def _source_entries(seed: dict, registry: dict, checked_at: date) -> list[dict]:
    """Seed references stay (ids are used by existing rows); registry sources are added."""
    sources: list[dict] = []
    seen: set[str] = set()
    for reference in seed.get("sources", []):
        entry = dict(reference)
        entry.setdefault("tier", "official" if reference.get("kind") == "institution" else "open-source")
        sources.append(entry)
        seen.add(entry["id"])
    for source in registry["sources"]:
        if source["id"] in seen:
            continue
        sources.append(
            {
                "id": source["id"],
                "titleFa": source["nameFa"],
                "titleEn": source["nameEn"],
                "url": source["url"],
                "kind": TIER_KIND.get(source["tier"], "report"),
                "checkedAt": checked_at.isoformat(),
                "tier": source["tier"],
            }
        )
        seen.add(source["id"])
    return sources


def _best_title(rows: list[dict], titles: Counter) -> dict | None:
    best, score = None, 0.0
    for title in titles:
        row = match_seed_title(rows, title)
        if row is None:
            continue
        value = similarity(title, row.get("titleFa") or row.get("titleEn"))
        if value > score:
            best, score = row, value
    return best


def build(
    seed: dict,
    registry: dict,
    events: list[RawEvent],
    anchors: LunarAnchorTable,
    *,
    data_version: int,
    updated_at: date,
    years: list[int] | None = None,
) -> FeedBuild:
    merged = collect_votes(events, anchors)
    votes = merged.votes
    status = Counter(event.source_id for event in events)

    sources = _source_entries(seed, registry, updated_at)
    known_source_ids = {source["id"] for source in sources}

    sections: dict[str, list[dict]] = {}
    rows_by_calendar: dict[str, list[dict]] = {}
    used_keys: set[tuple[str, int, int]] = set()
    holiday_days: dict[tuple[str, int, int], bool] = {}

    for calendar in CATALOG_CALENDARS:
        rows_by_calendar[calendar] = [dict(row) for row in seed.get(calendar, [])]

    # How many seed rows share the same calendar day? A shared day needs a title
    # match before it inherits the day's holiday flag.
    day_counts = Counter(
        (calendar, row["month"], row["day"])
        for calendar, rows in rows_by_calendar.items()
        for row in rows
    )

    for calendar, rows in rows_by_calendar.items():
        output: list[dict] = []
        for row in rows:
            key = (calendar, row["month"], row["day"])
            used_keys.add(key)
            vote: Vote | None = votes.get(key)
            source_ids = list(dict.fromkeys(row.get("sourceIds", [])))
            if vote:
                source_ids = list(dict.fromkeys(source_ids + sorted(vote.sources)))
                day_holiday = vote.holiday()
                if day_counts[key] == 1:
                    row["holiday"] = day_holiday
                else:
                    matched = any(
                        similarity(title, row.get("titleFa", "")) >= 0.35 or
                        similarity(title, row.get("titleEn", "")) >= 0.35
                        for title in vote.holiday_titles
                    )
                    row["holiday"] = day_holiday and matched
                if len(vote.sources) >= 2 and row.get("evidence") in (None, "legacy"):
                    row["evidence"] = "sourced"
                holiday_days[key] = day_holiday
            else:
                # No live confirmation this run: keep a lunar occasion's own flag only
                # when nobody contradicted it, and never invent one.
                row["holiday"] = bool(row.get("holiday")) and calendar != LUNAR
                if calendar == LUNAR:
                    row["holiday"] = bool(row.get("holiday"))
            if row.get("evidence") == "guidance" or row.get("category") == "educational":
                row["holiday"] = False
            if "iranian" not in row.get("cultures", []):
                row["holiday"] = False
            row["sourceIds"] = [value for value in source_ids if value in known_source_ids]
            if not row["sourceIds"]:
                row["sourceIds"] = ["ut-geophysics"]
            output.append(row)
        sections[calendar] = output

    # A lunar occasion reported on a solar date is a duplicate of the correctly resolved
    # lunar row - exactly the "frozen to a one-year solar copy" noise that must not return.
    lunar_titles = [title for (calendar, _, _), vote in votes.items() if calendar == LUNAR for title in vote.titles]

    def duplicates_lunar(title: str) -> bool:
        return any(similarity(title, candidate) >= 0.5 for candidate in lunar_titles)

    # New occasion discovered by the sources that the seed never had.
    added = 0
    for (calendar, month, day), vote in sorted(votes.items()):
        if (calendar, month, day) in used_keys:
            continue
        if len(vote.sources) < 2 and not any(tier == OFFICIAL for tier in vote.tiers):
            continue
        if calendar not in CATALOG_CALENDARS:
            continue
        title = vote.titles.most_common(1)[0][0]
        if not title.strip():
            continue
        if calendar in (JALALI, GREGORIAN) and duplicates_lunar(title):
            continue
        matched = _best_title(rows_by_calendar[calendar], vote.titles)
        english = matched.get("titleEn") if matched else title
        holiday = vote.holiday()
        category = "religious" if calendar == LUNAR else ("official" if holiday else "national")
        if category not in CATEGORIES:
            category = "national"
        identifier = f"{calendar}-{month}-{day}-{_slug(english)}"
        if any(row.get("id") == identifier for row in sections[calendar]):
            continue
        sections[calendar].append(
            {
                "month": month,
                "day": day,
                "id": identifier,
                "titleFa": title,
                "titleEn": english,
                "category": category,
                "holiday": holiday,
                "evidence": "sourced",
                "sourceIds": [value for value in sorted(vote.sources) if value in known_source_ids] or ["ut-geophysics"],
                "cultures": ["iranian"],
                "tags": ["islamic"] if calendar == LUNAR else [],
            }
        )
        added += 1

    for calendar in CATALOG_CALENDARS:
        sections[calendar].sort(key=lambda row: (row["month"], row["day"]))

    resolved = {year: _year_holidays(sections, anchors, year) for year in (years or [])}

    catalog = {
        "schemaVersion": 6,
        "calendar": "jalali",
        "dataVersion": str(data_version),
        "updatedAt": updated_at.isoformat(),
        "note": (
            "Generated online from official, unofficial and open-source sources; "
            "lunar dates resolve against the official Iranian month table."
        ),
        "lunarAnchors": anchors.as_catalog_fields(),
        "jalali": sections["jalali"],
        "lunar": sections["lunar"],
        "gregorian": sections["gregorian"],
        "yearly": seed.get("yearly", {}),
        "idAliases": seed.get("idAliases", {}),
        "sources": sources,
        "schoolPrograms": seed.get("schoolPrograms", []),
        "verification": {
            "generatedAt": updated_at.isoformat(),
            "participatingSources": len({event.source_id for event in events}),
            "tiers": dict(Counter(source.get("tier", "open-source") for source in sources)),
            "lunarDaysCorroborated": len([vote for vote in votes.values() if vote.sources]),
            "conflictingReports": len(merged.conflicts),
            "addedFromSources": added,
            "resolvedHolidays": {str(year): values for year, values in resolved.items()},
        },
    }

    health = {
        "generatedAt": updated_at.isoformat(),
        "sources": [
            {
                "id": source["id"],
                "tier": source["tier"],
                "events": status.get(source["id"], 0),
                "ok": status.get(source["id"], 0) > 0,
            }
            for source in registry["sources"]
        ],
        "conflicts": len(merged.conflicts),
        "addedFromSources": added,
    }
    return FeedBuild(catalog=catalog, health=health, conflicts=merged.conflicts)


def _year_holidays(sections: dict, anchors: LunarAnchorTable, jalali_year: int) -> list[str]:
    start = jalali_to_gregorian(jalali_year, 1, 1)
    end = jalali_to_gregorian(jalali_year + 1, 1, 1)
    days: set[str] = set()
    for row in sections["jalali"]:
        if not row["holiday"]:
            continue
        value = jalali_to_gregorian(jalali_year, row["month"], row["day"])
        if start <= value < end:
            days.add(value.isoformat())
    for row in sections["lunar"]:
        if not row["holiday"]:
            continue
        for occurrences in resolved_dates(anchors, row["month"], row["day"], [jalali_year]).values():
            days.update(value.isoformat() for value in occurrences)
    gregorian_holidays = {(row["month"], row["day"]) for row in sections["gregorian"] if row["holiday"]}
    if gregorian_holidays:
        cursor = start
        while cursor < end:
            if (cursor.month, cursor.day) in gregorian_holidays:
                days.add(cursor.isoformat())
            cursor += timedelta(days=1)
    return sorted(days)


