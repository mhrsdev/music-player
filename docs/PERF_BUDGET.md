# Performance budget (hard limits for reviewers)

Every change from every agent must keep these. `PerfBudgetTest` enforces what a source scan can; the rest is checked
by the reviewer with the commands in `docs/BATTERY_AUDIT.md`. A deliberate exception needs an entry in the Exceptions
list at the bottom (owner-visible) and an update of the test allow-list in the same commit.

## Process and startup (targets until measured on a device)

| Budget | Limit | How to check |
|--------|-------|--------------|
| Cold start to first frame (mid-range phone) | <= 600 ms, no jank in the first 2 s | `adb shell am start -W -n com.mhrsdev.musicplayer/.MainActivity` (TotalTime), `dumpsys gfxinfo` |
| `Application.onCreate` | only: StrictMode (debug), `AppContainer` construction, one observer registration, locale re-apply below API 33. No DB, MediaStore, file, network or thread work | `PerfBudgetTest.applicationOnCreateStaysCheap`; Perfetto slice `MusicPlayerApp.onCreate` < 30 ms |
| Idle RAM after 1 min in background, library of 10k tracks | PSS <= 60 MB (`adb shell dumpsys meminfo com.mhrsdev.musicplayer`, "TOTAL PSS"); Java heap <= 20 MB | meminfo |
| RAM with Home open | PSS <= 110 MB | meminfo |
| `largeHeap` | never | `PerfBudgetTest` (manifest) |
| Bitmap memory | `ArtworkLoader` LruCache <= min(16 MB, 1/16 of heap); negative cache <= 512 entries; row art decoded RGB_565; everything evicted on `onTrimMemory(UI_HIDDEN)` (`MusicPlayerApp.onTrimMemory`) | code review, meminfo "Graphics" |
| APK | release APK <= 3 MB, no native libraries, only `en`/`fa` resources (`localeFilters`), R8 full mode (AGP 8 default) + resource shrinking | `ls -l app/build/outputs/apk/release/*.apk` (2.77 MB at time of writing) |
| Threads | every executor is a `ThreadPoolExecutor` with `allowCoreThreadTimeOut(true)`; library scan on its own `THREAD_PRIORITY_BACKGROUND` thread | `PerfBudgetTest.threadsAlwaysTimeOut` |

## Per screen / per frame

* R1 No disk, DB, network or `SharedPreferences` first-load on the main thread after startup (StrictMode debug log must stay empty for your screen: `adb logcat -s StrictMode`).
* R2 RecyclerView: `DiffUtil` or targeted notify, stable ids where possible, no `notifyDataSetChanged` on playback ticks; cancel artwork loads on recycle/detach (`ArtworkView` does).
* R3 No allocation in `onDraw`/`dispatchDraw`/`draw` (no `new Paint/Path/RectF/Rect/Matrix/Gradient/Shader`, arrays, strings). Hoist into fields, rebuild shaders in `onSizeChanged`. Enforced by `PerfBudgetTest.noAllocationInDrawPaths`.
* R3b Overdraw (v0.3.1, D29): the GPU has no occlusion culling, so nothing may be drawn under an opaque full-screen
  layer. The window background is dropped after `setContentView` (the opaque root draws it); the player has no root
  background (its backdrop is opaque); while the full player covers the window, the shell under it (screens, scrim,
  dock) is INVISIBLE and the root background is dropped (`ui/nav/PlayerCover`, `PlayerCoverTest`; uncovered before the
  exit fade and while dragging the player down). Soft radial gradients that are transparent beyond their radius are
  drawn as circles, not full-screen rects (`AmbientField`, player glow; `AmbientFieldCoverageTest`: blobs 4.00 -> 2.94
  screens of fill per drift frame).
* R4 The 4 Hz playback state may only touch a view when the value it shows changed (see `PlayerFragment.render`); no `setImageResource`, `setBackgroundTintList` or text set per tick unless changed.
* R4b A TextView updated on a timer has a fixed width (`0dp` + weight or a fixed size), never `wrap_content`: a
  wrap_content TextView requests a layout of the whole screen on every `setText` (player remaining time, 1 Hz). The seek
  bar's 4 Hz position does not invalidate while its 30 fps loop runs (the next beat draws it).
