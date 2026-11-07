"""Calendar arithmetic shared by the occasion aggregation pipeline.

Three calendars are involved:

* Gregorian (``datetime.date``) - the wire format used by every JSON asset.
* Jalali / Solar Hijri - fixed 31/30-day months, a 33-year leap cycle.
* Hijri lunar - **not** the tabular ``islamic-civil`` calendar. Iran's official
  calendar follows the announced crescent sighting, so the month start moves by
  a day compared with the arithmetic calendar. The official month lengths for
  1423-1448 AH are recorded below and are the only authority the app trusts for
  converting a lunar occasion to a solar day.

The tabular calendar is still implemented: it is used to *estimate* months that
the official calendar has not published yet, and a single drift correction keeps
that estimate aligned with the last published month.
"""
from __future__ import annotations

from datetime import date, timedelta

# ---------------------------------------------------------------------------
# Jalali <-> Gregorian
# ---------------------------------------------------------------------------

_GREGORIAN_DAYS_IN_MONTH = (0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)


def _is_gregorian_leap(year: int) -> bool:
    return (year % 4 == 0 and year % 100 != 0) or year % 400 == 0


def jalali_to_gregorian(jy: int, jm: int, jd: int) -> date:
    """Convert a Jalali date to ``datetime.date`` (validated by round-trip tests)."""
    if not (1 <= jm <= 12 and 1 <= jd <= jalali_month_length(jy, jm)):
        raise ValueError(f"Invalid Jalali date {jy}/{jm}/{jd}")
    jy += 1595
    days = -355668 + (365 * jy) + ((jy // 33) * 8) + (((jy % 33) + 3) // 4) + jd
    days += (jm - 1) * 31 if jm < 7 else ((jm - 7) * 30) + 186
    gy = 400 * (days // 146097)
    days %= 146097
    if days > 36524:
        days -= 1
        gy += 100 * (days // 36524)
        days %= 36524
        if days >= 365:
            days += 1
    gy += 4 * (days // 1461)
    days %= 1461
    if days > 365:
        gy += (days - 1) // 365
        days = (days - 1) % 365
    gd = days + 1
    month = 0
    while month < 13 and gd > _month_length(gy, month):
        gd -= _month_length(gy, month)
        month += 1
    return date(gy, month, gd)


def _month_length(gy: int, gm: int) -> int:
    if gm == 2:
        return 29 if _is_gregorian_leap(gy) else 28
    return _GREGORIAN_DAYS_IN_MONTH[gm]


_MONTH_START_DAYS = (0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)


def gregorian_to_jalali(value: date) -> tuple[int, int, int]:
    gy, gm, gd = value.year, value.month, value.day
    if gy > 1600:
        jy = 979
        gy -= 1600
    else:
        jy = 0
        gy -= 621
    gy2 = gy + 1 if gm > 2 else gy
    days = (
        (365 * gy)
        + ((gy2 + 3) // 4)
        - ((gy2 + 99) // 100)
        + ((gy2 + 399) // 400)
        - 80
        + gd
        + _MONTH_START_DAYS[gm - 1]
    )
    jy += 33 * (days // 12053)
    days %= 12053
    jy += 4 * (days // 1461)
    days %= 1461
    if days > 365:
        jy += (days - 1) // 365
        days = (days - 1) % 365
    if days < 186:
        return jy, 1 + (days // 31), 1 + (days % 31)
    return jy, 7 + ((days - 186) // 30), 1 + ((days - 186) % 30)


def jalali_month_length(jy: int, jm: int) -> int:
    if not 1 <= jm <= 12:
        raise ValueError(f"Invalid Jalali month {jm}")
    if jm <= 6:
        return 31
    if jm <= 11:
        return 30
    # Esfand has 29 days unless the year is a leap year in the 33-year cycle.
    return 30 if ((25 * jy + 11) % 33) < 8 else 29


def is_jalali_leap(jy: int) -> bool:
    return jalali_month_length(jy, 12) == 30


def jalali_today(reference: date) -> tuple[int, int, int]:
    return gregorian_to_jalali(reference)


# ---------------------------------------------------------------------------
# Hijri lunar: official Iranian month lengths
# ---------------------------------------------------------------------------
#
# 1 Muharram 1423 AH = 2002-03-16. Each tuple lists the *official* length of the
# twelve months of that Hijri year, in order. The table ends with the months Iran
# has published so far for 1448 AH (through Shawwal), which covers the whole of
# 1405 SH - the year whose lunar dates the tabular calendar got wrong.
#
# Source of the table: the official Iranian calendar as republished by the
# open-source `iran-holidays` dataset (ISC). It is cross-checked on every build by
# `aggregate_occasions.py --verify`, which compares the resolved solar dates
# against the independently maintained holiday feeds.
HIJRI_ANCHOR_START = date(2002, 3, 16)
HIJRI_ANCHOR_START_YEAR = 1423
HIJRI_OFFICIAL_LENGTHS: dict[int, list[int]] = {
    1423: [29, 30, 30, 29, 29, 30, 29, 30, 29, 30, 29, 30],
    1424: [30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29],
    1425: [30, 29, 30, 30, 29, 30, 30, 29, 29, 30, 29, 30],
    1426: [29, 29, 30, 29, 30, 30, 30, 29, 30, 30, 29, 29],
    1427: [30, 29, 29, 30, 29, 30, 30, 30, 29, 30, 29, 30],
    1428: [29, 30, 29, 29, 29, 30, 30, 29, 30, 30, 30, 29],
    1429: [30, 29, 30, 29, 29, 29, 30, 30, 29, 30, 30, 29],
    1430: [30, 30, 29, 29, 30, 29, 30, 29, 29, 30, 30, 29],
    1431: [30, 30, 29, 30, 29, 30, 29, 30, 29, 29, 30, 29],
    1432: [30, 30, 29, 30, 30, 30, 29, 30, 29, 29, 30, 29],
    1433: [29, 30, 29, 30, 30, 30, 29, 30, 29, 30, 29, 30],
    1434: [29, 29, 30, 29, 30, 30, 29, 30, 30, 29, 30, 29],
    1435: [29, 30, 29, 30, 29, 30, 29, 30, 30, 30, 29, 30],
    1436: [29, 30, 29, 29, 30, 29, 30, 29, 30, 29, 30, 30],
    1437: [29, 30, 30, 29, 30, 29, 29, 30, 29, 29, 30, 30],
    1438: [29, 30, 30, 30, 29, 30, 29, 29, 30, 29, 29, 30],
    1439: [29, 30, 30, 30, 30, 29, 30, 29, 29, 30, 29, 29],
    1440: [30, 29, 30, 30, 30, 29, 30, 30, 29, 29, 30, 29],
    1441: [29, 30, 29, 30, 30, 29, 30, 30, 29, 30, 29, 30],
    1442: [29, 29, 30, 29, 30, 29, 30, 30, 29, 30, 30, 29],
    1443: [29, 30, 30, 29, 29, 30, 29, 30, 29, 30, 30, 29],
    1444: [30, 30, 29, 30, 29, 29, 30, 29, 30, 29, 30, 29],
    1445: [30, 30, 30, 29, 30, 29, 29, 30, 29, 30, 29, 29],
    1446: [30, 30, 30, 29, 30, 30, 29, 30, 29, 29, 29, 30],
    1447: [29, 30, 30, 29, 30, 30, 30, 29, 30, 29, 29, 29],
    1448: [30, 29, 30, 29, 30, 30, 30, 29, 30, 29],
}

_MONTH_NAMES = {
    "محرم": 1,
    "صفر": 2,
    "ربيع الاول": 3,
    "ربيع‌الاول": 3,
    "ربيع الثاني": 4,
    "ربيع‌الثاني": 4,
    "جمادي الاول": 5,
    "جمادي‌الاول": 5,
    "جمادي الاولي": 5,
    "جمادي الاولى": 5,
    "جمادي الثاني": 6,
    "جمادي‌الثاني": 6,
    "جمادي الثانيه": 6,
    "جمادي الثانية": 6,
    "رجب": 7,
    "شعبان": 8,
    "رمضان": 9,
    "شوال": 10,
    "ذو القعده": 11,
    "ذوالقعده": 11,
    "ذو الحجه": 12,
    "ذوالحجه": 12,
}


def _fold_name(name: str) -> str:
    """Fold Arabic/Persian letter variants so month names match in either script."""
    folded = name.replace("\u200c", " ").replace("\u200f", "").replace("\u200e", "")
    for source, target in (
        ("ي", "ی"), ("ك", "ک"), ("ة", "ه"), ("ۀ", "ه"), ("أ", "ا"), ("إ", "ا"), ("آ", "ا"),
        ("ٔ", ""), ("ّ", ""), ("َ", ""), ("ُ", ""), ("ِ", ""), ("ْ", ""),
    ):
        folded = folded.replace(source, target)
    return " ".join(folded.split())


_FOLDED_MONTH_NAMES = {_fold_name(name): index for name, index in _MONTH_NAMES.items()}


def hijri_month_name_index(name: str) -> int | None:
    """Map a Persian/Arabic Hijri month name to 1..12, ignoring letter variants."""
    return _FOLDED_MONTH_NAMES.get(_fold_name(name))


class LunarAnchorTable:
    """Official Iranian lunar month table plus a calibrated estimate beyond it."""

    def __init__(self, lengths: dict[int, list[int]] | None = None) -> None:
        self._official: list[tuple[int, int, date, int]] = []
        current = HIJRI_ANCHOR_START
        table = lengths if lengths is not None else HIJRI_OFFICIAL_LENGTHS
        for year in sorted(table):
            for index, month_length in enumerate(table[year]):
                if month_length not in (29, 30):
                    raise ValueError(f"Invalid lunar month length {month_length} for {year}/{index + 1}")
                self._official.append((year, index + 1, current, month_length))
                current += timedelta(days=month_length)
        self._by_key = {(y, m): (start, length) for y, m, start, length in self._official}
        self._index = {(y, m): i for i, (y, m, _, _) in enumerate(self._official)}
        last_year, last_month, last_start, last_length = self._official[-1]
        self.last_official = (last_year, last_month)
        # Drift of the tabular calendar against the last published month; the
        # estimate for later months is the tabular date shifted by this drift.
        self._drift = (last_start - tabular_hijri_to_gregorian(last_year, last_month, 1)).days

    # -- official range -----------------------------------------------------
    def official_months(self) -> list[tuple[int, int, int]]:
        return [(y, m, length) for y, m, _, length in self._official]

    def is_official(self, year: int, month: int) -> bool:
        return (year, month) in self._by_key

    def month_start(self, year: int, month: int) -> date:
        key = (year, month)
        if key in self._by_key:
            return self._by_key[key][0]
        return self._estimated_start(year, month)

    def month_length(self, year: int, month: int) -> int:
        key = (year, month)
        if key in self._by_key:
            return self._by_key[key][1]
        start = self._estimated_start(year, month)
        return (self._estimated_start(*_next_month(year, month)) - start).days

    def _estimated_start(self, year: int, month: int) -> date:
        start = tabular_hijri_to_gregorian(year, month, 1) + timedelta(days=self._drift)
        return start

    # -- conversions --------------------------------------------------------
    def to_gregorian(self, year: int, month: int, day: int) -> date:
        if not 1 <= day <= 30:
            raise ValueError(f"Invalid lunar day {day}")
        start = self.month_start(year, month)
        length = self.month_length(year, month)
        if day > length:
            # A month that turned out to be 29 days long: clamp, as the official
            # "last day of the month" rule does.
            day = length
        return start + timedelta(days=day - 1)

    def from_gregorian(self, value: date) -> tuple[int, int, int] | None:
        year, month = _estimate_hijri_year_month(value, self._drift)
        for _ in range(4):
            start = self.month_start(year, month)
            if value < start:
                year, month = _previous_month(year, month)
                continue
            length = self.month_length(year, month)
            if (value - start).days >= length:
                year, month = _next_month(year, month)
                continue
            return year, month, (value - start).days + 1
        return None

    # -- serialisation ------------------------------------------------------
    def as_catalog_fields(self) -> dict:
        """Compact schema-6 representation shared by the asset and the online feed."""
        official = self.official_months()
        estimated: list[int] = []
        cursor_year, cursor_month = _next_month(*self.last_official)
        for _ in range(36):
            estimated.append(self.month_length(cursor_year, cursor_month))
            cursor_year, cursor_month = _next_month(cursor_year, cursor_month)
        return {
            "startHijriYear": self._official[0][0],
            "startMonth": self._official[0][1],
            "startDate": self._official[0][2].isoformat(),
            "officialLengths": [length for _, _, length in official],
            "estimatedLengths": estimated,
            "officialThrough": {"hijriYear": self.last_official[0], "month": self.last_official[1]},
            "tabularDriftDays": self._drift,
        }

    @classmethod
    def from_catalog_fields(cls, payload: dict) -> "LunarAnchorTable":
        start = date.fromisoformat(payload["startDate"])
        year = int(payload["startHijriYear"])
        month = int(payload.get("startMonth", 1))
        lengths = list(payload["officialLengths"])
        by_year: dict[int, list[int]] = {}
        cursor_year, cursor_month = year, month
        for index, length in enumerate(lengths):
            by_year.setdefault(cursor_year, [])
            by_year[cursor_year].append(length)
            cursor_year, cursor_month = _next_month(cursor_year, cursor_month)
        table = cls(by_year if by_year else HIJRI_OFFICIAL_LENGTHS)
        if table._official[0][2] != start:
            raise ValueError("Anchor start date does not match the recorded lengths")
        return table


def _next_month(year: int, month: int) -> tuple[int, int]:
    return (year + 1, 1) if month == 12 else (year, month + 1)


def _previous_month(year: int, month: int) -> tuple[int, int]:
    return (year - 1, 12) if month == 1 else (year, month - 1)


def _estimate_hijri_year_month(value: date, drift_days: int) -> tuple[int, int]:
    shifted = value - timedelta(days=drift_days)
    approximate = int((shifted.toordinal() - date(622, 7, 19).toordinal()) / 354.367) + 1
    for year in (approximate - 1, approximate, approximate + 1):
        for month in range(1, 13):
            if tabular_hijri_to_gregorian(year, month, 1) > shifted:
                return _previous_month(year, month)
            if tabular_hijri_to_gregorian(*_next_month(year, month), 1) > shifted:
                return year, month
    return approximate, 1


# ---------------------------------------------------------------------------
# Tabular (arithmetic) Hijri - estimation only
# ---------------------------------------------------------------------------


def tabular_hijri_to_jdn(year: int, month: int, day: int) -> int:
    """Arithmetic (Kuwaiti) Hijri to Julian day number.

    This is the same civil calendar Android's ``islamic-civil`` ICU calendar uses,
    which is exactly why it cannot be treated as the official Iranian calendar.
    """
    return (
        day
        + (59 * (month - 1) + 1) // 2
        + 354 * (year - 1)
        + (3 + 11 * year) // 30
        + 1948439
    )


def jdn_to_gregorian(jdn: int) -> date:
    return date.fromordinal(jdn - 1721425)


def gregorian_to_jdn(value: date) -> int:
    return value.toordinal() + 1721425


def tabular_hijri_to_gregorian(year: int, month: int, day: int) -> date:
    return jdn_to_gregorian(tabular_hijri_to_jdn(year, month, day))


def gregorian_to_tabular_hijri(value: date) -> tuple[int, int, int]:
    jdn = gregorian_to_jdn(value)
    year = (30 * (jdn - 1948440) + 10646) // 10631
    month = min(12, ((jdn - 29 - tabular_hijri_to_jdn(year, 1, 1)) * 1000 + 1000) // 29500 // 1000 + 1)
    day = jdn - tabular_hijri_to_jdn(year, month, 1) + 1
    return year, month, day


LUNAR = LunarAnchorTable()
