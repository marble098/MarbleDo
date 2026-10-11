# Occasion provenance, date rules and update delivery

Review date: **2026-10-11**. Asset: `feature/calendar/src/main/assets/calendar/occasions-v4-fa.json`.

## What actually updates the app

The updater **does not query a government, Ministry, lunar authority or school API**. It downloads
this repository's manually maintained `main` asset through these fallbacks, in order:

1. `https://raw.githubusercontent.com/marble098/MarbleDo/main/feature/calendar/src/main/assets/calendar/occasions-v4-fa.json`
2. `https://cdn.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/occasions-v4-fa.json`
3. `https://fastly.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/occasions-v4-fa.json`
4. `https://api.github.com/repos/marble098/MarbleDo/contents/feature/calendar/src/main/assets/calendar/occasions-v4-fa.json?ref=main`
   with `Accept: application/vnd.github.raw+json`.

These are four **delivery paths for the same data**, not four independent research sources.
**Compatibility:** older app builds ignore `dateRule` and schema versions. Serving rule anchors
at their old `holidays-fa.json` URL would make them display e.g. Quds on 1 Ramadan. Therefore that
legacy endpoint remains byte-for-byte at the pre-migration data version 5. The new app explicitly
uses `occasions-v4-fa.json` for its bundle and all four download paths. The expanded/rule-aware
catalog requires upgrading the app; an old installed build does not gain it from a data refresh.

The downloaded cache is `filesDir/occasions/occasions-catalog.json`; the bundled asset remains the
offline baseline. Startup and scheduled checks respect a 20-hour freshness interval; a manual
refresh can run sooner. A bad or unavailable mirror never deletes the last valid catalog.
Version/schema rollback, missing categories, loss of all school programs, >15% record loss,
invalid references and responses over 3 million characters are rejected.

The in-app Sources sheet distinguishes references from mirrors and reports the last successful
mirror for this process. `checkedAt` is a **review date**, not the source publication date and not
proof that its website will always remain accessible.

## Inventory and limits

Schema 4 / data version 6 contains **834 recurring entries** (393 Jalali, 108 lunar, 333 Gregorian),
one explicitly year-specific school-election entry for 1404, and **34 undated school programs**.
227 recurring entries carry an education-relevance tag; they include ordinary public occasions
useful for school planning and should not be confused with 227 separate Ministry instructions.

Evidence metadata for recurring entries:

| Status | Count | Meaning |
| --- | ---: | --- |
| sourced | 381 | Matched to the pinned compilation or a linked institutional/news reference; not a blanket claim of official approval |
| traditional | 16 | A traditional/cultural/religious report; disagreements or variants can exist |
| guidance | 7 | A planning window or customary ceremony; verify this year's circular |
| legacy | 430 | Preserved from MarbleDo's previous catalog; individual date/source verification remains outstanding |

Every record has reference IDs. A link to the old MarbleDo catalog is **provenance, not independent
corroboration**. Reference matching against the compilation used dates and titles; title similarity
alone was not treated as proof of an unrelated event. Obvious mismatches and misleading inherited
English labels were manually corrected. The unchanged international/personal baseline was not
independently re-researched in full. This catalog is broader, but is **not exhaustive or an
authoritative replacement for the official annual calendar**.

## Research basis

