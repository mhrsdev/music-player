# Architecture (initial contract — sub-agents may refine, must update this file when they do)

## Stack
- Kotlin, Jetpack Compose, **Material 3 Expressive** (`androidx.compose.material3` latest alpha/stable that has `MaterialExpressiveTheme`, `LoadingIndicator`, wavy `Slider`/`LinearWavyProgressIndicator`, `MaterialShapes` + shape morphing), Navigation Compose, Coil (artwork), Palette/own color extraction for backdrop, Media3 (ExoPlayer + MediaSessionService), Room (library, likes, playlists, history), DataStore (settings), Hilt or Koin (DI — pick one, keep simple).
- minSdk 26, targetSdk/compileSdk latest stable. Single `:app` module to start (package-by-layer inside).

## Product scope
- **Phase 1 (this build):** local-device music library (MediaStore scan, permission flow) + full player experience + synced lyrics (LRC embedded/sidecar file, fallback to **LRCLIB** public API) + likes, playlists, history, queue, sleep timer, search, artists/albums.
- Home screen sections (New releases / Throwbacks / Quick picks) are driven by a `HomeFeedSource` interface; Phase 1 implements it from the local library + listening history (recently added, "from the past", most played). An online catalog provider can be plugged in later through the `MusicSource` interface — **no scraping of unofficial streaming APIs without the owner's explicit decision** (see STATUS.md → Decisions).

## Packages (`com.mhrsdev.musicplayer`)
```
domain/        pure Kotlin models + repository interfaces (Track, Album, Artist, Playlist, LyricLine, QueueState, PlaybackState, HomeSection …)
data/          Room DB, MediaStore scanner, LRCLIB client, repositories implementations, settings
playback/      Media3 MediaSessionService, PlayerController (domain-facing wrapper), queue ops, sleep timer, notification
ui/            ALL Compose UI (owned by the UI agent): theme/, components/, home/, search/, library/, artists/, player/, queue/, settings/, nav/
```
`ui/` depends only on `domain/` interfaces (+ `PlayerController`, `*Repository`). A `fake/` set of in-memory implementations with realistic sample data (Neffex-style tracks, covers via generated gradients) lets UI be built and previewed before data/playback land.

## Contracts the UI relies on (defined in `domain/`)
- `PlayerController`: `state: StateFlow<PlaybackState>` (current track, isPlaying, positionMs, durationMs, shuffle, repeatMode, queue, currentIndex, sleepTimer), commands: play/pause/seek/next/prev/skipTo/toggleShuffle/cycleRepeat/moveQueueItem/removeFromQueue/shuffleQueue()+undo/setSleepTimer.
- `LibraryRepository`: `likedTracks`, `albums`, `artists`, `playlists`, `history`, `recentlySaved`, `search(query, filter)`, `toggleLike`.
- `LyricsRepository`: `lyricsFor(track): Flow<Lyrics?>` (synced lines with timestamps, or plain).
- `HomeFeedSource`: `sections(): Flow<List<HomeSection>>`.

## UI requirements (beat the reference)
See `docs/design-reference/` + `docs/DIRECTIVES.md` table. Must-haves: floating expressive pill nav; hero carousel; wavy seek slider; scalloped morphing play/pause; blurred artwork backdrop with dynamic color from artwork; synced lyrics with smooth auto-scroll, tap-to-seek, active-line emphasis, word fade; queue sheet with drag reorder, swipe-remove, shuffle+Undo snackbar, sleep timer; predictive-back; edge-to-edge; RTL/Persian-ready layout and fonts; accessibility (content descriptions, 48dp targets, TalkBack); tablet/foldable adaptive layouts; haptics; shared-element transition mini-player → full player. Improvements over reference: mini-player, dynamic color toggle, light/dark/AMOLED, equalizer-friendly settings, better empty/loading/error states.
