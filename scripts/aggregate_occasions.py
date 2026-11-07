#!/usr/bin/env python3
"""Fetch every calendar source, merge it, and publish the online occasion feed.

The app no longer ships a hand-maintained occasion database. This job (run on a
schedule and on every pull request) does the heavy, fragile part of the work:

  1. fetch all official, unofficial and open-source sources listed in
     ``calendar_sources/registry.json``;
  2. normalise them with the adapters in ``scripts/occasions``;
  3. resolve every lunar occasion against the *official* Iranian month table
     instead of the tabular Hijri calendar;
  4. require cross-source consensus before a day is published as a public holiday;
  5. write ``data/occasions/occasions-live.json`` plus a source-health report.

Usage::

    python3 scripts/aggregate_occasions.py --offline --write   # fixtures, deterministic
    python3 scripts/aggregate_occasions.py --write             # fetch the live internet
    python3 scripts/aggregate_occasions.py --verify             # fail if data would change
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from occasions import adapters  # noqa: E402
from occasions.feed import build  # noqa: E402
from occasions.jalali import LunarAnchorTable  # noqa: E402
from occasions.model import RawEvent  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "calendar_sources/registry.json"
SEED_ASSET = ROOT / "feature/calendar/src/main/assets/calendar/occasions-v5-fa.json"
FIXTURES = ROOT / "scripts/occasions/fixtures"
OUTPUT = ROOT / "data/occasions/occasions-live.json"
BOOTSTRAP = ROOT / "feature/calendar/src/main/assets/calendar/occasions-v6-fa.json"
HEALTH = ROOT / "data/occasions/source-health.json"
CONFLICTS = ROOT / "data/occasions/conflicts.json"
LIVE_YEARS = (1404, 1405, 1406, 1407)
DATA_VERSION = 14
USER_AGENT = "MarbleDo-Aggregator"
TIMEOUT_SECONDS = 25

# Adapter -> fixture file, so the pipeline is fully testable without network access.
FIXTURE_FILES = {
    "pc-events": "pc-events.json",
    "iran-holidays-json": "iran-holidays-1405.json",
    "avan-persian-holidays": "avan-holidays-1405.json",
    "shamsi-holidays": "shamsi-holidays-1405.json",
    "jalali-calendar-year": "jalali-calendar-1405.json",
    "pholiday": "pholiday-lunar.js",
    "iran-holidays-npm": "iran-holidays-1.0.0.tgz",
    "bahesab": "bahesab-1405.txt",
    "pasoon": "pasoon-1405.txt",
    "chetor-calendar": "chetor-1405.txt",
}


def load_registry() -> dict:
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    seen: set[str] = set()
    for source in registry["sources"]:
        if source["id"] in seen:
            raise SystemExit(f"Duplicate source id: {source['id']}")
        seen.add(source["id"])
        if not source["url"].startswith("https://"):
            raise SystemExit(f"Source {source['id']} must use https")
        if source["tier"] not in ("official", "unofficial", "open-source"):
            raise SystemExit(f"Source {source['id']} has an unknown tier")
    return registry


def _fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:  # noqa: S310 - https only
        if response.status != 200:
            raise OSError(f"HTTP {response.status}")
        return response.read()


def probe_health(url: str) -> tuple[bool, str]:
    """Ask a review-only source whether it still answers.

    Review-only publishers ship scanned PDFs or unstructured pages, so nothing is parsed from
    them. They are still probed, so the health report can say "reachable" or "unreachable"
    instead of silently pretending they were never looked at.
    """
    try:
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:  # noqa: S310 - https only
            # A few kilobytes are enough to know the host answers; the body is not used.
            response.read(4096)
            return response.status == 200, "reachable"
    except (urllib.error.URLError, OSError, ValueError) as error:
        return False, f"unreachable ({error})"


def read_payload(source: dict, offline: bool) -> tuple[bytes | None, str]:
    fixture = FIXTURES / FIXTURE_FILES.get(source["id"], "")
    if offline:
        if fixture.is_file():
            return fixture.read_bytes(), "fixture"
        return None, "no fixture"
    try:
        return _fetch(source["url"]), "live"
    except (urllib.error.URLError, OSError, ValueError) as error:
        if fixture.is_file():
            return fixture.read_bytes(), f"live fetch failed ({error}); fixture used"
        return None, f"live fetch failed ({error})"


def collect_events(registry: dict, offline: bool) -> tuple[list[RawEvent], dict[str, dict]]:
    events: list[RawEvent] = []
    status: dict[str, dict] = {}
    official_lengths: dict[int, list[int]] | None = None

    for source in registry["sources"]:
        detail = {"id": source["id"], "tier": source["tier"], "events": 0, "status": "not-fetched", "ok": False}
        if source["adapter"] == "review-only":
            if offline:
                detail["status"] = "review-only"
            else:
                reachable, reason = probe_health(source["url"])
                detail["status"] = reason
                detail["ok"] = reachable
            status[source["id"]] = detail
            continue
        payload, mode = read_payload(source, offline)
        if payload is None:
            detail["status"] = mode
            status[source["id"]] = detail
            continue
        try:
            if source["adapter"] == "npm-official-hijri-months":
                table = adapters.npm_official_hijri_months(payload, source["id"], source["tier"])
                official_lengths = table["lengths"]
                detail["status"] = f"{mode}: official lunar month table verified"
                detail["events"] = len(official_lengths)
                detail["ok"] = True
            else:
                adapter = adapters.ADAPTERS[source["adapter"]]
                records = adapter(payload.decode("utf-8", "replace"), source["id"], source["tier"])
                detail["events"] = len(records)
                detail["status"] = mode
                # A source that answered but parsed nothing is a real failure, not a success.
                detail["ok"] = bool(records)
                events.extend(records)
        except (KeyError, ValueError, TypeError) as error:
            detail["status"] = f"{mode}: rejected ({error})"
        status[source["id"]] = detail
    if official_lengths is not None:
        _verify_official_table(official_lengths)
    return events, status


def _verify_official_table(upstream: dict[int, list[int]]) -> None:
    """The bundled table must match the published one; a mismatch is a hard failure."""
    from occasions.jalali import HIJRI_OFFICIAL_LENGTHS

    for year, lengths in upstream.items():
        local = HIJRI_OFFICIAL_LENGTHS.get(year)
        if local is None:
            continue
        shared = min(len(local), len(lengths))
        if local[:shared] != lengths[:shared]:
            raise SystemExit(f"Official lunar table drifted for {year}: {local} != {lengths}")


def bootstrap_catalog(catalog: dict) -> dict:
    """A tiny cold-start asset: official public holidays plus the lunar table.

    The app ships this only so a device that has never been online still shows the
    statutory days off. Every descriptive occasion comes from the online feed.
    """
    sections = {}
    referenced: set[str] = set()
    for calendar in ("jalali", "lunar", "gregorian"):
        rows = []
        for row in catalog[calendar]:
            if not row.get("holiday"):
                continue
            rows.append(row)
            referenced.update(row.get("sourceIds", []))
        sections[calendar] = rows
    sources = [source for source in catalog["sources"] if source["id"] in referenced]
    return {
        "schemaVersion": 6,
        "calendar": "jalali",
        "dataVersion": catalog["dataVersion"],
        "updatedAt": catalog["updatedAt"],
        "note": (
            "Cold-start seed: statutory public holidays and the official lunar month table. "
            "Descriptive occasions are downloaded from the online feed."
        ),
        "lunarAnchors": catalog["lunarAnchors"],
        "jalali": sections["jalali"],
        "lunar": sections["lunar"],
        "gregorian": sections["gregorian"],
        "yearly": {},
        "idAliases": {},
        "sources": sources,
        "schoolPrograms": [],
    }


def _strip_volatile(catalog: dict) -> dict:
    clone = json.loads(json.dumps(catalog, ensure_ascii=False, sort_keys=True))
    clone.pop("updatedAt", None)
    verification = clone.get("verification", {})
    verification.pop("generatedAt", None)
    for source in clone.get("sources", []):
        source.pop("checkedAt", None)
    return clone


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use recorded fixtures instead of the network")
    parser.add_argument("--write", action="store_true", help="write the published feed")
    parser.add_argument("--verify", action="store_true", help="fail when the committed feed is stale")
    parser.add_argument("--date", default=date.today().isoformat(), help="updatedAt stamp (ISO)")
    args = parser.parse_args()

    registry = load_registry()
    seed = json.loads(SEED_ASSET.read_text(encoding="utf-8"))
    anchors = LunarAnchorTable()
    events, status = collect_events(registry, args.offline)
    updated_at = date.fromisoformat(args.date)
    result = build(
        seed,
        registry,
        events,
        anchors,
        data_version=DATA_VERSION,
        updated_at=updated_at,
        years=list(LIVE_YEARS),
        source_status=status,
    )

    health = dict(result.health)
    health["sources"] = [
        {**entry, **status.get(entry["id"], {})} for entry in health["sources"]
    ]

    catalog_text = json.dumps(result.catalog, ensure_ascii=False, indent=1, sort_keys=True) + "\n"
    health_text = json.dumps(health, ensure_ascii=False, indent=1, sort_keys=True) + "\n"
    conflict_text = json.dumps(result.conflicts, ensure_ascii=False, indent=1, sort_keys=True) + "\n"

    counts = {
        "events": len(events),
        "sourcesWithData": sum(1 for entry in status.values() if entry["events"] > 0),
        "lunar": len(result.catalog["lunar"]),
        "jalali": len(result.catalog["jalali"]),
        "gregorian": len(result.catalog["gregorian"]),
        "conflicts": len(result.conflicts),
    }

    if args.verify:
        if not OUTPUT.is_file():
            print(f"FAIL: {OUTPUT.relative_to(ROOT)} is missing")
            return 1
        committed = json.loads(OUTPUT.read_text(encoding="utf-8"))
        if _strip_volatile(committed) != _strip_volatile(result.catalog):
            print("FAIL: the published feed is stale; rerun with --offline --write")
            _report_drift(committed, result.catalog)
            return 1
        print(f"OK: published feed is current ({json.dumps(counts, ensure_ascii=False)})")
        return 0

    if args.write:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(catalog_text, encoding="utf-8")
        BOOTSTRAP.write_text(
            json.dumps(bootstrap_catalog(result.catalog), ensure_ascii=False, indent=1, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        HEALTH.write_text(health_text, encoding="utf-8")
        if result.conflicts:
            CONFLICTS.write_text(conflict_text, encoding="utf-8")
        print(f"Wrote {OUTPUT.relative_to(ROOT)}")
    print(json.dumps(counts, ensure_ascii=False, indent=2))
    return 0


def _report_drift(committed: dict, generated: dict) -> None:
    for calendar in ("jalali", "lunar", "gregorian"):
        old = {row["id"]: row for row in committed.get(calendar, [])}
        new = {row["id"]: row for row in generated.get(calendar, [])}
        for identifier in sorted(set(old) | set(new)):
            if old.get(identifier) != new.get(identifier):
                print(f"  {calendar}: {identifier}")
                break


if __name__ == "__main__":
    sys.exit(main())
