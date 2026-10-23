# Perf regression v0.1.0 -> v0.2.0 (slow phone lags)

Reported: v0.2.0 is much laggier than v0.1.0 on a low-end phone. No device is available to the agents, so this is a
code-reading investigation (`git diff v0.1.0..v0.2.0`, 20 files under ui/widget, ui/nav, ui/home, ui/player changed)
plus JVM/Robolectric measurements of the counted work. Owner rule applied: **no visual is removed or simplified**;
work is made cheaper or removed where nothing needs it.

To collect real data: debug build, `adb logcat -s MowjJank` (every frame over 16.7 ms with screen name and the
layout / draw / sync / GPU split, `debug/JankLogger.java`, installed only when `BuildConfig.DEBUG`).

## Ranked suspects

| # | Suspect | Evidence | Verdict | Action |
|---|---------|----------|---------|--------|
| 1 | **Mini-player repainted in full every 250 ms** (it is on every screen while music plays) | `MainActivity.renderMini` calls `mini.setPlaying()` on every state tick; `MiniPlayerView.setPlaying` always called `invalidate()`; `setProgress` invalidated the whole pill; `onDraw` rebuilt the wave path (about 150 `sin` calls) each time. The pill is opaque, elevated (shadow) with a gradient, art and two texts: all redrawn 4 times a second although only a 2 px line moves. v0.2 made the pill opaque and added the progress inset, so the draw got heavier | **Confirmed regression (CPU+GPU, constant while playing)** | `setPlaying` no-op when unchanged; `setProgress` invalidates only the progress strip rectangle; wave path cached per progress pixel; `askNotificationsOnce` (permission IPC + prefs read) now once instead of at 4 Hz. Test `MiniPlayerInvalidationTest`: 400 ticks (100 s) -> 0 whole-view invalidations |
| 2 | **Artwork pipeline contention** (U3 `ArtworkResolver`, new in v0.2) | v0.1 opened one URI. v0.2 per cover: `loadThumbnail`, legacy open, then up to 4 tracks x (thumbnail + `MediaMetadataRetriever` + 12 file probes), on 2 worker threads regardless of core count; the same cover asked at 3 sizes (row / card / player) ran the whole probe 3 times in parallel; cover-less albums were probed again after every process start | **Likely major on a 2-4 core phone** | workers = clamp(cores - 1, 1, 2); striped per-cover lock so later sizes find the miss/winner instead of re-probing; resolver remembers the winning track per album (`secondSizeOfAnAlbumSkipsTheFailedProbes`: second size reads 1 track instead of 3). **Not done yet:** persistent miss cache across restarts, decode once at the largest size and derive smaller ones |
| 3 | **Hero cover decode size/format and cache thrash** | hero card is about 300 dp = 800-1000 px; `prepare()` used ARGB_8888 above 320 px = 3-4 MB per cover; the LruCache is `heap/16` = 8 MB on a 128 MB heap, i.e. two covers; every scroll evicted and re-decoded (through the heavy chain above) | **Likely major on low-heap phones** | opaque JPEG decoded RGB_565 up to 1100 px (half the memory, decoder dithers; alpha PNGs unchanged); cache floor 6 MB. Visual: no alpha lost; 565 gradient banding is the only possible difference, not visible on photos |
| 4 | **Hero carousel lays out a whole extra screen on each side** | `HeroCarousel.calculateExtraLayoutSpace` returned `getWidth()` both ways: 2-3 invisible full-size cards bound and decoded for every position (was needed for the v0.1 translated tail cards; v0.2 has none). `apply()` ran `findViewById` x3 per card per scroll frame, set alpha/scale/a11y mode even when unchanged, and the layout listener posted a new lambda per layout | Confirmed waste, smaller effect | extra space = the padding zone where the peeking card sits (+ gap); lookups cached on the card; setters only when the value changed; one pending apply. `HeroScrollCostTest`: initial binds 3 -> 2 (300 dp card, 411 dp screen), no layout request per scroll frame (5 requests in 120 frames, all from entering cards) |
| 5 | Track rows: `Format.duration` built two ICU `NumberFormat` objects per row bind; `getString(R.string.library_more, title)` = `String.format` per bind | `ui/library/Format.java`, `TrackAdapter.onBindViewHolder`, `HomeAdapter` | Confirmed per-bind cost (also in v0.1 for the first), hits every list on every scroll | Format caches the locale's zero digit and builds the string by hand (identical output in en/fa/ar/de, `FormatTest`); the "More options for <title>" label is built on demand by an accessibility delegate (TalkBack only) |
| 6 | Cookie button spin (`MorphPlayPauseView`) | v0.2 rebuilt a 121-point trig path 20 times a second | **Gone**: M1 replaced the cookie/spin with a spring morph that only draws while moving. My cached-shape change was dropped in the merge | M1 version taken as is; its buffering arc now rides the shared `FrameDriver` (was `postInvalidateOnAnimation`) |
| 7 | Home glow, bottom scrim, dock (`MainActivity`, `HomeGlow`, `BottomChrome`) | glow is one static `GradientDrawable` on a `View` sized once in the inset callback (no per-scroll work); scrim is a cached shader; `BottomChrome.apply` runs only when the dock height changes (`px == dockPx` guard) and skips RecyclerView subtrees | **Not a cause**; the opaque nav/mini pills added two elevated layers over lists (GPU shadow cost is constant, outlines cached) | no change |
| 8 | Skeleton shimmer 15 fps | shared single tick, gated on attached + visible + motion allowed (`SkeletonView.sync`) | Not a cause unless a skeleton stays visible | no change |
| 9 | Playback state fan-out | lists do **not** observe `PlaybackState` (only mini-player, player, queue); `QueueViewModel` maps each tick to a new `UiState` (O(queue) `remainingQueueMs`) while the queue screen is open | Minor, queue screen only | open item |
| 10 | StrictMode in release | `MusicPlayerApp.onCreate` guards it with `BuildConfig.DEBUG` | Not a cause | none |

