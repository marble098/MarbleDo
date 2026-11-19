"""Static guards for SystemUI layouts (which use the app language, not the host locale)."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


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
        settings = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            with self.subTest(design=design):
                if design == 'classic':
                    # Classic is the documented fallback of the view factory.
                    self.assertIn('else ->', views)
                else:
                    self.assertIn(f'"{design}" ->', views)
                self.assertIn(f'"{design}" to R.string.notification_design_{design}', settings)
                self.assertIn(f'R.layout.notification_{design}_compact_ltr', views)
                self.assertIn(f'R.layout.notification_{design}_expanded_ltr', views)
                for values in ('values', 'values-fa'):
                    strings = (ROOT / f'app/src/main/res/{values}/strings.xml').read_text()
                    self.assertIn(f'name="notification_design_{design}"', strings)

    def test_shadowed_headers_have_opaque_backing(self):
        for path in (
            'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/CalendarScreen.kt',
            'app/src/main/kotlin/com/marbledo/DashboardScreen.kt',
        ):
            self.assertNotIn('color = Color.Transparent,\n        shadowElevation', (ROOT / path).read_text())

    def test_compact_badge_fits_android_12_collapsed_height(self):
        """Android 12 clips the collapsed row at 48 dp, so the badge plus its own padding must fit.

        The paddings moved from the root to the content row when the card grew a plate: the root is
        now a FrameLayout holding the background image, and the padding belongs to the row that
        actually carries the text. The badge is still identified by id, because the banner theme
        also leads with an ImageView (the accent rule).
        """
        for path in (ROOT / 'app/src/main/res/layout').glob('notification_*_compact_*.xml'):
            root = ET.parse(path).getroot()
            parents = {
                child.get(ANDROID + 'id'): parent
                for parent in root.iter() for child in parent
            }
            badge = next(
                child for parent in root.iter() for child in parent
                if child.get(ANDROID + 'id', '').endswith('/notification_badge')
            )
            owner = parents[badge.get(ANDROID + 'id')]
            height = sum(int(v.removesuffix('dp')) for v in (
                badge.get(ANDROID + 'layout_height'),
                owner.get(ANDROID + 'paddingTop'), owner.get(ANDROID + 'paddingBottom')))
            self.assertLessEqual(height, 48, str(path))

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

    def test_banner_accent_rule_stays_an_imageview(self):
        """`setImageViewResource` targets this id, so it has to be an ImageView everywhere."""
        for size in ('compact', 'expanded'):
            for direction in ('rtl', 'ltr'):
                path = ROOT / f'app/src/main/res/layout/notification_banner_{size}_{direction}.xml'
                root = ET.parse(path).getroot()
                owners = [
                    parent for parent in root.iter()
                    if any(child.get(ANDROID + 'id', '').endswith('/notification_accent_rule') for child in parent)
                ]
                self.assertEqual(1, len(owners), str(path))
                # The rule is a direct child of a layout; check the element that carries the id.
                rules = [child for parent in owners for child in parent
                         if child.get(ANDROID + 'id', '').endswith('/notification_accent_rule')]
                self.assertEqual('ImageView', rules[0].tag, str(path))
                self.assertEqual('fitXY', rules[0].get(ANDROID + 'scaleType'), str(path))

    def test_notification_tint_is_not_reflective(self):
        """The banner tint must be a first-class RemoteViews call, not a setInt method lookup."""
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        code = re.sub(r'/\*.*?\*/', '', views, flags=re.S)
        code = '\n'.join(line.split('//')[0] for line in code.splitlines())
        self.assertNotIn('setInt(', code, 'the reflective RemoteViews action came back')
        self.assertIn('setImageViewResource(R.id.notification_accent_rule', code)
        for mood in ('holiday', 'religious', 'occasion', 'tasks', 'quiet'):
            drawable = ROOT / f'app/src/main/res/drawable/notification_banner_bar_{mood}.xml'
            self.assertTrue(drawable.exists(), f'missing mood drawable {drawable.name}')

    def test_settings_preview_uses_compose_without_android_view_interop(self):
        settings = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        self.assertNotIn('AndroidView', settings)
        self.assertIn('val isMinimal = design == "minimal"', settings)
        self.assertIn('val isEditorial = design == "editorial"', settings)
        self.assertIn('NotificationLivePreviewCard(', settings)
        self.assertIn('settings_notification_preview', settings)

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

    def test_expanded_notifications_have_two_sections_and_a_state_chip(self):
        """Priority 1 is the tasks, priority 2 the occasions, and the state is a chip.

        The redesign splits the card in two on purpose: what the user has to act on and what is
        merely interesting. A section that exists must also be *hideable*, which is why both
        containers carry ids whose visibility the builder sets.
        """
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for direction in ('rtl', 'ltr'):
                compact = (ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml').read_text()
                expanded = (ROOT / f'app/src/main/res/layout/notification_{design}_expanded_{direction}.xml').read_text()
                for markup, size in ((compact, 'compact'), (expanded, 'expanded')):
                    with self.subTest(design=design, direction=direction, size=size):
                        # The state chip replaces the progress bar and the mood pill.
                        self.assertIn('@+id/notification_state_container', markup)
                        self.assertIn('@+id/notification_state_label', markup)
                        self.assertIn('@+id/notification_state_chip_plate', markup)
                        self.assertIn('@+id/notification_state_icon', markup)
                        # Priority 1 is always present.
                        self.assertIn('@+id/notification_tasks_container', markup)
                        self.assertIn('@+id/notification_task_title', markup)
                        # What the chip replaced is gone for good.
                        self.assertNotIn('notification_progress', markup)
                        self.assertNotIn('notification_mood_pill', markup)
                # Priority 2 exists only on the expanded card, where there is room for it.
                self.assertIn('@+id/notification_occasions_container', expanded)
                self.assertIn('@+id/notification_occasions_label', expanded)
                self.assertIn('@+id/notification_occasions_icon', expanded)
                self.assertIn('@+id/notification_occasions_divider', expanded)
                self.assertNotIn('@+id/notification_occasions_container', compact)

        views_kt = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        self.assertIn('R.id.notification_state_label', views_kt)
        self.assertIn('R.id.notification_occasions_container', views_kt)
        self.assertIn('R.id.notification_task_title', views_kt)

    def test_the_task_title_is_never_clipped_to_one_line(self):
        """The most important line on the card was the one that got the ellipsis.

        A task title truncated to a single line is unreadable, so the compact card gives it two
        lines and the expanded card four; the ellipsis that remains is the last-resort overflow of
        a line that genuinely ran out of room.
        """
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for direction in ('rtl', 'ltr'):
                compact = ET.parse(ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml')
                expanded = ET.parse(ROOT / f'app/src/main/res/layout/notification_{design}_expanded_{direction}.xml')
                for tree, minimum in ((compact, 2), (expanded, 4)):
                    titles = [
                        view for view in tree.iter('TextView')
                        if (view.get(ANDROID + 'id') or '').endswith('/notification_task_title')
                    ]
                    with self.subTest(design=design, direction=direction, minimum=minimum):
                        self.assertEqual(1, len(titles), 'exactly one task title per card')
                        self.assertGreaterEqual(int(titles[0].get(ANDROID + 'maxLines')), minimum)
                        self.assertEqual('end', titles[0].get(ANDROID + 'ellipsize'))

    def test_the_chip_word_is_painted_in_an_ink_that_reads_on_its_own_pill(self):
        """The chip is a coloured pill with a word and a mark on it, not a word on the card.

        This is the contrast bug that a screenshot, not a compile, would catch: the state hue reads
        on the *card* (it clears 4.5:1 there) but the word sits on the state's own soft fill, and
        on the light theme's red that pairing is 1.3:1 — an embossed-looking chip. The fix is that
        the ink is chosen against the fill, so both the chip label and the mark must come from
        that one value, and neither may reuse the hue meant for the card.
        """
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        theme = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/NotificationTheme.kt').read_text()
        self.assertIn('val chipInk = inkOn(chipFill)', views,
                      'the chip ink must be derived from the fill behind it')
        self.assertIn('MarbleContrast.bestInk(fill)', views)
        # Both the word and the mark take that ink; the mark is a white vector in the resources, so
        # leaving it untinted would put white on a pale pill.
        self.assertIn('setTextColor(R.id.notification_state_label, chipInk)', views)
        self.assertIn('R.id.notification_state_icon, chipIcon(state), chipInk', views)
        self.assertNotIn('setTextColor(R.id.notification_state_label, stateHue)', views)
        # The fills are composited, so a bitmap can be filled with them and still hold ink.
        for field in ('accentFill', 'occasionFill'):
            self.assertIn(f'val {field}', theme, f'the palette lost {field}')
        self.assertIn('MarbleContrast.alphaOver(accent, plate,', theme)
        self.assertIn('MarbleContrast.alphaOver(occasion, plate,', theme)
        # The Compose preview reads the same two values, so it cannot show a chip the shade will not.
        settings = (ROOT / 'app/src/main/kotlin/com/marbledo/SettingsScreen.kt').read_text()
        self.assertIn('val chipFill = when (content.state)', settings)
        self.assertIn('val chipInk = Color(MarbleContrast.bestInk(chipFill))', settings)
        self.assertNotIn('tint = stateColor', settings)

    def test_the_chip_mark_is_not_left_white_on_a_pale_pill(self):
        """The static layout is the fallback when a tinted bitmap cannot be drawn.

        With no builder running, the drawable must still be visible: the pill is filled with the
        light theme's soft red, and the shipping vector is white, which is 1.3:1 on it. The layout
        therefore carries a static tint, and it is the state's own hue rather than the card's text
        colour so the two themes' colours stay in one table.
        """
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for direction in ('rtl', 'ltr'):
                for size in ('compact', 'expanded'):
                    path = ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml'
                    tree = ET.parse(path)
                    icons = [
                        view for view in tree.iter('ImageView')
                        if (view.get(ANDROID + 'id') or '').endswith('/notification_state_icon')
                    ]
                    with self.subTest(design=design, direction=direction, size=size):
                        self.assertEqual(1, len(icons), 'exactly one chip mark per card')
                        self.assertEqual('@color/notification_state_overdue', icons[0].get(ANDROID + 'tint'))

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

    def test_the_card_has_exactly_one_filled_surface(self):
        """The redesign's rule: the next step is the only place a background is painted."""
        for path in sorted((ROOT / 'app/src/main/res/layout').glob('notification_*_expanded_*.xml')):
            filled = [
                view.get(ANDROID + 'id', '').split('/')[-1]
                for view in ET.parse(path).getroot().iter()
                if view.get(ANDROID + 'background', '').startswith('@drawable/')
            ]
            expected = [] if 'minimal' in path.name else ['notification_next_container']
            with self.subTest(layout=path.name):
                self.assertEqual(expected, filled)

    def test_the_compact_card_shows_the_jalali_date_and_the_expanded_one_the_rest(self):
        """"۲۸ آبان ۱۴۰۵" collapsed; Gregorian and lunar only when the card has room.

        The old card stacked all three calendars into one low-contrast line that fit nowhere. The
        date the app is built around is the Jalali one, so that is the header; the cross-check line
        lives on the expanded card, labelled, where it can wrap onto two lines.
        """
        source = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/PersistentCalendarNotification.kt').read_text()
        self.assertIn('R.string.notification_secondary_dates', source)
        # The title is the day, the month *and* the year, with no weekday pushing it out.
        self.assertIn('${today.year}', source)
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        self.assertIn('setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)', views)
        # The compact card must not carry the secondary line at all.
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for direction in ('rtl', 'ltr'):
                compact = (ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml').read_text()
                self.assertNotIn('notification_secondary_dates', compact)

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
        self.assertIn('setTextColor(R.id.notification_state_label, chipInk)', views)
        self.assertIn('setTextColor(R.id.notification_tasks_label, tint)', views)
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
