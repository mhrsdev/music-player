# Diagnostics (X1, owner D30)

The owner's phone (Galaxy S26 Ultra, Android 16 / One UI 8) closes the app "in many places" and we have no device or
logcat. The app now records why it closed, so the next report carries a real stack trace or exit reason.

## What is recorded (app-private `files/crash/`, never uploaded)

| Piece | Where | When / cost |
|---|---|---|
| Crash report | `diag/CrashReporter` (first line of `MusicPlayerApp.onCreate`, chained onto the platform handler) | Only on an uncaught exception: time, app version, device/Android, thread, pid + process uptime, heap/native numbers, full stack trace with `Caused by`, the last 32 breadcrumbs. Ring of 5 (`crash-<time>.txt`) + `last.txt`. Then the previous handler runs, so the system still shows "Mowj closed because this app has a bug". |
| Exit reasons (Android 11+) | `diag/ExitInfoReader` | Once per process start on a low-priority background thread: `ActivityManager.getHistoricalProcessExitReasons` (last 5) -> new entries only (by timestamp), reason (CRASH / CRASH_NATIVE / ANR / LOW_MEMORY / INITIALIZATION_FAILURE / EXCESSIVE_RESOURCE_USAGE / USER_REQUESTED / SIGNALED / ...), status, importance, PSS/RSS, description, and the state summary we attached to that process. ANR traces are kept (8 KB); native-crash tombstones are binary, so only their readable strings (8 KB). Max 10 entries. |
| State summary | `ExitInfoReader.setStateSummary` | On every screen change (fragment resumed): `screen=<Fragment>` attached to the process (one tiny binder call), so a system kill still tells us which screen was showing. |
| Breadcrumbs | `diag/Breadcrumbs` (lock-free ring, 32 entries, memory only) | process start (pid), activity started/stopped/destroyed(finishing), screen shown, user left (Home/Recents), mini-player actions, library scan start/end/failure, artwork / generated-cover errors, trim-memory level. Saved to disk only when memory runs low (`onTrimMemory` RUNNING_LOW/CRITICAL, `onLowMemory`), because a low-memory kill runs no handler. |

| Non-fatal reports | `diag/SafeExecutor` (io, scan, lyrics, waveform, queue-store, update, track-diff, collage pools) | A `RuntimeException` in a background task used to reach the thread's uncaught handler = whole-app crash (playback included). Release builds now write it as a `NON-FATAL` report into the same ring (max 5 per process) and keep running; debug builds still crash so tests stay loud. |
| Playback breadcrumbs | `PlaybackService` | `player play/pause reason N` (Media3 `PLAY_WHEN_READY_CHANGE_REASON_*`: 1 user, 2 audio focus loss, 3 becoming noisy, 4 remote, 5 end of item, 6 suppressed too long), `player error <code>`, `service onTaskRemoved playing=...`, `service onDestroy`. |

"Back at the Home root" is not intercepted (predictive back keeps the system animation): in a log it shows as
`activity stopped MainActivity` without a preceding `user left (home/recents)` and without `activity destroyed
(finishing)` (Android 12+ moves the task back instead of finishing it).

Nothing runs per frame, nothing polls, no network.

## How the owner sends a log

1. If the previous run crashed, froze, or was killed while visible / playing, Home shows a one-time card
   "Mowj closed unexpectedly last time" with **Share report** (the X dismisses it for that incident).
2. Any time: **Settings > About > Diagnostics** shows "Last time the app closed: <reason>" in plain words, the
   first lines of the last crash, **Share diagnostics** (system share sheet, plain text: Telegram, email...) and
   **Clear**. The report holds technical details only (version, device, exit reasons, stack traces, screen names);
   it can be read in the share target before sending.

## Device checklist (when a phone with USB debugging is available)