- Main comparison dataset: [persian-calendar/events, pinned commit
  3bfbcc5b667765691784b55b58d8a385217d96d9](https://github.com/persian-calendar/events/blob/3bfbcc5b667765691784b55b58d8a385217d96d9/events.json).
  This is a community compilation, not a government feed. Its CC0 `COPYING` is preserved in
  `feature/calendar/licenses/CC0-events.txt`. Titles/dates are factual metadata; MarbleDo's
  explanatory notes and preparation checklists are separately written.
- [University of Tehran calendar PDF](https://calendar.ut.ac.ir/documents/2139738/7092644/Calendar-1405.pdf)
  was located but the fetch returned an interstitial. **The PDF itself was not independently read**;
  it is not used as a claim that all 834 entries were verified against that official publication.
- Targeted PANA, IQNA, IRIB, university and cultural reporting was checked for school windows and
  disputed recurring dates. Some checks could only use indexed excerpts; full-page review is
  explicitly distinguished below. News reports are not themselves Ministry circulars.

## Important corrections and school-date decisions

| Topic | Decision / evidence |
| --- | --- |
| Educational Affairs anniversary | 8 Esfand, not the old 24 Esfand. [IRIB minister message](https://www.iribnews.ir/fa/news/5729400). “Educational Affairs Week” remains an undated program because its annual implementation changes. |
| Student Organization | 21 Ordibehesht anniversary; [PANA](https://www.pana.ir/news/1183701). |
| Parent–teacher week | Common 24–30 Mehr window, labelled guidance rather than mandatory annual execution. [PANA circular reporting](https://www.pana.ir/news/958950). |
| Education Councils | 26 Dey law anniversary; the school program week is not frozen. [PANA](https://www.pana.ir/news/449388). |
| School election | 20 Aban **1404 only**, not an annual event. [PANA 1632420](https://www.pana.ir/news/1632420), full text read; it links the Ministry instruction. |
| Quran, Etrat and Prayer | A common 24–30 Azar planning window is guidance. [IQNA 3938677](https://iqna.ir/fa/news/3938677), full text read, describes a **1399 pandemic-specific extension** into early Dey, not a permanent deadline. |
| School Mental Health Week | Common 18–24 Mehr window, themes and implementation subject to the current circular. [PANA 1128882](https://www.pana.ir/news/1128882), 1399 circular reporting. |
| Health / physical education | Common 1–7 Ordibehesht and 26 Mehr–2 Aban windows; [Isfahan Medical University](https://mui.ac.ir/node/4971) / [University of Sistan and Baluchestan](https://www.usb.ac.ir/News/ID/20758). Neither makes new public holidays. |
| Ahla Min al-Asal | School ceremony on 6 Muharram, **not a claim that Qasim was martyred on that date**. [Defapress](https://defapress.ir/fa/news/842580). |
| Mohtasham / ritual poetry | **1 Muharram**, not an annual 8 Mordad. [Honaronline republication](https://sahebkhabar.ir/news/55355313), full text read, explicitly describes the resolution. The 8 Mordad headline was that year's coinciding solar date. |
| Ruqayya | 5 Safar, explicitly a traditional report, not historical certainty. [Karbobala](https://www.karbobala.com/articles/info/1198). |
| Quds / Chaharshanbe Suri / Qalishuyan | Last Friday of Ramadan; evening before Esfand's last Wednesday; second Friday of Mehr. Use rules, not a handful of hard-coded future-year dates. |
| Reza / Jawad | Resolve the last day of Safar / Dhu al-Qadah; no assumption that a lunar month always has 30 days. |
| Family, Waqf, Salman, Pahlevani, shrines, Shah Cheragh, Hadi | Corrected to lunar dates rather than repeating one year's accidental solar date. Preserve old favorite IDs. |
| MAHA week | Undated: dates differ by circular. [PANA 1403 report](https://www.pana.ir/news/1535377) is a past-year example, not a future recurring deadline. |
| NAMAD / JET | Undated programs, supported by [Parliament Research Center evaluation](https://report.mrc.ir/article_10004_a8a929bb618a5bc268aa115b1a441769.pdf) and [JET event website](https://www.legejet.ir/). |

The other school programs have no inferred dates: competitions, school council meetings, family
education, charity phases, summer clubs, trips, coming-of-age/prayer programs, research, arts,
reading, first-grade orientation and end-of-year celebrations require the current district/school
plan. A [public outline of a sample pastoral plan](https://mplibshop.ir/product/barnamesalaneparvareshi/)
was read as **non-official educational guidance only**; no paid document was obtained or copied.
The initially located sample PDF returned 404 and was replaced with that public outline. A
non-resolving shortened Honaronline link was replaced with the readable republication above.
The remaining institutional links may be intermittently inaccessible; linked excerpts and source
kinds are not represented as universally verified current-year instructions.

## Calendar semantics and maintenance

- Android ICU uses `islamic-civil`; a user-set ±2-day lunar adjustment is not an astronomical or
  religious determination. Consult local authority announcements for holidays and worship dates.
- Stable IDs survive corrected titles/dates. `idAliases` retains favorites for removed duplicates.
- `durationDays` records a window (exclusive end in ICS); the month grid marks its start instead
  of inventing separate daily occasions. Guidance/educational entries do not create public holidays.
- Year-specific instructions belong in `yearly`. Unknown dates belong in `schoolPrograms`, **never
  a guessed fixed day**. Source notes must distinguish a historical event from a modern ceremony.
- ICS exports the current filtered resolved dates with evidence/source notes. No lunar RRULE,
  alarms, invitations, or automatic calendar write permissions are generated. Importers control
  merging on repeat import; switching lunar adjustment changes the exported resolved date.
- The app's public category defaults and all notification/widget consumers share one preference
  gate for optional content. This is a UI preference, not an authentication/security boundary.

Before publishing a data edit, bump `dataVersion`, retain stable IDs and references, and run:

```sh
python3 scripts/check_calendar.py
python3 -m unittest discover -s scripts/tests -v
# Plus the Android build/test/lint command in README.md / .github/workflows/build.yml.
```

The validator checks structure, provenance integrity, known corrected dates, reference safety,
regression identities and the separation of dated occasions from undated programs. It cannot
establish source truth or confirm future Ministry schedules automatically.
