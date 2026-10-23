# Design Atelier 1.4.0 — build report

Built 2026-10-20. This report covers 1.4.0 first, then 1.3.0, then 1.2.0 and its agent trial, then the unchanged 1.1.0 report, whose counts describe 1.1.0.

## Added in 1.4.0: a research-grounded authorship pass

After 1.3.0 introduced the authorship pass, its evidence base was widened and the pass rebuilt. Sources read are listed with their limits in `SOURCES.md`: an empirical count of Show HN sites and its open-source detector, a research paper on homogenization in vibe coding and a long analysis of why defaults win, three anti-slop skills, Wikipedia's catalog of signs of machine-written text and a field guide to them, code-review and right-to-left write-ups. Three pages failed or held no usable text and are named there.

What changed: `references/authorship.md` was rewritten (why defaults win, a six-step way of looking, guardrails taken from the sources, reporting without a score); `references/authorship-catalog.md` is new (tells by family, each with a repair move, plus what is not evidence and where evidence is thin); `scripts/inspect_page.py` gained signals for a pill badge above the headline, a restyled headline word, a statistics banner, FAQ accordions, emoji navigation, colored glows, purple calls to action, links that go nowhere, onclick on non-interactive elements, generic call-to-action labels, promotional phrases, em dashes per 1,000 words and, on right-to-left pages, physical left or right rules, letter-spacing on connected script and a language mismatch. Each signal reports how many sit on the first screen, and the output gives a coarse density band. The entry file is 1,629 words (1.1.0: 1,303).

What the research did not find: any study of whether people can tell generated websites from authored ones, and any reliable list of Persian copy clichés. The catalog says so and points to native-speaker review.

| Check | Result | What it establishes |
|---|---|---|
| Helper tests | 52 passed (4 new since 1.3.0) | New signal wording and thresholds, first-screen counts, density bands, em-dash rate, a real headless render of a planted generated-looking page raising dead-link, FAQ and generic-label signals, and a real render of a right-to-left page raising physical-value and letter-spacing signals while an LTR page raises none |
| Local package validator | Passed, 24 modules | Links (including the new catalog), JSON, syntax, module consistency |
| Signals on the seven agent-trial pages | 1.1.0 landing: 1 (a large drawing). 1.2.0 landing: 2 (eyebrow labels, drawings). Strict no-skill landing: 6, density many (icon tiles, an identical card grid, FAQ accordion, five links to nowhere, generic call-to-action labels, a drawing). Host-skill landing: 8, density many (eyebrow labels, grid background, card group, zero-padded markers, FAQ accordion, three links to nowhere, generic labels, a drawing). 1.2.0 settings panel: 1 (zero-padded navigation markers). Both other settings panels: none | The detector separates pages with several habits from plain ones on real output and reports first-screen locations |
| Planted pages | A dark generated-looking page raised nine distinct signals; the Persian booking demo raised one (its brand-purple button) | The rules fire on what they describe |

