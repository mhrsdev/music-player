# Changelog

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
