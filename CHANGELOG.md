# Changelog

Releases are stable unless the version has a `-` suffix (e.g. `v1.0.0-rc.1`). `v0.1.0` and `v0.2.0` were published as pre-releases; `v0.3.0` onward are normal releases.

## v0.3.1

Normal (stable) release. Smoother and more stable than v0.3.0, with a crash log built in. Verified by tests only, not on real devices.

### Fixed - Performance
- The full player no longer redraws everything underneath it.
- Decorative animations now share one 30 fps beat.
- Lyrics redraw only the two lines that change.
- Large areas are no longer painted twice.
- Covers are decoded at display size.
- "No cover" results are remembered across restarts, so songs without a cover are no longer re-probed after every app switch.
- Waveform decoding runs only when it is needed and is skipped when the phone is hot.
- The queue is no longer copied 4 times a second.
- The widget no longer re-probes artwork on play / pause.
- Library rescan is throttled on old phones.
- Added a hand-written baseline profile, for a faster first start after install or update.
- Adaptive thermal behaviour: effects get temporarily lighter when the phone reports it is warm or hot. The state is shown in Settings > Performance.

### Fixed - Stability
- Errors in background tasks (scan, waveform, lyrics, queue saving, update check, artwork) are logged instead of crashing the app (release builds).
- The widget update is guarded against Android 17's bitmap size limit.
- An unplayable queue stops after 10 skips.
- Cover decode errors no longer leave a cover loading forever or crash with out-of-memory.
- Closing the player in the middle of an animation is guarded.
- The update banner now has an R8 keep rule.

