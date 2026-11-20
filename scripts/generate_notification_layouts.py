#!/usr/bin/env python3
"""Generate the sixteen SystemUI notification layouts from one look table.

The ongoing "today" notification is drawn by SystemUI from a ``RemoteViews`` tree, and it can be
shown in one of four *looks* the user picks in Settings. The layouts are **horizontal rows**: the
card is a stack of rows, and every row is a horizontal pairing — the date and the state share one
row, a task title and its meta share one row, the occasions sit under one hairline. Nothing is
piled vertically that can sit side by side, so the card reads as a tidy list of rows instead of a
tower of stacked lines.

The four looks
--------------

* **classic** — the Material card. A day badge leads the header, the state is a tonal pill, and
  the task rows sit on one tonal panel. The most "notification-like" of the four.
* **minimal** — text only. No badge, no pill, no panel: the state is a coloured dot and a word,
  the task title is the largest text on the card, and the occasions hang under a hairline.
* **editorial** — the poster. The Jalali date is set large, a hairline closes the masthead, and
  the task rows are a pull-quote with a vertical accent rule at their start.
* **banner** — the band. A full-width strip in the theme accent carries the date (and the state),
  the task rows read on the plain plate below it.

Rules every layout obeys
------------------------

* **Rows, not towers.** The header is one row (date … state), each task is one row
  (title … meta), and the occasion label and value share one row. A line that can
  sit beside another line does not get a line of its own.
* **Only first-class RemoteViews actions.** Every element the builder recolours is an
  ``ImageView`` painted with ``setImageViewResource``/``setImageViewBitmap`` or a ``TextView``
  painted with ``setTextColor``. No ``android:background`` tinting, no ``setInt(...)``.
* **No stretched corners.** A bitmap with rounded corners cannot be ``fitXY``-stretched to an
  unknown size without turning its corners into ellipses. Shapes that must scale (the card plate,
  the task panel, the state pill, the hairlines) are therefore *shape drawables* with literal
  colours, one per lightness, and the builder picks the light or dark twin. Bitmaps are only used
  where stretching is harmless: the banner band (drawn at screen width and at its exact dp
  height) and the editorial rule (a plain rectangle).
* **The plate ignores the device's night mode.** The card follows the *app* theme, so the
  colours are literals in drawables rather than ``@color`` references that would re-resolve
  against the system configuration.
* **A direction pair is a byte-for-byte copy** apart from ``rtl``/``ltr``; RemoteViews cannot
  mirror a layout by itself and the app language is not the host locale.

Run with ``--check`` to compare the files on disk with the table without writing anything.
"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT_DIR = ROOT / "app/src/main/res/layout"

# --- design tokens -------------------------------------------------------------------------

# Fallback text colours only: the builder states every colour at run time from the palette.
TITLE = "@color/notification_card_text"
MUTED = "@color/notification_card_muted"
ACCENT = "@color/notification_card_accent"
OVERDUE = "@color/notification_state_overdue"
OCCASION = "@color/notification_state_occasion"

PLATE_LIGHT = "@drawable/notification_card_plate_light"
PANEL_LIGHT = "@drawable/notification_panel_plate_light"
HAIRLINE_LIGHT = "@drawable/notification_hairline_light"
CHIP_NEUTRAL_LIGHT = "@drawable/notification_chip_neutral_light"
RULE_FALLBACK = "@drawable/notification_rule_fallback"

FONT = "@font/vazirmatn_variable"

# The type scale, in sp. The task title is the largest text on every look except editorial, where
# the date is the poster and the task is the body. Nothing is set below 11 sp: Persian in
# Vazirmatn has a low x-height and the old 10 sp labels were unreadable in the shade.
TASK_TITLE = (14, 16)        # compact, expanded
TASK_META = 11
CHIP = 11
SECTION_LABEL = 11
SECOND_TASK = 13
OCCASIONS = 12
SECONDARY_DATES = 11


@dataclass(frozen=True)
class Look:
    key: str
    comment: str
    badge: bool
    chip_pill: bool
    task_panel: bool
    accent_rule: bool
    band: bool
    header_divider: bool
    title_sp: tuple[int, int]
    badge_dp: tuple[int, int] = (20, 36)


LOOKS = {
    "classic": Look(
        key="classic",
        comment="Classic: a day badge, a tonal state pill and the task rows on one tonal panel.",
        badge=True, chip_pill=True, task_panel=True, accent_rule=False, band=False, header_divider=False,
        title_sp=(12, 15),
    ),
    "minimal": Look(
        key="minimal",
        comment="Minimal: text only. A dot and a word for the state, no badge, no panel.",
        badge=False, chip_pill=False, task_panel=False, accent_rule=False, band=False, header_divider=False,
        title_sp=(12, 14),
    ),
    "editorial": Look(
        key="editorial",
        comment="Editorial: a large date masthead over a hairline, the task rows as a pull-quote with a rule.",
        badge=False, chip_pill=False, task_panel=False, accent_rule=True, band=False, header_divider=True,
        title_sp=(13, 22),
    ),
    "banner": Look(
        key="banner",
        comment="Banner: a full-width accent band carries the date and the state; the task rows read below.",
        badge=False, chip_pill=False, task_panel=False, accent_rule=False, band=True, header_divider=False,
        title_sp=(12, 15),
    ),
}

DIRECTION = "rtl"


def _indent(block: str, spaces: int) -> str:
    pad = " " * spaces
    return "\n".join(pad + line if line else line for line in block.splitlines())


def _attrs(attrs: list[str]) -> str:
    return "\n".join(f"    {attr}" for attr in attrs)


def text(
    view_id: str,
    *,
    size: int,
    color: str,
    bold: bool = False,
    max_lines: int = 1,
    width: str = "match_parent",
    weight: int | None = None,
    max_width: int | None = None,
    margin_top: int | None = None,
    margin_start: int | None = None,
    margin_end: int | None = None,
) -> str:
    attrs = [f'android:id="@+id/{view_id}"', f'android:layout_width="{width}"', 'android:layout_height="wrap_content"']
    if weight is not None:
        attrs.append(f'android:layout_weight="{weight}"')
    if max_width is not None:
        attrs.append(f'android:maxWidth="{max_width}dp"')
    if margin_top is not None:
        attrs.append(f'android:layout_marginTop="{margin_top}dp"')
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    if margin_end is not None:
        attrs.append(f'android:layout_marginEnd="{margin_end}dp"')
    attrs += [
        'android:ellipsize="end"',
        f'android:fontFamily="{FONT}"',
        'android:includeFontPadding="false"',
        f'android:maxLines="{max_lines}"',
        'android:textAlignment="viewStart"',
        f'android:textColor="{color}"',
        f'android:textDirection="{DIRECTION}"',
        f'android:textSize="{size}sp"',
    ]
    if bold:
        attrs.append('android:textStyle="bold"')
    return f"<TextView\n{_attrs(attrs)} />"


def image(
    view_id: str,
    *,
    width: str = "wrap_content",
    height: str = "wrap_content",
    size: int | None = None,
    src: str | None = None,
    margin_top: int | None = None,
    margin_start: int | None = None,
    margin_end: int | None = None,
    tint: str | None = None,
    weight: int | None = None,
    scale: str = "fitXY",
) -> str:
    attrs = [f'android:id="@+id/{view_id}"']
    if weight is not None:
        attrs += ['android:layout_width="0dp"', f'android:layout_height="{height}"', f'android:layout_weight="{weight}"']
    elif size is not None:
        attrs += [f'android:layout_width="{size}dp"', f'android:layout_height="{size}dp"']
    else:
        attrs += [f'android:layout_width="{width}"', f'android:layout_height="{height}"']
    if margin_top is not None:
        attrs.append(f'android:layout_marginTop="{margin_top}dp"')
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    if margin_end is not None:
        attrs.append(f'android:layout_marginEnd="{margin_end}dp"')
    attrs.append('android:importantForAccessibility="no"')
    attrs.append(f'android:scaleType="{scale}"')
    if src is not None:
        attrs.append(f'android:src="{src}"')
    if tint is not None:
        # `app:tint`, not `android:tint`: lint's UseAppTint rejects the framework attribute when
        # AppCompat is on the classpath; RemoteViews inflates with the app's inflater, so both
        # resolve identically. The tint is only the fallback for the pre-tinted bitmap.
        attrs.append(f'app:tint="{tint}"')
    return f"<ImageView\n{_attrs(attrs)} />"


def linear(
    children: list[str],
    *,
    orientation: str,
    view_id: str | None = None,
    width: str = "match_parent",
    height: str = "wrap_content",
    weight: int | None = None,
    gravity: str | None = None,
    padding: tuple[int, int] | tuple[int, int, int] | None = None,
    margin_top: int | None = None,
    margin_start: int | None = None,
    margin_end: int | None = None,
) -> str:
    attrs = []
    if view_id is not None:
        attrs.append(f'android:id="@+id/{view_id}"')
    attrs += [f'android:layout_width="{width}"', f'android:layout_height="{height}"']
    if weight is not None:
        attrs.append(f'android:layout_weight="{weight}"')
    if margin_top is not None:
        attrs.append(f'android:layout_marginTop="{margin_top}dp"')
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    if margin_end is not None:
        attrs.append(f'android:layout_marginEnd="{margin_end}dp"')
    attrs.append(f'android:layoutDirection="{DIRECTION}"')
    if gravity is not None:
        attrs.append(f'android:gravity="{gravity}"')
    attrs.append(f'android:orientation="{orientation}"')
    if padding is not None:
        horizontal, top, bottom = padding if len(padding) == 3 else (padding[0], padding[1], padding[1])
        attrs += [
            f'android:paddingStart="{horizontal}dp"',
            f'android:paddingEnd="{horizontal}dp"',
            f'android:paddingTop="{top}dp"',
            f'android:paddingBottom="{bottom}dp"',
        ]
    return "\n".join(["<LinearLayout", _attrs(attrs) + ">", *(_indent(c, 4) for c in children), "</LinearLayout>"])


def frame(children: list[str], *, view_id: str, width: str = "match_parent", margin_top: int | None = None,
          margin_start: int | None = None) -> str:
    attrs = [f'android:id="@+id/{view_id}"', f'android:layout_width="{width}"', 'android:layout_height="wrap_content"']
    if margin_top is not None:
        attrs.append(f'android:layout_marginTop="{margin_top}dp"')
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    attrs.append(f'android:layoutDirection="{DIRECTION}"')
    return "\n".join(["<FrameLayout", _attrs(attrs) + ">", *(_indent(c, 4) for c in children), "</FrameLayout>"])


# --- card anatomy --------------------------------------------------------------------------

def card_plate() -> str:
    """The opaque plate behind the whole card, first in the tree so everything reads on it."""
    return image("notification_card_plate", width="match_parent", height="match_parent", src=PLATE_LIGHT)


def state_chip(look: Look, expanded: bool) -> str:
    """The state: "۳ عقب‌افتاده" / "امروز" as a tonal pill (classic) or a dot and a word.

    A FrameLayout so the pill can be an ImageView under the word; a background colour on a
    TextView is not a first-class RemoteViews action. The pill is a *shape drawable*, picked per
    state and lightness by the builder, so it scales to any word without distorting its corners.
    """
    if look.chip_pill:
        plate = image("notification_state_chip_plate", width="match_parent", height="match_parent", src=CHIP_NEUTRAL_LIGHT)
        icon = image("notification_state_icon", size=12, src="@drawable/ic_notification_warning", tint=OVERDUE)
        label = text("notification_state_label", size=CHIP, color=OVERDUE, bold=True, width="wrap_content",
                     margin_start=4, max_width=112 if expanded else 88)
        row = linear([icon, label], orientation="horizontal", width="wrap_content", gravity="start|center_vertical",
                     padding=(8, 3 if expanded else 2))
        return frame([plate, row], view_id="notification_state_container", width="wrap_content", margin_start=8)
    icon = image("notification_state_icon", size=7, src="@drawable/ic_notification_state_dot", tint=OVERDUE)
    label = text("notification_state_label", size=CHIP, color=OVERDUE, bold=True, width="wrap_content",
                 margin_start=5, max_width=112 if expanded else 88)
    row = linear([icon, label], orientation="horizontal", width="wrap_content", gravity="start|center_vertical")
    return frame([row], view_id="notification_state_container", width="wrap_content", margin_start=8)


def band_plate() -> str:
    """The banner's accent strip, bled to the card's edge, in three slices.

    A single rounded bitmap cannot be stretched to an unknown width and height without its
    corners turning into ellipses. The band is therefore a start cap, a flat fill and an end cap:
    the caps are 20 dp wide (exactly their bitmap width) and drawn with ``scaleType="matrix"``,
    which never scales — the bitmap is painted at its own size from the top and clipped at the
    bottom, so the card-radius corner stays a true circle at any band height. The fill between
    them is a plain colour, which ``fitXY`` cannot distort.
    """
    start = image("notification_band_start", width="20dp", height="match_parent", scale="matrix")
    fill = image("notification_band_fill", height="match_parent", weight=1)
    end = image("notification_band_end", width="20dp", height="match_parent", scale="matrix")
    return linear([start, fill, end], orientation="horizontal", height="match_parent")


def title(look: Look, expanded: bool, *, paired: bool = True) -> str:
    """The Jalali date, weighted so it owns the row it shares with the state."""
    return text("notification_title", size=look.title_sp[1 if expanded else 0], color=TITLE, bold=True,
                width="0dp" if paired else "match_parent", weight=1 if paired else None)


def secondary_dates() -> str:
    return text("notification_secondary_dates", size=SECONDARY_DATES, color=MUTED, max_lines=2, margin_top=2)


def header(look: Look, expanded: bool) -> str:
    """One horizontal row: the date on the start, the state on the end.

    The badge (classic) leads the row; the expanded card may carry the Gregorian/lunar
    cross-check *under* the date, still inside the row, so the date and the state stay on one
    line instead of the state dropping to a row of its own.
    """
    if look.band:
        # The band: a full-width accent strip. The plate is a bitmap drawn at screen width and
        # at the band's exact dp height, so nothing is stretched out of proportion.
        inner = linear(
            [title(look, expanded), state_chip(look, expanded)],
            orientation="horizontal", gravity="start|center_vertical", padding=(14 if expanded else 12, 9 if expanded else 0),
        )
        return frame([band_plate(), inner], view_id="notification_header")

    leading = [image("notification_badge", size=look.badge_dp[1 if expanded else 0], margin_end=10 if expanded else 6)] if look.badge else []
    if expanded:
        column = linear([title(look, expanded, paired=False), secondary_dates()], orientation="vertical", width="0dp", weight=1)
        row = linear(leading + [column, state_chip(look, expanded)], orientation="horizontal", gravity="start|center_vertical")
    else:
        row = linear(leading + [title(look, expanded), state_chip(look, expanded)], orientation="horizontal",
                     gravity="start|center_vertical")
    if not look.header_divider:
        return row
    divider = image("notification_header_divider", width="match_parent", height="1dp", margin_top=8 if expanded else 0,
                    src=HAIRLINE_LIGHT)
    return linear([row, divider], orientation="vertical", view_id="notification_header")


def paired_task_row(
    title_id: str,
    meta_id: str,
    *,
    title_size: int,
    title_lines: int,
    margin_top: int | None,
    title_color: str = TITLE,
    expanded: bool = True,
    view_id: str | None = None,
) -> str:
    """One horizontal row: the task title on the start, its meta on the end.

    This is the row that makes the card horizontal: the title keeps most of the width (it wraps
    to three lines expanded) and the due time / priority / category sit at the end,
    vertically centred, instead of costing a line of their own under the title.
    """
    title_view = text(title_id, size=title_size, color=title_color, bold=True, max_lines=title_lines,
                      width="0dp", weight=3)
    # Reserve the title's share even when metadata contains a long category or an overdue date.
    # Unbounded wrap_content here can measure the title down to zero on a narrow phone.
    meta_view = text(meta_id, size=TASK_META, color=MUTED, max_lines=2 if expanded else 1,
                     width="0dp", weight=2, margin_start=8)
    return linear([title_view, meta_view], orientation="horizontal", gravity="center_vertical",
                  margin_top=margin_top, view_id=view_id)


def task_rows(expanded: bool) -> str:
    """Priority 1: the tasks, as horizontal rows inside their container.

    The collapsed card has two single-line rows within SystemUI's 48dp custom-content budget.
    Expanding gives the task three lines, a second task and an overflow count. Trying to show all
    of that while collapsed simply lets SystemUI clip the task off the bottom.
    """
    parts: list[str] = []
    if expanded:
        parts.append(text("notification_tasks_label", size=SECTION_LABEL, color=ACCENT, bold=True))
    parts.append(paired_task_row(
        "notification_task_title", "notification_task_meta",
        title_size=TASK_TITLE[1 if expanded else 0], title_lines=3 if expanded else 1,
        margin_top=3 if expanded else None, expanded=expanded,
    ))
    if expanded:
        parts.append(paired_task_row(
            "notification_task_second_title", "notification_task_second_meta",
            title_size=SECOND_TASK, title_lines=2, margin_top=8, title_color=TITLE,
            view_id="notification_task_second_row",
        ))
        parts.append(text("notification_task_more", size=TASK_META, color=MUTED, margin_top=6))
    return linear(parts, orientation="vertical", view_id="notification_tasks_container")


def task_block(look: Look, expanded: bool) -> str:
    gap = 10 if expanded else 2
    rows = task_rows(expanded)
    if look.task_panel:
        plate = image("notification_task_panel", width="match_parent", height="match_parent", src=PANEL_LIGHT)
        padded = linear([rows], orientation="vertical", padding=(12, 8) if expanded else (4, 0))
        return frame([plate, padded], view_id="notification_next_container", margin_top=gap)
    if look.accent_rule:
        rule = image("notification_accent_rule", width="3dp", height="match_parent", margin_end=12, src=RULE_FALLBACK)
        column = linear([rows], orientation="vertical", width="0dp", weight=1)
        return linear([rule, column], orientation="horizontal", view_id="notification_next_container", margin_top=gap)
    return linear([rows], orientation="vertical", view_id="notification_next_container", margin_top=gap)


def occasion_section() -> str:
    """Priority 2: one horizontal labelled row under a hairline; hidden on an empty day."""
    divider = image("notification_occasions_divider", width="match_parent", height="1dp", margin_top=10, src=HAIRLINE_LIGHT)
    icon = image("notification_occasions_icon", size=13, src="@drawable/ic_notification_occasion_star", margin_end=6,
                 tint=OCCASION)
    label = text("notification_occasions_label", size=SECTION_LABEL, color=OCCASION, bold=True,
                 width="wrap_content", max_width=100)
    body = text("notification_occasions_text", size=OCCASIONS, color=MUTED, max_lines=2,
                width="0dp", weight=1, margin_start=8)
    row = linear([icon, label, body], orientation="horizontal", gravity="start|center_vertical", margin_top=8)
    return linear([divider, row], orientation="vertical", view_id="notification_occasions_container")


def card_root(content: str) -> str:
    return "\n".join(
        [
            '<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"',
            '    xmlns:app="http://schemas.android.com/apk/res-auto"',
            '    android:id="@+id/notification_root"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}">',
            _indent(card_plate(), 4),
            _indent(content, 4),
            "</FrameLayout>",
        ]
    )


def compact(look: Look) -> str:
    """The heads-up card: one header row, then one task row — the shortest card that still says
    the date, the state and the next task."""
    if look.band:
        # The band bleeds to the card's edge horizontally and sits under a small top inset; the
        # body keeps the normal side padding.
        body = linear([task_block(look, False)], orientation="vertical", padding=(12, 0, 2))
        content = linear([header(look, False), body], orientation="vertical", padding=(0, 2, 0))
    else:
        content = linear([header(look, False), task_block(look, False)], orientation="vertical", padding=(12, 2))
    return file_header(look, "compact") + card_root(content) + "\n"


def expanded(look: Look) -> str:
    if look.band:
        # The band is the header row; everything else reads on the plate below it.
        body = linear([secondary_dates(), task_block(look, True), occasion_section()], orientation="vertical",
                      padding=(16, 0, 14))
        content = linear([header(look, True), body], orientation="vertical", padding=(0, 0, 0))
    else:
        body = linear([task_block(look, True), occasion_section()], orientation="vertical")
        content = linear([header(look, True), body], orientation="vertical", padding=(16, 14))
    return file_header(look, "expanded") + card_root(content) + "\n"


def file_header(look: Look, size: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {look.comment}\n"
        f"     {size.capitalize()} card. Generated by scripts/generate_notification_layouts.py; edit the table there. -->\n"
    )


def build() -> dict[str, str]:
    global DIRECTION
    files: dict[str, str] = {}
    for look in LOOKS.values():
        for size, render in (("compact", compact), ("expanded", expanded)):
            for direction in ("rtl", "ltr"):
                DIRECTION = direction
                files[f"notification_{look.key}_{size}_{direction}.xml"] = render(look)
    DIRECTION = "rtl"
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail instead of writing when a file differs")
    args = parser.parse_args()
    files = build()
    stale = []
    for name, content in files.items():
        path = LAYOUT_DIR / name
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current != content:
            stale.append(name)
            if not args.check:
                path.write_text(content, encoding="utf-8")
    if args.check and stale:
        print("These layouts are out of date: " + ", ".join(sorted(stale)))
        return 1
    print(f"{len(files)} notification layouts " + ("are current." if args.check else "written."))
    return 0


if __name__ == "__main__":
    sys.exit(main())
