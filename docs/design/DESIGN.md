# Music Player: Design Spec v2 (T2b — own identity, Java + XML + custom Views)

Status: **design v2, for owner review; implementation agents build from this file.** Supersedes v1 (Compose / M3 Expressive).
Mockups: `docs/design/png/` (start with `00-overview.png`). Sources: `docs/design/src/` (HTML/CSS/SVG; `base.css` = tokens, `lib.js` = icons, shapes, seek bars).
Re-render: `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers node docs/design/src/render.mjs [prefix…]` (render `00` last; it embeds the other PNGs).

| PNG | What it shows |
|---|---|
| 00-overview | Everything on one page |
| 01-home | Recently added carousel (sleeve covers), Throwbacks as sleeves with the vinyl peeking out, Quick picks, the Deck (nav) |
| 02-search | Capsule field, text tabs with the wave tick, top result spotlight, match highlight |
| 03-library | Liked songs behind an arch "window" mosaic, shelves with cover fans, Recently saved |
| 04-artists | Artist portraits in Persian arches (طاق) |
| 05-player-cover | Player, **Waveform** seek (Melodiha-like layered hills), lamp play disc, Up-next strip |
| 06-player-lyrics | Player, synced lyrics with word sweep, **Wavy** seek; controls at the same y as 05 |
| 17-player-bars | Player, **Bars** seek (voice-message loudness bars) **while dragging**: lens, time bubble |
| 07-queue | Compact player + queue sheet: sleep chip, drag (tilted lift), swipe-remove, Undo toast |
| 08-miniplayer | The Deck with the artwork-tinted mini-player row and loudness-hills progress |
| 09-settings | Full scroll: theme discs, LampSwitch, Motion & effects (Auto/Full/Still), seek-style picker with live previews |
| 10-rtl-home / 11-rtl-player | Persian RTL Home; Persian lyrics player with Bars seek |
| 12-notification-sheet | Android 13–16 shade + lock (system-drawn), Now Bar, Android 8–12 MediaStyle, our widgets |
| 13-states-sheet | First run, scanning (BarsLoader + skeletons), no results, lyrics offline |
| 14-light-home / 15-light-player | Porcelain (light) theme |
| 16-components-sheet | Every custom View with states, icon set, type, color tokens |

---

## 0. What changed vs v1 and why (owner D6, D7, D8, D9)