### Added
- **Settings > About > Diagnostics**: the last crash with its stack trace, why the system last closed the app (including Android 17's memory limiter), breadcrumbs, and Share / Clear buttons.
- A one-time "closed unexpectedly last time" card on Home.

### Known limitations
- The cause of the crash seen on the maintainer's phone (Android 17 / One UI 9) is not confirmed yet. If Mowj crashes again, share the Diagnostics log.
- Everything above is verified by tests only, not on real devices.
- The v0.3.0 limitations below still apply.

## v0.3.0

Normal (stable) release. Most of it is verified by tests and renders only; see Known limitations.

### Added
- **Nine seek-bar styles** with a live-preview picker in Settings: Wavy, Waveform, Bars plus Ribbon, Pulse dots, LED ladder, Mountain, Thread and beads, Liquid.
- **Word-by-word lyrics** (enhanced LRC, `.elrc`, JSON, TTML, embedded tags) with per-word fill and a soft blur that grows away from the sung line. Lines without word timing get an estimated sweep. Per-track timing offset and text size.
- **Lyrics sources and tools**: sidecar file, then embedded tags, then LRCLIB. Junk tags (only a site name or advert) are ignored. When nothing is found: search manually, add a lyrics file, or retry. The lyrics button is hidden for tracks without lyrics.
- **Living backdrop** that follows the song: colors from the cover drift behind the player and lyrics and tint Home and the album, artist and playlist headers.
- **Generated covers** for songs without artwork (in the app, notification and widgets; Persian-capable).
- **Material You dynamic color** (Android 12+, off by default) and a Material 3 theme for stock components; Google Sans Flex with roundness, spring motion, page transitions, a new pause button shape.
- **Settings > Performance**: presets Full / Balanced / Light, switches for each heavy effect, and an animation frame-rate cap (screen / 60 / 30).
- **Headset and outputs**: map single / double / triple / long press, resume on connect, pause on disconnect, resume after calls, list of output devices with the active one and its volume, a saved sound profile per device, link to the phone's Dual Audio / Audio Sharing settings.
- **Sound**: equalizer, bass boost, virtualizer, loudness, playback speed, skip silence, ReplayGain.
- **Home-screen widgets** (three sizes), **Android Auto** browse tree, **backup / restore** of playlists, likes and history.
- **Auto-update from GitHub Releases** (once a day at most while the app is open, plus a manual button; SHA-256 and signing-certificate check; you confirm the install).
- **Library management**: playlist create / rename / delete / remove track, sort options, multi-select, genres / folders / years, track details, share and delete, rescan button and music-folder picker. Richer Home (mixes, decades, genres, stats).
- **Full-screen layout** (edge-to-edge, cutouts, optional immersive player).
- New name and logo: **Mowj / موج**.

### Changed
- Releases are no longer marked as pre-releases; the release title is "Mowj vX.Y.Z".
- The default seek-bar style stays Wavy; the pause button is a circle with centred bars (play button unchanged).
- Dependency tidy: redundant direct AndroidX declarations dropped in favour of what AppCompat brings; no `final` classes; Java 17.

### Fixed
- Lock-screen and notification-shade media card did not appear (the media session was never attached to the service).
- Headset and earbud buttons did not control playback.
- The dock touched the bottom edge on some phones, and the end of scrolling lists was hidden behind the nav and mini-player.
- Distorted play button shape; clipped long titles in the player; Bluetooth chip now shows the device name.
- Library did not refresh after new music was added; it now rescans when the app comes to the foreground.
- Lyrics view showing only a site watermark as "lyrics".
- Home section titles left in English in the Persian UI; overlapping layout on the empty Liked songs screen.

### Performance
- v0.2.0 felt laggier than v0.1.0 on some phones. Looping animations now share one frame driver, artwork decoding is bucketed, and decorative work stops when not visible. Effects are kept; the cost is cut ([`docs/PERF_REGRESSION_v0.2.0.md`](docs/PERF_REGRESSION_v0.2.0.md)).
- Budgets are enforced by a source-scan test ([`docs/PERF_BUDGET.md`](docs/PERF_BUDGET.md)); audit checklist in [`docs/BATTERY_AUDIT.md`](docs/BATTERY_AUDIT.md).

### Known limitations
- Not verified on real devices: headset buttons, lyrics file picker, Android Auto, widgets on other launchers, themed launcher icon, battery and RAM numbers, blur cost on low-end GPUs.
- Android has no public API to play to two Bluetooth devices at once; Mowj links to the phone's own Dual Audio / Audio Sharing settings where they exist.
- Aggressive battery managers (Xiaomi, Oppo, Realme, Samsung "sleeping apps") can stop headset buttons from waking the app.
- The One UI media card draws its own seek bar and cannot be restyled by apps.
- Persian copy has not been reviewed by a native speaker; LRCLIB availability is outside our control.
- Local files only (no streaming), no tag editor, no crossfade, no dedicated landscape/tablet layouts yet.

## v0.1.0

Pre-release (first installable build). Not tested on a physical device yet - expect rough edges.

### Included
- Local library: MediaStore scan (incremental), stored in framework SQLite; albums, artists, playlists, likes, play history and play counts.
- Playback: Media3 ExoPlayer in a foreground `MediaSessionService` (notification, lock screen / Bluetooth controls, media buttons),
  queue (play next, add, reorder, remove with undo), shuffle with undo, repeat off/all/one, sleep timer (minutes or end of track),
  queue and position restored after restart.
- Add to playlist (pick an existing playlist or create a new one) from the track menu and the player menu.
- Screens: Home, Search, Library (songs / albums / artists / playlists / liked / history), full-screen Player, Queue, Settings.
- Lyrics: embedded tags (ID3 / FLAC / MP4), sidecar `.lrc` / `.txt`, optional online lookup on LRCLIB (toggle in Settings), cached on disk
  including "not found" results; synced lines with tap-to-seek.
- Three seek-bar styles (Wavy, Waveform, Bars); the waveform/bars use real loudness extracted on-device and cached.
- Dark / light / black themes, colors from artwork, English and Persian (RTL) UI with bundled fonts.
- Battery saver mode (no motion, blur or grain) and reduced-motion support.
- Release build with R8 + resource shrinking, signed with the Android **debug** key unless a release keystore is configured.

### Polish
- Player: light and dark, contrast guard on the accent, real output-route chip, mini-player to player cover transition; lyrics view shows an offline state with a Retry chip.
- Library/Home: blurred liked-songs collage, 2x2 library grid, hero carousel, skeleton loading; per-script Persian font.
- Notification permission is requested on the first play; the first library scan runs right after the audio permission is granted.

### License
- MIT (see `LICENSE`). Bundled fonts (Google Sans Flex, Vazirmatn) are SIL OFL 1.1 (`app/src/main/assets/licenses/FONTS-NOTICE.txt`).

### Not included yet
- Equalizer, home-screen widget, Android Auto, Chromecast, crossfade/gapless tuning, tag editor.
- Music-folder picker (the setting shows "All folders"; the scanner supports folder filters but there is no UI yet).
- Playlist rename / delete / remove-track UI and a rescan button (the data layer supports them; no UI yet).
- Online catalog / streaming (the app is local-only by design in this phase).
- No store-grade signing key; no automatic updates (updating over a differently signed APK needs an uninstall first).
- Real-device verification (battery audit, Bluetooth, Android 14+ foreground-service behaviour, MediaCodec waveform extraction).
