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
