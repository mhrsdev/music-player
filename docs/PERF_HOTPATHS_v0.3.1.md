# UI hot paths v0.3.1 (D29: lag + "phone like a heater" on a Galaxy S26 Ultra)

Owner report: on the S26 Ultra (120 Hz LTPO, QHD+) the app lags and the phone got very hot. That is sustained CPU/GPU
load, not a one-off spike. No device or emulator is available to the agents, so this is a code audit of everything
under `ui/**` and the layouts, with the counted work measured on the JVM (Robolectric) and the GPU fill computed.
D24 applies: **no effect was removed or simplified**; only work that nobody can see was cut.

## 1. Everything that keeps drawing (audit table)

"Rate" = rendered frames per second it causes on a 120 Hz panel. "Stops" columns: screen off / app in background
(window invisible), view not visible, paused, reduce motion / battery saver / Performance switch.

| # | What animates | Screen | Runs while | Rate before -> after | Per-frame cost | Screen off / bg | Invisible | Paused | Reduce motion |
|---|---------------|--------|-----------|----------------------|----------------|-----------------|-----------|--------|---------------|
| 1 | Backdrop colour-field drift (`PlayerBackdropView`) | Player | player shown + playing | 15 fps on its own phase -> 15 fps on the shared beat | **full screen**: base gradient, 20 px art upscaled, 4 radial blobs, glow, scrim = 8 full-screen fills, plus the player root colour, root + window background and the whole shell (Home covers, dock, scrim) still drawn underneath (~12-13 fills). After: ~6.8 fills (blobs/glow as circles, nothing underneath). No allocation | yes (`onStop`, window visibility) | yes | yes | yes |
| 2 | Wavy / Waveform-fallback seek wave (`BaseSeekView` tick 33 ms) | Player | playing + visible + seek motion | 30 fps own phase -> 30 fps beat | path of ~150 points, 1 stroke | yes | yes | settles in 300 ms | yes |
| 3 | Pulse dots / Liquid (`FrameSeekView`) | Player | playing + visible | 30 fps own phase -> beat | ~200 circles / 12-point edge path | yes | yes | yes | yes |
| 4 | Beads release wobble | Player | ~0.5 s after a drag | display rate (touch) | small | n/a | yes | n/a | snaps |
| 5 | Seek position (4 Hz state) | Player | playing | 4 extra off-beat frames/s -> 0 (drawn by the next beat while the loop runs) | seek bar | yes | yes | n/a | n/a |
| 6 | Lyrics word sweep (`LyricsTicker` 33 ms) | Player, lyrics | synced, playing, sweep on | 30 fps own phase -> beat | active line only (3 text draws, clip) | yes | yes | yes | (sweep is information, keeps running) |
| 7 | Lyrics line change: emphasis / alpha / blur (`LyricLineView` 460 ms animator) | Player, lyrics | every line change (~every 3-5 s) | display rate, **every visible line (~12) re-recorded each frame; with blur (API 31+) every line's layer re-rendered and re-blurred ~55 times per change** -> only the 2 lines whose emphasis changes redraw; the others change RenderNode alpha and swap a cached blur only when its bucket changes | blur passes | yes | yes | n/a | blur off |
| 8 | Lyrics auto-scroll (`smoothScrollBy` 620 ms) | Player, lyrics | every line change | display rate (kept: text motion needs it) | composites cached line layers | yes | yes | n/a | jump instead |
| 9 | Title marquee (TextView, `marqueeRepeatLimit=1`) | Player | one pass after play / track change, long titles | display rate for one pass (kept, platform) | one TextView | yes | yes | stops | off |
| 10 | Remaining-time label | Player | playing | 1 **layout pass of the whole player** per second (`wrap_content` TextView) -> 0 (fixed width, redraw only) | layout | yes | yes | n/a | n/a |
| 11 | Play/pause morph + jiggle springs (`MorphPlayPauseView`) | Player, mini, hero cards | ~0.3-0.5 s per state change / press | display rate (touch, kept); motion-policy read moved out of `onDraw` | 1 path | n/a | snaps on detach | n/a | snaps |
| 12 | Buffering arc (`MorphPlayPauseView`, period 0) | Player, mini | while buffering | display rate (kept: a spinner); the mini-player's one now stops under the player (hidden) | 1 arc | yes | yes | n/a | still arc |
| 13 | Mini-player progress line | everywhere | playing | <= 4 Hz; **kept redrawing under the opaque player** -> not drawn there (INVISIBLE) | pill redraw (on hardware rendering `invalidate(rect)` is a whole-view redraw) | yes | yes | yes | n/a |
| 14 | Home under the player | Player open | always | every player frame re-rendered Home's hero covers, cards, glow, scrim, dock pills (no occlusion culling in the GPU) -> nothing | ~2-4 full-screen layers | | | | |
| 15 | Window background under the opaque root | every screen | every frame | 1 extra full-screen fill per frame (e.g. 120/s while scrolling Home) -> 0 | 1 fill | | | | |
| 16 | `FrameDriver` Choreographer callback | app | any tick registered | every vsync (120 wake-ups/s) -> 30/s while only decorative loops run | main-thread wake-up | n/a | n/a | n/a | n/a |
| 17 | Skeleton shimmer (`SkeletonView` 66 ms) | lists while loading | skeleton shown | 15 fps own phase -> beat | rounded rects | yes | yes | n/a | static |
| 18 | `AmbientBackdropView` FULL drift (66 ms) | (only SOFT is used today: library header, static) | | beat; blobs as circles | | yes | yes | yes | yes |
| 19 | Accent cross-fade (`ThemeController`, 600 ms per track change) | shell | per track change | display rate, one-shot (kept; mini-player re-tints per frame) | small allocations per frame | | | | 150 ms |
| 20 | Hero carousel / Home lists | Home | only while the finger scrolls | display rate (touch); `apply()` setters only on change (v0.2.1) | binds + artwork decode on worker | | | | |
| 21 | Artwork fade-in (`ArtworkView`) | lists | 150 ms per newly loaded cover | display rate, ends by itself | 1 rounded bitmap | | | | |

