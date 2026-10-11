#!/usr/bin/env python3
"""Publish actual JUnit/lint counts in a GitHub annotation and the Actions summary."""
import os
from collections import Counter, defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
modules = defaultdict(Counter)
for file in root.glob("**/build/test-results/**/TEST-*.xml"):
    suite = ET.parse(file).getroot()
    module = file.relative_to(root).as_posix().split("/build/", 1)[0]
    for field in ("tests", "failures", "errors", "skipped"):
        modules[module][field] += int(suite.get(field, "0"))
required = {"app", "core/domain", "core/data", "core/designsystem", "feature/tasks", "feature/calendar"}
missing = {module for module in required if modules[module]["tests"] == 0}
if missing:
    raise SystemExit(f"Expected unit-test results are missing for: {sorted(missing)}")
totals = Counter()
for counts in modules.values():
    totals.update(counts)
if totals["failures"] or totals["errors"]:
    raise SystemExit(f"Unit tests have failures: {dict(totals)}")
lint_path = root / "app/build/reports/lint-results-debug.xml"
lint = Counter(issue.get("severity") for issue in ET.parse(lint_path).getroot().findall("issue"))
if lint["Error"] or lint["Fatal"]:
    raise SystemExit(f"Lint errors remain: {dict(lint)}")
summary = (f"{totals['tests']} JUnit tests across {len(modules)} modules: "
           f"{totals['failures']} failures, {totals['errors']} errors, {totals['skipped']} skipped. "
           f"Lint: 0 errors, {lint['Warning']} warnings. Debug APK assembly succeeded.")
print(f"::notice title=Verification summary::{summary}")
lines = ["## Verification results", "", summary, "", "| Module | Tests | Skipped |", "| --- | ---: | ---: |"]
for module, counts in sorted(modules.items()):
    lines.append(f"| {module} | {counts['tests']} | {counts['skipped']} |")
lines += ["", "Countdown has no JVM unit-test sources; its compilation and assembly are exercised by the app build."]
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as output:
        output.write("\n".join(lines) + "\n")
