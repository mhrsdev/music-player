# Scroll path v0.3.3 (SC1, owner D39 + D40)

Owner report: "scrolling lags everywhere in the app" on a Galaxy S26 Ultra (Android 17, 120 Hz), plus heat and
6 % battery in 5 minutes (D40). His library is mostly cover-less Telegram downloads, so a generated cover sits in
nearly every row. No device or profiler was available: everything below was measured on the JVM (Robolectric 4.16,
native graphics, software canvas) or counted, and says which.

## How it was measured

* `ui/widget/ScrollBench` (test harness): an owner-shaped library (2,000 cover-less tracks in one "Telegram" folder
  album with Persian / Latin file-name titles + 200 tracks in 20 covered albums), an `ArtworkLoader` whose fake
  platform finds covers only for those 20 albums, and timers for one row: create, bind, measure+layout, draw (software
  canvas), each with the bytes the main thread allocated (`ThreadMXBean.getCurrentThreadAllocatedBytes`). Rows are
  bound round-robin to real positions after a warm-up round, artwork deliveries are drained between binds, so the cover
  cache is in the state a scroll sees. Density xxhdpi (3.0; the S26 Ultra at FHD+ is 2.8).
* Benchmarks: `HomeScrollBenchTest` (every Home row type incl. hero carousel, card rows, Continue, stats),
  `LibraryScrollBenchTest` (Songs / track lists, Albums and Artists grids, a fast fling), `SearchScrollBenchTest`,
  `QueueScrollBenchTest`, `CoverScrollBenchTest` (generated-cover renders per size, `CoverSpec`, `ArtworkView` bind).
  The suite runs a light pass that only asserts the allocation budgets below (deterministic); `SC1_BENCH=1 ./gradlew
  :app:testDebugUnitTest --tests '*ScrollBenchTest'` prints the full numbers (timings, scroll frames, ~2 min).
* `IdleRedrawTest` (testDebug): the real shell on the fake library, nobody touching, a simulated 120 Hz vsync for 3 s
  per screen; a vsync with an invalidated view counts as a rendered frame and the invalidating views are listed.
* JFR (`-XX:StartFlightRecording`) on the benchmarks to see where bind time and bytes go.

**Read the timings with care.** The machine is shared with three other agents' Gradle builds (load 3.5-4.4 on 4 cores):
the same unchanged `GeneratedCover.render` measured 3.8 ms and 8.4 ms in two runs. Robolectric also adds a large fixed
cost per framework call (shadowed resources, JNI text layout): a `TextView.setText` that costs ~20-50 us on a phone
costs ~150-300 us here, and every `onCreateViewHolder` (5-35 ms, 5-15 MB) is dominated by Robolectric's class
instrumentation and call-site linking (JFR: 66 % `InvokeDynamicSupport`, 34 % class loading), not by the app. **Bytes
per bind are the reliable number**; timings are only compared within one run.

## 1. Findings, ranked

