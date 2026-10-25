"""Static guards for SystemUI layouts (which use the app language, not the host locale)."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class NotificationDesignTests(unittest.TestCase):
    def test_every_design_has_explicit_direction_and_bounded_text(self):
        for design in ('classic', 'minimal', 'editorial'):
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

    def test_shadowed_headers_have_opaque_backing(self):
        for path in (
            'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/CalendarScreen.kt',
            'app/src/main/kotlin/com/marbledo/DashboardScreen.kt',
        ):
            self.assertNotIn('color = Color.Transparent,\n        shadowElevation', (ROOT / path).read_text())

    def test_compact_badge_fits_android_12_collapsed_height(self):
        for path in (ROOT / 'app/src/main/res/layout').glob('notification_*_compact_*.xml'):
            root = ET.parse(path).getroot()
            badge = next(root.iter('ImageView'))
            height = sum(int(v.removesuffix('dp')) for v in (
                badge.get(ANDROID + 'layout_height'),
                root.get(ANDROID + 'paddingTop'), root.get(ANDROID + 'paddingBottom')))
            self.assertLessEqual(height, 48, str(path))

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
        for design in ('classic', 'minimal', 'editorial'):
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


