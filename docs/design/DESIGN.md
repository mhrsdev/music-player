# Music Player: Design Spec (T2a, design v1)

Status: **design phase. For owner review before implementation (T2–T4).**
Mockups: `docs/design/png/` (start with `00-overview.png`). Sources: `docs/design/src/` (HTML/CSS/SVG).
Re-render: `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers node docs/design/src/render.mjs [prefix…]` (renders `00-overview` last if you pass `00` separately).

| PNG | What it shows |
|---|---|
| 00-overview | All screens + sheets on one page |
| 01-home | Recently added hero carousel (multi-browse), Throwbacks, Quick picks, floating pill nav |
| 02-search | Search field, filter chips, top result, match highlight, explicit badge, overflow |
| 03-library | Liked songs hero card, Albums/Playlists/Artists/History tiles with content peeks, Recently saved |
| 04-artists | Circular artist grid (crops of the artist's own album art) |
| 05-player-cover | Full player, cover view, **Waveform** seek style, cookie play button, output chip |
| 06-player-lyrics | Full player, synced lyrics with word sweep, **Wavy** seek |
| 07-queue | Compact player + queue sheet: sleep timer, "Playing from" chip, drag, swipe-remove, Undo snackbar |
| 08-miniplayer | Home scrolled; artwork-tinted mini-player above the floating nav |
| 09-settings | Full scroll capture: theme (System/Light/Dark/Black), artwork colors, language, folders, lyrics, sleep timer, about |
| 10-rtl-home / 11-rtl-player | Persian (fa, RTL) Home and Player |
| 12-notification-sheet | Android 13–16 shade + lock screen, Samsung Now Bar, Android 8–12 MediaStyle (collapsed/expanded), Glance widgets |
| 13-states-sheet | First run/permission, scanning/loading, search no-results, lyrics offline error |
| 14-light-home / 15-light-player | Light theme variants |
| 16-components-sheet | Play/pause morph, seek states, nav + mini-player states, row states, fallbacks, LoadingIndicator, type, color tokens |

---

## 1. Brief and thesis

- **Person / job:** the owner, a bilingual listener (Persian + English) with a local library who often listens at night with the phone in hand. Jobs: start music fast, see and steer what's playing, read lyrics, manage the queue.
- **Mode:** Reference + Create. Keep the structure and visual language of the reference app (`docs/design-reference/01–06`, M3 Expressive dark) and Melodiha's artwork-tinted media card (`07–08`), with higher polish.
- **Constraints:** Phase 1 is a **local-library** app (ARCHITECTURE K2). The design therefore uses "Recently added" instead of "New releases", has **no profile avatar** in the nav, and shows artist images as crops of the artist's own album art (local files have no artist photos). Lyrics shown in the mockups were written fresh for these mockups; no real lyrics are reproduced.

**Thesis:** *For a night-time, bilingual listener, the artwork is the light source. Each surface takes its color, glow and depth from the cover that's playing, while the controls stay quiet, rounded and tactile (M3 Expressive shapes that morph with playback). Persian and English text get equal typographic care, and legibility of text over artwork is guaranteed by measured scrims.*

**Expression budget (where we spend novelty):**
1. Artwork-derived light: blurred backdrop plus grain on the player, an ambient glow at the top of Home taken from the focused hero cover, an artwork-tinted mini-player, notification and widget.
2. Tactile playback: the shape-morphing cookie play button, and a seek bar that is wavy or a real waveform (setting).

Everything else (lists, chips, settings, search) is conventional M3, so it stays learnable.

## 2. Color

### 2.1 Base schemes (app chrome when no artwork drives color)
Seed: **lapis** (Persian tile blue), tertiary **turquoise**. Generated as an M3 tonal scheme and hand-checked.

| Role | Dark | Light | Black (AMOLED) |
|---|---|---|---|
| background / surface | `#0F1216` | `#F7F9FF` | `#000000` |
| surfaceContainerLowest | `#0A0D10` | `#FFFFFF` | `#000000` |
| surfaceContainerLow | `#171A1F` | `#F1F3FA` | `#0C0E11` |
| surfaceContainer | `#1B1F24` | `#EAEEF5` | `#111418` |
| surfaceContainerHigh | `#252A30` | `#E4E8EF` | `#1A1E23` |
| surfaceContainerHighest | `#30353C` | `#DEE3EA` | `#24292F` |
| onSurface | `#E2E2E9` | `#181C21` | `#E2E2E9` |
| onSurfaceVariant | `#BFC6D2` | `#414851` | `#BFC6D2` |
| outline / outlineVariant | `#8A919C` / `#3F4650` | `#717883` / `#C1C7D2` | `#8A919C` / `#2E343C` |
| primary / onPrimary | `#A6C8FF` / `#00315F` | `#2F5F95` / `#FFFFFF` | same as dark |
| primaryContainer / on | `#1E4878` / `#D4E3FF` | `#D4E3FF` / `#001C3A` | same as dark |
| secondaryContainer / on | `#364659` / `#D6E3F7` | `#D6E3F7` / `#0F1D2C` | `#2B394A` / `#D6E3F7` |
| tertiary / container | `#7FD6CB` / `#00504A` | `#006A62` / `#9EF2E7` | same as dark |
| error / errorContainer / on | `#FFB4AB` / `#93000A` / `#FFDAD6` | `#BA1A1A` / `#FFDAD6` / `#410002` | same as dark |
| inverseSurface / inverseOnSurface / inversePrimary | `#E2E2E9` / `#2E3036` / `#2F5F95` | `#2D3137` / `#EFF0F7` / `#A6C8FF` | same as dark |

Settings → **Wallpaper colors** (Android 12+) swaps the base scheme for `dynamicDark/LightColorScheme(context)`. **Black** only replaces the background and surface containers. Black is a theme mode, not a separate palette.

### 2.2 Artwork-derived roles (the "dynamic" in the thesis)
- **Seed extraction:** `androidx.palette` on a 128 px downscale of the cover → choose `vibrantSwatch ?: dominantSwatch`, then generate a scheme with **SchemeContent** (fidelity to the cover's hue) via material-color-utilities. The MaterialKolor library (`rememberDynamicColorScheme(seed, isDark, style = PaletteStyle.Content)`) is a convenient Compose wrapper. The T2 agent should check the current version before adding it.
- **Roles used by the player / mini-player / widget / Android 8–12 notification:** `dynPrimary` (tone 80 dark / 40 light), `dynOnPrimary` (20 / 100), `dynContainer` (30 / 90), `dynOnContainer` (90 / 10), `dynGlow` (container hue, used for the soft-light tint and the Home glow).
- Sample values used in the mockups (dark): Without You `#A9D4F5/#00344D/#1A4B66/#CDE8FF` · Electric Dusk `#FFB0CD/#5E1133/#7B2950/#FFD9E4` · شب‌های تهران `#E3B6FF/#4A1A6B/#5A2F78/#F3DAFF` · انار `#FFB3B1/#5F1416/#7C1E24/#FFDAD8` · باران `#86D5C9/#003731/#1D4F49/#A2F2E5` · Cold Light `#B8F27A/#1F3700/#2E4F0A/#D3FF9C`.
- **Transition:** when the track changes, crossfade the scheme colors over 600 ms (`animateColorAsState`, tween with the emphasized easing) and the backdrop bitmap over 800 ms. Never cut instantly.
- **Off switch:** "Colors from artwork" off → the player uses the base scheme over the same blurred backdrop.

### 2.3 Backdrop recipe (player, queue top, lyrics)
1. The cover bitmap, scaled to fill the screen plus 80 dp bleed, blurred with a 64 dp radius and saturation ×1.35. `Modifier.blur` needs API 31+. Below 31, pre-blur a 64 px downscale in code (stack blur) and upscale it.
2. A soft-light tint of `dynGlow` at 50–55%.
3. A vertical scrim (dark: `rgba(bg-ish, .30→.22→.62→.82)` top→bottom; light: `rgba(240,247,253, .62→.55→.84→.94)`).
4. **Contrast guard (required):** sample the backdrop luminance under the lyrics band. If white text at the dimmest lyric alpha would fall under 3:1 (large text), raise the scrim alpha until it doesn't. The mockups needed this on the bright pink cover (§8).
5. Grain: a tiled 220 px noise bitmap, BlendMode.Overlay at 14%. Purely decorative, so it is skipped in battery saver.

## 3. Typography

| Role | Latin (Google Sans Flex) | Persian (Vazirmatn) |
|---|---|---|
| Display (player title, page titles) | 34/40, wght 560, ROND 40 | 25–32/36–44, wght 800 |
| Headline (section titles) | 26/32, wght 520, ROND 40 | 24/38, wght 800 |
| Lyrics | 26/35, wght 560 → **760 active**, ROND 30 | 25/44, wght 500 → **800 active** |
| Title (row title) | 17/22, wght 560 | 16/26, wght 650 |
| Body | 15/20, wght 420 | 15/26 |
| Supporting | 14/19, wght 430 | 13.5/22 |
| Label | 13/16, wght 560 | 13/20, wght 600 |

- **Why Google Sans Flex:** it is the family M3 Expressive itself is drawn in, so it matches the reference. It is a variable font with `wght`, `wdth`, `opsz` and **`ROND` (roundness)**; we use ROND 30–50 on display sizes so the letterforms echo the rounded shapes. It is on Google Fonts under **SIL OFL 1.1** (license file checked in google/fonts and saved as `src/fonts/OFL-GoogleSansFlex.txt`).
- **Why Vazirmatn:** OFL, variable weight 100–900, excellent Persian shaping. پ چ ژ گ ک ی, the ZWNJ half-space (شب‌های, می‌آید, کوچه‌باغ) and Persian digits ۰–۹ were verified in the renders (10, 11, 16).
- **Delivery:** bundle both variable TTFs in `res/font/` and use `Font(R.font.x, variationSettings = FontVariation.Settings(FontVariation.weight(560), FontVariation.Setting("ROND", 40f)))` (API 26+ = our minSdk).
- **Mixed scripts:** Compose does not fall back per glyph to a *named* second family. Use a helper `scriptAwareFamily(text)` that returns Vazirmatn if the string contains U+0600–06FF, otherwise Google Sans Flex. Use it for all user content (titles, artists, lyrics). UI strings take the family of the app locale.
- Tabular figures (`fontFeatureSettings = "tnum"`) for durations and times.
- No letter-spacing on Persian. No all-caps labels anywhere (there is no case in Persian, and they read as generated).
- `LineBreak.Heading` (balanced) for lyrics and for headline titles, which avoids orphan words (fixed in the renders).

## 4. Shape

| Element | Shape |
|---|---|
| Hero carousel item | 28 dp (extraLarge); medium 24, small 20 (mask by `maskClip`) |
| Cards / tiles / Throwback covers | 24–26 dp |
| Liked songs card | 32 dp |
| List group | outer 22 dp, inner 6 dp, 3 dp gaps (segmented list) |
| Row artwork | 12 dp (52 dp art), mini-player art 16 dp, player cover 32 dp |
| Search field, nav, chips-in-pill, buttons | full (pill) |
| Filter chip | 12 dp |
| Bottom sheet | 32 dp top corners |
| Snackbar | 16 dp |
| Play/pause | **paused: squircle (30% corners) → playing: `MaterialShapes.Cookie9Sided`** |
| Transport side buttons | 22 dp rounded rect (76×64) |
| Toggle group | connected button group: outer full, inner 14 dp; selected item becomes full pill |
| LoadingIndicator | M3 Expressive `LoadingIndicator` (shape sequence) |

The mockup cookie is drawn with 10 lobes. **Implement with `MaterialShapes.Cookie9Sided`** (the closest stock shape); do not hand-roll a 10-lobe polygon.

## 5. Elevation, blur, depth
- Tonal elevation only for in-app surfaces (no drop shadows on cards).
- Shadows only where something truly floats: floating nav (0 8 24 @35%), mini-player (0 10 28 @45%), dragged queue row (0 14 30 @45%), player cover (0 30 70 @55% dark, 0 30 60 tinted @35% light), snackbar.
- Blur only where real layers overlap (player backdrop, mini-player background art, nav: 20 dp backdrop blur API 31+, otherwise 92% opaque `surfaceContainerHigh`).

## 6. Spacing and layout
- Window margin 16 dp; section title inset 20 dp; vertical rhythm: 8 dp inside groups, 20–28 dp between sections.
- Rows: 72 dp tall; touch targets ≥ 48 dp everywhere (icon buttons are 48×48).
- Floating nav: 68 dp tall, 24 dp above the gesture inset. Mini-player: 76 dp tall, 12 dp side insets, 8 dp above the nav. Content gets bottom padding equal to the nav + mini-player height, and a bottom scrim (transparent → background 96%) sits under the floating chrome.
- Edge-to-edge. Status-bar icons are light over player backdrops, theme-default elsewhere.
- Tablets/foldables (not mocked): Home and Library use two columns; the player becomes cover-left / lyrics-right at ≥ 600 dp width.

## 7. Components → Compose mapping

| Mockup element | Compose / M3 Expressive API | Notes |
|---|---|---|
| Theme | `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), typography, shapes)` | dynamic artwork scheme provided by a `CompositionLocal` for the player subtree |
| Floating pill nav (01) | Custom: `Surface(shape = CircleShape, tonalElevation)` + `Row` of items; selected item `animateContentSize(spring)` shows label | `HorizontalFloatingToolbar` is a fit if its item API allows the expanding label; hide on scroll via `exitAlwaysScrollBehavior` |
| Hero carousel "Recently added" | `HorizontalMultiBrowseCarousel(preferredItemWidth = 272.dp, itemSpacing = 8.dp)` + `Modifier.maskClip(RoundedCornerShape(28.dp))` | Cookie play FAB on the large item (56 dp, drop shadow) |
| Throwbacks | `HorizontalUncontainedCarousel(itemWidth = 152.dp)` | caption below |
| Quick picks / track rows | `ListItem` inside a custom segmented-group container (§4) | Persian title in an LTR list: `TextDirection.Content` + `TextAlign.Left` (align to the layout, not the text) |
| Search | `SearchBar` (expanded/docked) + `FilterChip` row (Songs, Albums, Artists, Playlists, Folders) | match highlight = `SpanStyle(fontWeight = W760)` on the matched range |
| Explicit badge | 16 dp rounded square, onSurfaceVariant fill, "E" 10.5 sp bold | content description "Explicit" |
| Liked songs card | `Card(colors = primaryContainer)` + blurred collage of 3 liked covers (alpha .55) + left-to-right container gradient | play = Cookie9Sided 72 dp |
| Library tiles | `Card` 2×2 grid; content peeks (stacked covers / avatars) | |
| Artists grid | `LazyVerticalGrid(2)`, circular `AsyncImage` of the artist's top album, zoomed 1.7–2× | fallback: monogram on secondaryContainer |
| Wavy seek (06, 07) | `Slider` with custom `track` drawing a sine path for the active part (amplitude 3 dp, wavelength 26 dp, phase advancing while playing), inactive part straight; default expressive line thumb | when paused animate amplitude → 0 (300 ms). `LinearWavyProgressIndicator` is display-only and suits the mini-player bar |
| Waveform seek (05, setting) | Custom `Canvas`: 3 layered smoothed envelopes (alpha .42/.5/.85) clipped at the thumb | amplitudes from a one-time decode (MediaExtractor + MediaCodec, ~120 buckets, cached in Room). Fall back to wavy until ready |
| Play/pause | `Surface` with a `Morph(squircle, MaterialShapes.Cookie9Sided)` shape driven by `animateFloatAsState(spring)`; `rememberInfiniteTransition` rotation 360° / 24 s while playing | press: scale .92 + morph toward circle; buffering: small `LoadingIndicator` inside |
| Shuffle / repeat / queue | Connected `ButtonGroup` of `ToggleButton`s (`ButtonGroupDefaults.connected*ButtonShapes`) | repeat cycles off → all → one (icon `repeat` / `repeat_one`) |
| Heart | `IconToggleButton` filled/outlined `favorite` in dynPrimary | haptic `CONFIRM` |
| Player (05/06) | `SharedTransitionLayout`: mini-player art → cover (`sharedElement`), title/artist `sharedBounds` | predictive back collapses to mini-player |
| Lyrics | `LazyColumn` with `animateScrollToItem` to keep the active line at 40% height, `TextStyle(brush = Brush.horizontalGradient(...))` for the word sweep (only if the LRC has word timings, otherwise line-level), tap a line → seek | fade mask top/bottom via `drawWithContent` + `BlendMode.DstIn` |
| Queue sheet (07) | `ModalBottomSheet` (or anchored-draggable sheet over the player) + reorderable `LazyColumn` (e.g. the "Reorderable" library) + `SwipeToDismissBox` (end-to-start, errorContainer background) | "Queue shuffled · Undo": `SnackbarHost`, `SnackbarDuration.Long`, undo restores the previous order |
| Sleep timer chip | `AssistChip`/`FilledTonalButton` with `bedtime` + "23 min" | tap → sheet of presets (15/30/45/60, end of track) |
| Mini-player (08) | Custom `Surface(24 dp)`: blurred art background + container wash, `LinearWavyProgressIndicator` along the bottom edge | swipe up = open, sideways = skip, down = stop and dismiss |
| Snackbar | `Snackbar` with inverse colors | |
| Loading (13) | `LoadingIndicator` / `ContainedLoadingIndicator`; skeletons = surfaceContainer with a slow shimmer | scan progress = determinate wavy indicator |
| Settings (09) | Segmented `ListItem` groups, `Switch` (with check icon), `SingleChoiceSegmentedButtonRow` (language, seek style), theme picker = 4 preview cards | language → `AppCompatDelegate.setApplicationLocales` |
| Notification (12) | Media3 `MediaSessionService` + default `DefaultMediaNotificationProvider`; `MediaSession.setCustomLayout`/media button preferences with Like + Shuffle `CommandButton`s | see §10 |
| Widget (12) | Glance `GlanceAppWidget`, sizes 4×2 and 2×2 | see §10 |

## 8. Accessibility (measured with `.claude/skills/design-atelier/scripts/contrast.py`)

Token pairs (WCAG 2.x ratio):

| Pair | Ratio |
|---|---|
| dark onSurface / background | 14.56 |
| dark onSurfaceVariant / surfaceContainer (row support text) | 9.63 |
| dark onSurfaceVariant / surfaceContainerHigh (nav icons) | 8.41 |
| dark primary / background | 11.02 |
| dark onPrimary / primary | 7.68 |
| dark onSecondaryContainer / secondaryContainer (selected nav, chips) | 7.43 |
| snackbar text / inverseSurface · Undo (inversePrimary) / inverseSurface | 10.23 · 5.10 |
| dark onErrorContainer / errorContainer (swipe "Remove") | 7.23 |
| light onSurface / background · onSurfaceVariant / surfaceContainer | 16.25 · 7.94 |
| light primary / background · onPrimary / primary | 6.25 · 6.57 |
| black onSurface / background · onSurfaceVariant / surfaceContainer | 16.28 · 10.74 |
| dynamic onPrimary / primary (6 covers, dark) | 7.51 – 9.98 |
| dynamic light (white / #2B6386) | 6.49 |
| dark outline / surfaceContainerHighest (switch off track, non-text) | 3.88 |
| dark outlineVariant / background (unselected chip border, non-text) | **1.96**: decorative; the chip is identified by its label (9.6:1). Use `outline` if a stricter 1.4.11 reading is required |

Text over artwork, measured on **composited pixels sampled from the renders** (white text alpha-composited over the sampled backdrop):

| Location | Ratio |
|---|---|
| 05 player title / artist (white .72) / times (white .78) | 13.78 / 7.86 / 10.20 |
| 06 lyrics active line / title area | 8.93 / 16.39 |
| 06 lyrics inactive (white .60) / past (white .50) / unsung part of the active line (white .66) | 4.45 / 3.82 / 4.89 |
| 11 RTL lyrics active / inactive | 17.41 / 6.67 |
| 07 queue title over backdrop | 6.50 |

The first render failed here: inactive lyric lines were at 2.68:1 on the pink cover. The alphas were raised (inactive .42→.60, past .34→.50, unsung .50→.66) and the scrim strengthened. All lyric lines are now ≥ 3:1 for large text (26 sp). Keep the contrast guard in §2.3, because other covers will differ.

Other obligations:
- Every icon-only control needs a `contentDescription` ("Shuffle, on", "Repeat one", "Show lyrics", "Like", "Sleep timer, 23 minutes left", "Reorder Grateful").
- Nav items without visible labels also get a tooltip on long-press.
- Targets ≥ 48 dp. Drag-to-reorder has an accessible alternative: custom actions "Move up/down" on each row.
- Seek: `Modifier.semantics { progressBarRangeInfo; setProgress }` so TalkBack can adjust it, with 10 s steps.
- Font scale 200%: rows grow (no fixed heights on text containers); lyrics reflow; the nav label may truncate, but the icon and its description remain.
- **Reduced motion** (`ANIMATOR_DURATION_SCALE == 0` or the app setting): no cookie rotation, no wave travel (static wave), no word sweep (line-level emphasis only), crossfades shortened to 150 ms.

## 9. Motion
Use `MotionScheme.expressive()` springs. Values below are intent; tune on a device.

| Moment | Spec |
|---|---|
| Play ↔ pause morph | spatial **default** spring (expressive: damping ≈ .8, stiffness ≈ 380); the icon crossfades in the first 40% |
| Press feedback (all buttons) | scale .92, spatial **fast** spring; button-group neighbours shrink as the pressed item widens (M3 Expressive button group) |
| Cookie rotation while playing | linear, 1 turn / 24 s; pauses with playback |
| Wavy seek | phase advance 1 wavelength / 1.6 s while playing; amplitude → 0 in 300 ms on pause |
| Nav selection | selected pill width and label: spatial **fast** spring; the icon fills (FILL 0→1) over 150 ms |
| Mini-player → player | shared element on the artwork + container transform, ~450 ms emphasized; predictive back scrubs it in reverse |
| Track change | cover slides 24 dp in the skip direction + fade; scheme colors crossfade 600 ms; backdrop crossfade 800 ms |
| Lyrics | active line: weight 560→760 and color alpha .60→1 over 250 ms; list auto-scroll ~450 ms emphasized decelerate; auto-scroll pauses for 3 s after a manual scroll ("Back to current line" chip) |
| Queue | drag lift: scale 1.025 + elevation in 150 ms; neighbours animate with `animateItem()`; swipe-remove: row collapses 200 ms, then the Undo snackbar |
| Home glow | crossfades to the newly focused hero card's color over 500 ms while the carousel scrolls |
| Haptics | `TEXT_HANDLE_MOVE` ticks while seeking (every 10 s), `CONFIRM` on like, `GESTURE_START` on drag lift |

## 10. Notification, lock screen, widget: what the app really controls

**Android 13–16 (shade + lock screen):** SystemUI draws the media player from the `MediaSession`. The app **cannot** change its layout, fonts, colors or the squiggly progress bar. The app **does** control, and therefore must get right:
- Artwork: a square bitmap ≥ 720 px (`MediaMetadata.artworkData` or `artworkUri` with a content provider). SystemUI derives the card's colors from it, so good artwork = a beautiful card.
- Title, artist, album, duration, and correct playback state/position (makes the progress bar draggable).
- Two custom actions via Media3's custom layout / media button preferences: **Like** (heart, filled when liked) and **Shuffle**. Prev/play/next come for free.
- The session stays alive while paused, so the card persists and playback resumes from the lock screen.
- **Samsung One UI 7+ Now Bar / lock-screen pill** (the Melodiha look the owner liked) is Samsung's own rendering of the same session. We get it automatically; its waveform and colors are not ours to design. The mockup in 12 is an approximation.

**Android 8–12:** classic `MediaStyle` notification. Here the app **does** pick the color: `setColorized(true)` + `setColor(dynContainer)`. Large icon = artwork. 3 compact actions (prev, play/pause, next) when collapsed, 5 when expanded (Like, prev, play/pause, next, Shuffle). The seek bar shows on Android 10+ when the session reports a duration.

**Glance widget (fully ours):** 4×2 (art, title, artist, wavy progress, prev / cookie play / next, like) and 2×2 (art plus play). The background is `dynContainer`, falling back to wallpaper colors (Android 12+) and then to the base scheme. Glance cannot animate or draw custom paths at runtime, so the wavy bar and the cookie/squircle are **pre-rendered bitmaps**, updated on track or state change rather than every second. RTL follows the device locale; transport stays in tape order.

## 11. RTL / Persian rules
1. Locale fa → `LayoutDirection.Rtl` for the whole app: the nav order, headers, carousels (the large item starts on the right), the top player row (lyrics toggle on the right, heart on the left) and toggle-group order all mirror. Directional icons (`chevron_right`, `arrow_back`) mirror (`autoMirrored` icons).
2. **Not mirrored:** prev/play/next glyphs, the play triangle, the seek bar and its fill direction, elapsed (left) / remaining (right). This follows Material's bidirectionality guidance that media playback controls refer to tape direction. **Owner decision D-A below.**
3. Persian digits (۰–۹) for all numbers in fa (durations, counts, the status-bar clock is the system's), produced by locale-aware formatting. Stored values stay ASCII.
4. Per-string direction: user content keeps its own direction (`TextDirection.Content`) for correct shaping and punctuation, but **aligns to the layout's start edge** in lists and cards, so a Persian title in an English list sits left like its neighbours, and an English title in a Persian list sits right (10, 08).
5. Vazirmatn line height ≈ 1.6–1.75× (26/16, 38/24, 44/25). Zero letter-spacing. ZWNJ must be preserved in titles from tags.
6. Persian copy used in the mockups (تازه‌های کتابخانه, خاطره‌بازی, انتخاب‌های سریع, امروز اضافه شد) is model-written. **A native-speaker check is advised**, and the owner is a native speaker.

## 12. States (13, 16)
Covered: first run / permission (explains why; secondary path "Choose folders instead"), scanning (determinate count plus skeletons), search no results (keeps the query, offers "Search all categories" and a near match), lyrics offline (music keeps playing, one Retry), lyrics not found (same layout, "No lyrics for this song yet", suggest a `.lrc` next to the file), track file missing (row at 42% + "File missing · tap to remove"), no cover (gradient seeded from the album name + note glyph), artist without art (monogram; Persian monogram in Vazirmatn), buffering (LoadingIndicator inside the play button), disabled play (nothing queued).
Not mocked yet: permission permanently denied (→ "Open settings" button), empty playlist, empty Liked songs ("Tap ♥ on any song to keep it here"), sleep-timer sheet, the overflow menu content, and the add-to-playlist dialog. These follow the same pattern and should be built in T3/T4.

## 13. Self-critique record (three perspectives + authorship)
Inspected: every PNG listed above, at phone size and zoomed crops. This was a full loop with two or more repair rounds per screen.

| Perspective | Finding (evidence) | Fix | Recheck |
|---|---|---|---|
| Professional | Hero cover cropped non-square; the Persian title on the cover was cut (01, round 1) | carousel item 272×272 | fixed (01) |
| Professional | SVG RTL text anchoring pushed cover text off-card; "2016" lost in RTL (10) | anchor swap + explicit `direction` on cover text | fixed |
| Professional | Waveform read as a flat plateau (05, rounds 1–2) | rebuilt as three phase-shifted smooth layers | fixed (05, 16) |
| Professional | Lyric orphans ("tune", "one", "sun") (06) | balanced line breaking | fixed |
| Professional | The fan of covers collided with the "Liked songs" title (03) | replaced with a blurred collage of the liked covers | fixed |
| Professional | Toggle-group icons dark on dark in light player (15) | on-color fix | fixed |
| Professional | Overview grid misaligned (CSS class collision) | renamed class | fixed |
| Professional / a11y | Inactive lyrics 2.68:1 on the pink cover (measured) | alphas + scrim | 3.82–4.89 |
| Beginner | Generic silhouettes looked like broken images (04) | the artist's own album art | fixed |
| Beginner | Sleep chip "23:41" could read as the clock time (07) | "23 min" | fixed |
| Beginner | Nav icons other than the selected one have no visible label (reference pattern) | tooltip + content descriptions (§8) | hypothesis, needs real users |
| Beginner | How to leave the full player? | drag handle at top + system back/predictive back | partially: the handle is subtle |
| Experienced | Persian rows right-aligned inside an LTR list looked ragged (08, 01) | align to layout start (§11.4) | fixed |
| Experienced | Dragged queue row sat on its own slot, so no sense of movement (07) | offset over the next item, placeholder visible | fixed |
| Experienced | Play/next/prev mirroring in RTL | kept in tape order (decision D-A) | owner to confirm |
| Authorship | Decorative "Added today" chip over cover text (01, 10) | cut; info moved into the caption | fixed |
| Authorship | Kept on purpose: dark ground (reference + night use); grain only on artwork/backdrops; covers drawn from each subject (Alborz skyline, girih, pomegranate, rain, cassette) rather than blobs; the JBL device chip from the owner's own screenshot | n/a | n/a |

## 14. Decisions for the owner
- **D-A RTL playback direction:** keep prev/next and the seek bar LTR in Persian (Material guidance, as mocked), or mirror the seek fill like Samsung does?
- **D-B Seek-bar default:** Wavy (reference look, cheap) or Waveform (Melodiha-like; needs a one-time audio decode per song)? Both are mocked; the setting allows either.
- **D-C App name and icon:** the mockups use the placeholder "Music Player" and a small wavy-line glyph. The owner should pick a name.
- **D-D Profile slot:** dropped from the nav because a local app has no account. Restore it if an online catalog/account comes later (STATUS open question 1).
- **D-E Lyrics alignment:** centered like the reference (as mocked) or start-aligned (easier to read for long lines)?

## 15. What is not verified
- Nothing here was compiled or run on Android. These are browser mockups at 412×915 css px (≈ dp), rendered with Chromium, so they are not proof of Compose behavior, TalkBack, performance or actual SystemUI rendering.
- System surfaces (12) are approximations of SystemUI / One UI. Exact spacing, fonts and the Now Bar's look are the OS's.
- Persian copy has had no native-speaker review.
- Contrast was measured on these sample covers only. Real covers need the runtime contrast guard (§2.3).
- Not mocked: tablet/foldable layouts, landscape, the overflow menus, the sleep-timer sheet, the add-to-playlist dialog, and permission-denied-forever.
