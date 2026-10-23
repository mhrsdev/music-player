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
| 6 | Cookie button spin (`MorphPlayPauseView`), mini-player and player, 20 fps while playing | rebuilt a 121-point path with 2 `pow` + 4 trig per point per frame; v0.2 added `radiusRotated` (more trig) | Real but small | settled squircle / cookie cached; the playing cookie is drawn with `canvas.rotate` on the cached path (a cookie only turns rigidly, so identical pixels); the morph transition still computes per frame |
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

## Not changed on purpose / next

* One shared frame driver for all animated views: the cookie spin (20 fps) and the player wave (30 fps) still post
  their own invalidates; M1's `ui/motion/FrameDriver` (branch `task/m1-motion`) should drive them once merged.
* Persistent "no artwork" memory and one decode shared across size buckets (see #2).
* `QueueViewModel` per-tick mapping (#9); `SearchResultAdapter` still formats its "More" label per bind.
* Colour extraction is still per artwork bind in the player and `ThemeController`; share one result per artwork key.
