# UI overhaul 2025 — the liquid-glass design record

This document is the design record of the app-shell overhaul: what the 2024–2025 generation of
platform design languages does, what each key area looked like as three real options, which one
won and why, the tokens everything reads from, and the self-review checklist with its evidence.
It supersedes nothing: the calm card language of [UI_COUNCIL_REVIEW.md](UI_COUNCIL_REVIEW.md),
the copy rules of [PLAIN_COPY_AND_CALM_UI.md](PLAIN_COPY_AND_CALM_UI.md) and the notification
contract of [NOTIFICATION_WIDGET_REDESIGN.md](NOTIFICATION_WIDGET_REDESIGN.md) all still hold.

---

## 1. What the 2024–2025 patterns taught us

Reviewed before any code was written: **iOS 18/26 "Liquid Glass"**, **Material 3 Expressive**
(the app already runs its alpha), **Fluent 2's translucent layers**, and the second generation of
web glassmorphism (blur + grain + specular, contrast-guaranteed text).

| Source | What it does | What we took | What we refused |
| --- | --- | --- | --- |
| iOS 18/26 Liquid Glass | Floating translucent chrome that refracts content underneath; pill-shaped tab bar with a morphing selection capsule; chrome that steps aside while scrolling | The floating bar, the true backdrop blur with adaptive tint, the morphing capsule, the auto-hide | The busy specular that harms text contrast; glass on every card (fatiguing) |
| Material 3 Expressive | One motion scheme for everything, spring physics, pill indicators that morph, state layers | `MarbleMotion` springs for the indicator, icon lift and hide/reveal; the indicator embraces icon + label | Oversized radii everywhere — our existing 12/18/26/34/42 ladder stays |
| Fluent 2 | Layered translucency with quiet shadows, depth by lightness | The multi-layer soft shadow token on floating chrome only | Flat "acrylic on everything" |
| Glassmorphism gen-2 (web) | Blur **plus** film grain **plus** a specular edge **plus** a hairline — grain kills blur banding | The exact five-layer material recipe below | Bare `backdrop-blur` with white text on top (the 2020 failure mode) |

The single biggest lesson across all four: **glass is only world-class when its text is
contrast-guaranteed over whatever is behind it**. That is why the ink of the bar is computed
against every theme surface (`MarbleGlass.inkOnGlass`, `MarbleContrast.readableAcross`) and the
guarantee is unit-tested — not eyeballed.

## 2. Deep material — the five-layer liquid glass

`LiquidGlassSurface` (`core/designsystem/LiquidGlass.kt`) draws, bottom to top:

1. **Backdrop** — a real capture (`GraphicsLayer.record`) of everything scrolling under the
   surface, blurred with `BlurEffect` (Android 12+; on older platforms the layer is not drawn and
   the material degrades to tint + grain, which is what those GPUs can afford).
2. **Adaptive tint** — theme-derived, 0.66–0.72 alpha, so the palette and dynamic colour repaint
   the glass like every other surface.
3. **Film grain** — a deterministic 96×96 noise tile (`MarbleGlass.grainPixels`, seeded) at
   3.5–5.5% alpha. Without it, a 24 dp blur bands into visible steps on large flat areas.
4. **Specular sheen** — a top-lit gradient; white at 55% in light themes, a whisper (14%) in dark
   ones.
5. **Hairline** — computed with `MarbleContrast.separator`, so it survives the AMOLED plate where
   a hand-painted `outlineVariant` line composited to 1.2:1 and disappeared.

Depth: the floating bar casts a two-colour soft shadow (16 dp, ambient + spot at 0.10/0.16 alpha)
and nothing else in the app casts one — see the card decision below.

## 3. Key areas: three options each, and the call

### 3.1 Tab bar

| | Option | Verdict |
| --- | --- | --- |
| A | **Docked M3 `NavigationBar`** (the status quo): flat surface, full width, standard pill indicator | Rejected — it is 2021 chrome; no material, no motion story, and it eats the bottom edge even when the user is reading |
| B | **Floating liquid-glass capsule** with a morphing selection pill, icon lift, and smart auto-hide | **Chosen** — the 2025 reference pattern (iOS 26, Material expressive nav); it makes the bottom edge content-first and gives the glass real content to refract |
| C | **Floating bar with sliding label cross-fade and dot indicator** (Nothing/LG style) | Rejected — the dot indicator loses the "which tab am I on" read at a glance and the label cross-fade fights dynamic type |

Why B, specifically: the capsule floats (18 dp side margins, 12 dp above the system bars), the
indicator is a liquid drop — it *squeezes* 12% between slots and settles at 62% of a slot
(`MarbleGlass.indicatorWidthFraction`) — and the selected icon lifts 12%. The bar auto-hides on
sustained downward scroll (40 dp threshold, `TabBarAutoHide`) and returns on any deliberate scroll
back; switching tabs always brings it home. Under reduced motion or touch exploration the bar
simply stays put.

What we rejected from the pattern: hiding the bar on *any* scroll pixel (jittery), and blurring
without grain (banded).

