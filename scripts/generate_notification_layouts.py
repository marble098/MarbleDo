#!/usr/bin/env python3
"""Generate the sixteen SystemUI notification layouts from one geometry table.

The notification card used to keep four hand-written design families, each with a compact and
an expanded layout, each duplicated for RTL and LTR: sixteen files that drifted apart. The
redesign keeps the direction pair (``RemoteViews`` cannot mirror a layout by itself and the app
language is not the host locale), but a pair is now a byte-for-byte copy apart from the
``rtl``/``ltr`` value, and every look is emitted from the same template.

The rules that the redesign encodes, and that ``scripts/tests/test_notification_designs.py``
re-checks:

* one idea per row — a date, a mood word, one next step, one progress reading, one occasion
  line — instead of stacked labels for each;
* a single filled surface (the next-step card), so the eye has one place to land;
* explicit text colours, because a custom ``RemoteViews`` layout inherits SystemUI's colour
  and would otherwise be invisible in one of the two themes;
* only widgets ``RemoteViews`` documents, because one unsupported class takes the whole row -
  and the status-bar icon with it - off the screen.

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
TRACK = "@color/notification_card_track"

TITLE_STYLE = "@style/TextAppearance.Compat.Notification.Title"
INFO_STYLE = "@style/TextAppearance.Compat.Notification.Info"
BODY_STYLE = "@style/TextAppearance.Compat.Notification"

# design -> (compact badge dp, expanded badge dp, compact title sp, expanded title sp,
#            next-card background, corner style, leading mood rule)
DESIGNS = {
    "classic": dict(badge=38, badge_expanded=44, title=14, title_expanded=17,
                    focus="@drawable/notification_focus_background", rule=False),
    "minimal": dict(badge=38, badge_expanded=44, title=15, title_expanded=16,
                    focus="@android:color/transparent", rule=False),
    "editorial": dict(badge=40, badge_expanded=48, title=16, title_expanded=19,
                      focus="@drawable/notification_focus_background_editorial", rule=False),
    "banner": dict(badge=38, badge_expanded=44, title=15, title_expanded=17,
                   focus="@drawable/notification_focus_background_banner", rule=True),
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
) -> str:
    attrs = [
        f'android:id="@+id/{view_id}"',
        f'style="{style}"',
        f'android:layout_width="{width}"',
        'android:layout_height="wrap_content"',
    ]
    if weight is not None:
        attrs.append(f'android:layout_weight="{weight}"')
    if margin_top is not None:
        attrs.append(f'android:layout_marginTop="{margin_top}dp"')
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    if margin_end is not None:
        attrs.append(f'android:layout_marginEnd="{margin_end}dp"')
    attrs += [
        'android:ellipsize="end"',
        'android:gravity="start"',
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


def badge(size: int, *, margin_end: int = 10, margin_start: int | None = None) -> str:
    attrs = [
        'android:id="@+id/notification_badge"',
        f'android:layout_width="{size}dp"',
        f'android:layout_height="{size}dp"',
    ]
    if margin_start is not None:
        attrs.append(f'android:layout_marginStart="{margin_start}dp"')
    attrs += [
        f'android:layout_marginEnd="{margin_end}dp"',
        'android:importantForAccessibility="no"',
    ]
    body = "\n".join(f"    {attr}" for attr in attrs)
    return f"<ImageView\n{body} />"


def accent_rule(height: int) -> str:
    """The banner's mood rule. An ImageView, because RemoteViews may not inflate a bare View."""
    attrs = [
        'android:id="@+id/notification_accent_rule"',
        'android:layout_width="3dp"',
        f'android:layout_height="{height}dp"',
        'android:importantForAccessibility="no"',
        'android:scaleType="fitXY"',
        'android:src="@drawable/notification_banner_bar"',
    ]
    body = "\n".join(f"    {attr}" for attr in attrs)
    return f"<ImageView\n{body} />"


def header_column(expanded: bool, title_sp: int) -> str:
    """Date title, mood word and — when expanded — the two secondary calendars."""
    title = text(
        "notification_title",
        size=title_sp,
        color=TITLE,
        bold=True,
        style=TITLE_STYLE,
        width="0dp",
        weight=1,
    )
    mood = text(
        "notification_mood_pill",
        size=9,
        color=ACCENT,
        bold=True,
        style=INFO_STYLE,
        width="wrap_content",
        margin_start=6,
    )
    title_row = "\n".join(
        [
            '<LinearLayout',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start|center_vertical"',
            '    android:orientation="horizontal">',
            _indent(title, 4),
            _indent(mood, 4),
            '</LinearLayout>',
        ]
    )

    if not expanded:
        return title_row

    secondary = text(
        "notification_secondary_dates",
        size=10,
        color=MUTED,
        style=INFO_STYLE,
        margin_top=1,
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
            _indent(title_row, 4),
            _indent(secondary, 4),
            '</LinearLayout>',
        ]
    )