```
adb logcat -b crash -d                                   # Java + native crashes since boot
adb shell dumpsys activity exit-info com.mhrsdev.musicplayer   # the same exit history the app reads
adb logcat -d -s AndroidRuntime:E DEBUG:F ActivityManager:I lowmemorykiller:I
adb bugreport bugreport.zip                              # everything (tombstones, ANR traces, freecess/thermal)
adb shell dumpsys meminfo com.mhrsdev.musicplayer        # heap / graphics / native while on Home
```
Android 17 per-app memory limiter (the owner's phone): a kill shows as reason `OTHER` with description
`MemoryLimiter:AnonSwap`; the app files it as "The system closed the app for using too much memory". To test it:
```
adb shell am memory-limiter status
adb shell am memory-limiter manual <pid> <limit>      # force a limit on the running app, then use it normally
```
Samsung-specific: a "freecess"/battery-manager kill shows up as `SIGNALED` or `OTHER` with a description; a
low-memory kill as `LOW_MEMORY` with importance 100 (foreground) or 125 (playing in the background).

## Hostile-data smoke test

`app/src/testDebug/.../smoke/SmokeHostileDataTest` boots the REAL app (SQLite library, MediaStore scanner, home
feed, ExoPlayer controller) at a 1440 px-wide xxxhdpi window against `HostileMediaStore` (null / empty / 2,000-char /
Persian+ZWNJ / emoji / zero-width / control-char metadata, duration 0 / -1 / huge, odd years, a duplicate id, files
that do not exist, 2,000 ordinary rows) and visits every screen: Home + every "See all" page, every Library hub one
level deep, Search with odd queries and every filter chip, tap-to-play of the hostile rows, the full player (PlayerCover
drag / snap back / close / Back during the close animation), lyrics, queue, Settings and every sub-screen including
Diagnostics; every list is scrolled end to end and flung, each step measured, laid out and drawn. Plus an empty
library and configuration changes with the player open (rotation, night / day, Persian, font scale 2, display size)
and rapid tab switching / Back. Fails on any main-thread exception, any worker-thread exception (captured by a
default handler), any error log with an app frame, and any crash report written. Runs on SDK 35: Robolectric 4.16's
SDK 36 has no `media_metrics` service, which kills ExoPlayer's playback thread there.

# Performance telemetry (PT1, owner D38 + D40)

The app lags and drains the battery on the owner's Galaxy S26 Ultra and we cannot attach a profiler. **Settings > About >
Diagnostics > Performance** records where the time and the charge go and shares it as one text report. Code:
`diag/PerfProbe` (control + hooks), `PerfStats` (frame histograms), `PerfTimeline` (5 s snapshots), `PerfThreads`
(per-thread CPU), `PerfPower` (battery maths), `PerfFindings` (rules), `PerfReport` (text). A real report built from
synthetic frames: `docs/sample-perf-report.txt`. The card: `docs/screens/pt1-perf-card-{dark,fa-light}.png`.

## How the owner records a session

1. Settings > About > Diagnostics > **Record performance (for bug reports)** ON. Unplug the charger if you can (a charging
   phone cannot show the drain).
2. Use the app for **about 2 minutes**, the way it lags: scroll Home, open Library tabs and Search, open the player, switch
   to lyrics, let a song play with the screen on. For the battery question also leave it playing 1-2 minutes with the screen
   **off** (the recording keeps running while music plays, up to 10 minutes) and 1 minute **untouched on Home**.
3. Back in Diagnostics: the card shows "Recording mm:ss · frames · janky %" and the worst screen. Turn the switch OFF
   (or wait: it stops by itself after 10 minutes, and 1 minute after you leave the app unless music is playing).
4. **Share performance report** and send the text. **Clear recording** forgets it. Nothing is stored on disk and nothing
   is sent anywhere; the toggle is not remembered, so after a restart recording is OFF again.

## What is measured (only while the switch is ON)

| What | How | Where in the report |
|---|---|---|
| Frame time per screen | `Window.addOnFrameMetricsAvailableListener` on the main window, delivered on the `perf-probe` thread; fixed histograms 0-4 / 4-8 / 8-12 / 12-17 / 17-33 / 33-66 / 66-150 / 150+ ms; janky (> one display interval), slow (> 2x), frozen (> 700 ms) counters; p50 / p95 / p99 from the histogram; the worst frame with all its phases | Per screen |
| Screen tag | the fragment on top (`MainActivity` fragment callbacks, the same place as the X1 breadcrumb), `PlayerFragment+lyrics` when lyrics are shown | first column |
| Decorations active per frame | bitmask set by their owners: playing, lyrics, lyrics-blur, backdrop-drift (`PlayerBackdropView`), decor-beat / springs (`FrameDriver`), mini-player | "janky% with/without", worst frame, idle line |
| Idle redraws (D40) | frames with no input handled for 1.5 s, divided by the idle seconds: **fps while nobody touches the app**; counted only while the activity is on screen | `idle` column, "idle:" line |
| Snapshots every 5 s | thermal status (API 29+), battery level / charging / temperature / voltage, current_now and charge counter (BatteryManager, 0 when the maker does not report), Java and native heap, GC runs / blocking GCs / blocking ms (`Debug.getRuntimeStat`), thread count (`/proc/self/status`), process CPU % of one core (`Process.getElapsedCpuTime`), frames/s, display rate, screen on (`PowerManager.isInteractive`), playing, lyrics open, the performance profile in effect (incl. thermal adaptation) | Timeline, Battery, CSV |
| Per-thread CPU (D40) | `/proc/self/task/*/stat` (utime + stime) at the start, every 30 s and at the end; threads grouped by name (`pool-#-thread-#`), top 8 | CPU |

Frames of a window's very first draw (inflation) are skipped and counted separately. If the system could not deliver every
frame report to the listener, `listener-dropped` says so. The frame budget is the display interval; on Android 12+ the
frame timeline `DEADLINE` is used (it follows an LTPO panel's real rate).

### Phases (FrameMetrics) and what a big one means

| Phase | Meaning | Big value usually means |
|---|---|---|
| delay (UNKNOWN_DELAY) | time between the intended vsync and the start of our work | main thread busy elsewhere (GC, binder, a long message) |
| input | touch / key dispatch | slow touch handlers |
| anim | Choreographer animation callbacks (`FrameDriver`, ValueAnimator, springs) | too many animators per frame |
| layout | measure + layout | `requestLayout` churn, `wrap_content` text updated on a timer, deep layouts |
| draw | recording of the display lists (`onDraw`, `draw`) | heavy custom drawing, overdraw, blur setup |
| sync | uploading bitmaps / display lists to the render thread | big bitmaps decoded or uploaded on the frame path |
| issue (COMMAND_ISSUE) | RenderThread issuing GL/Vulkan commands | too much to draw, GPU behind |
| swap | waiting for a buffer / presenting | compositor or GPU backpressure |
| gpu (API 31+) | GPU execution time (overlaps issue / swap, not added to them) | fill rate: blur, big gradients, full-screen layers |

## Findings (the top 3 in the report)

Score-ranked; thresholds in `PerfFindings`. Frame rules need >= 60 frames on a screen.

* Screen > 5 % janky: dominant phase of the slow frames + hint + the decoration that hurts most (`with lyrics-blur on: 41 % vs 3 % off`).
* Idle redraw: a screen drawing >= 20 frames/s untouched (>= 40 on the player, whose decorative beat is 30 by design) with the decorations running in those frames.
* Battery: drain >= 0.8 %/min (typical with music and the screen on is 0.2-0.4 %/min: 1-2 % per 5 minutes), average current >= 1500 mA screen on, > 100 mA screen off (> 300 mA while playing).
* CPU: >= 15 % of a core with the screen on and nothing playing; >= 3 % with the screen off and nothing playing (a leak); >= 12 % screen off while playing.
* Threads: RenderThread >= 10 % of a core, a `pool-` / `Thread-` / `AsyncTask` group >= 10 %, GC and finalizer threads >= 3 %, main >= 40 %.
* GC: blocking GC time >= 200 ms per minute, or >= 60 GCs per minute. Thermal MODERATE or worse reached (with the time). Java heap >= 80 % of its limit. Battery >= 40 C.

The recorder's own thread shows up as `perf-probe` in the thread table (its cost, normally < 0.5 %).

## The same numbers with adb (a phone with USB debugging)

```
# frame stats of the whole app (janky %, percentiles); reset first, run the scenario, read
adb shell dumpsys gfxinfo com.mhrsdev.musicplayer reset
adb shell dumpsys gfxinfo com.mhrsdev.musicplayer framestats      # one row per frame: intended vsync, phases, completion
adb shell dumpsys SurfaceFlinger --latency 'com.mhrsdev.musicplayer/com.mhrsdev.musicplayer.MainActivity#0'  # present times, 3 columns
adb shell dumpsys thermalservice                                  # temperatures + throttling status
adb shell dumpsys batterystats --reset                            # then 15 min of use unplugged:
adb shell dumpsys batterystats com.mhrsdev.musicplayer
adb shell top -H -d 2 -p $(adb shell pidof com.mhrsdev.musicplayer)   # CPU per thread, live
adb shell cat /sys/class/power_supply/battery/current_now         # microamps (sign differs by maker)
```

Perfetto trace (frame timeline + scheduling + GPU + frequency), 20 s while the lag happens:

```
adb shell perfetto -o /data/misc/perfetto-traces/mowj.pftrace -t 20s \
    sched freq idle gfx view input am wm power \
    -a com.mhrsdev.musicplayer
adb pull /data/misc/perfetto-traces/mowj.pftrace      # open in https://ui.perfetto.dev
```
In Perfetto: the app's "Expected / Actual Timeline" tracks show janky frames and why; `Choreographer#doFrame` on the main
thread gives the frame count per second; `DrawFrame` on `RenderThread` the GPU-side cost; the CPU frequency track shows
throttling.

Sampling profiler (which functions burn CPU; debug build or `adb root`):

```
adb shell simpleperf record -p $(adb shell pidof com.mhrsdev.musicplayer) --duration 20 -g -o /data/local/tmp/perf.data
adb pull /data/local/tmp/perf.data
simpleperf report -i perf.data --sort thread,symbol | head -60      # or app_profiler.py from the NDK
```
Debug builds also log every janky frame to `adb logcat -s MowjJank` (`debug/JankLogger`).

## What this cannot tell us

* Real GPU temperature or the SoC's clocks: only the thermal *status* and the battery temperature.
* Whether Samsung's frame-rate policy held the panel at 120 Hz: the display rate in the samples is the mode, the frame
  timeline `DEADLINE` is the better hint; confirm with `dumpsys SurfaceFlinger`.
* Current readings: the sign and unit of `current_now` differ between makers; the report prints the absolute value in
  mA as given, and the charge-counter average (when the phone reports one) is the more trustworthy number.
* Battery level has 1 % steps: a short recording gives a coarse %/min; use the mAh from the charge counter instead.
