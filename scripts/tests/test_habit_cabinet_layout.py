"""Guards for the habit cabinet's five zones.

The page used to mix a status card with the appearance controls and hand-rolled its own archive
block. These rules keep the reorganization: the header is only about today, the week gets its own
card, the archive reuses the drawer, and the appearance controls live in their own labelled zone.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / 'feature/habits/src/main/kotlin/com/marbledo/feature/habits/HabitCabinetScreen.kt'
COMPONENTS = ROOT / 'feature/habits/src/main/kotlin/com/marbledo/feature/habits/HabitComponents.kt'
RES = ROOT / 'feature/habits/src/main/res'


class HabitCabinetTests(unittest.TestCase):
    def setUp(self):
        self.source = SCREEN.read_text(encoding='utf-8')
        self.body = self.source[self.source.index('LazyColumn('):]

    def test_the_header_is_only_about_today(self):
        """Appearance controls in the header turned a status card into a settings card."""
        header = COMPONENTS.read_text(encoding='utf-8')
        header = header[header.index('fun HabitTodayHeader('):]
        header = header[:header.index('fun HabitOverviewStat(')]
        self.assertNotIn('footer', header)
        self.assertNotIn('HabitThemeDotRow', header)
        self.assertIn('HabitRing(', header)

    def test_the_week_has_its_own_card_above_the_drawers(self):
        self.assertIn('private fun HabitWeekOverview(', self.source)
        self.assertIn('HabitAnalytics.weekOverview(', self.source)
        self.assertLess(
            self.body.index('HabitWeekOverview('),
            self.body.index('HabitDrawer('),
        )

    def test_the_archive_reuses_the_drawer_component(self):
        archive = self.body[self.body.index('if (archived.isNotEmpty())'):]
        archive = archive[:archive.index('R.string.habit_look_title')]
        self.assertIn('HabitDrawer(', archive)
        self.assertIn('R.string.habit_archived_hint', archive)
        # No hand-rolled second expandable Surface: one drawer component, three users.
        self.assertNotIn('HorizontalDivider', self.body)
        self.assertEqual(3, self.body.count('HabitDrawer('))

    def test_appearance_and_the_widget_live_in_one_labelled_zone(self):
        self.assertIn('R.string.habit_look_title', self.body)
        self.assertIn('R.string.habit_look_hint', self.body)
        self.assertIn('HabitThemeDotRow(', self.body)
        self.assertIn('R.string.habit_add_widget', self.body)
        # ...and not inside the header.
        header = self.body[:self.body.index('HabitWeekOverview(')]
        self.assertNotIn('HabitThemeDotRow', header)
        self.assertNotIn('R.string.habit_add_widget', header)

    def test_habits_that_still_need_the_user_come_first(self):
        self.assertIn('private fun byAttention(', self.source)
        self.assertIn('compareByDescending<Habit> { isDoneToday(it, state) }', self.source)
        self.assertIn('orderedBuild.forEachIndexed', self.body)
        self.assertIn('orderedQuit.forEachIndexed', self.body)

    def test_every_drawer_says_how_it_is_ordered(self):
        self.assertEqual(2, self.body.count('R.string.habit_order_hint'))

    def test_the_new_strings_exist_in_both_languages(self):
        names = [
            'habit_archived_hint',
            'habit_week_title',
            'habit_week_done',
            'habit_week_none',
            'habit_look_title',
            'habit_look_hint',
            'habit_order_hint',
        ]
        for values in ('values', 'values-fa'):
            root = ET.parse(RES / values / 'strings.xml').getroot()
            found = {child.get('name') for child in root if child.tag == 'string'}
            for name in names:
                self.assertIn(name, found, f'{name} missing from {values}')

    def test_the_drawer_component_accepts_the_ordering_caption(self):
        components = COMPONENTS.read_text(encoding='utf-8')
        self.assertIn('caption: String? = null,', components)
        self.assertIn('caption,', components)

    def test_the_screen_has_no_leftover_unused_imports(self):
        """The archive rewrite removed the only users of these."""
        for symbol in ('HorizontalDivider', 'LocalConfiguration', 'ExpandLess', 'ExpandMore', 'clickable'):
            self.assertNotIn(
                f'import androidx.compose.{symbol}',
                self.source,
                f'{symbol} is imported but no longer used',
            )