* R5 A screen observes LiveData with the view lifecycle owner; `observeForever` only for process-wide singletons or with removal in the same callback; Handler callbacks removed in `onDestroyView`/`onDetachedFromWindow`; no static `Context/View/Activity/Fragment` fields (`PerfBudgetTest.noContextOrViewHeldInStaticFields`).
* R6 Lists of 10k rows: queries return in < 150 ms on a mid-range phone, use an index (see `QueryPlanTest`, prints every plan), share repeated strings (`TrackQueries.read`).
* R7 A 10k-track queue must not jank: queue save runs off the main thread (`PlaybackStore`), decode is incremental (`ExoPlayerController.readQueue`). Measured on the JVM: encode 16 ms, decode 10 ms, 1.35 MB.

## Background work

* B1 No WorkManager, JobScheduler, AlarmManager, boot/connectivity receivers, extra wake locks, FCM, SyncAdapter (`PerfBudgetTest`). The only receivers are the media button (`MediaButtonRestartReceiver`: starts the service only when a queue is saved; replaces Media3's), the three home-screen widgets (`updatePeriodMillis="0"`, redrawn from `NowPlayingBridge` on playback events) and two non-exported action/install-result receivers. Self-update (REQUEST_INSTALL_PACKAGES) runs only from the Update screen.
* B2 Network only on explicit user-driven need (lyrics view open + setting on). Never while only music plays.
* B3 Timers (`postDelayed`) only in the reviewed list in `PerfBudgetTest` and each gated on playing, observed or visible. `HeadsetControls` posts one 350 ms click-window callback per headset button press (removed when it fires or on release; never repeats, no idle wakeups).
* B4 The foreground service stops 60 s after playback stops (10 min when "resume when a headset connects" is on, so a reconnect finds it; no polling meanwhile; its `AudioDeviceCallback` lives only as long as the service); the player's queue and position are already persisted.
* B5 A feature that keeps a native resource (audio effect, `AudioDeviceCallback`, sensor) releases it when its screen is hidden or playback ends.

## Background CPU / sustained load (PF-B, D29, v0.3.1)

Details and measurements: `docs/PERF_BACKGROUND_v0.3.1.md`. Enforced by `PerfBudgetTest` (source scan) and unit tests.

* BG1 Audio decoding (`WaveformExtractor`) is on demand: the track on screen (only for the 6 seek styles that draw a waveform, `SeekBarStyle.usesWaveform`) or the track queued next. The next-track prefetch additionally needs: the UI visible (an activity is started), a waveform seek style, no battery saver. Nothing is decoded while the phone reports thermal status >= MODERATE (API 29+; cached envelopes are still served). One job at a time on one `THREAD_PRIORITY_BACKGROUND` thread, cancelled when nobody observes it, cached forever (256 bytes per track). `PerfBudgetTest.waveformDecodingIsOnDemandBackgroundPriorityAndGated`, `WaveformRepositoryImplTest`.
* BG2 The library scan reads MediaStore columns only (never opens audio files: no `MediaMetadataRetriever` / `MediaExtractor` / `MediaCodec` in `MediaStoreScanner`), is incremental and committed in chunks of 500, runs on its own background thread, is debounced after MediaStore changes (>= 2 s), throttled on foreground (>= 20 s; >= 5 min below API 30 where there is no MediaStore generation) and never watches MediaStore while the UI is hidden. `PerfBudgetTest.libraryScanIsDebouncedThrottledAndOnItsOwnLowPriorityThread`.
* BG3 Widgets: redrawn on track / play-state events only, coalesced over >= 200 ms, never on position ticks, seeks, buffering or metadata events; the cover of one track is resolved once (play/pause does not probe artwork again). `PerfBudgetTest.widgetsAreRedrawnOnPlaybackStateEventsOnlyAndCoalesced`.
* BG4 The 4 Hz player state (`ExoPlayerController.TICK_MS`) must not do O(queue) work: the queue snapshot is frozen once per queue edit (`PlaybackState.frozen`) and shared by every tick. `PlaybackStateTest`, `PerfBudgetTest.playerStateTickNeverCopiesTheQueue`.
* BG5 No regex compiled per call in non-UI code: `String.matches/replaceAll/replaceFirst` with a regex literal are forbidden (static `Pattern` instead). `PerfBudgetTest.noRegexCompiledPerCall`.
* BG6 Update check at most once a day (retry >= 1 h after a failure), foreground only; LRCLIB only for the track whose lyrics view is open; both cached. `PerfBudgetTest.updateCheckIsAtMostDaily`.
* BG7 A baseline profile (`app/src/main/baseline-prof.txt`, wildcard rules expanded by AGP) compiles the app and Media3 ahead of time; replace by a recorded profile when a device is available. `PerfBudgetTest.aBaselineProfileCompilesTheAppAheadOfTime`.

## Animations

* A0 Every looping animation runs through `ui/motion/PeriodicTick` or a `FrameDriver.Tick` (one Choreographer callback for the app); no private `postInvalidateDelayed` / `postOnAnimationDelayed` / Handler loops (`PerfBudgetTest`).
* A0b (v0.3.1, D29) Decorative loops act only on the driver's **shared 30 fps beat** (`FrameDriver.isBeat`): a
  `PeriodicTick` with a period and the 30 fps seek styles (`FrameSeekView`) never invalidate between beats, so every
  decorative loop on screen lands on the same rendered frame (player: 31 frames/s instead of 76 with three phases,
  `DecorBeatTest`). A new decorative loop must be a `PeriodicTick` with a period, or return `everyFrame() == false`
  and gate itself on `isBeat()`. Only springs, touch and spinners (period 0) may take every display frame.
* A0c While only beat loops are registered the driver sleeps until just before the next beat
  (`postFrameCallbackDelayed`, `FrameDriver.sleepMillis`): 30 main-thread wake-ups a second on a 120 Hz panel, not 120.
  Adding an every-frame tick (a press spring) re-posts at once, so touch latency is unchanged (`DecorBeatTest`).
* A0d A view whose own redraws are decorative votes a low refresh category on API 35+ (`ui/motion/FrameRateVote`:
  player and ambient backdrops, seek bars except while dragged), so LTPO/VRR panels are not held at 120 Hz by them.
* A1 Looping / infinite animations only while the view is attached, visible and `MotionPolicy.decorativeMotionEnabled` (off in battery saver and reduce-motion). Skeleton shimmer <= 15 fps.
* A2 Transitions are one-shot (<= 800 ms); none runs while the app is backgrounded; `animate()` / `ValueAnimator` are cancelled in `onDestroyView`/`onDetachedFromWindow`.
* A3 No live blur: blur once into a tiny bitmap off the main thread (`BackdropBlur`, `CollageBlurView`).
* A3b Lyrics: a line that stays inactive while the active line moves changes only its RenderNode alpha
  (`View.setAlpha`, `hasOverlappingRendering() == false`) and its cached blur effect; it is never invalidated per
  animation frame, so its blurred layer is not re-rendered and re-blurred 55 times per line change (`LyricLineCostTest`).
* A4 Settings > Performance (Q1, D26): an effect asks `ui/theme/MotionPolicy` (`transitionsEnabled`, `bounceEnabled`,
  `backdropMotionEnabled`, `seekMotionEnabled`, `lyricsBlurEnabled`, `lyricsWordsEnabled`), never the settings store; the
  profile (`data/settings/PerformanceProfile`) reaches MotionPolicy through the one app-wide observer in `MusicPlayerApp`
  and the frame-rate cap (screen / 60 / 30) is applied in `FrameDriver` only, so every spring and `PeriodicTick` obeys it.
  Default = Full (D24). JVM measurement (`FrameCapTest`, 1 s at 120 Hz, player playing + lyrics + one spring): tick
  bodies Full 199, Balanced 123, Light 31. One-shot `animate()`/`ValueAnimator` fades are not capped (platform API).

## Baseline Profile recommendation (not done yet: needs a device or emulator)

A Baseline Profile typically cuts cold start 15-30 % for a View-based app and avoids jank on first scroll. Plan:
1. Add module `:baselineprofile` (Groovy build file, Java test; plugin `androidx.baselineprofile`, `androidx.benchmark:benchmark-macro-junit4`).
2. Generate with a `BaselineProfileGenerator` test: cold start, open Library, scroll Home, open the player, seek, queue.
3. Check `app/src/main/baseline-prof.txt` in (about 20-60 KB); AGP bundles it into the release APK; `profileinstaller` (transitive via AndroidX) installs it on first run.
4. Compare `StartupBenchmark` (`CompilationMode.None()` vs `Partial()`).
Not added now because it needs a physical device or emulator that no agent has, and it adds a Gradle module and a
dependency; the supervisor should decide before the first public release.

## Build settings reviewed

* `android:largeHeap="false"` explicit; `android:allowBackup="false"`.
* `extractNativeLibs`: the APK has no `.so` files, so nothing is extracted or stored uncompressed; leave the default.
* R8 full mode is the default since AGP 8.0 (project uses AGP 8.13, no `android.enableR8.fullMode=false`); `minifyEnabled true` + `shrinkResources true` for release (`app/build.gradle`); keep rules are one line (Fragment constructors).
* `androidResources.localeFilters = ['en','fa']` removed ~0.4 MB of library translations (resources.arsc 1.10 MB to 0.67 MB, APK 3.18 MB to 2.77 MB).

## Exceptions

(none)
