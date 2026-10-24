# Generated covers (G1, owner directive D22)

When a track has no artwork, Mowj draws its own cover instead of the old gradient with a note glyph. The covers
are deterministic: the same album gives the same cover on every launch and every device. They cost one off-thread
render per cover and size, and they are cached under the app's existing RAM cap.

Code: `app/src/main/java/com/mhrsdev/musicplayer/ui/widget/cover/`. Sheet: `docs/screens/g1-covers-sheet.png`.
Integration renders: `docs/screens/g1-*.png`.

## Design rules

- **Reference:** the owner's "Without You" cover (`docs/design/png/05-player-cover.png`,
  `docs/design-reference/10-generated-cover-quality-reference.png`, vectors in `docs/design/src/lib.js`). The
  moonlit-hills scene ports that SVG: a three-stop navy-to-teal sky, a soft radial halo, a moon with three craters,
  40-55 stars of random size and opacity, and two quadratic hills that darken towards the viewer.
- **Night palette:** every palette is dark and slightly desaturated. Covers sit well in the dark UI and still read as
  "night prints" in the light theme. Depth comes from layered silhouettes and gradients, not from noise.
- **Typography:** the artist goes top-start in small tracked caps, and the title goes bottom-start in bold caps. These
  are Google Sans Flex wght 800 for Latin and Vazirmatn wght 800 for Persian/Arabic script. Persian has no case and
  no letter-spacing, and it is right-aligned. Bidi is handled by the text engine.
  - Orbs and contours use rounded mixed case (wght 600).
  - The poster scene sets the title large at the top.
  - Tiles centre the title and artist on a plaque under a gold rule.
- **Never cropped:** `CoverText.fit` picks the largest size that wraps into at most 2 lines (3 on the poster), with
  two-line results balanced. If even the minimum size fails, it breaks by code point and ends in an ellipsis that never
  sits after a ZWNJ or a space. Every line is measured, so none is wider than the box.
- **Text only where readable:**
  - The renderer draws text from 96 px. Below that it draws the scene only.
  - Views narrower than 96 dp (list rows, mini-player, Library peeks) use `CoverSpec.textless()`.
  - Home hero cards are also textless, because their play button sits on the text corner and the caption below
    repeats the title.
  - The subtitle is drawn only when it reaches 8.5 px.
- **Mowj mark:** a tiny wave stroke at 42% glow opacity, opposite the artist line. It is drawn only from 256 px and is
  left out on the vinyl and tiles scenes.
- **Artist avatars:** the scene of the artist's name, darkened at the centre, with one large initial. The view crops
  it to a circle.

## Scenes (`CoverScenes`)

| # | Scene | Notes |
|---|---|---|
| 0 | moonlit-hills | The reference. 30% crescent moon (path difference), 35% moon on the left |
| 1 | sunset-waves | Faint 16-ray sunburst, sun with optional retro stripes, reflection lines, three wave silhouettes |
| 2 | vinyl | Record under a soft spotlight, 22 grooves, sweep-gradient sheen, label, tone arm (mirrored for a Persian artist line) |
| 3 | aurora | Three gradient light curtains with glow edges, stars, two jagged ridges |
| 4 | tiles | Persian eight-point girih stars (kashi), gold grout, vignette, title plaque |
| 5 | orbs | Glassy spheres lit from the top left with halos, fine dust |
| 6 | poster | Typographic poster: large title, striped half-sun rising at the bottom, hairline rule and arc |
| 7 | contours | Topographic rings around two summits over a faint survey grid |

All scenes use Canvas primitives only: Path, Linear/Radial/Sweep gradients, circles, arcs and lines. They are drawn in
a 100-unit square, so they scale to any size.

## Palettes (`CoverPalettes`, 26)

The palettes come in these families:

| Family | Palettes |
|---|---|
| Lapis | lapis-moon (the reference), lapis-gold, indigo-teal, midnight-silver, ocean-deep, kashi-tile |
| Turquoise | turquoise-dusk, teal-amber, sea-glass |
| Saffron/amber | saffron-night, amber-dune, copper-dusk |
| Rose | rose-dusk, pomegranate, mulberry |
| Forest | forest-mist, olive-grove, pine-frost |
| Violet | violet-dream, tehran-nights, plum-ink, desert-night |
| Graphite | graphite, ink-vermillion, slate-mint |
| Northern | aurora-north |

Each palette has these roles: `sky0/1/2`, `hill`, `deep`, `accent`, `accent2`, `glow`, `halo` and `ink`.

