# Notification and widget redesign — “one row, one idea”

The complaint that started this pass was not “the notification is missing a feature”; it was that
the notification area **feels crowded**. So the work here is subtraction, not decoration.

## What was actually crowded

The ongoing card had four looks, but all four shared one content policy: every fact got a label, and
some facts got two.

| Where | What was on screen |
| --- | --- |
| Collapsed row | badge + title + mood pill + a summary line that chained a task, an occasion and a count with ` · ` |
| Expanded card | mood pill, title, two secondary dates, a progress label, a progress bar, a *labelled block* for the next task (label + title + time), a *labelled section* for occasions (heading + bulleted list + “and N more”), and a sub-text line |
| Action row | three actions: mark done, open the calendar, open the tasks |
| Countdown widget | a date header card, an occasions card, a next-task card, and a countdown card containing a nested “days · hours · minutes” card — four surfaces and six labels |
| Habit widget | name, progress, streak, four row cards, and a footer that counted the same habits again with a different denominator |

Nine of those elements said something the reader already knew, and each one was a separate row with
its own padding, so the eye had no place to land. The sixteen custom-view layouts were also
hand-maintained per look per size per direction, and they had drifted.

## The new anatomy

**Notifications (all four looks share this):**

```
compact                                    expanded
┌───────────────────────────────┐          ┌───────────────────────────────┐
│ [badge]  پنجشنبه ۲۴ آبان  امروز│          │ [badge] پنجشنبه ۲۴ آبان  امروز │
│          ۲۲:۳۰ · خرید          │          │         ۱۴۰۵/۰۸/۲۴ · ۲۵ جمادی…│
└───────────────────────────────┘          │ ۳ از ۵ ──────────────          │
                                           │ ┌─ بعدی ─────────── ۲۲:۳۰ ──┐ │
                                           │ │ خرید نان                  │ │
                                           │ └───────────────────────────┘ │
                                           │ اربعین · عاشورا · +۱           │
                                           └───────────────────────────────┘
```

Rules, in order of importance:

1. **One row, one idea.** Header = the date. Chip = the day's character in one or two words.
   Progress = one reading (`3 of 5`, plus `2 late` only when something is late). Next step = the
   only filled surface. Occasions = one value line, with the overflow count riding at the end.
2. **The collapsed row states exactly one fact**, in order of what can be acted on: the next step,
   else today's occasions, else progress, else the secondary calendars. It no longer joins three
   facts with dots.
3. **Two actions, not three.** The whole card opens the calendar, so the action row only adds
   “mark this done” (when there is a next task) and the explicit calendar verb.
4. **No section headings inside a notification.** The mood word already says “occasions”, so the
   occasions line does not need a heading above it.
5. **Explicit colours.** A custom `RemoteViews` layout inherits SystemUI's text colour; it now
   declares `notification_card_text` / `notification_card_muted` for both themes, so the card cannot
   disappear into a light or a dark shade.

The four looks differ in furniture, not in information:

| Look | Compact | Expanded |
| --- | --- | --- |
| **Card** (classic) | rounded date badge, title + mood, one line | header, progress + bar, rounded next-step plate, occasions line |
| **Line** (minimal) | no badge, no plate — text only | one column, no fill, square bar |
| **Poster** (editorial) | larger date, 16sp title | 19sp title, 48dp badge, square-ruled next-step plate |
| **Banner** | 3dp mood rule leads the row | mood rule runs the header's height, tinted plate |

## Widgets

**Countdown widget — three bands, one surface:**

1. `TODAY` eyebrow, the triple calendar date as one bold block.
2. A hairline (a 1dp line, not another card).
3. **One** card: countdown label + target date on one line, the moment's title, then the three
   readings — the day number large, hours and minutes smaller — each unit printed once beneath its
   number.
4. Two quiet meta lines: `Occasions · …` and `Next · …`. They are sentences, not cards.

**Habit widget — a scoreboard, not three counters:**

1. Eyebrow (the widget's name), the score as one large number (`2 / 5`), one short status word
   (`3 left today`, or “everything is done” when it is).
2. The streak chip stays.
3. The four habit rows stay (they are the interaction), with tighter padding and 28dp targets.
4. The footer now only says how many habits did not fit — it no longer restates the day's counts.

Both widget thumbnails (`ic_widget_preview`, `ic_habit_widget_preview`) were redrawn to match, since
they are what the launcher shows in the widget picker.

## Verification

The redesign is guarded by tests that fail if the clutter returns:

- `scripts/tests/test_notification_designs.py` — sixteen layouts come from one table
  (`scripts/generate_notification_layouts.py --check`), direction pairs differ only in direction,
  every text view states its colour, exactly one filled surface in the expanded card (none in the
  minimal look), the old section headings are gone, the collapsed row is a single choice, the
  RemoteViews widget allowlist still holds, and the accent ids the builder tints are exactly the
  ids the layouts paint with the accent colour.
- `app/src/test/.../NotificationLayoutContractTest.kt` — every `R.id` the builder sets exists in
  every layout it can be rendered from.
- `scripts/tests/test_widget_rtl.py` — one filled surface in the planner widget, the widget's
  strings are translated in both languages, and the habit widget states its score once.

`python3 scripts/generate_notification_layouts.py` rewrites the sixteen layouts after a change to
the table; `--check` is what the test runs.

## Limits

The sandbox has no Android SDK and no emulator, so this pass is verified by compilation in CI, unit
tests, lint and the static guards above — not by a screenshot. Android still owns the outer frame,
the header and the final expansion height, so a narrow shade or a very large font scale can ellipsize
the occasion line; every text view is bounded and ellipsized for exactly that reason. Device review on
API 26 and 31+, in Persian and English, light and dark, remains worth doing.
