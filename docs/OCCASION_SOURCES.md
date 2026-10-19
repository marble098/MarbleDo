# Occasion provenance, date rules and update delivery

Review date: **2026-10-15**. Active asset: `feature/calendar/src/main/assets/calendar/occasions-v5-fa.json`.

## What actually updates the app

The updater **does not query a government, Ministry, lunar authority or school API**. It downloads
this repository's manually maintained `main` asset through these fallbacks, in order:

1. `https://raw.githubusercontent.com/marble098/MarbleDo/main/feature/calendar/src/main/assets/calendar/occasions-v5-fa.json`
2. `https://cdn.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/occasions-v5-fa.json`
3. `https://fastly.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/occasions-v5-fa.json`
4. `https://api.github.com/repos/marble098/MarbleDo/contents/feature/calendar/src/main/assets/calendar/occasions-v5-fa.json?ref=main`
   with `Accept: application/vnd.github.raw+json`.

These are four **delivery paths for the same data**, not four independent research sources.
**Compatibility:** older app builds ignore `dateRule` and schema versions. Serving rule anchors
at their old `holidays-fa.json` URL would make them display e.g. Quds on 1 Ramadan. Therefore that
legacy endpoint remains byte-for-byte at the pre-migration data version 5. The new app explicitly
uses `occasions-v5-fa.json` (schema 5) for its bundle and all four download paths. The v4 rule-aware
endpoint also remains byte-for-byte at schema 4/data version 7 for older v4 clients. The expanded/rule-aware
catalog requires upgrading the app; an old installed build does not gain it from a data refresh.

The downloaded cache is `filesDir/occasions/occasions-catalog.json`; the bundled asset remains the
offline baseline. Startup and scheduled checks respect a 20-hour freshness interval; a manual
refresh can run sooner. A bad or unavailable mirror never deletes the last valid catalog.
Version/schema rollback, missing categories/culture packs, loss of all school programs, >15% record loss,
invalid references and responses over 3 million characters are rejected.

Each occasion carries an evidence label (sourced, traditional, guidance or legacy) and, where one
exists, a link to the checked reference; the four paths above are delivery mirrors of the same
file, not independent research sources. `checkedAt` is a **review date**, not the source
publication date and not proof that its website will always remain accessible.

## Inventory and limits

Schema 5 / data version 8 contains **991 recurring entries** (418 Jalali, 108 lunar, 465 Gregorian),
one explicitly year-specific school-election entry for 1404, and **38 undated school programs**.
253 recurring entries carry an education-relevance tag; they include ordinary public occasions
useful for school planning and should not be confused with 253 separate Ministry instructions.

Evidence metadata for recurring entries:

| Status | Count | Meaning |
| --- | ---: | --- |
| sourced | 519 | Matched to the pinned compilation or a linked institutional/news reference; not a blanket claim of official approval |
| traditional | 33 | A traditional/cultural/religious report; disagreements or variants can exist |
| guidance | 15 | A planning window or customary ceremony; verify this year's circular |
| legacy | 424 | Preserved from MarbleDo's previous catalog; individual date/source verification remains outstanding |

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
  it is not used as a claim that all 991 entries were verified against that official publication.
- Targeted PANA, IQNA, IRIB, university and cultural reporting was checked for school windows and
  disputed recurring dates. Some checks could only use indexed excerpts; full-page review is
  explicitly distinguished below. News reports are not themselves Ministry circulars.

## Western expansion and source boundaries (2026-10-15)

The new feed adds **131 Western/regional records** plus the missing October Migratory Bird Day,
retaining every recurring v4 ID. Culture packs contain 526 Iranian/Islamic, 329 international,
43 shared Western, 32 North American, 51 European, and 10 Australian/New Zealand records.