`CoverPalettesTest` checks that white ink, and the subtitle ink at 90%, reaches at least 4.5:1 against every surface
text may sit on:
- the three sky tones
- both hills
- the bright accents, glow and halo under the 70% `deep` text scrim

## Determinism guarantee

`CoverSpec` derives everything from a 64-bit seed:
- The seed is an FNV-1a hash of normalized text. Normalization folds NFKC, case, Arabic vs Persian yeh/kaf,
  tatweel and whitespace.
- The SplitMix64 finalizer mixes the hash. A pinned test locks the hash values.
- Scene drawing uses `CoverRandom`, which is SplitMix64. Nothing uses `Math.random`, `String.hashCode`, the time or
  the locale.

Identity:
- **Album:** `album:<albumId>|<normalized title>`, so every track of an album and the album tile share one cover.
- **Single:** `single:<title>|<artist>`, used when the album tag is missing or is a folder name such as Download,
  Music, Telegram or `<unknown>`.
- **Artist:** `artist:<name>`.
- **Playlist:** `playlist:<id>`.
- **Seed only:** `seed:<placeholderSeed>`, the fallback when a call site knows nothing else.

What is guaranteed, and what is not:
- Scene, palette and every geometric parameter are guaranteed identical on all devices. Tests check this.
- Two renders on one device are pixel-identical. Tests check this too.
- Exact pixels can differ slightly between Android versions, because Skia anti-aliasing and font rasterization differ.

## Integration points

- **`ui/widget/ArtworkView`:** there are three states.
  - Loading shows a flat `mpSurfaceContainerHigh` tone with no glyph.
  - Art shows the real cover.
  - No art shows the generated cover: after a resolver miss, a known miss or a null URI.

  The generated bitmap goes through `Listener.onArtwork`. So the player's ColorExtractor, backdrop, tints and seek or
  play colours follow the generated cover with no extra code. `bind(Track)` uses the album-level spec, and
  `setMonogram` uses the artist avatar.
- **`ui/widget/ArtworkLoader`:**
  - `loadGenerated`, `generatedNow` (sync only for buckets of 96 px or less) and `cachedGenerated`.
  - Size buckets are 64, 96, 128, 160, 192, 256, 384, 512, 640, 768 and 1024 (160 and 640 since SC1: a 52 dp row and
    an Albums cell no longer render 1.4-2x the pixels they show).
  - While a list flings fast, rows that miss the cache wait (`FlingGate`) and render when the fling ends or slows;
    see `docs/PERF_SCROLL_v0.3.3.md`.
  - It uses the same two low-priority workers, de-duplication and cancellation as artwork decoding.
  - It uses the same LruCache: 1/16 of the heap, at most 16 MB.
- **UI call sites:**
  - Mini-player and player use `ArtworkView.bind`.
  - Home cards, carousels and rows.
  - Library hub peeks, Albums grid, track lists and cards.
  - Search results.
  - Artist grids, avatars and peeks.
  - Playlist mosaics with fewer than 4 covers fall back to the text-less seed scene.
- **Media notification and lock screen (`playback/ArtworkLoader`, merged with N1):**
  `loadBitmapFromMetadata` returns the real cover through the resolver chain, or else the generated 512 px RGB_565
  cover for the track's spec, read from the queue item's extras. The result is kept in N1's bounded 4 MB cache.
  - Media3's `CacheBitmapLoader` delegates `loadBitmapFromMetadata`, so this also feeds the platform session's
    METADATA_KEY_ART.
  - Raw `artworkData` is not put in MediaMetadata, so no bytes travel over IPC for every queue item.
- **Home-screen widget (`appwidget/WidgetUpdater`):** first `WidgetArt.decode`, then the ArtworkResolver chain
  (albumart URIs often fail on Android 10+), then the generated 420 px cover. `NowPlaying` gained `albumId` and
  `albumTitle`, and keeps its old 6-argument constructor, so the widget uses the same album-level spec.
- **Share** sends audio files only, so there is no artwork to fill.

## Cost and battery

- The cover is rendered once per (spec, bucket) on a background thread and then only blitted. There is no animation
  and no network.
- A 512 px render is a few ms of Canvas work.
- RGB_565 is used everywhere, with dithered gradients. A 256 px cover takes 128 KB and a 512 px cover takes 512 KB.
- Paints and paths are pooled per instance, and `draw` is synchronized. Shaders are created per render, never per
  frame.
- Reduce-motion does not apply: the covers are static.

## Not verified on a device

- The renders come from Robolectric with native graphics, not a phone.
- Things still to check on a phone:
  - lock-screen and notification artwork on Android 13–16 and on Samsung's Now Bar
  - widget appearance
  - real render time on a low-end device
