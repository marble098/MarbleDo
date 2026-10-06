#!/usr/bin/env python3
"""Refresh Gradle version-catalog pins from official repository metadata.

By default published alpha, beta, RC, preview, canary, milestone, and EAP releases
are eligible; nightlies and snapshots are excluded. Pass --stable-only to exclude
all recognized pre-releases. No version is inferred when official metadata cannot be fetched.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
GOOGLE_MAVEN = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo.maven.apache.org/maven2"

# key -> (group, artifact, official repository)
ARTIFACTS: dict[str, tuple[str, str, str]] = {
    "agp": ("com.android.tools.build", "gradle", "google"),
    "kotlin": ("org.jetbrains.kotlin", "kotlin-gradle-plugin", "central"),
    "ksp": ("com.google.devtools.ksp", "symbol-processing-gradle-plugin", "central"),
    "composeBom": ("androidx.compose", "compose-bom-alpha", "google"),
    "material3": ("androidx.compose.material3", "material3", "google"),
    "activity": ("androidx.activity", "activity-compose", "google"),
    "navigation3": ("androidx.navigation3", "navigation3-runtime", "google"),
    "lifecycle": ("androidx.lifecycle", "lifecycle-runtime-compose", "google"),
    "core": ("androidx.core", "core-ktx", "google"),
    "appcompat": ("androidx.appcompat", "appcompat", "google"),
    "splash": ("androidx.core", "core-splashscreen", "google"),
    "room": ("androidx.room", "room-runtime", "google"),
    "datastore": ("androidx.datastore", "datastore", "google"),
    "work": ("androidx.work", "work-runtime-ktx", "google"),
    "glance": ("androidx.glance", "glance-appwidget", "google"),
    "koin": ("io.insert-koin", "koin-android", "central"),
    "coroutines": ("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "central"),
    "serialization": ("org.jetbrains.kotlinx", "kotlinx-serialization-json", "central"),
    "datetime": ("org.jetbrains.kotlinx", "kotlinx-datetime", "central"),
    "protobufPlugin": ("com.google.protobuf", "protobuf-gradle-plugin", "central"),
    "protobuf": ("com.google.protobuf", "protobuf-java", "central"),
    "junit": ("org.junit.jupiter", "junit-jupiter", "central"),
    "turbine": ("app.cash.turbine", "turbine", "central"),
    "androidxTestJunit": ("androidx.test.ext", "junit", "google"),
    "androidxTestRunner": ("androidx.test", "runner", "google"),
    "baselineprofile": ("androidx.benchmark", "benchmark-macro-junit4", "google"),
}

PRERELEASE = re.compile(r"(?:^|[-.+])(alpha|beta|rc|preview|canary|dev|snapshot|eap|milestone|m\d)(?:[-.+]?\d*)?(?:$|[-.+])", re.I)
NON_RELEASE = re.compile(r"(?:^|[-.+])(snapshot|dev|nightly)(?:[-.+]?\d*)?(?:$|[-.+])", re.I)
VERSION_PARTS = re.compile(r"^(\d+(?:\.\d+)*)(?:[-.]?([A-Za-z]+)(?:[-.]?(\d+))?)?.*$")
QUALIFIER_ORDER = {
    "snapshot": 0, "dev": 0, "eap": 0, "preview": 0, "milestone": 0, "m": 0,
    "alpha": 1, "beta": 2, "rc": 3,
}


def prerelease(version: str) -> bool:
    return bool(PRERELEASE.search(version))


def version_key(version: str) -> tuple[tuple[int, ...], int, int, str]:
    match = VERSION_PARTS.match(version)
    if not match:
        return (), -1, -1, version.lower()
    numbers = tuple(int(part) for part in match.group(1).split("."))
    qualifier = (match.group(2) or "").lower()
    rank = 4 if not qualifier else QUALIFIER_ORDER.get(re.sub(r"\d+$", "", qualifier), 1)
    qualifier_number = int(match.group(3) or 0)
    return numbers, rank, qualifier_number, qualifier


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "MarbleDo-Version-Auditor/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def maven_versions(group: str, artifact: str, repository: str) -> list[str]:
    base = GOOGLE_MAVEN if repository == "google" else CENTRAL
    path = group.replace(".", "/")
    url = f"{base}/{path}/{artifact}/maven-metadata.xml"
    root = ET.fromstring(fetch(url))
    return [item.text.strip() for item in root.findall("./versioning/versions/version") if item.text and item.text.strip()]


def gradle_versions() -> list[str]:
    payload = json.loads(fetch("https://services.gradle.org/versions/all"))
    return [
        item["version"] for item in payload
        if item.get("version") and not item.get("snapshot") and not item.get("nightly") and not item.get("broken")
    ]


def read_catalog() -> tuple[str, dict]:
    text = CATALOG.read_text(encoding="utf-8")
    return text, tomllib.loads(text)


def replace_version(text: str, key: str, new_value: str) -> str:
    start = text.index("[versions]")
    next_table = text.find("\n[", start + 1)
    if next_table < 0:
        next_table = len(text)
    prefix, block, suffix = text[:start], text[start:next_table], text[next_table:]
    pattern = re.compile(rf"(?m)^(\s*{re.escape(key)}\s*=\s*)\"[^\"]+\"(\s*(?:#.*)?)$")
    updated, count = pattern.subn(lambda match: f'{match.group(1)}"{new_value}"{match.group(2)}', block, count=1)
    if count != 1:
        raise ValueError(f"Could not update [versions].{key}; expected exactly one catalog entry")
    return prefix + updated + suffix


def replace_wrapper_distribution(text: str, gradle_version: str) -> str:
    url = f"https\\://services.gradle.org/distributions/gradle-{gradle_version}-bin.zip"
    updated, count = re.subn(r"(?m)^distributionUrl=.*$", f"distributionUrl={url}", text, count=1)
    if count != 1:
        raise ValueError("Could not update Gradle distributionUrl in gradle-wrapper.properties")
    return updated


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stable-only", action="store_true", help="Ignore alpha/beta/RC/preview/canary/milestone/EAP releases")
    parser.add_argument("--check", action="store_true", help="Report available updates without editing the catalog")
    args = parser.parse_args()

    text, catalog = read_catalog()
    current = catalog["versions"]
    candidates: dict[str, str] = {}
    failures: list[str] = []

    try:
        gradle_list = gradle_versions()
        allowed = [version for version in gradle_list if not args.stable_only or not prerelease(version)]
        if not allowed:
            raise RuntimeError("Official Gradle service returned no eligible versions")
        candidates["gradle"] = max(allowed, key=version_key)
    except Exception as error:  # noqa: BLE001 - report network, XML, and schema errors uniformly
        failures.append(f"gradle: {error}")

    for key, (group, artifact, repository) in ARTIFACTS.items():
        if key not in current:
            failures.append(f"{key}: version key is missing from the catalog")
            continue
        try:
            versions = maven_versions(group, artifact, repository)
            eligible = [
                version for version in versions
                if not NON_RELEASE.search(version) and (not args.stable_only or not prerelease(version))
            ]
            if not eligible:
                raise RuntimeError("official metadata contains no eligible versions")
            candidates[key] = max(eligible, key=version_key)
        except (urllib.error.URLError, ET.ParseError, OSError, RuntimeError, ValueError) as error:
            failures.append(f"{key} ({group}:{artifact}): {error}")

    changed: list[tuple[str, str, str]] = []
    for key, latest in candidates.items():
        old = str(current.get(key, ""))
        if old != latest:
            changed.append((key, old, latest))

    mode = "stable-only" if args.stable_only else "stable and pre-release"
    print(f"Verified {len(candidates)} official metadata sources ({mode}).")
    for key, old, new in changed:
        print(f"{key}: {old} -> {new}")
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)

    if failures:
        print("No catalog changes written because not all official sources were verified.", file=sys.stderr)
        return 2
    wrapper_path = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    wrapper_text = wrapper_path.read_text(encoding="utf-8")
    target_gradle = candidates.get("gradle", str(current.get("gradle", "")))
    synchronized_wrapper = replace_wrapper_distribution(wrapper_text, target_gradle)
    wrapper_changed = synchronized_wrapper != wrapper_text

    if args.check:
        if changed or wrapper_changed:
            if wrapper_changed:
                print("Gradle wrapper distributionUrl is out of sync with the version catalog.")
            return 1
        print("Version catalog and Gradle wrapper are current.")
        return 0

    for key, _old, new in changed:
        text = replace_version(text, key, new)
    if changed or wrapper_changed:
        # Validate both outputs before persisting them.
        tomllib.loads(text)
        CATALOG.write_text(text, encoding="utf-8")
        wrapper_path.write_text(synchronized_wrapper, encoding="utf-8")
        if wrapper_changed:
            print(f"Synchronized Gradle wrapper distribution to {target_gradle}.")
    else:
        print("No updates available.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
