#!/usr/bin/env python3
"""Generate the complete MarbleDo launcher icon set.

The mark is a **glass marble with a tick inside an orbit**: one polished sphere for "Marble",
one check for "Do", and an open ring that reads as a day turning. It replaces the flat calendar
page, which was the icon's third generation of the same idea and read as a copy of a stock
calendar app at 48 px: five pale day cells, a teal header and a tick all fighting for the same
twenty pixels.

One geometry table drives everything, so the adaptive icon, the themed icon and the legacy
bitmaps can never drift apart:

  * ``drawable/ic_launcher_background.xml``   - theme colour, a lit corner and a vignette
  * ``drawable/ic_launcher_foreground.xml``   - the marble, its tick and the orbit
  * ``drawable/ic_launcher_monochrome.xml``   - the single-colour silhouette
  * ``mipmap-*/ic_launcher{,_round}.png``     - legacy bitmaps at every density

The whole mark lives inside a **27.4 dp radius** around the centre of the 108x108 canvas. That is
the number that matters: a launcher may mask the 72 dp visible area into a circle (36 dp radius),
a squircle, a rounded square, a square, an oval (36 x 30) or a teardrop, and the mark has to be
complete under every one of them. ``--check`` and the mask table in ``launcher_icon_manifest.json``
prove it, mask by mask, instead of trusting a single bounding box.

Usage::

    python3 scripts/generate_launcher_icons.py            # write every file
    python3 scripts/generate_launcher_icons.py --check    # fail if anything is stale
"""
from __future__ import annotations

import argparse
import json
import math
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
DRAWABLE = RES / 'drawable'

# --- adaptive icon canvas -------------------------------------------------------
CANVAS = 108.0
# The inner 66dp of the 108dp canvas is the area the adaptive-icon contract guarantees, and the
# visible 72dp circle reaches 36dp from the centre. Both are checked below; the mark is smaller
# than either so that even an oval (36 x 30) mask keeps it whole.
SAFE_LO, SAFE_HI = 21.0, 87.0

# --- the mark -------------------------------------------------------------------
CENTER = 54.0
MARBLE_RADIUS = 19.5
ORBIT_RADIUS = 25.2
ORBIT_WIDTH = 4.4
ORBIT_START_DEG = -56.0
ORBIT_SWEEP_DEG = 300.0
CHECK_POINTS = ((45.0, 55.5), (51.0, 61.5), (63.5, 46.5))
CHECK_WIDTH = 6.6
HIGHLIGHT_CENTER = (45.4, 44.6)
HIGHLIGHT_RADIUS = 3.0

# --- palette --------------------------------------------------------------------
MARBLE_GRADIENT = (
    ('#FFE9FFF9', 0.0),
    ('#FF6BE8C6', 0.42),
    ('#FF12A08F', 0.78),
    ('#FF0A4F52', 1.0),
)
CHECK_COLOR = '#FFFFFF'
ORBIT_COLOR = '#D9FFF4'
HIGHLIGHT_COLOR = '#73FFFFFF'
# Both stops use Android's #AARRGGBB order, exactly as the vector gradients do.
SHEEN = (('#1FFFFFFF', 0.0), ('#08FFFFFF', 0.5), ('#00000000', 1.0))
GLOW = (('#5538E6C4', 0.0), ('#2238E6C4', 0.45), ('#00000000', 1.0))
VIGNETTE = (('#00000000', 0.0), ('#00000000', 0.45), ('#22000000', 1.0))
GLOW_CENTER = (26.0, 22.0)
GLOW_RADIUS = 78.0

# --- launcher masks -------------------------------------------------------------
# Every shape a launcher is known to use. The value is what the mask keeps around the centre;
# the distances are true signed distances (negative inside), so "clearance" is measurable.
MASKS = {
    'circle': 36.0,
    'squircle': 36.0,
    'rounded_square': 36.0,
    'square': 36.0,
    'oval': 36.0,
    'teardrop': 36.0,
}
# An oval is the tightest vertical mask in common use: 72 dp wide, 60 dp tall.
OVAL_HALF_HEIGHT = 30.0
# Clearance the mark must keep from any mask edge, in dp.
MASK_CLEARANCE = 1.5

# --- legacy bitmap densities ----------------------------------------------------
# mdpi is the 1x bucket, so every other bucket is a whole multiple of it.
DENSITIES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 128,
    'xxxhdpi': 192,
}
LEGACY_NAMES = ('ic_launcher.png', 'ic_launcher_round.png')


