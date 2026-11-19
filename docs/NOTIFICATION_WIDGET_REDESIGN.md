# Notification redesign — two sections, the task first

The first pass on this card was subtraction: it removed labels, killed the progress row's second
reading, and turned the occasions into one value line. This second pass fixes what subtraction could
not: the card still mixed two unrelated kinds of information, and the line that mattered most — the
task's title — was the one clipped to a single ellipsised row.

## What the user reported

| # | Report | Root cause |
| --- | --- | --- |
| 1 | The task title is cut with `…` and the task cannot be understood | the title was `maxLines="1"` in the card, next to a 38–48 dp badge and a mood pill |
| 2 | “عقب‌افتاده” and “مناسبت” look the same brown/orange | both were painted from a blend of the accent and the day's mood, i.e. from one hue |
| 3 | “۰ از ۱۰۱ عقب‌افتاده” with a progress bar is confusing | a track whose fill never moves is not a reading |
| 4 | Three calendars on one line are crowded and low-contrast | Gregorian + Jalali + lunar in one 10 sp muted line |
| 5 | “انجام شد” and “دیدن تقویم” have no icons and no “بعداً” | two text-only actions, no snooze at all |
| 6 | Occasions are a tiny line at the lowest priority | occasions were a value line *below* everything |
| 7 | Merging occasions and tasks in one card is cluttered | one stack of rows for two different concerns |

## The new anatomy

**Priority 1 is the task. Priority 2 is the occasion. The date is a date, not a paragraph.**

```
heads-up (88 dp budget)                      expanded
┌────────────────────────────────────┐       ┌────────────────────────────────────┐
│ [۲۸]  ۲۸ آبان ۱۴۰۵                │       │ [۲۸]  ۲۸ آبان ۱۴۰۵                │
│        ⚠ ۳ عقب‌افتاده              │       │         میلادی … · قمری …          │
│                                    │       │         ⚠ ۳ عقب‌افتاده            │
│  کارها                             │       │ ┌─ کارها ───────────────────────┐ │
│  خرید نان و شیر برای صبحانه        │       │ │ خرید نان و شیر برای صبحانه     │ │
│  ۲۲,۳۰ · بالا                      │       │ │ ۲۲,۳۰ · بالا                   │ │
└────────────────────────────────────┘       │ │ تماس با dentist               │ │
                                             │ │ و ۲ کار دیگر                   │ │
collapsed (platform template)                │ └────────────────────────────────┘ │
┌────────────────────────────────────┐       │ ─────────────────────────────────  │
│ [۲۸]  ۲۸ آبان ۱۴۰۵                │       │ ★ مناسبت‌های امروز                │
│        خرید نان و شیر…             │       │ روز جهانی مرد · روز جهانی فلسفه    │
└────────────────────────────────────┘       └────────────────────────────────────┘
```

### Why the collapsed row is the platform template

