"""Static guards for SystemUI layouts (which use the app language, not the host locale)."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'

# The anatomy of each look. scripts/generate_notification_layouts.py writes the XML from the same
# table and NotificationLook.kt carries the same flags; the tests below hold all three together.
LOOKS = {
    'classic': dict(badge=True, chip_pill=True, band=False, header_divider=False),
    'minimal': dict(badge=False, chip_pill=False, band=False, header_divider=False),
    'editorial': dict(badge=False, chip_pill=False, band=False, header_divider=True),
    'banner': dict(badge=False, chip_pill=False, band=True, header_divider=False),
}
FLAG_NAMES = {'badge': 'badge', 'chip_pill': 'chipPill', 'band': 'band', 'header_divider': 'headerDivider'}


class NotificationDesignTests(unittest.TestCase):
    def test_every_design_has_explicit_direction_and_bounded_text(self):
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for size in ('compact', 'expanded'):
                for direction in ('rtl', 'ltr'):
                    with self.subTest(design=design, size=size, direction=direction):
                        path = ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml'
                        tree = ET.parse(path)
                        self.assertEqual(direction, tree.getroot().get(ANDROID + 'layoutDirection'))
                        for text in tree.iter('TextView'):
                            self.assertEqual(direction, text.get(ANDROID + 'textDirection'))
                            self.assertEqual('viewStart', text.get(ANDROID + 'textAlignment'))
                            self.assertIsNotNone(text.get(ANDROID + 'maxLines'))

    def test_every_selectable_design_is_wired_to_a_layout_pair_and_strings(self):
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        look = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationLook.kt').read_text()
        settings = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        for design in LOOKS:
            with self.subTest(design=design):
                self.assertIn(f'{design.upper()}("{design}"', look)
                self.assertIn(f'NotificationLook.{design.upper()} ->', views)
                self.assertIn(f'"{design}" to R.string.notification_design_{design}', settings)
                self.assertIn(f'R.layout.notification_{design}_compact_ltr', views)
                self.assertIn(f'R.layout.notification_{design}_expanded_ltr', views)
                for values in ('values', 'values-fa'):
                    strings = (ROOT / f'app/src/main/res/{values}/strings.xml').read_text()
                    self.assertIn(f'name="notification_design_{design}"', strings)
        # Unknown values fall back to classic instead of crashing the builder.
        self.assertIn('?: CLASSIC', look)

    def test_shadowed_headers_have_opaque_backing(self):
        for path in (
            'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/CalendarScreen.kt',
            'app/src/main/kotlin/com/marbledo/DashboardScreen.kt',
        ):
            self.assertNotIn('color = Color.Transparent,\n        shadowElevation', (ROOT / path).read_text())

    def test_compact_badge_fits_android_12_collapsed_height(self):
        """Android 12 clips the collapsed row at 48 dp, so the badge plus its row padding must fit."""
        for design, look in LOOKS.items():
            if not look['badge']:
                continue
            for direction in ('rtl', 'ltr'):
                path = ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml'
                root = ET.parse(path).getroot()
                badge = next(v for v in root.iter() if v.get(ANDROID + 'id', '').endswith('/notification_badge'))
                paddings = [
                    int(v.get(ANDROID + 'paddingTop').removesuffix('dp')) + int(v.get(ANDROID + 'paddingBottom').removesuffix('dp'))
                    for v in root.iter() if v.get(ANDROID + 'paddingTop')
                ]
                height = int(badge.get(ANDROID + 'layout_height').removesuffix('dp')) + max(paddings, default=0)
                with self.subTest(design=design, direction=direction):
                    self.assertLessEqual(height, 48, str(path))

    def test_cards_fill_system_width_and_expanded_layouts_get_roomy_content_insets(self):
        for path in sorted((ROOT / 'app/src/main/res/layout').glob('notification_*.xml')):
            root = ET.parse(path).getroot()
            with self.subTest(layout=path.name):
                self.assertEqual('match_parent', root.get(ANDROID + 'layout_width'))
            if '_expanded_' not in path.name:
                continue
            content = list(root)[1]  # the plate is the first child, the card content is the second
            if '_banner_' in path.name:
                content = list(content)[1]  # banner header, then the padded body
            self.assertEqual('12dp', content.get(ANDROID + 'paddingStart'), path.name)
            self.assertEqual('12dp', content.get(ANDROID + 'paddingEnd'), path.name)
            self.assertEqual('20dp', content.get(ANDROID + 'paddingBottom'), path.name)

    def test_the_card_paints_its_own_plate(self):
        """A custom row sits inside SystemUI's shade, so it has to own its background.

        The root used to have no background at all: the text then sat on whatever the shade showed,
        which is why the same card was readable in one theme and washed out in the other. Every
        layout now starts with an ImageView plate painted by ``setImageViewResource`` — a
        first-class RemoteViews action, unlike the reflective ``setInt(…, "setBackgroundColor")``.
        """
        for path in sorted((ROOT / 'app/src/main/res/layout').glob('notification_*.xml')):
            root = ET.parse(path).getroot()
            plate = next(
                (child for parent in root.iter() for child in parent
                 if child.get(ANDROID + 'id', '').endswith('/notification_card_plate')),
                None,
            )
            self.assertIsNotNone(plate, f'{path.name}: no card plate')
            self.assertEqual('ImageView', plate.tag, str(path))
            self.assertEqual('fitXY', plate.get(ANDROID + 'scaleType'), str(path))
            self.assertIn(plate.get(ANDROID + 'src'), (
                '@drawable/notification_card_plate_light',
                '@drawable/notification_card_plate_dark',
            ), str(path))
            # One plate per card, and it is the first thing drawn.
            count = sum(
                1 for parent in root.iter() for child in parent
                if child.get(ANDROID + 'id', '').endswith('/notification_card_plate')
            )
            self.assertEqual(1, count, str(path))
            self.assertIs(next(iter(root)), plate, f'{path.name}: the content is drawn under the plate')

    def test_notification_layouts_only_use_widgets_remoteviews_can_inflate(self):
        """A class outside RemoteViews' allowlist takes the whole notification off the screen.

        This is the banner bug in its second form: every banner layout was built from bare
        `<View>` elements (the accent rule and the two hairlines). RemoteViews documents a fixed
        widget list that does not include `View`; a SystemUI build that enforces it fails to
        expand the custom view, the row is dropped, and the notification leaves the shade and
        the status bar together. The layouts therefore stick to ImageView/FrameLayout.
        """
        allowlist = {
            'LinearLayout', 'FrameLayout', 'RelativeLayout', 'GridLayout', 'AbsoluteLayout',
            'TextView', 'Button', 'ImageButton', 'ImageView', 'ProgressBar', 'Chronometer',
            'AnalogClock', 'TextClock', 'ViewFlipper', 'AdapterViewFlipper', 'ListView',
            'GridView', 'StackView', 'Space',
        }
        layouts = sorted((ROOT / 'app/src/main/res/layout').glob('notification_*.xml'))
        self.assertGreaterEqual(len(layouts), 16)
        for path in layouts:
            for element in ET.parse(path).getroot().iter():
                self.assertIn(
                    element.tag,
                    allowlist,
                    f'{path.name}: <{element.tag}> is not a RemoteViews-inflatable widget, so '
                    'SystemUI drops the notification instead of drawing it',
                )

    def test_every_look_declares_exactly_the_anatomy_its_flags_promise(self):
        """The layouts, the Kotlin `NotificationLook` flags and this table must agree.

        A `RemoteViews` action on an id the layout does not declare aborts the whole apply, so the
        builder only touches the ids the look's flags allow; conversely an element the layout has
        but the builder never paints would keep its fallback colour. The flags are therefore the
        single source of truth, and the generated XML is checked against them here.
        """
        look_kt = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationLook.kt').read_text()
        for design, look in LOOKS.items():
            line = re.search(rf'{design.upper()}\("{design}",(.*?)\),\n', look_kt).group(1)
            kotlin = {key: f'{camel} = true' in line for key, camel in FLAG_NAMES.items()}
            self.assertEqual(look, kotlin, f'NotificationLook.{design.upper()} disagrees with the layouts table')
            for size in ('compact', 'expanded'):
                for direction in ('rtl', 'ltr'):
                    path = ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml'
                    ids = {v.get(ANDROID + 'id', '').split('/')[-1] for v in ET.parse(path).getroot().iter()}
                    with self.subTest(design=design, size=size, direction=direction):
                        self.assertEqual(look['badge'], 'notification_badge' in ids)
                        self.assertEqual(look['chip_pill'], 'notification_state_chip_plate' in ids)
                        # One surface only: no look paints a fill under the tasks any more.
                        self.assertNotIn('notification_task_panel', ids)
                        self.assertNotIn('notification_accent_rule', ids)
                        self.assertEqual(look['band'], {'notification_band_start', 'notification_band_fill', 'notification_band_end'} <= ids)
                        self.assertEqual(look['header_divider'], 'notification_header_divider' in ids)
                        # The skeleton every look shares.
                        for shared in ('notification_card_plate', 'notification_title', 'notification_state_container',
                                       'notification_state_label', 'notification_state_icon', 'notification_tasks_container',
                                       'notification_task_title', 'notification_task_meta'):
                            self.assertIn(shared, ids)

    def test_nothing_with_a_corner_is_a_stretched_bitmap(self):
        """A rounded bitmap under `fitXY` turns its corners into ellipses; shapes carry the corners.

        The plate, the state pill and the hairlines are shape drawables (they scale exactly), and
        the only bitmaps on the card are the banner caps, which are drawn with `scaleType="matrix"`
        so they are never scaled at all. The band is three slices for exactly that reason.
        """
        for path in sorted((ROOT / 'app/src/main/res/layout').glob('notification_*.xml')):
            for view in ET.parse(path).getroot().iter('ImageView'):
                view_id = view.get(ANDROID + 'id', '').split('/')[-1]
                scale = view.get(ANDROID + 'scaleType')
                with self.subTest(layout=path.name, view=view_id):
                    if view_id in ('notification_band_start', 'notification_band_end'):
                        self.assertEqual('matrix', scale)
                        self.assertEqual('20dp', view.get(ANDROID + 'layout_width'))
                    else:
                        self.assertEqual('fitXY', scale)
                    src = view.get(ANDROID + 'src', '')
                    if view_id in ('notification_card_plate', 'notification_state_chip_plate',
                                   'notification_occasions_divider', 'notification_header_divider'):
                        self.assertTrue(src.startswith('@drawable/notification_'), f'{view_id} must be a shape drawable')
                        drawable = (ROOT / 'app/src/main/res/drawable' / (src.split('/')[-1] + '.xml')).read_text()
                        self.assertIn('<shape', drawable)
        # No layout paints a background drawable: every surface is an ImageView the builder picks.
        for path in sorted((ROOT / 'app/src/main/res/layout').glob('notification_*.xml')):
            for view in ET.parse(path).getroot().iter():
                self.assertIsNone(view.get(ANDROID + 'background'), f'{path.name}: {view.tag} paints a background')
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        artwork = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationArtwork.kt').read_text()
        for gone in ('chipPill(', 'focusPlate(', 'accentRule(', 'verticalRule('):
            self.assertNotIn(gone, views)
            self.assertNotIn(f'fun {gone}', artwork)
        self.assertIn('fun bandCap(', artwork)
        self.assertIn('BAND_CAP_DP = 20f', artwork)

    def test_the_card_follows_the_app_theme_not_the_device_night_mode(self):
        """Every drawable the builder picks by lightness is a literal colour with a light/dark twin."""
        drawables = ROOT / 'app/src/main/res/drawable'
        for stem in ('notification_card_plate', 'notification_hairline',
                     'notification_chip_overdue', 'notification_chip_occasion', 'notification_chip_neutral'):
            for half in ('light', 'dark'):
                path = drawables / f'{stem}_{half}.xml'
                with self.subTest(drawable=path.name):
                    self.assertTrue(path.exists(), f'missing {path.name}')
                    body = re.sub(r'<!--.*?-->', '', path.read_text(), flags=re.S)
                    self.assertNotIn('@color/', body, f'{path.name} re-resolves against night mode')
        theme = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationTheme.kt').read_text()
        look = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationLook.kt').read_text()
        pairs = {
            'notification_chip_overdue_light': ('OVERDUE_LIGHT', look),
            'notification_chip_overdue_dark': ('OVERDUE_DARK', look),
            'notification_chip_occasion_light': ('OCCASION_LIGHT', look),
            'notification_chip_occasion_dark': ('OCCASION_DARK', look),
            'notification_chip_neutral_light': ('NEUTRAL_LIGHT', look),
            'notification_chip_neutral_dark': ('NEUTRAL_DARK', look),
        }
        for drawable, (constant, source) in pairs.items():
            with self.subTest(constant=constant):
                value = re.search(constant + r'\s*=\s*0xFF([0-9A-Fa-f]{6})L', source).group(1).upper()
                self.assertIn(f'#{value}', (drawables / f'{drawable}.xml').read_text().upper())
        for half, constant in (('light', 'NOTIFICATION_HAIRLINE_LIGHT'), ('dark', 'NOTIFICATION_HAIRLINE_DARK')):
            value = re.search(constant + r'\s*=\s*0x([0-9A-Fa-f]{8})L', theme).group(1).upper()
            self.assertIn(f'#{value}', (drawables / f'notification_hairline_{half}.xml').read_text().upper())

    def test_notification_tint_is_not_reflective(self):
        """Every colour on the card is a first-class RemoteViews call, never a setInt method lookup."""
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        code = re.sub(r'/\*.*?\*/', '', views, flags=re.S)
        code = '\n'.join(line.split('//')[0] for line in code.splitlines())
        self.assertNotIn('setInt(', code, 'the reflective RemoteViews action came back')
        self.assertNotIn('setBackgroundColor', code)
        self.assertNotIn('setColorFilter', code)
        self.assertIn('setImageViewResource(R.id.notification_state_chip_plate', code)
        # Each look-specific action is guarded by the look's flag.
        for flag, needle in (('badge', 'R.id.notification_badge'), ('chipPill', 'R.id.notification_state_chip_plate'),
                             ('band', 'R.id.notification_band_fill'), ('headerDivider', 'R.id.notification_header_divider')):
            with self.subTest(flag=flag):
                guarded = re.search(rf'if \(look\.{flag}\) \{{(.*?)\n            \}}', code, flags=re.S)
                self.assertIsNotNone(guarded, f'no `if (look.{flag})` block')
                self.assertIn(needle, guarded.group(1))
                self.assertEqual(code.count(needle), guarded.group(1).count(needle), f'{needle} is used outside its guard')

    def test_settings_preview_uses_compose_without_android_view_interop(self):
        """The preview is Compose, and it branches on the same look flags as the layouts."""
        settings = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        self.assertNotIn('AndroidView', settings)
        self.assertIn('val look = NotificationLook.from(settings.notificationDesign)', settings)
        self.assertIn('NotificationLivePreviewCard(', settings)
        self.assertIn('settings_notification_preview', settings)
        self.assertIn('DesignSchematic(NotificationLook.from(design))', settings)
        for flag in ('look.badge', 'look.chipPill', 'look.band', 'look.headerDivider'):
            with self.subTest(flag=flag):
                self.assertIn(flag, settings)
        # No string comparisons against design names remain outside the option table.
        self.assertNotIn('design == "minimal"', settings)
        self.assertNotIn('design == "editorial"', settings)
        self.assertNotIn('design == "banner"', settings)

    def test_occasion_notification_keeps_its_semantic_extras(self):
        worker = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarOccurrenceWorker.kt').read_text()
        self.assertIn('.addExtras(Bundle().apply {', worker)
        self.assertIn('EXTRA_EDUCATIONAL_CONTENT', worker)
        self.assertIn('EXTRA_CULTURES', worker)
        self.assertIn('.setStyle(inbox)', worker)
        self.assertIn('.setColor(accentColor)', worker)
        self.assertNotIn('.setExtras(', worker, 'setExtras replaces the classification bundle needed to clear stale notifications')

    def test_occasion_bloom_lazy_row_uses_day_and_occasion_id_key(self):
        bloom = (ROOT / 'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/OccasionBloomView.kt').read_text()
        self.assertIn('key = { "${it.first}-${it.second.id}" }', bloom)

    def test_database_singleton_does_not_gate_on_is_open(self):
        db = (ROOT / 'core/data/src/main/kotlin/com/marbledo/core/data/db/MarbleDatabase.kt').read_text()
        self.assertNotIn('takeIf { it.isOpen }', db)

    def test_expanded_notifications_have_two_sections_and_a_state(self):
        """Priority 1 is the tasks, priority 2 the occasions, and the state is a word in a colour."""
        for design in LOOKS:
            for direction in ('rtl', 'ltr'):
                compact = (ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml').read_text()
                expanded = (ROOT / f'app/src/main/res/layout/notification_{design}_expanded_{direction}.xml').read_text()
                for markup, size in ((compact, 'compact'), (expanded, 'expanded')):
                    with self.subTest(design=design, direction=direction, size=size):
                        self.assertIn('@+id/notification_state_container', markup)
                        self.assertIn('@+id/notification_state_label', markup)
                        self.assertIn('@+id/notification_state_icon', markup)
                        self.assertIn('@+id/notification_tasks_container', markup)
                        self.assertIn('@+id/notification_task_title', markup)
                        self.assertNotIn('notification_progress', markup)
                        self.assertNotIn('notification_mood_pill', markup)
                self.assertIn('@+id/notification_occasions_container', expanded)
                self.assertIn('@+id/notification_occasions_label', expanded)
                self.assertIn('@+id/notification_occasions_icon', expanded)
                self.assertIn('@+id/notification_occasions_divider', expanded)
                self.assertNotIn('@+id/notification_occasions_container', compact)

    def test_task_line_budgets_preserve_the_collapsed_card_and_expanded_reading(self):
        """One line collapsed (48dp host budget), two expanded; never below 11sp.

        Two lines is the budget the expanded card can afford next to the second task and the
        occasion block — and the card is designed so a title never *needs* a third: whatever does
        not fit is counted as "و ۲ کار دیگر" rather than ellipsised.
        """
        for design in LOOKS:
            for direction in ('rtl', 'ltr'):
                compact = ET.parse(ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml')
                expanded = ET.parse(ROOT / f'app/src/main/res/layout/notification_{design}_expanded_{direction}.xml')
                for tree, minimum in ((compact, 1), (expanded, 2)):
                    titles = [v for v in tree.iter('TextView') if (v.get(ANDROID + 'id') or '').endswith('/notification_task_title')]
                    with self.subTest(design=design, direction=direction, minimum=minimum):
                        self.assertEqual(1, len(titles), 'exactly one task title per card')
                        self.assertGreaterEqual(int(titles[0].get(ANDROID + 'maxLines')), minimum)
                        self.assertEqual('end', titles[0].get(ANDROID + 'ellipsize'))
                    for view in tree.iter('TextView'):
                        size = int(view.get(ANDROID + 'textSize').removesuffix('sp'))
                        self.assertGreaterEqual(size, 11, f'{design}: {view.get(ANDROID + "id")} is {size}sp')

    def test_the_state_ink_is_chosen_against_the_surface_it_sits_on(self):
        """On a pill the word reads on the pill; on the band it is the band's ink; else the plate's.

        The state hue is checked against the *card* (it clears 4.5:1 there), which says nothing
        about how it reads on its own soft pill or on the accent band. The three cases are decided
        once in the builder and mirrored by the preview, so neither can paint a word the other won't.
        """
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        look = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationLook.kt').read_text()
        settings = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        self.assertIn('look.chipPill -> NotificationChipFills.ink(colors, state)', views)
        self.assertIn('look.band -> onAccent', views)
        self.assertIn('setTextColor(R.id.notification_state_label, stateInk)', views)
        self.assertIn('MarbleContrast.readable(palette.stateHue(state), fill(state, palette.isDark), MarbleContrast.TEXT_MINIMUM)', look)
        self.assertIn('look.chipPill -> Color(NotificationChipFills.ink(shade, content.state))', settings)
        self.assertIn('look.band -> onAccent', settings)

    def test_the_state_mark_has_a_static_fallback_tint(self):
        """With no builder running, the white vector must not be left white on a pale pill."""
        for design in LOOKS:
            for direction in ('rtl', 'ltr'):
                for size in ('compact', 'expanded'):
                    path = ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml'
                    icons = [v for v in ET.parse(path).iter('ImageView') if (v.get(ANDROID + 'id') or '').endswith('/notification_state_icon')]
                    with self.subTest(design=design, direction=direction, size=size):
                        self.assertEqual(1, len(icons), 'exactly one state mark per card')
                        self.assertEqual('@color/notification_state_overdue', icons[0].get(APP + 'tint'))

    def test_the_layouts_are_generated_from_one_table(self):
        """Sixteen hand-written files drifted; one table cannot. Regenerate with --check."""
        import subprocess
        import sys
        result = subprocess.run(
            [sys.executable, str(ROOT / 'scripts/generate_notification_layouts.py'), '--check'],
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_direction_pairs_are_identical_apart_from_the_direction(self):
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for size in ('compact', 'expanded'):
                with self.subTest(design=design, size=size):
                    base = ROOT / f'app/src/main/res/layout/notification_{design}_{size}'
                    rtl = base.with_name(base.name + '_rtl.xml').read_text(encoding='utf-8')
                    ltr = base.with_name(base.name + '_ltr.xml').read_text(encoding='utf-8')
                    self.assertEqual(rtl.replace('"rtl"', '"ltr"'), ltr)

    def test_every_text_states_its_colour(self):
        """A custom card inherits SystemUI's text colour; in one of the two themes it vanishes."""
        for path in sorted((ROOT / 'app/src/main/res/layout').glob('notification_*_rtl.xml')):
            for text in ET.parse(path).iter('TextView'):
                with self.subTest(layout=path.name, view=text.get(ANDROID + 'id')):
                    self.assertIsNotNone(text.get(ANDROID + 'textColor'), path.name)

    def test_the_card_has_exactly_one_fill_and_it_is_the_plate(self):
        """No look paints a second surface under the tasks, and none draws a rule beside them.

        Both used to exist: a tonal panel under the task block and, for the editorial look, a 3 dp
        vertical accent rule beside it. On a real phone the panel read as a box stuck onto the card
        (the "yellow box" the redesign was asked to remove) and the rule landed on the first letter
        of the task title in RTL. The plate is now the only fill on the card, and the space between
        sections does the separating.
        """
        for design in LOOKS:
            for path in sorted((ROOT / 'app/src/main/res/layout').glob(f'notification_{design}_*.xml')):
                root = ET.parse(path).getroot()
                containers = [v for v in root.iter() if v.get(ANDROID + 'id', '').endswith('/notification_next_container')]
                with self.subTest(layout=path.name):
                    self.assertEqual(1, len(containers))
                    # The task block is a plain vertical stack: its only child is the rows.
                    self.assertEqual('LinearLayout', containers[0].tag)
                    self.assertEqual('vertical', containers[0].get(ANDROID + 'orientation'))
                    # No image anywhere inside the block: no panel behind it, no rule beside it.
                    self.assertEqual([], [v for v in containers[0].iter('ImageView')])
                    # And no other full-height fill on the card: the plate is the only surface.
                    fills = {
                        v.get(ANDROID + 'id', '').split('/')[-1]
                        for parent in root.iter() for v in parent
                        if v.get(ANDROID + 'layout_height') == 'match_parent'
                        and v.get(ANDROID + 'scaleType') == 'fitXY'
                        and v.tag == 'ImageView'
                    }
                    # (The banner's band is a header fill, not a surface under the tasks.)
                    self.assertTrue(fills <= {
                        'notification_card_plate', 'notification_state_chip_plate',
                        'notification_band_start', 'notification_band_fill', 'notification_band_end',
                    }, fills)
                    self.assertIn('notification_card_plate', fills)

    def test_the_card_states_one_calendar_only(self):
        """"۲۸ آبان ۱۴۰۵" is the card's only date — no Gregorian, no lunar, at either size.

        The old card stacked three calendars into one low-contrast line under the header, which
        truncated to "قمری ۹ ج…" on every narrow phone and asked the reader to work out which date
        belonged to which task. A notification is a two-second summary, so it states the Jalali
        date with its year and leaves the cross-check to the calendar screen.
        """
        source = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/PersistentCalendarNotification.kt').read_text()
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        preview = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        # The title is the day, the month *and* the year, with no weekday pushing it out.
        self.assertIn('${today.year}', source)
        for text in (source, views, preview):
            self.assertNotIn('secondaryDatesLine', text)
            self.assertNotIn('notification_secondary_dates', text)
        self.assertNotIn('notification_secondary_dates', (ROOT / 'app/src/main/res/values/intelligent_planning_strings.xml').read_text())
        self.assertNotIn('notification_secondary_dates', (ROOT / 'app/src/main/res/values-fa/intelligent_planning_strings.xml').read_text())
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for size in ('compact', 'expanded'):
                for direction in ('rtl', 'ltr'):
                    layout = (ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml').read_text()
                    self.assertNotIn('notification_secondary_dates', layout)

    def test_a_task_row_carries_its_clock_and_nothing_else(self):
        """A row is "title … ۱۳:۰۰". The date, the priority and the category are not in the card.

        The old metadata was "۲۷ آبان · ۱۳:۰۰ · فوری · بین‌الملل", four facts in a column narrower
        than the title: the reader got "۲۹ آبان …" and "بین‌الملل…" and had to open the app anyway.
        """
        source = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/PersistentCalendarNotification.kt').read_text()
        self.assertNotIn('TaskPriority', source)
        self.assertNotIn('tasks_priority_', source)
        self.assertNotIn('task.category', source)
        self.assertIn('fun taskMeta(task: Task?): String? = taskTime(task)', source)

    def test_calendar_screen_uses_unified_command_card_and_adaptive_month_rows(self):
        cal_kt = (ROOT / 'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/CalendarScreen.kt').read_text()
        self.assertIn('CalendarCommandCard', cal_kt)
        self.assertIn('val cells = if (allCells.subList(35, 42).none { it.inDisplayedMonth }) allCells.take(35) else allCells', cal_kt)




class NotificationThemeTests(unittest.TestCase):
    """The shade follows the app theme, not a hard-coded indigo."""

    def test_accent_text_ids_match_the_layouts(self):
        """Every id the builder tints is exactly one the layout paints in the accent colour.

        The builder tints the compact and the expanded card separately, so each side is compared
        with the layouts of that size. An id that is tinted but not declared would make RemoteViews
        throw while SystemUI applies the row; an id that is declared but never tinted would keep
        the night resource — the device theme — instead of the app theme.
        """
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        # The accent is painted at run time, so the layouts state the accent only as a *fallback*
        # for the views the builder always recolours. Those ids are exactly the ones the builder
        # re-tints from the palette; anything else would keep the night resource, i.e. the device
        # theme, instead of the app theme.
        # The state label states the *state's* colour (red for overdue, the accent for today), so
        # it is stated in the layout and then re-stated by the builder; the tasks heading is the
        # one label that is always the brand accent.
        compact = set()
        expanded = {'notification_tasks_label'}
        self.assertIn('setTextColor(R.id.notification_state_label, stateInk)', views)
        self.assertIn('setTextColor(R.id.notification_tasks_label, accent)', views)
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for direction in ('rtl', 'ltr'):
                for size, expected in (('compact', compact), ('expanded', expanded)):
                    path = ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml'
                    actual = {
                        view.get(ANDROID + 'id').split('/')[-1]
                        for view in ET.parse(path).iter()
                        if view.get(ANDROID + 'textColor') == '@color/notification_card_accent'
                    }
                    with self.subTest(layout=path.name):
                        self.assertEqual(expected, actual, str(path))

    def test_the_palette_plates_match_the_drawables(self):
        """The palette checks text against a plate, so the plate constants must be the real fills.

        The card's contrasts are computed in `notificationPalette` against
        `NOTIFICATION_PLATE_LIGHT`/`_DARK`; if those drift from the two drawables, every number the
        palette promises describes a colour the shade never paints.
        """
        theme = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationTheme.kt').read_text()
        for half, constant in (('light', 'NOTIFICATION_PLATE_LIGHT'), ('dark', 'NOTIFICATION_PLATE_DARK')):
            with self.subTest(half=half):
                match = re.search(constant + r'\s*=\s*0xFF([0-9A-Fa-f]{6})L', theme)
                self.assertIsNotNone(match, f'{constant} is not a 0xAARRGGBB literal')
                drawable = (ROOT / f'app/src/main/res/drawable/notification_card_plate_{half}.xml').read_text()
                self.assertIn(f'#{match.group(1).upper()}', drawable.upper(),
                              f'{constant} no longer matches the plate drawable')

    def test_the_ongoing_notification_is_tinted_from_the_app_palette(self):
        """The card, its plate and its text all come from one palette resolved from the app theme.

        The palette lives in NotificationTheme.kt now, so the assertion is that the *builder* asks
        for it — reading the resolved palette from the app theme is what the old `@color/...` table
        could not do.
        """
        source = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/PersistentCalendarNotification.kt').read_text()
        theme = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationTheme.kt').read_text()
        self.assertIn('notificationPalette(notificationContext, settings.themeMode)', source)
        self.assertIn('MarblePalettes.resolve(', theme)
        self.assertIn('settings.themeMode', source)
        # A theme switch has to change the signature, otherwise the posted row is never updated.
        signature = source.split('val signature = listOf(')[1].split(')\n')[0]
        self.assertIn('settings.themeMode', signature)
        # Both the collapsed and the expanded card are handed the same palette.
        self.assertGreaterEqual(source.count('shade,'), 2, 'a card view is built without the palette')

    def test_every_plain_notification_uses_the_theme_accent(self):
        for name in ('ReminderReceiver.kt', 'CalendarOccurrenceWorker.kt'):
            with self.subTest(name=name):
                source = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications' / name).read_text()
                self.assertNotIn('R.color.marble_accent', source)
                self.assertIn('notificationAccent(', source)

    def test_both_widgets_paint_themselves_from_the_app_theme(self):
        widgets = {
            'countdown': ROOT / 'feature/countdown/src/main/kotlin/com/marbledo/feature/countdown/MarbleCountdownWidget.kt',
            'habits': ROOT / 'feature/habits/src/main/kotlin/com/marbledo/feature/habits/MarbleHabitWidget.kt',
        }
        for name, path in widgets.items():
            with self.subTest(widget=name):
                source = path.read_text(encoding='utf-8')
                self.assertIn('MarblePalettes.resolve(', source)
                self.assertIn('settings.themeMode', source)
        app = (ROOT / 'app/src/main/kotlin/com/marbledo/MarbleDoApp.kt').read_text()
        habit_effect = app.split('updateMarbleHabitWidgets(context)')[0].rsplit('LaunchedEffect(', 1)[1]
        self.assertIn('settings.themeMode', habit_effect)


def _block_after(source: str, marker: str) -> str:
    """The brace-balanced body of the first block that starts at ``marker``."""
    start = source.index(marker) + len(marker)
    depth = 1
    for index in range(start, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return source[start:index]
    raise AssertionError(f'unbalanced block after {marker!r}')


def _split_else(block: str):
    """Split a block on its top-level ``} else {``, or return None when it has no else branch."""
    depth = 0
    for index, char in enumerate(block):
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
            if depth == 0 and block[index:index + 9].startswith('} else {'):
                return block[:index], block[index + 8:]
    return None