## Measured / counted

| What | Before | After | How |
|------|--------|-------|-----|
| Whole-view mini-player invalidations over 100 s of playback | 400+ (one per tick, plus per `setPlaying`) | 0 (strip rect only, at most one per pixel of progress) | `MiniPlayerInvalidationTest` |
| Mini-player wave `sin` calls per draw | about 150 per draw | 0 unless the progress pixel moved | code |
| Cookie container path build while playing | 121 points x ~6 transcendental calls x 20 fps | 0 (canvas rotate) | code |
| Hero cards bound for the first screen | 3 | 2 | `HeroScrollCostTest` |
| `NumberFormat` objects per track-row bind | 2 | 0 | code, `FormatTest` equality |
| `String.format` per track-row bind | 1 | 0 | code |
| Probes for the same album at a second size | all failed tracks again | straight to the winner (1 vs 3 embedded-picture reads in the test) | `ArtworkResolverTest` |
| Hero cover memory | about 3.3 MB (ARGB_8888, 910 px) | about 1.7 MB | calculation |
| Permission IPC at 4 Hz while playing | 4 per second | 1 per process | code |

Frame-time numbers on the friend's phone are unknown: ask for `adb logcat -s MowjJank` while scrolling Home and while
playing with the mini-player visible.

## Second pass (after M1 / H1 / G1 / L1 / S1 landed on ccr)

Merged ccr into this branch (M1 `MorphPlayPauseView` and H1 `HeroCarousel` taken, then the still-valid optimisations
re-applied on top; G1 `ArtworkLoader` kept with its generated-cover path).

| Area (new code) | Finding | Change |
|-----------------|---------|--------|
| Animation loops | five private loops: `BaseSeekView` 30 fps (`postInvalidateDelayed`), `AmbientBackdropView` and `PlayerBackdropView` drift (`postOnAnimationDelayed`), `LyricsTicker` 30 fps, `SkeletonView` 15 fps (Handler), plus the buffering arc at 60 fps | all on **one Choreographer callback**: new `ui/motion/PeriodicTick` (fixed rate on M1's `FrameDriver`). Same rates, same gates (visible / playing / attached); stop on detach. `PerfBudgetTest.loopingAnimationsRideTheSharedFrameDriver` fails the build on a new private loop. `PeriodicTickTest`: 66 ms tick = 15 body calls per second of 60 Hz frames, nothing registered after `stop()` |
| Artwork | decode keyed by exact view size: row (137 px), card, mini-player and player of one cover = 3-4 decodes | `ArtworkLoader.key()` and the decode use G1's size buckets (64..1024): one decode per cover per bucket, shared by every view in it. Real art only: generated covers already used buckets |
| Colour extraction | `ColorExtractor.extractSeed` ran again for the same cover in Home tint, mini-player, player, widget | memoised on a 64-bit hash of the (downscaled) pixels, 24 entries; identical result by construction (`compute` unchanged) |
| Queue screen | `QueueViewModel` rebuilt and re-rendered (NumberFormat, adapter submit) on every 4 Hz state tick | emits only when the mapped `UiState` changed (distinct); 0 renders per second while playing instead of 4 |
| Search rows | `getString(..., title)` (a `String.format`) per bind | label built on demand by an accessibility delegate |
| Hero carousel (H1) | still laid out a full extra screen each side, looked children up 3x per card per frame, re-set pivot/scale/alpha/a11y every frame | reserved zone = padding + gap, cached lookups, setters only on change, one pending apply per layout (`HeroScrollCostTest`: first-screen binds 3 -> 2) |
| L1 lyrics / ambient | `LyricsTicker` 30 fps only while list visible + playing + active line has word timing; `AmbientBackdropView` 15 fps only visible + playing (FULL mode) | both on the shared driver now; gates unchanged |
| G1 generated covers | rendered on the artwork worker thread; at most `SYNC_MAX_PX` = 96 px synchronously on the main thread | no change needed |
| H1 Home feed / S1 widgets / U1b system bars | Home feed query on the io executor; widgets pushed by `NowPlayingBridge` per playback event (coalesced), `updatePeriodMillis=0`; system bars set once per window | no change; widget/bridge allow-listed in `PerfBudgetTest` |
| Foreground update check | `AppContainer.onAppForeground` -> `updates().checkIfDue()` (at most daily, foreground only) | documented in BATTERY_AUDIT B8 |

Both variants checked: `compileDebugJavaWithJavac` + `compileReleaseJavaWithJavac`, targeted tests in debug and release
(`PeriodicTickTest`, `PerfBudgetTest`, `HeroScrollCostTest`, `MiniPlayerInvalidationTest`, `FormatTest`, resolver, queue).

## Not changed on purpose / next

* `ArtworkView`'s 150 ms fade-in still uses `postInvalidateOnAnimation` (one-shot, ends by itself).
* Persistent "no artwork" memory and one decode shared across size buckets (see #2).
* The ambient palette (`AmbientPalette`) is still built per layer on the main thread (small bitmap, cached per key).
