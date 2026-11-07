"""Adapters that turn a source payload into normalised :class:`RawEvent` records.

Each function is pure: it receives bytes/text plus the Jalali year of interest and
returns records. Network access happens only in :mod:`fetcher`, so every adapter is
unit-tested against small fixtures recorded from the real sources.

Adapters deliberately keep the *source's own* notion of a date. A source that ships a
solar date computed with the tabular Hijri calendar is kept as reported; the merge
step re-derives lunar dates from the official anchor table instead of believing the
tabulated conversion. That is the whole point: several popular datasets place
"3 Jumada al-Thani" on 23 Aban because of that conversion error.
"""
from __future__ import annotations

import io
import json
import re
import tarfile
from datetime import date, timedelta

from .jalali import hijri_month_name_index, jdn_to_gregorian, jalali_to_gregorian
from .model import GREGORIAN, JALALI, LUNAR, PERSIAN_MONTHS, RawEvent, normalise, to_ascii_digits

HIJRI_LABEL = re.compile(r"[\[\]\u005b]?\s*([0-9]{1,2})\s+([^\d\W][^\]]{2,25}?)\s*[\]\u005d]?\s*$")
CLOSURE_WORDS = ("تعطیل", "تعطيل")


def _holiday_from_text(text: str) -> bool:
    return any(word in text for word in CLOSURE_WORDS)


def _clean_title(text: str) -> str:
    """Strip the «تعطیل» marker and stray quotes so the title is an occasion, not a flag."""
    cleaned = text
    for marker in ("«تعطیل»", "«تعطيل»", "تعطیل رسمی", "تعطيل رسمي", "تعطیل", "تعطيل"):
        cleaned = cleaned.replace(marker, " ")
    return cleaned.replace("«", " ").replace("»", " ").strip(" ‏-–—").strip()


def parse_iso(value: str) -> date | None:
    try:
        return date.fromisoformat(to_ascii_digits(value).strip().replace("/", "-"))
    except ValueError:
        return None


def parse_jalali_digits(value: str) -> date | None:
    """Parse ``1405/01/01`` or ``1405-01-01`` into a Gregorian date."""
    parts = to_ascii_digits(value).replace("-", "/").split("/")
    if len(parts) != 3 or not all(part.strip().isdigit() for part in parts):
        return None
    year, month, day = (int(part) for part in parts)
    if not 1200 <= year <= 1700:
        return None
    try:
        return jalali_to_gregorian(year, month, day)
    except ValueError:
        return None


def parse_hijri_label(text: str) -> tuple[int, int] | None:
    """Read a lunar month/day from a title such as ``...[ ٣ جمادي الثانيه ]``."""
    match = HIJRI_LABEL.search(normalise(text))
    if not match:
        return None
    day = int(match.group(1))
    month = hijri_month_name_index(match.group(2))
    if month is None or not 1 <= day <= 30:
        return None
    return month, day


# ---------------------------------------------------------------------------
# Open-source JSON adapters
# ---------------------------------------------------------------------------


def pc_events_json(payload: str, source_id: str, tier: str) -> list[RawEvent]:
    """persian-calendar/events: calendar-keyed events, Iran entries only."""
    root = json.loads(payload)
    events = root.get("data", root if isinstance(root, list) else [])
    mapping = {"Persian": JALALI, "Hijri": LUNAR, "Gregorian": GREGORIAN}
    records: list[RawEvent] = []
    for item in events:
        if item.get("type") not in ("Iran", "International"):
            continue
        calendar = mapping.get(item.get("calendar", ""))
        month, day = item.get("month"), item.get("day")
        if calendar is None or not isinstance(month, int) or not isinstance(day, int):
            continue
        title = (item.get("title") or "").strip()
        if not title:
            continue
        record = RawEvent(
            source_id=source_id,
            tier=tier if item.get("type") == "Iran" else "open-source",
            title=title,
            calendar=calendar,
            month=month,
            day=day,
            holiday=bool(item.get("holiday")) and item.get("type") == "Iran",
            note=str(item.get("rule") or ""),
        )
        # Lunar rows stay keyed by Hijri month/day: the merge resolves them with
        # the official table instead of trusting a tabular conversion.
        records.append(record)
    return records


