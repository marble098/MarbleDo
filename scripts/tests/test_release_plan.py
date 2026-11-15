import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('release_plan', Path(__file__).resolve().parents[1] / 'release_plan.py')
release_plan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_plan)


class ReleasePlanTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old = Path.cwd()
        import os
        os.chdir(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'test@example.com')
        self.git('config', 'user.name', 'Test')
        self.commit('initial')
        self.git('tag', 'v2.1.6')
        self.releases = [{'tag_name': 'v2.1.6', 'draft': False, 'prerelease': False}]

    def tearDown(self):
        import os
        os.chdir(self.old)
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', *args], text=True).strip()

    def commit(self, message):
        self.git('commit', '--allow-empty', '-qm', message)

    def test_patch_notes_are_current_only(self):
        self.commit('feat: new feature')
        tag, previous, notes = release_plan.plan(self.releases, 'HEAD')
        self.assertEqual((tag, previous), ('v2.1.7', 'v2.1.6'))
        self.assertIn('📝 feat: new feature', notes)
        self.assertNotIn('initial', notes)

    def test_explicit_minor_and_major(self):
        self.commit('Big change\n\nRelease-Minor: yes')
        self.assertEqual(release_plan.plan(self.releases, 'HEAD')[0], 'v2.2.0')
        self.commit('Breaking\n\nRelease-Major: yes')
        self.assertEqual(release_plan.plan(self.releases, 'HEAD')[0], 'v3.0.0')

    def test_no_changes_or_stable_ancestor(self):
        with self.assertRaises(ValueError):
            release_plan.plan(self.releases, 'HEAD')
        with self.assertRaises(ValueError):
            release_plan.plan([], 'HEAD')


if __name__ == '__main__':
    unittest.main()