| v1 (stock Google look) | v2 (ours) | Why |
|---|---|---|
| Google Sans Flex (Google's brand face) | **Readex Pro** (Latin) + **Vazirmatn** (Persian) | Readex was drawn to sit next to Arabic script: same x-height and width as Vazirmatn, so bilingual lines look like one voice. Not a Google brand face. |
| Material Symbols icon font | **Own icon set** (42 glyphs, 24 grid, 1.8 stroke); Home = an arch, Settings = sliders, Library = shelf | Icons are the loudest "this is Google" tell. |
| Pill nav with tonal selected pill | **Deck**: one floating object = mini-player row + nav row; labels always visible; selection = **wave tick** | Fewer floating layers (cheaper, calmer), beginner-readable labels, a mark nobody else uses. |
| Cookie (MaterialShapes) play button, squircle | **Lamp disc**: round, artwork-colored, soft top highlight + 8 dp halo; **MorphPlayPauseView** glyph morph (path interpolation) | Melodiha-like round controls; buildable with Canvas. |
| Connected toggle button group (shuffle/repeat/queue) at the top | Shuffle / repeat flank the transport with a **lit dot**; lyrics, sleep, output and **Up next** live in the bottom third | One-hand reach; no M3 button group. |
| M3 filter chips, segmented buttons, switches | **TabStrip** (text + wave tick), choice rows, **LampSwitch** | No stock widget silhouettes. |
| Segmented list groups / tonal cards | Rows on the ground, no cards; shelves; arch windows | Removes the M3 Expressive "grouped list" look. |
| Big M3 corners everywhere (24–32 dp) | **Sleeve** corners (tight, record-sleeve: 8–20 dp by size), **Arch** for people/collections, **Disc** for controls | Shape by role, drawn from the subject (records, Persian architecture). |
| LoadingIndicator, Snackbar | **BarsLoader**, **ToastCapsule** | Same language in waiting/feedback states. |
| Live blur (`Modifier.blur`, RenderEffect) | **Pre-blurred 64 px bitmap**, scaled up; gradient in Still mode | RAM/battery budget (D8), works on every API level. |
| Lapis/turquoise M3 tonal palette | **Ink** (lapis night) / **Porcelain** (tile glaze) / **Black**; turquoise action, **saffron** for "yours" (Liked) | Persian tile palette: lapis, turquoise, saffron-gold lines, glaze white. |
| Two seek styles | **Wavy, Waveform, Bars** (D4), user setting with live previews | Owner order. |

## 1. Thesis and UX principles

**Thesis:** *For a bilingual night listener, the cover is the only lamp in the room. Surfaces are ink tinted by that lamp; every control is a lit disc or a drawn line (wave, hills, bars) rather than a stock widget; the Persian arch and tile colors give the app a home that is not Google's. Beauty is spent where it is cheap to draw, and the app goes Still when the battery asks.*

**UX principles (owner D9)**
1. **One-hand reach.** Everything you press while listening lives in the bottom 45% of the player: seek, transport, shuffle/repeat, output, sleep, lyrics, Up next. The top holds only collapse and the overflow menu.
2. **Controls never move.** Cover, lyrics and Bars views share the exact same y positions for seek and transport (05/06/17). Only the upper half changes.
3. **Queue and lyrics are one gesture away.** Swipe up anywhere on the bottom strip (or tap "Up next") → queue sheet. Tap the cover or the lyrics button → lyrics; tap a lyric line → seek there. Long-press the cover → add to playlist.
4. **The cover is a handle.** Swipe the cover left/right → next/previous (cover slides, colors crossfade). Swipe down on the player → collapse to the Deck. Gesture zones keep 24 dp clear of the screen edges so system back still works (ui-ux-pro-max: gesture conflicts).
5. **Always say what is playing next and where from.** "Playing from …" at the top, "Up next · …" at the bottom, sleep timer folded into the Up-next line when set ("Up next · sleep in 23 min").
6. **Every destructive step is undoable** (remove from queue, shuffle queue, dismiss mini-player) via the ToastCapsule, 5 s.
7. **Labels are visible.** Nav items always show their label; icon-only buttons have content descriptions and long-press tooltips.
8. **Persian is not a translation.** RTL layout, Persian numerals, Vazirmatn metrics; playback direction stays tape order (decision K5).
9. **Beauty within a budget.** Rich look from static, pre-baked pixels; motion only where it tells state (playing, dragging, selected), only while visible.

**Expression budget:** (1) artwork light (backdrop, cover glow, tinted Deck/widget, Home glow); (2) the three loudness-driven seek bars and the lamp play disc; (3) the arch. Everything else is quiet.

## 2. Color tokens (`res/values/colors.xml`, `values-night/colors.xml`)

Token names are final (implementers depend on them). CSS names in `base.css` map 1:1 (`--ink0` → `@color/ink0`).

| Token | Ink (dark, default) | Porcelain (light) | Black (AMOLED) | Role |
|---|---|---|---|---|
| `ink0` | `#0B0E17` | `#F4F1EA` | `#000000` | window background |
| `ink1` | `#131826` | `#FCFAF6` | `#0C0F16` | Deck, sheets, panels |
| `ink2` | `#1B2133` | `#E9E4DA` | `#141925` | fields, pressed rows, switch track |
| `ink3` | `#252C42` | `#FFFFFF` | `#1D2333` | lifted (dragged row, toast) |
| `line` / `line2` | `#EEEAE1` @9% / @16% | `#151B2C` @9% / @16% | as Ink | hairlines, inner strokes |
| `text` | `#EEEAE1` | `#151B2C` | `#EEEAE1` | primary text (warm paper white / lapis ink) |
| `text2` | `#A7AEBE` | `#4E566A` | `#A7AEBE` | secondary text, inactive icons |
| `text3` | `#737B8F` | `#7A8194` | `#737B8F` | durations, inactive nav (dark only for text; light uses text2 for text) |
| `accent` / `onAccent` | `#63D5C7` / `#052420` | `#0C6D65` / `#FFFFFF` | as Ink | turquoise (firuzeh): action, selection, focus |
| `accentSoft` | accent @14% | accent @10% | | halos, lit backgrounds |
| `lapis` | `#7FA2F5` | `#2B56B5` | | links, info |
| `saffron` / `onSaffron` | `#EDC06A` / `#2B1C00` | `#9C6A10` / `#FFFFFF` | | "yours": Liked songs |
| `danger` / `dangerInk` | `#FF8A7A` / `#3D120E` | `#B3261E` / `#FFE4E0` | | remove, errors |
| `glass` / `glass2` | white @8% / @13% | ink @6% / @10% | | disc and capsule fills over artwork |

**Artwork-derived roles** (player, Deck mini row, widget, 8–12 notification): `dyn` (tone ≈80 of the cover's vibrant-or-dominant swatch), `onDyn` (≈20), `dynInk` (≈30), `onDynInk` (≈90), `dynGlow` (container hue). Extract with `androidx.palette` on a **48–64 px** downscale (same bitmap as the backdrop), tone-map in Java with HCT from `material-color-utilities` (Java artifact) or a small HSL clamp. Crossfade on track change: 600 ms `ArgbEvaluator`. Samples in 16-components.

Measured (design-atelier `contrast.py`, WCAG 2.x): text/ink0 **16.05** · text2/ink0 **8.66** · text2/ink1 **7.95** · text2/ink2 **7.19** · text3/ink0 **4.55** · accent/ink0 **10.89** · onAccent/accent **9.27** · saffron/ink1 **10.4** · danger/ink1 **7.72** · light text/ink0 **15.19** · light text2/ink0 **6.50** · light accent/ink0 **5.49** · white/light accent **6.19** · light saffron/ink0 **4.15** (large text and icons only) · light text3/ink0 **3.45** (non-text only) · black text/ink0 **17.49**.
Text over artwork keeps v1's rule: a **runtime contrast guard** samples the backdrop under the text band and raises the scrim until white text ≥ 4.5:1 (body) / 3:1 (lyrics ≥ 23 sp).

## 3. Typography (`res/font/`, `TextAppearance.Shab.*`)

| Style | Latin: Readex Pro | Persian: Vazirmatn |
|---|---|---|
| `Display` (page titles, player title) | 32/38 sp, 600, tracking −0.6 | 28/44, 800 |
| `Section` | 20/26, 560, −0.2 | 19/32, 800 |
| `Title` (rows) | 16/22, 500 | 15.5/26, 600 |
| `Body` | 15/22, 380 | 15/26, 400 |
| `Meta` | 13/18, 380, `text2` | 13/22 |
| `Label` | 12/16, 560 | 12.5/20, 600 |
| `Lyric` / `LyricActive` | 25/34, 450 → 27/36, 650 | 23/42, 500 → 24/44, 800 |
| `Time` | 12.5 sp, 450, `fontFeatureSettings="tnum"` | Persian digits via locale formatting |

- Both are variable TTFs (OFL; licenses in `src/fonts/`). Ship as `res/font/readex_pro.ttf` and `vazirmatn.ttf`; on API 26+ use `Typeface.Builder(...).setFontVariationSettings("'wght' 560")`, cache one `Typeface` per (family, weight) in a `Fonts` singleton.
- **Script-aware family:** `Fonts.forText(CharSequence)` returns Vazirmatn when the string contains U+0600–06FF, else Readex. Use it for all user content (titles, artists, lyrics). UI strings use the locale's family.
- No letter-spacing on Persian; no all-caps anywhere. Balanced line breaks for lyrics (`Layout.BREAK_STRATEGY_BALANCED`, `HYPHENATION_FREQUENCY_NONE`).

## 4. Shape, spacing, elevation

| Element | Shape |
|---|---|
| Cover "sleeve" | radius by size: 36–48 dp → 8 · 56 → 10 · 132–152 → 12 · 264 → 16 · 340 (player) → 20 |
| Arch (طاق) | artists, Liked-songs window, empty-state frames, 2×2 widget. Path: apex at top centre, two cubic Béziers to the springing line at `s = w/2` (controls at `0.42·s` and `0.30·w`), straight jambs, foot radius 12–14 dp. `ArchOutlineProvider` (API 30+ `Outline.setPath`); below 30 clip via `canvas.clipPath` once into a cached bitmap. |
| Disc | every button (48 icon target, 56 small play, 64 transport side, 88 play) |
| Capsule | search field (52 h), output chip (40 h), Up-next strip (54 h), toast (48 h) |
| Deck | 26 dp rounded rect, 12 dp side inset, 22 dp above gesture bar |
| Sheets | 28 dp top corners |

Spacing: 4 dp grid; window gutter **20 dp** (16 for floating objects); rows 64 dp; section gap 20–24 dp; ≥ 8 dp between touch targets; all targets ≥ 48 dp (ui-ux-pro-max: touch target/spacing).
Elevation: only things that float cast a shadow (Deck, dragged row, toast, player cover, play disc's colored drop light). Use `View.setElevation` + `setOutlineSpotShadowColor(dyn)` (API 28+) for colored shadows — RenderThread-drawn, cheap.

## 5. Motion language (`res/values/motion.xml` + `Motion.java`)

Character: **"lamp and string"** — things light up and settle like a plucked string: quick, slightly springy, never bouncy-cartoon. Implemented with `androidx.dynamicanimation` `SpringAnimation` (Java) and `ValueAnimator` + `PathInterpolator`.

| Token | Value | Used for |
|---|---|---|
| `spring_press` | stiffness 1500, damping 0.8 | press squash to 0.94 and release |
| `spring_morph` | stiffness 700, damping 0.72 | play/pause glyph, wave-tick travel |
| `spring_sheet` | stiffness 380, damping 0.86 | queue sheet, player expand/collapse, toast rise |
| `ease_emph` | `PathInterpolator(0.2, 0, 0, 1)` | crossfades, track change |
| `dur_fast` 160 ms · `dur_med` 280 ms · `dur_slow` 420 ms · `dur_color` 600 ms | | exits use `dur_fast` (exit faster than enter) |

Moments: press feedback ≤ 100 ms (scale .94 + fill +10%); play toggles → glyph morph + **one** halo ripple ring (420 ms); wave travels 1 wavelength / 1.8 s while playing; pause eases amplitude → 0 (280 ms); like → heart fills bottom-up + 6 sparks (360 ms) + `HapticFeedbackConstants.CONFIRM`; seek drag → thumb grows, bubble, Bars lens, haptic tick per 10 s; track change → cover slides 24 dp in the skip direction + fade, colors crossfade 600 ms, backdrop crossfade 800 ms, new loudness bars grow left→right once (360 ms); nav → wave tick draws in (180 ms); lyrics → active line weight/alpha 250 ms, list scroll 450 ms `ease_emph`, auto-scroll pauses 3 s after a manual scroll; queue drag → lift (scale 1.03, −1.2° tilt, elevation) 150 ms. At most **two** things animate on a screen at once (ui-ux-pro-max: excessive motion).

## 6. Battery and RAM budget (owner D6, D8) — and the Still mode

**Default ("Full") is already cheap:**
- **Backdrop** = the cover decoded at **64×64**, stack-blurred once off the main thread (radius 8), stored as one 16 KB ARGB bitmap, drawn scaled to the screen with `FILTER_BITMAP_FLAG` + dither, then tint + scrim as one `LinearGradient` shader. **No live RenderEffect/blur, no full-screen offscreen layer.** Two bitmaps exist only during the 800 ms crossfade (32 KB). The same bitmap feeds the cover glow, the Deck mini-row wash and palette extraction.
- **Grain**: one 128×128 `ALPHA_8` tile (16 KB) with `BitmapShader` REPEAT at 11% overlay. Dropped in Still.
- **Covers**: decoded at exact view size (row 48 dp ≈ 144 px ≈ 81 KB; player 340 dp ≈ 950 px ≈ 3.5 MB, only the current one). Thumbnail `LruCache` capped at `min(memoryClass/16, 12 MB)`. Use the loader the T2 agent picks (Glide is Java-friendly) with these sizes, or a small in-house loader.
- **Loudness data**: one `byte[]` of 200 buckets per track (200 B), cached in SQLite; computed by T-W in WorkManager **only while charging/idle**, never during playback start. Until it exists, Waveform/Bars draw the "not measured" state and still seek.
- **Paths are built once** per (track, width) and reused; per frame we only translate a phase or move a clip rect. Zero allocations in `onDraw`.
- **Animation runs only when** playing **and** the view is attached, visible and the screen is on (`onVisibilityAggregated`, `onWindowFocusChanged`). Wave phase is capped at **30 fps** (`postInvalidateDelayed(33)`), only the seek view invalidates. Deck progress hills update at **1 Hz**. Position UI ticks at 4 Hz max (ARCHITECTURE).
- Lyrics fade uses `requiresFadingEdge` on the RecyclerView (no custom saveLayer).

**Still mode** (Settings → Motion & effects: *Auto / Full / Still*; Auto = Still when `PowerManager.isPowerSaveMode()` or `ANIMATOR_DURATION_SCALE == 0`):
| Element | Full | Still |
|---|---|---|
| Player backdrop | 64 px pre-blurred bitmap + tint + grain | vertical gradient `dynInk` → `ink0` (no bitmap) |
| Cover glow | blurred bitmap under the cover | off (plain shadow) |
| Wavy seek | travelling wave | static wave (frozen phase) |
| Waveform / Bars | static drawing (same) + lens on drag | same, no lens animation, bubble only |
| Play disc | morph + one ripple ring | instant glyph swap, no ring |
| Live bars on now-playing row | 4 bars bounce (only while visible) | static bars |
| Lyrics | word sweep | line-level highlight only |
| Crossfades | 600–800 ms | 150 ms |
| Home glow | radial gradient (static draw) | same (it is static) |
| BarsLoader | breathing | static bars + text |

## 7. Components → Java Views (`ui/widget/`, `res/layout`, `res/drawable`)

| Component | Class / resource | Spec |
|---|---|---|
| Theme | `Theme.Shab` (parent `Theme.Material3.DayNight.NoActionBar`, invisible base) + `Theme.Shab.Black` | maps tokens to `colorSurface` etc. only so framework dialogs look right; we never use stock M3 widgets on screen |
| Icons | `res/drawable/ic_*.xml` VectorDrawables from `lib.js ICONS` (same path data, 24 vp, stroke 1.8, round caps) | `.f` variants filled for selected nav / liked |
| Lamp play disc | `MorphPlayPauseView` | circle `dyn` + radial highlight (`RadialGradient`, top 12%), 8 dp halo ring `dyn`@13%, colored elevation shadow; glyph = 2 interpolated quads (see 16) + `CornerPathEffect(2dp)`; `SpringAnimation` on t; buffering = 3 dp arc on the halo after 400 ms; sizes 88 / 56 / 46 |
| Disc buttons | `DiscButton` (extends `AppCompatImageButton`) | glass fill, inner 1 dp line, press scale .94 + fill 18%; 64 dp transport, 48 icon |
| Toggle with lit dot | `LitToggleButton` | glyph tinted `dyn`/`accent` when on + 4 dp dot 5 dp under the glyph with a 6 dp glow; content description includes state |
| Wavy seek | `WavySeekView` | stroke 3.5, amplitude 3.4, wavelength 28, played wave / unplayed straight @24%; thumb disc 16 (white ring 3.2, `dyn` core) → 24 + halo on drag |
| Waveform seek | `WaveformSeekView` | 3 layers of Gaussian hills from loudness (13/10/17 hills, scales 1/.82/.6), played `dyn` @38/50/gradient, unplayed white @11%, 3 dp baseline, same thumb |
| Bars seek | `BarsSeekView` | 3 dp bars, 2.6 gap, height = min 3 dp + loudness^1.5 × 0.9 × h (h = 40–44), centred; played `dyn`, current bar white, unplayed white @26%; drag lens ×1.5 Gaussian σ 22 dp + 2 dp playhead; "not measured" = flat 12% bars |
| Common seek base | `BaseSeekView` | `AccessibilityNodeInfo.RangeInfo`, `ACTION_SCROLL_FORWARD/BACKWARD` = ±10 s, haptic ticks, time bubble `SeekBubbleView`, RTL = still LTR (K5) |
| Deck | `DeckView` (`res/layout/view_deck.xml`) | `ink1` 26 dp, 1 dp `line` stroke, shadow; nav row 64 dp (4 items: icon 24 + label 11.5 sp, selected = filled icon `accent` + `WaveTickDrawable`); optional mini row 68 dp: 46 art, title/artist, 46 play disc, next; wash = backdrop bitmap + `dynInk` gradient; seam progress = 12 dp hills (`WaveformSeekView` in mini mode, 1 Hz) |
| Wave tick | `WaveTickDrawable` | 18×6 dp, 2 dp stroke, `accent`; draw-in 180 ms |
| Tab strip | `TabStripView` (HorizontalScrollView of TextViews) | 15 sp, selected 600 + tick; count in `text3` 12 sp; fading edge |
| LampSwitch | `LampSwitch` (extends `CompoundButton`, custom draw) | track 46×28 capsule (`ink2`, `line2` stroke) / on: `accentSoft` + accent stroke; knob off = 16 dp hollow ring `text3`, on = 20 dp `accent` disc with glow; 48 dp touch target |
| Rows | `TrackRowView` + `item_track.xml` | 64 dp, art 48 sleeve, title/meta, duration `text3`, "⋯" 48 dp; now-playing = accent title + `LiveBarsView`; pressed = `ink2` wash |
| Arch image | `ArchImageView` + `ArchOutlineProvider` | see §4; mosaic variant = 2×2 covers inside one arch (`ArchMosaicView`) |
| Vinyl sleeve | `SleeveWithRecordView` | cover + record disc drawn with `RadialGradient` grooves + label colored by `dyn`, peeking 30 dp on the end side |
| Player backdrop | `BackdropView` | §6 |
| Lyrics | `LyricsRecyclerView` + `LyricLineView` | centered, active line `LyricActive` with `LinearGradient` text shader for the word sweep (only if LRC has word timings) |
| Queue | `QueueSheetBehavior` (BottomSheetBehavior subclass, our skin) + `ItemTouchHelper` (drag with tilt, swipe end→start reveals `dangerInk` + trash) | Accessibility actions "Move up / Move down / Remove" on each row (ui-ux-pro-max: dragging alternative) |
| Toast | `ToastCapsuleView` | `ink3`, `line2`, accent action pill; 5 s with Undo; replaces Snackbar |
| Loader | `BarsLoaderView` | 5 bars 4 dp, heights breathe out of phase (900 ms loop), static in Still |
| Up-next strip | `UpNextView` | capsule glass, 36 round art, "Up next" 11.5 sp + title; tap/swipe-up opens queue |
| Notification | Media3 `MediaSessionService` + `DefaultMediaNotificationProvider`; custom commands Like + Shuffle; small icon `ic_app` (arch + bars) | §8 |
| Widget | `AppWidgetProvider` + RemoteViews (Glance is Kotlin-only) | 4×2 / 2×2; bars progress = pre-rendered bitmap updated on track/state change only |

## 8. Notification, lock screen, widget
Unchanged facts from v1: on Android 13+ SystemUI/One UI draws the media card from our `MediaSession` (we control artwork ≥ 720 px, metadata, state, Like/Shuffle commands, small icon). Android 8–12: `MediaStyle` + `setColorized(true)` with `dynInk`. Samsung Now Bar = One UI rendering of the same session (approximation in 12). The widget is fully ours (12, column 4).

## 9. RTL / Persian
Mirrors: layout, nav order, headers, carousels, record peeks, top player row, chevrons (`android:autoMirrored="true"`), lyrics icon. Not mirrored: transport order, play triangle, all three seek bars and their fill (K5, tape direction). Persian digits through locale formatting; ZWNJ preserved; Persian copy is model-written → native check by the owner.

## 10. Accessibility
Targets ≥ 48 dp, ≥ 8 dp apart; content descriptions with state ("Shuffle, on", "Sleep timer, 23 minutes left"); nav labels always visible; seek bars expose range + ±10 s actions; drag alternatives in the queue; font scale 200%: rows grow (no fixed text heights), the player's upper half shrinks first; Still mode covers reduced motion. Screen-reader order = visual order (top row, cover, title, seek, transport, actions, Up next).

## 11. ui-ux-pro-max (owner D10): what was adopted
Ran `--design-system "music player dark immersive artwork"` and `--domain ux` queries (touch target, animation timing, reduced motion, dragging movements, gesture conflicts) + `pro-rules.md` checklist.
- **Adopted:** 48 dp targets and 8 dp spacing; press feedback within ~100 ms without layout shift (scale transform only); exit faster than enter; continuous animation only as a state indicator (wave only while playing) and ≤ 2 animated elements per view; reduced-motion honored (Still mode); single-pointer alternatives to drag (Move up/down); one primary gesture per region and edge clearance for system back; consistent icon family/stroke; separate dark/light contrast checks.
- **Rejected:** its generated palette/typography (Poppins + Righteous, green play button, landing-page section pattern): generic web output that would erase the identity the owner asked for.

## 12. Self-critique record (design-atelier, rendered PNGs inspected)
| Perspective | Finding (evidence) | Fix | Recheck |
|---|---|---|---|
| Professional | Waveform read as a grey plateau (05 round 1) | rebuilt as layered Gaussian hills like Melodiha | fixed (05, 16) |
| Professional | Bars looked uniform, lens invisible (17 round 1) | loudness^1.5 contrast, per-bar variation, lens ×1.5 | fixed (17) |
| Professional | Vinyl records invisible on Ink (01 round 1) | lighter grooves, sheen, 44 dp peek | fixed (01) |
| Professional | Lyrics icon read as a chat bubble (05 round 1) | lines + note glyph | fixed |
| Professional | Queue header title truncated "Without …" (07) | removed prev from compact header | fixed |
| Professional | Lyrics-offline "Try again" overlapped the title (13) | moved state block up | fixed |
| Beginner | Unlabeled nav icons (v1) | labels always visible in the Deck | fixed |
| Beginner | How to reach the queue? | visible "Up next" strip with chevron | fixed (05/06/17) |
| Experienced | Transport layout familiar (shuffle · prev · play · next · repeat) | kept on purpose | — |
| Experienced | Controls jump between cover and lyrics views (v1) | same y in all player views | fixed |
| Authorship | Stock M3 silhouettes (pill nav, cookie, chips, switches, segmented lists, Material Symbols, Google Sans) | all replaced (§0) | 00 overview |
| Kept on purpose | dark ground (night use, reference), grain only on artwork backdrops, generated covers drawn per subject | | |

## 13. Owner decisions (open)
- **D-A** RTL playback direction stays LTR (tape order) — confirm.
- **D-B** Default seek style: Wavy (cheapest, works before loudness is measured) vs Waveform/Bars (need the one-time measurement). Recommendation: **Wavy** default; Bars is the showpiece in Settings.
- **D-C** App name + icon (mock uses "Music Player" and the arch-with-bars glyph).
- **D-E** Lyrics centered (as mocked) or start-aligned.
- **D-F (new)** Liked color: saffron in the app chrome, artwork color on the player (as mocked) — or saffron everywhere?

## 14. Not verified
- Browser mockups at 412×915 css px (≈ dp), Chromium; not compiled or run on Android: no TalkBack, performance, battery or real SystemUI evidence. The RAM/battery numbers above are budgets/estimates, to be measured in T9.
- Narrow phones (360 dp) and landscape/tablet were not mocked: transport gaps shrink to 8 dp and the cover scales to width − 72 dp; tablet = cover left / lyrics right ≥ 600 dp.
- Not mocked: overflow menus, sleep-timer sheet, add-to-playlist dialog, permission denied forever, empty playlist / empty Liked songs.
- Persian copy not reviewed by a native speaker. Contrast over artwork measured on sample covers only (runtime guard required).