def iran_holidays_json(payload: str, source_id: str, tier: str) -> list[RawEvent]:
    """iyazdanicharati/IranHollidaysJSON: one row per day of a Jalali year."""
    root = json.loads(payload)
    rows = root.get("data", root if isinstance(root, list) else [])
    records: list[RawEvent] = []
    for row in rows:
        solar = parse_jalali_digits(str(row.get("date", "")))
        description = (row.get("holidayDesription") or row.get("holidayDescription") or "").strip()
        if solar is None or not description:
            continue
        records.append(
            RawEvent(
                source_id=source_id,
                tier=tier,
                title=description,
                calendar=JALALI,
                holiday=bool(row.get("isHoliday")),
                solar=solar,
            )
        )
    return records


def shamsi_holidays_json(payload: str, source_id: str, tier: str) -> list[RawEvent]:
    """hasan-ahani/shamsi-holidays: list of days, each with its events."""
    rows = json.loads(payload)
    records: list[RawEvent] = []
    for row in rows if isinstance(rows, list) else []:
        solar = parse_jalali_digits(str(row.get("date", "")))
        if solar is None:
            continue
        for event in row.get("events", []):
            title = (event.get("description") or "").strip()
            if not title:
                continue
            records.append(
                RawEvent(
                    source_id=source_id,
                    tier=tier,
                    title=title,
                    calendar=JALALI,
                    holiday=bool(event.get("is_holiday")),
                    solar=solar,
                )
            )
    return records


def jalali_calendar_year_json(payload: str, source_id: str, tier: str) -> list[RawEvent]:
    """alirezadesh/jalali_calendar: per-day data with a lunar label field."""
    rows = json.loads(payload)
    records: list[RawEvent] = []
    for row in rows if isinstance(rows, list) else []:
        solar = parse_jalali_digits(str(row.get("date", "")))
        if solar is None:
            continue
        for event in row.get("events", []):
            title = (event.get("description") or "").strip()
            if not title:
                continue
            label = (event.get("additional_description") or "").strip()
            records.append(
                RawEvent(
                    source_id=source_id,
                    tier=tier,
                    title=title,
                    calendar=JALALI,
                    holiday=bool(event.get("is_holiday")),
                    solar=solar,
                    note=label,
                )
            )
    return records


_PHOLIDAY_ROW = re.compile(
    r"\{\s*'fday'\s*:\s*'(\d{1,2})/(\d{1,2})'\s*,\s*'title'\s*:\s*'([^']+)'\s*,\s*'holiday'\s*:\s*(true|false)",
    re.UNICODE,
)


def pholiday_lunar_js(payload: str, source_id: str, tier: str) -> list[RawEvent]:
    """pholiday/events/lunar.js: ``{'fday':'6/3', 'title': ..., 'holiday': ...}``."""
    records: list[RawEvent] = []
    for month, day, title, holiday in _PHOLIDAY_ROW.findall(payload):
        records.append(
            RawEvent(
                source_id=source_id,
                tier=tier,
                title=title.strip(),
                calendar=LUNAR,
                month=int(month),
                day=int(day),
                holiday=holiday == "true",
            )
        )
    return records


# ---------------------------------------------------------------------------
# Official lunar month lengths
# ---------------------------------------------------------------------------

_OFFICIAL_TABLE = re.compile(
    r"OFFICIAL_HIJRI_START_JDN\s*=\s*(\d+);(.*?)\];",
    re.DOTALL,
)
_YEAR_ROW = re.compile(r"\[\s*(\d{3,4})\s*,\s*\[([0-9,\s]+)\]\]")


def npm_official_hijri_months(tarball: bytes, source_id: str, tier: str) -> dict:
    """Extract the official lunar month-length table from the iran-holidays package."""
    with tarfile.open(fileobj=io.BytesIO(tarball), mode="r:gz") as archive:
        member = next((m for m in archive.getmembers() if m.name.endswith("official-months.js")), None)
        if member is None:
            raise ValueError("official-months.js is missing from the package")
        text = archive.extractfile(member).read().decode("utf-8")
    table = _OFFICIAL_TABLE.search(text)
    if not table:
        raise ValueError("official month table not found")
    lengths: dict[int, list[int]] = {}
    for year, values in _YEAR_ROW.findall(table.group(2)):
        months = [int(value) for value in values.split(",") if value.strip()]
        if not 1 <= len(months) <= 12 or any(month not in (29, 30) for month in months):
            raise ValueError(f"Invalid month lengths for {year}")
        lengths[int(year)] = months
    if not lengths:
        raise ValueError("official month table is empty")
    start = jdn_to_gregorian(int(table.group(1)))
    return {
        "startJdn": int(table.group(1)),
        "startDate": start,
        "lengths": lengths,
        "sourceId": source_id,
        "tier": tier,
    }


