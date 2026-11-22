# Minimal / Modern redesign — Material 3 Expressive

MarbleDo was re-skinned around a single idea: **Calm minimal surfaces, expressive motion, one
colour language per theme.** The redesign stays inside the existing Material 3 Expressive base —
the same colour system, the nine Vazirmatn weights, RTL-aware layout and the motion scheme — but
rewrites every visual surface so the app reads as one quiet, modern appliance instead of a stack
of bordered tiles.

## What changed

### 1. Colour — a near-neutral, one-accent palette

* AUTO light: a soft bluish-grey page (`#EEF0F6`), crisp white cards, flat ink (`#15171F`), and a
  single vivid indigo accent (`#3B4CD0`) — the rest is near-neutral chrome.
* AUTO dark: a flat near-black page (`#0F1016`), lifted near-black cards (`#1A1C26`), light ink and
  a soft periwinkle accent (`#AAB3FF`).
* The four named palettes (Ocean, Forest, Sunset, Rose), the glass variant and AMOLED were left
  alone on purpose — each keeps its own identity. Everything else repaints automatically through
  `MarblePalettes` (widgets, notification) and `MarbleTheme` (screens).
* Contrast was verified, not eyeballed: every `onSurface`/`onSurfaceVariant`/`primary` pair clears
  WCAG AA on its own card plate (8:1–18:1 in light, 8.5:1–14:1 in dark).

### 2. Shapes — a wider, calmer ladder

`Shapes` moved from `10/14/20/28/32` to `12/18/26/34/42` — larger, gentler corners so cards, chips,
fields and sheets read as one family of rounded surfaces instead of a stack of squarish tiles.

### 3. Cards — flat, tonal, shadowless

`MarbleBox` is now the single minimal surface of the app:

* **No hairline frame by default** (`border = null`) — the minimal material identity is the
  tonal contrast between a white card and the soft page behind it, not a grid of 1 dp boxes.
* **No tonal elevation** (`0.dp`) and white `surface` as the default container.
* Hairlines are opt-in (`MarbleBoxBorder`) and are kept only for explicit dividers and frames,
  where the structure still matters.

### 4. Dashboard header — calm gradient + one glow

The hero is no longer a drifting sky of clouds, stars and suns. `auroraBrush` is a two-stop
`primaryContainer → surface` wash (RTL-aware) and `GreetingSky` draws one static radial accent
glow. `GreetingHeadline` is a flat screen title with no shimmer sweep. Named themes and dynamic
colour repaint the hero through the same colour slots as the rest of the app.

### 5. Navigation — quiet, slim, minimal

The bottom bar is a flat white `NavigationBar` with a single active pill — no hairline above it
and no tonal elevation. Labels are semibold when selected; the rail on large screens is unchanged.

## What was deliberately left alone

* The nine-weight Vazirmatn typography system, numerals and RTL.
* The Material 3 Expressive motion scheme (`MotionScheme.expressive()`) — chips, sheets and
  transitions still animate on its springs; reduced-motion users still collapse to `snap()`.
* The five named palettes, AMOLED, glass and dynamic colour — each is a pinned identity.
* Launcher icons, the occasion catalog, notifications and widgets — same artwork, same language.

## Contract

* `MarbleBox` stays the only surface every app screen block lives in; gaps and paddings continue
  to come from `MarbleSpacing`.
* New layout code must not re-introduce per-screen bordered boxes — use a `MarbleBox`, and reach
  for `MarbleBoxBorder` only for an explicit frame or divider.
* Theme-derived colours only: never hardcode pastel sky colors. The hero paints from `primary`,
  `primaryContainer` and `surface`.
