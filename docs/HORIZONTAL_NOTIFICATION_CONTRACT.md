# Horizontal notification and responsive-control contract

## Reading order

A notification is a small planner, not a second app screen. It reads in this order in both Persian
and English:

1. Jalali date and a labelled urgency state, sharing one horizontal header. Cross-check dates are
   subordinate to this header and appear only after expansion.
2. Task title and metadata, sharing a horizontal row. The title owns three fifths of the row;
   metadata owns two fifths. A long category must not measure the task title down to zero.
3. Expanded only: a task-section label, a second task and an overflow count.
4. Expanded only, and only when present: an occasion label and value sharing a row below a hairline.

The compact custom-content area on modern Android can be as short as 48dp. It therefore contains
two single-line rows, with deliberately small insets rather than a panel's expanded padding.
Long titles are ellipsized here, retain their full spoken text, and can use three lines after
expansion. Expanded content uses wrap-content heights, not a fixed-height box that clips larger
fonts. Secondary tasks and the entire occasion section disappear when empty, including their gaps.

## Four looks, one information hierarchy

- **Classic:** a day badge, tonal state pill and one task panel.
- **Minimal:** plain text, a state dot and no filled panel.
- **Editorial:** a larger expanded date, masthead divider and a start-edge task rule.
- **Banner:** an accent-band header, with secondary dates and task rows on the card plate below.

The banner's secondary dates use the plate's muted ink, never the accent band's ink. State words
are checked against the surface they actually occupy. Rounded banner caps use fixed-width matrix
slices, not a stretched rounded bitmap. All runtime colour changes use RemoteViews-supported image
or text actions. The fallback layouts also carry explicit text colours.

`scripts/generate_notification_layouts.py` is the layout source of truth. All four looks have a
compact/expanded and LTR/RTL pair. Generated XML is never edited directly. A zero-width weighted
view may only be a direct child of a horizontal LinearLayout; in a vertical date column the date
must instead be match-parent and unweighted.

## Settings preview

The preview consumes the same content model, palette, state-ink policy and look flags as the
notification. It mirrors the expanded row structure, including bounded metadata and the
conditional location of secondary dates. It is not a pixel screenshot of SystemUI: the device
still supplies notification chrome, actions and available width. The hint below the preview makes
that distinction explicit. Its clock refreshes immediately on resume and pauses in the background.
The preview's spoken description uses the same reading order as the delivered notification.

## Responsive controls

- Keep controls at least 48dp interactive, even when the visible colour dot or icon is smaller.
- Let habit-icon grids and task-priority options wrap instead of shrinking their targets or
  forcing a fixed number of columns.
- Colour and theme choices expose a radio role and selected state. A colour-only choice also has
  a localized name; a visible check is not replaced with colour alone.
- The task editor has one vertically scrolling form and a separate, persistent Save/Cancel row.
- Drawer and calendar transitions use the design system's motion specs and respect reduced motion.
- Small text accents must remain legible on both light and dark app palettes.

## Verification

Automated checks cover all sixteen generated layouts, RemoteViews widget/id/guard contracts,
header/task/occasion row relationships, positive width allocation, metadata limits, optional-row
visibility, bilingual direction parity, spoken collapsed/expanded content, control roles and
responsive form structure. The compact geometry check counts every nested inset and gap with a
conservative 1.5em line allowance at default font scale. It is a structural budget, **not** an
Android renderer or an OEM compatibility certification.

Manual device acceptance matrix (not replaced by these tests):

- Persian and English app language, including a device using the opposite direction.
- All four looks, collapsed/expanded/heads-up, light/dark app theme on light/dark SystemUI.
- 320dp and 360dp phones, landscape and tablet; default and 1.5x text.
- Empty day, long task/category, overdue count, two tasks and long occasion names.
- TalkBack reading order, selected options, keyboard-open editor actions and reduced motion.

No emulator/device screenshot validation is implied by the source checks or by a successful build.

## Calendar in the notification shade

The persistent calendar is a child of the MarbleDo calendar summary on the existing date
channel. Expand the group, then the calendar row to see the horizontal task/time rows and
occasions. A tap on the summary opens today's calendar; the child keeps its own task actions.
SystemUI, not the app, decides the available width and initial expansion. Overdue reminders
keep their separate alert group. When testing on device, disable the persistent calendar and
confirm both rows disappear without dismissing reminders; re-enable it and check that repeated
refreshes update the same pair rather than adding rows.
