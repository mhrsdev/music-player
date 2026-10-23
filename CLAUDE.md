# Music Player (Android) — Project Protocol

Read this file first, then `docs/STATUS.md`, then `docs/DIRECTIVES.md`. Those three are enough to resume the project in a fresh chat with no extra explanation from the user.

## Roles (set by the owner — mandatory)

- **Supervisor (the top-level agent in the chat)**: manages and monitors only. Relays the owner's instructions to sub-agents, tracks tasks, reviews results, merges. **Does not write product code.** Its only direct edits are to `docs/STATUS.md`, `docs/DIRECTIVES.md`, `CLAUDE.md` and git operations.
- **UI sub-agent**: model **Opus 5.5** (`model: "opus"`). Owns everything under `app/src/main/java/.../ui/` and visual design (XML + custom Views in Java, own visual identity, animation, theming).
- **All other sub-agents**: model **Sonnet 5.5** (`model: "sonnet"`). Build system, data, playback service, lyrics, tests, CI, etc. They must follow the **ponytail** skill (`.claude/skills/ponytail`, owner D8); the UI sub-agent uses **design-atelier** (+ `ui-ux-pro-max`, owner D10) instead. D7 structure rules win over ponytail's minimalism when they conflict.
- **Parallelism (owner D8):** several sub-agents may run at the same time, each in its own `git worktree` + `task/<id>-<slug>` branch, touching only its own package/files.

## Design skill (set by the owner — mandatory for UI work)

The owner supplied the **design-atelier** skill, installed at `.claude/skills/design-atelier/` (start with its `SKILL.md`; relevant references: `platforms.md`, `motion-spatial.md`, `color.md`, `typography.md`, `localization.md`, `accessibility.md`, `self-critique.md`). The UI sub-agent must read and follow it (design brief, three-perspective self-critique, authorship pass) and report what it actually verified. The UI must **closely resemble the reference screenshots but be more beautiful/polished**.

## Git rules (set by the owner — mandatory)

- `main` is the main branch and the final deliverable. When work is done it must be **merged into `main`**.
- The Supervisor merges into `main`. The Supervisor and sub-agents work on a separate working branch (currently `ccr-b49e5e2a-53r8m3`, plus short-lived `task/<id>-<slug>` branches or worktrees when agents run in parallel).
- Sub-agents never push to `main`. They commit on their task branch; the Supervisor merges after review.
- No PR unless the owner asks for one.

## Keeping state current (set by the owner — mandatory)

After **every** meaningful event (owner instruction, task started, task finished, blocker, decision) the Supervisor updates:
1. `docs/STATUS.md` — task board: Done / In progress / Todo, decisions, blockers, how to build.
2. `docs/DIRECTIVES.md` — append-only archive of each owner message (verbatim, plus a short English summary).

Commit these updates together with the related work so a chat switch loses nothing.

## Layout

- `docs/design/` — **owner-approved design v1 (D13) — the visual target**; implement it as XML + custom Views in Java. (v2 is rejected; only on branch `task/t2b-design-revision`.)
- `docs/design-reference/` — owner's reference screenshots (target look & feel, must be matched and *improved on*).
- `docs/ARCHITECTURE.md` — module/package layout and contracts between UI and the rest.
- App code under `app/` (**Java only**, XML + custom Views, MVVM, Media3, framework SQLite — see Hard code rules).

## Hard code rules (owner D7 — mandatory for every sub-agent)

- **No Kotlin**: no `.kt`/`.kts` files anywhere; Gradle build files are Groovy `.gradle`. Java only (`.java`).
- **MVVM**: View (Activity/Fragment/custom View) ⇄ ViewModel (LiveData/observable state) ⇄ Repository.
- **No Room.** Use the Android framework database (`SQLiteOpenHelper`/`SQLiteDatabase`). Keep all SQL tidy: table/column constants + dedicated query/DAO classes, no SQL strings scattered around.
- **Clean code, proper folder structure** (package-by-layer with feature sub-packages, small focused classes).
- **Gradle 9.0.0** wrapper (verify AGP compatibility; report to owner before deviating).
- **UI bar (D8):** very beautiful, own exclusive identity (not Google-looking), polished; **battery and RAM use must be very low** (hard requirement).
- Battery efficiency is a hard requirement (see `docs/ARCHITECTURE.md`).