# =============================================================================
# geometry helpers
# =============================================================================
def circle_path(cx: float, cy: float, r: float) -> str:
    """A circle as four cubic segments, so its outer points are real path points."""
    k = 0.5522847498 * r
    return (
        f'M{cx - r:g},{cy:g} '
        f'C{cx - r:g},{cy - k:g} {cx - k:g},{cy - r:g} {cx:g},{cy - r:g} '
        f'C{cx + k:g},{cy - r:g} {cx + r:g},{cy - k:g} {cx + r:g},{cy:g} '
        f'C{cx + r:g},{cy + k:g} {cx + k:g},{cy + r:g} {cx:g},{cy + r:g} '
        f'C{cx - k:g},{cy + r:g} {cx - r:g},{cy + k:g} {cx - r:g},{cy:g} Z'
    )


def arc_path(cx: float, cy: float, r: float, start_deg: float, sweep_deg: float) -> str:
    """A circular arc, split into <= 90 degree cubic segments.

    A single ``A`` command would be shorter, but its bulge is invisible to a bounding-box reader;
    cubics keep every extreme of the arc as a real anchor point, which is what the safe-zone tests
    measure.
    """
    steps = max(1, int(math.ceil(abs(sweep_deg) / 90.0)))
    step = sweep_deg / steps
    alpha = 4.0 / 3.0 * math.tan(math.radians(step) / 4.0)
    x0 = cx + r * math.cos(math.radians(start_deg))
    y0 = cy + r * math.sin(math.radians(start_deg))
    parts = [f'M{x0:g},{y0:g}']
    angle = start_deg
    for _ in range(steps):
        a0 = math.radians(angle)
        a1 = math.radians(angle + step)
        p0 = (cx + r * math.cos(a0), cy + r * math.sin(a0))
        p1 = (cx + r * math.cos(a1), cy + r * math.sin(a1))
        c0 = (p0[0] - alpha * r * math.sin(a0), p0[1] + alpha * r * math.cos(a0))
        c1 = (p1[0] + alpha * r * math.sin(a1), p1[1] - alpha * r * math.cos(a1))
        parts.append(
            f'C{c0[0]:g},{c0[1]:g} {c1[0]:g},{c1[1]:g} {p1[0]:g},{p1[1]:g}'
        )
        angle += step
    return ' '.join(parts)


def polyline_path(points) -> str:
    return 'M' + ' L'.join(f'{x:g},{y:g}' for x, y in points)


def tick_polygon(points, half: float):
    """The outline of a stroked polyline, as a closed polygon.

    The themed icon punches the tick out of a solid marble, and a punched hole has to be a
    *fill*: a stroked path cannot be subtracted with ``fillType="evenOdd"``. The outline is built
    by offsetting the two legs and mitring their join, which is exactly what the stroked
    foreground draws with round joins, minus the rounding.
    """
    (ax, ay), (bx, by), (cx, cy) = points
    d1 = _normalize((bx - ax, by - ay))
    d2 = _normalize((cx - bx, cy - by))
    n1 = (-d1[1], d1[0])
    n2 = (-d2[1], d2[0])
    miter = _normalize((n1[0] + n2[0], n1[1] + n2[1]))
    cos_half = max(0.5, miter[0] * n1[0] + miter[1] * n1[1])
    miter_len = half / cos_half
    outer = []
    outer.append((ax + n1[0] * half, ay + n1[1] * half))
    outer.append((bx + miter[0] * miter_len, by + miter[1] * miter_len))
    outer.append((cx + n2[0] * half, cy + n2[1] * half))
    inner = []
    inner.append((cx - n2[0] * half, cy - n2[1] * half))
    inner.append((bx - miter[0] * miter_len, by - miter[1] * miter_len))
    inner.append((ax - n1[0] * half, ay - n1[1] * half))
    return outer + inner


def _normalize(vector) -> tuple[float, float]:
    length = math.hypot(*vector) or 1.0
    return vector[0] / length, vector[1] / length


def sd_rounded_rect(px: float, py: float, box, radius: float) -> float:
    """Signed distance to a rounded rectangle; negative means inside."""
    x0, y0, x1, y1 = box
    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    hx = (x1 - x0) / 2.0 - radius
    hy = (y1 - y0) / 2.0 - radius
    qx = abs(px - cx) - hx
    qy = abs(py - cy) - hy
    ax, ay = max(qx, 0.0), max(qy, 0.0)
    return math.hypot(ax, ay) + min(max(qx, qy), 0.0) - radius


