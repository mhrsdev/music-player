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
| K12 | Less stock-Google component look (D6) → T2b design revision | adopted |
| K13 | **Language: Java only, NO Kotlin** (D7, answers Q-J). Groovy `.gradle` build files | decided by owner |
| K14 | UI = XML + custom Views (no Compose) → Wavy/Waveform/Bars seek bars are custom `View`s (D7) | consequence of K13 |
| K15 | **No Room**: framework `SQLiteOpenHelper`/`SQLiteDatabase`, tidy query/DAO classes (D7) | decided by owner |
| K16 | Clean code, proper package structure; **Gradle 9.0.0** (D7) | decided; AGP compatibility to be verified by build agent |
| K9 | App name "Music Player" (placeholder; rename later) | default applied |
| K3 | Lyrics via embedded/sidecar LRC + LRCLIB | open, legal API |

## Open questions for the owner
0. ~~Q-J~~ **ANSWERED (D7): Java only, no Kotlin.** Residual assumption for owner: "کوعری ها رو مرتب بزن" was read as "keep DB queries tidy".
1. Online catalog (Home "New releases", search of songs not on device): keep Phase 1 local-only, or the owner picks a legal provider/API (e.g. own backend, Jamendo, Audius)? Default until answered: local-only + pluggable interface.

## Task board
Legend: ✅ done · 🔄 in progress · ⬜ todo · model in brackets

### In progress
- 🔄 **T2b** [Opus] Design revision for Java/Views: less stock-Material/Google look, own identity, custom-drawn controls; update `docs/design/DESIGN.md` + re-render PNGs; show owner in chat and get OK. Branch `task/t2b-design-revision`.

- 🔄 **T1b** [Sonnet + ponytail] Java re-scaffold (Gradle 9.0.0, framework SQLite, contracts + fakes). Worktree `../wt-t1b`, branch `task/t1b-java-scaffold`. Gates T5/T6/T7/T-W/T2 (they start in parallel once T1b contracts are merged).

### Todo
**Plan after D7/D8 (PARALLEL sub-agents allowed — D8 replaces D5; non-UI agents use the ponytail skill; each in its own git worktree; each in `task/<id>-<slug>`; supervisor reviews + merges into `ccr-b49e5e2a-53r8m3`, finally `main`).**
Rules for every task: Java only (no `.kt/.kts`), MVVM, framework SQLite (no Room), tidy query classes, clean packages, Gradle 9.0.0 (CLAUDE.md "Hard code rules").
- (T1b → in progress) Re-scaffold in Java: delete Kotlin/Compose/Room code, Groovy `.gradle`, Gradle 9.0.0 wrapper (+ compatible AGP; report if impossible), Material Components/AndroidX Views, Media3, ViewModel/LiveData, packages per ARCHITECTURE.md (rewrite it for Java), domain models + repository interfaces + in-memory fakes with sample data, SQLite helper skeleton, CI, SDK setup per README. `./gradlew :app:assembleDebug test lint` green.
- ⬜ **T2** [Opus] UI foundation (Views): theme/styles, custom widgets incl. 3 seek bars (Wavy/Waveform/**Bars** voice-memo loudness, D4), nav shell, `WaveformRepository` contract + fake.
- ⬜ **T5** [Sonnet] Data: MediaStore scanner, `SQLiteOpenHelper` DB (library, likes, playlists, history, waveform cache), tidy query/DAO classes, repositories, settings (SharedPreferences).
- ⬜ **T6** [Sonnet] Playback: Media3 `MediaSessionService`, `PlayerController` impl, queue ops, sleep timer, notification; battery rules.
- ⬜ **T7** [Sonnet] Lyrics: embedded/sidecar LRC parser + LRCLIB client + cache.
- ⬜ **T-W** [Sonnet] Waveform extractor (MediaExtractor/MediaCodec → RMS → bars, LRU+disk cache).
- ⬜ **T3** [Opus] Screens: Home, Search, Library, Artists/Albums/Playlists/History, Settings (against fakes).
- ⬜ **T4** [Opus] Player: mini-player → full player, lyrics view, queue sheet (drag, undo, sleep timer).
- ⬜ **T8** [Sonnet] Wire UI ↔ real data/playback, instrumentation smoke tests, unit tests.
- ⬜ **T9** [Sonnet] QA: release build, lint, battery audit checklist (wakelocks, idle-after-pause), README.
- ⬜ **T10** Supervisor: merge into `main`, verify `main` builds.
Obsolete WIP (Kotlin/Compose, do NOT reuse code): `task/t2-ui-foundation`, `task/t5-data`, `task/t7-lyrics`, `task/tw-waveform` — algorithms (LRC parsing, RMS extraction) may be ported to Java by reading them.

### Done
- ✅ **T2a** [Opus] Design phase: 17 PNG mockups in `docs/design/png/` + spec `docs/design/DESIGN.md` (sources in `docs/design/src/`). Sent to owner in chat. **Approved by owner (D4).** Not verified: nothing runs on Android (browser mockups); notification/lock-screen is approximate; Persian copy needs native check; not mocked: tablet, overflow menus, sleep-timer sheet, permission-denied state.
- ✅ **T1** [Sonnet] Scaffold: Gradle 9.8.2/AGP 9.4.1, Kotlin 2.4.21, compile/target SDK 37, M3 1.5.0-beta02 (Expressive), Koin, Media3, Room; domain contracts + fakes (37 tracks); `assembleDebug`, `lint`, 23 unit tests pass (agent-reported; SDK at /opt/android-sdk, `ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug`). NOT verified: device run, release/R8, GitHub Actions run.
- ✅ **T0** Supervisor: repo bootstrap — `CLAUDE.md`, `docs/` (STATUS, DIRECTIVES, ARCHITECTURE), reference screenshots saved.

## Change log
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
