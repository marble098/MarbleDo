"""Static guards for the launcher icon set.

The set has to stay complete and the mark has to stay whole under every launcher mask, otherwise
a launcher with an aggressive mask (an oval, a teardrop, a tight squircle) silently clips it.
"""
import importlib.util
import json
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
CHECK_STROKE = 6.6
ORBIT_STROKE = 4.4
# The mark is a marble inside an orbit; nothing may reach further than this from the centre.
MAX_INK_RADIUS = 28.0
LAUNCHER_MASKS = ('circle', 'squircle', 'rounded_square', 'square', 'oval', 'teardrop')

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


def path_points(path_data: str):
    """Every anchor the path actually passes through, following its commands."""
    xs, ys = [], []
    tokens = []
    for match in TOKEN.finditer(path_data):
        tokens.append(match.group(1) if match.group(1) else float(match.group(2)))
    x = y = start_x = start_y = 0.0
    command = None
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if isinstance(token, str):
            command = token
            index += 1
            if command in 'Zz':
                x, y = start_x, start_y
                xs.append(x)
                ys.append(y)
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
        xs.append(x)
        ys.append(y)
    return list(zip(xs, ys))


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


class LauncherMaskTests(unittest.TestCase):
    """The masks themselves, as opposed to the adaptive-icon safe zone.

    A bounding-box check inside 66x66 is necessary but not sufficient: an oval mask keeps only
    36 x 30 around the centre, which is *narrower* than the safe square at the top and bottom.
    The generator measures the real signed distance from the mark's outline to each mask and
    publishes it in the manifest, so a redesign cannot quietly ship an icon that only looks right
    on a Pixel's circle.
    """

    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('generate_launcher_icons', ROOT / 'scripts/generate_launcher_icons.py')
        cls.generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.generator)
        cls.manifest = json.loads((ROOT / 'scripts/launcher_icon_manifest.json').read_text())

    def test_every_launcher_mask_keeps_the_whole_mark(self):
        masks = self.manifest['masks']
        for name in LAUNCHER_MASKS:
            with self.subTest(mask=name):
                self.assertIn(name, masks)
                self.assertTrue(masks[name]['clear'], f'{name} clips the mark: {masks[name]}')
                self.assertGreaterEqual(masks[name]['clearance'], 1.0, f'{name}: {masks[name]}')
        self.assertTrue(self.manifest['all_masks_clear'])

    def test_the_mark_stays_small_enough_for_an_oval_launcher(self):
        self.assertLessEqual(self.manifest['ink_radius'], MAX_INK_RADIUS)
        self.assertLessEqual(self.generator.ORBIT_RADIUS + self.generator.ORBIT_WIDTH / 2.0, MAX_INK_RADIUS)
        self.assertLessEqual(self.generator.MARBLE_RADIUS, MAX_INK_RADIUS)

    def test_foreground_ink_is_inside_the_declared_radius(self):
        """Every point the mark passes through, plus half its stroke, stays inside the radius."""
        reach = self.generator.ORBIT_RADIUS + self.generator.ORBIT_WIDTH / 2.0
        for name in ('ic_launcher_foreground.xml', 'ic_launcher_monochrome.xml'):
            with self.subTest(name=name):
                root = ET.parse(RES / 'drawable' / name).getroot()
                for path in root:
                    grow = float(path.get(ANDROID + 'strokeWidth') or 0) / 2.0
                    for x, y in path_points(path.get(ANDROID + 'pathData')):
                        distance = ((x - 54.0) ** 2 + (y - 54.0) ** 2) ** 0.5
                        self.assertLessEqual(distance + grow, reach + 0.01, f'{name}: ({x}, {y})')

    def test_themed_icon_punches_the_tick_out_of_the_marble(self):
        """A stroked tick cannot be subtracted from a fill: the hole has to be a subpath."""
        root = ET.parse(RES / 'drawable/ic_launcher_monochrome.xml').getroot()
        body = root[0]
        self.assertEqual('evenOdd', body.get(ANDROID + 'fillType'))
        self.assertGreaterEqual(body.get(ANDROID + 'pathData').count('M'), 2)

    def test_generated_resources_match_the_generator(self):
        """Any hand edit to the vectors is caught here instead of in a launcher."""
        for name, expected in (
            ('ic_launcher_background.xml', self.generator.background_xml()),
            ('ic_launcher_foreground.xml', self.generator.foreground_xml()),
            ('ic_launcher_monochrome.xml', self.generator.monochrome_xml()),
        ):
            with self.subTest(name=name):
                self.assertEqual(expected, (RES / 'drawable' / name).read_text(encoding='utf-8'))
        for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
            with self.subTest(name=name):
                self.assertEqual(self.generator.adaptive_xml(), (RES / 'mipmap-anydpi-v26' / name).read_text(encoding='utf-8'))

    def test_the_legacy_bitmaps_are_real_rasterisations(self):
        """The square and round bitmaps carry different pixels, so none is a copy of the other."""
        for density in DENSITIES:
            with self.subTest(density=density):
                square = (RES / f'mipmap-{density}/ic_launcher.png').read_bytes()
                rounded = (RES / f'mipmap-{density}/ic_launcher_round.png').read_bytes()
                self.assertNotEqual(square, rounded)