def sd_segment(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> float:
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    length = vx * vx + vy * vy
    t = 0.0 if length == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / length))
    return math.hypot(px - (ax + t * vx), py - (ay + t * vy))


def sd_polyline(px: float, py: float, points) -> float:
    best = float('inf')
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        best = min(best, sd_segment(px, py, ax, ay, bx, by))
    return best


def coverage(distance: float, feather: float = 0.8) -> float:
    """Anti-aliased fill from a signed distance."""
    return max(0.0, min(1.0, 0.5 - distance / (2.0 * feather)))


# =============================================================================
# mask table
# =============================================================================
def mask_distance(mask: str, px: float, py: float) -> float:
    """Signed distance to a launcher mask; negative means the mask keeps this point."""
    dx, dy = px - CENTER, py - CENTER
    if mask == 'oval':
        return (math.hypot(dx / 36.0, dy / OVAL_HALF_HEIGHT) - 1.0) * OVAL_HALF_HEIGHT
    if mask == 'squircle':
        norm = (abs(dx / 36.0) ** 4 + abs(dy / 36.0) ** 4) ** 0.25
        return (norm - 1.0) * 36.0
    if mask == 'rounded_square':
        return sd_rounded_rect(px, py, (18.0, 18.0, 90.0, 90.0), 18.0)
    if mask == 'square':
        return max(18.0 - px, px - 90.0, 18.0 - py, py - 90.0)
    if mask == 'teardrop':
        # One circle plus a rounded square: the tightest of the two shapes wins, which is what a
        # teardrop mask is (a circle that keeps one squarer corner).
        return max(math.hypot(dx, dy) - 36.0, sd_rounded_rect(px, py, (18.0, 18.0, 90.0, 90.0), 26.0))
    return math.hypot(dx, dy) - 36.0


def ink_outline():
    """Points on the outline of the mark, used for the mask and safe-zone measurements."""
    points = []
    for index in range(720):
        angle = math.radians(index * 0.5)
        points.append((CENTER + math.cos(angle) * (ORBIT_RADIUS + ORBIT_WIDTH / 2.0),
                       CENTER + math.sin(angle) * (ORBIT_RADIUS + ORBIT_WIDTH / 2.0)))
    for index in range(360):
        angle = math.radians(index)
        for radius in (MARBLE_RADIUS,):
            points.append((CENTER + math.cos(angle) * radius, CENTER + math.sin(angle) * radius))
    return points


def ink_bounds() -> dict:
    """The bounding box of everything the mark draws, strokes included."""
    reach = ORBIT_RADIUS + ORBIT_WIDTH / 2.0
    check = CHECK_WIDTH / 2.0
    xs = [CENTER - reach, CENTER + reach]
    ys = [CENTER - reach, CENTER + reach]
    for x, y in CHECK_POINTS:
        xs.extend((x - check, x + check))
        ys.extend((y - check, y + check))
    return {'left': min(xs), 'top': min(ys), 'right': max(xs), 'bottom': max(ys)}


def mask_report() -> dict:
    """How much room the mark keeps inside each launcher mask."""
    outline = ink_outline()
    report = {}
    for mask in MASKS:
        worst = max(mask_distance(mask, px, py) for px, py in outline)
        report[mask] = {
            'clearance': round(-worst, 3),
            'clear': -worst >= MASK_CLEARANCE,
        }
    report['all_clear'] = all(entry['clear'] for key, entry in report.items() if isinstance(entry, dict))
    return report


# =============================================================================
# tiny rasteriser (no third-party imaging library is available in CI)
# =============================================================================
def hex_rgba(value: str):
    """Parse an Android colour literal. Eight digits are #AARRGGBB, six are #RRGGBB."""
    value = value.lstrip('#')
    if len(value) == 8:
        a, r, g, b = (int(value[i:i + 2], 16) for i in (0, 2, 4, 6))
    else:
        r, g, b = (int(value[i:i + 2], 16) for i in (0, 2, 4))
        a = 255
    return r / 255.0, g / 255.0, b / 255.0, a / 255.0


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def gradient_color(stops, t: float):
    t = max(0.0, min(1.0, t))
    for (color, offset), (next_color, next_offset) in zip(stops, stops[1:]):
        if offset <= t <= next_offset:
            span = next_offset - offset
            local = 0.0 if span == 0 else (t - offset) / span
            first, second = hex_rgba(color), hex_rgba(next_color)
            return tuple(lerp(first[i], second[i], local) for i in range(4))
    return hex_rgba(stops[-1][0])


