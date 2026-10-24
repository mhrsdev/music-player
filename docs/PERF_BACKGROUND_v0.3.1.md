# Background / sustained CPU audit (PF-B, D29, v0.3.1)

Owner report (D29): on a Galaxy S26 Ultra the app lags and the phone got "like a heater". PF-A audits `ui/**`; this
document is the non-UI half (`data/**`, `playback/**`, `appwidget/**`, `di/**`, `domain/**`, `MusicPlayerApp`, service,
receivers). Method: read every non-UI class that can run without a user gesture, list trigger / frequency / cost / does it
stop when idle, fix what is wasteful, pin the invariants with tests. **No device was available**: nothing below is a
measured battery or temperature number. Every "cost" is either counted on the JVM or reasoned from the code and says so.

## Honest summary

* The non-UI background code was already disciplined (no WorkManager/alarms/wake locks, timers gated, one thread per
  job type with timeouts, MediaStore watched only while visible, 4 Hz tick only while playing and observed). I found **no
  single non-UI loop that can by itself make a flagship phone hot**. Sustained heat on an S26 Ultra most likely comes
  from continuous rendering at 120 Hz (PF-A's area) plus the effects below.
* Real, fixable waste existed in five places (table). The largest non-UI item for "laggy right after an update" is the
  missing **baseline profile**: a sideloaded APK has no cloud profile, so after every install/update the whole app runs
  interpreted + JIT-compiled until the nightly dexopt. The owner installs a new build often, so he is always in that state.
* Biggest finding that needs a **UI-side** change (not in my scope): the artwork loader forgets "this cover does not
  exist" every time the app is hidden (see F1 below).

## Findings, ranked by estimated impact on heat / lag

Impact is an estimate (H/M/L), not a measurement.

| # | Finding | Trigger / frequency | Cost | Stops when idle? | Status |
|---|---------|--------------------|------|------------------|--------|
| F1 | **Cover-probe storm after every return to the app** (UI side, `ui/widget/ArtworkLoader.trimAll` -> `forgetMisses`) | `MusicPlayerApp.onTrimMemory(UI_HIDDEN)` clears the "no artwork" memory; the next visit re-runs the whole chain for every visible coverless row/card: `loadThumbnail` (fails), stream open, `MediaMetadataRetriever.setDataSource` + `getEmbeddedPicture`, up to 4 tracks per album, 12 `File.isFile` probes. The memory is a 512-entry `LruCache<String,Boolean>` (a few KB) so clearing it saves nothing | Library of coverless files: dozens of file opens per screen, 2 worker threads, repeated at **every app switch** and on cold start (nothing persists). **H** (CPU + flash I/O, scales with library) | Yes, but it re-runs on each foreground | **Not fixed (ui/**).** Recommendation for PF-A/Supervisor: `trimAll()` must not call `forgetMisses()`; persist misses (SharedPreferences set or a `tracks.artwork_state` column) keyed by uri + `date_modified`; forget them only after a rescan that changed rows |
| F2 | **Baseline profile missing** | every install/update; every cold start until the nightly dexopt | Interpreter + JIT for the whole app; JIT threads compete with the UI thread at start and while scrolling. **H for "lags", M for heat**, unmeasured | n/a | **Fixed**: `app/src/main/baseline-prof.txt` (catch-all wildcard rules for the app and Media3). `:app:assembleRelease` verified: AGP runs `expandReleaseArtProfileWildcards`, `assets/dexopt/baseline.prof` (4.4 KB) is in the APK, 2,029 of the app's methods + 527 classes survive R8. Not verified on a device |
| F3 | **Waveform decode for every track, whatever the seek style** | next-track prefetch fired on every track change while playing, also with the screen off, also for the 3 styles (Wavy default, Ribbon, Beads) that never draw it | One MediaCodec session, 256 windows x (200 ms + 50 ms warm-up) = ~64 s of audio = ~30 % of a 3.5 min song, 256 codec flushes + file seeks, ~0.3-2 s of one core per track (estimate). Library-wide: no, never (single job, cached forever). **M** | It ran at screen off | **Fixed**: prefetch needs UI visible + waveform seek style + no battery saver; **no decode at all** at thermal status >= MODERATE (API 29+, guarded); still on demand, 1 job, `THREAD_PRIORITY_BACKGROUND`, cancellable |
| F4 | **Widget cover re-probed on every widget update** | each play/pause/buffer-state event with a widget placed (coalesced 200 ms) | `WidgetArt.decode` + `ArtworkResolver` chain (new resolver each time, so no memory) + generated cover render (420 px) per update; coverless tracks pay the full probe each time. **M** only if the owner has a widget | Event driven | **Fixed**: the cover of the current track is memoised (`WidgetUpdater.cover`); play/pause toggles cost one `RemoteViews` build |
| F5 | **Queue copied on every 4 Hz state tick** | `PlaybackState` compact constructor did `new ArrayList<>(queue)` for each state: 4x/s while the player or mini-player is visible, plus every player event | A 10k-track queue ("Shuffle all" is a launcher shortcut) = ~40-80 KB garbage x4/s -> steady young-GC wakeups. **M** for big queues, 0 for small | Only while playing + observed | **Fixed**: queue frozen once per queue edit (`PlaybackState.frozen`), shared by all ticks; test pins it |
| F6 | Library scan on every foreground below API 30 | `onAppForeground` every activity start (>= 20 s apart) | Without a MediaStore generation `isStale()` is always true: a full cursor pass over all rows each time (the doc claimed a "6 h age" rule that never existed). S26 (API 36) not affected. **L here, M on old phones** | Only on foreground | **Fixed**: 5 min throttle below API 30 (observer-driven rescans while visible unchanged) |
| F7 | Regex compiled per call in hot loops | `ScannedAudio.cleanGenre` (per MediaStore row, 10k per full scan), `LrcParser.clean` (per lyric line), `TrackRef.norm` (3 per track on restore) | `Pattern.compile` each call; ~10-50 ms per 10k rows, once per scan. **L** | Event driven | **Fixed** (static `Pattern`); `PerfBudgetTest.noRegexCompiledPerCall` guards non-UI code |
| F8 | Cold start restores the saved queue on the main thread | `Application.onCreate` -> `PlayerHolder.get` -> `PlaybackStore.restoreAndAttach`: decode + `toMediaItem` for every saved track | JVM (Robolectric, not a phone): decode 80 ms + `toMediaItem` 328 ms for 10k tracks (the Bundle shadow is slower than the real one; a phone is likely a fraction, but not zero). Every cold start with a big queue. **M for start-up lag** | n/a | **Not fixed** (needs a design: restore a window around the index first and the rest on a worker). Suggested for PF1 |
| F9 | Home feed rebuild | `DbLiveData` reloads the whole feed on any TRACKS/LIKES/PLAYLISTS/HISTORY change while Home is observed: once per counted listen, per like, per playlist edit, once per changed scan | JVM: `HomeFeedBuilder.build` of 10k tracks = ~65 ms warm (+ the 3 queries); a phone maybe 150-300 ms on the io thread, every ~3 min of listening with Home started. **L** | Only while observed | Not changed (bursts collapse into one pending load already) |
| F10 | Animation frame rate follows the display (120 Hz on the S26) | `PerformanceProfile.FULL` = no cap | Every spring/PeriodicTick runs at 120 fps: `FrameCapTest` counts tick bodies per second Full 199 / Balanced 123 / Light 31. **H for heat (PF-A area)** | While animating | **Not changed (policy, ui/theme)**. Suggestions for PF-A: auto-switch to the Balanced profile at thermal status >= MODERATE (`PowerManager.addThermalStatusListener`, API 29+) and cap decorative loops at 60 fps even in Full |

### Checklist answers (trigger / frequency / cost / stops when idle)

1. **Loudness / waveform extraction.** Not run for the library, not on first launch. Triggers: (a) the player screen observes it (6 of 9 seek styles), (b) next-track prefetch while playing (now gated, F3). One job at a time on one `THREAD_PRIORITY_BACKGROUND` thread that exits after 20 s idle; cancelled when its observer leaves (`cancelled` flag polled every codec round); cached forever (SQLite blob, 256 bytes); failures negative-cached. Already a downsampled read (sparse windows, `seekTo` over gaps, warm-up only), never a full decode (a 60 min podcast decodes ~1.4 %; a 3.5 min song ~30 %, see F3). Thermal skip added. Stops when the screen is off (new).
2. **Library scan.** MediaStore columns only; no tag reader, no `MediaMetadataRetriever` (test-pinned). Incremental diff on (modified, size, path); the v4 "re-read once" mark is cleared by the first scan (`modified_ms = -1` rows are rewritten once, `needsRescan` is then false). Chunks of 500 rows per transaction, `THREAD_PRIORITY_BACKGROUND` thread, debounce 2.5 s after a MediaStore change, 20 s foreground throttle, MediaStore watched only while an activity is started, no rescan loop (the generation is stamped from before the scan; a change during the scan causes exactly one more). Genre comes from the MediaStore `GENRE` column (API 30+), not from tags.
3. **Artwork / palette / generated covers.** UI side: in-flight dedupe by key exists, size buckets exist, LruCache <= 16 MB, 2 low-priority workers, palette memoised. Gaps: F1 (misses forgotten on every UI_HIDDEN, nothing persisted), F4 (widget). Notification loader (`playback/ArtworkLoader`): 4 MB cache, 512 px RGB_565, misses remembered per service instance, generated covers cached - fine.
4. **DB.** No query on the main thread (all via the io executor, scan thread, lyrics thread, Auto browse thread). Indexes: album, artist, added, play_stats(last_played), play_events(played_at), playlist order; `QueryPlanTest` prints plans. `play_events` is written once per counted listen. No N+1 beyond the playlist mosaic (one small query per playlist). `needsRescan()` is a 10k-row scan (< 5 ms) on each foreground check. No invalidation storm: topics are per table group and loads collapse (`loadPending`). Library "all tracks" sorts with `COLLATE LOCALIZED` (ICU) on each reload: known R6 cost, only while that list is observed.
5. **Playback service.** Position tick 250 ms only while playing and the state is observed (stops when the activity stops); `onInactive` removes it. MediaSession/notification updates are Media3's, driven by player events, not position. Widgets: event driven (F4). Sleep timer: one one-shot, 250 ms steps only in the last 8 s of the countdown. Wake lock: ExoPlayer's `WAKE_MODE_LOCAL` only while playing. `AudioDeviceCallback`: service lifetime only (headset resume) / observed list. Service stops 60 s after pause (10 min with "resume on connect"). No WorkManager/Alarm/JobScheduler (test). Network: LRCLIB only for the track whose lyrics view is open (cached 30 d / 3 d / 5 min); update check once per 24 h on foreground (retry >= 1 h). ExoPlayer: audio-only renderer, 2-8 s / 4 MB buffers, no offload (see below).
6. **Startup.** `Application.onCreate` builds `AppContainer` (guarded by `applicationOnCreateStaysCheap`): `SettingsStore` (one SharedPreferences read), executors (lazy threads), `ExoPlayer` + saved-queue restore (F8), widget bridge, effects/outputs objects (no native effect until needed). No DB or MediaStore open at startup (`AppContainerTest`). StrictMode + `JankLogger` are debug-only.
7. **Cross-cutting.** Threads (all 1-thread pools that time out): io 30 s, scan 10 s, waveform 20 s, queue-store 10 s, notification artwork 10 s, lyrics 10 s, update 30 s, Auto browse 10 s (service lifetime), UI artwork <= 2 workers 10 s. No busy loops. No logging in hot paths (`Log.w` only on a failed extraction and in the debug jank logger). Regex: F7. LiveData storms: none found.

### Deliberately not done

* **Audio offload.** Would let the DSP decode while the CPU sleeps, but cannot be validated without a device and interacts with the equalizer/bass/virtualizer, speed/pitch, sleep fade and gapless. Experiment recipe in `docs/BATTERY_AUDIT.md` (Open risks).
* **Narrower waveform window** (200 ms -> 100 ms halves the decode): visible effect on bar jitter is unknown without a device; the decode is now rare, so it is not worth the risk.
* **Coalescing the Home feed rebuild** with a timer: adds a timer for a cost that is already collapsed per burst.

## Changes (branch `task/pfb-background-cpu`)

| File | Change |
|------|--------|
| `data/waveform/WaveformRepositoryImpl.java`, `di/WaveformModule.java` | second gate `throttled` (no decode at all, cached data still served); `skipPrefetch` semantics documented |
| `di/AppContainer.java` | `skipWaveformPrefetch` (UI visible, waveform style, battery saver), `thermalThrottled` (API 29+ guard), `uiVisible`, 5 min legacy foreground-scan throttle |
| `data/settings/SeekBarStyle.java` | `usesWaveform()` (data-layer rule; `SeekBarStyleTest` keeps it equal to `ui.widget.SeekStyles.usesWaveform`) |
| `domain/model/PlaybackState.java`, `playback/ExoPlayerController.java` | `PlaybackState.frozen`: the queue snapshot is built once per queue edit and shared by every tick |
| `appwidget/WidgetUpdater.java` | cover memo for the current track |
| `data/scanner/ScannedAudio.java`, `data/lyrics/LrcParser.java`, `data/backup/TrackRef.java` | static `Pattern`s |
| `app/src/main/baseline-prof.txt` | catch-all baseline profile (app + Media3) |
| tests | `WaveformRepositoryImplTest` (+3), `PlaybackStateTest`, `SeekBarStyleTest`, `PerfBudgetTest` (+7: scan debounce/throttle/priority/no file opening, waveform gating, widget cadence, tick without queue copy, no per-call regex, daily update check, baseline profile) |
| docs | `PERF_BUDGET.md` (BG1-BG7), `BATTERY_AUDIT.md` (B9 corrected, B13-B16, offload note), this file |

## Verified here vs not

Verified: `compileDebugJavaWithJavac`, `:app:lintDebug` (0 errors), `:app:assembleRelease` (R8 + profile expansion), the unit
tests listed above, the full `:app:testDebugUnitTest` (773 tests, 0 failures), the 10k-track Home feed
timing and the queue-store timings on the JVM.

**Not verified (no device): any battery, temperature, frame or start-up number.** Thermal gating, the baseline profile's
effect and the widget memo have only been exercised by unit tests / build output.

## Device checklist for the owner (about 30 minutes)

Install the release APK, unplug, then for each scenario reset first: `adb shell dumpsys batterystats --reset`.

1. **Idle after pause / screen off (5 min each), then dump:** `adb shell dumpsys batterystats --charged com.mhrsdev.musicplayer > bs.txt` (look at CPU time, wakeups, "Wake lock"); `adb shell dumpsys cpuinfo | grep musicplayer` (should read ~0 % in the background); `adb shell dumpsys power | grep -A3 "Wake Locks"`; `adb shell ps -T -p $(adb shell pidof com.mhrsdev.musicplayer)` (threads that should have exited: scan, waveform-extractor, queue-store, artwork).
2. **Playing with the screen off (10 min), Seek style Wavy:** `dumpsys cpuinfo` stays low and there is no `waveform-extractor` thread (F3). Then switch to Waveform style, play 3 tracks with the player screen open: one decode per track, thread gone 20 s later.
3. **Thermal:** `adb shell dumpsys thermalservice` (current status, headroom); while hot (`adb shell cmd thermalservice override-status 2` simulates MODERATE): Waveform style should decode nothing new; `override-status 0` resets.
4. **Foreground heat (the likely culprit):** open Home and the player for 5 min; `adb shell dumpsys gfxinfo com.mhrsdev.musicplayer framestats` (janky frames %), `adb shell dumpsys SurfaceFlinger --latency` or Perfetto's frame timeline; compare Settings > Performance Full / Balanced / Light at the same screen. If Light is cool, the cost is animation/GPU (PF-A), not background work.
5. **Cover storm (F1):** library with coverless tracks: scroll Library, press Home, come back, scroll again; Perfetto `sched` for the `artwork` threads or `adb shell dumpsys cpuinfo` right after returning. Expect a burst at every return until F1 is fixed in `ui/`.
6. **Widget (F4):** place the wide widget, play/pause 10 times: Perfetto should show one short render per toggle, no artwork probing.
7. **Start-up and baseline profile (F2, F8):** `adb shell am force-stop com.mhrsdev.musicplayer; adb shell am start -W -n com.mhrsdev.musicplayer/.MainActivity` (TotalTime), once right after install and once after `adb shell cmd package compile -m speed-profile -f com.mhrsdev.musicplayer`; repeat with a 10k-track queue saved (Shuffle all, force-stop) to see F8.
8. **Perfetto (60 s, playing, screen on):** recipe in `docs/BATTERY_AUDIT.md`; open https://ui.perfetto.dev, filter by process, look at CPU time per thread, `RenderThread`, `waveform-extractor`, `artwork`, `scan`, `DefaultDispatch`-style JIT threads (`Jit thread pool` high at the start = F2).
