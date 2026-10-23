# Post-build self-critique

Run this after creating or changing an interface, before presenting it as finished. Apply all three perspectives below. Scale the inspected area to the change: a spacing correction needs a local check; a new workflow needs a complete task and recovery walkthrough. In review-only mode, produce findings without modifying the artifact.

This is an explicit change of evaluation criteria within one agent. It does not require spawning agents and does not establish real user behavior, independent consensus or a usability-test success rate.

## Set up the inspection

Name a concrete task, starting state and expected outcome. Inspect the rendered artifact or running prototype when available, using the target content, input method, viewport and locale. Look at the artifact before leaning on the rationale you wrote while building it. Do not use knowledge of hidden code or your intended behavior to excuse an unclear interface.

Collect an initial pass for each perspective before synthesizing corrections. A critical runtime blocker may need repair before the remaining walkthrough can proceed; retain the failure and resume the interrupted perspectives afterward. Reuse the same factual evidence when appropriate, but ask different questions of it.

## Diagnose before you defend

Before explaining why the work is right, look at the render and list the three things you would criticize first if a stranger had made it. Tie each to something you can point at: a crowded cluster, a label that collides, a control with no visible state, a color that fails on the other theme. Repair those first. If you cannot find any, you probably have not looked closely enough, so inspect a narrow width and a difficult state before accepting that.

## Perspective 1: senior professional

Evaluate the work as an exacting designer who must defend its quality and consequences:

- Does the primary task actually govern hierarchy, grouping and reading order?
- Are typography, optical alignment, spacing, color roles and imagery coherent at normal viewing size and narrow widths?
- Which choices belong to this subject, and which merely repeat your default style?
- Are focus, pending, empty, error, success and relevant exceptional states designed as carefully as the ideal screen?
- Does the implementation preserve the design system, accessibility, performance and maintenance constraints?

Use [the design rubric](../rubrics/design-review.md). Distinguish observable defects, required-standard failures and contextual aesthetic judgments. Name valuable choices to preserve. A stylistic preference is not automatically a blocker.

## Perspective 2: complete beginner

Assume the person is new to the product and has very little experience with computers. Do not assume they know interface vocabulary, keyboard shortcuts, icon meanings, hover behavior or the difference between saving, submitting and confirming. Evaluate respectfully; low familiarity does not imply low intelligence, and playful or childish styling is not a solution to unclear interaction.

Starting only from visible cues, ask at each step:

- What is this screen for, and where can I safely begin?
- Can I recognize the action from its words and appearance, without being told where to click?
- Do I know what information to enter, why it is needed and which fields are optional?
- After acting, can I tell whether anything happened, whether I must wait and whether the task is actually complete?
- If I choose the wrong item, enter invalid information or navigate back, can I recover without losing my work?

Inspect the requested path plus a realistic mistake. Do not silently coach the imagined user, invent their click history or claim “a beginner completed it.” Write an observation such as “The only action is an unlabeled gear icon” and a hypothesis such as “A first-time user may not recognize the settings destination.” The second claim needs real user evidence to become an observed user result.

## Perspective 3: experienced user of similar tools

Assume familiarity with the domain and common tools, but no knowledge of this implementation. Assess whether experience transfers usefully:

- Do names, navigation, selection, sorting, search and back behavior follow recognizable conventions?
- Does the tool preserve filters, selection and entered work through appropriate transitions?
- Can frequent tasks be completed without unnecessary steps, repeated entry or excessive confirmations?
- Are useful shortcuts, bulk actions or accelerators appropriate to the actual scope and discoverable when provided? Do not add them automatically to a simple product.
- Are departures from convention beneficial and understandable, or merely surprising?

Use supplied or inspected comparable tools when available. Name the relevant behavior rather than asserting an unsupported “industry standard.” Without such evidence, label the comparison as a conventional expectation or hypothesis; do not invent a competitor's features. Familiarity does not imply that every user prefers density or uses keyboard shortcuts.

## Added lenses for charts, diagrams and published pages

Each perspective asks its own question of these artifacts. A chart or diagram viewed by the professional is judged on form, label collisions, color jobs and both themes. The complete beginner asks whether the point is visible without explanation, whether units and the legend are findable and whether a value can be read without hover. The experienced user asks whether axes, scale and defaults match what comparable charts do and whether the exact data is reachable. For a published single page, the professional checks the first frame, tokens and both themes, the beginner checks what to do first and what happened after acting, and the experienced user checks whether browser behavior such as zoom, back, selection and copy still works. See [charts](charts.md), [diagrams](diagrams.md) and [published pages](hosted-pages.md).

## Authorship pass

The three perspectives ask whether the work serves people. A separate question remains: does it look made by someone for this subject, or generated from the average of its category? After the three perspectives, work through [authorship](authorship.md): locate each habitual choice, replace it with a decision from the subject or cut it, and check that the replacement is not the next common look. Record it in the same table as a fourth row; its evidence is the render and its findings need locations.

## Reconcile, repair and inspect again

Record each material finding as **perspective → observation → evidence basis → consequence/severity → correction → recheck result**, using [the record](../templates/self-critique.md). A clean pass should state the task inspected and evidence, without inventing a quota of defects.

Resolve conflicts against the brief and actual consequences. Prefer a clearly labeled primary path with optional expert accelerators when that serves both audiences. Do not force every expert through unnecessary onboarding or hide essential controls from beginners. A critical task failure cannot be canceled by praise from another perspective or by averaging ratings.

For authorized changes, fix material findings in scope, preserve strengths and repeat the affected task and recovery checks. Reinspect from every perspective affected by the change: making a label clearer can also affect layout and expert terminology. If a fix regresses a working path, resolve that first. After two unsuccessful focused repair attempts, diagnose the missing evidence or wrong premise before another guess; disclose unresolved problems rather than claiming a pass.

Before delivery, account for all three perspectives as **inspected**, **partially inspected**, or **not inspected**, with scope and reasons. A source-only review may still inspect all three conceptually while leaving rendered behavior unverified. A specification can be walked through as a specification; it is not a tested application. Do not mark unavailable browser, platform or user evidence as passed.

End with a compact account of the important findings, corrections, actual rechecks and remaining limits. No elaborate persona narrative, private reasoning transcript, perfect self-score or extra approval checkpoint is required. This self-review supports iteration; actual novice and experienced-user sessions remain a separate source of evidence.