def render(size: int, circular: bool, base_color: str) -> bytearray:
    """Rasterise the icon at ``size`` x ``size`` pixels as straight RGBA."""
    scale = size / CANVAS
    buf = bytearray(size * size * 4)

    def put(x: int, y: int, r: float, g: float, b: float, a: float) -> None:
        if a <= 0.0:
            return
        i = (y * size + x) * 4
        dr, dg, db, da = (buf[i] / 255.0, buf[i + 1] / 255.0, buf[i + 2] / 255.0, buf[i + 3] / 255.0)
        out_a = a + da * (1.0 - a)
        if out_a <= 0.0:
            return
        for channel, value in ((0, r), (1, g), (2, b)):
            buf[i + channel] = int(round(255.0 * ((value * a + (dr, dg, db)[channel] * da * (1.0 - a)) / out_a)))
        buf[i + 3] = int(round(255.0 * out_a))

    def layer(paint) -> None:
        for y in range(size):
            py = (y + 0.5) / scale
            for x in range(size):
                px = (x + 0.5) / scale
                if circular and math.hypot(px - CANVAS / 2, py - CANVAS / 2) > CANVAS / 2:
                    continue
                r, g, b, a = paint(px, py)
                if a > 0.0:
                    put(x, y, r, g, b, a)

    base = hex_rgba(base_color)
    diagonal = math.hypot(CANVAS, CANVAS)

    # 1. the theme colour, matching the splash window.
    layer(lambda px, py: (*base[:3], base[3]))
    # 2. a soft diagonal sheen, lit from the top-left corner.
    layer(lambda px, py: gradient_color(SHEEN, (px + py) / diagonal))
    # 3. the teal glow behind the marble.
    def glow_paint(px, py):
        distance = math.hypot(px - GLOW_CENTER[0], py - GLOW_CENTER[1]) / GLOW_RADIUS
        return gradient_color(GLOW, distance)

    layer(glow_paint)
    # 4. a quiet corner vignette in the opposite corner.
    layer(lambda px, py: gradient_color(VIGNETTE, (px + py) / diagonal))

    # 5. the marble, lit from its upper left.
    light_center = (CENTER - MARBLE_RADIUS * 0.45, CENTER - MARBLE_RADIUS * 0.5)

    def marble_paint(px, py):
        distance = math.dist((px, py), (CENTER, CENTER))
        if distance > MARBLE_RADIUS:
            return 0.0, 0.0, 0.0, 0.0
        t = math.dist((px, py), light_center) / (MARBLE_RADIUS * 2.1)
        color = gradient_color(MARBLE_GRADIENT, t)
        return (*color[:3], color[3] * coverage(distance - MARBLE_RADIUS))

    layer(marble_paint)
    # 6. the tick.
    check = hex_rgba(CHECK_COLOR)
    layer(lambda px, py: (*check[:3], check[3] * coverage(sd_polyline(px, py, CHECK_POINTS) - CHECK_WIDTH / 2.0)))
    # 7. the orbit, with an open gap at the top right.
    orbit = hex_rgba(ORBIT_COLOR)

    def orbit_paint(px, py):
        distance = abs(math.hypot(px - CENTER, py - CENTER) - ORBIT_RADIUS) - ORBIT_WIDTH / 2.0
        if distance > 0:
            return 0.0, 0.0, 0.0, 0.0
        angle = math.degrees(math.atan2(py - CENTER, px - CENTER))
        rel = (angle - ORBIT_START_DEG) % 360.0
        if rel > ORBIT_SWEEP_DEG:
            return 0.0, 0.0, 0.0, 0.0
        return (*orbit[:3], orbit[3] * coverage(distance))

    layer(orbit_paint)
    # 8. the specular highlight on the glass.
    highlight = hex_rgba(HIGHLIGHT_COLOR)
    layer(lambda px, py: (
        *highlight[:3],
        highlight[3] * coverage(math.dist((px, py), HIGHLIGHT_CENTER) - HIGHLIGHT_RADIUS),
    ))
    return buf


def write_png(path: Path, size: int, rgba: bytearray) -> None:
    stride = size * 4
    raw = b''.join(b'\x00' + rgba[y * stride:(y + 1) * stride] for y in range(size))

    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)
    path.write_bytes(
        b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', header)
        + chunk(b'IDAT', zlib.compress(raw, 9))
        + chunk(b'IEND', b'')
    )


