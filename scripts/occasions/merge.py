"""Cross-source consensus for the occasion feed.

The merge is deliberately conservative:

* A **lunar** occasion is always placed on the date the official month table gives.
  If a source reports a different solar date for the same Hijri month/day, that is
  recorded as a conflict and the official date wins. This is the bug that showed
  "22 Aban" (martyrdom of Fatima, 3 Jumada al-Thani 1448) on 23 Aban.
* A **public holiday** needs an official-tier source, an official-basis open-source
  record, or at least two independent sources that agree on the same calendar day.
  One community calendar alone is never enough.
* A **Jalali/Gregorian** fixed date is kept, but a conflicting report is surfaced.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, timedelta

from .jalali import LunarAnchorTable, gregorian_to_jalali, jalali_to_gregorian
from .model import GREGORIAN, JALALI, LUNAR, RawEvent, normalise, title_key

OFFICIAL = "official"
UNOFFICIAL = "unofficial"
OPEN_SOURCE = "open-source"

# Sources whose lunar/solar mapping is derived from the official calendar.
OFFICIAL_BASIS = {"pc-events", "iran-holidays-npm"}


@dataclass
class Vote:
    sources: set[str] = field(default_factory=set)
    holiday_sources: set[str] = field(default_factory=set)
    tiers: set[str] = field(default_factory=set)
    official_basis_holiday: set[str] = field(default_factory=set)
    titles: Counter = field(default_factory=Counter)
    holiday_titles: Counter = field(default_factory=Counter)
    reported: set[date] = field(default_factory=set)

    def add(self, event: RawEvent) -> None:
        self.sources.add(event.source_id)
        self.tiers.add(event.tier)
        if event.holiday:
            self.holiday_sources.add(event.source_id)
            self.holiday_titles[event.title] += 1
        if event.holiday and event.source_id in OFFICIAL_BASIS:
            self.official_basis_holiday.add(event.source_id)
        self.titles[event.title] += 1
        if event.solar:
            self.reported.add(event.solar)

    def holiday(self) -> bool:
        if self.official_basis_holiday:
            # pc-events carries the official calendar's own holiday flags.
            return True
        if any(tier == OFFICIAL for tier in self.tiers) and self.holiday_sources:
            return True
        return len(self.holiday_sources) >= 2


@dataclass
class MergeResult:
    votes: dict[tuple[str, int, int], Vote]
    conflicts: list[dict]


def collect_votes(events: list[RawEvent], anchors: LunarAnchorTable) -> MergeResult:
    votes: dict[tuple[str, int, int], Vote] = defaultdict(Vote)
    conflicts: list[dict] = []
    for event in events:
        label = event.hijri_month_day
        if label is None and event.solar is not None:
            label = _lunar_label_from_event(event, anchors)
        if label is not None:
            month, day = label
            key = (LUNAR, month, day)
            votes[key].add(event)
            if event.solar is not None:
                candidates = _lunar_occurrences(anchors, month, day, gregorian_to_jalali(event.solar)[0])
                # The same Hijri day can occur twice in one Jalali year (Eid al-Fitr), so the
                # nearest occurrence is the one the report can meaningfully disagree with.
                expected = min(candidates, key=lambda value: abs((value - event.solar).days), default=None)
                if expected is not None and expected != event.solar:
                    conflicts.append(
                        {
                            "kind": "lunar-date",
                            "calendar": LUNAR,
                            "month": month,
                            "day": day,
                            "title": event.title,
                            "sourceId": event.source_id,
                            "tier": event.tier,
                            "reported": event.solar.isoformat(),
                            "official": expected.isoformat(),
                            "deltaDays": (event.solar - expected).days,
                        }
                    )
            continue
        if event.calendar == JALALI and event.month and event.day:
            votes[(JALALI, event.month, event.day)].add(event)
        elif event.calendar == GREGORIAN and event.month and event.day:
            votes[(GREGORIAN, event.month, event.day)].add(event)
        elif event.solar is not None:
            _, month, day = gregorian_to_jalali(event.solar)
            votes[(JALALI, month, day)].add(event)
    return MergeResult(votes=dict(votes), conflicts=conflicts)


def _lunar_label_from_event(event: RawEvent, anchors: LunarAnchorTable) -> tuple[int, int] | None:
    """Read an explicit Hijri label from the event's own text (never invert the date)."""
    from .adapters import parse_hijri_label

    for text in (event.note, event.title):
        if not text:
            continue
        label = parse_hijri_label(text)
        if label is not None:
            return label
    return None


