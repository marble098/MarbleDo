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
