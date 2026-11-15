import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('check_release_notes', ROOT / 'scripts' / 'check_release_notes.py')
notes_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes_check)


class ReleaseNotesContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old = Path.cwd()
        import os
        os.chdir(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'test@example.com')
        self.git('config', 'user.name', 'Test')
        self.git('commit', '--allow-empty', '-qm', 'initial')
        self.git('tag', 'v2.1.6')
        self.git('commit', '--allow-empty', '-qm', 'fix: keep the counter steady')
        self.git('commit', '--allow-empty', '-qm', 'chore: tighten the release policy')
        self.previous = 'v2.1.6'
        self.tag = 'v2.1.7'
        subjects = notes_check.commit_subjects(self.previous, 'HEAD')
        self.notes = '## ✨ Changes in v2.1.7\n\n' + '\n'.join(f'- 📝 {item}' for item in subjects) + '\n'

    def tearDown(self):
        import os
        os.chdir(self.old)
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', *args], text=True).strip()

    def test_scoped_emoji_notes_pass(self):
        self.assertEqual(notes_check.validate(self.notes, self.tag, self.previous, 'HEAD'), [])

    def test_copied_old_changelog_is_rejected(self):
        copied = self.notes + '- 📝 old change from v2.1.6\n'
        errors = notes_check.validate(copied, self.tag, self.previous, 'HEAD')
        self.assertTrue(any('not scoped' in error for error in errors))

    def test_missing_entry_is_rejected(self):
        shortened = '\n'.join(self.notes.splitlines()[:-1]) + '\n'
        errors = notes_check.validate(shortened, self.tag, self.previous, 'HEAD')
        self.assertTrue(any('not scoped' in error for error in errors))

    def test_plain_bullets_without_emoji_are_rejected(self):
        plain = self.notes.replace('- 📝 ', '- ')
        errors = notes_check.validate(plain, self.tag, self.previous, 'HEAD')
        self.assertTrue(any('every entry must be' in error for error in errors))

    def test_wrong_version_heading_is_rejected(self):
        wrong = self.notes.replace('v2.1.7', 'v2.1.6')
        errors = notes_check.validate(wrong, self.tag, self.previous, 'HEAD')
        self.assertTrue(any('emoji heading naming this version' in error for error in errors))

    def test_older_changelog_prose_is_rejected(self):
        prose = '## Changes\n\nThis release contains everything from v2.1.0 through v2.1.7.\n'
        errors = notes_check.validate(prose, self.tag, self.previous, 'HEAD')
        self.assertGreaterEqual(len(errors), 2)

    def test_cli_reports_success(self):
        path = Path(self.temp.name) / 'notes.md'
        path.write_text(self.notes)
        result = subprocess.run(
            ['python3', str(ROOT / 'scripts' / 'check_release_notes.py'),
             '--notes', str(path), '--tag', self.tag, '--previous', self.previous, '--source', 'HEAD'],
            text=True, capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('scoped, emoji-annotated entries', result.stdout)


if __name__ == '__main__':
    unittest.main()
