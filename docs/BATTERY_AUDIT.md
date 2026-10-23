# Battery audit

Owner requirement D6/D8: very low battery use. This is a checklist of claims with code evidence (file:line at the time
of writing), how to verify each on a device, the expected result and the open risks. Nobody has run these device
checks yet: the evidence below was established by reading code and by the source-scan test
`app/src/test/java/com/mhrsdev/musicplayer/PerfBudgetTest.java` (fails the build when a rule is broken).

Setup for every device check: install the release build (`./gradlew :app:assembleRelease`), unplug USB (or use
`adb tcpip`), then
```
adb shell dumpsys batterystats --reset
# ... run the scenario ...
adb shell dumpsys batterystats --charged com.mhrsdev.musicplayer > batterystats.txt
adb bugreport bugreport.zip      # open in https://bathist.ef.lc (Battery Historian) for the timeline
```

| # | Claim | Evidence | Verify on device | Expected |
|---|-------|----------|------------------|----------|
| B1 | No WakeLock except ExoPlayer's own | No `newWakeLock`/`PowerManager.WakeLock` in `app/src/main` (PerfBudgetTest.noScheduledBackgroundWork). ExoPlayer is built with `setWakeMode(C.WAKE_MODE_LOCAL)` (`playback/PlayerHolder.java:52`): a partial CPU wake lock held only while it is playing a local file. `WAKE_LOCK` is in the manifest only for that. | Paused for 5 min, screen off: `adb shell dumpsys power \| grep -A3 "Wake Locks"` | No wake lock owned by `com.mhrsdev.musicplayer` while paused; one `ExoPlayer:WakeLockManager` while playing |
| B2 | No AlarmManager / JobScheduler / WorkManager / boot receiver | grep finds none; the manifest has one service; receivers are the media button, three widgets (`updatePeriodMillis=0`, pushed by `playback/NowPlayingBridge` on playback events after a short coalescing delay) and two non-exported ones (widget actions, install result); no `BOOT_COMPLETED`/connectivity actions (PerfBudgetTest.manifestPermissionsAndComponentsAreTheReviewedSet) | `adb shell dumpsys alarm \| grep musicplayer`; `adb shell dumpsys jobscheduler \| grep musicplayer` | no entries |
| B3 | No periodic Handler loop while backgrounded | Every `postDelayed` is in the allow-list of PerfBudgetTest.delayedHandlerCallbacksOnlyInReviewedPlaces. `ExoPlayerController`: position tick `:359` only when `hasActiveObservers() && isPlaying()`, cancelled in `StateData.onInactive` (`:450`); sleep timer and play-count timers are single one-shot callbacks (`:265`, `:341`); `PlaybackService` idle stop `:26/:37/:60`; `PlaybackStore` 1 s debounce `:45`; `SkeletonView` only attached + visible + motion allowed (`ui/widget/SkeletonView.java:111-128`, `onVisibilityAggregated`) | Perfetto (below): main thread of the app process has no recurring slices while the app is in the background and paused | Zero wakeups from the app process when paused in background |
| B4 | Position tick only while playing and observed | `scheduleTick()` `ExoPlayerController.java:357-360`; `onInactive` removes the callback and flushes the position once (`:450-453`) | Playing, screen off, notification only (no activity started): Perfetto `sched` view of the app process | Only ExoPlayer's playback thread runs (decode every ~1-2 s); no main-thread 4 Hz ticks |
| B5 | Service stops about 60 s after pause | `PlaybackService.IDLE_STOP_DELAY_MS = 60_000` (`:26`); armed on pause/stop (`:34-38`) and at creation (`:60`); `stopIfIdle` stops the player (frees codec + AudioTrack) then `stopSelf()` (`:84-88`) | Play, pause, wait 70 s: `adb shell dumpsys activity services com.mhrsdev.musicplayer` | no `PlaybackService` record; notification gone |
| B6 | No network in the background | Only network code is `data/lyrics/UrlConnectionFetcher.java:25`, reached from `LyricsResolver` for the track whose lyrics view is open (see B7). No SyncAdapter/FCM/DownloadManager, no connectivity receiver (`ConnectivityManager` is only read once in `PlayerFragment.isOnline`, `:333`). `INTERNET` is used for LRCLIB only | `adb shell dumpsys netstats detail \| grep -A5 <uid>` (uid from `dumpsys package com.mhrsdev.musicplayer \| grep userId`) before/after 1 h of music with the screen off | 0 bytes in the background while only music plays |
| B7 | Lyrics fetch only on demand | `PlayerViewModel.updateLyricsRequest` asks the repository only while the lyrics view is shown (this audit, `ui/player/PlayerViewModel.java`; test `lyricsAreRequestedOnlyWhileTheLyricsViewIsShown`); results and misses cached (`LyricsResolver`); Settings "online lyrics" off = never call LRCLIB (`di/LyricsModule.java:31-42`) | Play 20 tracks with the cover view (default): no lyrics traffic in `netstats` | Only tracks whose lyrics view was opened produce requests |
| B8 | Update check at most once a day while the app is in the foreground (`AppContainer.onAppForeground` -> `updates().checkIfDue()`), manual otherwise; never in the background; no telemetry | `data/update/UpdateChecker` is reached only from the Update screen (`ui/update/UpdateFragment`) via `AppContainer.updates()`; no analytics dependency (`app/build.gradle`) | `adb shell dumpsys netstats` as B6 | none |
| B9 | Library scan only at first start / when MediaStore changed, on a low-priority thread | `AppContainer.onAppForeground` once per process (`di/AppContainer.java:132-136`), `MediaStoreScanner.isStale` uses MediaStore generation (API 30+) or a 6 h age (`data/scanner/MediaStoreScanner.java`); scan thread has `THREAD_PRIORITY_BACKGROUND` (`di/AppContainer.java`, "scan" executor) and exits after 10 s idle | Cold start twice in a row: second start shows no scan (`adb logcat -s StrictMode`, Perfetto: no `scan` thread activity) | one scan on first start, none afterwards unless music files changed |
| B10 | Idle threads exit | All executors are `ThreadPoolExecutor` + `allowCoreThreadTimeOut(true)` (PerfBudgetTest.threadsAlwaysTimeOut) | `adb shell ps -T -p $(pidof com.mhrsdev.musicplayer)` 30 s after the last interaction | only main, RenderThread, GC/JIT daemons and the ExoPlayer playback thread if the player exists |
| B11 | Audio effects (EQ, bass, virtualizer) cost CPU only while enabled and on screen/playing | `playback/AudioEffectsEngine.refresh()` releases the native effects when not alive / nothing active (`shouldHold`) | Play with effects on vs off, compare `batterystats` CPU | effects off = no effect objects (`dumpsys media.audio_flinger`) |
| B12 | Decorative animations stop in battery saver / reduce motion; Settings > Performance can stop single effects and cap all animation at 60/30 fps | `ui/theme/MotionPolicy` (battery-saver override and `setPerformance` from Settings, `MusicPlayerApp.java`); `SkeletonView.sync`; cap in `ui/motion/FrameDriver.due` (`FrameCapTest`) | Turn on battery saver, open Home while loading: no 15 fps invalidation (`adb shell dumpsys gfxinfo com.mhrsdev.musicplayer` frame count stays flat) | no frames drawn while static |

