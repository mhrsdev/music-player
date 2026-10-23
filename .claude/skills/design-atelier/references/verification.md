# Rendered critique and evidence

Use the actual output to decide whether the implementation meets the contract. Read-only review stays read-only. For authorized implementation, correct relevant defects without adding a new approval gate for routine reversible fixes.

## Establish a reproducible observation

Record route, viewport, state, locale, data, motion setting and relevant environment. Wait for fonts and required resources. Stabilize fixture data and optional animation for image comparisons; do not mask genuine defects. Inspect a full frame and readable details, because a downscaled long screenshot can hide clipping and wrong variants.

Test the critical journey plus a relevant failure/recovery path. Check navigation, actual controls, keyboard, focus visibility, input preservation and back/cancel. Capture runtime errors and missing required resources. Use representative sizes and content stress cases from [responsive design](responsive.md).

## Looking at your own render

Look at the artifact itself before judging it from the source. If the host offers a preview, use it. Otherwise a headless Chromium can render a local page: `python scripts/inspect_page.py page.html --out shots` saves screenshots at phone and desktop widths in light and dark and lists measurable problems. Read the screenshots as images; the script reports facts such as sideways overflow, failed loads, unnamed controls, low text contrast and text left invisible, and it cannot tell you whether the page is clear or well made. Say which states you rendered and which you did not.

## Review lenses

After a build or change, apply [self-critique](self-critique.md) from the senior-professional, complete-beginner and experienced-user perspectives, then the [authorship pass](authorship.md). Keep each initial assessment visible before reconciling the findings; a single technical check does not stand in for all three. Scale the walkthrough to the changed task and respect review-only scope.

Apply [the rubric](../rubrics/design-review.md) in order: task, content, composition/type, interaction/inclusion, implementation. Record strengths as well as defects so repairs preserve valuable decisions. A critique should identify a concrete observation and its implication for a person; “make it pop” is not actionable.

Use severity by consequence: blocked critical task, major friction or misleading behavior, meaningful local weakness, optional refinement. Subjective preferences can be suggestions without being defects. Do not manufacture consensus by calling sequential self-review multiple independent reviewers.

## Repair loop

1. Reproduce and locate the issue.
2. Choose the smallest correction that addresses its cause.
3. Preserve unrelated working behavior and strengths.
4. Re-render and rerun affected checks from each affected perspective, including a previous successful control.
5. Compare the intended result, then stop when the requested scope is met.

If a repair regresses a critical task, resolve the regression first. If two focused attempts yield no useful change, diagnose missing evidence, wrong assumptions or environmental limitations before another aesthetic guess. A fixed round limit is a cost control, not permission to ship a known blocker silently.

Account for all three perspectives before delivery. Separate artifact observations from predictions about a beginner or experienced user. Do not convert a self-review into a claim that real users were tested.

## Evidence labels

- **Observed/measured:** directly inspected or calculated, with method and artifact.
- **Judgment:** a reasoned aesthetic or product interpretation.
- **Proposed:** a change not yet implemented or tested.
- **Not run/unavailable:** a needed check lacking execution.

For check results use `pass`, `fail`, `not_run` or `not_applicable`, with reasons for the latter two. A passing image diff measures consistency, not beauty. A contrast calculation proves only its tested pair/state. An automated accessibility pass is not a full accessibility audit.

The optional evidence script verifies schema, bounded local artifact paths and SHA-256 matches. It cannot establish that a log is genuine, that the command actually executed or that a design is good. Keep those limits in the final report rather than laundering a file-existence check into a quality certification.
