#!/usr/bin/env python3
"""Plan a conservative, self-deciding release from published versions and history.

The release automation owns version numbers. Every merge into main produces
exactly one new release, and this script decides by itself how far the version
moves. It deliberately advances slowly: patch is the norm even for features,
a minor needs accumulated feature evidence *and* a cool-down since the last
non-patch step, and a major needs a declared breaking change *and* a longer
cool-down. See docs/RELEASE_POLICY.md.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import re
import subprocess
import sys

VERSION = re.compile(r'^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$')
MAJOR_TRAILER = re.compile(r'(?im)^release-major:\s*yes\s*$')
MINOR_TRAILER = re.compile(r'(?im)^release-minor:\s*yes\s*$')
# Deliberate breaking-change declarations in a first-parent commit message:
# "BREAKING CHANGE: ..." / "BREAKING-CHANGE: ..." body lines, or a conventional
# "feat(api)!: ..." subject. A bare mention of "breaking" is not enough.
BREAKING_DECLARATION = re.compile(
    r'(?im)^\s*breaking[-\s]?change\s*:|(?:^|\n)\s*[a-z]+(?:\([^)]*\))?!\s*:'
)
# Feature evidence in a merge/subject line. Kept intentionally broad enough for
# the repository's mixed commit style, but a single hit never earns a minor.
FEATURE_SIGNAL = re.compile(
    r'(?i)(?:\bfeat(?:ure)?(?:\([^)]*\))?\s*:'
    r'|\b(?:add|adds|added|new|introduce[sd]?|implement(?:s|ed)?|enable[sd]?)\b)'
)

# Slow-progression guardrails. Both a volume/evidence test and a cool-down must
# pass before the automation is allowed to move beyond a patch on its own.
MIN_FEATURE_SIGNALS = 3
MIN_CHANGED_LINES = 150
MIN_CHANGED_FILES = 8
PATCH_STREAK_FOR_MINOR = 5
DAYS_FOR_MINOR = 14
PATCH_STREAK_FOR_MAJOR = 10
DAYS_FOR_MAJOR = 30


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def try_run(*args, default=''):
    try:
        return run(*args)
    except subprocess.CalledProcessError:
        return default


def published_date(release):
    value = release.get('published_at') or release.get('created_at') or ''
    try:
        parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=dt.timezone.utc)
    except (ValueError, AttributeError):
        return None


def step_kind(previous, current):
    """Classify a version transition between two published stable releases."""
    if current[0] > previous[0]:
        return 'major'
    if current[0] == previous[0] and current[1] > previous[1]:
        return 'minor'
    if current[:2] == previous[:2] and current[2] > previous[2]:
        return 'patch'
    return None


def cool_down(history, level, now=None):
    """Return (allowed, explanation) for moving beyond a patch automatically.

    `history` is [(version_tuple, published_datetime|None)] for every published
    stable ancestor release. Requiring several patch releases (or a time gap)
    since the last comparable step is what keeps versions from racing ahead.
    """
    versions = sorted(version for version, _ in history)
    dates = {version: date for version, date in history}
    targets = ('major',) if level == 'major' else ('minor', 'major')
    required_streak = PATCH_STREAK_FOR_MAJOR if level == 'major' else PATCH_STREAK_FOR_MINOR
    required_days = DAYS_FOR_MAJOR if level == 'major' else DAYS_FOR_MINOR

    last_step_index = None
    for index in range(len(versions) - 1, 0, -1):
        if step_kind(versions[index - 1], versions[index]) in targets:
            last_step_index = index
            break

    # Number of published releases released after the last qualifying step. When
    # no such step is visible, every observed transition was a patch.
    streak = len(versions) - 1 - (last_step_index if last_step_index is not None else 0)
    stamp = dates.get(versions[last_step_index]) if last_step_index is not None else None
    today = now or dt.datetime.now(dt.timezone.utc)
    days = (today - stamp).days if stamp is not None else None
    allowed = streak >= required_streak or (days is not None and days >= required_days)
    explanation = (
        f'{streak} release(s) since the last {"major" if level == "major" else "non-patch"} step '
        f'(need {required_streak}), '
        f'{days if days is not None else "unknown"} day(s) elapsed (need {required_days})'
    )
    return allowed, explanation


def diff_stats(previous, source):
    """Changed lines and files between the previous release and this commit."""
    lines = files = 0
    for row in try_run('git', 'diff', '--numstat', f'{previous}..{source}').splitlines():
        columns = row.split('\t')
        if len(columns) < 3:
            continue
        files += 1
        for column in columns[:2]:
            if column.isdigit():
                lines += int(column)
    return lines, files


def decide(messages, bodies, stats, history, now=None):
    """Choose the smallest version step justified by the merged changes."""
    # Maintainers can deliberately override the automatic decision. These
    # trailers are rare and always opt-in, on their own line in a first-parent
    # commit message; agents must not use them for routine changes.
    if MAJOR_TRAILER.search(bodies):
        return 'major', 'explicit Release-Major: yes trailer in a merged commit'
    if MINOR_TRAILER.search(bodies):
        return 'minor', 'explicit Release-Minor: yes trailer in a merged commit'

    lines, files = stats
    if BREAKING_DECLARATION.search(bodies):
        allowed, why = cool_down(history, 'major', now)
        if allowed:
            return 'major', f'declared breaking change; major cool-down satisfied ({why})'
        return 'minor', f'declared breaking change but major cool-down not met ({why}); holding at minor'

    signals = sum(1 for message in messages if FEATURE_SIGNAL.search(message))
    volume = int(lines >= MIN_CHANGED_LINES) + int(files >= MIN_CHANGED_FILES)
    evidence = (
        f'{signals} feature signal(s) in {len(messages)} merge(s), '
        f'{lines} changed line(s) in {files} file(s)'
    )
    if signals >= MIN_FEATURE_SIGNALS and volume >= 1:
        allowed, why = cool_down(history, 'minor', now)
        if allowed:
            return 'minor', f'accumulated feature work ({evidence}); minor cool-down satisfied ({why})'
        return 'patch', f'accumulated feature work ({evidence}), but minor cool-down not met ({why})'
    return 'patch', f'routine changes ({evidence}); patch is the smallest justified step'


def plan(releases, source, decision=None):
    """Return (tag, previous_tag, emoji notes) for one release of `source`."""
    stable = []
    for release in releases:
        match = VERSION.fullmatch(release.get('tag_name', ''))
        if not match or release.get('draft') or release.get('prerelease'):
            continue
        tag = release['tag_name']
        if subprocess.run(['git', 'merge-base', '--is-ancestor', tag, source]).returncode == 0:
            stable.append((tuple(map(int, match.groups())), tag, published_date(release)))
    if not stable:
        raise ValueError('No published stable release tag is an ancestor of this commit')

    version, previous, _ = max(stable, key=lambda item: item[0])
    range_spec = f'{previous}..{source}'
    messages = run('git', 'log', '--first-parent', '--format=%s', range_spec).splitlines()
    if not messages:
        raise ValueError('No changes since the previous release')
    bodies = run('git', 'log', '--first-parent', '--format=%B%x00', range_spec)
    bump, reason = decide(messages, bodies, diff_stats(previous, source),
                          [(item[0], item[2]) for item in stable])

    if bump == 'major':
        version = (version[0] + 1, 0, 0)
    elif bump == 'minor':
        version = (version[0], version[1] + 1, 0)
    else:
        version = (version[0], version[1], version[2] + 1)
    tag = 'v' + '.'.join(map(str, version))
    # Agents' contract: only this version's first-parent commits, with emoji,
    # in this version's GitHub Release. Enforced by check_release_notes.py.
    notes = '## ✨ Changes in ' + tag + '\n\n' + '\n'.join('- 📝 ' + m for m in messages) + '\n'
    if decision is not None:
        decision.update(tag=tag, previous=previous, bump=bump, reason=reason,
                        commits=len(messages))
    return tag, previous, notes


def main():
    releases = json.loads(run('gh', 'api', '--paginate', '--slurp', 'repos/{owner}/{repo}/releases?per_page=100'))
    decision = {}
    tag, previous, notes = plan([item for page in releases for item in page],
                                os.environ['GITHUB_SHA'], decision)
    # Never overwrite a tag belonging to another commit, including unpublished tags.
    result = subprocess.run(['git', 'rev-parse', '-q', '--verify', f'refs/tags/{tag}^{{commit}}'],
                            text=True, capture_output=True)
    if result.returncode == 0 and result.stdout.strip() != run('git', 'rev-parse', os.environ['GITHUB_SHA']):
        raise ValueError(f'{tag} already points to a different commit')
    temp = os.environ['RUNNER_TEMP']
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'tag={tag}\nversion={tag[1:]}\nprevious={previous}\nbump={decision["bump"]}\n')
    with open(temp + '/release-notes.md', 'w') as output:
        output.write(notes)
    with open(temp + '/version-decision.md', 'w') as output:
        output.write(
            '### 🧭 Version decision (chosen by the release automation)\n\n'
            f'- `{previous}` → `{tag}` (**{decision["bump"]}**), '
            f'{decision["commits"]} merged commit(s) in this release\n'
            f'- {decision["reason"]}\n'
            '- Policy: patch by default; minor/major require evidence and a cool-down, '
            'so versions advance slowly (docs/RELEASE_POLICY.md).\n'
        )
    print(f'Planned {tag} ({decision["bump"]}): {decision["reason"]}')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f'Release planning failed: {error}', file=sys.stderr)
        sys.exit(1)
