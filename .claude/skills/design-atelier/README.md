# Design Atelier

An independent, English-language design skill for websites, web applications, mobile and desktop software. It combines a compact decision process with selectively loaded design references. It guides judgment; it does not turn a model into a certified professional or guarantee a particular aesthetic result.

## Install

Copy the entire `design-atelier` directory, preserving its structure:

- Codex project: `.agents/skills/design-atelier/`
- Claude Code project: `.claude/skills/design-atelier/`
- Another host supporting Agent Skills: use that host's documented skill directory.

Do not replace project instructions or flatten the references into separate skills. Restart or reload the host if required. For manual use, ask the agent to read the installed `SKILL.md` and follow it for the design task. No hooks, background processes, remote calls or global configuration changes are installed. Python 3.10+ is only needed for optional helpers; the design instructions work without Python.

## Example requests

“Use design-atelier to redesign this scheduling application. Keep the current component library and make the mobile booking flow clearer.”

“Review this dashboard without editing. Show the most consequential design issues with evidence.”

“Create a distinctive editorial site for a coastal architecture journal. Use real content structure and make the navigation work.”

“این فرم رزرو فارسی را با حفظ هویت فعلی بهتر کن؛ خطاها، کیبورد و موبایل را هم بررسی کن.”

## Package structure

- `SKILL.md`: workflow and reference selection.
- `references/`: twenty-four focused knowledge modules plus a catalog of authorship tells, including contextual taste, Persian/RTL, post-build self-critique, an authorship pass against machine-made looks, charts, diagrams and single-file published pages.
- `rubrics/`, `templates/`: critique, brief, decisions, states, evidence contracts and a light/dark theme-token starting point.
- `examples/`: three worked decisions and interactive local HTML demonstrations.
- `scripts/`: optional offline routing, contrast calculation, headless page inspection, evidence validation and package checks, using the standard library.
- `tests/`: meaningful failure and routing tests.
- `evals/`: design tasks, near-miss trigger cases and a controlled evaluation protocol.
- `SOURCES.md`: pinned sources, influence, disagreements and provenance limits.

## Post-build self-critique

After creating or changing an interface, the skill inspects its own output from three perspectives: a senior professional, a complete beginner with very little computer experience, and an experienced user of similar tools. Each pass examines the same concrete task and relevant recovery behavior. Material findings lead to scoped corrections and another inspection before delivery; review-only requests produce recommendations without edits.

The [protocol](references/self-critique.md) defines the questions, evidence boundaries, conflict resolution and completion check. The [record](templates/self-critique.md) keeps findings and rechecks concise. For tiny changes, apply the three perspectives locally. These are simulated review lenses, not actual user research or independent reviewers.

## Optional checks

Run from the package directory:

```sh
python scripts/validate.py
python -m unittest discover -s tests -p 'test_*.py'
python scripts/route.py "Persian booking form with inline validation"
python scripts/evidence.py /path/to/evidence.json --root /path/to/project
python scripts/contrast.py "#767676" "#ffffff"
python scripts/contrast.py --css templates/theme-tokens.css --pair fg:bg --pair muted:surface
python scripts/inspect_page.py /path/to/page.html --out /tmp/page-shots
```

`inspect_page.py` needs an installed Chromium-family browser (it looks at `--browser`, `CHROME_BIN`, the Playwright cache, then `PATH`) and installs nothing. It renders at 390 and 1440 px in light and dark, saves screenshots to look at, and lists measurable facts: sideways overflow, failed loads, unnamed controls, low text contrast and text left invisible. It also lists machine-made signals (eyebrow labels, gradient text, pill badges, icon tiles, identical card groups, statistics banners, links that go nowhere, filler copy, and on right-to-left pages physical left or right rules), notes how many sit on the first screen and gives a coarse density band. Signals are prompts to look, can be wrong either way, and are not defects by themselves. The script does not judge clarity or beauty.

The evidence validator checks declared metadata, local paths and hashes. It does not run browser tests or certify that an attached log tells the truth. Open `examples/index.html` to explore the demonstrations. Their data is fictional and local; no bookings, payments or messages are sent.

The optional example browser checks additionally require Playwright's Python package and an installed Chromium browser. They install nothing automatically:

```sh
python tests/browser_smoke.py --output /tmp/design-atelier-evidence
# If using an existing system Chromium instead of Playwright's browser:
python tests/browser_smoke.py --output /tmp/design-atelier-evidence --executable /usr/bin/chromium
```

## Evaluation status

See `BUILD-REPORT.md` for checks actually performed on this release, including a small two-prompt agent trial whose limits it states plainly. The example builds are author-run smoke evaluations. Controlled with-skill/without-skill model comparisons and independent human taste evaluation are separate experiments described in `evals/PROTOCOL.md`; no such improvement percentage is claimed. A compact entry point and on-demand references reduce mandatory reading, but actual model token savings have not been measured.
