"""Guard the release contract that all agents and workflows must keep."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ABIS = ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')


class ReleasePolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.agents = (ROOT / 'AGENTS.md').read_text(encoding='utf-8')
        cls.workflow = (ROOT / '.github' / 'workflows' / 'release.yml').read_text(encoding='utf-8')
        cls.build_workflow = (ROOT / '.github' / 'workflows' / 'build.yml').read_text(encoding='utf-8')
        cls.gradle = (ROOT / 'app' / 'build.gradle.kts').read_text(encoding='utf-8')
        cls.policy = (ROOT / 'docs' / 'RELEASE_POLICY.md').read_text(encoding='utf-8')

    def test_all_agents_are_bound_by_the_changelog_contract(self):
        agents = self.agents.lower()
        for phrase in ('all agents', 'must', 'only', 'emoji', 'github release',
                       'changelog', 'older release', 'latest', 'apk'):
            self.assertIn(phrase, agents, f'AGENTS.md must state: {phrase}')

    def test_policy_is_documented(self):
        for phrase in ('patch', 'minor', 'major', 'cool-down', 'latest', 'changelog'):
            self.assertIn(phrase, self.policy.lower())

    def test_release_runs_on_every_merge_to_main(self):
        self.assertIn('branches: [main]', self.workflow)
        self.assertIn('github.ref == \'refs/heads/main\'', self.workflow)

    def test_every_abi_and_universal_apk_is_required(self):
        for abi in ABIS + ('universal',):
            self.assertIn(f'app-{abi}-release.apk', self.workflow)
        for abi in ABIS:
            self.assertIn(f'"{abi}"', self.gradle)
        self.assertIn('isUniversalApk = true', self.gradle)

    def test_bundle_and_split_apks_are_built_in_separate_invocations(self):
        # AGP 9 refuses an AAB and ABI-split APKs in one Gradle invocation
        # (issuetracker 402800800). Keep the two invocations distinct.
        self.assertIn('buildsBundle', self.gradle)
        self.assertIn('startParameter.taskNames', self.gradle)
        self.assertIn(':app:bundleRelease', self.workflow)
        self.assertIn(':app:assembleRelease', self.workflow)
        bundle_line = next(line for line in self.workflow.splitlines() if ':app:bundleRelease' in line)
        self.assertNotIn(':app:assembleRelease', bundle_line)

    def test_release_is_published_as_latest_with_scoped_notes(self):
        self.assertIn('--latest', self.workflow)
        self.assertIn('--notes-file', self.workflow)
        self.assertIn('scripts/check_release_notes.py', self.workflow)
        self.assertIn('releases/latest', self.workflow)
        self.assertIn('SHA256SUMS.txt', self.workflow)

    def test_pull_request_ci_verifies_the_release_build(self):
        self.assertIn(':app:bundleRelease', self.build_workflow)
        self.assertIn(':app:assembleRelease', self.build_workflow)
        self.assertIn('for abi in universal armeabi-v7a arm64-v8a x86 x86_64', self.build_workflow)
        self.assertIn('app-${abi}-release*.apk', self.build_workflow)
        self.assertIn('bundle/release/*.aab', self.build_workflow)


if __name__ == '__main__':
    unittest.main()
