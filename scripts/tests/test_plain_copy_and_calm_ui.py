"""Guards for the plain-language pass and the calmer home and calendar screens.

Two promises are checked here:

* the Persian copy stays short and free of the technical words the app used to speak in, and
* the home page keeps labelled controls (a word next to every tool) and the calendar keeps its
  circular day numbers, because both were the main source of "this looks busy" feedback.
"""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DASHBOARD = ROOT / 'app/src/main/kotlin/com/marbledo/DashboardScreen.kt'
CALENDAR = ROOT / 'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/CalendarScreen.kt'
RES = ROOT / 'app/src/main/res'
CAL_RES = ROOT / 'feature/calendar/src/main/res'

PERSIAN_STRING_FILES = sorted(
    list((ROOT / 'app/src/main/res').glob('values-fa/*.xml'))
    + [path for path in (ROOT / 'feature').glob('*/src/main/res/values-fa/*.xml')]
)

# Persian copy that lives in Kotlin because it becomes task titles rather than UI labels.
PERSIAN_KOTLIN_COPY = [
    ROOT / 'feature/calendar/src/main/kotlin/com/marbledo/feature/calendar/OccasionTemplates.kt',
]

# Words the old copy leaned on. None of them is needed to say the same thing in everyday Persian.
JARGON = [
    'بذر تعطیلات',
    'گاه‌شماری',
    'آینهٔ آماده',
    'کانال تاریخ',
    'نویسه',
    'تحلیل‌گر',
    'ردیاب',
    'بخشنامه',
    'پردازش نشد',
    'در دسترس نمی‌باشد',
]


def strings(path):
    root = ET.parse(path).getroot()
    return {child.get('name'): (child.text or '') for child in root if child.tag == 'string'}


class PlainCopyTests(unittest.TestCase):
    def test_no_technical_jargon_is_left_in_the_persian_copy(self):
        for path in PERSIAN_STRING_FILES + PERSIAN_KOTLIN_COPY:
            text = path.read_text(encoding='utf-8')
            for word in JARGON:
                self.assertNotIn(word, text, f'{word} still appears in {path.name}')

    def test_no_persian_sentence_is_a_wall_of_text(self):
        for path in PERSIAN_STRING_FILES:
            for name, value in strings(path).items():
                # One short paragraph is the ceiling; longer explanations belong in the README.
                self.assertLess(len(value), 200, f'{path.name}/{name} is {len(value)} characters')

    def test_the_new_home_and_calendar_words_exist_in_both_languages(self):
        for keys, en, fa in (
            (
                ('dash_hero_today_label', 'dash_hero_open_count', 'dash_hero_all_done',
                 'dash_progress_done_total', 'dash_add_first_task'),
                RES / 'values/strings.xml',
                RES / 'values-fa/strings.xml',
            ),
            (
                ('calendar_day_no_tasks', 'calendar_day_tasks_label',
                 'calendar_day_occasions_label', 'calendar_quick_add'),
                CAL_RES / 'values/strings.xml',
                CAL_RES / 'values-fa/strings.xml',
            ),
        ):
            for path in (en, fa):
                found = strings(path)
                for key in keys:
                    self.assertIn(key, found, f'{key} missing from {path}')


class HomeLayoutTests(unittest.TestCase):
    def setUp(self):
        self.source = DASHBOARD.read_text(encoding='utf-8')

    def test_every_home_tool_carries_its_word(self):
        self.assertIn('private fun ToolChip(', self.source)
        tool_row = self.source[self.source.index('// A labelled tool row.'):]
        tool_row = tool_row[:tool_row.index('\n                    }\n', tool_row.index('ToolChip('))]
        self.assertGreaterEqual(tool_row.count('ToolChip('), 4)
        for label in ('R.string.dash_search', 'R.string.dash_categories', 'R.string.dash_sort_menu'):
            self.assertIn(label, tool_row)

    def test_the_task_list_headline_shows_progress_and_a_labelled_add(self):
        self.assertIn('private fun TaskListHeader(', self.source)
        header = self.source[self.source.index('private fun TaskListHeader('):]
        header = header[:header.index('\n}\n')]
        self.assertIn('R.string.dash_progress_done_total', header)
        self.assertIn('R.string.dash_new_task', header)
        self.assertIn('fillMaxWidth(fraction)', header)

    def test_the_empty_list_offers_one_way_out(self):
        self.assertIn('R.string.dash_add_first_task', self.source)
        self.assertIn('R.string.dash_empty_body', self.source)


class CalendarLayoutTests(unittest.TestCase):
    def setUp(self):
        self.source = CALENDAR.read_text(encoding='utf-8')

    def test_the_day_number_sits_in_its_own_circle(self):
        cell = self.source[self.source.index('private fun DayCellView('):]
        cell = cell[:cell.index('private fun SelectedDayCard(')]
        self.assertIn('CircleShape', cell)
        self.assertIn('.size(34.dp)', cell)
        self.assertIn('defaultMinSize(minHeight = 52.dp)', cell)

    def test_the_command_card_labels_both_shortcuts(self):
        card = self.source[self.source.index('private fun CalendarCommandCard('):]
        card = card[:card.index('private fun CalendarStepButton(')]
        self.assertIn('R.string.calendar_today', card)
        self.assertIn('R.string.calendar_go_to_date', card)
        self.assertIn('CalendarViewSwitch(', card)

    def test_the_selected_day_opens_with_its_own_number_tile(self):
        card = self.source[self.source.index('private fun SelectedDayCard('):]
        card = card[:card.index('private fun AgendaRow(')]
        self.assertIn('persian.day', card)
        self.assertIn('R.string.calendar_quick_add', card)
        self.assertIn('R.string.calendar_day_no_tasks', card)


if __name__ == '__main__':
    unittest.main()
