"""Static guards for the launcher icon set.

The set has to stay complete and the mark has to stay inside the adaptive-icon safe
zone, otherwise a launcher with an aggressive mask (a teardrop, a tight squircle)
silently clips the calendar.
"""
import re
import struct
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'

# The inner 66dp of the 108dp adaptive canvas. Every mask keeps at least this much.
CANVAS = 108.0
SAFE_LO, SAFE_HI = 21.0, 87.0
OUTLINE_STROKE = 5.0
CHECK_STROKE = 6.0

DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 128, 'xxxhdpi': 192}


TOKEN = re.compile(r'([MmLlHhVvAaCcSsQqTtZz])|(-?\d*\.?\d+(?:[eE][-+]?\d+)?)')


def path_bounds(path_data: str):
    """Bounding box reached by an SVG path, following its commands.

    Arc flags are plain numbers, so the coordinates cannot simply be taken as every
    other token in the string.
    """
    tokens = []
    for match in TOKEN.finditer(path_data):
        tokens.append(match.group(1) if match.group(1) else float(match.group(2)))
    x = y = start_x = start_y = 0.0
    xs, ys = [], []
    command = None
    index = 0

    def record():
        xs.append(x)
        ys.append(y)

    while index < len(tokens):
        token = tokens[index]
        if isinstance(token, str):
            command = token
            index += 1
            if command in 'Zz':
                x, y = start_x, start_y
                record()
            continue
        if command in 'Mm':
            x, y = token, tokens[index + 1]
            index += 2
            start_x, start_y = x, y
        elif command in 'LlTt':
            x, y = token, tokens[index + 1]
            index += 2
        elif command == 'Hh':
            x = token
            index += 1
        elif command == 'Vv':
            y = token
            index += 1
        elif command in 'Aa':
            # rx ry rotation large-arc sweep x y
            x, y = tokens[index + 5], tokens[index + 6]
            index += 7
        elif command in 'Cc':
            x, y = tokens[index + 4], tokens[index + 5]
            index += 6
        elif command in 'QqSs':
            x, y = tokens[index + 2], tokens[index + 3]
            index += 4
        else:
            index += 1
            continue
        record()
    if not xs:
        raise AssertionError(f'no coordinates in {path_data!r}')
    return min(xs), min(ys), max(xs), max(ys)