# ---------------------------------------------------------------------------
# Persian calendar page text (official and unofficial publishers)
# ---------------------------------------------------------------------------

_YEAR_HEADING = re.compile(r"مناسبت\s*های\s+(\S+)\s+(\d{4})")
_CLOSURE_HEADING = re.compile(r"مناسبت\s*های\s*تعطیل\s+(\S+)\s*(?::|：)")
_LINE = re.compile(r"^([0-9]{1,2})\s+(\S+)\s*[-–—]\s*(.+)$")
_CLOSURE_ITEM = re.compile(r"([0-9]{1,2})\s*[-–—]\s*([^-–—]+)")


def persian_calendar_text(payload: str, source_id: str, tier: str) -> list[RawEvent]:
    """Extract ``<day> <month> - <title>`` rows from a Persian calendar page.

    Two shapes are supported because Iranian sites use both: month sections
    (``مناسبت‌های آبان ۱۴۰۵``) and an explicit closure summary
    (``مناسبت‌های تعطیل آبان : ۲۲ - شهادت ...``). Anything without a month is
    ignored rather than guessed.
    """
    month = 0
    year = 0
    records: list[RawEvent] = []
    for raw_line in payload.splitlines():
        # Persian publishers join words with ZWNJ and sometimes escape the dash; both
        # would defeat the row patterns, so they are folded before matching.
        line = " ".join(to_ascii_digits(raw_line.replace("\u200c", " ").replace("\\", "")).split())
        if not line:
            continue
        closure_heading = _CLOSURE_HEADING.search(line)
        if closure_heading:
            month = PERSIAN_MONTHS.get(closure_heading.group(1), 0)
            if not month:
                continue
            remainder = line[closure_heading.end():]
            for day_text, title in _CLOSURE_ITEM.findall(to_ascii_digits(remainder)):
                title = title.strip(" ‏-–—")
                if title and 1 <= int(day_text) <= 31:
                    records.append(
                        RawEvent(
                            source_id=source_id,
                            tier=tier,
                            title=title,
                            calendar=JALALI,
                            month=month,
                            day=int(day_text),
                            holiday=True,
                            solar=_solar(year, month, int(day_text)),
                        )
                    )
            continue
        heading = _YEAR_HEADING.search(line)
        if heading:
            candidate = PERSIAN_MONTHS.get(heading.group(1))
            if candidate:
                month = candidate
                year = int(to_ascii_digits(heading.group(2)))
            # Publishers put the first row of the month on the heading line itself
            # ("مناسبت‌های آبان ۱۴۰۵ ۲ آبان - ..."), so keep the remainder.
            line = line[heading.end():].strip()
            if not line:
                continue
        match = _LINE.match(line)
        if not match or not month:
            continue
        day = int(to_ascii_digits(match.group(1)))
        if not 1 <= day <= 31:
            continue
        solar = _solar(year, month, day)
        # One day usually lists several occasions separated by a dash; only the
        # item that says «تعطیل» is a closure. Treating the whole line as one title
        # would mark every neighbour of a holiday as a holiday too.
        for item in re.split(r"\s+[-–—]\s+", match.group(3)):
            title = _clean_title(item)
            if not title:
                continue
            records.append(
                RawEvent(
                    source_id=source_id,
                    tier=tier,
                    title=title,
                    calendar=JALALI,
                    month=month,
                    day=day,
                    holiday=_holiday_from_text(item),
                    solar=solar,
                )
            )
    return records


def _solar(year: int, month: int, day: int) -> date | None:
    if 1200 <= year <= 1700 and 1 <= month <= 12 and 1 <= day <= 31:
        try:
            return jalali_to_gregorian(year, month, day)
        except ValueError:
            return None
    return None


ADAPTERS = {
    "pc-events-json": pc_events_json,
    "iran-holidays-json": iran_holidays_json,
    "shamsi-holidays-json": shamsi_holidays_json,
    "jalali-calendar-year-json": jalali_calendar_year_json,
    "pholiday-lunar-js": pholiday_lunar_js,
    "persian-calendar-text": persian_calendar_text,
}
