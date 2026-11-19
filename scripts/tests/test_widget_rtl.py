"""Guards for the widget right-to-left contract.

Glance lays its rows out in the *device* direction, but a Persian user who picked Persian
inside the app has to get a right-to-left widget even on an English phone. These rules keep
the four pieces of that contract in place: a locale-bound context, an explicit direction
flag, a mirrored body alignment, and a row helper that reverses its children.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

WIDGETS = {
    'habits': ROOT / 'feature/habits/src/main/kotlin/com/marbledo/feature/habits/MarbleHabitWidget.kt',
    'countdown': ROOT / 'feature/countdown/src/main/kotlin/com/marbledo/feature/countdown/MarbleCountdownWidget.kt',
}


class WidgetRtlTests(unittest.TestCase):
    def test_both_widgets_follow_the_app_language_not_the_device_language(self):
        for name, path in WIDGETS.items():
            with self.subTest(widget=name):
                source = path.read_text(encoding='utf-8')
                self.assertIn('val isRtl = settings.languageTag == "fa"', source)
                self.assertIn(
                    'val bodyAlignment = if (isRtl) TextAlign.End else TextAlign.Start',
                    source,
                )
                self.assertIn('context.forLanguage(settings.languageTag)', source)

    def test_the_locale_context_also_sets_the_layout_direction(self):
        for name, path in WIDGETS.items():
            with self.subTest(widget=name):
                source = path.read_text(encoding='utf-8')
                helper = source[source.index('private fun Context.forLanguage('):]
                self.assertIn('setLocale(locale)', helper)
                self.assertIn('setLayoutDirection(locale)', helper)
                self.assertIn('createConfigurationContext(configuration)', helper)

    def test_rows_mirror_their_children_in_rtl(self):
        for name, path in WIDGETS.items():
            with self.subTest(widget=name):
                source = path.read_text(encoding='utf-8')
                self.assertIn(
                    '(if (isRtl) items.asReversed() else items).forEach { item -> item() }',
                    source,
                )
                self.assertGreaterEqual(source.count('directionalRow('), 2)

    def test_every_widget_text_states_its_alignment(self):
        """A Text without textAlign silently falls back to the device direction."""
        for name, path in WIDGETS.items():
            with self.subTest(widget=name):
                source = path.read_text(encoding='utf-8')
                texts = re.findall(r'\bText\(', source)
                alignments = re.findall(r'textAlign\s*=', source)
                self.assertEqual(
                    len(texts),
                    len(alignments),
                    f'{name}: {len(texts)} Text composables but {len(alignments)} alignments',
                )

    def test_justified_body_text_fills_the_width_it_is_justified_in(self):
        """textAlign only bites when the Text is wider than its content."""
        for name, path in WIDGETS.items():
            with self.subTest(widget=name):
                source = path.read_text(encoding='utf-8')
                # Every multi-line body Text has to be fillMaxWidth so the second line
                # really does start at the right edge in Persian.
                for pattern in ('maxLines = 2', 'maxLines = 3'):
                    for match in re.finditer(re.escape(pattern), source):
                        start = source.rfind('Text(', 0, match.start())
                        window = source[start:match.end()]
                        self.assertIn('fillMaxWidth', window, window[:160])


class WidgetRedesignTests(unittest.TestCase):
    """The widgets read as one card with one job, not as a stack of cards with labels."""

    def test_the_planner_widget_has_one_filled_surface(self):
        source = WIDGETS['countdown'].read_text(encoding='utf-8')
        # Exactly one inner card: the countdown. The header is a date, the two meta lines are
        # sentences. The radius moved from 22 dp to 18 dp so it sits concentrically inside the
        # 26 dp plate at its 4 dp inset; what must not change is that there is only one card.
        self.assertEqual(1, source.count('cornerRadius(18.dp)'), 'the widget grew a second card')
        self.assertNotIn('GlanceInfoCard(', source, 'the occasions/next-task cards came back')
        self.assertIn('R.string.widget_meta_occasions', source)
        self.assertIn('R.string.widget_meta_next', source)
        # The upper-case section headers were the loudest thing on the old card.
        self.assertNotIn('R.string.widget_occasions_label', source)
        self.assertNotIn('R.string.widget_next_task_label', source)

    def test_the_habit_widget_states_its_score_once(self):
        source = WIDGETS['habits'].read_text(encoding='utf-8')
        self.assertIn('R.string.habit_widget_score', source)
        self.assertIn('R.string.habit_widget_more', source)
        # The footer that counted the same habits again in another denominator is gone.
        self.assertNotIn('R.string.habit_widget_progress', source)

    def test_every_widget_string_is_translated(self):
        """A widget follows the app language; a missing Persian string silently falls back."""
        modules = {
            'countdown': (
                WIDGETS['countdown'],
                ROOT / 'feature/countdown/src/main/res/values/widget_strings.xml',
                ROOT / 'feature/countdown/src/main/res/values-fa/widget_strings.xml',
            ),
            'habits': (
                WIDGETS['habits'],
                ROOT / 'feature/habits/src/main/res/values/strings.xml',
                ROOT / 'feature/habits/src/main/res/values-fa/strings.xml',
            ),
        }
        for name, (source_path, english, persian) in modules.items():
            used = set(re.findall(r'R\.string\.(\w+)', source_path.read_text(encoding='utf-8')))
            for path in (english, persian):
                declared = set(re.findall(r'name="(\w+)"', path.read_text(encoding='utf-8')))
                missing = used - declared
                with self.subTest(widget=name, file=path.name):
                    self.assertFalse(missing, f'{name}: {sorted(missing)} missing from {path}')
