# The MarbleDo icon set

One mark, delivered everywhere Android can ask for it. The whole set is generated from a
single geometry table in [`scripts/generate_launcher_icons.py`](../scripts/generate_launcher_icons.py),
so the adaptive icon, the themed icon and the legacy bitmaps can never drift apart.

```
python3 scripts/generate_launcher_icons.py          # rewrite every icon file
python3 scripts/generate_launcher_icons.py --check  # fail if the set is incomplete or stale
```

## Shapes

| Mask | How it is served |
| --- | --- |
| Circle, squircle, rounded square, square, teardrop | `mipmap-anydpi-v26/ic_launcher.xml` — the launcher masks the three layers into whatever shape it uses |
| Round (legacy launchers) | `mipmap-anydpi-v26/ic_launcher_round.xml`, plus `mipmap-*/ic_launcher_round.png` |
| Square (legacy launchers) | `mipmap-*/ic_launcher.png` |
| Themed / monochrome (Android 13+) | the `<monochrome>` layer, which the system replaces with a single tint |
| Notification small and large icons | `drawable/ic_stat_*.xml` and `drawable/ic_notification_*_large.xml` |
| Widget previews | `feature/*/src/main/res/drawable/ic_*_widget_preview.xml` |

### The safe zone

An adaptive icon is drawn on a 108 × 108 canvas. Launchers only ever show the middle
72 × 72, and a launcher that masks to a circle, a squircle or a teardrop can cut further
into that. The area every mask is guaranteed to keep is the inner **66 × 66**, i.e. the
square from 21 to 87 on both axes.

Every element of the mark — the tile outline, the header band, the binding rings, the day
cells and the completion check — is drawn inside that square, **including half of each
stroke width**. `scripts/tests/test_launcher_icons.py` parses the generated vectors and
fails the build if anything spills out, so a future edit to the mark cannot quietly clip
itself on a Pixel or a Galaxy.

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
lowered, and they give tools that only read bitmaps something to look at. Both bitmaps
are real rasterisations of the same geometry, anti-aliased from signed-distance coverage
rather than upscaled from a smaller file.

The notification and widget icons are vectors, so they need one file per concept rather
than one per density.

## Layers

* `drawable/ic_launcher_background.xml` — the same `@color/marble_launcher_bg` the splash
  window paints, plus a soft diagonal sheen and a corner vignette, so the tile reads as a
  lit surface instead of a flat swatch. Using the shared colour is what keeps the launch
  animation from flashing a different background.
* `drawable/ic_launcher_foreground.xml` — the coloured calendar: teal header, two binding
  rings, five day cells and a green completion check on an off-white tile.
* `drawable/ic_launcher_monochrome.xml` — the same mark reduced to one colour, as an
  outlined page with a solid header band and the day cells and check inside it. The system
  tints this for themed icons, so it has to work as a silhouette rather than as a
  recoloured version of the foreground.

## Editing the icon

Change the constants at the top of `scripts/generate_launcher_icons.py` and re-run it.
The script refuses to write a mark that leaves the safe zone, and
`scripts/tests/test_launcher_icons.py` re-checks the result on every CI run.
