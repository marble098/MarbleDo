# The MarbleDo icon set

One mark, delivered everywhere Android can ask for it. The whole set is generated from a
single geometry table in [`scripts/generate_launcher_icons.py`](../scripts/generate_launcher_icons.py),
so the adaptive icon, the themed icon and the legacy bitmaps can never drift apart.

```
python3 scripts/generate_launcher_icons.py          # rewrite every icon file
python3 scripts/generate_launcher_icons.py --check  # fail if the set is incomplete or stale
```

## What was wrong with the old icon

The previous mark was a calendar page: a teal header band, two binding rings, five pale day
cells and a green check. Written down, that is a reasonable idea. At the size an icon is
actually seen at, it was not:

* **It was a stock-calendar silhouette.** The header plus the binding rings plus a grid of
  cells is the same composition as the dialler on a dozen OEM launchers. Nothing in it said
  *MarbleDo*; it said *calendar*, which is the one thing every user already assumes.
* **It had five ideas at once.** Header band, rings, five cells, two-tone palette, check —
  five competing elements inside a 48 px square. Each one was legible on a 512 px Play Store
  screenshot and none of them survived the shrink: the cells fused into a grey field, the
  rings into two dark commas, and the check became a stray fleck.
* **The palette fought the app.** Off-white tile, teal band, mint check. On a light
  wallpaper the tile dissolved; on a dark home screen it glowed. The app itself is a calm,
  dark, glass-and-marble product, and the icon looked like a spreadsheet template.
* **The safe zone was a guess.** The only guarantee was "fits in 66 × 66", which is *not*
  the tightest common mask: an oval launcher keeps 36 × 30, narrower than the safe square at
  the top and bottom. Nothing checked the mark against the masks it was actually going to
  live inside.
* **The themed icon was a different drawing.** The monochrome layer redrew the calendar as
  outlines, so Android 13 users saw a second, unrelated logo in their themed icon pack.

## The mark

A glass marble with a tick, inside an open orbit.

| Element | Reads as | Why |
| --- | --- | --- |
| Marble sphere, mint → deep teal | *Marble* | It is the product's name and its material, and a sphere is unmistakable at 48 px |
| White tick punched into the sphere | *Do* | One shape, one meaning: something got done |
| Open ring around it, three quarters closed | a day turning | The arc is the calendar without drawing a calendar |
| Deep indigo field with a teal glow | the app's own dark surface | The icon now matches the product instead of describing a category |

Everything hangs on one photograph: a lit sphere. A sphere survives shrinking because its
shading gradient does the work that detail would otherwise do — the icon stays interesting at
192 px and still has a clear silhouette at 48 px, where the tick and the ring are the only
things left and both are simple enough to read.

Geometry, in the 108 × 108 adaptive canvas:

| Constant | Value |
| --- | --- |
| Marble radius | 19.5 |
| Orbit radius / stroke | 25.2 / 4.4 |
| Orbit arc | from −56°, 300° sweep (gap at the top) |
| Tick | (45, 55.5) → (51, 61.5) → (63.5, 46.5), 6.6 stroke, round caps |
| Specular highlight | r 3.0 at (45.4, 44.6) |
| **Ink radius** | **27.4** |

## Shapes

| Mask | How it is served |
| --- | --- |
| Circle, squircle, rounded square, square, oval, teardrop | `mipmap-anydpi-v26/ic_launcher.xml` — the launcher masks the three layers into whatever shape it uses |
| Round (legacy launchers) | `mipmap-anydpi-v26/ic_launcher_round.xml`, plus `mipmap-*/ic_launcher_round.png` |
| Square (legacy launchers) | `mipmap-*/ic_launcher.png` |
| Themed / monochrome (Android 13+) | the `<monochrome>` layer, which the system replaces with a single tint |
| Notification small and large icons | `drawable/ic_stat_*.xml` and `drawable/ic_notification_*_large.xml` |
| Widget previews | `feature/*/src/main/res/drawable/ic_*_widget_preview.xml` |

### The safe zone, measured per mask

An adaptive icon is drawn on a 108 × 108 canvas. Launchers only ever show the middle
72 × 72, and the mask they apply inside that varies — and an oval is *tighter than the
contract's* 66 × 66 box, because it keeps just 30 dp above and below the centre.

So the set is checked twice:

1. **The safe zone.** Every point of every layer, plus half of its stroke, must land inside
   the 21…87 square. `scripts/tests/test_launcher_icons.py` parses the generated vectors,
   follows their commands and fails the build if anything spills out.
2. **The masks themselves.** The generator computes the real signed distance from the mark's
   outline to each mask edge and publishes the result in
   [`launcher_icon_manifest.json`](../scripts/launcher_icon_manifest.json). The build fails if
   any mask would clip the mark or if the clearance falls below 1.5 dp.

Current clearance, in dp, from the edge of each mask:

| Mask | Shape | Clearance |
| --- | --- | --- |
| Circle | r 36 | 8.6 |
| Squircle | r 36, superellipse (n = 4) | 8.6 |
| Rounded square | 72 × 72, r 18 | 8.6 |
| Square | 72 × 72 | 8.6 |
| Teardrop | circle + squarer corner | 8.6 |
| **Oval** | **72 × 60** | **2.6** |

The oval is the binding constraint, which is why the mark is 27.4 dp in radius and not 30.

## Sizes

| Bucket | Scale | Legacy `ic_launcher.png` |
| --- | --- | --- |
| `mipmap-mdpi` | 1× | 48 × 48 |
| `mipmap-hdpi` | 1.5× | 72 × 72 |
| `mipmap-xhdpi` | 2× | 96 × 96 |
| `mipmap-xxhdpi` | 3× | 128 × 128 |
| `mipmap-xxxhdpi` | 4× | 192 × 192 |

`minSdk` is 26, so `mipmap-anydpi-v26` always wins on a supported device and the bitmaps
are the belt-and-braces path: they keep the icon resolvable if the minimum SDK is ever
lowered, and they give tools that only read bitmaps something to look at. Both bitmaps are
real rasterisations of the same geometry, anti-aliased from signed-distance coverage rather
than upscaled from a smaller file — the round one is masked to a circle, the square one keeps
the full-bleed field.

The notification and widget icons are vectors, so they need one file per concept rather than
one per density.

## Layers

* `drawable/ic_launcher_background.xml` — `@color/marble_launcher_bg` (the same deep indigo
  the splash window uses, so the launch animation never changes colour underneath the user),
  a teal radial glow behind the marble, a soft diagonal sheen and an opposite-corner vignette.
  The glow is what stops the field from reading as a flat swatch under a mask.
* `drawable/ic_launcher_foreground.xml` — the marble as a radial gradient (four stops, lit
  from the upper left), the white tick with round caps, the orbit arc in pale mint and a small
  specular highlight on the glass.
* `drawable/ic_launcher_monochrome.xml` — the same geometry as a silhouette: one path that
  fills the marble and punches the tick out of it with `fillType="evenOdd"`, plus the orbit
  stroked around it. The tick is a *hole* rather than a stroke because a stroked path cannot
  be subtracted from a fill; the hole is generated as the mitred outline of the tick polyline.
  The system tints this for themed icons, so it has to read as a shape, not as a recoloured
  copy of the foreground.

## Editing the icon

Change the constants at the top of `scripts/generate_launcher_icons.py` and re-run it. The
script refuses to write a mark that leaves the safe zone or that any launcher mask would clip,
and `scripts/tests/test_launcher_icons.py` re-renders every layer from the same constants and
compares them byte for byte with what is on disk, so a hand edit to a vector cannot survive CI.
