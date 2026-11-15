# Release instructions — binding for all agents

**All agents working on this repository MUST follow these rules.** They are
enforced in CI by `scripts/check_release_notes.py`,
`scripts/tests/test_release_policy.py`, and the release workflow. See
[docs/RELEASE_POLICY.md](docs/RELEASE_POLICY.md) for the full policy.

1. **The automation owns version numbers — it decides the step itself, slowly.**
   Every merge into `main` produces exactly one new release. `scripts/release_plan.py`
   reads the merged first-parent commits, the diff volume, and the published
   release history and chooses the smallest justified step on its own: patch by
   default (even for features), minor only after accumulated feature evidence
   plus a cool-down, major only for a declared breaking change plus a longer
   cool-down. Never bump `appVersionName`/`appVersionCode` or version-catalog
   pins to "make" a release, never create tags or GitHub Releases by hand, and
   never guess a version number in code, docs, or commit messages. The only
   human override is `Release-Minor: yes` or `Release-Major: yes` on its own
   line in a first-parent commit message, reserved for genuinely significant
   changes; do not add it to routine changes.

2. **Changelog: write ONLY that version's changes, with emoji, in that
   version's own GitHub Release.** The release notes published with version
   `vX.Y.Z` must contain only the first-parent commits merged since the
   preceding published stable release, one emoji-annotated line each. Never
   copy, repeat, summarize, or carry over an older release's changelog; never
   add changelog entries to README, a `CHANGELOG.md`, commit messages, or any
   other file; never write one version's changelog into another version's
   Release; and never edit the notes of an already published release. An agent
   that needs a changelog must put it in that version's GitHub Release notes
   only.

3. **Artifacts: every release must ship all architectures and be latest.**
   Preserve signed APKs for `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` and a
   universal APK, the AAB, SHA-256 checksums, and `--latest` publication.
   Never remove an ABI split, disable the universal APK, drop the AAB or
   checksums, or publish without `--latest`. Because AGP 9 cannot build an AAB
   and ABI-split APKs in one Gradle invocation, keep `:app:bundleRelease` and
   `:app:assembleRelease` as separate invocations (the bundle first) and keep
   `app/build.gradle.kts`'s `buildsBundle` guard.

4. **Verify before merging.** Pull-request CI must pass, including the release
   build check, before a change to release automation is merged.
