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
        for path in (ROOT / 'app/src/main/res/layout').glob('notification_*_compact_*.xml'):
            root = ET.parse(path).getroot()
            # Identify the badge by id: the banner theme also leading with an ImageView (the
            # accent rule) would otherwise be mistaken for it.
            badge = next(view for view in root.iter() if view.get(ANDROID + 'id', '').endswith('/notification_badge'))
            height = sum(int(v.removesuffix('dp')) for v in (
                badge.get(ANDROID + 'layout_height'),
                root.get(ANDROID + 'paddingTop'), root.get(ANDROID + 'paddingBottom')))
            self.assertLessEqual(height, 48, str(path))

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

    def test_occasion_bloom_lazy_row_uses_day_and_occasion_id_key(self):
        bloom = (ROOT / 'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/OccasionBloomView.kt').read_text()
        self.assertIn('key = { "${it.first}-${it.second.id}" }', bloom)

    def test_database_singleton_does_not_gate_on_is_open(self):
        db = (ROOT / 'core/data/src/main/kotlin/com/marbledo/core/data/db/MarbleDatabase.kt').read_text()
        self.assertNotIn('takeIf { it.isOpen }', db)

    def test_expanded_notifications_have_smart_section_containers_and_mood_pill(self):
        for design in ('classic', 'minimal', 'editorial', 'banner'):
            for direction in ('rtl', 'ltr'):
                compact = (ROOT / f'app/src/main/res/layout/notification_{design}_compact_{direction}.xml').read_text()
                expanded = (ROOT / f'app/src/main/res/layout/notification_{design}_expanded_{direction}.xml').read_text()
                self.assertIn('@+id/notification_mood_pill', compact)
                self.assertIn('@+id/notification_mood_pill', expanded)
                self.assertIn('@+id/notification_progress_container', expanded)
                self.assertIn('@+id/notification_next_container', expanded)
                self.assertIn('@+id/notification_occasions_container', expanded)

        views_kt = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        self.assertIn('R.id.notification_progress_container', views_kt)
        self.assertIn('R.id.notification_next_container', views_kt)
        self.assertIn('R.id.notification_occasions_container', views_kt)

    def test_calendar_screen_uses_unified_command_card_and_adaptive_month_rows(self):
        cal_kt = (ROOT / 'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/CalendarScreen.kt').read_text()
        self.assertIn('CalendarCommandCard', cal_kt)
        self.assertIn('val cells = if (allCells.subList(35, 42).none { it.inDisplayedMonth }) allCells.take(35) else allCells', cal_kt)




class NotificationThemeTests(unittest.TestCase):
    """The shade follows the app theme, not a hard-coded indigo."""

    def test_accent_text_ids_match_the_layouts(self):
        """The ids the builder tints are exactly the ones the XML paints in the accent colour."""
        views = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt').read_text()
        for compact_list, expanded_list in (
            ('accentTextIds', 'expandedAccentTextIds'),
        ):
            compact = set(re.findall(r'R\.id\.(\w+)', views.split(compact_list + ' = listOf(')[1].split(')')[0]))
            expanded = set(re.findall(r'R\.id\.(\w+)', views.split(expanded_list + ' = listOf(')[1].split(')\n')[0]))
            self.assertTrue(compact, 'no compact accent ids declared')
            self.assertTrue(expanded, 'no expanded accent ids declared')
            for design in ('classic', 'minimal', 'editorial', 'banner'):
                for direction in ('rtl', 'ltr'):
                    for size, declared in (('compact', compact), ('expanded', expanded)):
                        path = ROOT / f'app/src/main/res/layout/notification_{design}_{size}_{direction}.xml'
                        tree = ET.parse(path)
                        actual = {
                            view.get(ANDROID + 'id').split('/')[-1]
                            for view in tree.iter()
                            if view.get(ANDROID + 'textColor') == '@color/notification_card_accent'
                        }
                        self.assertEqual(declared, actual, str(path))

    def test_the_ongoing_notification_is_tinted_from_the_app_palette(self):
        source = (ROOT / 'app/src/main/kotlin/com/marbledo/app/notifications/PersistentCalendarNotification.kt').read_text()
        self.assertIn('MarblePalettes.resolve(', source)
        self.assertIn('settings.themeMode', source)
        # A theme switch has to change the signature, otherwise the posted row is never updated.
        signature = source.split('val signature = listOf(')[1].split(')')[0]
        self.assertIn('settings.themeMode', signature)
        # Both the collapsed and the expanded card are handed the theme accent.
        self.assertGreaterEqual(source.count(', accent)'), 2, 'a card view is built without the theme accent')

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