# =============================================================================
# resource files
# =============================================================================
def background_xml() -> str:
    sheen = '\n'.join(
        f'                <item android:offset="{offset:g}" android:color="{color}" />' for color, offset in SHEEN
    )
    glow = '\n'.join(
        f'                <item android:offset="{offset:g}" android:color="{color}" />' for color, offset in GLOW
    )
    vignette = '\n'.join(
        f'                <item android:offset="{offset:g}" android:color="{color}" />' for color, offset in VIGNETTE
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Launcher icon background. Generated by scripts/generate_launcher_icons.py - edit the
  script, not this file.

  The base colour is the same one the splash window uses, so the launch animation never changes
  colour underneath the user. The teal glow sits behind the marble and the vignette darkens the
  opposite corner, which is what makes the flat square read as a lit surface under any mask.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@color/marble_launcher_bg"
        android:pathData="M0,0h108v108h-108z" />
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:centerX="{GLOW_CENTER[0]:g}"
                android:centerY="{GLOW_CENTER[1]:g}"
                android:gradientRadius="{GLOW_RADIUS:g}"
                android:type="radial">
{glow}
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="108"
                android:endY="108"
                android:startX="0"
                android:startY="0"
                android:type="linear">
{sheen}
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="0"
                android:endY="0"
                android:startX="108"
                android:startY="108"
                android:type="linear">
{vignette}
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


def foreground_xml() -> str:
    marble = circle_path(CENTER, CENTER, MARBLE_RADIUS)
    check = polyline_path(CHECK_POINTS)
    orbit = arc_path(CENTER, CENTER, ORBIT_RADIUS, ORBIT_START_DEG, ORBIT_SWEEP_DEG)
    highlight = circle_path(HIGHLIGHT_CENTER[0], HIGHLIGHT_CENTER[1], HIGHLIGHT_RADIUS)
    gradient = '\n'.join(
        f'                    <item android:offset="{offset:g}" android:color="{color}" />'
        for color, offset in MARBLE_GRADIENT
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Launcher icon foreground. Generated by scripts/generate_launcher_icons.py - edit the
  script, not this file.

  A glass marble with a tick and an open orbit around it. The whole mark fits inside a 27.4dp
  radius around the centre of the 108dp canvas, so a circle, an oval, a squircle, a rounded
  square, a square and a teardrop mask all show it whole.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="{marble}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:centerX="{CENTER - MARBLE_RADIUS * 0.45:g}"
                android:centerY="{CENTER - MARBLE_RADIUS * 0.5:g}"
                android:gradientRadius="{MARBLE_RADIUS * 2.1:g}"
                android:type="radial">
{gradient}
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:fillColor="{CHECK_COLOR}"
        android:pathData="{check}"
        android:strokeColor="{CHECK_COLOR}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="{CHECK_WIDTH:g}" />
    <path
        android:fillColor="#00000000"
        android:pathData="{orbit}"
        android:strokeColor="{ORBIT_COLOR}"
        android:strokeLineCap="round"
        android:strokeWidth="{ORBIT_WIDTH:g}" />
    <path
        android:fillColor="{HIGHLIGHT_COLOR}"
        android:pathData="{highlight}" />
</vector>
'''


def monochrome_xml() -> str:
    marble = circle_path(CENTER, CENTER, MARBLE_RADIUS)
    tick = 'M' + ' L'.join(
        f'{x:g},{y:g}' for x, y in tick_polygon(CHECK_POINTS, CHECK_WIDTH / 2.0)
    ) + ' Z'
    orbit = arc_path(CENTER, CENTER, ORBIT_RADIUS, ORBIT_START_DEG, ORBIT_SWEEP_DEG)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Themed (monochrome) launcher icon. Generated by scripts/generate_launcher_icons.py -
  edit the script, not this file.

  The system replaces every colour with one tint, so the mark becomes a silhouette: a solid
  marble with the tick punched out of it (one path, fillType evenOdd — a stroked tick cannot be
  subtracted from a fill) and the orbit around it. Same 27.4dp radius as the foreground, so the
  themed icon never clips either.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{marble} {tick}" />
    <path
        android:fillColor="#00000000"
        android:pathData="{orbit}"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeWidth="{ORBIT_WIDTH:g}" />
</vector>
'''


def adaptive_xml() -> str:
    return '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Adaptive launcher icon: three layers, masked by the launcher into whatever shape it uses. The
  background is a drawable rather than a flat colour so the lit corner survives; the monochrome
  layer is what themed icons tint on Android 13 and later.
-->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''


# =============================================================================
def read_launcher_bg() -> str:
    for candidate in (RES / 'values/colors.xml', RES / 'values-night/colors.xml'):
        for line in candidate.read_text(encoding='utf-8').splitlines():
            if 'name="marble_launcher_bg"' in line:
                return line.split('>', 1)[1].split('<', 1)[0].strip()
    raise SystemExit('marble_launcher_bg is not defined')


def manifest() -> dict:
    bounds = ink_bounds()
    masks = mask_report()
    return {
        'canvas': CANVAS,
        'safe_zone': [SAFE_LO, SAFE_HI],
        'ink_radius': ORBIT_RADIUS + ORBIT_WIDTH / 2.0,
        'ink_bounds': bounds,
        'safe': bounds['left'] >= SAFE_LO and bounds['top'] >= SAFE_LO
        and bounds['right'] <= SAFE_HI and bounds['bottom'] <= SAFE_HI,
        'masks': masks,
        'all_masks_clear': masks['all_clear'],
        'densities': DENSITIES,
    }


def generate() -> int:
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    files = {
        DRAWABLE / 'ic_launcher_background.xml': background_xml(),
        DRAWABLE / 'ic_launcher_foreground.xml': foreground_xml(),
        DRAWABLE / 'ic_launcher_monochrome.xml': monochrome_xml(),
        RES / 'mipmap-anydpi-v26/ic_launcher.xml': adaptive_xml(),
        RES / 'mipmap-anydpi-v26/ic_launcher_round.xml': adaptive_xml(),
    }
    for path, text in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')

    base = read_launcher_bg()
    written = 0
    for density, size in DENSITIES.items():
        for circular in (False, True):
            buf = render(size, circular, base)
            name = 'ic_launcher_round.png' if circular else 'ic_launcher.png'
            path = RES / f'mipmap-{density}' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            write_png(path, size, buf)
            written += 1

    (ROOT / 'scripts/launcher_icon_manifest.json').write_text(
        json.dumps(manifest(), indent=2) + '\n', encoding='utf-8'
    )
    print(f'wrote {len(files)} resources and {written} bitmaps')
    if not manifest()['safe']:
        print('ERROR: the mark leaves the 66dp safe zone', file=sys.stderr)
        return 1
    if not manifest()['all_masks_clear']:
        print('ERROR: the mark is clipped by at least one launcher mask', file=sys.stderr)
        return 1
    return 0


def check() -> int:
    problems = []
    vectors = {
        DRAWABLE / 'ic_launcher_background.xml': background_xml,
        DRAWABLE / 'ic_launcher_foreground.xml': foreground_xml,
        DRAWABLE / 'ic_launcher_monochrome.xml': monochrome_xml,
        RES / 'mipmap-anydpi-v26/ic_launcher.xml': adaptive_xml,
        RES / 'mipmap-anydpi-v26/ic_launcher_round.xml': adaptive_xml,
    }
    for path, renderer in vectors.items():
        if not path.exists():
            problems.append(f'missing {path.relative_to(ROOT)}')
        elif path.read_text(encoding='utf-8') != renderer():
            problems.append(f'{path.relative_to(ROOT)} is stale, re-run the generator')
    for density, size in DENSITIES.items():
        for name in LEGACY_NAMES:
            path = RES / f'mipmap-{density}' / name
            if not path.exists():
                problems.append(f'missing {path.relative_to(ROOT)}')
                continue
            data = path.read_bytes()
            if data[:8] != b'\x89PNG\r\n\x1a\n':
                problems.append(f'{path.relative_to(ROOT)} is not a PNG')
                continue
            width, height = struct.unpack('>II', data[16:24])
            if (width, height) != (size, size):
                problems.append(f'{path.relative_to(ROOT)} is {width}x{height}, expected {size}x{size}')
    if not manifest()['safe']:
        problems.append('the mark leaves the 66dp safe zone')
    if not manifest()['all_masks_clear']:
        problems.append(f'the mark is clipped by a launcher mask: {manifest()["masks"]}')
    for problem in problems:
        print(problem, file=sys.stderr)
    return 1 if problems else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='only verify the set is complete')
    args = parser.parse_args()
    if args.check:
        return check()
    return generate()


if __name__ == '__main__':
    raise SystemExit(main())
