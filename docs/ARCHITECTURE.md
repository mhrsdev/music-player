# Architecture (initial contract — sub-agents may refine, must update this file when they do)

## Stack
- Kotlin, Jetpack Compose, **Material 3 Expressive** (`androidx.compose.material3` latest alpha/stable that has `MaterialExpressiveTheme`, `LoadingIndicator`, wavy `Slider`/`LinearWavyProgressIndicator`, `MaterialShapes` + shape morphing), Navigation Compose, Coil (artwork), Palette/own color extraction for backdrop, Media3 (ExoPlayer + MediaSessionService), Room (library, likes, playlists, history), DataStore (settings), **Koin** (DI — chosen in T1: no codegen, no kapt/KSP coupling).
- minSdk 26, compileSdk/targetSdk **37**, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, Gradle 9.8.2. (Latest stable AndroidX — Compose 1.12, core 1.19, navigation 2.10 — requires AGP >= 9.1 and compileSdk 37.) **Material 3 Expressive needs `material3` 1.5.0-beta02** (stable 1.4.0 lacks `MaterialShapes`/wavy indicators; verified in the AAR); it is pinned in `gradle/libs.versions.toml` over the Compose BOM 2026.09.00. The module opts in to `ExperimentalMaterial3ExpressiveApi` globally via compiler arg. Single `:app` module to start (package-by-layer inside).

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

## Contracts the UI relies on (defined in `domain/`) — FINAL signatures (T1)
Models: `domain/model/Models.kt` (package `com.mhrsdev.musicplayer.domain.model`). Interfaces: `domain/repository/Contracts.kt` (package `...domain.repository`). Read those two files; they are the source of truth. Refinements vs the first draft:
- Artwork: models carry `artworkUri: String?` + `placeholderSeed: Int`; when the URI is null the UI draws a deterministic gradient from the seed.
- `PlayerController.play(tracks, startIndex, playingFrom)` starts a queue; `resume()/pause()/togglePlayPause()`; `addToQueue(track, index?)` (also used to undo a removal); `playNext(track)`; `shuffleQueue()` sets `PlaybackState.canUndoShuffle`, `undoShuffleQueue()` restores; sleep timer = `setSleepTimer(minutes)`, `setSleepTimerEndOfTrack()`, `cancelSleepTimer()`; state exposes `SleepTimer` (`Off | Countdown(remainingMs,totalMs) | EndOfTrack`).
- `PlaybackState` also has `playingFrom` (the "Playing from … · N min left" chip; `remainingQueueMs` helper), `isBuffering`, `hasNext/hasPrevious`.
- `LibraryRepository` adds `allTracks`, `album/artist/playlist(id)`, `albumTracks/artistTracks/playlistTracks`, `recordPlay`, `createPlaylist`, `addToPlaylist`; `search` returns `Flow<List<SearchResult>>` (sealed: `OfTrack|OfAlbum|OfArtist|OfPlaylist`). `SearchFilter = ALL, SONGS, ALBUMS, ARTISTS, PLAYLISTS` (no VIDEOS in Phase 1; the reference's Videos chip belongs to a future online `MusicSource`).
- `Lyrics(trackId, lines, isSynced, source)` with `activeLineIndex(positionMs)`; unsynced lines have `timeMs = LyricLine.UNSYNCED`. Sample lyrics: track-3 plain, track-4 none, track-34..37 Persian synced, rest synced English.
- `HomeSection(id,title,subtitle,style: HERO_CAROUSEL|CAROUSEL|LIST, items: List<HomeItem>)`, `HomeItem = OfTrack|OfAlbum|OfPlaylist`.
- `MusicSource(id, displayName, suspend search, suspend resolvePlayableUri)`.
- DI: Koin module `di/AppModule.kt` binds the fakes to the interfaces; UI gets them via `koinInject<T>()` / `koinViewModel()`. T8 swaps bindings.
- Fakes (`fake/`): `SampleData` (37 tracks, 7 artists incl. NEFFEX and a Persian artist, 7 albums, 3 playlists, seeded likes/history), `FakePlayerController` (coroutine-ticker simulation, starts paused mid-track in a 12-item queue), `FakeLibraryRepository`, `FakeLyricsRepository`, `FakeHomeFeedSource`, `FakeMusicSource`.

Original draft contract (kept for reference):
- `PlayerController`: `state: StateFlow<PlaybackState>` (current track, isPlaying, positionMs, durationMs, shuffle, repeatMode, queue, currentIndex, sleepTimer), commands: play/pause/seek/next/prev/skipTo/toggleShuffle/cycleRepeat/moveQueueItem/removeFromQueue/shuffleQueue()+undo/setSleepTimer.
- `LibraryRepository`: `likedTracks`, `albums`, `artists`, `playlists`, `history`, `recentlySaved`, `search(query, filter)`, `toggleLike`.
- `LyricsRepository`: `lyricsFor(track): Flow<Lyrics?>` (synced lines with timestamps, or plain).
- `HomeFeedSource`: `sections(): Flow<List<HomeSection>>`.

## UI requirements (beat the reference)
See `docs/design-reference/` + `docs/DIRECTIVES.md` table. Must-haves: floating expressive pill nav; hero carousel; wavy seek slider; scalloped morphing play/pause; blurred artwork backdrop with dynamic color from artwork; synced lyrics with smooth auto-scroll, tap-to-seek, active-line emphasis, word fade; queue sheet with drag reorder, swipe-remove, shuffle+Undo snackbar, sleep timer; predictive-back; edge-to-edge; RTL/Persian-ready layout and fonts; accessibility (content descriptions, 48dp targets, TalkBack); tablet/foldable adaptive layouts; haptics; shared-element transition mini-player → full player. Improvements over reference: mini-player, dynamic color toggle, light/dark/AMOLED, equalizer-friendly settings, better empty/loading/error states.
