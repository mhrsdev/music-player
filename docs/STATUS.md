# Project Status (live — supervisor updates after every event)

_Last updated: 2026-10-23 — D8: parallel agents + ponytail skill + UI/RAM/battery bar; D7: Q-J answered → JAVA ONLY (no Kotlin), MVVM, no Room (framework SQLite), Gradle 9.0.0; Compose/Kotlin scaffold obsolete_

## HOW TO RESUME IN A NEW CHAT (read this first)
1. Read `CLAUDE.md` (roles/rules), this file, `docs/DIRECTIVES.md` (all owner orders, D1–D7), `docs/design/DESIGN.md` (approved design; PNGs in `docs/design/png/`).
2. `git fetch`; work branch = `ccr-b49e5e2a-53r8m3`; final target `main`. You are the supervisor: do NOT write product code; launch the next Todo task as ONE sub-agent (UI → `model: opus`, else `sonnet`), review, merge, update this file, push.
3. Build: `export ANDROID_HOME=/opt/android-sdk` (re-install SDK per README.md if container is fresh) → `./gradlew :app:assembleDebug test lint`.

## Snapshot
- Goal: Android music player (Java, MVVM, XML/custom Views), UI with its own identity that beats the reference screenshots in `docs/design-reference/`.
- Branches: `main` = final deliverable (supervisor merges). Working branch: `ccr-b49e5e2a-53r8m3`.
- Models: UI → Opus 5.5. Everything else → Sonnet 5.5. Supervisor writes no product code.
- Environment: JDK 21 + Gradle present; Android SDK installed by T1 at `/opt/android-sdk` (platform 37, build-tools 37.0.0; reinstall steps in README.md). Network (dl.google.com, Maven Central/Google, services.gradle.org) works through the proxy.
- How to build: `export ANDROID_HOME=/opt/android-sdk && ./gradlew assembleDebug lint test` (SDK install steps + details in `README.md`; SDK lives outside the repo, `local.properties` is gitignored). JDK 21, Gradle wrapper 9.8.2, AGP 9.4.1, compileSdk 37.

## Decisions
| # | Decision | Why / status |
|---|---|---|
| K1 | Native Android, ~~Kotlin + Compose~~ → **Java + Views** (D7) + Media3 | superseded by K13/K14 |
| K2 | Phase 1 = local-library player; sources behind `MusicSource` interface | Reference looks like an online YouTube-Music-style client; unofficial scraping of such services violates their ToS, so it is **not** built by default. **Open question for owner** (see below). |
| K4 | UI = reference look, more polished; UI agent must use `.claude/skills/design-atelier` (owner directive D2) | decided |
| K5 | RTL: seek bar + prev/next stay LTR (Material guidance) | default applied; owner may flip |
| K6 | Seek bar styles: **Wavy** (default), **Waveform** (Melodiha-like), **Bars** (voice-memo style, real loudness; owner D4) — user setting | decided |
| K7 | Lyrics centered | default applied |
| K8 | No profile avatar in nav (local app) | default applied |
| K10 | MVVM architecture (D6) | adopted |
| K11 | Battery efficiency = hard requirement (D6), see ARCHITECTURE.md | adopted |
| K12 | ~~Less stock-Google look (D6) → v2~~ **Superseded by D13: use design v1** (`docs/design/`), built as Java Views | owner decision |
| K13 | **Language: Java only, NO Kotlin** (D7, answers Q-J). Groovy `.gradle` build files | decided by owner |
| K14 | UI = XML + custom Views (no Compose) → Wavy/Waveform/Bars seek bars are custom `View`s (D7) | consequence of K13 |
| K15 | **No Room**: framework `SQLiteOpenHelper`/`SQLiteDatabase`, tidy query/DAO classes (D7) | decided by owner |
| K17 | Gradle 9.0.0 + AGP 8.13.2 (forced: AGP 9 needs Gradle ≥ 9.1). Owner may allow Gradle 9.1+ later for AGP 9.x | accepted by supervisor, owner to confirm |
| K16 | Clean code, proper package structure; **Gradle 9.0.0** (D7) | decided; AGP compatibility to be verified by build agent |
| K9 | App name "Music Player" (placeholder; rename later) | default applied |
| K3 | Lyrics via embedded/sidecar LRC + LRCLIB | open, legal API |

