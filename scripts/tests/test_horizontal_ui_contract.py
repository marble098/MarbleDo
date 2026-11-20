"""Structural regression checks for horizontal notifications and responsive controls.

These are source/layout contract checks, not Android screenshot tests. In particular the compact
height estimate is a conservative geometry budget at the default font scale, not proof of OEM
rendering. Font-scaled/device rendering still needs the manual matrix in the design contract.
"""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LAYOUTS = ROOT / 'app/src/main/res/layout'
ANDROID = '{http://schemas.android.com/apk/res/android}'
APP = ROOT / 'app/src/main/kotlin/com/marbledo'
FEATURE = ROOT / 'feature'


def source(path):
    return path.read_text(encoding='utf-8')


def layouts(size='*'):
    for path in sorted(LAYOUTS.glob(f'notification_*_{size}_*.xml')):
        root = ET.parse(path).getroot()
        by_id = {v.get(ANDROID + 'id', '').split('/')[-1]: v for v in root.iter()}
        parents = {child: parent for parent in root.iter() for child in parent}
        yield path.name, root, by_id, parents


def height_budget(view):
    """Allow 1.5em per line, count nested insets/gaps, and ignore match-parent backplates."""
    def dp(key):
        return float(view.get(ANDROID + key, '0dp').removesuffix('dp'))

    inset = sum(dp(key) for key in ('paddingTop', 'paddingBottom', 'layout_marginTop', 'layout_marginBottom'))
    height = view.get(ANDROID + 'layout_height', 'wrap_content')
    if height.endswith('dp'):
        return float(height[:-2]) + inset
    if view.tag == 'TextView':
        return int(view.get(ANDROID + 'maxLines')) * float(view.get(ANDROID + 'textSize')[:-2]) * 1.5 + inset
    children = [height_budget(child) for child in view]
    if view.tag == 'LinearLayout' and view.get(ANDROID + 'orientation') == 'vertical':
        return sum(children) + inset
    return max(children, default=0) + inset


class HorizontalNotificationTests(unittest.TestCase):
    def test_all_sixteen_variants_are_covered(self):
        self.assertEqual(16, len(list(layouts())))

    def test_zero_width_weighted_views_are_only_in_horizontal_linear_rows(self):
        """A weighted 0dp title in a vertical date column disappears completely."""
        for name, root, _, parents in layouts():
            for view in root.iter():
                if view.get(ANDROID + 'layout_width') != '0dp':
                    continue
                with self.subTest(layout=name, view=view.get(ANDROID + 'id')):
                    self.assertGreater(float(view.get(ANDROID + 'layout_weight', '0')), 0)
                    self.assertEqual('LinearLayout', parents[view].tag)
                    self.assertEqual('horizontal', parents[view].get(ANDROID + 'orientation'))

    def test_date_and_state_share_a_horizontal_header(self):
        for name, _, ids, parents in layouts():
            ancestor = ids['notification_title']
            while ancestor in parents:
                ancestor = parents[ancestor]
                if ids['notification_state_container'] in ancestor.iter():
                    break
            with self.subTest(layout=name):
                self.assertEqual('LinearLayout', ancestor.tag)
                self.assertEqual('horizontal', ancestor.get(ANDROID + 'orientation'))

    def test_task_and_metadata_share_a_row_with_a_fixed_time_column(self):
        """The time is a *column*, not a share of the row.

        A weighted metadata cell moved with the length of the title beside it, so the two rows'
        times did not line up and the RTL rows read as scattered. A fixed dp column puts every time
        at the same distance from the row's edge on every card and in both sizes, which is the
        alignment the redesign asked for; the title takes the rest of the row.
        """
        for name, _, ids, parents in layouts():
            stems = ['notification_task']
            if '_expanded_' in name:
                stems.append('notification_task_second')
            expanded = '_expanded_' in name
            for stem in stems:
                title, meta = ids[stem + '_title'], ids[stem + '_meta']
                with self.subTest(layout=name, task=stem):
                    self.assertIs(parents[title], parents[meta])
                    self.assertEqual('horizontal', parents[title].get(ANDROID + 'orientation'))
                    self.assertEqual('0dp', title.get(ANDROID + 'layout_width'))
                    self.assertEqual('1', title.get(ANDROID + 'layout_weight'))
                    # One column width for both rows of a card, and a single line for the time.
                    self.assertEqual('52dp' if expanded else '46dp', meta.get(ANDROID + 'layout_width'))
                    self.assertEqual('1', meta.get(ANDROID + 'maxLines'))

    def test_the_occasion_heading_gets_a_row_of_its_own(self):
        """Side by side, the heading ate the value's width and the value arrived cut off.

        "مناسبت‌های امروز" in a fixed box next to the value left "بین‌الملل…" — a truncated word in
        the one place the card has spare width, since the pair is only two short lines.
        """
        for name, _, ids, parents in layouts('expanded'):
            label, value = ids['notification_occasions_label'], ids['notification_occasions_text']
            with self.subTest(layout=name):
                self.assertIsNot(parents[label], parents[value])
                self.assertEqual('match_parent', value.get(ANDROID + 'layout_width'))
                self.assertEqual('2', value.get(ANDROID + 'maxLines'))
                # The heading row is the icon and the label, full width.
                self.assertIs(parents[label], parents[ids['notification_occasions_icon']])
                self.assertEqual('horizontal', parents[label].get(ANDROID + 'orientation'))
                self.assertEqual('0dp', label.get(ANDROID + 'layout_width'))

    def test_collapsed_geometry_budgets_the_whole_card_not_just_the_badge(self):
        for name, root, ids, _ in layouts('compact'):
            with self.subTest(layout=name):
                self.assertLessEqual(height_budget(root), 48, name)
                self.assertEqual('1', ids['notification_task_title'].get(ANDROID + 'maxLines'))
                self.assertEqual('1', ids['notification_task_meta'].get(ANDROID + 'maxLines'))
                self.assertNotIn('notification_task_second_row', ids)
                self.assertNotIn('notification_occasions_container', ids)
                self.assertNotIn('notification_secondary_dates', ids)

    def test_text_can_grow_with_font_scale_without_a_fixed_height(self):
        for name, root, _, _ in layouts():
            for view in root.iter('TextView'):
                with self.subTest(layout=name, view=view.get(ANDROID + 'id')):
                    self.assertEqual('wrap_content', view.get(ANDROID + 'layout_height'))
                    self.assertEqual('false', view.get(ANDROID + 'includeFontPadding'))

    def test_state_words_cannot_consume_the_entire_header(self):
        for name, _, ids, _ in layouts():
            self.assertEqual('112dp' if '_expanded_' in name else '88dp',
                             ids['notification_state_label'].get(ANDROID + 'maxWidth'), name)

    def test_builder_hides_the_entire_unused_second_task_row(self):
        builder = source(APP / 'app/notifications/CalendarNotificationViews.kt')
        self.assertIn('setViewVisibility(R.id.notification_task_second_row, if (content.secondTaskTitle == null) View.GONE else View.VISIBLE)', builder)
        self.assertIn('setViewVisibility(R.id.notification_tasks_label, if (content.hasTask) View.VISIBLE else View.GONE)', builder)
        for name, _, ids, _ in layouts('expanded'):
            self.assertIn('notification_task_second_row', ids, name)

    def test_the_card_states_one_calendar_in_both_the_shade_and_the_preview(self):
        """No Gregorian/lunar line anywhere: not in the builder, not in the live preview.

        It was the line that always truncated ("قمری ۹ ج…"), and the card is meant to be read in
        two seconds rather than to carry a third calendar.
        """
        for path in (APP / 'app/notifications/CalendarNotificationViews.kt', APP / 'SettingsScreen.kt',
                     APP / 'app/notifications/PersistentCalendarNotification.kt'):
            text = source(path)
            self.assertNotIn('secondaryDatesLine', text, path.name)
            self.assertNotIn('notification_secondary_dates', text, path.name)
        for name, _, ids, _ in layouts():
            self.assertNotIn('notification_secondary_dates', ids, name)


