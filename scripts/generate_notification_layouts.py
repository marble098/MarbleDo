#!/usr/bin/env python3
"""Generate the sixteen SystemUI notification layouts from one geometry table.

The card used to mix two different things — the tasks a person has to act on and the occasions
that are merely interesting — into one stack of rows, and then shrank the most important line
(the task title) to a single ellipsised row next to a 44 dp badge, a mood pill and a progress
bar. This generator writes the redesigned card:

* **two sections, in priority order.** The task section comes first and is always visible; the
  occasion section is second, quieter, under a hairline, and collapses to nothing when the day
  has no occasion.
* **the task title gets two lines**, never one: `maxLines="2"` in the compact card and four in
  the expanded one, so a sentence that fits is read whole.
* **one date in the compact card.** It is the Jalali date the app is built around
  ("۲۸ آبان ۱۴۰۵"); the Gregorian and lunar cross-checks only appear expanded.
* **the day badge lives in the header and is small.** A 28–34 dp plate buys the text the width
  the old 38–48 dp badge was eating.
* **no progress bar.** A "۰ از ۱۰۱ عقب‌افتاده" track said nothing the state chip does not say
  better ("۳ عقب‌افتاده" / "امروز"), so the chip replaced it.

**Which card the shade shows.** Android limits a *collapsed* custom view to 48 dp (and a message
from Google is explicit that "collapsed view layouts are limited to as little as 48 dp"). A
48 dp box cannot hold a two-line title *and* a date, so the collapsed row is the platform's own
template — title, one text line and the large date badge — while the compact card generated here
is used as the **heads-up** view (88 dp of budget) and the expanded card is the big content view.
That is the "RemoteViews only where it is needed" half of the redesign: the row the shade draws
itself keeps the platform's touch feedback, and the two surfaces that have room get the design.

The direction pair is kept (``RemoteViews`` cannot mirror a layout by itself and the app language
is not the host locale), but a pair is a byte-for-byte copy apart from the ``rtl``/``ltr`` value,
and every look is emitted from the same template.

Run with ``--check`` to compare the files on disk with the table without writing anything.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT_DIR = ROOT / "app/src/main/res/layout"

# --- design tokens -------------------------------------------------------------------------

TITLE = "@color/notification_card_text"
MUTED = "@color/notification_card_muted"
ACCENT = "@color/notification_card_accent"
OVERDUE = "@color/notification_state_overdue"
OCCASION = "@color/notification_state_occasion"
# The plate the card paints behind itself. The builder swaps in the dark twin when the *app* theme
# is dark, so a pinned dark theme keeps a dark card inside a light shade (and vice versa) and every
# text colour on it stays readable.
PLATE_LIGHT = "@drawable/notification_card_plate_light"
# The pill behind the state word. Neutral here; the builder repaints it with a first-class bitmap
# call in the semantic colour of the moment (overdue red, brand accent, occasion purple).
CHIP = "@drawable/notification_state_chip"
RULE = "@drawable/notification_banner_bar"

TITLE_STYLE = "@style/TextAppearance.Compat.Notification.Title"
INFO_STYLE = "@style/TextAppearance.Compat.Notification.Info"
BODY_STYLE = "@style/TextAppearance.Compat.Notification"

# Every remote-view text uses the app's own Persian face. The font resource is merged into the APK
# from :core/designsystem, so a layout reference resolves for SystemUI as well.
FONT = "@font/vazirmatn_variable"

# design -> badge sizes, title sizes and the one filled surface each look is allowed.
DESIGNS = {
    "classic": dict(badge=28, badge_expanded=34, title=12, title_expanded=16,
                    focus="@drawable/notification_focus_background", rule=False, body=12),
    "minimal": dict(badge=28, badge_expanded=34, title=13, title_expanded=15,
                    focus="@android:color/transparent", rule=False, body=12),
    "editorial": dict(badge=30, badge_expanded=38, title=13, title_expanded=18,
                      focus="@drawable/notification_focus_background_editorial", rule=False, body=12),
    "banner": dict(badge=28, badge_expanded=34, title=13, title_expanded=16,
                   focus="@drawable/notification_focus_background_banner", rule=True, body=12),
}


def _indent(block: str, spaces: int) -> str:
    pad = " " * spaces
    return "\n".join(pad + line if line else line for line in block.splitlines())


def text(
    view_id: str,
    *,
    size: int,
    color: str,
    bold: bool = False,
    max_lines: int = 1,
    style: str = BODY_STYLE,
    width: str = "match_parent",
    weight: int | None = None,
    margin_top: int | None = None,
    margin_start: int | None = None,
    margin_end: int | None = None,
    gravity: str = "start",
    line_spacing: int | None = None,
) -> str:
    attrs = [f'android:id="@+id/{view_id}"']
    if weight is not None:
        attrs += [f'android:layout_width="{width}"', 'android:layout_height="wrap_content"',
                  f'android:layout_weight="{weight}"']
    else:
        attrs += [f'android:layout_width="{width}"', 'android:layout_height="wrap_content"']
    if margin_top is not None:
        attrs.append(f'android:layout_marginTop="{margin_top}dp"')
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    if margin_end is not None:
        attrs.append(f'android:layout_marginEnd="{margin_end}dp"')
    attrs += [
        'android:ellipsize="end"',
        f'android:fontFamily="{font_family()}"',
        f'android:gravity="{gravity}"',
    ]
    if line_spacing is not None:
        attrs.append(f'android:lineSpacingExtra="{line_spacing}dp"')
    attrs += [
        f'android:maxLines="{max_lines}"',
        "android:textAlignment=\"viewStart\"",
        f'android:textColor="{color}"',
        f'android:textDirection="{DIRECTION}"',
        f'android:textSize="{size}sp"',
    ]
    if bold:
        attrs.append('android:textStyle="bold"')
    body = "\n".join(f"    {attr}" for attr in attrs)
    return f"<TextView\n{body} />"


def image(
    view_id: str,
    *,
    width: str = "wrap_content",
    height: str = "wrap_content",
    size: int | None = None,
    src: str | None = None,
    scale: str = "fitXY",
    margin_top: int | None = None,
    margin_start: int | None = None,
    margin_end: int | None = None,
    tint: str | None = None,
    description: str | None = None,
    weight: int | None = None,
) -> str:
    attrs = [f'android:id="@+id/{view_id}"']
    if weight is not None:
        attrs += ['android:layout_width="0dp"', f'android:layout_height="{height}"',
                  f'android:layout_weight="{weight}"']
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
    if description is not None:
        attrs.append(f'android:contentDescription="{description}"')
    else:
        attrs.append('android:importantForAccessibility="no"')
    attrs.append(f'android:scaleType="{scale}"')
    if src is not None:
        attrs.append(f'android:src="{src}"')
    if tint is not None:
        # `app:tint`, not `android:tint`: with AppCompat on the classpath lint's UseAppTint check
        # rejects the framework attribute. RemoteViews is inflated by the app's own LayoutInflater,
        # so the support attribute resolves exactly like the framework one on every API level.
        attrs.append(f'app:tint="{tint}"')
    body = "\n".join(f"    {attr}" for attr in attrs)
    return f"<ImageView\n{body} />"


def font_family() -> str:
    return FONT


def badge(size: int, *, margin_end: int = 10, margin_start: int | None = None) -> str:
    return image("notification_badge", size=size, margin_start=margin_start, margin_end=margin_end)


def card_plate() -> str:
    """The opaque plate behind the whole card.

    A RemoteViews layout is drawn inside SystemUI's shade, which follows the wallpaper or the
    device theme rather than the app theme. Without a plate the card's text sat on whatever the
    shade happened to show, so the same row could be low-contrast in one theme and fine in the
    other — the "some parts never change colours" report. The plate is an ImageView and is painted
    by a first-class ``setImageViewResource`` (never a reflective ``setInt``).
    """
    return image("notification_card_plate", width="match_parent", height="match_parent", src=PLATE_LIGHT)


def accent_rule(height: int) -> str:
    """The banner's mood rule. An ImageView, because RemoteViews may not inflate a bare View."""
    return image("notification_accent_rule", width="3dp", height=f"{height}dp", src=RULE)


