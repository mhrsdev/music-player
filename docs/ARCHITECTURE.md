# Architecture (Java / Views / MVVM / SQLite)

Single `:app` module, package-by-layer with feature sub-packages. **Java only** (no `.kt`/`.kts`), Groovy `.gradle`.
Sub-agents may refine this file but must keep it current.

## Stack
- Java 17 source level, XML layouts + **custom Views**, ViewBinding, AndroidX (appcompat, core, activity, fragment,
  recyclerview, constraintlayout), Material Components (View-based; only an invisible foundation: DayNight + widget
  defaults, the visual identity is our own), **MVVM** with `ViewModel` + `LiveData`.
- Media3 (`exoplayer`, `session`), framework **SQLite** (`SQLiteOpenHelper`, no Room), `SharedPreferences` for settings.
- Plain-Java DI: `di/AppContainer`, created in `MusicPlayerApp`. No Koin/Hilt/Dagger. No RxJava/coroutines: async work
  runs on `AppContainer.ioExecutor` (one background thread that exits after 30 s idle) and results arrive as
  `LiveData` (`postValue`) or `Callback<T>` on the main thread.
- Artwork loading is a small own bitmap loader with an `LruCache` (UI/data task); no image library.
- Gradle wrapper **9.0.0**, AGP **8.13.2** (newest AGP that runs on Gradle 9.0.0), minSdk 26, compile/target SDK 37.
  Release builds: R8 + resource shrinking.

## Product scope
- **Phase 1:** local-device music library (MediaStore scan, permission flow) + full player experience + synced lyrics
  (LRC embedded/sidecar file, fallback to **LRCLIB** public API) + likes, playlists, history, queue, sleep timer,
  search, artists/albums, waveform seek bar.
- Home sections (New releases / Throwbacks / Quick picks) come from `HomeFeedSource`; Phase 1 builds them from the
  local library + listening history. An online catalog plugs in later through `MusicSource` -- **no scraping of
  unofficial streaming APIs without the owner's explicit decision** (see STATUS.md).

## Packages (`com.mhrsdev.musicplayer`)
```
MusicPlayerApp, MainActivity        application (creates AppContainer) and the single-activity shell
domain/model        immutable records: Track, Album, Artist, Playlist, LyricLine, Lyrics, PlaybackState, SleepTimer,
                    RepeatMode, HomeSection, HomeItem, HomeSectionStyle, SearchFilter, SearchResult
domain/repository   interfaces the UI codes against (all results as LiveData): PlayerController, LibraryRepository,
                    LyricsRepository, WaveformRepository, HomeFeedSource, MusicSource, Callback<T>
data/db             DbHelper (SQLiteOpenHelper)
data/db/contract    one final class per table: table + column constants + CREATE statement (LikesContract = example)
data/db/query       DAO-style query classes, the ONLY place with SQL (LikeQueries = example)
data/scanner        MediaStore scanner
data/lyrics         LRC parser, LRCLIB client, cache
data/waveform       waveform extractor + cache
data/repository     implementations of domain.repository on top of data.*
data/settings       SharedPreferences-backed settings
playback            Media3 MediaSessionService, real PlayerController, sleep timer, notification (PlaybackService = stub)
di                  AppContainer (hand-written DI, today bound to fakes)
fake                in-memory implementations + SampleData (UI development and tests)
ui/theme  ui/widget  ui/nav  ui/home  ui/search  ui/library  ui/player  ui/queue  ui/settings
                    (owned by the UI agent; ui/home/HomeViewModel is the ViewModel example)
```
Dependency rule: `ui` -> `domain` (+ `di` to obtain instances); `data`, `playback`, `fake` -> `domain`; `domain` depends on
nothing but `androidx.lifecycle.LiveData` and `androidx.annotation`. `ui` never imports `data.*` or `playback.*`.

## MVVM
`View` (Activity/Fragment/custom View, ViewBinding) observes a `ViewModel` (exposes `LiveData` only, takes user intents
as method calls, holds no `Context`/View references) which talks to the domain interfaces. One ViewModel per screen;
`PlayerController.getState()` is shared app-wide. ViewModels with constructor arguments use a static `factory(...)`
(see `HomeViewModel`). Unit-test ViewModels on the JVM against `fake/` with `InstantTaskExecutorRule`.

## SQLite rules
All SQL lives in `data/db/query/*Queries` classes; table/column names and `CREATE` statements in `data/db/contract/*Contract`
(static constants only). `DbHelper` wires them to the lifecycle (`onCreate` runs every contract's `CREATE`; schema changes bump
`VERSION` and add an `if (oldVersion < N)` step). Queries run on `ioExecutor`, never on the main thread.