## Open questions for the owner
0. ~~Q-J~~ **ANSWERED (D7): Java only, no Kotlin.** Residual assumption for owner: "کوعری ها رو مرتب بزن" was read as "keep DB queries tidy".
(v2 questions D-A..D-F void after D13; v1 defaults K5-K9 stand.)
1. Online catalog (Home "New releases", search of songs not on device): keep Phase 1 local-only, or the owner picks a legal provider/API (e.g. own backend, Jamendo, Audius)? Default until answered: local-only + pluggable interface.

## Task board
Legend: ✅ done · 🔄 in progress · ⬜ todo · model in brackets

### In progress






- 🔄 **P2** [Opus] Polish: light player + contrast guard, lyrics offline/Retry, shared-element-like transition, real output chip, add-to-playlist sheet; render player PNGs. Branch `task/p2-polish-player`, worktree `../wt-p2`.

- 🔄 **R1** [Sonnet+ponytail] Release engineering: signing config, tag-triggered release workflow, README (en+fa) + CHANGELOG, APK build/verify. Branch `task/r1-release`, worktree `../wt-r1`.

- 🔄 **F1** [Sonnet+ponytail] Functional integration audit of the critical user flows (first-run permission→scan, notification permission, play from every list, mini→full player, restore, settings) + fixes. Branch `task/f1-flows`, worktree `../wt-f1`.

- 🔄 **P3** [Opus] Library/Home layout gaps (collage, 2×2 grid, snap carousel, skeletons, search/artists, per-script font). Branch `task/p3-polish-library`, worktree `../wt-p3`.