What the signals still miss: a warm-cream, serif and amber direction (no counting rule can see a palette's origin), eyebrow labels that are neither uppercase nor tracked, and whether a drawing looks finished. Some signals fire on legitimate structure (a numbered navigation index, a three-step list) and are written as prompts to look.

### Second agent trial, with 1.4.0

The two prompts from the 1.2.0 trial were run again with skill 1.4.0 (one Haiku-class run each, same wording). All nine runs below were then measured with the 1.4.0 inspector at 390 and 1440 px in both color schemes. "Signals" is the inspector's distinct-signal count and density band; "problems" is measurable defects (overflow, load errors, unnamed controls, contrast, invisible text, small targets).

| Prompt | Condition | Tokens | Tool calls | Edit calls | Time | Signals | Problems |
|---|---|---|---|---|---|---|---|
| Landing page | Skill 1.1.0 | 158,448 | 31 | 1 | 6 min 55 s | 1 (a few) | none |
| Landing page | Skill 1.2.0 | 154,437 | 37 | 1 | 5 min 36 s | 2 (a few) | none |
| Landing page | Skill 1.4.0 | 146,576 | 43 | 9 | 5 min 09 s | 1 (a few) | none |
| Landing page | No skill (strict) | 91,985 | 9 | 1 | 3 min 07 s | 6 (many) | header button contrast 3.64:1 light, 1.16:1 dark |
| Landing page | Host `artifact-design` | 125,544 | 8 | 0 | 4 min 34 s | 8 (many) | none |
| Settings panel | Skill 1.2.0 | 177,656 | 33 | 3 | 7 min 19 s | 1 (a few) | none |
| Settings panel | Skill 1.4.0 | 180,153 | 35 | 5 | 8 min 01 s | 0 (none) | none |
| Settings panel | No skill (strict) | 148,865 | 14 | 1 | 7 min 22 s | 0 (none) | none |
| Settings panel | Host `artifact-design` | 174,700 | 15 | 1 | 8 min 07 s | 0 (none) | range input under 24 px |

What can be said: both 1.4.0 runs read `authorship.md` (the landing run also read the catalog) and used `contrast.py` and `inspect_page.py` several times. The 1.4.0 landing page used 5% fewer tokens and 8% less time than 1.2.0 and raised no habit signals beyond one large drawing; its agent reported leaving out a gradient hero, icon cards and the stock section order, and its page has no eyebrow labels, icon tiles, card grid, FAQ or statistics. The 1.4.0 runs revised their own files more (9 and 5 edit calls against at most 3 elsewhere). Against the strict no-skill runs the 1.4.0 runs cost more: landing +59% tokens and +66% time, settings +21% tokens and +9% time.

What cannot be said: with one run per cell, none of these differences is established, and the evaluator wrote the skill and was not blind to condition. The 1.4.0 settings panel loads a Google Fonts stylesheet although the task asked for one self-contained file (it falls back to system fonts), has no skip link and keeps a few literal colors outside `:root`; the 1.4.0 landing page's navigation wraps at 390 px. The strict no-skill landing page raised six signals and a real contrast defect, but a strict no-skill settings panel raised none, so the signals do not rank pages on their own.

Limits: no claim is made that the authorship pass makes output look less generated beyond the observations above. Thresholds are original approximations informed by the sources, and the original analyst measured about 5 to 10 percent false positives on manual checks of a comparable list. The sources are opinion, tool documentation and one preliminary conceptual paper.

# Design Atelier 1.3.0 — build report

This section describes the version that introduced the authorship pass; its signal list and calibration are superseded by 1.4.0 above.

## Added in 1.3.0 (after the 1.2.0 trial)

An authorship pass for work that reads as machine-made: a new reference, a fourth review step after the three perspectives, a rubric dimension, router aliases, two evaluation tasks, five trigger cases and a signal list in `inspect_page.py`. The design is grounded in public write-ups and a community skill read on 2026-10-20 (see `SOURCES.md`); their shared finding, that banning one default moves output to the next, is why the pass asks for a decision drawn from the subject and not a deletion. The entry file is 1,622 words (1.1.0: 1,303). There are twenty-four reference modules.

| Check | Result | What it establishes |
|---|---|---|
| Helper tests | 48 passed (6 new) | Signal wording and thresholds, a real headless render of a planted generated-looking page that raises the expected signals and a plain page that raises none, bilingual routing of the new module, and that asking about detecting AI text or images does not route to it |
| Local package validator | Passed, 24 modules | Links, JSON, syntax and module consistency |
| Signals on the seven agent-trial pages | Raised on five of seven: eyebrow labels above headings (4 of 11 and 6 of 15 headings), six icons in tinted squares, a grid background, identical card groups, zero-padded markers, large inline drawings. None on the strict no-skill settings panel and the host-skill settings panel | The detector separates pages with obvious structural habits from plain ones on real output |

What the signals did not catch: the warm-cream, serif and amber direction of the 1.1.0 landing page, which no counting rule can see and which the pass leaves to the model's judgment, and an eyebrow style that is neither uppercase nor tracked. Some signals fire on legitimate structure: a numbered navigation index and a three-step list were flagged. They are written as prompts to look and say so.

Limits: the authorship pass has not been run by a model, so no claim is made that it makes output look less generated. The 1.2.0 agent trial below predates it. The source material is opinion and tool documentation, and its specific rules were not independently verified.

# Design Atelier 1.2.0 — build report

This section adds the 1.2.0 work and its checks.

## Added in 1.2.0

Three reference modules (charts, diagrams, standalone and published pages), a light/dark token template, two helpers (`contrast.py`, `inspect_page.py`) and targeted additions to color, localization, verification, self-critique, judgment and the entry file. They are written as a designer's way of looking, with questions to ask of your own render, so the model finds its own defects instead of receiving a checklist. The entry file grew from 1,303 to 1,567 words (93 lines); reference modules from twenty to twenty-three. The evaluation set grew to thirty design tasks (six new ones carry objective assertions) and thirty-two trigger cases. Source influence and what was not copied are in `SOURCES.md`.

## Executed verification (1.2.0)

| Check | Result | What it establishes |
|---|---|---|
| Helper tests | 42 passed (21 from 1.1.0, 21 new) | Contrast math on known values (21:1, 4.54:1 passes, 4.47:1 fails), transparency rejected, theme template passes ten pairs in both themes and its two dark blocks agree, new bilingual routes and abstentions, inspector flag logic, one real headless render of a planted overflow |
| Local package validator | Passed, 23 modules | Entry fields, internal links, JSON, Python syntax, module and dependency consistency |
| `inspect_page.py` on a deliberately broken page | Flagged sideways overflow, a failed image load, a missing alt, an unnamed button, low contrast, placeholder text, text invisible at rest, an empty title and a missing viewport tag | The helper detects the problems it claims to detect |
| `inspect_page.py` on the three 1.0.0 demos | No measurable problems after fixing a target-size false positive for labelled radio buttons | The helper does not flag known-good pages. The demos show no color-scheme response, which is noted as informational |
| Chromium example smoke suite | Not rerun | The HTML demos and smoke script are byte-identical to 1.0.0 |

## Two-prompt agent trial (not a statistical result)

Seven Haiku-class subagent runs, one per cell, on two plain prompts: an English landing page for a fictional offline field-notebook app, and a settings panel for the same app. Tokens and time are the harness's reported totals for each subagent.

| Prompt | Condition | Tokens | Tool calls | Time |
|---|---|---|---|---|
| Landing page | Skill 1.1.0 | 158,448 | 31 | 6 min 55 s |
| Landing page | Skill 1.2.0 | 154,437 | 37 | 5 min 36 s |
| Landing page | No skill (strict: told not to call any skill) | 91,985 | 9 | 3 min 07 s |
| Landing page | No skill instruction; agent loaded the host's own `artifact-design` skill | 125,544 | 8 | 4 min 34 s |
| Settings panel | Skill 1.2.0 | 177,656 | 33 | 7 min 19 s |
| Settings panel | No skill (strict) | 148,865 | 14 | 7 min 22 s |
| Settings panel | No skill instruction; agent loaded the host's own `artifact-design` skill | 174,700 | 15 | 8 min 07 s |

The first "no skill" runs were not clean baselines: both agents called the host's built-in design skill on their own. Strict reruns forbade skill calls. Both groups are reported.

What was measured on the final files with `inspect_page.py` at 390 and 1440 px in both color schemes: no measurable problems in either skill-version landing page or in the 1.2.0 settings panel. The strict no-skill landing page had a header button whose text measured 3.64:1 in light and 1.16:1 in dark. The default-skill settings panel had a range input flagged under 24 px. Both skill-version runs used the new helpers (1.2.0 landing page: six renders and seven contrast checks; settings panel: three and five); the 1.1.0 run wrote its own contrast and render scripts. Each skill-version run reported at least one defect it found in its own render and fixed it.

Judgment, not measurement: the skill-version landing pages were more specific to the subject (a sample entry, a pinned transect, export samples; a pinned-photo map card) than the strict no-skill page, which used a conventional card, FAQ and pricing template. The strict no-skill settings panel was comparable in polish to the 1.2.0 one and exercised more functional checks (67 scripted checks against 33).

Limits: one run per cell, one model, prompts chosen by the author, an evaluator who wrote the skill and was not blind to condition, no human preference study, self-reported agent checks apart from tool-call counts and the final files. Against the strict no-skill runs, both skill versions used more tokens in every pair and more time in two of three (landing page: 1.2.0 +68% tokens and +80% time, 1.1.0 +72% and +122%; settings panel: 1.2.0 +19% tokens and about equal time). Version 1.2.0 against 1.1.0 on the landing page was 2.5% fewer tokens and 19% less time. None of this supports a claim about quality improvement or cost saving.

## Explicit limits (1.2.0)

Native mobile and desktop guidance, screen-reader behavior and real-device checks remain unverified, as in 1.1.0. `inspect_page.py` measures; its contrast heuristic ignores images, gradients and overlays and it cannot judge clarity or beauty. Color-vision separation for chart palettes is not computed here; the charts module names the optional `dataviz` validator and says to report the check as not run without it. The Persian font candidates in the localization notes were not verified in a render.

## Earlier report: 1.1.0
Built 2026-10-20. Instructions, documentation and helpers are English. Persian appears intentionally in localization aliases, evaluation prompts and the RTL demonstration.

## Delivered

A single Agent Skill with a 1,303-word entry file, twenty selectively loaded reference modules totaling about 9,052 words, one anchored review rubric, six templates/contracts, four worked examples and three interactive HTML demonstrations. Supporting files provide optional offline reference routing, evidence integrity checks, package validation, twenty-one helper tests, twenty-four design evaluation tasks and twenty host-trigger cases. The core file is 88 lines; neither word nor line count is a measured model token count.

The design process covers discovery and truthful assumptions, content-first hierarchy, contextual art direction, typography and optical craft, semantic color, components, complete interaction states, recovery, accessibility, responsive composition, Persian/RTL, data interfaces, platform distinctions, purposeful motion/3D and rendered critique. Senior roles become sequential review lenses. No independent reviewer, user research or professional certification is simulated as fact.

## Added in 1.1.0: three-perspective self-critique

After a build or change, the agent inspects the actual artifact as a senior professional, a complete beginner with very little computer experience, and an experienced user of similar tools. Each initial pass addresses the same task and a recovery path, using different criteria. It then reconciles material findings, makes authorized corrections and reinspects affected perspectives before delivery. Small changes receive a local pass rather than a full-product audit.

The new reference and record template separate observed artifact facts, design judgment and usability hypotheses. Beginner/expert conflicts resolve against task consequences; a clearly labeled main path can coexist with optional accelerators. All three perspectives receive explicit inspection coverage. No real-user success, independent consensus or unexecuted browser pass is fabricated. Review-only requests remain read-only.

Four development evaluation cases were added for the protocol, conflicting needs, review-only scope and unavailable rendering. They are future model evaluations, not executed model trials. The worked review demonstrates source/previous-artifact reasoning and labels its suggested copy change as proposed, not applied.

## Source synthesis

- Ponytail contributes scope before minimization, a reuse/native/existing-dependency ladder, completeness and conditions for upgrading a simple solution.
- Perfect AI contributes broad specialist coverage. All 2,702 skill bodies were machine-ingested and hashed, with shared-template consolidation, per-skill cards, mechanism/context mapping and detailed family/tool inspection. This is not an independent manual line-by-line audit of every word or execution of every capability.
- UI UX Pro Max contributes selective retrieval, explicit fallback and source-aware suggestions. Its database and BM25 implementation are not vendored.
- Inspected Anthropic/OpenAI materials inform subject-specific direction, progressive disclosure, component/token reuse and scoped evaluation. Google's inspected Gemini CLI commands are repository workflow examples, not a universal design standard.

The source revisions, specific influences, excluded defaults and licensing boundaries are documented in `SOURCES.md`. The complete Perfect AI study ledger is distributed separately so ordinary skill use does not load a 2,702-record research catalog. Its topic classification is heuristic; 178 generic records receive an explicitly labeled fallback rather than a falsely precise mapping.

## Executed verification

| Check | Result | What it establishes |
|---|---|---|
| Python helper unit tests | 21 passed on 1.1.0 | Bilingual aliases including the new self-critique route and prerequisite, dependency closure, abstention/word boundaries, malformed/absent/tampered evidence, traversal and symlink rejection, honest unrun states |
| Local package validator | Passed | Required entry fields, internal Markdown targets, JSON parsing, Python syntax and module/dependency consistency |
| Strict YAML / upstream skill-format validator | Passed | Entry frontmatter parses and matches basic Agent Skill requirements |
| Chromium example smoke suite | 39 cases passed on 1.0.0 fixtures | 24 render configurations, 12 interaction journeys and 3 short-height layout checks |
| Rendered screenshot artifacts | 24 captured on 1.0.0 fixtures | Three examples × widths 320/390/768/1440 × normal/reduced motion |
| Runtime page errors | 0 observed | No captured JavaScript page errors in the exercised fixture states |
| Text contrast heuristic | Passed in sampled render states | Direct text with solid ancestor-background compositing; excludes placeholder/image/overlap and full non-text auditing |

The interactive HTML fixtures and browser smoke script are byte-identical to the 1.0.0 package, checked against its content manifest. Their browser checks were not rerun for this documentation/routing update. The following browser environment and results describe the earlier fixture run, not a fresh 1.1.0 run.

Browser: headless system Chromium 154.0.8037.57 via Python Playwright. An initial launch could not locate Playwright's bundled browser; the suite then used the existing system executable without downloading a browser. The final passing run used the documented `--executable` option.

Interaction coverage includes combined dashboard filters, filtered-empty recovery, reset, correct record details, Enter activation, Escape dismissal and focus restoration. The Persian form checks required errors, first-error focus, radio activation, invalid phone input, preserved name, Persian numeral normalization, keyboard submission, honest local review and return-to-edit preservation. Editorial checks cover actual reading anchors and keyboard disclosure.

The author inspected full-size desktop and narrow screenshots. One refinement aligned table headers with record columns and added a narrow-screen scroll cue. The affected browser matrix was rerun. These are author judgments and local fixture checks, not independent aesthetic ratings.

## Explicit limits

No controlled model with-skill/without-skill comparison, independent designer preference study or real user test was run. No percentage improvement in beauty, task usability, time, cost or model tokens is claimed. The entry/reference structure limits mandatory reading, but actual token savings remain unmeasured.

The twenty-four design tasks and twenty host-trigger cases are a future evaluation set, not forty-four passed model trials. Only the first three tasks have author-built example artifacts. The router's unit tests do not test a host model's decision to activate the skill.

Native mobile/desktop instructions were not executed in native runtimes. The browser matrix is viewport emulation, not physical-device, Safari, screen-reader or comprehensive zoom testing. The contrast heuristic and automated controls checks are not a full WCAG audit. No real backend, reservation, payment or message service is connected. Advanced motion and graphics guidance is not an implementation of every upstream graphics technique.

The evidence helper verifies metadata and local SHA-256 values. A successful validation cannot prove a log is genuine, a command ran, or an interface is usable or beautiful. Sources and local artifacts support only the specific claims stated here.

## Reproduce

From the package directory:

```sh
PYTHONDONTWRITEBYTECODE=1 python scripts/validate.py
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s tests -p 'test_*.py' -v
PYTHONDONTWRITEBYTECODE=1 python tests/browser_smoke.py --output /tmp/design-atelier-evidence --executable /usr/bin/chromium
```

The browser command requires existing optional Playwright and Chromium installations. It binds a temporary local HTTP server to loopback, captures fixture evidence outside the skill, and shuts down the server. No host installation or remote publishing is performed.
