#!/usr/bin/env python3
"""Plan a conservative release using published versions and first-parent history."""
import json
import os
import re
import subprocess
import sys

VERSION = re.compile(r'^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$')


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def plan(releases, source):
    candidates = []
    for release in releases:
        match = VERSION.fullmatch(release['tag_name'])
        if not match or release['draft'] or release['prerelease']:
            continue
        tag = release['tag_name']
        if subprocess.run(['git', 'merge-base', '--is-ancestor', tag, source]).returncode == 0:
            candidates.append((tuple(map(int, match.groups())), tag))
    if not candidates:
        raise ValueError('No published stable release tag is an ancestor of this commit')
    version, previous = max(candidates)
    messages = run('git', 'log', '--first-parent', '--format=%s', f'{previous}..{source}').splitlines()
    if not messages:
        raise ValueError('No changes since the previous release')
    # Opt-in only: most changes (including features) advance just the patch.
    bodies = run('git', 'log', '--first-parent', '--format=%B%x00', f'{previous}..{source}')
    if re.search(r'(?im)^release-major:\s*yes\s*$', bodies):
        version = (version[0] + 1, 0, 0)
    elif re.search(r'(?im)^release-minor:\s*yes\s*$', bodies):
        version = (version[0], version[1] + 1, 0)
    else:
        version = (version[0], version[1], version[2] + 1)
    tag = 'v' + '.'.join(map(str, version))
    notes = '## ✨ Changes in ' + tag + '\n\n' + '\n'.join('- 📝 ' + m for m in messages) + '\n'
    return tag, previous, notes


def main():
    releases = json.loads(run('gh', 'api', '--paginate', '--slurp', 'repos/{owner}/{repo}/releases?per_page=100'))
    tag, previous, notes = plan([item for page in releases for item in page], os.environ['GITHUB_SHA'])
    # Never overwrite a tag belonging to another commit, including unpublished tags.
    result = subprocess.run(['git', 'rev-parse', '-q', '--verify', f'refs/tags/{tag}^{{commit}}'], text=True, capture_output=True)
    if result.returncode == 0 and result.stdout.strip() != run('git', 'rev-parse', os.environ['GITHUB_SHA']):
        raise ValueError(f'{tag} already points to a different commit')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'tag={tag}\nversion={tag[1:]}\nprevious={previous}\n')
    with open(os.environ['RUNNER_TEMP'] + '/release-notes.md', 'w') as output:
        output.write(notes)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f'Release planning failed: {error}', file=sys.stderr)
        sys.exit(1)