def state_chip(*, margin_top: int | None = None) -> str:
    """The pill behind the state word — the replacement for the progress bar.

    A FrameLayout so the pill can be its own ImageView (repainted by the builder in the semantic
    colour) with the word on top; a background colour on a TextView is not a first-class
    RemoteViews action.
    """
    chip_plate = image("notification_state_chip_plate", width="match_parent", height="match_parent", src=CHIP)
    # The mark ships white and is repainted in the chip's ink by the builder. The static tint is
    # the fallback for the path where the tinted bitmap could not be drawn: it is the state's own
    # hue, which clears AA on the pale pill in the light theme (and is what values-night lifts).
    icon = image(
        "notification_state_icon",
        size=12,
        src="@drawable/ic_notification_warning",
        margin_start=7,
        tint=OVERDUE,
    )
    label = text(
        "notification_state_label",
        size=10,
        color=OVERDUE,
        bold=True,
        style=INFO_STYLE,
        width="wrap_content",
        max_lines=1,
        margin_start=4,
        margin_end=7,
    )
    return "\n".join(
        [
            "<FrameLayout",
            '    android:id="@+id/notification_state_container"',
            '    android:layout_width="wrap_content"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            *( [f'    android:layout_marginTop="{margin_top}dp"'] if margin_top is not None else [] ),
            '    android:importantForAccessibility="no">',
            _indent(chip_plate, 4),
            _indent(
                "\n".join(
                    [
                        '<LinearLayout',
                        '    android:layout_width="wrap_content"',
                        '    android:layout_height="wrap_content"',
                        f'    android:layoutDirection="{DIRECTION}"',
                        '    android:gravity="start|center_vertical"',
                        '    android:orientation="horizontal"',
                        '    android:paddingTop="2dp"',
                        '    android:paddingBottom="2dp">',
                        _indent(icon, 4),
                        _indent(label, 4),
                        '</LinearLayout>',
                    ]
                ),
                4,
            ),
            "</FrameLayout>",
        ]
    )