Date facts were compared with the MIT-licensed [python-holidays community calendar, pinned commit
5d0f4aac695d4c3c3a46d4747725fdac80bd4fff](https://github.com/vacanza/holidays/tree/5d0f4aac695d4c3c3a46d4747725fdac80bd4fff/holidays).
References point to the checked Christian rule module and country files for the US, UK, Canada,
Australia, New Zealand, Germany, France, Ireland, Spain, Italy, the Netherlands, Sweden, Norway,
Denmark, Finland, Portugal, Austria, Switzerland, Mexico and Brazil. The MIT notice is preserved in
`feature/calendar/licenses/MIT-python-holidays.txt`. The app has no runtime dependency on that library.
This is a community ruleset, **not 21 official government confirmations**.

Shared Christian cycles, national commemorations, Thanksgiving variants, family days, Carnival,
Halloween, Advent, Christmas, Black Friday/Cyber Monday and selected regional customs are named in
both languages. `traditional` customs whose reference is regional context rather than independent
corroboration explicitly say so in their notes. Black Friday/Cyber Monday/Giving Tuesday references
anchor US Thanksgiving, not legal recognition of those commercial/charitable customs. Evidence
labels do not turn every traditional or contextual record into a newly verified official event.

New rules are Gregorian Western and Julian-computus Orthodox Easter (with Julian-day conversion,
not a fixed 13-day drift), weekday on/before an anchor, and the Dutch Sunday-to-Saturday exception.
Offsets are bounded to ±60 days; affected neighboring calendar years are included in the index.
Advent is computed from the Sunday on/before December 24. Earlier rules still handle nth/last
weekdays and adjusted lunar month ends. Year-specific entries with the same ID now correctly
supersede a recurring row rather than being discarded by de-duplication.

World Maritime Day, Habitat Day, Road Traffic Victims Day, Philosophy Day, Cooperatives Day and
both Migratory Bird Day observances now use annual weekday rules. Fixed world weeks carry their
actual durations. Science and Peace Week uses an explicitly labelled Monday–Sunday planning
convention containing November 11 and remains **guidance**, not a newly verified official schedule.
Their inherited legacy references are not relabelled as independently verified sources.

Foreign observances never set `holiday=true` in this feed. International Workers' Day is not treated
as an unconditional closure of every Iranian institution; worker-specific/workplace rules require
separate confirmation. The calendar still does not promise every regional variation, all religious
calendars, substitute bank holidays or one-off future proclamations. Source notes and evidence remain
visible when opening an occasion. No lunar/Western feast dates are guessed from a single past year.

## Important corrections and school-date decisions

| Topic | Decision / evidence |
| --- | --- |
| Educational Affairs anniversary | 8 Esfand, not the old 24 Esfand. [IRIB minister message](https://www.iribnews.ir/fa/news/5729400). The published week 8–14 Esfand is now a dated window as well; the undated `education-week` program remains because a district can still shift its own observance. |
| Student Organization | 21 Ordibehesht anniversary; [PANA](https://www.pana.ir/news/1183701). |
| Parent–teacher week | Common 24–30 Mehr window, labelled guidance rather than mandatory annual execution. [PANA circular reporting](https://www.pana.ir/news/958950). |
| Education Councils | 26 Dey law anniversary; the published school week starts 24 Dey and runs seven days. [ISNA](https://www.isna.ir/news/98102317867), [PANA](https://www.pana.ir/news/449388). |
| School election | 20 Aban **1404 only**, not an annual event. [PANA 1632420](https://www.pana.ir/news/1632420), full text read; it links the Ministry instruction. |
| Quran, Etrat and Prayer | No fixed nationwide window: 24–30 Azar (1399, guidance), 11–17 Azar (1402) and early Aban (1405) are all announced examples. [IQNA 3938677](https://iqna.ir/fa/news/3938677) describes a **1399 pandemic-specific extension** into early Dey, not a permanent deadline; [Mehr 6975442](https://www.mehrnews.com/news/6975442) reports the 1405 early-Aban plan. |
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

## School-occasion weeks with exact dates (2026-10-13 review)

The Ministry of Education's own pastoral weeks are held on published or customary dates. The catalog
now stores each one as a dated row anchored on its start day with `durationDays` for the length, so
the month grid, search and ICS export show the window without inventing a holiday:

| Window | Dates | Evidence | Anchor reference |
| --- | --- | --- | --- |
| National Health Week (school health) | 1–7 Ordibehesht | sourced | [Isfahan MU](https://mui.ac.ir/node/4971) |
| Career Introduction Week | 1–7 Ordibehesht | sourced | [PANA 1365196](https://www.pana.ir/news/1365196) |
| Labor Week | 5–11 Ordibehesht | sourced | [Behzisti](https://www.behzisti.ir/news/177752) |
| Teachers' Appreciation Week | 12–18 Ordibehesht | sourced | [IRNA 85805083](https://www.irna.ir/news/85805083) |
| National Environment Week | 16–22 Khordad | sourced | [Kanoon](https://www.kanoonnews.ir/news/374813) |
| Drug Abuse Prevention Week | variable (1–8 Tir in 1401, 4–10 Tir in 1400) | guidance | [MEFDA](https://www.mefda.ir/news/223922) |
| Chastity and Hijab Week | 21 Tir national day; week announced (15–21 in 1398, 21–27 in 1399) | guidance | [IRIB 2465060](https://www.iribnews.ir/fa/news/2465060) |
| National Skills Week | 31 Tir–6 Mordad (1405) | sourced | [Bazarekar](https://bazarekar.ir/fa/news/178896) |
| Police Week | 13–19 Mehr (1404, 1405), 17–23 (1403) | guidance | [Setare](https://setare.com/fa/news/34932) |
| National Child Week | 15–21 Mehr (1405); 16 Mehr is the fixed national day | guidance | [DefaPress](https://defapress.ir/fa/news/864294) |
| Women's Health Week (Saba) | 24–30 Mehr | sourced | [KUMS](https://khc.kums.ac.ir/fa/home/monasebathaibehdashti/saba) |
| Poisoning Prevention Week | 1–7 Aban | sourced | [SBUMS](https://shmu.ac.ir/fdo/fa/page/204) |
| Student Basij Week | 8–13 Aban | sourced | [YJC](https://www.yjc.ir/fa/news/8854494) |
| Civil Defense Week | 8 Aban fixed day; week announced (4–11 in 1405) | guidance | [Paydar-e Melli](https://paydarymelli.ir/fa/news/66920) |
| Book and Reading Week | 24–30 Aban (older sources to 2 Azar) | sourced | [IQNA](https://zanjan.iqna.ir/fa/news/4314403) |
| Basij Week | 29 Aban–5 Azar | sourced | [IRIB 3281575](https://www.iribnews.ir/fa/news/3281575) |
| Research and Technology Week | announced in late Azar (19–25 in 1402); 25 Azar fixed day | guidance | [Mehr 5957328](https://www.mehrnews.com/news/5957328) |
| Education Councils Week | from 24 Dey, seven days | sourced | [ISNA](https://www.isna.ir/news/98102317867) |
| Clean Air Week | 29 Dey fixed day; week announced (24–30 Dey in 1401) | guidance | [Borna](https://borna.news/fa/news/1421464) |
| Fajr Decade | 12–22 Bahman | sourced | [ATU](https://farhangi.atu.ac.ir/fa/news/16999) |
| Men's Health Week (Sama) | 1–7 Esfand | sourced | [YJC](https://www.yjc.ir/fa/news/9054140) |
| Education Affairs and Islamic Education Week | 8–14 Esfand | sourced | [Fars News](https://farsnews.ir/social/1582617739000628791) |
| Charity and Benevolence Week | 14–21 Esfand | sourced | [IRNA 85752648](https://www.irna.ir/news/85752648) |
| Natural Resources Week | 15–21 Esfand | sourced | [IRIB 2663135](https://www.irib-news.ir/fa/news/2663135) |
| Martyrs Commemoration Week | 16–22 Esfand | sourced | [DefaPress 655388](https://defapress.ir/fa/news/655388) |
| World Breastfeeding Week | 1–7 August = 10–16 Mordad | sourced | [IMNA](https://www.imna.ir/news/993003) |

Supporting school days already in the catalog (13 Aban Student Day, 8 Aban Adolescent and Student
Basij Day, 16 Azar Student Day, 15 Esfand Tree Planting Day, 14 Esfand Charity Day, 24 Aban Book and
Librarian Day, 26 Dey councils law anniversary, 8 Aban Civil Defense Day) keep their own rows; the
new windows are additive and no existing ID changed.

Weeks whose start moves every year stay **undated** in `schoolPrograms` (disability week, prayer
conventions, health ambassadors, Muharram/Safar/Fatimiyya mourning programs). The disability week is
the Saturday–Friday week containing 12 Azar (8–14 Azar in 1404, 9–15 in 1398); the prayer convention
and health-ambassador rounds differ per year and province.

## Data version 11: duplicate and mis-date cleanup

The v5 feed carried several classes of noise that made the catalogue look bigger but read as
incomplete on screen. Data version 11 removes them and records the migration in `idAliases`:

- **Wrong solar copies of international days.** 21 Jalali rows repeated a Gregorian observance
  (for example "World Cancer Day" on 4 Bahman instead of its real Gregorian date) because the
  Gregorian month/day had been pasted into a Jalali row. The Gregorian row is canonical and the
  removed IDs now alias to it, so saved favourites still resolve.
- **Placeholder padding.** Rows whose English title ended in "Extended" or "(extra)" repeated an
  existing observance and were deleted; they were never separate occasions.
- **Same-day duplicates.** "Pen Day" (1 Farvardin) vs "Day of the Pen" (14 Tir), two Organ
  Donation rows, three Physician rows, and near-identical Gregorian entries (Rare Disease Day,
  St. Lucia Day, Canada Day, "World Thinking Day (Scouting)", the Indigenous Peoples apostrophe
  variant) were collapsed to one canonical row each.
- **Misplaced entries.** "World Health Day (Iran)" on 6 Farvardin, "International Day of Peace
  (Iran)" on 5 Shahrivar, "World Science Day for Peace" on 9 Aban and "Hope and Greetings Day"
  were removed; the real Gregorian dates already exist.
- **Missing observances added.** International Mine Awareness Day (4 April), World
  Telecommunication Day (17 May), World Children's Day (20 November), Global Recycling Day
  (18 March) and the International Day for Monuments and Sites (18 April), all sourced from the
  existing UN and timeanddate references.
- **Cultural filter coverage.** Every non-Iranian row now carries the `culture` tag, so the
  "cultural occasions" filter no longer silently hides 344 entries.

`scripts/check_calendar.py` now rejects the "Extended"/"(extra)" placeholder shape, a Jalali row
that duplicates a Gregorian title, and a foreign row without the `culture` tag.

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
