# Project Status (live — supervisor updates after every event)

_Last updated: 2026-10-23 — D2 (design skill) received_

## Snapshot
- Goal: Android music player, Material 3 Expressive UI that beats the reference screenshots in `docs/design-reference/`.
- Branches: `main` = final deliverable (supervisor merges). Working branch: `ccr-b49e5e2a-53r8m3`.
- Models: UI → Opus 5.5. Everything else → Sonnet 5.5. Supervisor writes no product code.
- Environment: JDK 21 + Gradle present; Android SDK installed by T1 at `/opt/android-sdk` (platform 37, build-tools 37.0.0; reinstall steps in README.md). Network (dl.google.com, Maven Central/Google, services.gradle.org) works through the proxy.
- How to build: `export ANDROID_HOME=/opt/android-sdk && ./gradlew assembleDebug lint test` (SDK install steps + details in `README.md`; SDK lives outside the repo, `local.properties` is gitignored). JDK 21, Gradle wrapper 9.8.2, AGP 9.4.1, compileSdk 37.

## Decisions
| # | Decision | Why / status |
|---|---|---|
| K1 | Native Android, Kotlin + Compose + Media3 | standard; matches reference look |
| K2 | Phase 1 = local-library player; sources behind `MusicSource` interface | Reference looks like an online YouTube-Music-style client; unofficial scraping of such services violates their ToS, so it is **not** built by default. **Open question for owner** (see below). |
| K4 | UI = reference look, more polished; UI agent must use `.claude/skills/design-atelier` (owner directive D2) | decided |
| K3 | Lyrics via embedded/sidecar LRC + LRCLIB | open, legal API |

## Open questions for the owner
1. Online catalog (Home "New releases", search of songs not on device): keep Phase 1 local-only, or the owner picks a legal provider/API (e.g. own backend, Jamendo, Audius)? Default until answered: local-only + pluggable interface.

## Open design decisions for the owner (from T2a)
1. RTL playback controls: keep seek bar + prev/next LTR (Material guidance, as mocked) or mirror (Samsung style)?
2. Default seek bar: Wavy (reference) or Waveform (Melodiha; needs one-time audio scan)? Both mocked; can be a setting.
3. App name & icon (placeholder "Music Player").
4. Profile avatar removed from nav (local app has no account) — OK?
5. Lyrics alignment: centered (as mocked) or left?

## Task board
Legend: ✅ done · 🔄 in progress · ⬜ todo · model in brackets

### In progress

### Todo
- ~~T1~~ (done) Scaffold Gradle project (app module, version catalog, Compose + M3 Expressive, Media3, Room, DI), Android SDK setup, `./gradlew assembleDebug` works, domain models/interfaces per ARCHITECTURE.md, in-memory fakes with sample data, CI workflow (GitHub Actions build + lint + unit tests).
- ⬜ **T2** [Opus] UI foundation: theme (M3 Expressive, dynamic color from artwork, dark/light/AMOLED), shared components (floating pill nav, wavy slider, morphing play button, hero carousel, track row, artwork w/ placeholder), navigation shell.
- ⬜ **T3** [Opus] Screens: Home, Search, Library, Artists/Albums/Playlists/History, Settings (against fakes).
- ⬜ **T4** [Opus] Player: mini-player → full player (shared element), lyrics view, queue sheet (drag, undo, sleep timer).
- ⬜ **T5** [Sonnet] Data layer: Room, MediaStore scanner + permissions, repositories, likes/playlists/history, HomeFeedSource from local data.
- ⬜ **T6** [Sonnet] Playback: Media3 MediaSessionService, PlayerController impl, queue ops, sleep timer, notification, audio focus, Bluetooth/headset controls.
- ⬜ **T7** [Sonnet] Lyrics: LRC parser, embedded tag + sidecar reader, LRCLIB client + cache.
- ⬜ **T8** [Sonnet] Wire UI ↔ real data/playback (replace fakes via DI), instrumentation smoke tests, unit tests.
- ⬜ **T9** [Sonnet] Final QA pass: build release, lint, fix; README with screenshots/build instructions.
- ⬜ **T10** Supervisor: merge everything into `main`, verify `main` builds.

### Done
- ✅ **T2a** [Opus] Design phase: 17 PNG mockups in `docs/design/png/` + spec `docs/design/DESIGN.md` (sources in `docs/design/src/`). Sent to owner in chat. **Awaiting owner approval before T2–T4.** Not verified: nothing runs on Android (browser mockups); notification/lock-screen is approximate; Persian copy needs native check; not mocked: tablet, overflow menus, sleep-timer sheet, permission-denied state.
- ✅ **T1** [Sonnet] Scaffold: Gradle 9.8.2/AGP 9.4.1, Kotlin 2.4.21, compile/target SDK 37, M3 1.5.0-beta02 (Expressive), Koin, Media3, Room; domain contracts + fakes (37 tracks); `assembleDebug`, `lint`, 23 unit tests pass (agent-reported; SDK at /opt/android-sdk, `ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug`). NOT verified: device run, release/R8, GitHub Actions run.
- ✅ **T0** Supervisor: repo bootstrap — `CLAUDE.md`, `docs/` (STATUS, DIRECTIVES, ARCHITECTURE), reference screenshots saved.

## Change log
- 2026-10-23: T2a done; mockups delivered in chat; waiting for owner feedback/approval, then T2–T4 (Opus) + T5–T7 (Sonnet) start.
- 2026-10-23: T1 completed (see Done). Note: scaffold landed inside commit b9b7e4c (shared working tree) — harmless.
- 2026-10-23: D3 archived (Melodiha refs saved). Design phase T2a launched (Opus); mockups to be shown to owner in chat before UI implementation.
- 2026-10-23: D2 archived: design-atelier skill installed in `.claude/skills/`; T2–T4 briefs must cite it and the "similar to reference, but prettier" target.
- 2026-10-23: T0 bootstrap committed to `main` + working branch; directive D1 archived.
- 2026-10-23: T1 delegated to Sonnet sub-agent. T2–T4 (UI, Opus) start after T1 lands contracts; T5–T7 (Sonnet) can run in parallel with UI once contracts exist.
