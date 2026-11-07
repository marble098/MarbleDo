# Online occasion sources, the official lunar table and update delivery

Last review: **2026-11-07**. Active runtime data: `data/occasions/occasions-live.json` (schema 6,
data version 14). Cold-start seed: `feature/calendar/src/main/assets/calendar/occasions-v6-fa.json`.

## The bug that started this work: 23 Aban is not a day off

The martyrdom of Fatima (3 Jumada al-Thani 1448) was rendered on **23 Aban 1405**. The statutory
day off is **22 Aban 1405** (Friday, 2026-11-13). The app therefore closed a Saturday that is a
normal working day.

Root cause: Android's `islamic-civil` calendar is *arithmetic*. Iran's official calendar follows
the announced crescent sighting, and for 1448 AH the two disagree by one day:

| Hijri date | arithmetic `islamic-civil` | official Iranian calendar | Shamsi |
| --- | --- | --- | --- |
| 3 Jumada al-Thani 1448 | 2026-11-14 | **2026-11-13** | **22 Aban 1405** |
| 10 Muharram 1448 (Ashura) | 2026-06-26 | **2026-06-25** | **4 Tir 1405** (not 5 Tir) |

The same off-by-one is repeated by several popular community datasets, which is why a naive
"fetch five calendars and take the majority" design would have reproduced the bug. The fix and the
architecture both follow from that observation:

1. **Lunar dates never come from an arithmetic conversion.** The catalog carries an official month
   table (`lunarAnchors`), and the engine resolves every lunar occasion from it.
2. **A community calendar is never a date authority.** It can corroborate a closure, it can reveal
   a disagreement, but it cannot move a lunar occasion.

## The official lunar month table

`lunarAnchors` is a compact serialisation of the official Iranian month lengths:

```json
"lunarAnchors": {
  "startHijriYear": 1423, "startMonth": 1, "startDate": "2002-03-16",
  "officialLengths": [29, 30, 30, ...],
  "estimatedLengths": [30, 29, ...],
  "officialThrough": {"hijriYear": 1448, "month": 10},
  "tabularDriftDays": 0
}
```

* `startDate` is 1 Muharram 1423 AH; `officialLengths` are the published lengths of every month
  after it (310 months, through Shawwal 1448 = 18 Farvardin 1406).
* `estimatedLengths` continues beyond the published range using the arithmetic calendar shifted by
  the drift measured at the last published month. They are marked as estimates in `LunarAnchors`
  and in the settings screen, and they are replaced automatically as soon as the official calendar
  for 1406 is published.
* `LunarAnchors.parse` rejects a table with a month that is not 29 or 30 days, and schema 6 needs
  the table: a schema-6 document without it is rejected as a whole, so a broken server response can
  never fall back to silently wrong dates.

`scripts/occasions/jalali.py` implements the same table for the pipeline, so the Python aggregator
and the Kotlin engine place an occasion on the same day. Both are covered by tests that assert
**22 Aban = 2026-11-13** and **23 Aban is not a holiday**.

## Source tiers

`calendar_sources/registry.json` is the single list of sources. Twenty-two sources are registered in
three tiers. `adapter` says how a source is consumed; `review-only` sources are fetched for health
and human review because they publish PDFs or unstructured pages.

### Official (7)

| id | Publisher | What is taken | Adapter |
| --- | --- | --- | --- |
| `ut-geophysics` | Institute of Geophysics, University of Tehran - the official calendar publisher | official lunar month table, statutory days | review-only (PDF) |
| `dolat` | Government portal | cabinet holiday decisions | review-only |
| `majlis-research` | Parliament Research Center | holiday legislation reports | review-only |
| `irna` | IRNA (state news agency) | closure announcements | review-only |
| `iribnews` | IRIB News | observance weeks, closures | review-only |
| `behdasht` | Ministry of Health | health weeks and days | review-only |
| `medu` | Ministry of Education | school circulars and closures | review-only |

### Unofficial (7)

| id | Publisher | What is taken | Adapter |
| --- | --- | --- | --- |
| `time-ir` | time.ir | calendar cross-check | review-only |
| `bahesab` | bahesab.ir 1405 calendar | month listing with explicit «تعطیل» markers | `persian-calendar-text` |
| `pasoon` | pasoon.ir 1405 | month listing | `persian-calendar-text` |
| `chetor-calendar` | chetor.com 1405 | holiday table with weekdays | `persian-calendar-text` |
| `beytote` | beytote.com | monthly listings | review-only |
| `tasnim` | Tasnim News | occasion reporting | review-only |
| `mehrnews` | Mehr News | observance weeks | review-only |

### Open source (8)