### 3.2 Cards

| | Option | Verdict |
| --- | --- | --- |
| A | **Flat `MarbleBox`** (the status quo, chosen by the UI council) | **Chosen — kept** |
| B | Layered soft shadows on every card ("deep material everywhere") | Rejected — the council review measured this as the "grid of boxed tiles" look; shadow-where-nothing-floats is noise |
| C | Glass cards | Rejected — glass behind glass dilutes the material and costs contrast for nothing |

Rationale: 2025 platforms spend elevation only on what physically floats (tab bar, sheets, FABs,
snackbars). The overhaul therefore *adds* a shadow vocabulary for floating chrome (`MarbleGlass`
soft two-tone shadow) and leaves the page calm. This is a deliberate composition with the earlier
review, not a shortcut — Option B was re-evaluated and rejected again for the same measured
reasons.

### 3.3 Header

| | Option | Verdict |
| --- | --- | --- |
| A | **Theme-derived wash + one soft glow** (the status quo, `AuroraHeader`) | **Chosen — kept** |
| B | Glass header that the list scrolls under (collapsing, blurred) | Rejected for this pass — a collapsing header is a behavioural change of the dashboard, needs its own product pass and device testing |
| C | Full-bleed image/gradient hero per theme | Rejected — conflicts with the calm-copy rules and dynamic colour |

Rationale: the header already derives every colour from the theme table and mirrors in RTL. The
glass language enters through the chrome (bar, notices, rail) first; the header keeps its job of
being quiet paper under the greeting.

### 3.4 Sheets

| | Option | Verdict |
| --- | --- | --- |
| A | Stock M3 `ModalBottomSheet` on the expressive motion scheme (status quo) | **Chosen** |
| B | Glass sheets | Rejected — sheets are reading surfaces; tinting their paper costs contrast |
| C | Custom drag-handle sheet with detents | Rejected — stock sheets already carry detents, predictive back and a11y; a custom one is pure risk |

Rationale: every sheet already rides `MarbleMotion` through `MaterialTheme.motionScheme` and pads
its own safe areas. "Redesign" here means knowing when not to touch.

## 4. Design tokens

All in `MarbleGlass` (`core/designsystem/MarbleGlass.kt`); **no glass geometry is hardcoded at a
call site**. Summary: blur 24 dp · float margin 18 dp · float gap 12 dp · clearance gap 10 dp ·
bar height 62 dp · indicator height 48 dp · hairline 2 dp (drawn) · icon 24 dp · scroll threshold
40 dp · indicator width 0.62 slot (squeeze 0.12) · icon lift 1.12× · grain tile 96 px @ seed 0x5EED
· tint α 0.72 light / 0.66 dark · sheen α 0.55 light / 0.14 dark · grain α 0.035 light / 0.055 dark
· panel corner 28 dp. Spacing continues to come from `MarbleSpacing`, type from
`MarbleTextStyles`, motion from `MarbleMotion`, colour from `MaterialTheme.colorScheme` — the
glass derives everything (`MarbleGlass.recipe`) and never owns a palette.

## 5. Motion

One vocabulary (`MarbleMotion` → `MaterialTheme.motionScheme` springs):

* **Spatial** — the indicator morph, the hide/reveal slide, the screen transitions.
* **Effects** — the icon lift, inks and tints.
* Under **reduced motion** everything collapses to `snap()` and the auto-hide is switched off
  entirely — a bar that disappears is a spatial surprise we refuse to serve to that setting.
* Haptics are not added to the tab switch — the morph is the feedback.

## 6. Accessibility

* **Contrast:** bar labels ≥ 4.5:1 and icons ≥ 3:1 against the *composited glass over every theme
  surface*, checked for all nine theme modes by `MarbleGlassMathTest`; the hairline ≥ 1.8:1 on
  the AMOLED plate; selected tab pairs (primary/primaryContainer, onPrimaryContainer) are the
  theme's guaranteed M3 pairs.
* **Targets & roles:** every tab is a `Role.Tab` selectable inside a `selectableGroup()`, cell
  height 62 dp ≥ 48 dp; the notice dismiss button carries a caller-supplied localized label.
* **Screen readers:** the bar never auto-hides under touch exploration (the shell checks
  `AccessibilityManager.isTouchExplorationEnabled`); icons are decorative, the label names the
  tab; the in-app notice is a polite live region.
* **Dynamic type:** labels use the app type scale and clamp to one line inside a fixed-height
  capsule — at maximum font scale the label truncates rather than breaking the bar.
* **Reduced motion:** see §5.

## 7. Responsive, safe areas, IME

* **Compact (< 600 dp):** the floating glass bar, inset 18 dp from the sides and 12 dp above the
  system navigation area; content is full-bleed and keeps its bottom clear through
  `LocalGlassNavInset` (lists, FAB, notices).
* **Large (≥ 600 dp, portrait tablets and landscape phones):** a floating glass **rail** — same
  material composited over the flat page — with its own margins; the content column stays capped
  at 880 dp and centred.
