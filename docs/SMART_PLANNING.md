# Smart planning and notification design

## Five new capabilities

### 1. Selectable culture packs and a rule-aware occasion catalog

Settings → Calendar → **Culture packs** controls Iranian/Islamic, international, Western traditions,
North America, Europe, and Australia/New Zealand independently. All packs are initially available;
an intentionally empty selection stays empty across restarts and backup/restore. Category toggles
still intersect with these choices. The school layer remains opt-in.

The v5 bundle contains **991 recurring occasions**, including **131 new Western/regional records**
and the missing October Migratory Bird Day. The previous v4 catalog had 859 records. Existing IDs,
favorites, year-specific school instructions and undated school programs are retained.

Western and Orthodox Easter are computed independently every year. Lent, Carnival, Good Friday,
Ascension, Pentecost and Corpus Christi are relative to Easter. Thanksgiving variants, Black Friday,
Cyber Monday, Mother's/Father's Day variants, Advent, Victoria Day, Midsummer and King's Day have
explicit weekday/exception rules instead of copying a recent year's date. Floating international
observances are corrected too, and multi-day world weeks carry real export durations.

These are **discovery packs, not national legal-holiday services**. Regional names and notes state
where a custom applies; foreign events do not become Iranian closures. International Workers' Day
is no longer treated as a blanket public closure for every Iranian institution. Local substitute
bank holidays, one-off government changes, every country's regional custom, and religious
announcements still require local confirmation. See [source inventory](OCCASION_SOURCES.md).

One `OccasionVisibility` gate feeds the calendar, home, radar, filtered ICS export, daily and
preparation notifications, persistent notification, and widget. Changing culture packs also
withdraws already-delivered mixed calendar summaries containing a hidden pack. Working-day helpers
intentionally retain registered Iranian public closures even when their discovery pack is hidden.

### 2. Explainable occasion radar

Home → **Occasion radar** looks 7, 30 or 90 days ahead. It prioritizes favorites, then proximity,
then registered holidays, and explains each recommendation. Preparation lead time is bounded by
how much time remains. The selection uses only enabled categories/packs.

Already-planned occasions are hidden by default, with an explicit option to include them. Linked
tasks remain recognizable after their title changes; links include the actual occurrence date, so
planning this Christmas does not hide next year's Christmas. Legacy tasks with the same bilingual
title and date are recognized without rewriting their data. Archived tasks do not suppress a fresh
plan. Tap any recommendation to review its source notes, create a draft, or open the timeline.

This is a deterministic, offline suggestion engine, **not cloud AI**. It does not infer religion,
ethnicity or location, upload task titles, or create tasks without confirmation.

### 3. Editable multi-stage preparation timelines

Open an occasion in the calendar or radar → **Preparation timeline**. Four stages are proposed:
planning (14 days ahead), coordination (7 days), final preparation (1 day), and the occasion itself.
Each title and date can be edited and each stage can be selected independently.

Optional working-day suggestions move preparation *earlier*, around Iranian Friday weekends,
optional Thursday closure and registered public holidays. The occasion itself never moves. Past
stages and existing stages are excluded; editing a past date to today/future makes it selectable.
If the bounded working-day search cannot find a date, the UI says so instead of pretending a closed
day is available. Users can choose an explicit date or turn off the suggestion.

Only confirmation inserts the selected tasks, as one Room transaction. Stable occurrence/stage tags
protect against double taps and renamed completed stages. A buffered confirmation survives Calendar-to-Home navigation. The normal Home Undo action removes the
whole inserted plan. Tasks remain editable through the ordinary editor; backup includes their links,
checklists, dates and estimates. Only the occasion-day stage enables a live countdown.

### 4. Free-time finder and overlap detection

Home → **Find free time** offers seven civil dates, focus durations and three work windows. It
returns the earliest fitting slot in each free gap and flags overlapping estimated task blocks.
Opening a conflict reviews the first task in the ordinary editor; nothing is automatically moved.

The assumptions are shown in the sheet: a timed task's due time is the **end**, not the beginning,
of its estimated focus block. `focusMinutes` is an estimate, with 25 minutes used when unset.
All-day tasks remain flexible and are listed separately; completed/archived/undated tasks do not
reserve time. Cross-midnight blocks, today's current time, touching intervals and DST are handled
using instants and civil work-window boundaries. Results are based only on MarbleDo tasks, not on
external calendars or actual presence. Choosing a slot opens an unsaved draft for review.

### 5. Honest weekly review

Home → **Weekly review** summarizes the seven complete civil days ending yesterday, with daily
completion bars, a comparison to the preceding seven days, planned focus estimates, next-week load,
and actionable overdue/undated work. The recommendation gives one manageable next step. Sharing is
explicit and exports aggregate text without task titles.

`Task` has no `completedAt` or focus-session log. Consequently these figures are explicitly a
**current snapshot grouped by due date**, not a completion-time history or measured focus time.
Archived tasks are excluded. Empty periods have no invented percentage or trend. Overdue rules
respect the distinction between all-day dates and timed deadlines.

## Notification critique → redesign

| Previous problem | New treatment |
| --- | --- |
| A long, emoji-prefixed `BigTextStyle` mixed dates, occasions and tasks into a wall of text | A compact two-line glance and a framework-decorated expanded layout with a date header, progress, next-task panel and a separate occasion section |
| Attractive Settings preview was not the actual notification layout | Both render the same `NotificationBoxContent`, sections, counts, task/time and overflow text; Settings explicitly identifies Android-controlled framing and sizing |
| Next task omitted its precise time, and a future task could hide overdue work | Localized due date/time, an attention label for overdue work, and overdue-first selection with priority tie-breaking |
| No daily completion indicator or immediate action | Today's done/total progress plus a system **Mark task done** action; the persistent card refreshes after completion |
| Large badge could pair a Gregorian/Islamic status day with a Persian month | Large badge now belongs to its Persian primary date; the status-bar calendar and its rendering are unchanged |
| Reminder detail was only a generic message | Task reminders show the real due date/time, priority/category, checklist progress and a bounded description |
| Sensitive lock-screen text had no deliberate redacted alternative | Date-only/app-only public versions with private task content kept in the unlocked notification |

The expanded layout uses native notification text appearances, light/night accents, modest rounded
sections, locale-aware layout direction, bidi-isolated content, bounded text lines and accessible
text. Android retains the app header, expand affordance and system actions through
`DecoratedCustomViewStyle`. The persistent channel remains silent; no loud channel is recreated to
force attention, and no foreground service is added.

**No changes were made to `NotificationArtwork`, the status-bar vector assets, the icon-style model,
or their mood priority. No icon explanation screen, legend, setting or feature was added.**

## Verification and remaining device checks

- `scripts/check_calendar.py`: schema, IDs, source safety, cultures, rules, holiday scope and known corrections.
- Python regression tests preserve both old feed hashes and reject malformed updates.
- Kotlin unit tests cover Easter vectors (including 2100), weekday ranges, culture/category gates,
  replacement precedence, radar links and explanations, timeline duplicates/past/closure edge cases,
  batch duplicate protection/Undo, free-time/DST/overlaps, review semantics and notification selection.
- Backup tests preserve culture choices (including none) and preparation identity tags.
- Existing CI builds all modules, runs unit tests and Android lint, and assembles the debug APK.

The sandbox has no Android SDK/emulator. A green build/test/lint run is not a screenshot or a device
UI test. On-device review remains recommended on API 26 and 31+, in Persian/English, light/dark,
large font scales, narrow notification widths, locked/unlocked states, and OEM notification shades.
Android controls the outer notification dimensions and may truncate more aggressively on some devices.
