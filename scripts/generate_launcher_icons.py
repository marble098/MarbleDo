#!/usr/bin/env python3
"""Generate the complete MarbleDo launcher icon set.

One geometry table drives three things, so the adaptive icon, the themed icon and the
legacy bitmaps can never drift apart:

  * ``drawable/ic_launcher_background.xml``   - theme colour plus a soft sheen
  * ``drawable/ic_launcher_foreground.xml``   - the coloured calendar mark
  * ``drawable/ic_launcher_monochrome.xml``   - the single-colour silhouette
  * ``mipmap-*/ic_launcher{,_round}.png``     - legacy bitmaps at every density

Everything is drawn inside the 66x66 safe zone of the 108x108 adaptive canvas, so every
launcher mask (circle, squircle, rounded square, square, teardrop) shows the whole mark.

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
# The inner 66dp of the 108dp canvas is the only area every launcher mask is guaranteed
# to keep, so all artwork (strokes included) has to stay inside it.
SAFE_LO, SAFE_HI = 21.0, 87.0

# --- the mark -------------------------------------------------------------------
TILE = (24.0, 28.0, 84.0, 84.0)          # x0, y0, x1, y1
TILE_RADIUS = 10.0
HEADER = (24.0, 28.0, 84.0, 44.0)        # rounded on the top corners only
RINGS = ((38.0, 25.0, 44.0, 37.0), (64.0, 25.0, 70.0, 37.0))
RING_RADIUS = 2.0
CELLS = (
    (38.0, 48.0, 46.0, 56.0),
    (50.0, 48.0, 58.0, 56.0),
    (62.0, 48.0, 70.0, 56.0),
    (38.0, 64.0, 46.0, 72.0),
    (50.0, 64.0, 58.0, 72.0),
)
CELL_RADIUS = 2.0
CHECK_POINTS = ((63.0, 68.0), (68.0, 73.0), (77.0, 59.0))
CHECK_WIDTH = 6.0

# --- palette --------------------------------------------------------------------
TILE_COLOR = '#F7FAF8'
HEADER_COLOR = '#007C83'
RING_COLOR = '#D9FFF1'
CELL_COLOR = '#A8B7B6'
CHECK_COLOR = '#27B894'
# Both stops use Android's #AARRGGBB order, exactly as the vector gradients do.
SHEEN = (('#33FFFFFF', 0.0), ('#0FFFFFFF', 0.5), ('#00000000', 1.0))
VIGNETTE = (('#00000000', 0.0), ('#00000000', 0.45), ('#14000000', 1.0))

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
def rounded_rect_path(x0: float, y0: float, x1: float, y1: float, r: float) -> str:
    """SVG-style path data for a rounded rectangle."""
    if r <= 0:
        return f'M{x0:g},{y0:g} H{x1:g} V{y1:g} H{x0:g} Z'
    return (
        f'M{x0 + r:g},{y0:g} '
        f'H{x1 - r:g} A{r:g},{r:g} 0 0 1 {x1:g},{y0 + r:g} '
        f'V{y1 - r:g} A{r:g},{r:g} 0 0 1 {x1 - r:g},{y1:g} '
        f'H{x0 + r:g} A{r:g},{r:g} 0 0 1 {x0:g},{y1 - r:g} '
        f'V{y0 + r:g} A{r:g},{r:g} 0 0 1 {x0 + r:g},{y0:g} Z'
    )


def top_rounded_rect_path(x0: float, y0: float, x1: float, y1: float, r: float) -> str:
    """Path data for a rectangle whose top corners are rounded."""
    return (
        f'M{x0 + r:g},{y0:g} '
        f'H{x1 - r:g} A{r:g},{r:g} 0 0 1 {x1:g},{y0 + r:g} '
        f'V{y1:g} H{x0:g} '
        f'V{y0 + r:g} A{r:g},{r:g} 0 0 1 {x0 + r:g},{y0:g} Z'
    )


def polyline_path(points) -> str:
    return 'M' + ' L'.join(f'{x:g},{y:g}' for x, y in points)


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


def sd_top_rounded_rect(px: float, py: float, box, radius: float) -> float:
    """Signed distance to a rectangle with rounded top corners."""
    d = sd_rounded_rect(px, py, box, radius)
    if py > box[3]:
        dx = max(box[0] - px, px - box[2], 0.0)
        d = min(d, math.hypot(dx, py - box[3]))
    return d


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


def ink_bounds() -> dict:
    """The bounding box of the mark, grown by half of each stroke width it carries.

    The monochrome layer strokes the tile outline, and both layers stroke the completion
    check, so the union has to account for those before it can be compared with the safe
    zone.
    """
    boxes = [TILE, HEADER, *RINGS, *CELLS]
    xs = [value for box in boxes for value in (box[0], box[2])]
    ys = [value for box in boxes for value in (box[1], box[3])]
    left, top, right, bottom = min(xs), min(ys), max(xs), max(ys)
    # The tile is stroked 5 wide in the monochrome layer.
    outline = 5.0 / 2.0
    left, top = left - outline, top - outline
    right, bottom = right + outline, bottom + outline
    # The completion check is stroked on both layers.
    check = CHECK_WIDTH / 2.0
    cx = [point[0] for point in CHECK_POINTS]
    cy = [point[1] for point in CHECK_POINTS]
    return {
        'left': min(left, min(cx) - check),
        'top': min(top, min(cy) - check),
        'right': max(right, max(cx) + check),
        'bottom': max(bottom, max(cy) + check),
    }


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
    # 3. a quiet corner vignette in the opposite corner.
    layer(lambda px, py: gradient_color(VIGNETTE, (px + py) / diagonal))

    tile = hex_rgba(TILE_COLOR)
    header = hex_rgba(HEADER_COLOR)
    ring = hex_rgba(RING_COLOR)
    cell = hex_rgba(CELL_COLOR)
    check = hex_rgba(CHECK_COLOR)

    # 4. the calendar tile.
    layer(lambda px, py: (*tile[:3], tile[3] * coverage(sd_rounded_rect(px, py, TILE, TILE_RADIUS))))
    # 5. the header band, rounded only where the tile is rounded.
    layer(lambda px, py: (*header[:3], header[3] * coverage(sd_top_rounded_rect(px, py, HEADER, TILE_RADIUS))))
    # 6. the binding rings.
    def ring_paint(px, py):
        alpha = 0.0
        for box in RINGS:
            alpha = max(alpha, coverage(sd_rounded_rect(px, py, box, RING_RADIUS)))
        return (*ring[:3], ring[3] * alpha)

    layer(ring_paint)
    # 7. the day cells.

    def cell_paint(px, py):
        alpha = 0.0
        for box in CELLS:
            alpha = max(alpha, coverage(sd_rounded_rect(px, py, box, CELL_RADIUS)))
        return (*cell[:3], cell[3] * alpha)

    layer(cell_paint)
    # 8. the completion check.
    layer(lambda px, py: (*check[:3], check[3] * coverage(sd_polyline(px, py, CHECK_POINTS) - CHECK_WIDTH / 2.0)))
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
VECTOR_HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Generated by scripts/generate_launcher_icons.py - edit the script, not this file.
'''

def background_xml() -> str:
    sheen = '\n'.join(
        f'                <item android:offset="{offset:g}" android:color="{color}" />' for color, offset in SHEEN
    )
    vignette = '\n'.join(
        f'                <item android:offset="{offset:g}" android:color="{color}" />' for color, offset in VIGNETTE
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Launcher icon background. Generated by scripts/generate_launcher_icons.py - edit the
  script, not this file.

  The base colour is the same one the splash window uses, so the launch animation never
  changes colour underneath the user. The sheen and the vignette give the tile a lit edge
  under every launcher mask instead of reading as a flat swatch.
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
    tile = rounded_rect_path(*TILE, TILE_RADIUS)
    header = top_rounded_rect_path(*HEADER, TILE_RADIUS)
    rings = ' '.join(rounded_rect_path(*box, RING_RADIUS) for box in RINGS)
    cells = ' '.join(rounded_rect_path(*box, CELL_RADIUS) for box in CELLS)
    check = polyline_path(CHECK_POINTS)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Launcher icon foreground. Generated by scripts/generate_launcher_icons.py - edit the
  script, not this file.

  The whole mark lives inside the 66dp safe zone of the 108dp canvas, so a circle, a
  squircle, a rounded square, a square and a teardrop mask all reveal the same calendar.
  The rings are the only elements allowed above the tile, and they stop at the safe zone.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{TILE_COLOR}"
        android:pathData="{tile}" />
    <path
        android:fillColor="{HEADER_COLOR}"
        android:pathData="{header}" />
    <path
        android:fillColor="{RING_COLOR}"
        android:pathData="{rings}" />
    <path
        android:fillColor="{CELL_COLOR}"
        android:pathData="{cells}" />
    <path
        android:fillColor="#00000000"
        android:pathData="{check}"
        android:strokeColor="{CHECK_COLOR}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="{CHECK_WIDTH:g}" />
</vector>
'''


def monochrome_xml() -> str:
    tile = rounded_rect_path(*TILE, TILE_RADIUS)
    # The header band has to hug the inner edge of the 5-wide outline, which is the tile
    # inset by half a stroke on every side.
    inset = 5.0 / 2.0
    header = top_rounded_rect_path(
        TILE[0] + inset, TILE[1] + inset, TILE[2] - inset, HEADER[3], TILE_RADIUS - inset,
    )
    rings = ' '.join(rounded_rect_path(*box, RING_RADIUS) for box in RINGS)
    cells = ' '.join(rounded_rect_path(*box, CELL_RADIUS) for box in CELLS)
    check = polyline_path(CHECK_POINTS)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Themed (monochrome) launcher icon. Generated by scripts/generate_launcher_icons.py -
  edit the script, not this file.

  The system replaces every colour with one tint, so the mark is drawn as a silhouette:
  an outlined page, a solid header band, two binding rings and the day cells plus the
  completion check inside it. Same safe zone as the foreground.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:pathData="{tile}"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineJoin="round"
        android:strokeWidth="5" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{header}" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{rings}" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{cells}" />
    <path
        android:fillColor="#00000000"
        android:pathData="{check}"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="{CHECK_WIDTH:g}" />
</vector>
'''


def adaptive_xml() -> str:
    return '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Adaptive launcher icon: three layers, masked by the launcher into whatever shape it
  uses. The background is a drawable rather than a flat colour so the tile keeps its
  sheen; the monochrome layer is what themed icons tint on Android 13 and later.
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
    return {
        'canvas': CANVAS,
        'safe_zone': [SAFE_LO, SAFE_HI],
        'ink_bounds': bounds,
        'safe': bounds['left'] >= SAFE_LO and bounds['top'] >= SAFE_LO
        and bounds['right'] <= SAFE_HI and bounds['bottom'] <= SAFE_HI,
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
    return 0


def check() -> int:
    problems = []
    for name in ('ic_launcher_background.xml', 'ic_launcher_foreground.xml', 'ic_launcher_monochrome.xml'):
        path = DRAWABLE / name
        if not path.exists():
            problems.append(f'missing {path.relative_to(ROOT)}')
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
