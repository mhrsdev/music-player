---
name: design-atelier
description: "Design and refine websites, landing pages, web apps, dashboards, charts, diagrams, and mobile and desktop software with senior-level product reasoning, deliberate art direction, complete interaction states and critique of the actual render. Use whenever the task is UI/UX design, redesign, visual polish, a design system, a landing page or single-file published HTML page, data visualization or dashboard design, an explanatory architecture or flow diagram, light and dark themes, reference-based implementation, or a design review, including Persian/RTL interfaces, even when the request never says design. Also use for requests to make an interface feel more human, beautiful, coherent, less generic or less AI-generated. Excludes backend-only work, standalone illustration, data analysis with no visual deliverable and unrelated graphic production."
---

# Design Atelier

Make a useful interface with a specific point of view. Work like a senior designer who can explain a choice, implement it within real constraints, and revise it after looking at the result. A style name or a quality score is not a substitute for judgment.

## 1. Establish the contract

Use the conversation, existing application, content, assets and project rules before asking questions. Identify the user, their main job, the product's promise, target platform, deliverable, constraints and evidence of success. Separate supplied facts, observed facts, assumptions and unknowns. Ask only when a missing answer would materially change the work; continue independent work meanwhile. For an open brief, state a concrete, reversible assumption and proceed. Do not invent research, brand approval, testimonials or product facts.

Choose the task mode:

- **Create:** establish a design language and complete the requested experience.
- **Extend/polish:** preserve working behavior and the established identity; target the actual weak points.
- **Reference:** recover observable relationships, record uncertain details, and preserve functional accessibility.
- **Review:** inspect and report; editing requires an editing request.
- **Specify:** deliver flows, states, component contracts and acceptance criteria; distinguish a specification from working software.

Match process depth to uncertainty and consequence. A button-spacing adjustment needs inspection and a focused check; a new onboarding flow needs journeys, failure states and broader verification. Use [the brief](templates/brief.md) for substantial work, keeping it compact.

## 2. Load the right knowledge

Read [judgment](references/judgment.md) for a new direction or aesthetic problem. Select further references below as needed, initially about two to four. Follow a prerequisite when relevant; load more when an unresolved requirement warrants it. This is progressive disclosure, not a hard cap on completeness.

| Problem | Reference |
|---|---|
| Goals, research uncertainty, discovery | [Discovery](references/discovery.md) |
| Brand, references, taste, distinctive direction | [Art direction](references/art-direction.md) |
| Navigation, journeys, prioritization | [Information architecture](references/information-architecture.md) |
| Layout, grouping, visual hierarchy | [Composition](references/composition.md) |
| Type, shaping, fonts, reading | [Typography](references/typography.md) |
| Palette, contrast, modes, semantic color | [Color](references/color.md) |
| Words, proof, imagery, icons | [Content and assets](references/content-assets.md) |
| Components, tokens, consistency | [Design systems](references/design-systems.md) |
| Controls, forms, recovery, permissions | [Interaction](references/interaction.md) |
| Inclusive access and keyboard | [Accessibility](references/accessibility.md) |
| Narrow screens, zoom, touch | [Responsive design](references/responsive.md) |
| Persian, RTL, mixed scripts | [Localization](references/localization.md) |
| Dashboard, tables, filters, dense workflows | [Data interfaces](references/data-interfaces.md) |
| Charts, palettes for data, direct labels, chart interaction | [Charts](references/charts.md) |
| Architecture, flow and comparison diagrams | [Diagrams](references/diagrams.md) |
| One-file or published pages, landing pages, light/dark tokens | [Standalone and published pages](references/hosted-pages.md) |
| Marketing, commerce, booking, content | [Product patterns](references/product-patterns.md) |
| Native mobile, desktop, cross-platform | [Platforms](references/platforms.md) |
| Motion, scroll, gesture, 3D | [Motion and spatial](references/motion-spatial.md) |
| Assets, implementation, performance | [Delivery](references/delivery.md) |
| Screenshots, critique, repair, evidence | [Verification](references/verification.md) |
| Looks generated, generic or templated | [Authorship](references/authorship.md) |
| Post-build professional, beginner and experienced-user critique | [Self-critique](references/self-critique.md) |

Optional `python <skill-dir>/scripts/route.py "task"` suggests references with reasons. It is a small lexical index, not semantic understanding or a replacement for reading the task. Select references manually when it abstains or misses context. Two more offline helpers measure and never judge: `scripts/contrast.py` computes WCAG ratios, and `scripts/inspect_page.py` renders a local page at phone and desktop widths in both color schemes, saves screenshots and lists measurable problems and machine-made signals. Paths resolve from this skill directory, not the user's project. Do not load the upstream research archive into ordinary design work.

## 3. Choose the simplest complete intervention

