"""Normalised record produced by every source adapter."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

JALALI = "jalali"
LUNAR = "lunar"
GREGORIAN = "gregorian"

PERSIAN_MONTHS = {
    "فروردین": 1, "اردیبهشت": 2, "خرداد": 3, "تیر": 4, "مرداد": 5, "شهریور": 6,
    "مهر": 7, "آبان": 8, "آذر": 9, "دی": 10, "بهمن": 11, "اسفند": 12,
}

# Persian/Arabic-Indic digits used by every Iranian publisher.
_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def to_ascii_digits(text: str) -> str:
    return text.translate(_DIGITS)


def normalise(text: str) -> str:
    """Normalise Persian/Arabic text so titles from different sources compare equal."""
    cleaned = text.replace("\u200c", " ").replace("\u200f", "").replace("\u200e", "")
    cleaned = cleaned.replace("ي", "ی").replace("ك", "ک").replace("ۀ", "ه").replace("ة", "ه")
    cleaned = cleaned.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ٱ", "ا")
    cleaned = cleaned.replace("ٔ", "").replace("ّ", "").replace("َ", "").replace("ُ", "")
    cleaned = cleaned.replace("ِ", "").replace("ْ", "").replace("ً", "").replace("ٌ", "")
    cleaned = cleaned.replace("«", " ").replace("»", " ").replace("(", " ").replace(")", " ")
    cleaned = cleaned.replace("ـ", "").replace("،", " ").replace("؛", " ")
    cleaned = to_ascii_digits(cleaned)
    return " ".join(cleaned.split())


def title_key(title: str) -> str:
    """Stable identity for cross-source matching (not for display)."""
    return normalise(title).casefold().replace(" ", "")


@dataclass(frozen=True)
class RawEvent:
    """One occasion as a single source reported it."""

    source_id: str
    tier: str
    title: str
    calendar: str
    month: int | None = None
    day: int | None = None
    holiday: bool = False
    solar: date | None = None
    hijri_year: int | None = None
    note: str = ""
    tags: tuple[str, ...] = field(default_factory=tuple)

    @property
    def hijri_month_day(self) -> tuple[int, int] | None:
        if self.calendar == LUNAR and self.month and self.day:
            return self.month, self.day
        return None


@dataclass(frozen=True)
class SourceStatus:
    source_id: str
    tier: str
    ok: bool
    events: int
    checked_at: date
    detail: str = ""