* **Insets:** the shell applies `safeDrawing` (top + horizontal) exactly where the old scaffold
  did, and for the first time the large branch gets them too (content used to run under the status
  bar on tablets — fixed here).
* **Keyboard:** with the IME open the bar slides away (it is navigation, not an input accessory)
  and `LocalGlassNavInset` grows to the IME height, so focused fields scroll clear — the same
  guarantee the old scaffold's inner padding gave.
* **Landscape:** phones land on the rail layout (width ≥ 600 dp), which is the one-thumb-hostile
  orientation; the bar's auto-hide covers the remaining narrow-landscape case.

## 8. In-app notices

`MarbleGlassSnackbar` renders `SnackbarData` — the same liquid-glass capsule as the bar, polite
live-region semantics, optional action and dismiss, ink contrast-guaranteed like the bar. The
Dashboard's snackbar host now renders through it; call sites keep the plain `SnackbarHostState`
API. Toast-based flows elsewhere are deliberately left alone this pass (they are ephemeral system
chrome, and a toast is not a design surface we can guarantee).

**Notification system (push) and widgets:** the RemoteViews notification pipeline and both Glance
widgets were redesigned in the immediately preceding pass under hard device-verified contracts
([NOTIFICATION_WIDGET_REDESIGN.md](NOTIFICATION_WIDGET_REDESIGN.md)), including the guards that
keep the clutter from returning. They already paint from the same `MarblePalettes` table the new
glass reads, so every theme (including the glass tint) repushes them unchanged. Reworking
RemoteViews art without a device would violate the no-regression rule; the honest call is:
**kept, audited, unified through the palette**, not rewritten. A dedicated notification *centre*
(history) is a data feature (new tables, retention policy, product copy) and gets its own pass.

## 9. Self-review checklist (evidence)

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Every new surface derives from tokens; no hardcoded glass geometry | ✅ | `MarbleGlass` object is the only source; grep shows call sites only reference it |
| 2 | Floating tab bar: liquid glass + morphing indicator + auto-hide + RTL | ✅ | `FloatingGlassTabBar.kt`, `TabBarBehavior.kt`; geometry RTL-mirrored and tested (`indicatorCentersMirrorInRtl`) |
| 3 | Backdrop blur is real, with a defined older-Android fallback | ✅ | `glassBackdropSource` + `BlurEffect`; documented degrade path (tint + grain) |
| 4 | Contrast of every ink on glass ≥ its WCAG bar across all nine themes | ✅ | `MarbleGlassMathTest.labelAndIconInkOnGlassReadOverEveryThemeAndEveryBackdrop` |
| 5 | Hairline visible on AMOLED | ✅ | `MarbleGlassMathTest.theHairlineSurvivesTheAmoledPlate` |
| 6 | Auto-hide behaviour (thresholds, flip, wobble, reset) | ✅ | `TabBarBehaviorTest` (8 cases) |
| 7 | Reduced motion collapses all glass motion and disables auto-hide | ✅ | `MarbleMotion` snap path + `autoHideEnabled` gate in `MarbleDoApp` |
| 8 | Screen-reader safety (no auto-hide under touch exploration, roles, labels) | ✅ | `touchExploration()` in `MarbleDoApp`; `Role.Tab` + `selectableGroup`; live-region notice |
| 9 | Responsive: compact bar / large rail / landscape / safe areas / IME | ✅ | Shell branches in `MarbleDoApp`; IME hides the bar and grows the inset |
| 10 | In-app notices share the material and keep semantics | ✅ | `MarbleGlassSnackbar` wired at the Dashboard host |
| 11 | Notifications and widgets stay on the shared palette; contracts untouched | ✅ | No RemoteViews/Glance code changed; `NotificationPaletteContrastTest` and the layout contract tests still green |
| 12 | Unit tests for every new pure decision | ✅ | `MarbleGlassMathTest`, `TabBarBehaviorTest`, `MarbleContrastTest.readableAcross` |
| 13 | Lint / type-check / build green | ✅ | CI `verify` job (tests + lint + debug + release build check) on this branch/PR |
| 14 | No TODO/FIXME/placeholder in the new code | ✅ | Grep over the diff |
| 15 | Device screenshots / Lighthouse | ⚠️ adapted | This is an Android app built in a sandbox with no emulator; the platform equivalents — unit tests, lint, release-build check, the static design record above — stand in for Lighthouse (which scores web pages) and on-device captures. The HTML design mock (`docs/design/liquid-glass-spec.html`) is labelled a mock, never a screenshot. |

### Platform note on "Definition of Done" items that name web/multiplatform tools

Lighthouse (Accessibility ≥ 95 / Performance ≥ 90) scores web pages; this repository is a native
Android app. The adapted bar is: `:app:lintDebug` plus the accessibility guarantees above
(contrast tested per theme, roles, touch targets, screen-reader behaviour), and performance is
guarded by the baseline-profile module, the release-build check and the deliberate cost ceiling of
the glass (one display-list capture per frame, one blurred draw in the bar bounds, one 96 px
grain tile). The adapted bar is marked on item 15 rather than silently dropped.