| # | Finding | Where | Measured (JVM, per row) | Severity |
|---|---------|-------|-------------------------|----------|
| 1 | **Home stats row rebuilt on every bind**: `removeAllViews` + 4 cells of 2 new TextViews with text appearances each time the row scrolled into view | `HomeAdapter.bindStats` | bind **13.3 ms, 14.4 MB** | High (the one Home row that alone exceeds a 120 Hz frame) |
| 2 | **Home's horizontal rows shared one view type**: a carousel scrolled back into view was usually given another section's row, so its inner RecyclerView re-laid out all cards, diffed the new list (off-thread) and ran card add/remove animations while the user scrolled; also carried the other section's scroll offset | `HomeAdapter.getItemViewType` / carousel bind | measure+layout **2.0 ms, 745 KB** (chips 1.5 ms, 445 KB) | High (every carousel entering the screen; Home has ~12) |
| 3 | **Continue card fully rebound every 2 % of the song** (~every 4 s while music plays): `notifyItemChanged` without payload = cover bind + texts + a change crossfade with a second holder + a predictive layout pass of the whole Home list, in the middle of any scroll | `HomeAdapter.setNowPlaying` | bind 1.6 ms, 23 KB + a full Home layout, every ~4 s | High (periodic hitch on Home while playing) |
| 4 | **Queue rows rebuilt their accessibility actions, labels and number formats per bind** (3 `replaceAccessibilityAction` with new lambdas, 3-4 `getString`, 2 theme colour look-ups, 2 `NumberFormat` instances via `PlayerFormat.time`) | `QueueAdapter.onBindViewHolder`, `PlayerFormat.time` | bind **61 KB** | Medium |
| 5 | **Queue sheet rebound every row once a minute** while playing: the "N min left" label changes, the fragment re-submitted the same list and the adapter did `notifyDataSetChanged` | `QueueAdapter.submit` | all visible rows + full layout per minute | Medium |
| 6 | **Persian / Latin rows laid their text out twice**: `ScriptFont.setText` set the text, then switched the typeface, which throws the new layout away (`nullLayouts` + `requestLayout`) whenever the script differs from the previous row - the owner's library alternates constantly | `ScriptFont.setText` (every list) | part of the songs row's 10 KB / 0.66 ms | Medium |
| 7 | **Cover work for rows nobody sees**: a fast fling over cover-less rows queued a generated-cover render (192 px: 3.8-8.4 ms on the JVM worker, 72 KB bitmap) for every row that entered; renders already started finish even when the row left; recycling a Songs row bound it to the "seed 0" cover, which requested yet another render | `ArtworkView`, `TrackAdapter.onViewRecycled` | fling simulation: 29 renders for ~43 rows passed (workers saturated) | Medium (CPU, heat, native memory churn; not the main thread) |
| 8 | **Bucket sizes 2x the pixels needed**: a 52 dp row (146-156 px) rendered/decoded at 192 px; an Albums cell (~540 px) at 768 px (1.18 MB RGB_565: the 16 MB cache held 13 grid covers) | `ArtworkLoader.BUCKETS` | +44 % / +100 % pixels per cover | Medium (memory, re-renders when scrolling back) |
| 9 | Per-bind string work in the artwork path: the cover spec (NFKC normalisation + two hashes) recomputed on every bind, 2-3 cache keys built per bind, `setZoom` / `setCircle` / `setCornerRadiusDp` invalidating on every bind | `CoverSpec.forTrack`, `ArtworkView`, `Art.bind` | 4.0 KB, 14.6 us per cached bind | Low |
| 10 | Home track rows: `getString(format)` for the overflow label + two new click lambdas per bind | `HomeAdapter` | 18.7 KB vs 10 KB in Songs | Low |
| 11 | Search: a query keystroke rebound every visible row through a change crossfade (second set of holders) | `SearchResultAdapter.setQuery` | n x full bind + crossfade per keystroke | Low |
| 12 | `setHasFixedSize` missing on match_parent lists (Home, Search, grids): an adapter change re-laid out the shell above the list | fragments | layout of the parents per change | Low |

Looked at and found clean (no change): row layouts are 1-4 levels deep, LinearLayout/FrameLayout only, no elevation,
outline clip, auto-size, marquee or per-view font variation (`RowLayoutBudgetTest` now pins this); the variable font is
picked once per weight by the font-family XML; `ArtworkView` draws one rounded rect with a `BitmapShader` (no
`clipPath`); Track lists diff off the main thread, have stable rows and payload selection updates; nested Home rows
share one `RecycledViewPool` and prefetch 4 cards; `HeroCarousel.apply` touches 2-3 cards per scroll frame with no
allocation; the dock and mini-player are opaque; the window background is not drawn under the root; `FastScrollerView`
does a few flag updates per scroll event. **No generated cover is rendered on the main thread at phone densities**:
the synchronous path is for buckets <= 96 px, and every list-row, card and grid cover is >= 128 px at 2.6x and up
(`CoverScrollBenchTest.bindNeverRendersOnTheMainThread`). Only the Library hub's 36 dp peeks (94 px at 2.625x) can still
render synchronously, once per cover, outside any list.