class ResponsiveControlTests(unittest.TestCase):
    def test_habit_icon_options_wrap_instead_of_forcing_six_columns(self):
        code = source(FEATURE / 'habits/src/main/kotlin/com/marbledo/feature/habits/HabitComponents.kt')
        picker = code.split('fun HabitIconPicker(', 1)[1].split('fun HabitThemeSwatchRow(', 1)[0]
        self.assertIn('FlowRow(', picker)
        self.assertIn('.selectableGroup()', picker)
        self.assertIn('.size(48.dp)', picker)
        self.assertNotIn('chunked(6)', picker)
        self.assertIn('role = Role.RadioButton', picker)

    def test_task_editor_has_one_scroll_owner_and_persistent_save_actions(self):
        code = source(FEATURE / 'tasks/src/main/kotlin/com/marbledo/feature/tasks/TaskEditorSheet.kt')
        sheet = code.split('    ModalBottomSheet(', 1)[1].split('    if (showPicker)', 1)[0]
        self.assertEqual(1, sheet.count('.verticalScroll('))
        self.assertIn('.weight(1f, fill = false)', sheet)
        self.assertIn('            HorizontalDivider()\n            Row(', sheet)
        self.assertGreater(sheet.index('onSave('), sheet.index('            HorizontalDivider()'))
        self.assertIn('FlowRow(', sheet)

    def test_colour_options_have_named_selected_states_in_both_languages(self):
        code = source(FEATURE / 'tasks/src/main/kotlin/com/marbledo/feature/tasks/TaskEditorSheet.kt')
        self.assertIn('.semantics { contentDescription = colorLabel }', code)
        self.assertIn('.size(MarbleSpacing.touchTarget)', code)
        self.assertIn('role = Role.RadioButton', code)
        self.assertIn('Icons.Outlined.Check,', code)
        for values in ('values', 'values-fa'):
            strings = ET.parse(FEATURE / f'tasks/src/main/res/{values}/strings.xml').getroot()
            label = next(v for v in strings if v.get('name') == 'tasks_color_option')
            self.assertIn('%1$s', label.text)

    def test_label_free_countdown_swatches_are_still_named_48dp_options(self):
        code = source(FEATURE / 'countdown/src/main/kotlin/com/marbledo/feature/countdown/CountdownThemes.kt')
        swatch = code.split('fun CountdownThemeSwatch(', 1)[1].split('fun CountdownCaption(', 1)[0]
        self.assertIn('.sizeIn(minWidth = 48.dp, minHeight = 48.dp)', swatch)
        self.assertIn('if (!showLabel) contentDescription = label', swatch)
        self.assertIn('role = Role.RadioButton', swatch)

    def test_live_notification_preview_refreshes_on_resume_not_in_the_background(self):
        preview = source(APP / 'SettingsScreen.kt').split('private fun NotificationLivePreviewCard(', 1)[1]
        self.assertIn('lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED)', preview)
        self.assertIn('60_000L - current % 60_000L', preview)
        self.assertIn('content.accessibilityText(expanded = true)', preview)