Set the scope before minimizing. In order, consider: remove unnecessary content; improve grouping and wording; reuse the project's components and tokens; use native platform behavior; use an installed dependency; create a new pattern only when the earlier options cannot meet the need. Preserve required functionality, accessible behavior, validation and recovery. Concise code is useful only while it remains clear and complete.

Record the chosen intervention, its tradeoff and the condition that would justify a more elaborate solution. Do not redesign an entire application to fix a local hierarchy problem. Do not import a new framework, font service or animation engine merely to demonstrate sophistication. Use available tools according to their host instructions; this skill neither requires nor prohibits MCP.

## 4. Form an intentional direction

Start with realistic content and the critical task. Define a short design thesis: **“For [person/task], [visual and behavioral choices] communicate [specific quality], while [constraint] remains protected.”** Explain type roles, composition, semantic color, density, imagery and motion in relation to that thesis. Translate adjectives such as “premium” into observable decisions.

Explore alternatives when the direction is genuinely uncertain or the user requests them. Alternatives should differ in structure or reading experience, not merely hue. Compare them against the same content and constraints. A focused change or an established brand does not need a ceremonial set of three concepts or five palettes.

Choose one or two places for expression and keep routine interactions learnable. Novelty is a budget, not a requirement. Gradients, cards, serif type, monochrome and symmetry are legitimate choices when they fit this brief. Avoid arriving at the same composition for unrelated subjects. On an open brief, notice which look you reach for by habit and ask what in this subject's own world would have produced something else ([judgment](references/judgment.md)). User preferences and established project requirements take precedence over generic style advice.

## 5. Design and build the experience

Resolve information hierarchy and a functional first version before adding optional effects. Implement the requested journeys with honest data boundaries. For each relevant component, cover default, focus, pressed/selected, disabled, pending, empty, partial/stale, error and success states. Include cancellation, retry and preservation of user input where applicable. Do not display a completed transaction when only a mock or local simulation exists.

Use intrinsic layout, semantic controls, existing components and semantic tokens. Consider mobile composition, keyboard access, long content, localization, reduced motion and slow or unavailable resources during construction. For native software, respect platform navigation, input, accessibility and window behavior rather than applying a website layout indiscriminately.

## 6. Critique your own work from three perspectives, then repair

After building or changing the interface, run [the self-critique protocol](references/self-critique.md) before delivery. Inspect the actual artifact, not just your explanation of it, from all three perspectives:

1. **Senior professional:** assess craft, hierarchy, identity, state completeness, accessibility and implementation tradeoffs against the brief.
2. **Complete beginner:** assume no familiarity with the product and very little computer experience. Check whether visible cues explain where to start, what to do, what changed and how to recover, without coaching or technical vocabulary.
3. **Experienced user of similar tools:** check familiar conventions, predictable behavior, speed, control, preservation of work and justified differences from comparable workflows.

Whichever perspective you take first, begin with your own weakest point: look at the render as you would at a stranger's work, name the three things you would criticize first with what shows each, and repair those before anything cosmetic. Depth follows consequence. A published page or prototype earns one honest look at phone and desktop widths in both themes and one round of repairs; a product in a repository, a multi-step flow or anything handling data or money earns the full loop. Say which you did.

Then take the authorship pass ([authorship](references/authorship.md); its [catalog](references/authorship-catalog.md) lists tells and repair moves). Looking as an editor who has seen a thousand generated pages, point with locations at what the page does because pages usually do and not because this subject asked, replace each with a decision drawn from the subject (or cut it), and check the replacement is not the next common look. A tell is a reason to look closer, and a stated user or project direction wins.

Walk the same critical task through each perspective and inspect a relevant mistake/recovery path. Keep the initial findings separate, then reconcile them by user consequence. These are simulated review lenses, not real users or independent expert consensus. Preserve strengths and label usability predictions as hypotheses. For a tiny change, apply all three locally without expanding into a full-product audit.

Use [the rubric](rubrics/design-review.md), [finding template](templates/finding.md) and, when useful, [the three-perspective record](templates/self-critique.md). Fix material findings within the authorized scope, then re-render and rerun affected checks from the affected perspectives. Fix structure and broken journeys before decorative details. Do not declare completion with an unreported blocker or an uninspected perspective. If repeated changes show no improvement, stop guessing, diagnose the cause, and report the unresolved limitation. Review-only tasks produce findings and recommendations without edits.

State measured results separately from aesthetic judgment. File presence, a screenshot diff and a high self-rating do not prove usability or beauty. If rendering or a required check is unavailable, say what was inspected and what remains unverified. End with the result, major decisions, actual validation and relevant limits; omit process paperwork for small tasks.

Worked examples: [editorial site](examples/editorial.md), [operations dashboard](examples/operations.md), [Persian booking](examples/booking-rtl.md). These demonstrate reasoning and local interactions; they are not mandatory style templates.

See [the worked three-perspective review](examples/self-critique.md) for evidence boundaries and reconciliation in review-only mode.