## 2. What changed (D24: no effect removed)

| Fix | Files |
|-----|-------|
| Stats cells built once per holder; a bind sets four texts | `HomeAdapter.bindStats/addStat/setStat` |
| One view type per section for carousels, hero, avatars and chips; a rebind with the same feed list is a no-op (`HomeCardAdapter.submitPreview`); each row keeps its own scroll position | `HomeAdapter.getItemViewType`, `HomeCardAdapter` |
| Continue progress goes straight to the bound card (`bindProgress`: visibility + RenderNode scale, no layout); otherwise a payload rebind of only the strip | `HomeAdapter.setNowPlaying` |
| Home track rows: listeners and the TalkBack label per holder | `HomeAdapter.trackHolder` |
| Section headers rebound to their own section skip the bind | `HomeAdapter` |
| Queue rows: actions, listeners, colours and the "Now playing" string per holder; actions removed only for the current row; TalkBack labels built on demand. `PlayerFormat.time` = `Format.duration` (same text, no `NumberFormat`); the adapter ignores a re-submit of the same queue | `QueueAdapter`, `PlayerFormat` |
| `ScriptFont.setText` applies the typeface before the text | `ScriptFont` |
| `FlingGate`: while a list (or any list around a nested one) is in a fast fling (SETTLING and > 8 dp per frame), rows that miss the cache hold their decode / generated render; the held rows load as soon as the fling ends or slows to a readable speed. Cached covers still appear at once; during the fast part a new row shows the same quiet loading tone it showed before its cover arrived | `ui/widget/FlingGate` (new), `ArtworkView.holdWhileFlinging/resumeLoad` |
| `ArtworkView.clear()` for recycled rows (no seed-cover render for a pooled view); setters return early when unchanged; one cache key per bind (`cachedKey`, key-taking `generatedNow` / `loadGenerated`) | `ArtworkView`, `ArtworkLoader`, `TrackAdapter` |
| New buckets 160 and 640 px: a row cover is 160 px (50 KB instead of 72 KB), an Albums cell 640 px (0.82 MB instead of 1.18 MB): ~45 % more row covers and ~40 % more grid covers fit the same 16 MB cache, fewer re-renders when scrolling back | `ArtworkLoader.BUCKETS` |
| `CoverSpec.forTrack(Track)` remembers its last 512 answers (rows scrolled back and forth) | `CoverSpec` |
| Search query changes rebind visible rows in place (payload, no crossfade) | `SearchResultAdapter` |
| `setHasFixedSize(true)` on Home, Search results, Albums/Artists/Playlists grids | `HomeFragment`, `SearchFragment`, `CollectionFragment` |
| `FrameDriver`: sleep between beats clamped to one beat; the clock is a seam so the idle test can run the beat on Robolectric's simulated vsync | `FrameDriver` |

## 3. Before / after (same benchmark; JVM, xxhdpi, per row)

Bytes = main-thread bytes allocated per call (deterministic). Timings: two runs on a loaded shared machine, so treat
differences below ~2x as noise.