def header_column(expanded: bool, title_sp: int) -> str:
    """Jalali date over the state chip, and — when expanded — the two secondary calendars.

    In the compact card the header is a plain block in the vertical column
    (``match_parent``); in the expanded card it shares a horizontal header row with the badge,
    so there it takes the remaining width (``0dp`` + weight).
    """
    title = text(
        "notification_title",
        size=title_sp,
        color=TITLE,
        bold=True,
        style=TITLE_STYLE,
        width="match_parent",
        max_lines=1,
    )
    chip = state_chip(margin_top=3)
    if not expanded:
        return "\n".join(
            [
                '<LinearLayout',
                '    android:layout_width="match_parent"',
                '    android:layout_height="wrap_content"',
                f'    android:layoutDirection="{DIRECTION}"',
                '    android:gravity="start"',
                '    android:orientation="vertical">',
                _indent(title, 4),
                _indent(chip, 4),
                '</LinearLayout>',
            ]
        )

    secondary = text(
        "notification_secondary_dates",
        size=10,
        color=MUTED,
        style=INFO_STYLE,
        margin_top=2,
        max_lines=2,
    )
    return "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="0dp"',
            '    android:layout_height="wrap_content"',
            '    android:layout_weight="1"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start"',
            '    android:orientation="vertical">',
            _indent(title, 4),
            _indent(secondary, 4),
            _indent(chip, 4),
            '</LinearLayout>',
        ]
    )