Home at rest has **no** loop (the glow is static, the Continue card moves in 2 % steps). The sustained load was the
**player** (cover or lyrics) left open while music plays: rows 1, 2/3, 5, 6, 7, 8, 10, 13, 14, 15, 16 together.

## 2. What changed (ranked by estimated impact on that sustained load)

| Rank | Change | Files | Evidence / measurement |
|------|--------|-------|------------------------|
| 1 | **The shell is not drawn under the full player.** Once the player's enter fade ends, the screen container, scrim and dock become INVISIBLE (same size: no relayout; their loops stop through `onVisibilityAggregated`) and the root background is dropped; everything is restored before the exit fade starts and while the player is dragged down | `ui/nav/PlayerCover` (new), `MainActivity.setPlayerCovering`, `PlayerNavigator`, `PlayerFragment.onCreateAnimator` / swipe / `onDestroyView` | `PlayerCoverTest`. Removes ~2-4 full-screen layers (Home covers, glow, scrim, elevated mini-player + nav with shadows, root fill) from **every** player frame, plus the mini-player's 4 Hz redraws |
| 2 | **One shared decorative beat.** Every fixed-rate loop (`PeriodicTick` with a period, the 30 fps seek styles) acts only on the driver's 30 fps beat, so backdrop, seek and lyrics invalidate on the same display frame | `FrameDriver`, `PeriodicTick`, `FrameSeekView` | `DecorBeatTest`: three loops started out of phase, 1 s at 120 Hz: **76 rendered frames (v0.3.0 scheme) -> 31**; each loop keeps its rate (31/30/15 body calls). Fewer, regular frames also let an LTPO panel drop its refresh rate |
| 3 | **No overdraw under opaque layers.** Window background dropped after `setContentView` (the opaque root draws it); player root colour removed (backdrop is opaque); backdrop blobs and glow drawn as circles (identical pixels: their gradients are transparent beyond the radius) | `MainActivity`, `player_fragment.xml`, `PlayerFragment`, `AmbientField`, `PlayerBackdropView` | `AmbientFieldCoverageTest` (1440x3120): blob fill per drift frame 4.00 -> avg 2.94 (worst 3.27) screens; glow 1.00 -> 0.86. Player backdrop frame: ~12-13 -> ~6.8 full-screen fills. App-wide: 1 fill less on every frame |
| 4 | **Lyrics: inactive lines are not redrawn on line changes.** Their alpha moves on the RenderNode (`setAlpha`, `hasOverlappingRendering() == false` so no offscreen layer) and the blur effect is swapped only when its half-pixel bucket changes | `LyricLineView` | `LyricLineCostTest`: a 2 -> 3 distance transition re-records **0** display lists (v0.3.0: one per frame, ~55 at 120 Hz, each re-rendering + re-blurring the line's layer on API 31+); a line becoming active still animates its scale |
| 5 | **Driver sleeps between beats.** With only decorative loops registered, `postFrameCallbackDelayed` until ~6 ms before the next beat; a spring added meanwhile re-posts at once | `FrameDriver.sleepMillis`, `add` | `DecorBeatTest`: sleeps 26 ms after a beat; 0 with a spring. 120 -> 30 main-thread wake-ups/s on a 120 Hz panel |
| 6 | **No 1 Hz layout pass in the player.** Remaining-time label `wrap_content` -> `0dp` + weight, right-aligned (same look) | `player_fragment.xml` | TextView `checkForRelayout` only invalidates when the width is fixed and the height unchanged |
| 7 | **Seek position folded into the beat.** `setPosition` does not invalidate while the bar's frame loop runs | `BaseSeekView`, `FrameSeekView` | 4 off-beat frames/s -> 0 while playing |
| 8 | **Low refresh vote for decorative views** (API 35+): player / ambient backdrops and seek bars (not while dragged) ask for the low frame-rate category | `ui/motion/FrameRateVote` (new) | not measurable here; on Android 15+ VRR/LTPO panels it lets the 30 fps decorative frames run the panel below 120 Hz |
| 9 | Play/pause `onDraw` read the motion policy (settings + power state) on every morph frame; now only while the buffering arc shows | `MorphPlayPauseView` | code |

| 10 | **Artwork memory.** A decode whose shorter side is over 1.25 x its bucket is scaled to the bucket once on the worker (power-of-two sampling left up to ~2x per side) | `ArtworkLoader.fit` | `ArtworkMemoryTest`: a 3000 px picture for a 192 px row 281 KB -> 74 KB in the cache, so ~4x more row covers fit and scrolling re-decodes less (CPU, GC, OOM risk) |
| 11 | **"No artwork" is remembered** across app switches (it was wiped on every `UI_HIDDEN`, so each return re-probed every cover-less row: thumbnail, MediaMetadataRetriever, up to 4 tracks x 12 file probes) and across restarts (`cacheDir/artwork_misses.txt`, read on the worker, 3-day TTL so new folder art is found) | `ArtworkLoader` | `ArtworkMemoryTest`: after trim + restart the cover is answered without a probe. PF-B finding |
| 12 | **Generated covers on the main thread no longer wait for the worker.** `GeneratedCover.draw` is synchronized; the <= 96 px synchronous renders shared the worker's instance and could block a scroll frame behind a 1024 px render | `ArtworkLoader.syncGenerator`, `GeneratedCover.copy` | code |
| 13 | **Adaptive thermal** (always on, D29/PF-B): MODERATE = at most Balanced (60 fps cap, no drift / lyrics blur), SEVERE+ = at most Light, user's profile back when cool; note in Settings > Performance | `ui/theme/ThermalWatch`, `MotionPolicy.setThermalLevel` / `effective` | `ThermalPolicyTest`. Decorative loops are already 30 / 15 fps on the beat; the "199 tick bodies" of Full in `FrameCapTest` are a touch spring at 120 Hz plus those loops, so no default cap was added for touch |

Crash hazards checked (owner: crashes while scrolling / tapping a song; X1 owns the hunt): RecyclerView click
handlers all guard `NO_POSITION`; `TrackAdapter.moveLocal` invalidates in-flight diffs; artwork callbacks of recycled
rows are cancelled / ignored by key; `FrameDriver` tolerates ticks added / removed during a frame; no cached bitmap is
ever recycled. Fixed defensively: every pixel read of a cover (`AmbientPalette`, `ThemeController.seedOf`,
`PlayerBackdropView.prepare`) copies a HARDWARE bitmap to software first (getPixels on a HARDWARE bitmap throws, and
createScaledBitmap keeps HARDWARE); `ArtworkLoader.fit` returns the original on OOM; `QueueFragment.removeWithUndo` ignores a negative position (a swipe
on a holder that was already unbound passed `toUpNext(NO_POSITION)` = -2 on to the view model).

Expected combined effect, player open and playing with lyrics on a 120 Hz QHD+ panel (estimates from the counts
above, not device measurements): rendered frames from loops ~80/s -> ~31/s; GPU fill per backdrop frame about halved;
blur passes per line change cut by roughly 80 %; main-thread wake-ups 120/s -> 30/s; no layout passes while playing.
Home and lists gain one full-screen fill less per frame.

## 3. Looked at and deliberately not changed

* **Backdrop drift at 15 fps.** Worst-case colour change per drift frame is below one 8-bit level (blob gradient
  slope <= 0.11 level/px, drift <= ~130 px/s at QHD+), so frames are not redundant enough to drop without risking
  visible stepping; kept at 15 fps, now aligned and cheaper.
* **Rendering the backdrop at low resolution and upscaling.** Would cut its fill ~5x, but dark gradients upscaled
  from 8-bit can band visibly and that cannot be checked without a device. Candidate for v0.4.0 with a device.
* **Lyrics auto-scroll and the title marquee** run at the display rate: text in motion needs it; both are one-shots.
* **Accent cross-fade** (600 ms per track) re-tints the mini-player every frame with small allocations: one-shot.
* **Artwork / generated-cover pipeline**: decode and generation already run on the worker (generated covers only
  synchronously up to 96 px); cache budget is an owner-visible limit (PERF_BUDGET) - not touched. If Home still lags
  while scrolling on the device, the next suspect is cache thrash of 1024 px hero covers (4 MB each) on QHD+.
* `invalidate(Rect)` in `MiniPlayerView` is a whole-view redraw under hardware rendering (API 21+); at <= 4 Hz and now
  hidden under the player, not worth a change.
* `MotionPolicy.decorativeMotionEnabled` reads `Settings.Global` and `PowerManager.isPowerSaveMode()` (both cached by
  the platform on current Android) a few times per 4 Hz tick: negligible.

## 4. Verified vs not

Verified here: compile (debug), `lintDebug` 0 errors, the new JVM tests (`DecorBeatTest`, `PlayerCoverTest`,
`LyricLineCostTest`, `AmbientFieldCoverageTest`, `ArtworkMemoryTest`, `ThermalPolicyTest`) plus `PeriodicTickTest`, `FrameCapTest`, `PerfBudgetTest`,
`MiniPlayerInvalidationTest`, and the full `:app:testDebugUnitTest`.

Not measurable without a device: real GPU time and temperature, the panel's actual refresh rate (LTPO / Samsung
frame-rate policy), whether HWUI caches a blurred line layer exactly as assumed, the visual of the player enter / exit
with the shell hidden (logic is unit-tested; the fade itself is the same animator), and whether the remaining heat
comes from elsewhere (e.g. playback / background CPU: PF-B's scope).

## 5. Checklist for the owner / friend (USB debugging on, debug or release build)

Run each scenario for 2 minutes, before (v0.3.0) and after (v0.3.1), same song, same brightness, charger unplugged.
Scenarios: A) player with cover, playing; B) player with synced lyrics, playing; C) Home, scroll up and down;
D) screen off, music playing.