| Row | bind before | bind after | measure+layout before | measure+layout after |
|-----|-------------|------------|-----------------------|----------------------|
| Home stats | 13,265 us / **14,441,176 B** | 1,372 us / **34,944 B** | 1,521 us / 25,692 B | 141 us / 16 B |
| Home carousel (cards), rebind | 57 us / 222 B | 7 us / 40 B | 1,999 us / **745,334 B** | 423 us / **163 B** |
| Home chips, rebind | 55 us / 136 B | 3 us / 37 B | 1,485 us / **445,075 B** | 266 us / **52 B** |
| Home Continue, per 2 % progress step | full bind 1,551 us / 22,831 B + change animation + Home layout pass | **0 binds**: 2 property sets on the bound card | | |
| Home track row | 1,767 us / 18,739 B | 1,391 us / **7,262 B** | 1,963 us / 11,291 B | 2,850 us / 11,454 B |
| Home header (new section) | 786 us / 15,919 B | 1,166 us / 15,924 B (own section: skipped) | 642 us / 1,213 B | 824 us / 1,224 B |
| Hero card | 1,238 us / 11,305 B | 1,244 us / 7,502 B | 287 us / 3,476 B | 648 us / 3,539 B |
| Home card | 595 us / 10,643 B | 1,085 us / 8,178 B | 152 us / 2,170 B | 382 us / 2,202 B |
| Songs / track list row | 659 us / 10,128 B | **201 us / 3,344 B** | 644 us / 13,376 B | 589 us / 13,505 B |
| Search result row | 1,169 us / 10,551 B | 655 us / 3,766 B | 999 us / 13,429 B | 849 us / 13,558 B |
| Queue row | 567 us / **60,670 B** | 271 us / **7,645 B** | 435 us / 21,515 B | 391 us / 21,738 B |
| Albums grid cell | 613 us / 11,808 B | 682 us / 4,919 B | 382 us / 4,517 B | 667 us / 4,597 B |
| Artists grid cell | 217 us / 2,664 B | 282 us / 1,656 B | 134 us / 305 B | 166 us / 310 B |
| `ArtworkView.bind`, cached cover | 14.6 us / 3,999 B (192 px bitmap) | 19.0 us / **1,242 B** (160 px) | | |
| `CoverSpec.forTrack` | 3.6 us / 1,390 B every bind | same on a miss, **0 B** on a repeat bind | | |

Whole scroll frames (`scrollBy` incl. binds + layout of entering rows; prefetch is off in Robolectric, so every bind
lands in a frame): main-thread bytes per frame Home 552 KB -> 370 KB (the rest is Robolectric class loading /
inflation of new holders), Queue 13.4 KB -> 4.6 KB, Search 4.4 KB -> 3.2 KB, Songs 2.8 KB -> 2.0 KB, Albums 1.4 KB ->
1.0 KB. Mean frame times (0.2-1.0 ms) did not move beyond the noise; the worst Home frame (~30 ms) is a Robolectric
`onCreateViewHolder` and is unchanged.

Generated-cover renders on the artwork worker (baseline run, textless rows from 160 px up): 64 px 0.6 ms, 96 px 2.3,
128 px 2.6, 192 px 3.8, 256 px 4.5, 384 px 7.3, 512 px 9.4, 768 px 17.8, 1024 px 30.6 ms; ~15 KB Java heap per render
plus the bitmap. A cache hit is a map lookup.

