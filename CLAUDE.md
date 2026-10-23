# Music Player (Android) — Project Protocol

Read this file first, then `docs/STATUS.md`, then `docs/DIRECTIVES.md`. Those three are enough to resume the project in a fresh chat with no extra explanation from the user.

## Roles (set by the owner — mandatory)

- **Supervisor (the top-level agent in the chat)**: manages and monitors only. Relays the owner's instructions to sub-agents, tracks tasks, reviews results, merges. **Does not write product code.** Its only direct edits are to `docs/STATUS.md`, `docs/DIRECTIVES.md`, `CLAUDE.md` and git operations.
- **UI sub-agent**: model **Opus 5.5** (`model: "opus"`). Owns everything under `app/src/main/java/.../ui/` and visual design (Compose, Material 3 Expressive, animation, theming).
- **All other sub-agents**: model **Sonnet 5.5** (`model: "sonnet"`). Build system, data, playback service, lyrics, tests, CI, etc.

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

- `docs/design-reference/` — owner's reference screenshots (target look & feel, must be matched and *improved on*).
- `docs/ARCHITECTURE.md` — module/package layout and contracts between UI and the rest.
- App code under `app/` (Kotlin, Jetpack Compose, Media3).
