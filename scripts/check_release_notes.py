#!/usr/bin/env python3
"""Enforce the agent changelog contract before a GitHub Release is published.

Every agent must write a release's changelog ONLY in that version's own GitHub
Release notes, with emoji, and ONLY about the commits merged since the preceding
published stable release. This validator makes that contract fail closed: a
copied older changelog, a missing emoji, an entry from another version, or a
changelog written as prose in a file all stop the release workflow.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# Emoji used by release_plan.py (✨, 📝) and their Unicode blocks.
EMOJI = re.compile('[\u2600-\u27bf\U0001f000-\U0001faff]')
BULLET = re.compile(r'^- (\S+)\s+(.+)$')


def has_emoji(text):
    return bool(EMOJI.search(text))


def commit_subjects(previous, source):
    output = subprocess.check_output(
        ['git', 'log', '--first-parent', '--format=%s', f'{previous}..{source}'], text=True
    )
    return [line.strip() for line in output.splitlines() if line.strip()]


def validate(text, tag, previous, source):
    """Return a list of contract violations; an empty list means the notes pass."""
    errors = []
    lines = text.splitlines()
    if not lines:
        return ['release notes are empty']

    header = lines[0]
    if not header.startswith('## ') or not has_emoji(header) or tag not in header:
        errors.append(f'first line must be an emoji heading naming this version ({tag})')

    entries = []
    for number, line in enumerate(lines[1:], start=2):
        if not line.strip():
            continue
        match = BULLET.match(line)
        if not match or not has_emoji(match.group(1)):
            errors.append(f'line {number}: every entry must be "- <emoji> <commit subject>"')
            continue
        entries.append(match.group(2).strip())

    subjects = commit_subjects(previous, source)
    if sorted(entries) != sorted(subjects):
        expected = set(subjects)
        extra = sorted({entry for entry in entries if entry not in expected})
        missing = sorted({subject for subject in subjects if subject not in set(entries)})
        errors.append(
            'changelog is not scoped to this version: '
            f'{len(entries)} entry/entries vs {len(subjects)} merged commit(s); '
            f'unexpected={extra!r}; missing={missing!r}. '
            'Never copy or carry over an older release\'s changelog.'
        )
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--notes', required=True, type=Path, help='Path to the generated notes markdown')
    parser.add_argument('--tag', required=True, help='Version being released, e.g. v2.1.7')
    parser.add_argument('--previous', required=True, help='Preceding published stable release tag')
    parser.add_argument('--source', required=True, help='Commit being released')
    args = parser.parse_args()

    errors = validate(args.notes.read_text(encoding='utf-8'), args.tag, args.previous, args.source)
    for error in errors:
        print(f'::error title=Release changelog contract::{error}', file=sys.stderr)
    if errors:
        return 1
    count = len(commit_subjects(args.previous, args.source))
    print(f'Release notes for {args.tag} pass: {count} scoped, emoji-annotated entries.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