Fast fling over the cover-less Songs list (14,000 px/s to rest in 1.4 s at 120 Hz, frames driven by hand because
Robolectric's own fling jumps its clock; the two workers run in real time beside the simulated frames): generated
renders for the fling **29 -> 25**. The simulation is worker-bound (the baseline's workers could not keep up, so many
rows were cancelled before starting), so it understates the saving on a phone whose workers keep up with a fling; the
gate removes every render for a row that enters during the fast phase and leaves the screen before it slows.

Against the coordinator's budget for a phone ~4x slower than this JVM (bind < 0.25 ms and < 2 KB, measure+layout and
draw < 0.3 ms each): **not met in absolute JVM numbers.** After the fixes no row allocates more than 8 KB per bind except
Home Continue/stats (rare binds), and the songs row binds in ~0.2 ms, but measure+layout stays 0.4-2.9 ms and
11-22 KB per row here, which is TextView measuring (StaticLayout / Minikin through Robolectric's JNI) for 3-4 text
views per row. That share cannot be separated from Robolectric's overhead without a device; PT1's recorder (below)
reports the real layout/measure and draw split per screen.

## 4. Idle redraw (D40): rendered frames per second, nobody touching

`IdleRedrawTest`, real shell with fake data, simulated 120 Hz vsync, 3 s per screen after 2.5 s to settle.

| Screen | frames/s | Who invalidates |
|--------|----------|-----------------|
| Home, paused, mini-player shown | **0** | - |
| Home, playing | **0** | (mini-player progress: < 1 px per 250 ms tick at this width, see note) |
| Library hub, playing | **0** | - |
| Songs list, playing | **0** | - |
| Search (field focused), playing | 2 | the text cursor blink (platform) |
| Settings, playing / paused | **0** / **0** | - |
| Player, cover, playing | 28.6 | Wavy seek bar 30 fps + backdrop drift 15 fps, both on the shared 30 fps beat; time labels 1 Hz |
| Player, lyrics, playing | 28.8 | as above + the active lyric line's word sweep (same beat) |
| Player, paused | **0** | - |
| Queue sheet over the playing player | 28.5 | the player under the sheet keeps its 30 fps beat |

Static screens let the panel idle; the player draws only its decorative beat (the Settings > Performance presets and
the thermal policy lower it). No runaway loop, no `setFrameRate` override and no infinite animator was found on any
screen, so the D40 drain is not an idle UI loop in these screens. Same numbers before and after SC1 (none of the fixes
touches a loop); the test now pins them (static screens <= 1 fps, player <= 36 fps).

Notes for other owners (not changed here, outside SC1's files):
* **U4 / MiniPlayerView.setProgress**: it compares against the previous *tick* (`position = positionMs` every call),
  not the last *drawn* position, so when 250 ms of a song is under 1 px of the progress line (long songs, narrow
  pills, low density) the line never moves. Compare against the last drawn position instead.
* **Player under the queue sheet**: the wave and the drift keep their 30 fps beat behind the sheet's scrim. Pausing the
  seek wave while a sheet covers it would halve that; it is a design call (the drift stays visible under the dim).
* **C1 / QueueViewModel**: the distinct check compares the whole up-next list on every 4 Hz tick (`UiState.equals`,
  O(queue)); a 10k queue pays that four times a second while the queue is open.

## 5. Device checklist (what remains unverified)

Not verified without a device: real frame times and which phase (input / animation / layout / draw / sync / GPU)
dominates on the S26 Ultra; whether Robolectric's text-layout cost hides a real per-row text cost; the visual of rows
showing the loading tone during a fast fling and fading in when it slows; the RenderThread texture upload of new covers;
GC pauses on ART.

1. **In-app (no cable): Settings > About > Diagnostics > Record performance** (PT1). Record 2 minutes: scroll Home up
   and down, Songs, Albums, Search results, the queue; then share the report. It gives frame-time histograms per screen
   with the layout/measure vs draw vs sync vs GPU split, janky %, GC count / blocking ms and per-thread CPU (the
   `artwork` workers show the cover-render share).
2. `adb shell dumpsys gfxinfo com.mhrsdev.musicplayer reset`, scroll one screen for 20 s, then
   `adb shell dumpsys gfxinfo com.mhrsdev.musicplayer framestats` -> janky frames, 50/90/95/99th percentile; repeat
   per screen.
3. Developer options > **Profile HWUI rendering > On screen as bars** while scrolling: bars over the green 8.3 ms line
   at 120 Hz; the colour of the tall segments names the phase (blue = draw/record, green = measure/layout, red/orange =
   GPU/RenderThread).
4. Developer options > **Debug GPU overdraw** while on Home and Songs: rows should be blue (1x) at most.
5. Perfetto, 20 s of scrolling: `adb shell perfetto -o /data/misc/perfetto-traces/mowj.pftrace -t 20s sched freq
   gfx view am wm dalvik`, open in ui.perfetto.dev: `RV OnBindView`, `RV CreateView`, `RV Prefetch` and `inflate`
   slices on the main thread, `Choreographer#doFrame` length, `DrawFrame` on RenderThread, GC slices, and the `artwork`
   threads during a fling.
6. Debug build: `adb logcat -s MowjJank` (frames over 16.7 ms with the screen and phase split).