| id | Project | Licence | What is taken | Adapter |
| --- | --- | --- | --- | --- |
| `pc-events` | persian-calendar/events | CC0 | Hijri/Persian/Gregorian keyed events; its Iran entries cite the official University of Tehran calendar | `pc-events-json` |
| `iran-holidays-npm` | iran-holidays | ISC | the official lunar month lengths | `npm-official-hijri-months` |
| `iran-holidays-json` | IranHollidaysJSON | public data | per-day holiday flags + Hijri labels | `iran-holidays-json` |
| `shamsi-holidays` | hasan-ahani/shamsi-holidays (daily scrape) | public data | per-day events + holiday flags | `shamsi-holidays-json` |
| `jalali-calendar-year` | alirezadesh/jalali_calendar | public data | per-day events + lunar labels | `jalali-calendar-year-json` |
| `pholiday` | shkarimpour/pholiday | MIT | lunar month/day occasion list | `pholiday-lunar-js` |
| `persian-holidays-rules` | karfekr/persian-holidays | MIT | floating-date rules (Quds Day, …) | review-only |
| `vacanza-holidays` | vacanza/holidays | MIT | Easter/Orthodox rules, Western holidays | review-only |
| `un-observances` | United Nations | UN terms | international observance days | review-only |

Each adapter is a pure function tested against a recorded fixture in
`scripts/occasions/fixtures/`. Fixtures are real payload excerpts; the recording date is in the
file or in the fixture name. Nothing is scraped at build time to make a test pass.

## What the scheduled job does

`.github/workflows/refresh-calendar-sources.yml` runs daily and
`scripts/aggregate_occasions.py --offline --verify` runs on every pull request.

```
fetch (registry) → adapters → votes per calendar day → official anchors → consensus → publish
```

1. **Fetch** every machine-readable source; a failed fetch falls back to the recorded fixture and is
   reported as such, so one dead site never empties the feed.
2. **Normalise** to `RawEvent`s. Persian/Arabic digits, ZWNJ, yeh/kaf variants and «تعطیل» markers
   are folded; the first row of a month that publishers glue to the heading line is recovered.
3. **Extract a Hijri label** (`[ ٣ جمادي الثانيه ]`) where a source gives one. The label wins over
   the solar date the source printed, and a disagreement is written to
   `data/occasions/conflicts.json` with the delta in days. This is where the 23-Aban class of error
   is both detected and corrected.
4. **Resolve** lunar occasions with the official table, keeping *both* occurrences when a Hijri day
   falls twice inside one Jalali year (Eid al-Fitr on 1 Farvardin and 19 Esfand 1405).
5. **Decide closures** conservatively. A day is published as a public holiday only when
   * an official-basis record says so (`pc-events` carries the official calendar's flags), or
   * an official-tier source says so, or
   * at least **two** independent sources agree *and* at least one of them labels the Hijri date.
   One community calendar, and any number of newswire reports alone, never create a day off.
6. **Reject duplicates.** A solar-dated copy of a lunar occasion ("Ashura on 5 Tir") is dropped;
   that noise is what made the previous catalog look richer than it was.
7. **Publish** `data/occasions/occasions-live.json`, `data/occasions/source-health.json` and
   `data/occasions/conflicts.json`, plus the cold-start seed asset.

When content changes, the workflow opens a pull request. Reviewers see the source-health table, the
conflict list and the diff of the resolved holidays.

## What the app does

* The bundle ships **33 statutory holidays plus the official lunar table** (24 KB). It is a
  cold-start seed, not a catalog: there is no hand-maintained occasion database inside the app.
* On start (and every 20 hours, or on demand) the app downloads `occasions-live.json` from four
  independent delivery paths (GitHub raw, two jsDelivr edges, the GitHub contents API). A mirror
  failure falls through to the next one; the last valid catalog is never deleted.
* It additionally reads three independent community day lists **directly** for the current Jalali
  year. Those are used only to *cross-check*: a holiday is reported as confirmed when at least two
  of them agree, and days they mark while the official table does not are listed separately
  ("days only community calendars mark"). They are never used to move a date.
* Settings → Calendar → *Occasion sources* shows the last update, whether the catalog carries the
  official table or estimates, the per-source result of the last refresh, and the cross-check
  counts. Occasion sheets label each reference with its tier (official / unofficial / open source).

## Deliberate limits

* The official PDF calendar and ministry pages are **review-only**: they are fetched for health and
  read by a human before a data-version bump. Parsing a scanned PDF on a phone would be a larger
  source of error than the problem it solves.
* Beyond Shawwal 1448 the lunar months are estimates. The settings screen says so, and the daily job
  replaces them when the official table extends.
* Community datasets are frequently one day off for lunar occasions. The app shows that disagreement
  instead of hiding it, but the official table always wins.
* A user's ±2-day manual lunar adjustment still applies on top of the table, because some users
  follow a different local announcement. It is a user preference, not evidence.
* `dataVersion` must increase when the feed changes shape; the app rejects a rollback, a schema
  downgrade, a truncated document, a lost category, a lost culture pack, and a >15% record drop.

## Maintenance

```sh
python3 scripts/aggregate_occasions.py --offline --write --date "$(date +%F)"   # regenerate
python3 scripts/aggregate_occasions.py --offline --verify                       # CI drift check
python3 scripts/check_calendar.py
python3 -m unittest discover -s scripts/tests -v
# Android build/test/lint: see .github/workflows/build.yml
```

To refresh a fixture after a source changes shape, replace the recorded payload, rerun
`--offline --write`, and make sure the adapter tests still describe the real format. Per-occasion
research notes from earlier releases are preserved in
[OCCASION_DATA_HISTORY.md](OCCASION_DATA_HISTORY.md) and in each row's `sourceIds` provenance.
