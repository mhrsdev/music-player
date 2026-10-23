# Changelog

## 1.4.0 — 2026-10-20

- Rebuilt the authorship pass on a wider evidence base (see `SOURCES.md`): an empirical count of Show HN sites and its open-source detector, two research papers, three anti-slop skills, a catalog of signs of machine-written text, and review and RTL write-ups. The module now covers why defaults win, how to look (subject inventory, locate before judging, first screen first, competitor test, claim-per-section test, repair by decision, one specific hand-made element), guardrails drawn from the sources (no single tell proves anything, false positives, vocabulary drift, do not fill removed ornament with ornament, direction wins) and reporting without a score.
- Added [authorship-catalog](references/authorship-catalog.md): tells by family with a repair move for each, from first screen, type, color, structure, components and motion to copy, imagery, completeness, data, right-to-left pages and sets. It states what is not evidence (correct grammar, plain prose, a transition word) and where the evidence is thin (Persian copy, charts).
- `scripts/inspect_page.py` signals now include pill badge above the headline, a restyled headline word, a statistics banner, FAQ accordion, emoji navigation, colored glows, purple call to action, links that go nowhere, onclick on non-interactive elements, generic call-to-action labels, promotional phrases, em dashes per 1,000 words, and on right-to-left pages physical left or right rules, letter-spacing on connected script and a language mismatch. Each signal reports how many sit on the first screen, and the output gives a coarse density band. The stock-font list is aligned with the sources.
- Tasks 33 and 34 (a right-to-left layout that is only translated; dead links and invented claims) added to the evaluation set. Four helper tests added.
- Unchanged: the 1.3.0 authorship wiring in the entry file, rubric, record and router.

## 1.3.0 — 2026-10-20

- Added the [authorship](references/authorship.md) pass: after the three perspectives, look at the render as an editor who has seen a thousand generated pages, locate what looks machine-made, replace each tell with a decision from the subject (or cut it), and check the replacement is not the next common look. It treats tells as reasons to look closer, defers to a stated user or project direction, and rejects banning patterns, because a removed default is replaced by the next most probable one.
- Wired it into the entry file, self-critique, verification, judgment, the review rubric (new dimension and severity note), the self-critique record and the router (English and Persian aliases that do not capture AI-detection requests).
- `scripts/inspect_page.py` now lists machine-made signals alongside measurable problems: eyebrow labels, gradient text and purple-to-blue gradients, glass, grid backgrounds, side-stripe cards, icon tiles, identical card groups, uniform radius, stock fonts, repeated hover lifts, endless animation, centered text share, arrows on buttons, zero-padded markers, filler words, em dashes, contrast slogans, round statistics, social-proof phrases, stock names, emoji and large inline drawings. Signals are prompts to look and can be wrong either way.
- Evaluation set: tasks 31 and 32 (self-repair of a generated-looking page; a requested cream, serif and terracotta look must not be called a defect) and five more trigger cases, two of them near misses about detecting AI-written text or AI-made images. Six new helper tests.
- The entry file is 1,622 words, up from 1,303 in 1.1.0.

## 1.2.0 — 2026-10-20

- Added three reference modules: [charts](references/charts.md) (form chosen by the data's job, the four jobs of color, marks, labels, interaction and table alternatives), [diagrams](references/diagrams.md) (mechanism over name, comparison by drawing the difference, labeled arrows, inline SVG) and [standalone and published pages](references/hosted-pages.md) (sandbox limits, completeness at rest, theme tokens, naming, fit, depth by consequence).
- Extended existing modules in the same designer's voice: themes in code (color), Persian page implementation notes (localization), looking at your own render (verification), diagnose-before-you-defend and lenses for charts, diagrams and published pages (self-critique), and a check on habitual looks for open briefs (judgment). The entry file adds starting from your own weakest point and letting depth follow consequence.
- Added `scripts/contrast.py` (WCAG ratios for hex pairs and for token pairs per theme), `scripts/inspect_page.py` (headless render at phone and desktop widths in both color schemes with measurable facts and screenshots) and `templates/theme-tokens.css`.
- Routing: three new modules with English and Persian aliases; chart vocabulary moved from data-interfaces to charts; landing-page vocabulary added to product-patterns. The description now names charts, diagrams, landing pages and themes.
- Evaluation set: tasks 25 to 30 with objective assertions and twelve more trigger cases, six of them near misses. Twenty-one new helper tests.
- Unchanged: the three interactive HTML demonstrations, the browser smoke script, `route.py` logic and `evidence.py`.

## 1.1.0 — 2026-10-20

- Made three-perspective self-critique a post-build workflow step: senior professional, complete beginner with very little computer experience, and experienced user of similar tools.
- Added separate task/recovery questions, artifact evidence boundaries, novice/expert conflict resolution, scoped repair/reinspection and explicit coverage accounting.
- Integrated the protocol into the main skill, verification reference, review rubric and finding record. Added a concise three-perspective template and a review-only worked example.
- Added bilingual reference-routing aliases with the verification prerequisite, one functional routing test and four unexecuted development evaluation scenarios.
- Kept review-only requests read-only and distinguished simulated perspectives from real user research or independent expert review.
- Existing interactive HTML fixtures are unchanged. Their previous browser results remain attributed to version 1.0.0; the 1.1.0 helpers and package are checked separately.

## 1.0.0 — 2026-10-20

Initial independent design skill with nineteen knowledge modules, three interactive examples, evidence/routing helpers and documented verification limits.