## Perfetto recipe

```
adb shell perfetto -o /data/misc/perfetto-traces/trace.pftrace -t 60s \
  sched freq idle am wm gfx view binder_driver hal dalvik power
adb pull /data/misc/perfetto-traces/trace.pftrace
```
Open in https://ui.perfetto.dev, filter by process `com.mhrsdev.musicplayer`, check "CPU time per thread" and wakeups
per second in scenario S1 (playing, screen off, 5 min) and S2 (paused, screen off, 5 min). Targets in
`docs/PERF_BUDGET.md`.

## Open risks (not provable without a device)

* ExoPlayer's local playback decodes in short bursts; the real cost depends on codec and device (hardware offload is not
  enabled; `setAudioOffloadPreferences` would let the DSP decode while the CPU sleeps, worth trying for MP3/AAC/FLAC).
* The media notification and `MediaSession` are Media3's; their update frequency (progress ticks for the lock-screen
  seek bar) is Media3's, not ours.
* `MediaStore.getGeneration` is API 30+; on 26-29 the 6 h age rule decides, which can cost one extra scan a day.
* Audio effects (D-sound) are DSP work for as long as they are enabled; the setting is the user's choice.
* `Process priority`: the queue writer thread (`queue-store`) runs at normal priority on purpose (it must finish when the
  UI goes to the background); it works for a few ms per queue edit.
