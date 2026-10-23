# Project Status (live — supervisor updates after every event)

_Last updated: 2026-10-23 — D6: handoff to new chat; Java/MVVM/battery/less-Google look; T2 paused_

## HOW TO RESUME IN A NEW CHAT (read this first)
1. Read `CLAUDE.md` (roles/rules), this file, `docs/DIRECTIVES.md` (all owner orders, D1–D5), `docs/design/DESIGN.md` (approved design; PNGs in `docs/design/png/`).
2. `git fetch`; work branch = `ccr-b49e5e2a-53r8m3`; final target `main`. You are the supervisor: do NOT write product code; launch the next Todo task as ONE sub-agent (UI → `model: opus`, else `sonnet`), review, merge, update this file, push.
3. Build: `export ANDROID_HOME=/opt/android-sdk` (re-install SDK per README.md if container is fresh) → `./gradlew :app:assembleDebug test lint`.

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
| K5 | RTL: seek bar + prev/next stay LTR (Material guidance) | default applied; owner may flip |
| K6 | Seek bar styles: **Wavy** (default), **Waveform** (Melodiha-like), **Bars** (voice-memo style, real loudness; owner D4) — user setting | decided |
| K7 | Lyrics centered | default applied |
| K8 | No profile avatar in nav (local app) | default applied |
| K10 | MVVM architecture (D6) | adopted |
| K11 | Battery efficiency = hard requirement (D6), see ARCHITECTURE.md | adopted |
| K12 | Less stock-Google component look (D6) → T2b design revision | adopted |
| K13 | Language: Kotlin (current) vs Java (friend's suggestion) | **OPEN — Q-J** |
| K9 | App name "Music Player" (placeholder; rename later) | default applied |
| K3 | Lyrics via embedded/sidecar LRC + LRCLIB | open, legal API |

## Open questions for the owner
0. **Q-J (BLOCKING, ask first):** Friend suggested **Java** (+MVVM). Compose is Kotlin-only → Java = rewrite UI with XML/custom Views, no battery benefit. Supervisor recommends **Kotlin + MVVM** (Compose, ViewModel/StateFlow). Owner to decide: keep Kotlin (recommended) or switch to Java + Views.
1. Online catalog (Home "New releases", search of songs not on device): keep Phase 1 local-only, or the owner picks a legal provider/API (e.g. own backend, Jamendo, Audius)? Default until answered: local-only + pluggable interface.

## Task board
Legend: ✅ done · 🔄 in progress · ⬜ todo · model in brackets

### In progress
_(none — T2 paused by owner for the stack/design decisions in D6; partial WIP on `task/t2-ui-foundation`)_

### Todo
**Revised plan after D6 (resume here):** (1) ask Q-J; (2) **T2b [Opus] design revision**: make components less stock-Material/Google-looking (own identity, custom-drawn controls; update `docs/design/DESIGN.md` + re-render affected PNGs, show owner in chat, get OK); (3) continue T2 → ... below, MVVM + battery rules from ARCHITECTURE.md.
**Execution order (sequential, ONE sub-agent at a time — D5):** T2 → T5 → T6 → T7 → T-W → T3 → T4 → T8 → T9 → T10. Each runs in its own branch `task/<id>-<slug>`; supervisor reviews + merges into `ccr-b49e5e2a-53r8m3`, finally into `main`.
Partial WIP from the stopped wave-1 agents is on origin: `task/t2-ui-foundation`, `task/t5-data`, `task/t7-lyrics`, `task/tw-waveform` (each only a few files — treat as a head start, or restart cleanly); `task/t6-playback` has nothing yet. Briefs for each task are in the task descriptions below + `docs/DIRECTIVES.md`; reuse the wave-1 scoping (each agent touches only its own package; own Koin module file `di/<X>Module.kt`; T8 wires them).
- (T2 → see In progress) UI foundation + 3 seek-bar styles (Wavy/Waveform/**Bars** = voice-memo loudness bars, D4), `domain/repository/WaveformRepository.kt` contract (`fun waveformFor(track: Track, bars: Int = 128): Flow<FloatArray?>`) + fake; theme, components, nav shell per `docs/design/DESIGN.md`.
- ⬜ **T-W** [Sonnet] Waveform extractor (MediaExtractor/MediaCodec → RMS → bars, LRU+disk cache) in `data/waveform/`.
- ~~T1~~ (done) Scaffold Gradle project (app module, version catalog, Compose + M3 Expressive, Media3, Room, DI), Android SDK setup, `./gradlew assembleDebug` works, domain models/interfaces per ARCHITECTURE.md, in-memory fakes with sample data, CI workflow (GitHub Actions build + lint + unit tests).
- ⬜ **T3** [Opus] Screens: Home, Search, Library, Artists/Albums/Playlists/History, Settings (against fakes).
- ⬜ **T4** [Opus] Player: mini-player → full player (shared element), lyrics view, queue sheet (drag, undo, sleep timer).
- ⬜ **T8** [Sonnet] Wire UI ↔ real data/playback (replace fakes via DI), instrumentation smoke tests, unit tests.
- ⬜ **T9** [Sonnet] Final QA pass: build release, lint, fix; README with screenshots/build instructions.
- ⬜ **T10** Supervisor: merge everything into `main`, verify `main` builds.

### Done
- ✅ **T2a** [Opus] Design phase: 17 PNG mockups in `docs/design/png/` + spec `docs/design/DESIGN.md` (sources in `docs/design/src/`). Sent to owner in chat. **Approved by owner (D4).** Not verified: nothing runs on Android (browser mockups); notification/lock-screen is approximate; Persian copy needs native check; not mocked: tablet, overflow menus, sleep-timer sheet, permission-denied state.
- ✅ **T1** [Sonnet] Scaffold: Gradle 9.8.2/AGP 9.4.1, Kotlin 2.4.21, compile/target SDK 37, M3 1.5.0-beta02 (Expressive), Koin, Media3, Room; domain contracts + fakes (37 tracks); `assembleDebug`, `lint`, 23 unit tests pass (agent-reported; SDK at /opt/android-sdk, `ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug`). NOT verified: device run, release/R8, GitHub Actions run.
- ✅ **T0** Supervisor: repo bootstrap — `CLAUDE.md`, `docs/` (STATUS, DIRECTIVES, ARCHITECTURE), reference screenshots saved.

## Change log
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