1. Frames: `adb shell dumpsys gfxinfo com.mhrsdev.musicplayer reset`, run the scenario, then
   `adb shell dumpsys gfxinfo com.mhrsdev.musicplayer` -> "Total frames rendered" / 120 s (A/B expected ~31/s after,
   was ~75-80), "Janky frames", 90th / 99th percentile.
2. Refresh rate: Developer options -> "Show refresh rate" (or `adb shell dumpsys SurfaceFlinger | grep -i "fps\|refresh"`);
   in A/B the panel should no longer sit at 120 Hz.
3. Overdraw: Developer options -> "Debug GPU overdraw" -> "Show overdraw areas": the player should be mostly
   uncoloured / blue, not green / red.
4. CPU per thread: `adb shell top -H -d 2 -p $(adb shell pidof com.mhrsdev.musicplayer)` -> main thread and
   `RenderThread` % in A/B.
5. Heat: `adb shell dumpsys thermalservice` (temperatures + throttling status) at the start and after 10 minutes of B.
6. Battery: `adb shell dumpsys batterystats --reset`, 15 minutes of B unplugged, then
   `adb shell dumpsys batterystats com.mhrsdev.musicplayer > bs.txt` (or `adb bugreport bug.zip` for Battery Historian).
7. Trace (best evidence, 20 s of B):
   `adb shell perfetto -o /data/misc/perfetto-traces/mowj.pftrace -t 20s sched freq gfx view am wm power`,
   `adb pull /data/misc/perfetto-traces/mowj.pftrace`, open in ui.perfetto.dev: count `Choreographer#doFrame` per
   second on the main thread, `DrawFrame` length on RenderThread, GPU completion, CPU frequency.
8. Debug build only: `adb logcat -s MowjJank` while doing A-C (every frame over 16.7 ms, with screen and phase split).