## Contracts the UI relies on -- FINAL signatures
Source of truth: the files under `domain/model/` and `domain/repository/` (javadoc'd). Conventions: models are Java
`record`s (accessors without `get`, e.g. `track.durationMs()`, `track.liked()`), 0 = unknown for `year`/`trackNumber`,
nullable things are `@Nullable`; repository getters return `LiveData` delivered on the main thread; one-shot async uses
`Callback<T>` (main thread); write methods are fire-and-forget.

```java
// domain/repository
interface PlayerController {
    LiveData<PlaybackState> getState();
    void play(List<Track> tracks, int startIndex, @Nullable String playingFrom);
    void resume(); void pause(); void togglePlayPause(); void seekTo(long positionMs);
    void next(); void previous(); void skipTo(int index);
    void toggleShuffle(); void cycleRepeat();                       // OFF -> ALL -> ONE -> OFF
    void moveQueueItem(int from, int to); void removeFromQueue(int index);
    void addToQueue(Track track); void addToQueue(Track track, int index);  // index form also undoes a removal
    void playNext(Track track);
    void shuffleQueue(); void undoShuffleQueue();                   // sets PlaybackState.canUndoShuffle()
    void setSleepTimer(int minutes); void setSleepTimerEndOfTrack(); void cancelSleepTimer();
}
interface LibraryRepository {
    LiveData<List<Track>> getAllTracks(), getLikedTracks(), getHistory() /*newest first*/, getRecentlySaved();
    LiveData<List<Album>> getAlbums();  LiveData<List<Artist>> getArtists();  LiveData<List<Playlist>> getPlaylists();
    LiveData<Album> getAlbum(String id);  LiveData<Artist> getArtist(String id);  LiveData<Playlist> getPlaylist(String id); // null value = unknown id
    LiveData<List<Track>> getAlbumTracks(String albumId), getArtistTracks(String artistId), getPlaylistTracks(String playlistId);
    LiveData<List<SearchResult>> search(String query, SearchFilter filter);   // blank query -> empty list
    void toggleLike(String trackId);  void recordPlay(String trackId);
    void createPlaylist(String name, @Nullable Callback<Playlist> callback);  void addToPlaylist(String playlistId, String trackId);
}
interface LyricsRepository   { LiveData<Lyrics> lyricsFor(Track track); }   // no value while loading, then Lyrics or Lyrics.none(id)
interface WaveformRepository { LiveData<float[]> waveformFor(Track track, int bars); } // values in [0,1]; no value while extracting
interface HomeFeedSource     { LiveData<List<HomeSection>> getSections(); }
interface MusicSource {
    String getId(); String getDisplayName();
    void search(String query, int limit, Callback<List<Track>> callback);
    void resolvePlayableUri(Track track, Callback<String> callback);        // null = not playable here
}
interface Callback<T> { void onResult(T value); }
```
Model notes:
- Artwork: models carry `artworkUri` (nullable; `imageUri` on `Artist`) + `placeholderSeed`; when the URI is null the UI draws a
  deterministic gradient from the seed.
- `PlaybackState(currentTrack, playing, buffering, positionMs, durationMs, shuffleEnabled, repeatMode, queue, currentIndex,
  sleepTimer, playingFrom, canUndoShuffle)` + `hasNext()`, `hasPrevious()`, `remainingQueueMs()`, `PlaybackState.empty()`.
  Position is only refreshed while playing.
- `SleepTimer(kind, remainingMs, totalMs)`, `Kind = OFF | COUNTDOWN | END_OF_TRACK`; constants `SleepTimer.OFF`,
  `SleepTimer.END_OF_TRACK`, factory `SleepTimer.countdown(remaining, total)`.
- `Lyrics(trackId, lines, synced, source)` with `activeLineIndex(positionMs)`, `isEmpty()`, `Lyrics.none(trackId)`;
  unsynced lines have `timeMs == LyricLine.UNSYNCED`.
- `HomeItem` / `SearchResult` are tagged records: check `kind()` then read the matching field; build with `HomeItem.of(x)` /
  `SearchResult.of(x)`. `HomeSection(id, title, subtitle, style = HERO_CAROUSEL | CAROUSEL | LIST, items)`.
- `SearchFilter = ALL, SONGS, ALBUMS, ARTISTS, PLAYLISTS` (no VIDEOS in Phase 1).

## Fakes (`fake/`, bound in `di/AppContainer`)
`SampleData` (37 tracks, 7 artists incl. NEFFEX and Persian artist "سهراب نوری", 8 albums, 3 playlists, seeded likes/history,
sample lyrics: track-3 plain, track-4 none, track-34..37 Persian synced, rest synced English),
`FakePlayerController` (Handler ticker, 250 ms, runs only while playing; starts paused mid-track in a 12-item queue),
`FakeLibraryRepository`, `FakeLyricsRepository`, `FakeWaveformRepository`, `FakeHomeFeedSource`, `FakeMusicSource`.
Later tasks swap the assignments in `AppContainer`; the UI must not notice.

## UI requirements
See `docs/design/DESIGN.md`, `docs/design-reference/` and `docs/DIRECTIVES.md`. Seek bars (Wavy, Waveform, Bars) are custom
Views; the UI is Views-only, so animation uses `ValueAnimator`/`ViewPropertyAnimator`/RenderEffect and respects reduce-motion.

## Battery & RAM requirements (owner D6/D8 -- hard requirements)
- Playback runs in the Media3 foreground `MediaSessionService`; **no wake locks of our own** beyond ExoPlayer's
  `setWakeMode`, released on pause/stop; the service stops (notification dismissible) shortly after pause.
- Position ticks only while playing AND UI visible (~4-10 Hz max; notification progress is system-driven by MediaSession).
  No polling loops when backgrounded; observe LiveData with a lifecycle owner so observers stop when not started.
- Animations (wavy/bars/backdrop blur) pause when the screen is off/app backgrounded or the player isn't visible; honor
  "reduce motion" and battery saver (cheaper visuals: no blur, static bars). Draw-phase animation only, no per-frame allocation.
- Heavy work deferred: library scan and **waveform extraction** run on `ioExecutor` / WorkManager-style constraints
  (preferably charging/idle, never during playback start); results cached on disk; bounded concurrency; cancellable.
- Network (LRCLIB) only on demand per track, cached including negative results, no background sync.
- RAM: downsample artwork, cap the bitmap `LruCache` (a few MB), no blur on low-end devices, `ioExecutor` thread exits when idle.
- Final QA task adds a battery audit checklist (Perfetto/Battery Historian notes, wakelock dump, idle-after-pause check).