Android limits a *collapsed* custom view to **48 dp** and says so in the docs ("collapsed view
layouts are limited to as little as 48 dp"). Forty-eight dp cannot hold a date, a chip and two
lines of task. The redesign therefore spends its `RemoteViews` where there is room:

* **collapsed** — the platform's own template: title, one text line, the large date badge. It also
  keeps SystemUI's touch ripple and expand affordance, which a custom row loses.
* **heads-up** (88 dp) — the *compact* card: Jalali date, state chip, task title with **two lines**.
* **expanded** (252 dp+) — the *expanded* card: both calendars, the state chip, the task section on
  the card's one filled surface, then the occasion section under a hairline.

That is the "use `RemoteViews` only where it is needed" half of the requirement.

### Rules, in order of importance

1. **Two sections, in priority order.** The task section is always present; the occasion section is
   second, quieter, and hidden whole (`notification_occasions_container` → `GONE`) when the day has
   no occasion. A labelled section that says "nothing" was half the clutter.
2. **The task title gets two lines, then four.** `maxLines="2"` in the heads-up card and `4` when
   expanded; `ellipsize="end"` stays only as the last-resort overflow of a line that ran out of room.
3. **One date in the compact card.** `۲۸ آبان ۱۴۰۵` — day, month **and year** — with no weekday
   pushing it out. Gregorian and lunar appear only expanded, and labelled: `میلادی … · قمری …`.
4. **The state is a chip, not a bar.** One pill with a mark and a word: `⚠ ۳ عقب‌افتاده` or
   `امروز`. The progress bar and its `۰ از ۱۰۱` label are gone.
5. **The day badge is small and lives in the header.** 28 dp compact, 34 dp expanded — the old
   38–48 dp plate was taking the width the title needed.
6. **No section headings for occasions on the collapsed card**; the expanded one gets a hairline, a
   star and `مناسبت‌های امروز`, which is a *priority* marker, not a restatement.

### Colour and semantics

Every colour is stated on the card, because a custom `RemoteViews` row inherits SystemUI's text
colour and disappears in one of the two themes. The three states are **hues**, not shades of the
brand accent, and each is lifted only in lightness until it clears WCAG AA on the plate actually
painted:

| State | Hue | Mark | Contrast |
| --- | --- | --- | --- |
| عقب‌افتاده | deep red-orange `#C33A22` | triangle warning | lifted to ≥ 4.5:1 on both plates |
| امروز | the theme accent (Material You's primary under `DYNAMIC`) | tasks mark | ≥ 4.5:1 |
| مناسبت | calm purple `#6B4FA8` | four-point star | ≥ 4.5:1 |

The lift is lightness-only, and `MarbleContrastTest` pins that red and purple stay **≥ 60° apart on
the hue wheel** — a contrast *ratio* cannot express "these two look different", and that is exactly
how the old card ended up with two brown chips.

`values/notification_colors.xml` and `values-night/notification_colors.xml` carry the same three
hues as layout fallbacks; the builder repaints them from `NotificationPalette`, which is resolved
from the **app theme** (so a pinned dark theme gets a dark card inside a light shade, and vice
versa) and from Material You's `dynamicLightColorScheme` / `dynamicDarkColorScheme` under `DYNAMIC`.

### Actions — three, at most

| Action | Icon | What it does |
| --- | --- | --- |
| `انجام شد` | ✓ | `ReminderReceiver` completes the task, cancels its alarm, dismisses the card and shakes 30 ms with a toast — **without opening the app** |
| `بعداً` | ⏰ | a transparent `SnoozePickerActivity` offers `یک ساعت دیگر`, `امشب`, `فردا صبح`; the choice reschedules the alarm and dismisses the card |
| `باز کردن` | ↗ | deep-links to that task: `marbledo://task/{id}` **plus** `EXTRA_TASK_ID`, which `MainActivity` and `DashboardScreen.pendingEditTaskId` already know how to open |

Tapping the card itself carries the same deep link as `باز کردن`. On an occasion-only day the
task verbs are not offered at all and the single action opens the calendar; the persistent date
icon's card keeps `Done`/`Later`/`Open`.

`SnoozeOptions` is pure `ZonedDateTime` arithmetic and is unit-tested: an hour, 18:00 if it is still
more than half an hour away (otherwise an hour), and 08:00 tomorrow — never 00:40, which is what
"24 hours from now" turns a late-night snooze into.

### Channels

| Channel id | Label | Importance |
| --- | --- | --- |
| `marbledo_overdue` | عقب‌افتاده | HIGH, vibrating |
| `marbledo_today` | یادآور امروز | DEFAULT |
| `marbledo_occasions` | مناسبت‌ها | LOW, `setSound(null, null)`, no vibration, no badge |
| `marbledo_status_date` | تقویم همیشگی | DEFAULT, silent, ongoing |

`marbledo_occasions` deliberately has a **new** id: a channel's importance is frozen when it is
created, so reusing `marbledo_calendar` would have left every existing install with a ringing
channel for a briefing the redesign promises is silent. Settings now has one switch per channel
(`overdueNotificationsEnabled`, `todayRemindersEnabled`, `calendarNotificationsEnabled`) plus the
briefing hour (`occasionNotificationHour`, 06–20), and the morning work is re-armed whenever that
hour changes.

### Grouped overdue reminders

Each late task is posted into `setGroup("marbledo_overdue_tasks")`. A group **summary**
(`GroupedNotification`) then states up to three of them by name, `و ۲ مورد دیگر` for the rest, and
the count in the chip. The summary is silent (the cards already buzzed), is rebuilt from the
database whenever a task is completed from a notification, and disappears once nothing is late.

### Accessibility

* Every remote `TextView` is RTL, `textAlignment="viewStart"` and uses `@font/vazirmatn_variable`.
* The state chip's mark carries its own meaning (warning / task / star), and the card's root has a
  `contentDescription` that reads the date, the state, the task and its due time in order.
* Each action has a word next to its icon; an icon alone is not a TalkBack label.
* The chip and its mark are `importantForAccessibility="no"` where the label beside them already
  says the same thing, so TalkBack reads the card once, not twice.

## The four looks (second pass)

The first pass left the four "designs" as one card with four badge sizes: `classic`, `minimal`,
`editorial` and `banner` differed by 2 dp of badge and a drawable, and the Settings preview drew the
same Compose layout for all of them. On top of that the card stacked a plate, a `@color` section
background that followed the *device's* night mode, a bitmap plate stretched with `fitXY`, and a
pill bitmap stretched to whatever width the word happened to have — three frames inside one
another, with elliptical corners. The second pass gives each look its own anatomy and removes every
stretched corner.

| Look | Header | State | Tasks | Anything else |
| --- | --- | --- | --- | --- |
| `classic` | day badge + date | tonal pill, state mark | on a tonal panel (shape drawable) | — |
| `minimal` | date | dot + word | plain, largest text on the card | — |
| `editorial` | date set at 17/22 sp | dot + word | pull-quote beside a 3 dp accent rule | hairline closes the masthead |
| `banner` | full-width accent band (bleeds to the card edge) | dot + word in the band's ink | plain | — |

The table lives in three places that `test_notification_designs.py` and
`NotificationLayoutContractTest` hold together: `scripts/generate_notification_layouts.py` (the
XML), `NotificationLook.kt` (the builder's flags) and the Settings preview, which branches on the
same flags.

### No stretched corners

* Everything with a corner that has to scale — card plate, task panel, state pill, hairlines — is a
  **shape drawable** with literal colours and a light/dark twin picked by `setImageViewResource`.
* Bitmaps are only used where stretching is harmless: the banner's flat fill, the editorial rule (a
  plain rectangle) and the banner caps, which are drawn at exactly their 20 dp layout width and
  shown with `scaleType="matrix"`, so they are clipped rather than scaled.
* No layout paints an `android:background`; nothing is `@color`-referenced from a drawable the
  builder picks by lightness.

### Type scale

Task title 14/16 sp (two lines compact, three expanded); meta, state, section labels 11 sp;
occasions 12 sp; secondary dates 11 sp. Nothing on the card is below 11 sp — the old 10 sp labels
were unreadable in Persian.

## Verification

- `scripts/tests/test_notification_designs.py` — sixteen layouts from one table
  (`scripts/generate_notification_layouts.py --check`), direction pairs differ only in direction,
  every text states its colour, one filled surface, the collapsed card has no secondary dates, the
  task title is never `maxLines="1"`, the state chip exists in both cards and the progress bar does
  not, and the accent ids the builder tints are exactly the ids the layouts paint with it.
- `app/src/test/.../NotificationLayoutContractTest.kt` — every `R.id` the builder sets exists in
  every layout it can be rendered from, under the new `if (expanded)` / `if (design == "minimal")`
  branch analysis.
- `app/src/test/.../NotificationBehaviourTest.kt` — the three snooze moments (including the
  late-night case), distinct channel ids with a silent briefing, three labelled actions, the group
  contract, "done" never starting an activity, and the deep-link resolution.
- `core/designsystem/.../MarbleContrastTest.kt` — the state hues stay ≥ 60° apart and each clears
  AA on both plates after being lifted.
- `core/domain/.../NotificationPlanTest.kt` — the overdue list's reading order and the grouped
  summary's hidden count.

## Limits

The sandbox has no Android SDK and no emulator, so this pass is verified by CI's compilation, unit
tests, lint and the static guards above — not by a screenshot. Android still owns the outer frame,
the header, the action row and the final expansion height, so a narrow shade or a very large font
scale can still clip; every text view is bounded and ellipsised for exactly that reason. Device
review on API 26 and 31+, in Persian and English, light and dark, remains worth doing.
