import datetime as dt
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

    def change(self, message, lines=200):
        """A real source change, so the planner can weigh the diff volume."""
        # Include the message so repeated changes are never empty commits.
        Path('feature.txt').write_text(f'// {message}\n' + '\n'.join(f'line {i}' for i in range(lines)) + '\n')
        self.git('add', 'feature.txt')
        self.git('commit', '-qm', message)

    def history(self, versions, start='2026-01-01'):
        """Synthetic published history with one release per day."""
        base = dt.datetime.fromisoformat(start).replace(tzinfo=dt.timezone.utc)
        return [(version, base + dt.timedelta(days=index)) for index, version in enumerate(versions)]

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

    def test_single_feature_is_never_a_minor_on_its_own(self):
        self.change('feat: a brand new planner')
        decision = {}
        tag, _previous, _notes = release_plan.plan(self.releases, 'HEAD', decision)
        self.assertEqual(tag, 'v2.1.7')
        self.assertEqual(decision['bump'], 'patch')
        self.assertIn('patch is the smallest justified step', decision['reason'])

    def test_automatic_minor_waits_for_a_cool_down(self):
        for index in range(3):
            self.change(f'feat: capability {index}')
        decision = {}
        tag, _previous, _notes = release_plan.plan(self.releases, 'HEAD', decision)
        # Plenty of feature evidence, but only one release in the published
        # history: the automation must not race the version forward.
        self.assertEqual(tag, 'v2.1.7')
        self.assertIn('minor cool-down not met', decision['reason'])

    def test_automatic_minor_after_a_slow_run_of_patches(self):
        self.git('tag', '-d', 'v2.1.6')
        versions = ['v2.0.9', 'v2.1.0', 'v2.1.1', 'v2.1.2', 'v2.1.3', 'v2.1.4', 'v2.1.5', 'v2.1.6']
        for version in versions:
            self.commit(f'release {version}')
            self.git('tag', version)
        for index in range(3):
            self.change(f'feat: capability {index}')
        releases = [{'tag_name': version, 'draft': False, 'prerelease': False} for version in versions]
        decision = {}
        tag, previous, _notes = release_plan.plan(releases, 'HEAD', decision)
        self.assertEqual(previous, 'v2.1.6')
        self.assertEqual(tag, 'v2.2.0')
        self.assertEqual(decision['bump'], 'minor')
        self.assertIn('cool-down satisfied', decision['reason'])

    def test_declared_breaking_change_is_rate_limited(self):
        bodies = 'Remove the legacy export format\n\nBREAKING CHANGE: exports are now encrypted\n'
        messages = ['Remove the legacy export format']
        short = [((2, 1, 6), None)]
        long_history = self.history([(2, 0, i) for i in range(12)] + [(2, 1, i) for i in range(7)])
        now = dt.datetime(2026, 11, 15, tzinfo=dt.timezone.utc)
        self.assertEqual(release_plan.decide(messages, bodies, (400, 12), short, now)[0], 'minor')
        self.assertEqual(release_plan.decide(messages, bodies, (400, 12), long_history, now)[0], 'major')
        # A bare mention of "breaking" without a declaration stays a patch.
        self.assertEqual(
            release_plan.decide(['fix: breaking crash', 'docs: notes', 'chore: tidy'],
                                'nothing declared', (10, 1), short, now)[0], 'patch')

    def test_cool_down_requires_several_patches_or_time(self):
        now = dt.datetime(2026, 3, 1, tzinfo=dt.timezone.utc)
        # v2.1.0 is the last non-patch step; the rest are patches.
        versions = [(2, 0, 9), (2, 1, 0), (2, 1, 1), (2, 1, 2)]
        recent = self.history(versions, start='2026-02-20')
        self.assertFalse(release_plan.cool_down(recent, 'minor', now)[0])
        older = self.history(versions, start='2026-01-01')
        self.assertTrue(release_plan.cool_down(older, 'minor', now)[0])
        many = self.history(versions + [(2, 1, patch) for patch in range(3, 9)], start='2026-02-20')
        self.assertTrue(release_plan.cool_down(many, 'minor', now)[0])

    def test_notes_and_decision_are_version_scoped(self):
        self.change('chore: tighten the release policy')
        decision = {}
        tag, previous, notes = release_plan.plan(self.releases, 'HEAD', decision)
        self.assertEqual((decision['tag'], decision['previous']), (tag, previous))
        self.assertEqual(decision['commits'], 1)
        self.assertTrue(notes.startswith('## ✨ Changes in ' + tag))
        self.assertNotIn('initial', notes)


if __name__ == '__main__':
    unittest.main()