def task_section(expanded: bool) -> str:
    """Priority 1: the tasks themselves. Two lines compact, four expanded, never clipped to one."""
    parts = []
    if expanded:
        parts.append(
            text(
                "notification_tasks_label",
                size=10,
                color=ACCENT,
                bold=True,
                style=INFO_STYLE,
                max_lines=1,
                margin_top=10,
            )
        )
    parts.append(
        text(
            "notification_task_title",
            size=14 if not expanded else 15,
            color=TITLE,
            bold=True,
            style=TITLE_STYLE,
            max_lines=2 if not expanded else 4,
            margin_top=6 if not expanded else 4,
            line_spacing=1,
        )
    )
    parts.append(
        text(
            "notification_task_meta",
            size=10,
            color=MUTED,
            style=INFO_STYLE,
            max_lines=1,
            margin_top=2,
        )
    )
    if expanded:
        parts.append(
            text(
                "notification_task_second_title",
                size=12,
                color=TITLE,
                style=BODY_STYLE,
                max_lines=2,
                margin_top=8,
            )
        )
        parts.append(
            text(
                "notification_task_second_meta",
                size=10,
                color=MUTED,
                style=INFO_STYLE,
                max_lines=1,
                margin_top=1,
            )
        )
        parts.append(
            text(
                "notification_task_more",
                size=10,
                color=MUTED,
                style=INFO_STYLE,
                max_lines=1,
                margin_top=4,
            )
        )
    return "\n".join(
        [
            '<LinearLayout',
            '    android:id="@+id/notification_tasks_container"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start"',
            '    android:orientation="vertical">',
            *[_indent(part, 4) for part in parts],
            '</LinearLayout>',
        ]
    )


def occasion_section() -> str:
    """Priority 2: today's occasions, below a hairline, with a star and their own heading.

    Hidden entirely when the day has nothing to show — the labelled section for a line that said
    "nothing" was half of the clutter the old card carried.
    """
    divider = image(
        "notification_occasions_divider",
        width="match_parent",
        height="1dp",
        margin_top=10,
        src=RULE,
    )
    icon = image(
        "notification_occasions_icon",
        size=13,
        src="@drawable/ic_notification_occasion_star",
        margin_end=5,
        tint=OCCASION,
    )
    label = text(
        "notification_occasions_label",
        size=10,
        color=OCCASION,
        bold=True,
        style=INFO_STYLE,
        width="0dp",
        weight=1,
        max_lines=1,
    )
    header_row = "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start|center_vertical"',
            '    android:orientation="horizontal"',
            '    android:layout_marginTop="5dp">',
            _indent(icon, 4),
            _indent(label, 4),
            '</LinearLayout>',
        ]
    )
    body = text(
        "notification_occasions_text",
        size=11,
        color=MUTED,
        max_lines=3,
        margin_top=2,
    )
    return "\n".join(
        [
            '<LinearLayout',
            '    android:id="@+id/notification_occasions_container"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start"',
            '    android:orientation="vertical">',
            _indent(divider, 4),
            _indent(header_row, 4),
            _indent(body, 4),
            '</LinearLayout>',
        ]
    )


def compact(design: str) -> str:
    """The heads-up card: 88 dp of budget, so the date, the chip and two lines of task fit."""
    spec = DESIGNS[design]
    leading = []
    if spec["rule"]:
        leading.append(accent_rule(30))
    leading.append(badge(spec["badge"], margin_start=8 if spec["rule"] else None))

    column = "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="0dp"',
            '    android:layout_height="wrap_content"',
            '    android:layout_weight="1"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start"',
            '    android:orientation="vertical">',
            _indent(header_column(False, spec["title"]), 4),
            _indent(task_section(False), 4),
            '</LinearLayout>',
        ]
    )
    inner = "\n".join(_indent(child, 4) for child in leading + [column])
    content = "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start|center_vertical"',
            '    android:orientation="horizontal"',
            '    android:paddingStart="10dp"',
            '    android:paddingEnd="10dp"',
            '    android:paddingTop="6dp"',
            '    android:paddingBottom="6dp">',
            inner,
            '</LinearLayout>',
        ]
    )
    return header(design, "compact") + card_root(content) + "\n"


