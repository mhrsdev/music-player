# Compatibility: Android 8.0 to 17, and weak phones (C1, owner D32)

Owner's phone: Android 17 / One UI 9. Most users: older Android and weaker phones. The app supports **API 26 (Android 8.0)
to API 37 (Android 17)**, `minSdk 26`, `compileSdk/targetSdk 37`. Every API newer than 26 is behind a runtime check with a
fallback that keeps the app usable and looking right; lint (`NewApi`, `InlinedApi`, aborts the build on errors) plus the
API-matrix tests below guard it.

## What degrades, per API level

| Below API | Feature | What the user gets instead | Where |
|-----------|---------|----------------------------|-------|
| 27 | Dark navigation-bar icons | Light theme gets a dim (40 %) navigation bar behind the white icons, so the bar stays readable | `ui/nav/SystemBars.applyAppearance` |
| 27 | Variable-font axes in `res/font/*.xml` (`fontVariationSettings` is an API 28 attribute) | Text weights fall back to the font's default instance: titles are not bolder than body on Android 8.0 / 8.1 (cosmetic, legible). Not fixed: needs a device to verify | `res/font/mp_sans*.xml`, `ui/theme/TypeRoles` |
| 28 | `Typeface.create(family, weight)`, `getAddress()` of audio devices, signing certificates API | Bold / regular only; the output device key falls back to its name; old signature API for self-update | `TypeRoles`, `ScriptFont`, `OutputDevicesImpl`, `PackageApkInspector` |
| 29 | Thermal status (`ThermalWatch`, `AppContainer.thermalThrottled`) | No adaptive effect throttling and no pause of background waveform decoding when the phone is hot (user's profile stays) | `ui/theme/ThermalWatch` |
| 29 | MediaStore thumbnails (`loadThumbnail`), `IS_PENDING` | Cover comes from the embedded picture / folder art; pending rows are not filtered | `ArtworkLoader.PlatformBackend`, `MediaStoreScanner.projection` |
| 29 | Nav/status bar contrast enforcement off | The system may draw a scrim behind 3-button navigation | `SystemBars.prepare` |
| 30 | `ApplicationExitInfo` (why the app closed), MediaStore generation, genre / bitrate columns, `createDeleteRequest`, `HapticFeedbackConstants.CONFIRM` | Diagnostics says "needs Android 11+"; library check is a full pass at most every 5 min (instead of generation-based 20 s); no genre column; delete asks our own dialog first (API 26-28 also asks the storage permission; API 29 may show a consent screen); a virtual-key tap instead of the confirm haptic | `diag/ExitInfoReader`, `AppContainer`, `MediaStoreScanner`, `TrackActions`, `ui/motion/Haptics` |
| 31 | `RenderEffect` (lyrics blur), Material You wallpaper colours, `BLUETOOTH_CONNECT` (output chip shows the Bluetooth name), immutable `PendingIntent` default, widget corner radius dimens | Lyrics lines fade and scale without blur; the "Wallpaper colours" setting is hidden and the lapis identity stays; the output chip shows a generic Bluetooth label; install-session intent stays mutable-by-default; fixed widget radius | `LyricLineView`, `LyricsEffects.blurSupported`, `ThemeController.isDynamicColorSupported`, `WidgetPalette`, `OutputDevicesImpl`, `SessionApkInstaller`, `res/values-v31` |
| 33 | `POST_NOTIFICATIONS`, `READ_MEDIA_AUDIO`, predictive back (`enableOnBackInvokedCallback`), per-app language (`localeConfig`), `ValueAnimator.getDurationScale` | No notification prompt (granted at install); `READ_EXTERNAL_STORAGE` is requested instead of `READ_MEDIA_AUDIO`; Back uses the legacy dispatcher (same results, no preview animation); the stored language is re-applied on every start; the system animator scale is read from settings once | `MainActivity.askNotificationsOnce`, `ui/nav/AudioPermission`, `MusicPlayerApp.onCreate`, `ui/motion/Motion.durationScale` |
| 35 | Frame-rate category votes | No `View.setRequestedFrameRate`: adaptive-refresh panels may stay at their higher rate while decorative views draw (the shared 30 fps beat still limits the work) | `ui/motion/FrameRateVote` |
| 37 | (nothing is API-37 only) | Android 17 behaviour changes that apply because `targetSdk 37` are handled in code that runs on every level: widget bitmap cap, artwork cache capped at 16 MB / 1/16 heap, foreground-service and audio-focus traces (X1) | `appwidget/*`, `ui/widget/ArtworkLoader.cacheBytes`, `PlaybackService` |

Declared once, honoured where it exists: `foregroundServiceType="mediaPlayback"` (attribute and
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission are 29 / 34 and harmless below), `WRITE_EXTERNAL_STORAGE` only up to API 28,
`READ_EXTERNAL_STORAGE` up to 32. The notification channel is created by Media3 (API 26 is the minimum).

Fixed in C1 (real gaps found by the audit):

* `HapticFeedbackConstants.CONFIRM` (API 30) was used without a check on two Settings rows -> `Haptics.confirm`.
* "Delete from device" on API 26-28 always failed (no `WRITE_EXTERNAL_STORAGE`) -> permission (max API 28) asked at the
  first delete.
* API 26: dark light-theme navigation icons are not available -> dim bar (see table).
* The lyrics blur is also off on low-RAM / small-heap phones, and Settings > Performance disables the switch there.

## Device classes and the first-run Performance preset

`data/settings/DeviceClass` reads four numbers once (`ActivityManager.isLowRamDevice()`, `getMemoryClass()`, total RAM, CPU
core count) plus the API level. On the first start, when **no Performance setting was ever saved** (the older Reduce
motion switch counts as saved), `MusicPlayerApp.onCreate` stores:

| Class | Rule | Preset |
|-------|------|--------|
| very weak | `isLowRamDevice` and heap class <= 96 MB, **or** API 26-27 with <= 3 GB RAM | **Light** |
| low / mid | `isLowRamDevice`, **or** heap class <= 128 MB, **or** <= 4 CPU cores, **or** API <= 28 | **Balanced** |
| everything else | | **Full** (D24: the default stays "no effect cut") |

A saved choice is never overridden; a restored backup that carries Performance keys replaces it. Settings > Performance
shows "Chosen for this phone ..." under the presets until the user changes anything (`SettingsStore.performanceChosenForPhone`,
removed on any `setPerformance`). Rules and numbers: `DeviceClassTest`, `SettingsStoreDeviceDefaultTest`.

Budgets that follow the same class (`DeviceClass.lowEnd` = low-RAM flag or heap class <= 128 MB):

| Budget | Normal | Low-end |
|--------|--------|---------|
| Artwork bitmap cache (`ArtworkLoader.cacheBytes`) | min(16 MB, heap / 16), at least 6 MB | same formula: 8 MB on a 128 MB heap, 6 MB on 96 MB (already scales with the heap class) |
| Simultaneous artwork decodes | 2 (cores - 1, at most 2) | 1 |
| Largest decoded cover | 1024 px bucket | 768 px bucket |
| ExoPlayer target buffer (local files) | 4 MB | 2 MB |
| Notification cover cache | 4 MB | 2 MB |
| Lyrics per-line blur | API 31+ and Performance "Lyrics blur" on | never (switch disabled) |
| Queue items as `MediaItem`s in the player | window of +-200 around the playing one | same |

Other caches (palette 48 entries, colour seeds, waveform envelopes 8, collage blur 4 tiny bitmaps) are a few KB and are not
scaled.

**Queue window (the Android 17 memory-limiter fix).** A saved queue of 10,000 tracks used to become 10,000 `MediaItem`s on
the main thread at every start (~10-20 MB, ~400 ms JVM). `PlaybackStore` now keeps only +-200 items in the player; the rest
stays as `Track`s (views of one immutable list, no copy), joins when playback comes within 50 items of the window edge, when
the queue is edited in a hidden part, or in the background when shuffle / repeat-all are on (they need the whole queue).
The queue screen, `PlaybackState.queue()`, the saved file and the saved index are always the whole queue
(`QueueWindowTest`: 10k restore ~50 ms, next / previous across both edges, edits, shuffle, far `skipTo`, persistence). An
external controller that replaces the queue (Android Auto, Assistant) drops the hidden rest, because it replaced the queue.

## 60 Hz versus 120 Hz

The decorative beat is time based (`FrameDriver.BEAT_NANOS` = 30 fps), not frame-count based. `DecorBeatRefreshRateTest`
drives one second of frames at 60 / 72 / 90 / 120 / 144 Hz: 33 ms loops get 29-31 calls a second at 60, 90, 120 Hz (25 at
72 Hz, 29 at 144 Hz), 66 ms loops 13-16. The user's frame cap (screen / 60 / 30) is applied in the same driver.

## What is tested, and how

| Test | What | Time |
|------|------|------|
| `smoke/ApiMatrixSmokeTest` (`@Config(sdk = {26, 28, 29, 30, 31, 33, 34, 35})`) | Real app (SQLite library, scanner, ExoPlayer) against the X1 hostile MediaStore: launch, Home, Library > Songs scroll, tap-to-play, full player, lyrics view, queue, Back, close, Settings (incl. Performance), Diagnostics, plus a first-run empty library. Fails on any main-thread or worker-thread exception, app error log or crash report | ~2 min |
| `appwidget/WidgetApiMatrixTest` (same SDKs) | Every widget kind x playing / paused / idle x light / dark x 4 sizes builds, inflates, measures | ~30 s |
| `data/settings/DeviceClassTest`, `SettingsStoreDeviceDefaultTest` | Preset rules per class, never override, backup, marker | seconds |
| `ui/motion/DecorBeatRefreshRateTest` | 60-144 Hz beat | seconds |
| `playback/QueueWindowTest` | 10k queue window | seconds |
| `smoke/SmallHeapTest` via `./gradlew :app:testSmallHeap` (256 MB heap; skipped in the normal task) | Home, Library (2,000 rows scrolled and flung 3x), tap-to-play, player, lyrics, queue with 2,000 tracks: no `OutOfMemoryError` on any thread, artwork cache <= its budget (16 MB at 256 MB heap; measured 16.3 / 16.4 MB, heap in use ~164 MB) | ~1 min |

SDKs 27 and 32 are not in the matrix (the code paths equal 26 / 28 and 31 respectively). SDK 36 is left out because
Robolectric 4.16 has no `media_metrics` service there, which kills ExoPlayer's thread (the X1 note); 37 does not exist in
Robolectric yet. The first run downloads the Robolectric `android-all-instrumented` jars for SDK 26-34 (95-165 MB each) into
`~/.m2`; CI needs network once.

## NOT tested on real devices (honest limits)

* No physical phone or emulator was available to any agent. Everything above is JVM + Robolectric: it proves "no exception,
  right branch taken", **not** framework behaviour. Robolectric runs on a modern JDK, so a call to a method missing from an
  old Android's real framework (a `java.*` / `android.*` API added after 26) is found only by lint (`NewApi`; D8 desugaring
  of `List.of`, records etc. is assumed to match lint's model), not by the matrix.
* Timing, jank, thermals, battery and RAM on 2-4 GB phones and 60 Hz panels are unmeasured; the low-end rules are derived
  from the platform's own classes, not from measurements. Thresholds (128 MB heap, 4 cores, 3 GB) are a judgement call.
* OEM skins (One UI 1-9, MIUI, ColorOS...) may kill the service or delay the media notification in ways no test shows.
* Variable-font weights on API 26-27, the dim navigation bar on API 26, the legacy delete permission flow on API 26-28 and
  the haptic fallback are untested visually.
* The queue window was never exercised against a real `MediaController` (Android Auto / Assistant) or a Bluetooth head unit.
  External queue replacement is detected through the session callback only.
* Android 17 specifics (per-app memory limiter, widget bitmap cap, background-audio hardening) are handled by X1 and the
  lower baseline memory here; their effect on the owner's phone is not measured.
