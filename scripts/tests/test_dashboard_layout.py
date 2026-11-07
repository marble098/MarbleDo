"""Guards for the three-stop home page.

The home page used to be one endless stack of eleven cards, which is what made it read as
cluttered. These rules keep the structure that replaced it: a hero that stays put, one
segmented row, and exactly three stops with one job each.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / 'app/src/main/kotlin/com/marbledo/DashboardScreen.kt'
RES = ROOT / 'app/src/main/res'


class DashboardLayoutTests(unittest.TestCase):
    def setUp(self):
        self.source = SCREEN.read_text(encoding='utf-8')
        self.body = self.source[self.source.index('when (selectedTab) {'):]

    def test_the_home_page_has_exactly_three_stops(self):
        enum = self.source[self.source.index('private enum class DashboardTab'):]
        enum = enum[:enum.index('}')]
        stops = re.findall(r'^\s{4}(\w+)\(R\.string\.(\w+)\),', enum, re.M)
        self.assertEqual(
            [
                ('TODAY', 'dash_tab_today'),
                ('WEEK', 'dash_tab_week'),
                ('OCCASIONS', 'dash_tab_occasions'),
            ],
            stops,
        )

    def test_every_stop_is_translated(self):
        for name in ('dash_tab_today', 'dash_tab_week', 'dash_tab_occasions'):
            for values in ('values', 'values-fa'):
                root = ET.parse(RES / values / 'strings.xml').getroot()
                found = {child.get('name') for child in root if child.tag == 'string'}
                self.assertIn(name, found, f'{name} missing from {values}')

    def test_the_stops_are_reachable_from_one_segmented_row(self):
        self.assertIn('private fun DashboardTabRow(', self.source)
        row = self.source[self.source.index('private fun DashboardTabRow('):]
        row = row[:row.index('\n}\n')]
        self.assertIn('DashboardTab.entries.forEach', row)
        self.assertIn('.selectable(selected = isSelected, role = Role.Tab)', row)
        # Every stop keeps a full-size touch target instead of being squeezed to a third.
        self.assertIn('.weight(1f)', row)

    def test_each_stop_owns_its_own_block(self):
        # Order in the source is the order on the screen, so each card has to sit inside the
        # branch that owns it rather than in one shared stack below all three.
        self.assertLess(self.body.index('DashboardTab.TODAY -> {'), self.body.index('R.string.dash_tasks'))
        self.assertLess(self.body.index('DashboardTab.WEEK -> {'), self.body.index('WeeklyWorkloadCard('))
        self.assertLess(self.body.index('WeeklyWorkloadCard('), self.body.index('DashboardTab.OCCASIONS -> {'))
        self.assertLess(self.body.index('DashboardTab.OCCASIONS -> {'), self.body.index('OccasionsCard('))
        self.assertLess(self.body.index('OccasionsCard('), self.body.index('OccasionRadarCard('))

    def test_the_hero_no_longer_repeats_todays_occasions(self):
        """The occasions stop owns them now; a second copy above it was pure clutter."""
        hero = self.source[self.source.index('private fun DailyHeaderCard('):]
        hero = hero[:hero.index('private fun HeaderMetric(')]
        self.assertNotIn('primaryOccasion', hero)
        self.assertNotIn('todayOccasions', hero)

    def test_the_hero_keeps_the_date_and_the_counters(self):
        hero = self.source[self.source.index('private fun DailyHeaderCard('):]
        hero = hero[:hero.index('private fun HeaderMetric(')]
        self.assertIn('PersianDateUtils.tripleDate(', hero)
        self.assertIn('FocusOrbit(', hero)
        self.assertIn('R.string.dash_stat_today', hero)