def expanded(design: str) -> str:
    spec = DESIGNS[design]
    leading = []
    if spec["rule"]:
        leading.append(accent_rule(44))
    leading.append(badge(spec["badge_expanded"], margin_start=10 if spec["rule"] else None))
    header_row = "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start|center_vertical"',
            '    android:orientation="horizontal">',
            _indent("\n".join(leading), 4),
            _indent(header_column(True, spec["title_expanded"]), 4),
            '</LinearLayout>',
        ]
    )

    next_card = "\n".join(
        [
            '<FrameLayout',
            '    android:id="@+id/notification_next_container"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            f'    android:background="{spec["focus"]}"',
            '>',
            _indent(image("notification_focus_plate", width="match_parent", height="match_parent"), 4),
            _indent(
                "\n".join(
                    [
                        '<LinearLayout',
                        '    android:layout_width="match_parent"',
                        '    android:layout_height="wrap_content"',
                        f'    android:layoutDirection="{DIRECTION}"',
                        '    android:orientation="vertical"',
                        '    android:paddingStart="12dp"',
                        '    android:paddingTop="6dp"',
                        '    android:paddingEnd="12dp"',
                        '    android:paddingBottom="8dp">',
                        _indent(task_section(True), 4),
                        '</LinearLayout>',
                    ]
                ),
                4,
            ),
            '</FrameLayout>',
        ]
    )

    inner = "\n".join(
        _indent(block, 4) for block in (header_row, next_card, occasion_section())
    )
    content = "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start"',
            '    android:orientation="vertical"',
            '    android:paddingStart="14dp"',
            '    android:paddingEnd="14dp"',
            '    android:paddingTop="8dp"',
            '    android:paddingBottom="8dp">',
            inner,
            '</LinearLayout>',
        ]
    )
    return header(design, "expanded") + card_root(content) + "\n"


def card_root(content: str) -> str:
    """A FrameLayout root whose first child is the card plate.

    ``RemoteViews`` has no first-class way to set a view *background* colour: the only API is the
    reflective ``setInt(id, "setBackgroundColor", colour)``, which a locked-down SystemUI may
    reject and which takes the whole row — status-bar icon included — off the screen. A plate is an
    ``ImageView`` instead, painted with ``setImageViewResource``, so the card can follow the app
    theme's lightness with an ordinary first-class action.
    """
    return "\n".join(
        [
            '<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"',
            '    xmlns:app="http://schemas.android.com/apk/res-auto"',
            '    android:id="@+id/notification_root"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            # The root keeps the explicit direction as well as the content row: SystemUI walks the
            # whole tree for the direction, and a root without one lets the shade decide.
            f'    android:layoutDirection="{DIRECTION}">',
            _indent(card_plate(), 4),
            _indent(content, 4),
            '</FrameLayout>',
        ]
    )


def header(design: str, size: str) -> str:
    titles = {
        ("classic", "compact"): "Classic, heads-up: Jalali date, state chip, two lines of task.",
        ("classic", "expanded"): "Classic, expanded: the tasks, then the occasions under a hairline.",
        ("minimal", "compact"): "Minimal, heads-up: text only, no badge and no filled surface.",
        ("minimal", "expanded"): "Minimal, expanded: one column, no card and no rule.",
        ("editorial", "compact"): "Editorial, heads-up: a large date and a tight title.",
        ("editorial", "expanded"): "Editorial, expanded: poster scale over the task list.",
        ("banner", "compact"): "Banner, heads-up: a mood rule leads the band.",
        ("banner", "expanded"): "Banner, expanded: the mood rule runs the height of the header.",
    }
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {titles[(design, size)]}\n"
        "     Generated by scripts/generate_notification_layouts.py; edit the table there. -->\n"
    )


DIRECTION = "rtl"


def build() -> dict[str, str]:
    global DIRECTION
    files: dict[str, str] = {}
    for design in DESIGNS:
        for size, render in (("compact", compact), ("expanded", expanded)):
            for direction in ("rtl", "ltr"):
                DIRECTION = direction
                files[f"notification_{design}_{size}_{direction}.xml"] = render(design)
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
