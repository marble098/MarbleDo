# Release instructions for all agents

- Every release's notes must describe only changes since the preceding published release, with emoji. Never copy an older release's changelog into a new release.
- Release automation owns versions: use a patch increment by default, even for features. Only add `Release-Minor: yes` or `Release-Major: yes` on its own line in a first-parent commit message when a genuinely significant change warrants it. Do not bump versions in the repository for routine changes.
- Preserve signed APKs for armeabi-v7a, arm64-v8a, x86, x86_64 and a universal APK, the AAB, checksums, and latest-release publication.