class LauncherIconTests(unittest.TestCase):
    def test_every_density_carries_a_square_and_a_round_bitmap(self):
        for density, size in DENSITIES.items():
            for name in ('ic_launcher.png', 'ic_launcher_round.png'):
                with self.subTest(density=density, name=name):
                    path = RES / f'mipmap-{density}' / name
                    self.assertTrue(path.is_file(), f'missing {path.relative_to(ROOT)}')
                    data = path.read_bytes()
                    self.assertEqual(b'\x89PNG\r\n\x1a\n', data[:8], str(path))
                    width, height = struct.unpack('>II', data[16:24])
                    self.assertEqual((size, size), (width, height), str(path))

    def test_square_and_round_bitmaps_actually_differ(self):
        """A round bitmap that is not masked is the classic copy-paste bug."""
        for density in DENSITIES:
            with self.subTest(density=density):
                square = (RES / f'mipmap-{density}/ic_launcher.png').read_bytes()
                rounded = (RES / f'mipmap-{density}/ic_launcher_round.png').read_bytes()
                self.assertNotEqual(square, rounded)

    def test_adaptive_icons_declare_all_three_layers(self):
        for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
            with self.subTest(name=name):
                root = ET.parse(RES / 'mipmap-anydpi-v26' / name).getroot()
                self.assertEqual('adaptive-icon', root.tag)
                layers = {child.tag: child.get(ANDROID + 'drawable') for child in root}
                self.assertEqual(
                    {
                        'background': '@drawable/ic_launcher_background',
                        'foreground': '@drawable/ic_launcher_foreground',
                        'monochrome': '@drawable/ic_launcher_monochrome',
                    },
                    layers,
                )

    def test_manifest_points_at_the_launcher_icons(self):
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:icon="@mipmap/ic_launcher"', manifest)
        self.assertIn('android:roundIcon="@mipmap/ic_launcher_round"', manifest)

    def test_background_reuses_the_splash_colour(self):
        background = (RES / 'drawable/ic_launcher_background.xml').read_text()
        self.assertIn('@color/marble_launcher_bg', background)
        themes = (RES / 'values/themes.xml').read_text()
        self.assertIn('windowSplashScreenIconBackgroundColor', themes)

    def test_foreground_and_monochrome_stay_inside_the_safe_zone(self):
        for name, stroke in (('ic_launcher_foreground.xml', CHECK_STROKE),
                             ('ic_launcher_monochrome.xml', OUTLINE_STROKE)):
            with self.subTest(name=name):
                root = ET.parse(RES / 'drawable' / name).getroot()
                self.assertEqual((108.0, 108.0), (float(root.get(ANDROID + 'width')[:-2]),
                                                  float(root.get(ANDROID + 'height')[:-2])))
                boxes = [path_bounds(path.get(ANDROID + 'pathData')) for path in root]
                # A stroked path reaches half a stroke past its own coordinates.
                grow = max(
                    (float(path.get(ANDROID + 'strokeWidth') or 0) / 2.0 for path in root),
                    default=0.0,
                )
                self.assertGreater(grow, 0.0, name)
                left = min(box[0] for box in boxes) - grow
                top = min(box[1] for box in boxes) - grow
                right = max(box[2] for box in boxes) + grow
                bottom = max(box[3] for box in boxes) + grow
                self.assertGreaterEqual(left, SAFE_LO, f'{name} spills left of the safe zone')
                self.assertGreaterEqual(top, SAFE_LO, f'{name} spills above the safe zone')
                self.assertLessEqual(right, SAFE_HI, f'{name} spills right of the safe zone')
                self.assertLessEqual(bottom, SAFE_HI, f'{name} spills below the safe zone')

    def test_monochrome_is_a_single_colour_silhouette(self):
        root = ET.parse(RES / 'drawable/ic_launcher_monochrome.xml').getroot()
        for path in root:
            fill = path.get(ANDROID + 'fillColor')
            stroke = path.get(ANDROID + 'strokeColor')
            for colour in (fill, stroke):
                if colour is not None:
                    self.assertIn(colour.upper(), ('#FFFFFFFF', '#00000000'), colour)
            # Every mark has to be visible, so a path cannot be both empty and unstroked.
            self.assertTrue(fill != '#00000000' or stroke is not None, path.get(ANDROID + 'pathData'))

    def test_generator_reports_the_safe_zone_it_drew(self):
        manifest_path = ROOT / 'scripts/launcher_icon_manifest.json'
        self.assertTrue(manifest_path.is_file(), 'run scripts/generate_launcher_icons.py')
        manifest = __import__('json').loads(manifest_path.read_text())
        bounds = manifest['ink_bounds']
        self.assertTrue(manifest['safe'], f'the mark leaves the safe zone: {bounds}')
        self.assertGreaterEqual(bounds['left'], SAFE_LO)
        self.assertGreaterEqual(bounds['top'], SAFE_LO)
        self.assertLessEqual(bounds['right'], SAFE_HI)
        self.assertLessEqual(bounds['bottom'], SAFE_HI)
        self.assertEqual(DENSITIES, manifest['densities'])

    def test_notification_icons_declare_their_own_size(self):
        """Every vector icon states a size so it never inherits a neighbour's density."""
        for path in sorted((RES / 'drawable').glob('ic_*.xml')):
            with self.subTest(name=path.name):
                root = ET.parse(path).getroot()
                self.assertIsNotNone(root.get(ANDROID + 'width'), str(path))
                self.assertIsNotNone(root.get(ANDROID + 'height'), str(path))
                self.assertIsNotNone(root.get(ANDROID + 'viewportWidth'), str(path))