def compact(design: str) -> str:
    spec = DESIGNS[design]
    leading = []
    if spec["rule"]:
        leading.append(accent_rule(30))
    leading.append(badge(spec["badge"], margin_start=10 if spec["rule"] else None))

    summary = text(
        "notification_compact_summary",
        size=12,
        color=MUTED,
        max_lines=1,
        margin_top=1,
    )
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
            _indent(summary, 4),
            '</LinearLayout>',
        ]
    )
    children = leading + [column]
    inner = "\n".join(_indent(child, 4) for child in children)
    root = "\n".join(
        [
            '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"',
            '    android:id="@+id/notification_root"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start|center_vertical"',
            '    android:orientation="horizontal"',
            '    android:paddingTop="2dp"',
            '    android:paddingBottom="2dp">',
            inner,
            '</LinearLayout>',
        ]
    )
    return header(design, "compact") + root + "\n"


def expanded(design: str) -> str:
    spec = DESIGNS[design]
    leading = []
    if spec["rule"]:
        leading.append(accent_rule(48))
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

    progress_label = text(
        "notification_progress_label",
        size=10,
        color=MUTED,
        style=INFO_STYLE,
        width="wrap_content",
    )
    progress_bar = "\n".join(
        [
            "<ImageView",
            '    android:id="@+id/notification_progress"',
            '    android:layout_width="0dp"',
            '    android:layout_height="3dp"',
            '    android:layout_weight="1"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:layout_marginStart="10dp"',
            '    android:importantForAccessibility="no"',
            '    android:scaleType="fitXY" />',
        ]
    )
    progress = "\n".join(
        [
            '<LinearLayout',
            '    android:id="@+id/notification_progress_container"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:layout_marginTop="8dp"',
            '    android:gravity="start|center_vertical"',
            '    android:orientation="horizontal">',
            _indent(progress_label, 4),
            _indent(progress_bar, 4),
            '</LinearLayout>',
        ]
    )

    next_label = text(
        "notification_next_label",
        size=9,
        color=ACCENT,
        bold=True,
        style=INFO_STYLE,
        width="0dp",
        weight=1,
    )
    next_time = text(
        "notification_next_time",
        size=9,
        color=MUTED,
        style=INFO_STYLE,
        width="wrap_content",
        margin_start=6,
    )
    next_title = text(
        "notification_next_title",
        size=13,
        color=TITLE,
        bold=True,
        style=TITLE_STYLE,
        margin_top=1,
    )
    next_card = "\n".join(
        [
            '<FrameLayout',
            '    android:id="@+id/notification_next_container"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:layout_marginTop="8dp"',
            f'    android:background="{spec["focus"]}"',
            '    android:clipToOutline="true">',
            '    <ImageView',
            '        android:id="@+id/notification_focus_plate"',
            '        android:layout_width="match_parent"',
            '        android:layout_height="match_parent"',
            '        android:importantForAccessibility="no"',
            '        android:scaleType="fitXY" />',
            '    <LinearLayout',
            '        android:layout_width="match_parent"',
            '        android:layout_height="wrap_content"',
            f'        android:layoutDirection="{DIRECTION}"',
            '        android:orientation="vertical"',
            '        android:paddingStart="10dp"',
            '        android:paddingTop="8dp"',
            '        android:paddingEnd="10dp"',
            '        android:paddingBottom="8dp">',
            _indent(
                "\n".join(
                    [
                        '<LinearLayout',
                        '    android:layout_width="match_parent"',
                        '    android:layout_height="wrap_content"',
                        f'    android:layoutDirection="{DIRECTION}"',
                        '    android:gravity="start|center_vertical"',
                        '    android:orientation="horizontal">',
                        _indent(next_label, 4),
                        _indent(next_time, 4),
                        '</LinearLayout>',
                    ]
                ),
                8,
            ),
            _indent(next_title, 8),
            '    </LinearLayout>',
            '</FrameLayout>',
        ]
    )

    occasions_text = text(
        "notification_occasions_text",
        size=11,
        color=MUTED,
        max_lines=1,
    )
    occasions = "\n".join(
        [
            '<LinearLayout',
            '    android:id="@+id/notification_occasions_container"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:layout_marginTop="8dp"',
            '    android:gravity="start"',
            '    android:orientation="vertical">',
            _indent(occasions_text, 4),
            '</LinearLayout>',
        ]
    )

    inner = "\n".join(_indent(block, 4) for block in (header_row, progress, next_card, occasions))
    root = "\n".join(
        [
            '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"',
            '    android:id="@+id/notification_root"',
            '    android:layout_width="match_parent"',
            '    android:layout_height="wrap_content"',
            f'    android:layoutDirection="{DIRECTION}"',
            '    android:gravity="start"',
            '    android:orientation="vertical"',
            '    android:paddingTop="2dp"',
            '    android:paddingBottom="2dp">',
            inner,
            '</LinearLayout>',
        ]
    )
    return header(design, "expanded") + root + "\n"


def header(design: str, size: str) -> str:
    titles = {
        ("classic", "compact"): "Classic, compact: date, mood word and one next step.",
        ("classic", "expanded"): "Classic, expanded: the next step is the only filled surface.",
        ("minimal", "compact"): "Minimal, compact: text only, no badge and no filled surface.",
        ("minimal", "expanded"): "Minimal, expanded: one column, no card and no rule.",
        ("editorial", "compact"): "Editorial, compact: a large date and a tight title.",
        ("editorial", "expanded"): "Editorial, expanded: poster scale with a square next-step plate.",
        ("banner", "compact"): "Banner, compact: a mood rule leads the band.",
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