def hijri_years_for_jalali_year(anchors: LunarAnchorTable, jalali_year: int) -> list[int]:
    start = jalali_to_gregorian(jalali_year, 1, 1)
    end = jalali_to_gregorian(jalali_year + 1, 1, 1)
    first = anchors.from_gregorian(start - timedelta(days=35))
    last = anchors.from_gregorian(end + timedelta(days=5))
    if first is None or last is None:
        return []
    return list(range(first[0], last[0] + 1))


def _lunar_occurrences(
    anchors: LunarAnchorTable, month: int, day: int, jalali_year: int
) -> list[date]:
    start = jalali_to_gregorian(jalali_year, 1, 1)
    end = jalali_to_gregorian(jalali_year + 1, 1, 1)
    occurrences: list[date] = []
    for hijri_year in hijri_years_for_jalali_year(anchors, jalali_year):
        resolved = anchors.to_gregorian(hijri_year, month, day)
        if start <= resolved < end and resolved not in occurrences:
            occurrences.append(resolved)
    return occurrences


def _lunar_in_year(
    anchors: LunarAnchorTable, month: int, day: int, jalali_year: int
) -> date | None:
    occurrences = _lunar_occurrences(anchors, month, day, jalali_year)
    return occurrences[0] if occurrences else None


def resolved_dates(
    anchors: LunarAnchorTable, month: int, day: int, jalali_years: list[int]
) -> dict[int, list[date]]:
    """Every occurrence of a lunar occasion inside each Jalali year.

    A Jalali year can hold the *same* Hijri month/day twice: Eid al-Fitr falls on
    1 Farvardin 1405 and again on 19 Esfand 1405. Returning a single date would
    silently drop a public holiday.
    """
    result: dict[int, list[date]] = {}
    for year in jalali_years:
        occurrences = _lunar_occurrences(anchors, month, day, year)
        if occurrences:
            result[year] = occurrences
    return result


def similarity(left: str, right: str) -> float:
    """Token overlap of two Persian titles, 0..1, tolerant of Persian suffixes.

    "عاشورا" and "عاشورای حسینی" are the same occasion; token overlap alone scores
    that pair 0 because the ezafe suffix changes the token, so a prefix match counts
    as a strong signal.
    """
    normalised_left = normalise(left)
    normalised_right = normalise(right)
    left_tokens = set(normalised_left.split())
    right_tokens = set(normalised_right.split())
    if not left_tokens or not right_tokens:
        return 0.0
    score = len(left_tokens & right_tokens) / len(left_tokens | right_tokens)
    compact_left = normalised_left.replace(" ", "")
    compact_right = normalised_right.replace(" ", "")
    if compact_left and compact_right and (
        compact_left == compact_right
        or compact_left.startswith(compact_right)
        or compact_right.startswith(compact_left)
    ):
        score = max(score, 0.9)
    return score


def match_seed_title(seed_rows: list[dict], title: str) -> dict | None:
    best, score = None, 0.0
    for row in seed_rows:
        for candidate in (row.get("titleFa", ""), row.get("titleEn", "")):
            value = similarity(title, candidate)
            if value > score:
                best, score = row, value
    return best if score >= 0.5 else None


@dataclass(frozen=True)
class HolidayCrossCheckView:
    """Mirror of the app's HolidayCrossChecker so both sides apply one rule."""

    national: set[int]
    feeds: list[set[int]]

    @property
    def _community(self) -> set[int]:
        votes: Counter = Counter()
        for days in self.feeds:
            votes.update(days)
        return {day for day, count in votes.items() if count >= 2}

    @property
    def confirmed(self) -> set[int]:
        return self.national & self._community

    @property
    def community_only(self) -> set[int]:
        return self._community - self.national