### Todo
**Plan after D7/D8 (PARALLEL sub-agents allowed — D8 replaces D5; non-UI agents use the ponytail skill; each in its own git worktree; each in `task/<id>-<slug>`; supervisor reviews + merges into `ccr-b49e5e2a-53r8m3`, finally `main`).**
Rules for every task: Java only (no `.kt/.kts`), MVVM, framework SQLite (no Room), tidy query classes, clean packages, Gradle 9.0.0 (CLAUDE.md "Hard code rules").
- (T1b → in progress) Re-scaffold in Java: delete Kotlin/Compose/Room code, Groovy `.gradle`, Gradle 9.0.0 wrapper (+ compatible AGP; report if impossible), Material Components/AndroidX Views, Media3, ViewModel/LiveData, packages per ARCHITECTURE.md (rewrite it for Java), domain models + repository interfaces + in-memory fakes with sample data, SQLite helper skeleton, CI, SDK setup per README. `./gradlew :app:assembleDebug test lint` green.
- ⬜ **T2** [Opus] UI foundation (Views): theme/styles, custom widgets incl. 3 seek bars (Wavy/Waveform/**Bars** voice-memo loudness, D4), nav shell, `WaveformRepository` contract + fake.
- ⬜ **T5** [Sonnet] Data: MediaStore scanner, `SQLiteOpenHelper` DB (library, likes, playlists, history, waveform cache), tidy query/DAO classes, repositories, settings (SharedPreferences).
- ⬜ **T6** [Sonnet] Playback: Media3 `MediaSessionService`, `PlayerController` impl, queue ops, sleep timer, notification; battery rules.
- ⬜ **T7** [Sonnet] Lyrics: embedded/sidecar LRC parser + LRCLIB client + cache.
- ⬜ **T3** [Opus] Screens: Home, Search, Library, Artists/Albums/Playlists/History, Settings (against fakes).
- ⬜ **T4** [Opus] Player: mini-player → full player, lyrics view, queue sheet (drag, undo, sleep timer).
- ⬜ **T9** [Sonnet] QA: release build, lint, battery audit checklist (wakelocks, idle-after-pause), README.
- ⬜ **T10** Supervisor: merge into `main`, verify `main` builds.
Obsolete WIP (Kotlin/Compose, do NOT reuse code): `task/t2-ui-foundation`, `task/t5-data`, `task/t7-lyrics`, `task/tw-waveform` — algorithms (LRC parsing, RMS extraction) may be ported to Java by reading them.

### Done
- ✅ **P1** [Opus] Polish merged: bundled fonts (Google Sans Flex subset 110 KB + Vazirmatn 114 KB, OFL notices in assets/licenses), v1 type ramp, morph play buttons on Home/Library, Persian-in-English subtitle fix, Robolectric screen renders (`docs/screens/p1-*.png`, `ScreenRenderTest` + `FakeRenderApp`). Not done (→ P3): liked collage, 2×2 library grid, carousel snap, skeletons, search/artist polish, per-script font.
- ✅ **T8** [Sonnet+ponytail] Wiring merged: `AppContainer` real impls by default (`USE_FAKES` switch), `LibraryAdmin`, SqliteWaveformStore as WaveformStore, SqliteLyricsCacheStore (DB v3), single SettingsStore (battery-saver/sleep-default/seek-style/online-lyrics now consumed), deterministic history order (fixes flaky test), manifest perms (READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE ≤32, INTERNET), debug+release+test+lint green (agent-reported). **Supervisor found a gap:** nothing calls `AppContainer.onAudioPermissionGranted()` → first scan after in-app permission grant never runs → F1.
- ✅ **T2** [Opus] UI foundation merged (design v1 tokens): `ui/theme` (Tokens, ThemeController, AccentScheme w/ 4.5:1 guard, ColorExtractor, MotionPolicy), `ui/widget` (SeekBarView + Wavy/Waveform/Bars, MorphPlayPauseView, FloatingNavView, MiniPlayerView, ArtworkView/Loader, BackdropBlur, ChipView, SectionHeaderView, TrackRowView), 26 icons, debug WidgetGalleryActivity + Robolectric render test → `docs/screens/t2-widget-gallery-*.png` (real render from Android code). Gaps: fonts not bundled (system sans), LoadingIndicator, grain, hand-drawn 10-lobe cookie, device run.
- ✅ **T3** [Opus] Screens merged (design v1): `ui/nav`+MainActivity shell (PlayerNavigator, ScreenNavigator, permission first-run), Home, Search, Library hub + detail lists + grids, Settings (PrefsSettingsStore), en+fa strings, VM tests. After merge supervisor re-ran `:app:assembleDebug test lint` → green. Gaps: liked-card collage, multi-browse carousel, plain play circles on Home/Library, system fonts, no skeletons/add-to-playlist dialog, folder picker not implemented; settings (battery-saver/seek-style/online-lyrics/sleep-default) not yet read by consumers → T8. Duplicate SettingsStore (ui vs data) → T8 reconciles.
- ✅ **T4** [Opus] Player merged (retargeted to design v1): `ui/player` (PlayerFragment/ViewModel, cookie play, 3 seek bars from T2, 20px-backdrop + crossfade, swipe-down), `ui/player/lyrics` (centered, tap-to-seek, 3 s manual-scroll pause), `ui/queue` (sleep timer, drag, swipe-remove+Undo, shuffle+Undo), en+fa strings, VM tests. Branch also carried intermediate T2/T3 merges. Gaps: no light player (15), no word-sweep, no lyrics error/Retry state, no contrast guard, static output chip, not a true shared element, system bold instead of v1 type ramp. Lint: 32 errors outside T4 (18 MissingTranslation, 14 UseAppTint in T3 layouts) → assigned to T3. Not seen rendered anywhere.
- ✅ **T7** [Sonnet+ponytail] Lyrics merged: `data/lyrics` (LrcParser, embedded ID3/FLAC/MP4 readers, sidecar .lrc/.txt, LrclibClient /get→/search, LyricsResolver with TTL cache 30d/3d/5min, LyricsRepositoryImpl observer-driven), `di/LyricsModule`, 118 JVM tests, supervisor re-ran `:app:assembleDebug test` after merge → green (all test XMLs pass). Not verified: real tagged files, MediaStore file locator, real Persian LRCLIB hits; LRCLIB /get returned 503s during agent checks. T8 must add INTERNET permission + bind LyricsCacheStore.
- ✅ **T5** [Sonnet+ponytail] Data merged: `data/db` (DbHelper v2, contract/ + query/ classes, all SQL there), `data/scanner` (incremental MediaStore scanner), `data/repository` (SqliteLibraryRepository, LocalHomeFeedSource, SqliteWaveformStore), `data/settings/SettingsStore`, `di/DataModule`; 55 JVM tests (Robolectric 4.16 test-only dep for real SQLite), build+lint green (agent-reported). Not verified on device: MediaStore query, album-art URI (UI needs placeholder/loadThumbnail fallback). Extra repo methods outside the interface: rescan/scanIfStale/isScanning/renamePlaylist/deletePlaylist/removeFromPlaylist/moveInPlaylist → T8 exposes.
- ✅ **T6** [Sonnet+ponytail] Playback merged: `playback/` (PlayerHolder single ExoPlayer, ExoPlayerController, PlaybackService MediaSessionService stops 60 s after pause, PlaybackStore, SleepTimerLogic w/ fade, PlayCountListener), `di/PlaybackModule`, 18 JVM tests, build green (agent-reported). T8: create controller in `MusicPlayerApp.onCreate` via `PlaybackModule.createController(ctx, library::recordPlay)`. Not verified on device: notification, Bluetooth, FGS start on Android 14+; audio offload not enabled; fakes without mediaUri are unplayable.
- ❌ **T2b** [Opus] Design v2 — REJECTED by owner (D13), v1 is the target again; v2 lives only on branch `task/t2b-design-revision`. (Old entry:) `docs/design/DESIGN.md` v2 + 18 PNGs (new `17-player-bars.png`). Own identity (Readex Pro + Vazirmatn, 42 custom icons, Persian arch/tile language, 'Deck' = mini-player+nav, lamp controls, 3 real-loudness seek bars, 'Still' low-power mode, 64px pre-blurred backdrop, ≤30 fps wave only while playing+visible). Screenshots sent to owner in chat. Not verified: any Android run; not mocked: tablet/landscape, overflow menus, sleep-timer sheet, permission-denied states. **Open owner questions D-A..D-F** (see below).
- ✅ **T-W** [Sonnet+ponytail] Waveform extractor merged: `data/waveform` (WaveformMath, SamplingPlan sparse windows ~25% decode, EnvelopeBuilder, WaveformExtractor MediaCodec glue, WaveformStore + in-memory/file impls, WaveformRepositoryImpl with lazy/observer-driven work + battery-saver skip, `di/WaveformModule`), 45 JVM tests, build green (agent-reported). NOT verified: MediaCodec decode on a real device. T8 should bind `WaveformStore` to SQLite and call `prefetch(next track)`.
- ✅ **T1b** [Sonnet+ponytail] Java scaffold merged into the working branch. Gradle 9.0.0 + **AGP 8.13.2** (AGP 9.x needs Gradle ≥ 9.1; tried 9.0.1 → refused), compileSdk/targetSdk 37 (suppressUnsupportedCompileSdk), minSdk 26, Java records for models, LiveData contracts + `Callback<T>`, fakes (37 tracks / 7 artists / 8 albums), `AppContainer`, `DbHelper` skeleton, 24 JVM tests, R8 release builds. **Supervisor re-ran `:app:assembleDebug test lint` → green; 0 `.kt/.kts` files.** Not verified: device run, GitHub Actions.
- ✅ **T2a** [Opus] Design phase: 17 PNG mockups in `docs/design/png/` + spec `docs/design/DESIGN.md` (sources in `docs/design/src/`). Sent to owner in chat. **Approved by owner (D4).** Not verified: nothing runs on Android (browser mockups); notification/lock-screen is approximate; Persian copy needs native check; not mocked: tablet, overflow menus, sleep-timer sheet, permission-denied state.
- ✅ **T1** [Sonnet] Scaffold: Gradle 9.8.2/AGP 9.4.1, Kotlin 2.4.21, compile/target SDK 37, M3 1.5.0-beta02 (Expressive), Koin, Media3, Room; domain contracts + fakes (37 tracks); `assembleDebug`, `lint`, 23 unit tests pass (agent-reported; SDK at /opt/android-sdk, `ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug`). NOT verified: device run, release/R8, GitHub Actions run.
- ✅ **T0** Supervisor: repo bootstrap — `CLAUDE.md`, `docs/` (STATUS, DIRECTIVES, ARCHITECTURE), reference screenshots saved.

## Change log
- 2026-10-23: D15: MIT license added (LICENSE); README license section to say MIT.
- 2026-10-23: D14: owner wants a GitHub release now + README OK + main fully updated; then full-featured version. Release agent R1 launched (signing, release workflow, README/CHANGELOG, APK build).
- 2026-10-23: T2 merged; P1/P2 polish agents launched (deadline ~19:28 UTC). Remaining: T8 finish, T9 QA (after T8), merge to main.
- 2026-10-23: T3, T4 merged; ccr green (build+test+lint, supervisor-run). T2 widget gallery PNGs rendered from real Android code (docs/screens/) and sent to owner.
- 2026-10-23: Merged T5, T7 into ccr (supervisor fixed a conflict-marker slip from the T5 merge; one flaky T5 test `SqliteRepositoryTest.historyAndPlayCountsMoveToTheTop` (same-ms ties) passed on re-run; T8 told to make ordering deterministic).
- 2026-10-23: D13: owner rejects v2, wants v1. docs/design restored to v1; T2/T3/T4 retargeted.
- 2026-10-23: T2b done + merged; PNGs sent to owner in chat; owner feedback to be applied as follow-up to T2/T3/T4.
- 2026-10-23: D12 archived: 1-hour budget, up to 8 agents. Launched T2/T3/T4 (Opus) in parallel on WIP design v2; T2b asked to wrap up and report.
- 2026-10-23: T1b merged into ccr-b49e5e2a-53r8m3 (reviewed: build green, no Kotlin). T-W launched → 5 agents running (T2b, T5, T6, T7, T-W).
- 2026-10-23: D11 archived: up to 5 parallel agents; design screenshots go to the owner in chat when T2b is complete. Launched T5, T6, T7 on top of T1b contracts (origin/task/t1b-java-scaffold).
- 2026-10-23: D10 archived: ui-ux-pro-max skill installed + relayed to the UI agent.
- 2026-10-23: D9 archived and relayed to T2b (god-tier UI/UX, reasonable battery).
- 2026-10-23: D8 archived: parallel agents allowed (replaces D5), ponytail skill installed in .claude/skills and mandatory for non-UI agents, UI must be prettier/own-identity, battery+RAM minimal. T2b told; T1b launched in parallel.
- 2026-10-23: D7 archived (Q-J answered: Java only, MVVM, no Room, tidy queries, Gradle 9.0.0). CLAUDE.md got "Hard code rules". Plan rewritten (new T1b re-scaffold). T2b launched (Opus).
- 2026-10-23: D6 archived; T2 paused (WIP pushed); handoff prompt written to docs/HANDOFF_PROMPT.md.
- 2026-10-23: Owner: continue, one sub at a time. T2 launched (Opus).
- 2026-10-23: D5: all wave-1 agents stopped; WIP pushed to task branches; switched to sequential single-agent mode. Next: launch T2.
- 2026-10-23: D4 archived. Wave 1 launched in isolated worktrees: T2 (Opus), T5, T6, T7, T-W (Sonnet). Wave 2 after T2 merges: T3 + T4 (Opus, parallel).
- 2026-10-23: T2a done; mockups delivered in chat; waiting for owner feedback/approval, then T2–T4 (Opus) + T5–T7 (Sonnet) start.
- 2026-10-23: T1 completed (see Done). Note: scaffold landed inside commit b9b7e4c (shared working tree) — harmless.
- 2026-10-23: D3 archived (Melodiha refs saved). Design phase T2a launched (Opus); mockups to be shown to owner in chat before UI implementation.
- 2026-10-23: D2 archived: design-atelier skill installed in `.claude/skills/`; T2–T4 briefs must cite it and the "similar to reference, but prettier" target.
- 2026-10-23: T0 bootstrap committed to `main` + working branch; directive D1 archived.
- 2026-10-23: T1 delegated to Sonnet sub-agent. T2–T4 (UI, Opus) start after T1 lands contracts; T5–T7 (Sonnet) can run in parallel with UI once contracts exist.
